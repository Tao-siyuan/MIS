package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.SysRole;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.entity.SysUserRole;
import com.ustb.mis.mapper.SysRoleMapper;
import com.ustb.mis.mapper.SysUserMapper;
import com.ustb.mis.mapper.SysUserRoleMapper;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.service.AuditService;
import com.ustb.mis.service.AuthService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色与权限配置（RBAC），支持一人多角色。
 * 角色配置独立授权码 P20，操作日志审计为 P16，二者不再共用。
 */
@RestController
@RequestMapping("/api")
@RequirePerm("P20")
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final AuditService auditService;

    public RoleController(SysRoleMapper roleMapper, SysUserMapper userMapper,
                          SysUserRoleMapper userRoleMapper, AuditService auditService) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.auditService = auditService;
    }

    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.ok(roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getRoleId)));
    }

    @PostMapping("/roles")
    public Result<Void> saveRole(@RequestBody SysRole role) {
        if (role.getRoleId() == null || role.getRoleId().isBlank()) {
            throw new BizException("角色编号不能为空");
        }
        if (roleMapper.selectById(role.getRoleId()) == null) {
            if (role.getRoleStatus() == null) {
                role.setRoleStatus("1");
            }
            roleMapper.insert(role);
            auditService.record("01", "sys_role:" + role.getRoleId(), null, role);
        } else {
            SysRole old = roleMapper.selectById(role.getRoleId());
            roleMapper.updateById(role);
            auditService.record("02", "sys_role:" + role.getRoleId(), old, role);
        }
        return Result.ok();
    }

    @PutMapping("/roles/{id}")
    public Result<Void> updateRole(@PathVariable String id, @RequestBody SysRole role) {
        SysRole old = roleMapper.selectById(id);
        if (old == null) {
            throw new BizException("角色不存在");
        }
        role.setRoleId(id);
        roleMapper.updateById(role);
        auditService.record("02", "sys_role:" + id, old, role);
        return Result.ok();
    }

    @PutMapping("/roles/{id}/status")
    public Result<Void> roleStatus(@PathVariable String id, @RequestParam String status) {
        SysRole old = roleMapper.selectById(id);
        if (old == null) {
            throw new BizException("角色不存在");
        }
        if ("0".equals(status)) {
            long users = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getRoleId, id).eq(SysUser::getStatus, "1"));
            long links = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                    .eq(SysUserRole::getRoleId, id));
            if (users > 0 || links > 0) {
                throw new BizException("该角色下仍有账号，请先调整账号角色");
            }
        }
        SysRole update = new SysRole();
        update.setRoleId(id);
        update.setRoleStatus(status);
        roleMapper.updateById(update);
        auditService.record("02", "sys_role:" + id, Map.of("roleStatus", old.getRoleStatus()),
                Map.of("roleStatus", status));
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users(@RequestParam(required = false) String keyword) {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUserName, keyword).or().like(SysUser::getRealName, keyword))
                .orderByAsc(SysUser::getUserId));
        Map<String, String> roleNames = new HashMap<>();
        for (SysRole role : roleMapper.selectList(Wrappers.<SysRole>lambdaQuery())) {
            roleNames.put(role.getRoleId(), role.getRoleName());
        }
        Map<String, List<String>> userRoles = new HashMap<>();
        for (SysUserRole link : userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery())) {
            userRoles.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(link.getRoleId());
        }
        return Result.ok(users.stream().map(u -> {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", u.getUserId());
            item.put("userName", u.getUserName());
            item.put("realName", u.getRealName());
            item.put("roleId", u.getRoleId());
            item.put("roleName", roleNames.get(u.getRoleId()));
            List<String> roleIds = userRoles.getOrDefault(u.getUserId(), List.of());
            item.put("roleIds", roleIds);
            item.put("roleNames", roleIds.stream().map(roleNames::get).filter(n -> n != null).toList());
            item.put("collegeId", u.getCollegeId());
            item.put("classId", u.getClassId());
            item.put("teacherId", u.getTeacherId());
            item.put("status", u.getStatus());
            item.put("lastLogin", u.getLastLogin());
            return item;
        }).toList());
    }

    /** 多角色增量维护：主角色取列表首个（P1-11） */
    @PutMapping("/users/{id}/roles")
    @Transactional
    public Result<Void> changeRoles(@PathVariable String id, @RequestBody Map<String, Object> body) {
        SysUser old = userMapper.selectById(id);
        if (old == null) {
            throw new BizException("用户不存在");
        }
        @SuppressWarnings("unchecked")
        List<String> roleIds = (List<String>) body.get("roleIds");
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BizException("请至少选择一个角色");
        }
        for (String roleId : roleIds) {
            if (roleMapper.selectById(roleId) == null) {
                throw new BizException("角色不存在：" + roleId);
            }
        }
        SysUser update = new SysUser();
        update.setUserId(id);
        update.setRoleId(roleIds.get(0));
        userMapper.updateById(update);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        for (String roleId : roleIds) {
            SysUserRole link = new SysUserRole();
            link.setUserId(id);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
        auditService.record("02", "sys_user:" + id, Map.of("roleId", old.getRoleId()),
                Map.of("roleIds", roleIds));
        return Result.ok();
    }

    /** 兼容单角色接口：等价于只保留一个角色 */
    @PutMapping("/users/{id}/role")
    public Result<Void> changeRole(@PathVariable String id, @RequestParam String roleId) {
        return changeRoles(id, Map.of("roleIds", List.of(roleId)));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> userStatus(@PathVariable String id, @RequestParam String status) {
        SysUser update = new SysUser();
        update.setUserId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        auditService.record("02", "sys_user:" + id, null, Map.of("status", status));
        return Result.ok();
    }

    @PutMapping("/users/{id}/password")
    public Result<Void> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            password = defaultPassword(user.getUserId());
        }
        SysUser update = new SysUser();
        update.setUserId(id);
        update.setPassword(AuthService.hash(password));
        userMapper.updateById(update);
        auditService.record("02", "sys_user:" + id, null, Map.of("action", "resetPassword"));
        return Result.ok();
    }

    /**
     * 默认口令规则：系统管理员固定 123456；其余账号为 USTB@ + 工号/学号后 6 位。
     */
    private String defaultPassword(String userId) {
        if ("admin".equals(userId)) {
            return "123456";
        }
        if (userId == null || userId.length() <= 6) {
            return "USTB@" + userId;
        }
        return "USTB@" + userId.substring(userId.length() - 6);
    }
}
