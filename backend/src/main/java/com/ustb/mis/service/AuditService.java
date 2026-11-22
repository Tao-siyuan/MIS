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

    public void record(String operType, String operObject, Object before, Object after) {
        try {
            LoginUser user = UserContext.get();
            AuditLog entry = new AuditLog();
            entry.setLogId("L" + LocalDateTime.now().format(FMT));
            entry.setUserId(user == null ? "anonymous" : user.getUserId());
            entry.setRoleId(user == null ? null : user.getRoleId());
            entry.setOperType(operType);
            entry.setOperObject(trim(operObject, 60));
            entry.setBeforeValue(trim(toJson(before), 300));
            entry.setAfterValue(trim(toJson(after), 300));
            entry.setOperResult("1");
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
