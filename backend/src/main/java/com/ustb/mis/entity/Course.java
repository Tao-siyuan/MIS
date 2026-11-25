package com.ustb.mis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * course 实体（对应数据字典中的业务表）。
 */
@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.INPUT)
    private String courseId;
    private String courseName;
    private String courseType;
    private BigDecimal credit;
    private Integer totalHours;
    /** 理论学时：按 2 学时递增，可为 0 */
    private Integer theoryHours;
    /** 实践学时：为 0 或不少于 8 学时且按 4 学时递增 */
    private Integer practiceHours;
    /** 实践学时所需教室类型：41机房/42物理实验室/43化学实验室/44生物实验室/45操场/46金工实习场地/47其他 */
    private String practiceRoomType;
    private String assessType;
    private String openCollege;
    private String preCourseId;
    private String syllabus;
    private String courseStatus;
}
