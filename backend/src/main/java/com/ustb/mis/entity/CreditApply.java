package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * credit_apply 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("credit_apply")
public class CreditApply {
    @TableId(type = IdType.INPUT)
    private String applyId;
    private String stuId;
    private String fromMajor;
    private String toMajor;
    private LocalDate applyDate;
    private BigDecimal applyCredit;
    private String collegeOpinion;
    private String auditStatus;
    private String approver;
    private String auditOpinion;
    private LocalDate auditDate;
}
