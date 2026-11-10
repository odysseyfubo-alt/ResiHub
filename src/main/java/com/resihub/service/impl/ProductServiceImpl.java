package com.resihub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.resihub.dto.Result;
import com.resihub.entity.Product;
import com.resihub.mapper.ProductMapper;
import com.resihub.service.IProductService;
import com.resihub.utils.CacheClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.resihub.utils.RedisConstants.CACHE_COMMUNITY_KEY;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    private static final String CACHE_PRODUCT_KEY = CACHE_COMMUNITY_KEY + "product:";

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryList() {
        Long communityId = com.resihub.utils.UserHolder.getCommunityId();
        if (communityId == null) {
            return Result.fail("请先选择小区");
        }
        List<Product> list = query()
                .eq("community_id", communityId)
                .eq("status", 1)
                .orderByAsc("id")
                .list();
        return Result.ok(list, (long) list.size());
    }

    @Override
    public Result detail(Long id) {
        // 逻辑过期 + 异步重建，商品详情是本项目真正的热点
        String key = CACHE_PRODUCT_KEY + id;
        Product product = cacheClient.queryWithLogicalExpire(
                key, id, Product.class, this::getById, 20L, TimeUnit.SECONDS);
        if (product == null) {
            // 首次访问：回源并预热（逻辑过期方案需要先有旧值，才能"过期了先返旧值"）
            product = getById(id);
            if (product == null) {
                return Result.fail("商品不存在");
            }
            cacheClient.setWithLogicalExpire(key, product, 20L, TimeUnit.SECONDS);
        }
        return Result.ok(product);
    }
}
