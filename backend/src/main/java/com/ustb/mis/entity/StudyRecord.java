package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * study_record 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("study_record")
public class StudyRecord {
    @TableId(type = IdType.INPUT)
    private String recId;
    private String stuId;
    private String programType;
    private String majorId;
    private String courseId;
    private String tcId;
    private String studyType;
    private String sourceCourseId;
    private BigDecimal credit;
    private String conflictType;
    private String auditStatus;
    private String remark;
}
