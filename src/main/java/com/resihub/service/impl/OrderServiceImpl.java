package com.resihub.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.GroupBuy;
import com.resihub.entity.Order;
import com.resihub.entity.Product;
import com.resihub.mapper.GroupBuyMapper;
import com.resihub.mapper.OrderMapper;
import com.resihub.mapper.ProductMapper;
import com.resihub.service.INotificationService;
import com.resihub.service.IOrderService;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Resource
    private GroupBuyMapper groupBuyMapper;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private INotificationService notificationService;

    @Override
    public Result myOrders() {
        List<Order> list = query()
                .eq("user_id", UserHolder.getUserId())
                .orderByDesc("create_time")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result detail(String orderNo) {
        Order order = query().eq("order_no", orderNo).one();
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), UserHolder.getUserId())) {
            return Result.fail("订单不存在");
        }
        fill(java.util.Collections.singletonList(order));
        return Result.ok(order);
    }

    private void fill(List<Order> list) {
        for (Order o : list) {
            Product p = productMapper.selectById(o.getProductId());
            o.setProductName(p == null ? "" : p.getName());
            GroupBuy gb = groupBuyMapper.selectById(o.getGroupBuyId());
            if (gb != null) {
                o.setPickupPoint(gb.getPickupPoint());
                o.setGroupStatus(gb.getStatus());
            }
        }
    }

    @Override
    public Result verify(String orderNo) {
        Order order = query().eq("order_no", orderNo).one();
        if (order == null) {
            return Result.fail("订单不存在");
        }
        Integer role = UserHolder.getRole();
        GroupBuy gb = groupBuyMapper.selectById(order.getGroupBuyId());
        boolean isLeader = gb != null && Objects.equals(gb.getLeaderUserId(), UserHolder.getUserId());
        boolean isStaff = role != null && role >= 2;
        if (!isLeader && !isStaff) {
            return Result.fail("只有团长或管家可以核销");
        }
        if (order.getStatus() == null || order.getStatus() != 1) {
            return Result.fail("订单当前不可核销（状态：" + statusText(order.getStatus()) + "）");
        }
        // 条件更新保证只能核销一次
        boolean ok = update().set("status", 2).set("verify_time", LocalDateTime.now())
                .eq("id", order.getId()).eq("status", 1).update();
        if (!ok) {
            return Result.fail("核销失败，订单可能已被核销");
        }
        return Result.ok();
    }

    private String statusText(Integer s) {
        if (s == null) {
            return "未知";
        }
        switch (s) {
            case 0: return "待成团";
            case 1: return "待自提";
            case 2: return "已完成";
            case 3: return "已退款";
            default: return "已取消";
        }
    }

    @Override
    @Transactional
    public void handleJoinMessage(String orderNo, Long userId, Long productId, Long groupBuyId) {
        // 幂等：orderNo 唯一索引兜底
        if (query().eq("order_no", orderNo).count() > 0) {
            log.info("订单 {} 已存在，跳过重复消费", orderNo);
            return;
        }
        GroupBuy gb = groupBuyMapper.selectById(groupBuyId);
        if (gb == null || gb.getStatus() == null || gb.getStatus() != 0) {
            log.warn("团购 {} 已结束，丢弃消息 orderNo={}", groupBuyId, orderNo);
            return;
        }
        Product product = productMapper.selectById(productId);
        Order order = new Order()
                .setOrderNo(orderNo)
                .setUserId(userId)
                .setProductId(productId)
                .setGroupBuyId(groupBuyId)
                .setQuantity(1)
                .setAmount(product == null || product.getPrice() == null ? 0 : product.getPrice())
                .setStatus(0)
                .setPickupCode(RandomUtil.randomNumbers(6));
        try {
            save(order);
        } catch (DuplicateKeyException e) {
            log.info("订单 {} 并发重复，忽略", orderNo);
            return;
        }
        // DB 库存条件扣减，防超卖
        int stockUpdated = productMapper.update(null, new UpdateWrapper<Product>()
                .setSql("stock = stock - 1").eq("id", productId).gt("stock", 0));
        if (stockUpdated == 0) {
            log.warn("商品 {} 库存不足，订单 {} 置为已取消", productId, orderNo);
            update().set("status", 4).eq("id", order.getId()).update();
            return;
        }
        // 团进度 +1
        groupBuyMapper.update(null, new UpdateWrapper<GroupBuy>()
                .setSql("current_users = current_users + 1").eq("id", groupBuyId));

        // 成团判定（条件更新避免并发重复成团）
        GroupBuy now = groupBuyMapper.selectById(groupBuyId);
        if (now != null && now.getStatus() != null && now.getStatus() == 0
                && now.getCurrentUsers() >= now.getMinUsers()) {
            int flipped = groupBuyMapper.update(null, new UpdateWrapper<GroupBuy>()
                    .set("status", 1).eq("id", groupBuyId).eq("status", 0));
            if (flipped > 0) {
                update().set("status", 1).eq("group_buy_id", groupBuyId).eq("status", 0).update();
                List<Order> orders = query().eq("group_buy_id", groupBuyId).list();
                for (Order o : orders) {
                    notificationService.send(o.getUserId(), gb.getCommunityId(), "团购成团了",
                            "「" + (product == null ? "团购商品" : product.getName()) + "」已成团，请 " + orderNo
                                    + " 等订单到「" + gb.getPickupPoint() + "」自提。", "order", o.getId());
                }
                log.info("团购 {} 成团，订单 {} 笔转为待自提", groupBuyId, orders.size());
            }
        }
    }
}
