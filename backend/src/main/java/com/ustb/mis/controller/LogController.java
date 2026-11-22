package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.AuditLog;
import com.ustb.mis.mapper.AuditLogMapper;
import com.ustb.mis.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志审计：按人员、时间、操作类型、对象组合检索。
 */
@RestController
@RequestMapping("/api/logs")
@RequirePerm("P16")
public class LogController {

    private final AuditLogMapper logMapper;

    public LogController(AuditLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String userId,
                                            @RequestParam(required = false) String operType,
                                            @RequestParam(required = false) String operObject,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<AuditLog> wrapper = Wrappers.<AuditLog>lambdaQuery()
                .like(userId != null && !userId.isBlank(), AuditLog::getUserId, userId)
                .eq(operType != null && !operType.isBlank(), AuditLog::getOperType, operType)
                .like(operObject != null && !operObject.isBlank(), AuditLog::getOperObject, operObject)
                .ge(startDate != null && !startDate.isBlank(), AuditLog::getOperTime,
                        startDate == null || startDate.isBlank() ? null : LocalDate.parse(startDate).atStartOfDay())
                .le(endDate != null && !endDate.isBlank(), AuditLog::getOperTime,
                        endDate == null || endDate.isBlank() ? null : LocalDate.parse(endDate).atTime(23, 59, 59))
                .orderByDesc(AuditLog::getOperTime);
        Page<AuditLog> result = logMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        return Result.ok(data);
    }
}
