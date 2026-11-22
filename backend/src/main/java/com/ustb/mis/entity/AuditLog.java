package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * audit_log 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.INPUT)
    private String logId;
    private String userId;
    private String roleId;
    private String operType;
    private String operObject;
    private String beforeValue;
    private String afterValue;
    private String operResult;
    private String clientIp;
    private LocalDateTime operTime;
}
