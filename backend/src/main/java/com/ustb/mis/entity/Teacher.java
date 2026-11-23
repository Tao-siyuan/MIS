package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * teacher 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.INPUT)
    private String teacherId;
    private String teacherName;
    private String collegeId;
    private String title;
    private String teacherStatus;
    /** 登录工号（sys_user.user_id）：界面统一展示工号，不对外暴露师资子系统编号 */
    @TableField(exist = false)
    private String userId;
}
