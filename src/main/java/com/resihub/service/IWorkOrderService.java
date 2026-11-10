package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.WorkOrder;

public interface IWorkOrderService extends IService<WorkOrder> {

    /** 居民提报修单 */
    Result create(WorkOrder form);

    /** 维修工待抢工单池 */
    Result pool();

    /** 维修工抢单（Redisson 锁 + 状态条件更新） */
    Result accept(Long id);

    /** 维修工完工，转待居民确认 */
    Result finish(Long id);

    /** 居民确认并评价 */
    Result rate(Long id, Integer rating, String comment);

    /** 我的报修 */
    Result myOrders();

    /** 我接的单（维修工） */
    Result assignedOrders();

    /** 全部工单（管家/管理员） */
    Result allOrders(Integer status);

    Result detail(Long id);

    /** 超时未接单：升级给管家 */
    void upgradeTimeout(Long id);
}
