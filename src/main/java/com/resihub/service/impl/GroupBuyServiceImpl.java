package com.resihub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.GroupBuy;
import com.resihub.entity.Order;
import com.resihub.entity.Product;
import com.resihub.entity.User;
import com.resihub.mapper.GroupBuyMapper;
import com.resihub.mapper.OrderMapper;
import com.resihub.mapper.ProductMapper;
import com.resihub.mapper.UserMapper;
import com.resihub.service.IGroupBuyService;
import com.resihub.service.IUserService;
import com.resihub.utils.RedisIdWorker;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.resihub.utils.RedisConstants.GROUP_STOCK_KEY;
import static com.resihub.utils.RedisConstants.GROUP_USERS_KEY;
import static com.resihub.utils.RedisConstants.ORDER_STREAM;

@Slf4j
@Service
public class GroupBuyServiceImpl extends ServiceImpl<GroupBuyMapper, GroupBuy> implements IGroupBuyService {

    @Resource
    private ProductMapper productMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private OrderMapper orderMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private IUserService userService;
    @Resource
    private com.resihub.service.IProductService productService;
    @Resource
    private com.resihub.service.IOrderService orderService;
    @Resource
    private com.resihub.service.INotificationService notificationService;

    private static final DefaultRedisScript<Long> JOIN_SCRIPT;

    static {
        JOIN_SCRIPT = new DefaultRedisScript<>();
        JOIN_SCRIPT.setLocation(new ClassPathResource("lua/join_group.lua"));
        JOIN_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result queryList() {
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        List<GroupBuy> list = query()
                .eq("community_id", communityId)
                .orderByAsc("status")
                .orderByAsc("deadline")
                .list();
        list.forEach(this::fillExtras);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result detail(Long id) {
        GroupBuy gb = getById(id);
        if (gb == null) {
            return Result.fail("团购不存在");
        }
        fillExtras(gb);
        return Result.ok(gb);
    }

    private void fillExtras(GroupBuy gb) {
        Product p = productMapper.selectById(gb.getProductId());
        gb.setProduct(p);
        User leader = userMapper.selectById(gb.getLeaderUserId());
        gb.setLeaderName(leader == null ? "邻居" : leader.getNickName());
        Long userId = UserHolder.getUserId();
        if (userId != null) {
            Integer count = orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>()
                    .eq("user_id", userId)
                    .eq("group_buy_id", gb.getId()));
            gb.setJoined(count != null && count > 0);
        }
    }

    @Override
    public Result open(Long productId, Integer minUsers, String pickupPoint, Integer deadlineHours) {
        userService.assertAudited();
        Long userId = UserHolder.getUserId();
        Long communityId = UserHolder.getCommunityId();
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            return Result.fail("商品不存在或已下架");
        }
        if (!product.getCommunityId().equals(communityId)) {
            return Result.fail("只能为本小区商品开团");
        }
        GroupBuy exists = query().eq("product_id", productId).one();
        int min = minUsers == null || minUsers < 1 ? 3 : minUsers;
        int hours = deadlineHours == null || deadlineHours < 1 ? 24 : deadlineHours;
        String point = pickupPoint == null || pickupPoint.trim().isEmpty() ? "小区东门" : pickupPoint.trim();
        LocalDateTime deadline = LocalDateTime.now().plusHours(hours);

        GroupBuy gb;
        if (exists == null) {
            gb = new GroupBuy()
                    .setProductId(productId).setCommunityId(communityId)
                    .setLeaderUserId(userId).setMinUsers(min).setCurrentUsers(0)
                    .setPickupPoint(point).setDeadline(deadline).setStatus(0);
            save(gb);
        } else {
            if (exists.getStatus() != null && exists.getStatus() == 0) {
                return Result.fail("该商品已有进行中的团购");
            }
            exists.setLeaderUserId(userId).setMinUsers(min).setCurrentUsers(0)
                    .setPickupPoint(point).setDeadline(deadline).setStatus(0);
            updateById(exists);
            gb = exists;
        }
        // 初始化 Redis 名额（幂等覆盖），清空上一轮的占位用户
        stringRedisTemplate.opsForValue().set(GROUP_STOCK_KEY + gb.getId(),
                String.valueOf(product.getStock() == null ? 0 : product.getStock()), 7, TimeUnit.DAYS);
        stringRedisTemplate.delete(GROUP_USERS_KEY + gb.getId());
        log.info("开团成功 groupBuyId={}, productId={}, minUsers={}, deadline={}", gb.getId(), productId, min, deadline);
        return Result.ok(gb.getId());
    }

    @Override
    public Result join(Long groupBuyId) {
        userService.assertAudited();
        Long userId = UserHolder.getUserId();
        GroupBuy gb = getById(groupBuyId);
        if (gb == null) {
            return Result.fail("团购不存在");
        }
        if (gb.getStatus() != 0) {
            return Result.fail(gb.getStatus() == 1 ? "该团已成交，去别的团看看" : "该团已流团");
        }
        if (gb.getDeadline().isBefore(LocalDateTime.now())) {
            return Result.fail("已过截团时间");
        }
        if (!java.util.Objects.equals(gb.getCommunityId(), UserHolder.getCommunityId())) {
            return Result.fail("只能参加本小区的团购");
        }
        String stockKey = GROUP_STOCK_KEY + groupBuyId;
        String usersKey = GROUP_USERS_KEY + groupBuyId;
        // 名额 key 缺失时用商品库存兜底（避免 Lua 里 tonumber(false) 报错）
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            Product p = productMapper.selectById(gb.getProductId());
            stringRedisTemplate.opsForValue()
                    .setIfAbsent(stockKey, String.valueOf(p == null || p.getStock() == null ? 0 : p.getStock()));
        }
        String orderNo = String.valueOf(redisIdWorker.nextId("order"));
        Long r = stringRedisTemplate.execute(JOIN_SCRIPT,
                Arrays.asList(stockKey, usersKey),
                userId.toString(), orderNo, gb.getProductId().toString(),
                groupBuyId.toString(), ORDER_STREAM);
        int code = r == null ? -1 : r.intValue();
        if (code == 1) {
            return Result.fail("名额已抢完");
        }
        if (code == 2) {
            return Result.fail("你已经参加过这个团了");
        }
        if (code == 3) {
            return Result.fail("团购名额未初始化，请稍后重试");
        }
        if (code != 0) {
            return Result.fail("参团失败，请重试");
        }
        return Result.ok(orderNo);
    }

    /** 流团：状态置 2，订单退款，库存回滚，清 Redis */
    @Override
    public void closeAsFailed(GroupBuy gb) {
        boolean flipped = update().set("status", 2).eq("id", gb.getId()).eq("status", 0).update();
        if (!flipped) {
            return;
        }
        List<Order> orders = orderService.query().eq("group_buy_id", gb.getId()).eq("status", 0).list();
        for (Order o : orders) {
            orderService.update().set("status", 3).set("refund_time", LocalDateTime.now())
                    .eq("id", o.getId()).eq("status", 0).update();
            notificationService.send(o.getUserId(), gb.getCommunityId(), "团购未成团，已自动退款",
                    "「" + productName(gb.getProductId()) + "」未达到成团人数，订单金额已原路退回。", "order", o.getId());
        }
        if (!orders.isEmpty()) {
            productService.update().setSql("stock = stock + " + orders.size())
                    .eq("id", gb.getProductId()).update();
        }
        stringRedisTemplate.delete(Arrays.asList(GROUP_STOCK_KEY + gb.getId(), GROUP_USERS_KEY + gb.getId()));
        log.info("团购流团处理完成 groupBuyId={}, 退款订单数={}", gb.getId(), orders.size());
    }

    private String productName(Long productId) {
        Product p = productMapper.selectById(productId);
        return p == null ? "团购商品" : p.getName();
    }
}
