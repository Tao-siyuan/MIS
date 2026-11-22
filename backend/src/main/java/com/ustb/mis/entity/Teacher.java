package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * teacher 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.INPUT)
    private String teacherId;
    private String teacherName;
    private String collegeId;
    private String title;
    private String teacherStatus;
}
