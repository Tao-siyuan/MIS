package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * schedule_rule 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("schedule_rule")
public class ScheduleRule {
    @TableId(type = IdType.INPUT)
    private String ruleId;
    private String ruleName;
    private String ruleType;
    private String ruleContent;
    private Integer priorityNo;
    private String enable;
    private LocalDateTime updateTime;
}
