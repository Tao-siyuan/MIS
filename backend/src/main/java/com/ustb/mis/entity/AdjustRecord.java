package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * adjust_record 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("adjust_record")
public class AdjustRecord {
    @TableId(type = IdType.INPUT)
    private String adjustId;
    private String schedId;
    private String adjustType;
    private String oldValue;
    private String newValue;
    private String oldRoomId;
    private String oldWeekDay;
    private Integer oldStartSlot;
    private Integer oldEndSlot;
    private String oldWeekRange;
    private String newRoomId;
    private String newWeekDay;
    private Integer newStartSlot;
    private Integer newEndSlot;
    private String reason;
    private LocalDate effectDate;
    private String approveStatus;
    private String approver;
    private String approveOpinion;
    private String restoreFlag;
    private String notifyStatus;
    private LocalDateTime createTime;
}
