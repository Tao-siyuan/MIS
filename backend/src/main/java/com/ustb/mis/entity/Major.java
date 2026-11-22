package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * major 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("major")
public class Major {
    @TableId(type = IdType.INPUT)
    private String majorId;
    private String majorName;
    private String collegeId;
    private Integer duration;
    private String degree;
    private BigDecimal minCredit;
    private String majorStatus;
}
