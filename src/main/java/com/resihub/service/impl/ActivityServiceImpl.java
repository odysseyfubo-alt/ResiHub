package com.resihub.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.Activity;
import com.resihub.entity.ActivitySignup;
import com.resihub.mapper.ActivityMapper;
import com.resihub.mapper.ActivitySignupMapper;
import com.resihub.service.IActivityService;
import com.resihub.service.IPointsService;
import com.resihub.service.IUserService;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    @Resource
    private ActivitySignupMapper signupMapper;
    @Resource
    private IUserService userService;
    @Resource
    private IPointsService pointsService;

    @Override
    public Result queryList() {
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        List<Activity> list = query()
                .eq("community_id", communityId)
                .orderByAsc("status")
                .orderByAsc("start_time")
                .list();
        Long userId = UserHolder.getUserId();
        for (Activity a : list) {
            ActivitySignup su = signupMapper.selectOne(new QueryWrapper<ActivitySignup>()
                    .eq("activity_id", a.getId()).eq("user_id", userId));
            a.setJoined(su != null);
            if (su != null) {
                a.setMyCode(su.getCode());
            }
        }
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result create(Activity activity) {
        Long communityId = UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        if (activity.getTitle() == null || activity.getTitle().trim().isEmpty()) {
            return Result.fail("请填写活动标题");
        }
        if (activity.getQuota() == null || activity.getQuota() < 1) {
            return Result.fail("请设置名额");
        }
        activity.setId(null);
        activity.setCommunityId(communityId);
        activity.setSigned(0);
        activity.setStatus(1);
        if (activity.getStartTime() == null) {
            activity.setStartTime(LocalDateTime.now().plusDays(3));
        }
        if (activity.getEndTime() == null) {
            activity.setEndTime(activity.getStartTime().plusHours(3));
        }
        save(activity);
        return Result.ok(activity.getId());
    }

    @Override
    public Result signup(Long id) {
        userService.assertAudited();
        Activity activity = getById(id);
        if (activity == null) {
            return Result.fail("活动不存在");
        }
        if (!Objects.equals(activity.getCommunityId(), UserHolder.getCommunityId())) {
            return Result.fail("活动不存在");
        }
        if (activity.getStatus() == null || activity.getStatus() != 1) {
            return Result.fail("活动已结束");
        }
        // 名额限流：条件更新，只有 signed < quota 才能 +1
        boolean ok = update()
                .setSql("signed = signed + 1")
                .eq("id", id)
                .eq("status", 1)
                .apply("signed < quota")
                .update();
        if (!ok) {
            return Result.fail("名额已满");
        }
        Long userId = UserHolder.getUserId();
        String code = RandomUtil.randomNumbers(6);
        ActivitySignup signup = new ActivitySignup()
                .setActivityId(id).setUserId(userId).setCode(code).setUsed(0);
        try {
            signupMapper.insert(signup);
        } catch (DuplicateKeyException e) {
            update().setSql("signed = signed - 1").eq("id", id).gt("signed", 0).update();
            return Result.fail("你已经报名过该活动");
        }
        pointsService.addPoints(userId, 3, "activity", "参与社区活动：" + activity.getTitle());
        log.info("活动报名成功 activityId={}, userId={}, code={}", id, userId, code);
        return Result.ok(code);
    }

    @Override
    public Result verify(String code) {
        ActivitySignup signup = signupMapper.selectOne(new QueryWrapper<ActivitySignup>()
                .eq("code", code));
        if (signup == null) {
            return Result.fail("核销码不存在");
        }
        if (signup.getUsed() != null && signup.getUsed() == 1) {
            return Result.fail("该码已核销");
        }
        int updated = signupMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ActivitySignup>()
                .set("used", 1).eq("id", signup.getId()).eq("used", 0));
        if (updated == 0) {
            return Result.fail("核销失败，该码可能已被使用");
        }
        return Result.ok();
    }

    @Override
    public Result myActivities() {
        List<ActivitySignup> signups = signupMapper.selectList(new QueryWrapper<ActivitySignup>()
                .eq("user_id", UserHolder.getUserId())
                .orderByDesc("create_time"));
        List<Activity> activities = new java.util.ArrayList<>();
        for (ActivitySignup s : signups) {
            Activity a = getById(s.getActivityId());
            if (a != null) {
                a.setJoined(true);
                a.setMyCode(s.getCode());
                activities.add(a);
            }
        }
        return Result.ok(activities, (long) activities.size());
    }
}
