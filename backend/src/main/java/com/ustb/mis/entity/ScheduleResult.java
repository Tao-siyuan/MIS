package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * schedule_result 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("schedule_result")
public class ScheduleResult {
    @TableId(type = IdType.INPUT)
    private String schedId;
    private String tcId;
    private String roomId;
    private String weekRange;
    private String weekDay;
    private Integer startSlot;
    private Integer endSlot;
    private String schedStatus;
    private String lockFlag;
    private String conflictDesc;
}
