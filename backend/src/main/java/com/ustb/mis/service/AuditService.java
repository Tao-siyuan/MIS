package com.ustb.mis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.mis.entity.AuditLog;
import com.ustb.mis.mapper.AuditLogMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志：关键操作留痕，只追加、不修改（对应 3.5.8 支撑模块）。
 * 操作类型：01新增 / 02修改 / 03删除 / 04审批 / 05发布 / 06导出 / 07登录 / 08登出。
 * 操作结果：1成功 / 0失败。
 */
@Slf4j
@Service
public class AuditService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /** 以当前登录用户身份记录 */
    public void record(String operType, String operObject, Object before, Object after) {
        LoginUser user = UserContext.get();
        recordAs(user == null ? "anonymous" : user.getUserId(),
                user == null ? null : user.getRoleId(),
                operType, operObject, before, after, "1");
    }

    /** 指定操作人记录（登录/登出等上下文尚未建立或已清理的场景） */
    public void recordAs(String userId, String roleId, String operType, String operObject,
                         Object before, Object after, String operResult) {
        try {
            AuditLog entry = new AuditLog();
            entry.setLogId("L" + LocalDateTime.now().format(FMT));
            entry.setUserId(userId == null || userId.isBlank() ? "anonymous" : userId);
            entry.setRoleId(roleId);
            entry.setOperType(operType);
            entry.setOperObject(trim(operObject, 60));
            entry.setBeforeValue(trim(toJson(before), 300));
            entry.setAfterValue(trim(toJson(after), 300));
            entry.setOperResult(operResult == null ? "1" : operResult);
            entry.setClientIp(clientIp());
            entry.setOperTime(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String trim(String s, int len) {
        return s == null ? null : (s.length() > len ? s.substring(0, len) : s);
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.getRemoteAddr();
            }
            return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
        } catch (Exception e) {
            return null;
        }
    }
}
