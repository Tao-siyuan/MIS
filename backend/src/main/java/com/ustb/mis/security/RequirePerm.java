package com.ustb.mis.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 功能权限声明，值为权限码（P01～P24）。
 * P01 学院与专业维护 / P02 方案编制 / P03 方案审核发布 / P04 通识课程计划 / P05 课程库维护 /
 * P06 新增课程申报 / P07 新增课程终审 / P08 教学班查询 / P09 排课与课表发布 / P10 调课申请 /
 * P11 调课审批 / P12 课表查询 / P13 双学位报名与查询 / P14 学分转换申请 / P15 评教提交与结果查询 /
 * P16 操作日志审计 / P17 双学位录取审批 / P18 评教方案管理 / P19 教学班维护 / P20 角色与用户配置 /
 * P21 新增课程学院初审 / P22 跨院课程确认 / P23 通识课程承接确认 / P24 学分认定审核。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    String[] value();
}
