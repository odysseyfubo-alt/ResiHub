package com.resihub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.User;
import com.resihub.entity.WorkOrder;
import com.resihub.mapper.UserMapper;
import com.resihub.mapper.WorkOrderMapper;
import com.resihub.service.INotificationService;
import com.resihub.service.IPointsService;
import com.resihub.service.IUserService;
import com.resihub.service.IWorkOrderService;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.resihub.utils.RedisConstants.WORK_TIMEOUT_KEY;

@Slf4j
@Service
public class WorkOrderServiceImpl extends ServiceImpl<WorkOrderMapper, WorkOrder> implements IWorkOrderService {

    /** 报修后多久没人接单就升级给管家 */
    private static final int TIMEOUT_MINUTES = 30;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;
    @Resource
    private IUserService userService;
    @Resource
    private INotificationService notificationService;
    @Resource
    private IPointsService pointsService;

    @Override
    public Result create(WorkOrder form) {
        userService.assertAudited();
        Long userId = UserHolder.getUserId();
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        if (form.getType() == null || form.getType().trim().isEmpty()) {
            return Result.fail("请选择报修类型");
        }
        if (form.getDescription() == null || form.getDescription().trim().isEmpty()) {
            return Result.fail("请填写问题描述");
        }
        LocalDateTime timeoutAt = LocalDateTime.now().plusMinutes(TIMEOUT_MINUTES);
        WorkOrder order = new WorkOrder()
                .setCommunityId(communityId)
                .setUserId(userId)
                .setType(form.getType().trim())
                .setDescription(form.getDescription().trim())
                .setImages(form.getImages() == null ? "" : form.getImages())
                .setStatus(0)
                .setTimeoutAt(timeoutAt);
        save(order);
        // 超时升级队列：score 用毫秒时间戳
        stringRedisTemplate.opsForZSet().add(WORK_TIMEOUT_KEY, order.getId().toString(),
                System.currentTimeMillis() + TIMEOUT_MINUTES * 60_000L);
        notificationService.sendToRole(communityId, 1, "有新的报修工单",
                "「" + order.getType() + "」" + order.getDescription(), "work", order.getId());
        log.info("新工单 id={}, type={}, timeoutAt={}", order.getId(), order.getType(), timeoutAt);
        return Result.ok(order.getId());
    }

    @Override
    public Result pool() {
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        List<WorkOrder> list = query()
                .eq("community_id", communityId)
                .eq("status", 0)
                .orderByAsc("create_time")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result accept(Long id) {
        WorkOrder order = getById(id);
        if (order == null) {
            return Result.fail("工单不存在");
        }
        if (!Objects.equals(order.getCommunityId(), UserHolder.getCommunityId())) {
            return Result.fail("工单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            return Result.fail("该工单已被接单或已关闭");
        }
        Long userId = UserHolder.getUserId();
        RLock lock = redissonClient.getLock("lock:work:" + id);
        boolean locked = false;
        try {
            locked = lock.tryLock(2, 10, TimeUnit.SECONDS);
            if (!locked) {
                return Result.fail("手慢了，工单已被抢走");
            }
            // 状态条件更新是第二道保险：即使锁失效也只会成功一次
            boolean ok = update()
                    .set("status", 1)
                    .set("assignee_id", userId)
                    .set("accept_time", LocalDateTime.now())
                    .eq("id", id)
                    .eq("status", 0)
                    .update();
            if (!ok) {
                return Result.fail("手慢了，工单已被抢走");
            }
            stringRedisTemplate.opsForZSet().remove(WORK_TIMEOUT_KEY, id.toString());
            notificationService.send(order.getUserId(), order.getCommunityId(), "报修已接单",
                    "维修工已接单，工单号 " + id + "，稍后上门。", "work", id);
            return Result.ok();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("抢单失败，请重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Result finish(Long id) {
        WorkOrder order = getById(id);
        if (order == null) {
            return Result.fail("工单不存在");
        }
        if (!Objects.equals(order.getAssigneeId(), UserHolder.getUserId())) {
            return Result.fail("只能完工自己接的单");
        }
        boolean ok = update()
                .set("status", 2)
                .set("finish_time", LocalDateTime.now())
                .eq("id", id)
                .eq("status", 1)
                .update();
        if (!ok) {
            return Result.fail("工单状态不允许完工");
        }
        notificationService.send(order.getUserId(), order.getCommunityId(), "维修已完工",
                "工单 " + id + " 已完工，请确认并评价。", "work", id);
        return Result.ok();
    }

    @Override
    public Result rate(Long id, Integer rating, String comment) {
        WorkOrder order = getById(id);
        if (order == null) {
            return Result.fail("工单不存在");
        }
        if (!Objects.equals(order.getUserId(), UserHolder.getUserId())) {
            return Result.fail("只能评价自己的工单");
        }
        if (order.getStatus() == null || order.getStatus() != 2) {
            return Result.fail("只能对已完工的工单评价");
        }
        int r = rating == null ? 5 : Math.max(1, Math.min(5, rating));
        boolean ok = update()
                .set("status", 3)
                .set("rating", r)
                .set("comment", comment)
                .eq("id", id)
                .eq("status", 2)
                .update();
        if (!ok) {
            return Result.fail("评价失败，工单状态已变化");
        }
        pointsService.addPoints(order.getUserId(), 5, "repair", "报修完成评价");
        notificationService.send(order.getAssigneeId(), order.getCommunityId(), "收到一条报修评价",
                "工单 " + id + " 被评为 " + r + " 星。", "work", id);
        return Result.ok();
    }

    @Override
    public Result myOrders() {
        List<WorkOrder> list = query()
                .eq("user_id", UserHolder.getUserId())
                .orderByDesc("create_time")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result assignedOrders() {
        List<WorkOrder> list = query()
                .eq("assignee_id", UserHolder.getUserId())
                .orderByDesc("accept_time")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result allOrders(Integer status) {
        Long communityId = UserHolder.getCommunityId();
        List<WorkOrder> list = query()
                .eq(communityId != null, "community_id", communityId)
                .eq(status != null, "status", status)
                .orderByDesc("create_time")
                .list();
        fill(list);
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result detail(Long id) {
        WorkOrder order = getById(id);
        if (order == null) {
            return Result.fail("工单不存在");
        }
        fill(java.util.Collections.singletonList(order));
        return Result.ok(order);
    }

    @Override
    public void upgradeTimeout(Long id) {
        boolean ok = update().set("status", 4).eq("id", id).eq("status", 0).update();
        if (!ok) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(WORK_TIMEOUT_KEY, id.toString());
        WorkOrder order = getById(id);
        if (order == null) {
            return;
        }
        notificationService.sendToRole(order.getCommunityId(), 2, "工单超时未接单，请介入",
                "工单 " + id + "（" + order.getType() + "）超过 " + TIMEOUT_MINUTES + " 分钟无人接单，已升级。",
                "work", id);
        notificationService.send(order.getUserId(), order.getCommunityId(), "报修已升级处理",
                "工单 " + id + " 超时未接单，已升级给管家跟进。", "work", id);
        log.warn("工单 {} 超时升级", id);
    }

    private void fill(List<WorkOrder> list) {
        for (WorkOrder o : list) {
            User reporter = userMapper.selectById(o.getUserId());
            if (reporter != null) {
                o.setUserName(reporter.getNickName());
            }
            if (o.getAssigneeId() != null) {
                User assignee = userMapper.selectById(o.getAssigneeId());
                if (assignee != null) {
                    o.setAssigneeName(assignee.getNickName());
                }
            }
        }
    }
}
