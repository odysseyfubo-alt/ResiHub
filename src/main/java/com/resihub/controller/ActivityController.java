package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.entity.Activity;
import com.resihub.service.IActivityService;
import com.resihub.utils.RequireRole;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Resource
    private IActivityService activityService;

    @GetMapping("/list")
    public Result list() {
        return activityService.queryList();
    }

    /** 发布活动（管家/管理员） */
    @PostMapping
    @RequireRole({2, 3})
    public Result create(@RequestBody Activity activity) {
        return activityService.create(activity);
    }

    /** 报名：名额有限，先到先得 */
    @PostMapping("/{id}/signup")
    public Result signup(@PathVariable("id") Long id) {
        return activityService.signup(id);
    }

    /** 现场核销（管家/管理员） */
    @PostMapping("/verify")
    @RequireRole({2, 3})
    public Result verify(@RequestParam("code") String code) {
        return activityService.verify(code);
    }

    /** 我报名的活动 */
    @GetMapping("/my")
    public Result my() {
        return activityService.myActivities();
    }
}
