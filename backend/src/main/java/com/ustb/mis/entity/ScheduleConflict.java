package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * schedule_conflict 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("schedule_conflict")
public class ScheduleConflict {
    @TableId(type = IdType.INPUT)
    private String conflictId;
    private String tcId;
    private String otherTcId;
    private String conflictType;
    private String conflictDesc;
    private String suggestPlan;
    private String handleStatus;
    private LocalDateTime foundTime;
}
