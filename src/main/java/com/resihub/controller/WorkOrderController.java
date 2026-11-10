package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.entity.WorkOrder;
import com.resihub.service.IWorkOrderService;
import com.resihub.utils.RequireRole;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/work-order")
public class WorkOrderController {

    @Resource
    private IWorkOrderService workOrderService;

    /** 居民提报修单 */
    @PostMapping
    public Result create(@RequestBody WorkOrder form) {
        return workOrderService.create(form);
    }

    /** 待接单池（维修工/管家/管理员） */
    @GetMapping("/pool")
    @RequireRole({1, 2, 3})
    public Result pool() {
        return workOrderService.pool();
    }

    /** 抢单 */
    @PostMapping("/{id}/accept")
    @RequireRole({1, 2, 3})
    public Result accept(@PathVariable("id") Long id) {
        return workOrderService.accept(id);
    }

    /** 完工 */
    @PostMapping("/{id}/finish")
    @RequireRole({1, 2, 3})
    public Result finish(@PathVariable("id") Long id) {
        return workOrderService.finish(id);
    }

    /** 居民确认并评价 */
    @PostMapping("/{id}/rate")
    public Result rate(@PathVariable("id") Long id,
                       @RequestParam("rating") Integer rating,
                       @RequestParam(value = "comment", required = false) String comment) {
        return workOrderService.rate(id, rating, comment);
    }

    /** 我的报修 */
    @GetMapping("/my")
    public Result my() {
        return workOrderService.myOrders();
    }

    /** 我接的单 */
    @GetMapping("/assigned")
    @RequireRole({1, 2, 3})
    public Result assigned() {
        return workOrderService.assignedOrders();
    }

    /** 工单看板（管家/管理员） */
    @GetMapping("/list")
    @RequireRole({2, 3})
    public Result list(@RequestParam(value = "status", required = false) Integer status) {
        return workOrderService.allOrders(status);
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return workOrderService.detail(id);
    }
}
