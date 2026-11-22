package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * class_info 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("class_info")
public class ClassInfo {
    @TableId(type = IdType.INPUT)
    private String classId;
    private String className;
    private String majorId;
    private String grade;
    private Integer studentNum;
}
