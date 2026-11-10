package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Order;

public interface IOrderService extends IService<Order> {

    Result myOrders();

    Result detail(String orderNo);

    /** 核销自提（团长 / 管家），一次性 */
    Result verify(String orderNo);

    /** 消费 Stream 消息：落库 + 扣库存 + 团进度 + 成团判定 */
    void handleJoinMessage(String orderNo, Long userId, Long productId, Long groupBuyId);
}
