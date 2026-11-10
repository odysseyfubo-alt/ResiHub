package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.entity.Notice;
import com.resihub.service.INoticeService;
import com.resihub.utils.RequireRole;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Resource
    private INoticeService noticeService;

    /** 公告列表，可传 type 筛选（0公告 1停水停电 2活动 3紧急） */
    @GetMapping("/list")
    public Result list(@RequestParam(value = "type", required = false) Integer type) {
        return noticeService.list(type);
    }

    /** 公告详情，同时标记已读 */
    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return noticeService.detail(id);
    }

    /** 未读数量 */
    @GetMapping("/unread")
    public Result unread() {
        return noticeService.unreadCount();
    }

    /** 一键已读 */
    @PostMapping("/read-all")
    public Result readAll() {
        return noticeService.readAll();
    }

    /** 发布公告（管家 / 管理员） */
    @PostMapping
    @RequireRole({2, 3})
    public Result publish(@RequestBody Notice notice) {
        return noticeService.publish(notice);
    }
}
