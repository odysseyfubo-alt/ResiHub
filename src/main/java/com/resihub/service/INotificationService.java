package com.resihub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.resihub.dto.Result;
import com.resihub.entity.Notification;

public interface INotificationService extends IService<Notification> {

    /** 发一条站内消息 */
    void send(Long userId, Long communityId, String title, String content, String type, Long bizId);

    /** 给同一小区某角色的所有人发消息 */
    void sendToRole(Long communityId, int role, String title, String content, String type, Long bizId);

    Result list(Integer page);

    Result unreadCount();

    Result readAll();
}
