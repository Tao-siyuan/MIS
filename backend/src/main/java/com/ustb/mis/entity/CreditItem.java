package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * credit_item 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("credit_item")
public class CreditItem {
    private String applyId;
    @TableId(type = IdType.INPUT)
    private Integer seqNo;
    private String sourceCourseId;
    private BigDecimal sourceCredit;
    private String targetCourseId;
    private BigDecimal targetCredit;
    private String transferType;
    private String itemStatus;
}
