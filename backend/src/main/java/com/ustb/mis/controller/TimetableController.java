package com.ustb.mis.controller;

import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课表查询：教师 / 班级 / 教室 / 学生四类视图，按数据范围自动过滤（对应 3.5.3 课表查询）。
 */
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final ViewMapper viewMapper;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public TimetableController(ViewMapper viewMapper, JdbcTemplate jdbc, AuditService auditService) {
        this.viewMapper = viewMapper;
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @GetMapping("/terms")
    @RequirePerm("P12")
    public Result<List<String>> terms() {
        return Result.ok(jdbc.queryForList(
                "select distinct term from teaching_class order by term desc", String.class));
    }

    /**
     * 已修课程：学生查看本人所在班级在往期学期已完成（课表已发布）的课程清单。
     */
    @GetMapping("/history")
    @RequirePerm("P12")
    public Result<List<Map<String, Object>>> history(@RequestParam(required = false) String classId,
                                                     @RequestParam(required = false) String beforeTerm) {
        LoginUser user = UserContext.require();
        String target = user.getClassId() != null ? user.getClassId() : classId;
        if (target == null) {
            return Result.ok(List.of());
        }
        String current = beforeTerm;
        if (current == null || current.isBlank()) {
            List<String> terms = jdbc.queryForList(
                    "select distinct term from teaching_class order by term desc", String.class);
            current = terms.isEmpty() ? "" : terms.get(0);
        }
        String sql = "select tc.term, tc.tc_id as tcId, c.course_id as courseId, c.course_name as courseName, "
                + "c.credit, c.assess_type as assessType, tc.hours, tc.week_count as weekCount, "
                + "t.teacher_name as teacherName, tc.tc_type as tcType "
                + "from teaching_class tc join course c on c.course_id = tc.course_id "
                + "left join teacher t on t.teacher_id = tc.teacher_id "
                + "where tc.class_ids like ? and tc.term < ? and tc.tc_status = '4' "
                + "order by tc.term desc, c.course_id";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, "%" + target + "%", current);
        return Result.ok(rows);
    }

    @GetMapping
    @RequirePerm({"P12", "P09"})
    public Result<Map<String, Object>> timetable(@RequestParam String term,
                                                 @RequestParam(defaultValue = "self") String view,
                                                 @RequestParam(required = false) String key,
                                                 @RequestParam(required = false) String export) {
        LoginUser user = UserContext.require();
        String teacherId = null;
        String classId = null;
        String roomId = null;
        if ("2".equals(user.getDataScope())) {
            if ("room".equals(view)) {
                roomId = key;
            } else if ("class".equals(view)) {
                classId = key;
            } else if ("teacher".equals(view)) {
                teacherId = key == null || key.isBlank() ? user.getTeacherId() : key;
            } else {
                teacherId = user.getTeacherId();
                if (teacherId == null) {
                    classId = key;
                }
            }
        } else if ("3".equals(user.getDataScope())) {
            if (user.getTeacherId() != null) {
                teacherId = user.getTeacherId();
            } else {
                classId = user.getClassId();
            }
        } else {
            if ("teacher".equals(view)) {
                teacherId = key;
            } else if ("class".equals(view)) {
                classId = key;
            } else if ("room".equals(view)) {
                roomId = key;
            } else if (user.getTeacherId() != null) {
                teacherId = user.getTeacherId();
            } else if (user.getClassId() != null) {
                classId = user.getClassId();
            }
        }
        if (teacherId == null && classId == null && roomId == null) {
            throw new BizException("请指定查询对象（教师/班级/教室）");
        }
        List<Map<String, Object>> rows = viewMapper.timetable(term, teacherId, classId, roomId);
        Map<String, Object> data = new HashMap<>();
        data.put("term", term);
        data.put("view", view);
        data.put("teacherId", teacherId);
        data.put("classId", classId);
        data.put("roomId", roomId);
        data.put("rows", rows);
        if ("1".equals(export)) {
            auditService.record("06", "v_full_schedule:" + term, null, Map.of("view", view, "key", String.valueOf(key)));
        }
        return Result.ok(data);
    }
}
