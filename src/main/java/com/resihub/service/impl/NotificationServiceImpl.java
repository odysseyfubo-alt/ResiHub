package com.resihub.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.Notification;
import com.resihub.entity.User;
import com.resihub.mapper.NotificationMapper;
import com.resihub.mapper.UserMapper;
import com.resihub.service.INotificationService;
import com.resihub.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    @Resource
    private UserMapper userMapper;

    @Override
    public void send(Long userId, Long communityId, String title, String content, String type, Long bizId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification()
                .setUserId(userId)
                .setCommunityId(communityId == null ? 0L : communityId)
                .setTitle(title)
                .setContent(content)
                .setType(type)
                .setBizId(bizId)
                .setReadFlag(0);
        save(n);
    }

    @Override
    public void sendToRole(Long communityId, int role, String title, String content, String type, Long bizId) {
        if (communityId == null) {
            return;
        }
        List<User> users = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("community_id", communityId)
                        .eq("role", role)
                        .eq("status", 1));
        for (User u : users) {
            send(u.getId(), communityId, title, content, type, bizId);
        }
    }

    @Override
    public Result list(Integer page) {
        Long userId = UserHolder.getUserId();
        Page<Notification> p = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(page == null ? 1 : page, 20));
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result unreadCount() {
        long count = query().eq("user_id", UserHolder.getUserId()).eq("read_flag", 0).count();
        return Result.ok(count);
    }

    @Override
    public Result readAll() {
        update().eq("user_id", UserHolder.getUserId()).eq("read_flag", 0).set("read_flag", 1).update();
        return Result.ok();
    }
}
