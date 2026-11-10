package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.service.IOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private IOrderService orderService;

    /** 我的团购订单 */
    @GetMapping("/my")
    public Result my() {
        return orderService.myOrders();
    }

    @GetMapping("/{orderNo}")
    public Result detail(@PathVariable("orderNo") String orderNo) {
        return orderService.detail(orderNo);
    }

    /** 自提核销（团长 / 管家），一次性 */
    @PostMapping("/{orderNo}/verify")
    public Result verify(@PathVariable("orderNo") String orderNo) {
        return orderService.verify(orderNo);
    }
}
