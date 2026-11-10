package com.resihub.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletResponse;

/**
 * 登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(javax.servlet.http.HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
