package com.resihub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.dto.UserDTO;
import com.resihub.entity.Notice;
import com.resihub.entity.User;
import com.resihub.mapper.NoticeMapper;
import com.resihub.service.INoticeService;
import com.resihub.service.IUserService;
import com.resihub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.resihub.utils.RedisConstants.NOTICE_READ_KEY;

@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements INoticeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /** 当前用户可见的公告（本小区 + 全小区或本楼栋定向） */
    private List<Notice> visibleNotices(Integer type) {
        UserDTO user = UserHolder.getUser();
        Long communityId = user.getCommunityId();
        if (communityId == null) {
            return java.util.Collections.emptyList();
        }
        String building = user.getBuilding();
        return query()
                .eq("community_id", communityId)
                .and(w -> w.isNull("building_target").or().eq("building_target", building))
                .eq(type != null, "type", type)
                .orderByDesc("top")
                .orderByDesc("create_time")
                .list();
    }

    private Set<Object> readIds(Long userId) {
        return stringRedisTemplate.opsForHash().keys(NOTICE_READ_KEY + userId);
    }

    @Override
    public Result list(Integer type) {
        UserDTO user = UserHolder.getUser();
        if (user.getCommunityId() == null) {
            return Result.fail("请先选择小区");
        }
        List<Notice> notices = visibleNotices(type);
        Set<String> readSet = readIds(user.getId()).stream().map(Object::toString).collect(Collectors.toSet());
        // 发布人昵称，避免 N+1
        Map<Long, String> nameCache = new HashMap<>();
        for (Notice n : notices) {
            n.setReadFlag(readSet.contains(n.getId().toString()));
            n.setPublisherName(nameCache.computeIfAbsent(n.getPublisherId(), pid -> {
                User u = userService.getById(pid);
                return u == null ? "系统" : u.getNickName();
            }));
        }
        return Result.ok(notices, (long) notices.size());
    }

    @Override
    public Result detail(Long id) {
        Notice notice = getById(id);
        if (notice == null) {
            return Result.fail("公告不存在");
        }
        UserDTO user = UserHolder.getUser();
        if (user.getCommunityId() != null && !Objects.equals(notice.getCommunityId(), user.getCommunityId())) {
            return Result.fail("公告不存在");
        }
        stringRedisTemplate.opsForHash().put(NOTICE_READ_KEY + user.getId(), id.toString(), "1");
        notice.setReadFlag(true);
        User publisher = userService.getById(notice.getPublisherId());
        notice.setPublisherName(publisher == null ? "系统" : publisher.getNickName());
        return Result.ok(notice);
    }

    @Override
    public Result publish(Notice notice) {
        UserDTO user = UserHolder.getUser();
        if (user.getCommunityId() == null) {
            return Result.fail("请先选择小区");
        }
        if (notice.getTitle() == null || notice.getTitle().trim().isEmpty()) {
            return Result.fail("标题不能为空");
        }
        notice.setId(null);
        notice.setCommunityId(user.getCommunityId());
        notice.setPublisherId(user.getId());
        if (notice.getType() == null) {
            notice.setType(0);
        }
        if (notice.getTop() == null) {
            notice.setTop(0);
        }
        save(notice);
        return Result.ok(notice.getId());
    }

    @Override
    public Result unreadCount() {
        UserDTO user = UserHolder.getUser();
        if (user.getCommunityId() == null) {
            return Result.ok(0);
        }
        List<Notice> notices = visibleNotices(null);
        Set<String> readSet = readIds(user.getId()).stream().map(Object::toString).collect(Collectors.toSet());
        long unread = notices.stream().filter(n -> !readSet.contains(n.getId().toString())).count();
        return Result.ok(unread);
    }

    @Override
    public Result readAll() {
        UserDTO user = UserHolder.getUser();
        List<Notice> notices = visibleNotices(null);
        if (!notices.isEmpty()) {
            Map<String, String> map = new HashMap<>();
            notices.forEach(n -> map.put(n.getId().toString(), "1"));
            stringRedisTemplate.opsForHash().putAll(NOTICE_READ_KEY + user.getId(), map);
        }
        return Result.ok();
    }
}
