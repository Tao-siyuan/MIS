package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * sys_user 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String userName;
    private String password;
    private String realName;
    private String roleId;
    private String collegeId;
    private String majorId;
    private String classId;
    private String teacherId;
    private String status;
    private LocalDateTime lastLogin;
}
