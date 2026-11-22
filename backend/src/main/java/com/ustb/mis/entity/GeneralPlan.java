package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * general_plan 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("general_plan")
public class GeneralPlan {
    private String schoolYear;
    @TableId(type = IdType.INPUT)
    private String courseId;
    private String planCategory;
    private Integer suggestHours;
    private String undertakeCollege;
    private String teacherId;
    private String confirmStatus;
    private String planStatus;
}
