package com.ustb.mis.security;

import lombok.Data;

import java.util.Set;

/**
 * 登录上下文：功能权限 + 数据范围（全校/本学院/本人）。
 */
@Data
public class LoginUser {

    private String userId;
    private String userName;
    private String realName;
    private String roleId;
    private String roleName;
    private String dataScope;
    private Set<String> perms;
    private String collegeId;
    private String majorId;
    private String classId;
    private String teacherId;

    public boolean hasPerm(String code) {
        return perms != null && perms.contains(code);
    }
}
