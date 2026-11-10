package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.service.ICommunityService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/community")
public class CommunityController {

    @Resource
    private ICommunityService communityService;

    /** 小区列表（注册时选择，免登录） */
    @GetMapping("/list")
    public Result list() {
        return communityService.listAll();
    }

    /** 小区详情（带缓存） */
    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return communityService.detail(id);
    }
}
