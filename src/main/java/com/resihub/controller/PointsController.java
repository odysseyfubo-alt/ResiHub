package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.service.IPointsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/points")
public class PointsController {

    @Resource
    private IPointsService pointsService;

    /** 每日签到，返回连续签到天数 */
    @PostMapping("/sign")
    public Result sign() {
        return pointsService.sign();
    }

    /** 本月连续签到天数 */
    @GetMapping("/sign/count")
    public Result signCount() {
        return pointsService.signCount();
    }

    /** 我的积分流水 */
    @GetMapping("/log")
    public Result log() {
        return pointsService.log();
    }

    /** 我的积分总额 */
    @GetMapping
    public Result total() {
        return pointsService.total();
    }
}
