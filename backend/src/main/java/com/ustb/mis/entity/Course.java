package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * course 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.INPUT)
    private String courseId;
    private String courseName;
    private String courseType;
    private BigDecimal credit;
    private Integer totalHours;
    private String assessType;
    private String openCollege;
    private String preCourseId;
    private String syllabus;
    private String courseStatus;
}
