package com.resihub.task;

import com.resihub.service.IWorkOrderService;
import com.resihub.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;

/**
 * 报修工单超时升级：扫描 Redis ZSet，超时未接单的自动升级给管家。
 */
@Slf4j
@Component
public class WorkOrderTimeoutTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IWorkOrderService workOrderService;

    @Scheduled(fixedDelay = 5000, initialDelay = 6000)
    public void scanTimeout() {
        long now = System.currentTimeMillis();
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .rangeByScore(RedisConstants.WORK_TIMEOUT_KEY, 0, now, 0, 20);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            try {
                workOrderService.upgradeTimeout(Long.valueOf(id));
            } catch (Exception e) {
                log.error("工单超时升级失败 id={}: {}", id, e.getMessage());
            } finally {
                // 无论是否升级成功都出队，避免死循环
                stringRedisTemplate.opsForZSet().remove(RedisConstants.WORK_TIMEOUT_KEY, id);
            }
        }
    }
}
