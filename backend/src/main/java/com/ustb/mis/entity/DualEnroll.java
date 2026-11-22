package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * dual_enroll 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("dual_enroll")
public class DualEnroll {
    @TableId(type = IdType.INPUT)
    private String enrollId;
    private String stuId;
    private String majorId;
    private String enrollYear;
    private LocalDate applyDate;
    private BigDecimal scoreAvg;
    private String auditStatus;
    private String finishStatus;
    private LocalDate enrollDate;
    private String remark;
}
