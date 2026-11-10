package com.resihub.controller;

import com.resihub.dto.LoginFormDTO;
import com.resihub.dto.Result;
import com.resihub.service.IUserService;
import com.resihub.utils.RequireRole;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    /** 发送验证码（mock 短信，验证码打印在日志） */
    @PostMapping("/code")
    public Result code(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /** 验证码登录，首次登录自动注册 */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO form) {
        return userService.login(form);
    }

    /** 当前登录用户（实时读库，认证状态不会滞后） */
    @GetMapping("/me")
    public Result me() {
        return userService.me();
    }

    /** 提交业主认证：选小区 + 楼栋 + 房号 */
    @PostMapping("/apply")
    public Result apply(@RequestParam("communityId") Long communityId,
                        @RequestParam("building") String building,
                        @RequestParam(value = "room", required = false) String room) {
        return userService.apply(communityId, building, room);
    }

    /** 待审核列表（管家 / 管理员） */
    @GetMapping("/audit/list")
    @RequireRole({2, 3})
    public Result auditList() {
        return userService.pendingAudits();
    }

    /** 审核（管家 / 管理员） */
    @PostMapping("/audit/{userId}/{pass}")
    @RequireRole({2, 3})
    public Result audit(@PathVariable("userId") Long userId, @PathVariable("pass") boolean pass) {
        return userService.audit(userId, pass);
    }
}
