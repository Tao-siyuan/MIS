package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * teaching_class 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("teaching_class")
public class TeachingClass {
    @TableId(type = IdType.INPUT)
    private String tcId;
    private String term;
    private String courseId;
    private String teacherId;
    private String classIds;
    private Integer studentNum;
    private Integer capacity;
    private String tcType;
    private String teachType;
    private Integer hours;
    private Integer theoryHours;
    private Integer practiceHours;
    private Integer weekCount;
    private Integer weekStart;
    private String preferSlot;
    private String unavailableTime;
    private String multiclassPrefer;
    private String tcStatus;
    private String source;
    private Integer actualNum;
    private Integer openLower;
    private String noOpenReason;
}
