package com.ustb.mis.security;

import com.ustb.mis.common.BizException;

/**
 * 以 ThreadLocal 保存当前请求的登录用户。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
