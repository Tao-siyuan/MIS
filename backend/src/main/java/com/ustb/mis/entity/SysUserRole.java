package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * sys_user_role 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String roleId;
}
