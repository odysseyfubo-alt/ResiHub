package com.resihub.utils;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 角色拦截器：校验 @RequireRole
 */
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod hm = (HandlerMethod) handler;
        RequireRole require = hm.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require == null) {
            return true;
        }
        Integer role = UserHolder.getRole();
        if (role == null) {
            response.setStatus(401);
            return false;
        }
        for (int allowed : require.value()) {
            if (allowed == role) {
                return true;
            }
        }
        response.setStatus(403);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"success\":false,\"errorMsg\":\"没有权限\"}");
        return false;
    }
}
