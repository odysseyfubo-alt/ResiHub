package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.service.IGroupBuyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/group-buy")
public class GroupBuyController {

    @Resource
    private IGroupBuyService groupBuyService;

    /** 本小区团购列表 */
    @GetMapping("/list")
    public Result queryList() {
        return groupBuyService.queryList();
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return groupBuyService.detail(id);
    }

    /** 团长开团 */
    @PostMapping
    public Result open(@RequestParam("productId") Long productId,
                       @RequestParam(value = "minUsers", required = false) Integer minUsers,
                       @RequestParam(value = "pickupPoint", required = false) String pickupPoint,
                       @RequestParam(value = "hours", required = false) Integer hours) {
        return groupBuyService.open(productId, minUsers, pickupPoint, hours);
    }

    /** 参团 */
    @PostMapping("/{id}/join")
    public Result join(@PathVariable("id") Long id) {
        return groupBuyService.join(id);
    }
}
