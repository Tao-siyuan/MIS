package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * eval_record 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("eval_record")
public class EvalRecord {
    @TableId(type = IdType.INPUT)
    private String recordId;
    private String planId;
    private String tcId;
    private String stuId;
    private BigDecimal score;
    private String itemScore;
    private String comment;
    private String validFlag;
    private LocalDateTime submitTime;
}
