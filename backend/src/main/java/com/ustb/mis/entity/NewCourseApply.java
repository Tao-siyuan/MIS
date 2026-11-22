package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * new_course_apply 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("new_course_apply")
public class NewCourseApply {
    @TableId(type = IdType.INPUT)
    private String applyId;
    private String applyUser;
    private String courseName;
    private String courseType;
    private String applyReason;
    private BigDecimal credit;
    private Integer hours;
    private String collegeOpinion;
    private String auditStatus;
    private String courseId;
    private String auditOpinion;
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
}
