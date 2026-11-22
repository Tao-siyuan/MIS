package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * program_course 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("program_course")
public class ProgramCourse {
    private String majorId;
    private String grade;
    private String programType;
    @TableId(type = IdType.INPUT)
    private String courseId;
    private String courseType;
    private BigDecimal credit;
    private Integer hours;
    private String openSemester;
    private String preCourseId;
    private String crossStatus;
}
