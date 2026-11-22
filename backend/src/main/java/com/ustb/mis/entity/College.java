package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * college 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("college")
public class College {
    @TableId(type = IdType.INPUT)
    private String collegeId;
    private String collegeName;
    private String collegeShort;
    private String collegeHead;
    private String contactPhone;
    private String collegeStatus;
}
