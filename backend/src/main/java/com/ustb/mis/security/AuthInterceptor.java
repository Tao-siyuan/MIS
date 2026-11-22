package com.ustb.mis.security;

import com.ustb.mis.common.BizException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 令牌校验 + 功能权限校验（对应设计文档：越权访问直接拦截）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserLoader userLoader;

    public AuthInterceptor(JwtUtil jwtUtil, UserLoader userLoader) {
        this.jwtUtil = jwtUtil;
        this.userLoader = userLoader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            token = request.getHeader("token");
        }
        if (token == null || token.isBlank()) {
            throw new BizException(401, "未登录或登录已过期");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        LoginUser user = userLoader.load(claims.getSubject());
        UserContext.set(user);

        RequirePerm requirePerm = null;
        if (handler instanceof HandlerMethod hm) {
            requirePerm = hm.getMethodAnnotation(RequirePerm.class);
            if (requirePerm == null) {
                requirePerm = hm.getBeanType().getAnnotation(RequirePerm.class);
            }
        }
        if (requirePerm != null) {
            boolean pass = Arrays.stream(requirePerm.value()).anyMatch(user::hasPerm);
            if (!pass) {
                throw new BizException(403, "无该功能权限，请联系管理员授权");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
