package com.resihub.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 分布式自增 ID：时间戳左移 32 位 + 当日序列号。
 * workerId 未参与位运算的场景下仅单机安全，本类通过 "icr:{worker}:{prefix}:{date}" 的
 * 前缀把序列号按实例隔离，避免多实例拿到相同序列号。
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;
    private final String workerId;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        // 实例标识：主机名 + 进程号，集群热扩时也能区分
        String host = System.getenv("COMPUTERNAME");
        if (host == null) {
            host = System.getenv("HOSTNAME");
        }
        this.workerId = (host == null ? "w" : host) + "-" + currentPid();
    }

    private static String currentPid() {
        try {
            return java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        } catch (Exception e) {
            return "0";
        }
    }

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = stringRedisTemplate.opsForValue()
                .increment("icr:" + workerId + ":" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }
}
