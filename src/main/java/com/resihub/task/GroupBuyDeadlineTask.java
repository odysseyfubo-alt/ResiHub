package com.resihub.task;

import com.resihub.entity.GroupBuy;
import com.resihub.service.IGroupBuyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 团购到点判定：未达人数的自动流团并退款。
 */
@Slf4j
@Component
public class GroupBuyDeadlineTask {

    @Resource
    private IGroupBuyService groupBuyService;

    @Scheduled(fixedDelay = 10000, initialDelay = 8000)
    public void closeExpired() {
        List<GroupBuy> expired = groupBuyService.query()
                .eq("status", 0)
                .lt("deadline", LocalDateTime.now())
                .list();
        for (GroupBuy gb : expired) {
            try {
                groupBuyService.closeAsFailed(gb);
            } catch (Exception e) {
                log.error("流团处理失败 groupBuyId={}: {}", gb.getId(), e.getMessage());
            }
        }
    }
}
