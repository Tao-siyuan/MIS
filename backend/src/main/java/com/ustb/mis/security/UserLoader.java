package com.ustb.mis.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.SysRole;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.mapper.SysRoleMapper;
import com.ustb.mis.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据用户编号装配登录上下文（角色、功能权限、数据范围）。
 */
@Component
public class UserLoader {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public UserLoader(SysUserMapper userMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
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
        SysRole role = roleMapper.selectById(user.getRoleId());
        if (role == null || !"1".equals(role.getRoleStatus())) {
            throw new BizException(403, "角色已停用，请联系管理员");
        }
        LoginUser lu = new LoginUser();
        lu.setUserId(user.getUserId());
        lu.setUserName(user.getUserName());
        lu.setRealName(user.getRealName());
        lu.setRoleId(role.getRoleId());
        lu.setRoleName(role.getRoleName());
        lu.setDataScope(role.getDataScope());
        lu.setCollegeId(user.getCollegeId());
        lu.setMajorId(user.getMajorId());
        lu.setClassId(user.getClassId());
        lu.setTeacherId(user.getTeacherId());
        Set<String> perms = new HashSet<>();
        if (role.getFuncPerm() != null && !role.getFuncPerm().isBlank()) {
            perms = Arrays.stream(role.getFuncPerm().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        lu.setPerms(perms);
        return lu;
    }
}
