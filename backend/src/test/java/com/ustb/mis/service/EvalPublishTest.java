package com.ustb.mis.service;

import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.EvalPlan;
import com.ustb.mis.entity.EvalRecord;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.EvalPlanMapper;
import com.ustb.mis.mapper.EvalRecordMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评教结果发布：教务处发布后教师仅能看到本人教学班的汇总与各维度均分，学生信息不对外。
 * 测试数据自建并随事务回滚，不依赖演示教学班。
 */
@SpringBootTest
@Transactional
class EvalPublishTest {

    private static final String PLAN_ID = "EP262799";
    private static final String TERM = "TEST-EVAL";
    private static final String MY_TC = "TESTE001";
    private static final String OTHER_TC = "TESTE002";
    private static final String MY_TEACHER = "A260001";

    @Autowired
    private EvalService evalService;
    @Autowired
    private EvalPlanMapper planMapper;
    @Autowired
    private EvalRecordMapper recordMapper;
    @Autowired
    private TeachingClassMapper tcMapper;

    @BeforeEach
    void setUp() {
        EvalPlan plan = new EvalPlan();
        plan.setPlanId(PLAN_ID);
        plan.setTerm(TERM);
        plan.setPlanName("评教发布测试方案");
        plan.setIndicatorJson("[{\"name\":\"教学态度\",\"w\":0.5},{\"name\":\"教学效果\",\"w\":0.5}]");
        plan.setOpenDate(LocalDate.of(2026, 11, 1));
        plan.setCloseDate(LocalDate.of(2026, 11, 20));
        plan.setAnonymity("1");
        plan.setMinSample(1);
        plan.setPlanStatus("2");
        planMapper.insert(plan);

        insertTc(MY_TC, MY_TEACHER);
        insertTc(OTHER_TC, "A260002");
        insertRecord("ER26279901", MY_TC, "U000000001", "{\"教学态度\":90,\"教学效果\":80}");
        insertRecord("ER26279902", OTHER_TC, "U000000002", "{\"教学态度\":60,\"教学效果\":70}");
    }

    private void insertTc(String tcId, String teacherId) {
        TeachingClass tc = new TeachingClass();
        tc.setTcId(tcId);
        tc.setTerm(TERM);
        tc.setCourseId("0910001");
        tc.setTeacherId(teacherId);
        tc.setClassIds("TESTCLS1");
        tc.setStudentNum(30);
        tc.setCapacity(0);
        tc.setTcType("1");
        tc.setTeachType("1");
        tc.setHours(64);
        tc.setTheoryHours(64);
        tc.setPracticeHours(0);
        tc.setWeekCount(16);
        tc.setWeekStart(1);
        tc.setTcStatus("1");
        tc.setSource("1");
        tc.setActualNum(30);
        tc.setOpenLower(10);
        tcMapper.insert(tc);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void insertRecord(String id, String tcId, String stuId, String itemScore) {
        EvalRecord record = new EvalRecord();
        record.setRecordId(id);
        record.setPlanId(PLAN_ID);
        record.setTcId(tcId);
        record.setStuId(stuId);
        record.setScore(new BigDecimal("85.0"));
        record.setItemScore(itemScore);
        record.setValidFlag("1");
        record.setSubmitTime(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private void loginAsTeacher() {
        LoginUser teacher = new LoginUser();
        teacher.setUserId(MY_TEACHER);
        teacher.setUserName(MY_TEACHER);
        teacher.setRealName("测试教师");
        teacher.setRoleId("R04");
        teacher.setDataScope("3");
        teacher.setTeacherId(MY_TEACHER);
        UserContext.set(teacher);
    }

    private void loginAsDean() {
        LoginUser dean = new LoginUser();
        dean.setUserId("B220001");
        dean.setUserName("B220001");
        dean.setRoleId("R02");
        dean.setDataScope("1");
        UserContext.set(dean);
    }

    @Test
    void teacherCannotSeeResultBeforePublish() {
        loginAsTeacher();
        BizException ex = assertThrows(BizException.class, () -> evalService.stats(PLAN_ID));
        assertTrue(ex.getMessage().contains("尚未发布"));
    }

    @Test
    void teacherSeesOnlyOwnClassWithItemAveragesAfterPublish() {
        loginAsDean();
        evalService.publishResult(PLAN_ID);
        assertEquals("3", planMapper.selectById(PLAN_ID).getPlanStatus());

        loginAsTeacher();
        Map<String, Object> stats = evalService.stats(PLAN_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tcStats = (List<Map<String, Object>>) stats.get("tcStats");
        assertEquals(1, tcStats.size(), "教师只应看到本人教学班");
        Map<String, Object> row = tcStats.get(0);
        assertEquals(MY_TC, row.get("tcId"));
        assertFalse(row.containsKey("stuId"), "统计结果不得包含学生信息");

        @SuppressWarnings("unchecked")
        Map<String, Double> itemAvg = (Map<String, Double>) row.get("itemAvg");
        assertEquals(90.0, itemAvg.get("教学态度"));
        assertEquals(80.0, itemAvg.get("教学效果"));

        assertTrue(evalService.records(PLAN_ID, null).isEmpty(), "教师不可查看学生原始评教记录");
    }

    @Test
    void teacherOnlySeesPublishedPlans() {
        loginAsTeacher();
        assertTrue(evalService.plans(null).stream().noneMatch(p -> PLAN_ID.equals(p.getPlanId())),
                "结果未发布时教师看不到该评教方案");

        loginAsDean();
        evalService.publishResult(PLAN_ID);

        loginAsTeacher();
        assertTrue(evalService.plans(null).stream().anyMatch(p -> PLAN_ID.equals(p.getPlanId())));
    }

    @Test
    void revokeHidesResultAgain() {
        loginAsDean();
        evalService.publishResult(PLAN_ID);
        evalService.revokeResult(PLAN_ID);
        assertEquals("2", planMapper.selectById(PLAN_ID).getPlanStatus());

        loginAsTeacher();
        assertThrows(BizException.class, () -> evalService.stats(PLAN_ID));
        assertTrue(evalService.plans(null).stream().noneMatch(p -> PLAN_ID.equals(p.getPlanId())));
    }

    @Test
    void revokeRejectedWhenNotPublished() {
        loginAsDean();
        BizException ex = assertThrows(BizException.class, () -> evalService.revokeResult(PLAN_ID));
        assertTrue(ex.getMessage().contains("尚未发布"));
    }

    @Test
    void cannotPublishBeforeFinish() {
        planMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<EvalPlan>lambdaUpdate()
                .eq(EvalPlan::getPlanId, PLAN_ID).set(EvalPlan::getPlanStatus, "1"));
        loginAsDean();
        BizException ex = assertThrows(BizException.class, () -> evalService.publishResult(PLAN_ID));
        assertTrue(ex.getMessage().contains("尚未结束"));
    }
}
