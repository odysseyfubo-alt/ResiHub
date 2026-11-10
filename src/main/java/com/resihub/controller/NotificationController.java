package com.resihub.controller;

import com.resihub.dto.Result;
import com.resihub.service.INotificationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Resource
    private INotificationService notificationService;

    @GetMapping("/list")
    public Result list(@RequestParam(value = "page", defaultValue = "1") Integer page) {
        return notificationService.list(page);
    }

    @GetMapping("/unread")
    public Result unread() {
        return notificationService.unreadCount();
    }

    @PostMapping("/read-all")
    public Result readAll() {
        return notificationService.readAll();
    }
}
