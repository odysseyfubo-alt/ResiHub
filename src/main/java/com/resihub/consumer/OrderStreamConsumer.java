package com.resihub.consumer;

import com.resihub.service.IOrderService;
import com.resihub.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 团购订单异步消费者。
 *
 * 两个要点：
 * 1) XACK 的 key 必须是真实 stream 名（课程版本写成 "s1"，导致 PEL 永不清理、异常时整段重放）。
 * 2) XREADGROUP ... BLOCK 是阻塞命令，若与普通命令共用连接，会把同一连接上的其它命令一起阻塞
 *    （实测 200 并发下 P50 从 20ms 恶化到 1200ms）。这里给消费者单独开一条连接。
 */
@Slf4j
@Component
public class OrderStreamConsumer {

    private static final String STREAM = RedisConstants.ORDER_STREAM;
    private static final String GROUP = RedisConstants.ORDER_GROUP;
    private static final String CONSUMER_NAME = "c1";
    private static final byte[] STREAM_BYTES = STREAM.getBytes(StandardCharsets.UTF_8);

    @Resource
    private RedisConnectionFactory connectionFactory;

    @Resource
    private IOrderService orderService;

    private volatile boolean running = true;
    private RedisConnection consumerConnection;

    @PostConstruct
    public void init() {
        ensureGroup();
        Thread t = new Thread(this::loop, "order-stream-consumer");
        t.setDaemon(true);
        t.start();
        log.info("订单流消费者已启动 stream={} group={}（独立连接）", STREAM, GROUP);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        closeQuietly();
    }

    private void ensureGroup() {
        try (RedisConnection conn = connectionFactory.getConnection()) {
            conn.streamCommands().xGroupCreate(STREAM_BYTES, GROUP, ReadOffset.from("0"));
            log.info("消费组 {} 创建成功", GROUP);
        } catch (Exception e) {
            if (String.valueOf(e.getMessage()).contains("BUSYGROUP")) {
                log.info("消费组 {} 已存在", GROUP);
                return;
            }
            try (RedisConnection conn = connectionFactory.getConnection()) {
                conn.streamCommands().xAdd(StreamRecords
                        .rawBytes(Map.of("init".getBytes(StandardCharsets.UTF_8), "1".getBytes(StandardCharsets.UTF_8)))
                        .withStreamKey(STREAM_BYTES));
                conn.streamCommands().xGroupCreate(STREAM_BYTES, GROUP, ReadOffset.from("0"));
                log.info("stream 初始化并创建消费组成功");
            } catch (Exception ex) {
                log.warn("消费组创建失败（可能已存在）：{}", ex.getMessage());
            }
        }
    }

    private RedisConnection conn() {
        if (consumerConnection == null || consumerConnection.isClosed()) {
            closeQuietly();
            consumerConnection = connectionFactory.getConnection();
        }
        return consumerConnection;
    }

    private void closeQuietly() {
        if (consumerConnection != null) {
            try {
                consumerConnection.close();
            } catch (Exception ignored) {
            }
            consumerConnection = null;
        }
    }

    private void loop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<ByteRecord> records = conn().streamCommands().xReadGroup(
                        Consumer.from(GROUP, CONSUMER_NAME),
                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_BYTES, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (ByteRecord record : records) {
                    handle(record);
                }
            } catch (Exception e) {
                log.error("读取订单流异常：{}", e.getMessage());
                closeQuietly();
                handlePending();
                sleepQuietly();
            }
        }
    }

    private void handlePending() {
        int round = 0;
        while (round++ < 10) {
            try {
                List<ByteRecord> list = conn().streamCommands().xReadGroup(
                        Consumer.from(GROUP, CONSUMER_NAME),
                        StreamReadOptions.empty().count(10),
                        StreamOffset.create(STREAM_BYTES, ReadOffset.from("0")));
                if (list == null || list.isEmpty()) {
                    return;
                }
                for (ByteRecord record : list) {
                    handle(record);
                }
            } catch (Exception e) {
                log.error("处理 pending 消息异常：{}", e.getMessage());
                closeQuietly();
                return;
            }
        }
    }

    private void handle(ByteRecord record) {
        // 注意：ByteRecord 的 key 是 byte[]，不能直接用 Map.get(byte[]) 查（byte[] 的 equals 是引用比较）
        Map<String, String> v = new java.util.HashMap<>();
        record.getValue().forEach((k, val) -> v.put(new String(k, StandardCharsets.UTF_8),
                val == null ? null : new String(val, StandardCharsets.UTF_8)));
        String orderNo = v.get("orderNo");
        Long userId = longVal(v.get("userId"));
        Long productId = longVal(v.get("productId"));
        Long groupId = longVal(v.get("groupId"));
        if (orderNo == null || userId == null || productId == null || groupId == null) {
            log.warn("消息字段缺失，直接确认 id={}", record.getId());
            ack(record);
            return;
        }
        try {
            orderService.handleJoinMessage(orderNo, userId, productId, groupId);
            ack(record);
        } catch (Exception e) {
            // 不 ack：留给 pending 重试，保证不丢单
            log.error("处理参团消息失败 orderNo={}：{}", orderNo, e.getMessage());
        }
    }

    private void ack(ByteRecord record) {
        try {
            conn().streamCommands().xAck(STREAM_BYTES, GROUP, record.getId());
        } catch (Exception e) {
            log.error("XACK 失败 id={}：{}", record.getId(), e.getMessage());
            closeQuietly();
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String str(byte[] b) {
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private static Long longVal(String s) {
        try {
            return s == null ? null : Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
