package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.SysRole;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.entity.SysUserRole;
import com.ustb.mis.mapper.SysRoleMapper;
import com.ustb.mis.mapper.SysUserMapper;
import com.ustb.mis.mapper.SysUserRoleMapper;
import com.ustb.mis.security.JwtUtil;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.security.UserLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录认证：口令摘要校验 + 令牌签发 + 角色权限装载。
 * 登录成功/失败均写审计日志，日志类型 07（登录），操作结果 1 成功 / 0 失败。
 */
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final UserLoader userLoader;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    public AuthService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, UserLoader userLoader,
                       JwtUtil jwtUtil, AuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userLoader = userLoader;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
    }

    public static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(("mis@" + raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException(500, "口令摘要计算失败");
        }
    }

    public Map<String, Object> login(String userName, String password) {
        // 登录接口被拦截器排除，线程可能复用上一请求的上下文，先显式清理（P1-10）
        UserContext.clear();
        if (userName == null || userName.isBlank() || password == null || password.isBlank()) {
            throw new BizException(400, "请输入账号和密码");
        }
        String account = userName.trim();
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUserName, account));
        if (user == null) {
            loginFailed(account, null, "账号不存在");
            throw new BizException(401, "账号或密码错误");
        }
        if (!"1".equals(user.getStatus())) {
            loginFailed(account, user.getUserId(), "账号已停用");
            throw new BizException(403, "账号已停用，请联系管理员");
        }
        if (!hash(password).equals(user.getPassword())) {
            loginFailed(account, user.getUserId(), "口令错误");
            throw new BizException(401, "账号或密码错误");
        }
        user.setLastLogin(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtil.createToken(user.getUserId(), user.getRoleId());
        LoginUser lu = userLoader.load(user.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", lu.getUserId());
        data.put("userName", lu.getUserName());
        data.put("realName", lu.getRealName());
        data.put("roleId", lu.getRoleId());
        data.put("roleName", lu.getRoleName());
        data.put("dataScope", lu.getDataScope());
        data.put("perms", new ArrayList<>(lu.getPerms()));
        data.put("roles", rolesOf(user.getUserId()));
        data.put("collegeId", lu.getCollegeId());
        data.put("majorId", lu.getMajorId());
        data.put("classId", lu.getClassId());
        data.put("teacherId", lu.getTeacherId());
        auditService.recordAs(user.getUserId(), lu.getRoleId(), "07",
                "sys_user:" + user.getUserId(), null,
                Map.of("action", "login", "account", account), "1");
        return data;
    }

    private void loginFailed(String account, String userId, String reason) {
        auditService.recordAs(userId == null ? account : userId, null, "07",
                "sys_user:" + account, null, Map.of("action", "login", "reason", reason), "0");
    }

    public void logout() {
        LoginUser user = UserContext.get();
        if (user != null) {
            auditService.recordAs(user.getUserId(), user.getRoleId(), "08",
                    "sys_user:" + user.getUserId(), null, Map.of("action", "logout"), "1");
        }
        UserContext.clear();
    }

    public List<String> rolesOf(String userId) {
        List<String> result = new ArrayList<>();
        List<SysUserRole> links = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        for (SysUserRole link : links) {
            SysRole role = roleMapper.selectById(link.getRoleId());
            if (role != null && "1".equals(role.getRoleStatus())) {
                result.add(role.getRoleName());
            }
        }
        return result;
    }

    public Map<String, Object> profile(LoginUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("userName", user.getUserName());
        data.put("realName", user.getRealName());
        data.put("roleId", user.getRoleId());
        data.put("roleName", user.getRoleName());
        data.put("dataScope", user.getDataScope());
        data.put("perms", new ArrayList<>(user.getPerms()));
        data.put("roles", rolesOf(user.getUserId()));
        data.put("collegeId", user.getCollegeId());
        data.put("majorId", user.getMajorId());
        data.put("classId", user.getClassId());
        data.put("teacherId", user.getTeacherId());
        return data;
    }
}
