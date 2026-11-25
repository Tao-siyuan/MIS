package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排课周数规则：16 周 / 前 8 周 / 后 8 周，且同一课程两次课至少间隔一天。
 * 测试事务结束后回滚，不污染演示数据。
 */
@SpringBootTest
@Transactional
class ScheduleWeekRuleTest {

    private static final String TC_ID = "202610010";

    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private TeachingClassMapper tcMapper;
    @Autowired
    private ScheduleResultMapper resultMapper;
    @Autowired
    private ViewMapper viewMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 设定周数后重排该教学班，返回排课结果 */
    private List<Map<String, Object>> reschedule(int weekCount, int weekStart) {
        TeachingClass tc = tcMapper.selectById(TC_ID);
        tc.setWeekCount(weekCount);
        tc.setWeekStart(weekStart);
        tc.setTcStatus("1");
        tcMapper.updateById(tc);
        // 先清理调课记录（外键引用排课结果），再清空该教学班已有排课，保证从零重排
        jdbc.update("DELETE FROM adjust_record WHERE sched_id IN "
                + "(SELECT sched_id FROM schedule_result WHERE tc_id = ?)", TC_ID);
        resultMapper.delete(Wrappers.<ScheduleResult>lambdaQuery().eq(ScheduleResult::getTcId, TC_ID));
        scheduleService.autoSchedule(tc.getTerm(), List.of(TC_ID));
        return viewMapper.listScheduleResults(tc.getTerm(), TC_ID, null);
    }

    @Test
    void eightWeekCourseUsesSecondHalfOfTerm() {
        List<Map<String, Object>> rows = reschedule(8, 9);
        assertFalse(rows.isEmpty(), "后 8 周课程应完成排课");
        for (Map<String, Object> row : rows) {
            assertEquals("9-16", String.valueOf(row.get("weekRange")), "后 8 周课程的周次范围应为 9-16");
        }
    }

    @Test
    void sameCourseGapsAtLeastOneDay() {
        List<Map<String, Object>> rows = reschedule(8, 1);
        assertFalse(rows.isEmpty(), "前 8 周课程应完成排课");
        for (Map<String, Object> row : rows) {
            assertEquals("1-8", String.valueOf(row.get("weekRange")), "前 8 周课程的周次范围应为 1-8");
        }
        List<Integer> days = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            days.add(Integer.parseInt(String.valueOf(row.get("weekDay"))));
        }
        days.sort(Comparator.naturalOrder());
        for (int i = 1; i < days.size(); i++) {
            assertTrue(days.get(i) - days.get(i - 1) >= 2,
                    "同一门课程两次课至少间隔一天，实际上课日：" + days);
        }
    }

    @Test
    void sixteenWeekCourseKeepsFullTerm() {
        List<Map<String, Object>> rows = reschedule(16, 1);
        assertFalse(rows.isEmpty(), "16 周课程应完成排课");
        for (Map<String, Object> row : rows) {
            assertEquals("1-16", String.valueOf(row.get("weekRange")), "16 周课程的周次范围应为 1-16");
        }
    }
}
