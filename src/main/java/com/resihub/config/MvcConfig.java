package com.resihub.config;

import com.resihub.utils.LoginInterceptor;
import com.resihub.utils.RefreshTokenInterceptor;
import com.resihub.utils.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 2. 角色校验（在登录校验之后）
        registry.addInterceptor(new RoleInterceptor()).addPathPatterns("/**").order(2);

        // 1. 登录校验
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/code/peek",
                        "/user/login",
                        "/community/list",
                        "/error",
                        // 前端静态资源（index.html / css / js / 图片）不参与登录校验
                        "/",
                        "/index.html",
                        "/css/**",
                        "/js/**",
                        "/imgs/**",
                        "/favicon.ico"
                ).order(1);

        // 0. token 刷新
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
    }
}
