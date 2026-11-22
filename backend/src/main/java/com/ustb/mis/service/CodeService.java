package com.ustb.mis.service;

import com.ustb.mis.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 共享编码服务：集中发放业务编号，业务端只能申请、不得自编。
 * 规则见 3.4.2 共享编码设计。
 */
@Service
public class CodeService {

    private final JdbcTemplate jdbc;

    public CodeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 教学班号：学年4位 + 学期1位 + 序号4位，如 202610001 */
    public String nextTcId(String term) {
        String year = term.substring(0, 4);
        String semester = term.substring(term.length() - 1);
        String prefix = year + semester;
        String max = maxId("teaching_class", "tc_id", prefix);
        int seq = max == null ? 1 : Integer.parseInt(max.substring(prefix.length())) + 1;
        return prefix + String.format("%04d", seq);
    }

    /** 课程号：开课学院2位 + 顺序号5位，如 0900125 */
    public String nextCourseId(String collegeId) {
        if (collegeId == null || !collegeId.matches("\\d{2}")) {
            throw new BizException("请指定开课学院后再生成课程号");
        }
        String max = maxId("course", "course_id", collegeId);
        int seq = max == null ? 1 : Integer.parseInt(max.substring(2)) + 1;
        return collegeId + String.format("%05d", seq);
    }

    /** 排课编号：S + 年份 + 4位流水 */
    public String nextSchedId() {
        return nextSerial("schedule_result", "sched_id", "S" + LocalDate.now().getYear(), 4);
    }

    /** 单据编号：前缀2位 + 年份 + 4位流水，如 AD20260001 */
    public String nextBillId(String prefix, String table, String column) {
        return nextSerial(table, column, prefix + LocalDate.now().getYear(), 4);
    }

    private String nextSerial(String table, String column, String prefix, int digits) {
        String max = maxId(table, column, prefix);
        int seq = max == null ? 1 : Integer.parseInt(max.substring(prefix.length())) + 1;
        return prefix + String.format("%0" + digits + "d", seq);
    }

    private String maxId(String table, String column, String prefix) {
        if (!table.matches("[a-z_]+") || !column.matches("[a-z_]+")) {
            throw new BizException("非法的编码表名");
        }
        return jdbc.queryForObject(
                "select max(" + column + ") from " + table + " where " + column + " like ?",
                String.class, prefix + "%");
    }
}
