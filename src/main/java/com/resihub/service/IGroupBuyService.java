package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.GroupBuy;

public interface IGroupBuyService extends IService<GroupBuy> {

    /** 本小区进行中/已结束的团 */
    Result queryList();

    Result detail(Long id);

    /** 团长开团 */
    Result open(Long productId, Integer minUsers, String pickupPoint, Integer deadlineHours);

    /** 参团（Lua 原子扣名额 + 异步下单） */
    Result join(Long groupBuyId);

    /** 到点未成团：流团 + 退款 + 库存回滚 */
    void closeAsFailed(GroupBuy gb);
}
