package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 培养方案历史版本快照（对应设计文档：版本化编制，已发布版本只读留档）。
 */
@Data
@TableName("major_program_version")
public class MajorProgramVersion {
    @TableId(type = IdType.INPUT)
    private String majorId;
    private String grade;
    private String programType;
    private String version;
    private String planStatus;
    private BigDecimal totalCredit;
    private String goalDesc;
    private Integer dualQuota;
    private String reviewOpinion;
    private LocalDate publishDate;
    private Integer courseCount;
    private LocalDateTime archiveTime;
}
