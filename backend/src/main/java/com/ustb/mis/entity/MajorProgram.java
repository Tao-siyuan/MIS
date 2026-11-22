package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * major_program 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("major_program")
public class MajorProgram {
    @TableId(type = IdType.INPUT)
    private String majorId;
    private String grade;
    private String programType;
    private BigDecimal totalCredit;
    private String goalDesc;
    private String version;
    private String planStatus;
    private Integer dualQuota;
    private String reviewOpinion;
    private LocalDate publishDate;
    private LocalDateTime createTime;
}
