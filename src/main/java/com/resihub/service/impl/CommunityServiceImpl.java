package com.resihub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.Community;
import com.resihub.mapper.CommunityMapper;
import com.resihub.service.ICommunityService;
import com.resihub.utils.CacheClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.resihub.utils.RedisConstants.CACHE_COMMUNITY_KEY;
import static com.resihub.utils.RedisConstants.CACHE_COMMUNITY_TTL;

@Service
public class CommunityServiceImpl extends ServiceImpl<CommunityMapper, Community> implements ICommunityService {

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result listAll() {
        List<Community> list = query().eq("status", 1).orderByAsc("id").list();
        return Result.ok(list);
    }

    @Override
    public Result detail(Long id) {
        // 空值缓存解决穿透
        Community community = cacheClient.queryWithPassThrough(
                CACHE_COMMUNITY_KEY, id, Community.class, this::getById,
                CACHE_COMMUNITY_TTL, TimeUnit.MINUTES);
        if (community == null) {
            return Result.fail("小区不存在");
        }
        return Result.ok(community);
    }
}
