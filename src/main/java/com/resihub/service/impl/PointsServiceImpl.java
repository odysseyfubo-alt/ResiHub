package com.resihub.service.impl;

import com.resihub.dto.Result;
import com.resihub.service.IPointsService;
import com.resihub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.resihub.utils.RedisConstants.SIGN_KEY;

@Slf4j
@Service
public class PointsServiceImpl implements IPointsService {

    /** 每日签到积分 */
    private static final int SIGN_POINTS = 2;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public void addPoints(Long userId, int points, String type, String remark) {
        if (userId == null || points == 0) {
            return;
        }
        jdbcTemplate.update("update tb_user set points = points + ? where id = ?", points, userId);
        jdbcTemplate.update("insert into tb_points_log(user_id, points, type, remark) values (?,?,?,?)",
                userId, points, type, remark);
        log.debug("积分变动 userId={}, points={}, type={}", userId, points, type);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUserId();
        String key = signKey(userId);
        int day = LocalDate.now().getDayOfMonth();
        Boolean signed = stringRedisTemplate.opsForValue().getBit(key, day - 1);
        if (Boolean.TRUE.equals(signed)) {
            return Result.fail("今天已经签到过了");
        }
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        addPoints(userId, SIGN_POINTS, "sign", "每日签到");
        int continuous = continuousDays(userId);
        return Result.ok(continuous);
    }

    @Override
    public Result signCount() {
        return Result.ok(continuousDays(UserHolder.getUserId()));
    }

    @Override
    public Result log() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select points, type, remark, create_time from tb_points_log where user_id = ? order by create_time desc limit 50",
                UserHolder.getUserId());
        return Result.ok(rows, (long) rows.size());
    }

    @Override
    public Result total() {
        Integer points = jdbcTemplate.queryForObject(
                "select points from tb_user where id = ?", Integer.class, UserHolder.getUserId());
        return Result.ok(points == null ? 0 : points);
    }

    /** 本月连续签到天数（从今天往前数连续的 1） */
    private int continuousDays(Long userId) {
        String key = signKey(userId);
        int day = LocalDate.now().getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day))
                        .valueAt(0));
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0;
        }
        long num = result.get(0);
        int count = 0;
        while (num > 0 && (num & 1) == 1) {
            count++;
            num >>= 1;
        }
        return count;
    }

    private String signKey(Long userId) {
        return SIGN_KEY + userId + ":" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
}
