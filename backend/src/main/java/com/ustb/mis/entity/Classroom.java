package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * classroom 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("classroom")
public class Classroom {
    @TableId(type = IdType.INPUT)
    private String roomId;
    private String roomName;
    private String building;
    private Integer capacity;
    private String roomType;
    private String availableTime;
    private String roomStatus;
}
