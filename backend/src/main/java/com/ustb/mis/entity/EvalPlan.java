package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * eval_plan 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("eval_plan")
public class EvalPlan {
    @TableId(type = IdType.INPUT)
    private String planId;
    private String term;
    private String planName;
    private String indicatorJson;
    private LocalDate openDate;
    private LocalDate closeDate;
    private String anonymity;
    private Integer minSample;
    private String planStatus;
}
