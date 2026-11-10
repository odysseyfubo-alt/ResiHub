package com.resihub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.LoginFormDTO;
import com.resihub.dto.Result;
import com.resihub.dto.UserDTO;
import com.resihub.entity.User;
import com.resihub.mapper.UserMapper;
import com.resihub.service.INotificationService;
import com.resihub.service.IUserService;
import com.resihub.utils.RegexUtils;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.resihub.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.resihub.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.resihub.utils.RedisConstants.LOGIN_USER_KEY;
import static com.resihub.utils.RedisConstants.LOGIN_USER_TOKENS_KEY;
import static com.resihub.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private INotificationService notificationService;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("【ResiHub】发送短信验证码成功，phone={}，code={}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO form) {
        String phone = form.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(form.getCode())) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = new User()
                    .setPhone(phone)
                    .setNickName("业主" + phone.substring(7))
                    .setIcon("")
                    .setRole(0)
                    .setAudit(0)
                    .setPoints(0)
                    .setStatus(1);
            save(user);
            log.info("新用户注册 phone={}, id={}", phone, user.getId());
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return Result.fail("账号已被封禁");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        UserDTO dto = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(dto, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 记录该用户的全部 token，便于认证/角色变更后同步会话
        stringRedisTemplate.opsForSet().add(LOGIN_USER_TOKENS_KEY + user.getId(), token);
        stringRedisTemplate.expire(LOGIN_USER_TOKENS_KEY + user.getId(), LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 验证码一次性使用
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return Result.ok(token);
    }

    @Override
    public Result me() {
        User user = getById(UserHolder.getUserId());
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    @Override
    public Result apply(Long communityId, String building, String room) {
        Long userId = UserHolder.getUserId();
        if (communityId == null || building == null || building.trim().isEmpty()) {
            return Result.fail("请选择小区并填写楼栋");
        }
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        User update = new User()
                .setId(userId)
                .setCommunityId(communityId)
                .setBuilding(building.trim())
                .setRoom(room == null ? null : room.trim())
                .setAudit(0);
        updateById(update);
        syncSession(userId);
        notificationService.sendToRole(communityId, 2, "新的业主认证申请",
                (user.getNickName() == null ? user.getPhone() : user.getNickName())
                        + " 申请认证 " + building + (room == null ? "" : " " + room), "notice", null);
        return Result.ok();
    }

    @Override
    public Result pendingAudits() {
        Long communityId = UserHolder.getCommunityId();
        List<User> list = query()
                .eq(communityId != null, "community_id", communityId)
                .eq("audit", 0)
                .orderByAsc("create_time")
                .list();
        List<UserDTO> dtos = list.stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(dtos);
    }

    @Override
    @Transactional
    public Result audit(Long userId, boolean pass) {
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        Long communityId = UserHolder.getCommunityId();
        if (communityId != null && user.getCommunityId() != null && !communityId.equals(user.getCommunityId())) {
            return Result.fail("无权审核其他小区用户");
        }
        updateById(new User().setId(userId).setAudit(pass ? 1 : 2));
        syncSession(userId);
        notificationService.send(userId, user.getCommunityId(),
                pass ? "业主认证已通过" : "业主认证被驳回",
                pass ? "认证通过，现在可以使用报修、团购等全部服务。" : "认证被驳回，请核对楼栋房号后重新提交。",
                "notice", null);
        return Result.ok();
    }

    @Override
    public void assertAudited() {
        User user = getById(UserHolder.getUserId());
        if (user == null || user.getAudit() == null || user.getAudit() != 1) {
            throw new RuntimeException("请先完成业主认证");
        }
    }
    /**
     * 会话同步：业主认证/角色变化后，把该用户所有在线 token 里缓存的用户信息刷一遍，
     * 否则服务端按旧 token 判断归属小区/认证状态会出错。
     */
    private void syncSession(Long userId) {
        User user = getById(userId);
        if (user == null) {
            return;
        }
        Set<String> tokens = stringRedisTemplate.opsForSet().members(LOGIN_USER_TOKENS_KEY + userId);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        UserDTO dto = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> raw = BeanUtil.beanToMap(dto, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor(
                        (fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        Map<String, String> toWrite = new HashMap<>();
        raw.forEach((k, v) -> {
            if (v != null) {
                toWrite.put(k, v.toString());
            }
        });
        for (String token : tokens) {
            String key = LOGIN_USER_KEY + token;
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                stringRedisTemplate.opsForSet().remove(LOGIN_USER_TOKENS_KEY + userId, token);
                continue;
            }
            stringRedisTemplate.opsForHash().putAll(key, toWrite);
            stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        }
    }
}
