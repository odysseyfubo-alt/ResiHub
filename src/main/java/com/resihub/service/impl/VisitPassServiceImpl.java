package com.resihub.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.VisitPass;
import com.resihub.mapper.VisitPassMapper;
import com.resihub.service.IUserService;
import com.resihub.service.IVisitPassService;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.resihub.utils.RedisConstants.VISIT_PASS_KEY;

@Slf4j
@Service
public class VisitPassServiceImpl extends ServiceImpl<VisitPassMapper, VisitPass> implements IVisitPassService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT;

    static {
        VERIFY_SCRIPT = new DefaultRedisScript<>();
        VERIFY_SCRIPT.setLocation(new ClassPathResource("lua/verify_pass.lua"));
        VERIFY_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result create(VisitPass form, Integer hours) {
        userService.assertAudited();
        Long communityId = UserHolder.getCommunityId();
        if (form.getVisitorName() == null || form.getVisitorName().trim().isEmpty()) {
            return Result.fail("请填写访客姓名");
        }
        if (form.getVisitorPhone() == null || form.getVisitorPhone().trim().isEmpty()) {
            return Result.fail("请填写访客手机号");
        }
        int h = hours == null ? 24 : Math.max(1, Math.min(72, hours));
        String code = RandomUtil.randomNumbers(8);
        LocalDateTime expire = LocalDateTime.now().plusHours(h);
        VisitPass pass = new VisitPass()
                .setCommunityId(communityId)
                .setUserId(UserHolder.getUserId())
                .setVisitorName(form.getVisitorName().trim())
                .setVisitorPhone(form.getVisitorPhone().trim())
                .setCode(code)
                .setExpireTime(expire)
                .setUsed(0);
        save(pass);
        // Redis 里放一份一次性凭证，带 TTL
        stringRedisTemplate.opsForValue().set(VISIT_PASS_KEY + code, "0", h, TimeUnit.HOURS);
        log.info("生成访客码 code={}, 有效期 {} 小时", code, h);
        return Result.ok(code);
    }

    @Override
    public Result verify(String code) {
        Long r = stringRedisTemplate.execute(VERIFY_SCRIPT,
                Collections.singletonList(VISIT_PASS_KEY + code));
        int v = r == null ? -1 : r.intValue();
        if (v == 0) {
            return Result.fail("通行码不存在或已过期");
        }
        if (v == 2) {
            return Result.fail("该通行码已使用过");
        }
        if (v != 1) {
            return Result.fail("核销失败");
        }
        int updated = getBaseMapper().update(null, new UpdateWrapper<VisitPass>()
                .set("used", 1).set("use_time", LocalDateTime.now())
                .eq("code", code).eq("used", 0));
        if (updated == 0) {
            return Result.fail("核销失败，通行码状态异常");
        }
        VisitPass pass = query().eq("code", code).one();
        return Result.ok(pass == null ? null : pass.getVisitorName());
    }

    @Override
    public Result myPasses() {
        List<VisitPass> list = query()
                .eq("user_id", UserHolder.getUserId())
                .orderByDesc("create_time")
                .list();
        return Result.ok(list, (long) list.size());
    }
}
