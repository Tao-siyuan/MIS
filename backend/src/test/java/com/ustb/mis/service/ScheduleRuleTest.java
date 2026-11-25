package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排课规则：一节课两小节、按周数决定每周次数、实践课机房、体育课不占教室。
 * 测试数据自建并随事务回滚，不依赖演示教学班。
 */
@SpringBootTest
@Transactional
class ScheduleRuleTest {

    private static final String TERM = "TEST-SCH";
    private static final String THEORY_TC = "TESTS001";
    private static final String PRACTICE_TC = "TESTS002";
    private static final String PE_TC = "TESTS003";
    private static final String EIGHT_WEEK_TC = "TESTS004";

    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private TeachingClassMapper tcMapper;
    @Autowired
    private ScheduleResultMapper resultMapper;
    @Autowired
    private ViewMapper viewMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // 0910001 程序设计基础（理论），0910017 人工智能通识A（实践场地 41=机房），1810001 体育（一）
        insertTc(THEORY_TC, "0910001", "A260001", 96, 96, 0, 16);
        insertTc(PRACTICE_TC, "0910017", "A260002", 32, 16, 16, 16);
        insertTc(PE_TC, "1810001", "A260009", 32, 32, 0, 16);
        insertTc(EIGHT_WEEK_TC, "0910001", "A260003", 48, 48, 0, 8);
    }

    private void insertTc(String id, String courseId, String teacherId,
                          int hours, int theory, int practice, int weeks) {
        TeachingClass tc = new TeachingClass();
        tc.setTcId(id);
        tc.setTerm(TERM);
        tc.setCourseId(courseId);
        tc.setTeacherId(teacherId);
        tc.setClassIds("TESTCLS1");
        tc.setStudentNum(30);
        tc.setCapacity(0);
        tc.setTcType("1");
        tc.setTeachType("1");
        tc.setHours(hours);
        tc.setTheoryHours(theory);
        tc.setPracticeHours(practice);
        tc.setWeekCount(weeks);
        tc.setWeekStart(1);
        tc.setTcStatus("1");
        tc.setSource("1");
        tc.setActualNum(30);
        tc.setOpenLower(10);
        tcMapper.insert(tc);
    }

    private List<Map<String, Object>> reschedule(String tcId) {
        jdbc.update("DELETE FROM adjust_record WHERE sched_id IN "
                + "(SELECT sched_id FROM schedule_result WHERE tc_id = ?)", tcId);
        resultMapper.delete(Wrappers.<ScheduleResult>lambdaQuery().eq(ScheduleResult::getTcId, tcId));
        scheduleService.autoSchedule(TERM, List.of(tcId));
        return viewMapper.listScheduleResults(TERM, tcId, null);
    }

    private int intOf(Map<String, Object> row, String key) {
        return Integer.parseInt(String.valueOf(row.get(key)));
    }

    @Test
    void theoryCourseSchedulesWeeklyBlocksOfTwoPeriods() {
        List<Map<String, Object>> rows = reschedule(THEORY_TC);
        assertEquals(3, rows.size(), "96 学时 16 周应排 3 次");
        List<Integer> days = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            assertEquals(1, intOf(row, "endSlot") - intOf(row, "startSlot"), "一节课占两小节");
            assertEquals(1, intOf(row, "startSlot") % 2, "每节课从奇数小节开始");
            days.add(intOf(row, "weekDay"));
        }
        days.sort(Comparator.naturalOrder());
        for (int i = 1; i < days.size(); i++) {
            assertTrue(days.get(i) - days.get(i - 1) >= 2, "同一门课两次课至少间隔一天：" + days);
        }
    }

    @Test
    void eightWeekCourseSchedulesMoreSessionsPerWeek() {
        List<Map<String, Object>> rows = reschedule(EIGHT_WEEK_TC);
        assertEquals(3, rows.size(), "8 周课程每周应排 3 次");
        for (Map<String, Object> row : rows) {
            assertEquals("1-8", String.valueOf(row.get("weekRange")));
            assertEquals(1, intOf(row, "endSlot") - intOf(row, "startSlot"), "每节课占两小节");
        }
    }

    @Test
    void peCourseOccupiesNoClassroom() {
        List<Map<String, Object>> rows = reschedule(PE_TC);
        assertFalse(rows.isEmpty(), "体育课应能排出");
        for (Map<String, Object> row : rows) {
            assertNull(row.get("roomId"), "体育课不应占用教室");
        }
    }

    @Test
    void practiceSessionUsesComputerRoomAndTwoSlots() {
        List<Map<String, Object>> rows = reschedule(PRACTICE_TC);
        assertEquals(2, rows.size(), "理论 1 次 + 实践 1 次");
        boolean inComputerRoom = false;
        for (Map<String, Object> row : rows) {
            assertEquals(1, intOf(row, "endSlot") - intOf(row, "startSlot"), "每次课占两小节");
            String roomId = String.valueOf(row.get("roomId"));
            Integer roomType = jdbc.queryForObject(
                    "SELECT CAST(room_type AS UNSIGNED) FROM classroom WHERE room_id = ?",
                    Integer.class, roomId);
            if (Integer.valueOf(3).equals(roomType)) {
                inComputerRoom = true;
            }
        }
        assertTrue(inComputerRoom, "实践学时（场地 41）应安排在机房类场地（room_type=3）");
    }
}
