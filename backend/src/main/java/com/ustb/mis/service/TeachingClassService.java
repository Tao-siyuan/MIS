package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.ClassInfo;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.Teacher;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.ClassInfoMapper;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.TeacherMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教学班生成与开课任务确认（对应 3.5.3 教学班生成）。
 */
@Service
public class TeachingClassService {

    private final TeachingClassMapper tcMapper;
    private final ProgramCourseMapper programCourseMapper;
    private final MajorProgramMapper programMapper;
    private final ClassInfoMapper classInfoMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final ScheduleResultMapper scheduleResultMapper;
    private final ViewMapper viewMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public TeachingClassService(TeachingClassMapper tcMapper, ProgramCourseMapper programCourseMapper,
                                MajorProgramMapper programMapper, ClassInfoMapper classInfoMapper,
                                TeacherMapper teacherMapper, CourseMapper courseMapper,
                                ScheduleResultMapper scheduleResultMapper, ViewMapper viewMapper,
                                CodeService codeService, AuditService auditService) {
        this.tcMapper = tcMapper;
        this.programCourseMapper = programCourseMapper;
        this.programMapper = programMapper;
        this.classInfoMapper = classInfoMapper;
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.scheduleResultMapper = scheduleResultMapper;
        this.viewMapper = viewMapper;
        this.codeService = codeService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list(String term, String keyword, String tcStatus) {
        LoginUser user = UserContext.require();
        String teacherId = null;
        String collegeId = null;
        String classId = null;
        if ("2".equals(user.getDataScope())) {
            collegeId = user.getCollegeId();
            if (user.getTeacherId() != null) {
                teacherId = user.getTeacherId();
            }
        } else if ("3".equals(user.getDataScope())) {
            if (user.getTeacherId() != null) {
                teacherId = user.getTeacherId();
            }
            classId = user.getClassId();
        }
        return viewMapper.listTeachingClasses(term, keyword, tcStatus, teacherId, collegeId, classId);
    }

    public List<TeachingClass> listByTerm(String term) {
        return tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term)
                .orderByAsc(TeachingClass::getTcId));
    }

    @Transactional
    public void save(Map<String, Object> body) {
        String tcId = str(body.get("tcId"));
        TeachingClass tc;
        boolean isNew = tcId == null || tcId.isBlank();
        if (isNew) {
            tc = new TeachingClass();
            tc.setTerm(str(body.get("term")));
            tc.setCourseId(str(body.get("courseId")));
            tc.setTcId(codeService.nextTcId(tc.getTerm()));
            tc.setSource("1");
        } else {
            tc = tcMapper.selectById(tcId);
            if (tc == null) {
                throw new BizException("教学班不存在");
            }
            if ("4".equals(tc.getTcStatus())) {
                throw new BizException("教学班已发布课表，不能修改");
            }
            requireOwnership(tc);
        }
        if (body.get("teacherId") != null) {
            tc.setTeacherId(str(body.get("teacherId")));
        }
        if (body.get("classIds") != null) {
            tc.setClassIds(str(body.get("classIds")));
        }
        if (body.get("studentNum") != null) {
            tc.setStudentNum(Integer.valueOf(str(body.get("studentNum"))));
        }
        if (body.get("capacity") != null) {
            tc.setCapacity(Integer.valueOf(str(body.get("capacity"))));
        } else if (isNew) {
            tc.setCapacity(Math.max(tc.getStudentNum() == null ? 0 : tc.getStudentNum() + 10, 40));
        }
        if (body.get("tcType") != null) {
            tc.setTcType(str(body.get("tcType")));
        }
        if (body.get("teachType") != null) {
            tc.setTeachType(str(body.get("teachType")));
        }
        if (body.get("hours") != null) {
            tc.setHours(Integer.valueOf(str(body.get("hours"))));
        } else if (isNew) {
            Course course = courseMapper.selectById(tc.getCourseId());
            tc.setHours(course == null ? 48 : course.getTotalHours());
        }
        if (body.get("preferSlot") != null) {
            tc.setPreferSlot(str(body.get("preferSlot")));
        }
        if (body.get("unavailableTime") != null) {
            tc.setUnavailableTime(str(body.get("unavailableTime")));
        }
        if (tc.getCapacity() != null && tc.getStudentNum() != null && tc.getCapacity() < tc.getStudentNum()) {
            throw new BizException("容量上限不得小于面向班级人数（BR-04）");
        }
        if (tc.getTeacherId() == null || tc.getTeacherId().isBlank()) {
            tc.setTcStatus("0");
            if (isNew) {
                tcMapper.insert(tc);
                auditService.record("01", "teaching_class:" + tc.getTcId(), null, tc);
                return;
            }
        } else if (tc.getTcStatus() == null || "0".equals(tc.getTcStatus())) {
            tc.setTcStatus("1");
        }
        // 教师（本人数据范围）只能调整偏好时段与不可用时间，其余字段由教务处/学院教务员维护（P0-03）
        LoginUser current = UserContext.require();
        if (!isNew && "3".equals(current.getDataScope())) {
            TeachingClass limited = new TeachingClass();
            limited.setTcId(tc.getTcId());
            limited.setPreferSlot(tc.getPreferSlot());
            limited.setUnavailableTime(tc.getUnavailableTime());
            tcMapper.updateById(limited);
            auditService.record("02", "teaching_class:" + tc.getTcId(), null,
                    Map.of("preferSlot", tc.getPreferSlot() == null ? "" : tc.getPreferSlot(),
                            "unavailableTime", tc.getUnavailableTime() == null ? "" : tc.getUnavailableTime()));
            return;
        }
        if (isNew) {
            tcMapper.insert(tc);
            auditService.record("01", "teaching_class:" + tc.getTcId(), null, tc);
        } else {
            tcMapper.updateById(tc);
            auditService.record("02", "teaching_class:" + tc.getTcId(), null, tc);
        }
    }

    /** 按“方案课程 × 面向班级”批量生成教学班 */
    @Transactional
    public Map<String, Object> batchGenerate(String term, String majorId, String grade, String programType) {
        MajorProgram program = programMapper.selectOne(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType));
        if (program == null) {
            throw new BizException("培养方案不存在");
        }
        if (!"3".equals(program.getPlanStatus())) {
            throw new BizException("方案未发布，不能生成教学班");
        }
        List<ProgramCourse> courses = programCourseMapper.selectList(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType));
        List<ClassInfo> classes = classInfoMapper.selectList(Wrappers.<ClassInfo>lambdaQuery()
                .eq(ClassInfo::getMajorId, majorId)
                .eq(ClassInfo::getGrade, grade));
        if (classes.isEmpty()) {
            throw new BizException("该专业年级下没有班级数据，无法生成教学班");
        }
        String classIds = classes.stream().map(ClassInfo::getClassId).collect(Collectors.joining(","));
        int studentNum = classes.stream().mapToInt(c -> c.getStudentNum() == null ? 0 : c.getStudentNum()).sum();

        int created = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (ProgramCourse pc : courses) {
            if ("8".equals(pc.getOpenSemester())) {
                skipped++;
                messages.add(pc.getCourseId() + " 为毕业学期课程，不参与排课");
                continue;
            }
            // 按课程开课学期拆分到对应学期，避免全部堆在同一个 term（P2-07）
            String courseTerm = termFor(grade, pc.getOpenSemester(), term);
            long exists = tcMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()
                    .eq(TeachingClass::getTerm, courseTerm)
                    .eq(TeachingClass::getCourseId, pc.getCourseId())
                    .eq(TeachingClass::getClassIds, classIds));
            if (exists > 0) {
                skipped++;
                continue;
            }
            TeachingClass tc = new TeachingClass();
            tc.setTcId(codeService.nextTcId(courseTerm));
            tc.setTerm(courseTerm);
            tc.setCourseId(pc.getCourseId());
            tc.setClassIds(classIds);
            tc.setStudentNum(studentNum);
            tc.setCapacity(Math.max(studentNum + 10, 40));
            tc.setTcType("1");
            Course course = courseMapper.selectById(pc.getCourseId());
            tc.setTeachType(course != null && "0901006".equals(course.getCourseId()) ? "3" : "1");
            tc.setHours(pc.getHours());
            tc.setWeekCount(16);
            tc.setMulticlassPrefer("1");
            tc.setOpenLower(10);
            tc.setSource("1");
            Teacher teacher = pickTeacher(course == null ? null : course.getOpenCollege(), pc.getCourseId());
            if (teacher != null) {
                tc.setTeacherId(teacher.getTeacherId());
                tc.setTcStatus("1");
            } else {
                tc.setTcStatus("0");
            }
            tcMapper.insert(tc);
            created++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("messages", messages);
        auditService.record("01", "teaching_class:batch:" + majorId + "-" + grade,
                null, Map.of("term", term, "created", created));
        return result;
    }

    /** 教师分配：按开课学院筛选在职教师，取当前教学班数量最少者，避免随机指派（P2-07） */
    private Teacher pickTeacher(String collegeId, String courseId) {
        if (collegeId == null) {
            return null;
        }
        List<Teacher> teachers = teacherMapper.selectList(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getCollegeId, collegeId)
                .eq(Teacher::getTeacherStatus, "1")
                .orderByAsc(Teacher::getTeacherId));
        if (teachers.isEmpty()) {
            return null;
        }
        Map<String, Long> load = new HashMap<>();
        for (TeachingClass tc : tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .isNotNull(TeachingClass::getTeacherId))) {
            load.merge(tc.getTeacherId(), 1L, Long::sum);
        }
        return teachers.stream()
                .min(java.util.Comparator.comparingLong(t -> load.getOrDefault(t.getTeacherId(), 0L)))
                .orElse(teachers.get(0));
    }

    /** 依据入学年级与开课学期推算学期：2026 级第 3 学期 → 2027-2028-1 */
    private String termFor(String grade, String openSemester, String fallback) {
        if (grade == null || !grade.matches("\\d{4}") || openSemester == null
                || !openSemester.matches("[1-8]")) {
            return fallback;
        }
        int enrollYear = Integer.parseInt(grade);
        int semester = Integer.parseInt(openSemester);
        int year = enrollYear + (semester - 1) / 2;
        return year + "-" + (year + 1) + "-" + ((semester - 1) % 2 + 1);
    }

    /** 数据范围校验：全校可维护；学院教务员限本学院开课；教师限本人任教（P0-03） */
    private void requireOwnership(TeachingClass tc) {
        LoginUser user = UserContext.require();
        if ("1".equals(user.getDataScope())) {
            return;
        }
        if ("2".equals(user.getDataScope())) {
            Course course = courseMapper.selectById(tc.getCourseId());
            if (course != null && user.getCollegeId() != null
                    && !user.getCollegeId().equals(course.getOpenCollege())) {
                throw new BizException(403, "只能维护本学院开设的教学班");
            }
            return;
        }
        if (user.getTeacherId() == null || !user.getTeacherId().equals(tc.getTeacherId())) {
            throw new BizException(403, "只能维护本人任教的教学班");
        }
    }

    @Transactional
    public void changeStatus(String tcId, String status, String reason) {
        TeachingClass tc = tcMapper.selectById(tcId);
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        requireOwnership(tc);
        TeachingClass update = new TeachingClass();
        update.setTcId(tcId);
        update.setTcStatus(status);
        update.setNoOpenReason(reason);
        tcMapper.updateById(update);
        auditService.record("04", "teaching_class:" + tcId,
                Map.of("tcStatus", tc.getTcStatus()), Map.of("tcStatus", status));
    }

    @Transactional
    public void delete(String tcId) {
        TeachingClass tc = tcMapper.selectById(tcId);
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        requireOwnership(tc);
        if ("4".equals(tc.getTcStatus())) {
            throw new BizException("教学班已发布课表，不能删除");
        }
        long sched = scheduleResultMapper.selectCount(Wrappers.<ScheduleResult>lambdaQuery()
                .eq(ScheduleResult::getTcId, tcId));
        if (sched > 0) {
            throw new BizException("该教学班已排课，请先调整或删除排课结果");
        }
        tcMapper.deleteById(tcId);
        auditService.record("03", "teaching_class:" + tcId, tc, null);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
