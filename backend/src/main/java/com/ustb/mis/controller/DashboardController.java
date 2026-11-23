package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.AuditLog;
import com.ustb.mis.entity.ClassInfo;
import com.ustb.mis.entity.College;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.DualEnroll;
import com.ustb.mis.entity.Major;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.ScheduleConflict;
import com.ustb.mis.entity.Teacher;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.AuditLogMapper;
import com.ustb.mis.mapper.ClassInfoMapper;
import com.ustb.mis.mapper.CollegeMapper;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.DualEnrollMapper;
import com.ustb.mis.mapper.MajorMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.NewCourseApplyMapper;
import com.ustb.mis.mapper.ScheduleConflictMapper;
import com.ustb.mis.mapper.TeacherMapper;
import com.ustb.mis.mapper.ViewMapper;
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
    private final ClassInfoMapper classInfoMapper;
    private final TeacherMapper teacherMapper;
    private final ViewMapper viewMapper;

    public DashboardController(CollegeMapper collegeMapper, MajorMapper majorMapper, CourseMapper courseMapper,
                               MajorProgramMapper programMapper, TeachingClassMapper tcMapper,
                               ScheduleConflictMapper conflictMapper, NewCourseApplyMapper applyMapper,
                               AuditLogMapper logMapper, DualEnrollMapper dualEnrollMapper,
                               ClassInfoMapper classInfoMapper, TeacherMapper teacherMapper,
                               ViewMapper viewMapper) {
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.courseMapper = courseMapper;
        this.programMapper = programMapper;
        this.tcMapper = tcMapper;
        this.conflictMapper = conflictMapper;
        this.applyMapper = applyMapper;
        this.logMapper = logMapper;
        this.dualEnrollMapper = dualEnrollMapper;
        this.classInfoMapper = classInfoMapper;
        this.teacherMapper = teacherMapper;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        LoginUser user = UserContext.require();
        Map<String, Object> data = new HashMap<>();
        // 学院教务（数据范围 2）：工作台指标全部收敛到本学院（学院教务修改意见第 4 条）
        final boolean collegeScope = "2".equals(user.getDataScope()) && user.getCollegeId() != null;
        final List<String> ownMajorIds = collegeScope
                ? majorMapper.selectList(Wrappers.<Major>lambdaQuery()
                        .eq(Major::getCollegeId, user.getCollegeId())).stream()
                        .map(Major::getMajorId).toList()
                : List.of();
        final List<String> ownCourseIds = collegeScope
                ? courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                        .eq(Course::getOpenCollege, user.getCollegeId())).stream()
                        .map(Course::getCourseId).toList()
                : List.of();
        data.put("collegeCount", collegeMapper.selectCount(Wrappers.<College>lambdaQuery()
                .eq(College::getCollegeStatus, "1")));
        data.put("majorCount", collegeScope
                ? countIf(!ownMajorIds.isEmpty(), () -> majorMapper.selectCount(Wrappers.<Major>lambdaQuery()
                        .eq(Major::getMajorStatus, "1").in(Major::getMajorId, ownMajorIds)))
                : majorMapper.selectCount(Wrappers.<Major>lambdaQuery().eq(Major::getMajorStatus, "1")));
        data.put("courseCount", collegeScope
                ? countIf(!ownCourseIds.isEmpty(), () -> courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                        .eq(Course::getCourseStatus, "1").in(Course::getCourseId, ownCourseIds)))
                : courseMapper.selectCount(Wrappers.<Course>lambdaQuery().eq(Course::getCourseStatus, "1")));
        data.put("programCount", collegeScope
                ? countIf(!ownMajorIds.isEmpty(), () -> programMapper.selectCount(Wrappers.<MajorProgram>lambdaQuery()
                        .eq(MajorProgram::getPlanStatus, "3").in(MajorProgram::getMajorId, ownMajorIds)))
                : programMapper.selectCount(Wrappers.<MajorProgram>lambdaQuery().eq(MajorProgram::getPlanStatus, "3")));
        data.put("tcCount", collegeScope
                ? countIf(!ownCourseIds.isEmpty(), () -> tcMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()
                        .in(TeachingClass::getCourseId, ownCourseIds)))
                : tcMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()));
        data.put("conflictCount", collegeScope ? collegeConflictCount(ownCourseIds) : conflictMapper.selectCount(
                Wrappers.<ScheduleConflict>lambdaQuery().eq(ScheduleConflict::getHandleStatus, "0")));
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
            long pending;
            if ("2".equals(user.getDataScope()) && user.getCollegeId() != null) {
                pending = viewMapper.listNewCourseApplies("0", null, user.getCollegeId()).size();
            } else {
                pending = applyMapper.selectCount(Wrappers.<com.ustb.mis.entity.NewCourseApply>lambdaQuery()
                        .eq(com.ustb.mis.entity.NewCourseApply::getAuditStatus, "0"));
            }
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
        data.put("profile", profileOf(user));
        data.put("collegeScope", collegeScope);
        return Result.ok(data);
    }

    /** 空集合时直接返回 0，避免生成非法 IN () 语句 */
    private long countIf(boolean cond, java.util.function.Supplier<Long> counter) {
        return cond ? counter.get() : 0L;
    }

    /** 本学院教学班（含对方教学班）产生的未处理冲突数 */
    private long collegeConflictCount(List<String> ownCourseIds) {
        if (ownCourseIds.isEmpty()) {
            return 0L;
        }
        List<String> tcIds = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                        .in(TeachingClass::getCourseId, ownCourseIds)).stream()
                .map(TeachingClass::getTcId).toList();
        if (tcIds.isEmpty()) {
            return 0L;
        }
        return conflictMapper.selectCount(Wrappers.<ScheduleConflict>lambdaQuery()
                .eq(ScheduleConflict::getHandleStatus, "0")
                .and(w -> w.in(ScheduleConflict::getTcId, tcIds)
                        .or()
                        .in(ScheduleConflict::getOtherTcId, tcIds)));
    }

    /** 个人信息卡：姓名、学号/工号、学院、专业/系、班级等（教师端与学生端工作台使用） */
    private Map<String, Object> profileOf(LoginUser user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("realName", user.getRealName());
        profile.put("roleName", user.getRoleName());
        profile.put("identity", user.getTeacherId() != null ? "教师"
                : user.getClassId() != null ? "学生" : "职工");
        profile.put("collegeId", user.getCollegeId());
        profile.put("majorId", user.getMajorId());
        profile.put("classId", user.getClassId());
        profile.put("teacherId", user.getTeacherId());
        if (user.getCollegeId() != null) {
            College college = collegeMapper.selectById(user.getCollegeId());
            profile.put("collegeName", college == null ? user.getCollegeId() : college.getCollegeName());
        }
        if (user.getMajorId() != null) {
            Major major = majorMapper.selectById(user.getMajorId());
            profile.put("majorName", major == null ? user.getMajorId() : major.getMajorName());
        }
        if (user.getClassId() != null) {
            ClassInfo info = classInfoMapper.selectById(user.getClassId());
            profile.put("className", info == null ? user.getClassId() : info.getClassName());
            if (info != null && user.getMajorId() == null) {
                Major major = majorMapper.selectById(info.getMajorId());
                profile.put("majorId", info.getMajorId());
                profile.put("majorName", major == null ? info.getMajorId() : major.getMajorName());
            }
        }
        if (user.getTeacherId() != null) {
            Teacher teacher = teacherMapper.selectById(user.getTeacherId());
            if (teacher != null) {
                profile.put("teacherName", teacher.getTeacherName());
                profile.put("title", teacher.getTitle());
                if (user.getCollegeId() == null) {
                    College college = collegeMapper.selectById(teacher.getCollegeId());
                    profile.put("collegeName", college == null ? teacher.getCollegeId() : college.getCollegeName());
                }
            }
        }
        return profile;
    }
}
