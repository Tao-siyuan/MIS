package com.ustb.mis.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.SysRole;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.entity.SysUserRole;
import com.ustb.mis.mapper.SysRoleMapper;
import com.ustb.mis.mapper.SysUserMapper;
import com.ustb.mis.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据用户编号装配登录上下文（角色、功能权限、数据范围）。
 * 支持一人多角色：权限取并集，数据范围取最宽（全校 &gt; 本学院 &gt; 本人）。
 */
@Component
public class UserLoader {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    public UserLoader(SysUserMapper userMapper, SysRoleMapper roleMapper,
                      SysUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public SysUser findUser(String userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        if (!"1".equals(user.getStatus())) {
            throw new BizException(403, "账号已停用，请联系管理员");
        }
        return user;
    }

    public LoginUser load(String userId) {
        SysUser user = findUser(userId);
        List<SysRole> roles = activeRoles(user);
        if (roles.isEmpty()) {
            throw new BizException(403, "角色已停用，请联系管理员");
        }
        Set<String> perms = new HashSet<>();
        String scope = null;
        Set<String> roleNames = new LinkedHashSet<>();
        for (SysRole role : roles) {
            roleNames.add(role.getRoleName());
            if (role.getFuncPerm() != null && !role.getFuncPerm().isBlank()) {
                perms.addAll(Arrays.stream(role.getFuncPerm().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()));
            }
            scope = widerScope(scope, role.getDataScope());
        }
        SysRole primary = roles.stream()
                .filter(r -> r.getRoleId().equals(user.getRoleId()))
                .findFirst().orElse(roles.get(0));
        LoginUser lu = new LoginUser();
        lu.setUserId(user.getUserId());
        lu.setUserName(user.getUserName());
        lu.setRealName(user.getRealName());
        lu.setRoleId(primary.getRoleId());
        lu.setRoleName(String.join("、", roleNames));
        lu.setDataScope(scope);
        lu.setCollegeId(user.getCollegeId());
        lu.setMajorId(user.getMajorId());
        lu.setClassId(user.getClassId());
        lu.setTeacherId(user.getTeacherId());
        lu.setPerms(perms);
        return lu;
    }

    /** 汇总 sys_user_role 中全部在用角色；若关联表为空则退回 sys_user.role_id */
    private List<SysRole> activeRoles(SysUser user) {
        List<SysUserRole> links = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, user.getUserId()));
        List<SysRole> roles = new ArrayList<>();
        for (SysUserRole link : links) {
            SysRole role = roleMapper.selectById(link.getRoleId());
            if (role != null && "1".equals(role.getRoleStatus())) {
                roles.add(role);
            }
        }
        if (roles.isEmpty() && user.getRoleId() != null) {
            SysRole role = roleMapper.selectById(user.getRoleId());
            if (role != null && "1".equals(role.getRoleStatus())) {
                roles.add(role);
            }
        }
        return roles;
    }

    /** 数据范围：1 全校 &gt; 2 本学院 &gt; 3 本人，取最宽 */
    private String widerScope(String current, String next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        return current.compareTo(next) <= 0 ? current : next;
    }
}
