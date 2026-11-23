package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.AuditLog;
import com.ustb.mis.entity.College;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.DualEnroll;
import com.ustb.mis.entity.Major;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.ScheduleConflict;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.AuditLogMapper;
import com.ustb.mis.mapper.CollegeMapper;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.DualEnrollMapper;
import com.ustb.mis.mapper.MajorMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.NewCourseApplyMapper;
import com.ustb.mis.mapper.ScheduleConflictMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页工作台：关键指标与待办。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final CourseMapper courseMapper;
    private final MajorProgramMapper programMapper;
    private final TeachingClassMapper tcMapper;
    private final ScheduleConflictMapper conflictMapper;
    private final NewCourseApplyMapper applyMapper;
    private final AuditLogMapper logMapper;
    private final DualEnrollMapper dualEnrollMapper;

    public DashboardController(CollegeMapper collegeMapper, MajorMapper majorMapper, CourseMapper courseMapper,
                               MajorProgramMapper programMapper, TeachingClassMapper tcMapper,
                               ScheduleConflictMapper conflictMapper, NewCourseApplyMapper applyMapper,
                               AuditLogMapper logMapper, DualEnrollMapper dualEnrollMapper) {
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.courseMapper = courseMapper;
        this.programMapper = programMapper;
        this.tcMapper = tcMapper;
        this.conflictMapper = conflictMapper;
        this.applyMapper = applyMapper;
        this.logMapper = logMapper;
        this.dualEnrollMapper = dualEnrollMapper;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        LoginUser user = UserContext.require();
        Map<String, Object> data = new HashMap<>();
        data.put("collegeCount", collegeMapper.selectCount(Wrappers.<College>lambdaQuery()
                .eq(College::getCollegeStatus, "1")));
        data.put("majorCount", majorMapper.selectCount(Wrappers.<Major>lambdaQuery()
                .eq(Major::getMajorStatus, "1")));
        data.put("courseCount", courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseStatus, "1")));
        data.put("programCount", programMapper.selectCount(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getPlanStatus, "3")));
        data.put("tcCount", tcMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()));
        data.put("conflictCount", conflictMapper.selectCount(Wrappers.<ScheduleConflict>lambdaQuery()
                .eq(ScheduleConflict::getHandleStatus, "0")));
        data.put("pendingApply", applyMapper.selectCount(Wrappers.<com.ustb.mis.entity.NewCourseApply>lambdaQuery()
                .eq(com.ustb.mis.entity.NewCourseApply::getAuditStatus, "3")));

        // 最近操作仅系统管理员可见，其余角色只提供待办（P1-09 数据最小化）
        boolean admin = "R01".equals(user.getRoleId()) || user.hasPerm("P20");
        if (admin) {
            List<AuditLog> logs = logMapper.selectList(Wrappers.<AuditLog>lambdaQuery()
                    .orderByDesc(AuditLog::getOperTime).last("limit 8"));
            data.put("recentLogs", logs == null ? new ArrayList<>() : logs);
        } else {
            data.put("recentLogs", new ArrayList<>());
        }

        List<Map<String, Object>> todo = new ArrayList<>();
        if (user.hasPerm("P03")) {
            long pending = programMapper.selectCount(Wrappers.<MajorProgram>lambdaQuery()
                    .in(MajorProgram::getPlanStatus, List.of("1", "2")));
            if (pending > 0) {
                todo.add(Map.of("title", "培养方案待审核", "count", pending, "path", "/programs"));
            }
        }
        if (user.hasPerm("P21")) {
            long pending = applyMapper.selectCount(Wrappers.<com.ustb.mis.entity.NewCourseApply>lambdaQuery()
                    .eq(com.ustb.mis.entity.NewCourseApply::getAuditStatus, "0"));
            if (pending > 0) {
                todo.add(Map.of("title", "新增课程申报待学院初审", "count", pending, "path", "/course-apply"));
            }
        }
        if (user.hasPerm("P07")) {
            long pending = applyMapper.selectCount(Wrappers.<com.ustb.mis.entity.NewCourseApply>lambdaQuery()
                    .eq(com.ustb.mis.entity.NewCourseApply::getAuditStatus, "3"));
            if (pending > 0) {
                todo.add(Map.of("title", "新增课程申报待终审", "count", pending, "path", "/course-apply"));
            }
        }
        if (user.hasPerm("P17")) {
            long pending = dualEnrollMapper.selectCount(Wrappers.<DualEnroll>lambdaQuery()
                    .eq(DualEnroll::getAuditStatus, "0"));
            if (pending > 0) {
                todo.add(Map.of("title", "双学位报名待录取审批", "count", pending, "path", "/dual"));
            }
        }
        if (user.hasPerm("P09")) {
            long pending = conflictMapper.selectCount(Wrappers.<ScheduleConflict>lambdaQuery()
                    .eq(ScheduleConflict::getHandleStatus, "0"));
            if (pending > 0) {
                todo.add(Map.of("title", "排课冲突待处理", "count", pending, "path", "/schedule"));
            }
        }
        data.put("todo", todo);
        data.put("welcome", user.getRealName() + "（" + user.getRoleName() + "）");
        return Result.ok(data);
    }
}
