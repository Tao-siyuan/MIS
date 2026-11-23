package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * tc_student 实体：教学班学生名单（任课教师可在本人教学班内添加/移除学生）。
 */
@Data
@TableName("tc_student")
public class TcStudent {
    @TableId(type = IdType.INPUT)
    private String tcId;
    private String stuId;
    private LocalDateTime addTime;
}
