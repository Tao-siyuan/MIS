package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.ClassInfo;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.entity.TcStudent;
import com.ustb.mis.entity.Teacher;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.ClassInfoMapper;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.SysUserMapper;
import com.ustb.mis.mapper.TcStudentMapper;
import com.ustb.mis.mapper.TeacherMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final SysUserMapper sysUserMapper;
    private final TcStudentMapper tcStudentMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public TeachingClassService(TeachingClassMapper tcMapper, ProgramCourseMapper programCourseMapper,
                                MajorProgramMapper programMapper, ClassInfoMapper classInfoMapper,
                                TeacherMapper teacherMapper, CourseMapper courseMapper,
                                ScheduleResultMapper scheduleResultMapper, ViewMapper viewMapper,
                                SysUserMapper sysUserMapper, TcStudentMapper tcStudentMapper,
                                CodeService codeService, AuditService auditService) {
        this.tcMapper = tcMapper;
        this.programCourseMapper = programCourseMapper;
        this.programMapper = programMapper;
        this.classInfoMapper = classInfoMapper;
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.scheduleResultMapper = scheduleResultMapper;
        this.viewMapper = viewMapper;
        this.sysUserMapper = sysUserMapper;
        this.tcStudentMapper = tcStudentMapper;
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
            if (semesterIndex(grade, pc.getOpenSemester()) >= 8) {
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

    /** 方案课程开课学期已是完整学期（YYYY-YYYY-N），直接返回；旧数据单个数字按年级推算兼容 */
    private String termFor(String grade, String openSemester, String fallback) {
        if (openSemester == null || openSemester.isBlank()) {
            return fallback;
        }
        if (openSemester.matches("\\d{4}-\\d{4}-[1-3]")) {
            return openSemester;
        }
        if (grade != null && grade.matches("\\d{4}") && openSemester.matches("[1-8]")) {
            int enrollYear = Integer.parseInt(grade);
            int semester = Integer.parseInt(openSemester);
            int year = enrollYear + (semester - 1) / 2;
            return year + "-" + (year + 1) + "-" + ((semester - 1) % 2 + 1);
        }
        return fallback;
    }

    /** 计算课程在大学八学期中的序号（完整学期按入学年级换算，兼容旧数字） */
    private int semesterIndex(String grade, String openSemester) {
        if (openSemester == null || openSemester.isBlank()) {
            return 0;
        }
        if (openSemester.matches("[1-8]")) {
            return Integer.parseInt(openSemester);
        }
        if (grade != null && grade.matches("\\d{4}") && openSemester.matches("\\d{4}-\\d{4}-[1-3]")) {
            String[] parts = openSemester.split("-");
            int yearDiff = Integer.parseInt(parts[0]) - Integer.parseInt(grade);
            return Math.max(0, yearDiff * 2 + Integer.parseInt(parts[2]));
        }
        return 0;
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

    // ---------------- 教学班学生名单（教师可往本人教学班内添加学生） ----------------

    /** 教学班现有学生名单 */
    public List<Map<String, Object>> students(String tcId) {
        TeachingClass tc = requireTc(tcId);
        requireOwnership(tc);
        List<String> stuIds = tcStudentMapper.selectList(Wrappers.<TcStudent>lambdaQuery()
                        .eq(TcStudent::getTcId, tcId)).stream()
                .map(TcStudent::getStuId)
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (stuIds.isEmpty()) {
            return rows;
        }
        List<SysUser> students = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getUserId, stuIds)
                .orderByAsc(SysUser::getUserId));
        Map<String, String> classNames = classNameMap(students);
        for (SysUser stu : students) {
            Map<String, Object> row = new HashMap<>();
            row.put("stuId", stu.getUserId());
            row.put("stuName", stu.getRealName());
            row.put("classId", stu.getClassId());
            row.put("className", classNames.getOrDefault(stu.getClassId(), "-"));
            rows.add(row);
        }
        return rows;
    }

    /** 可加入教学班的候选学生（未被该教学班收录，可按学号/姓名检索） */
    public List<Map<String, Object>> studentCandidates(String tcId, String keyword) {
        TeachingClass tc = requireTc(tcId);
        requireOwnership(tc);
        List<String> joined = tcStudentMapper.selectList(Wrappers.<TcStudent>lambdaQuery()
                        .eq(TcStudent::getTcId, tcId)).stream()
                .map(TcStudent::getStuId)
                .toList();
        List<SysUser> students = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleId, "R05")
                .eq(SysUser::getStatus, "1")
                .notIn(!joined.isEmpty(), SysUser::getUserId, joined)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUserId, keyword)
                        .or()
                        .like(SysUser::getRealName, keyword))
                .orderByAsc(SysUser::getUserId)
                .last("LIMIT 50"));
        Map<String, String> classNames = classNameMap(students);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser stu : students) {
            Map<String, Object> row = new HashMap<>();
            row.put("stuId", stu.getUserId());
            row.put("stuName", stu.getRealName());
            row.put("classId", stu.getClassId());
            row.put("className", classNames.getOrDefault(stu.getClassId(), "-"));
            rows.add(row);
        }
        return rows;
    }

    /** 教师向本人教学班添加学生：按学号+姓名核对身份，校验容量上限，并同步实际选课人数 */
    @Transactional
    public void addStudent(String tcId, String stuId, String stuName) {
        TeachingClass tc = requireTc(tcId);
        requireOwnership(tc);
        if (stuId == null || stuId.isBlank()) {
            throw new BizException("请输入学生学号");
        }
        SysUser stu = sysUserMapper.selectById(stuId.trim());
        if (stu == null || !"R05".equals(stu.getRoleId())) {
            throw new BizException("学生不存在或不是在校本科生，请核对学号");
        }
        if (stuName != null && !stuName.isBlank() && !stuName.trim().equals(stu.getRealName())) {
            throw new BizException("学号与姓名不匹配，请核对后重新添加");
        }
        long exists = tcStudentMapper.selectCount(Wrappers.<TcStudent>lambdaQuery()
                .eq(TcStudent::getTcId, tcId)
                .eq(TcStudent::getStuId, stu.getUserId()));
        if (exists > 0) {
            throw new BizException("该学生已在教学班名单中");
        }
        long count = tcStudentMapper.selectCount(Wrappers.<TcStudent>lambdaQuery()
                .eq(TcStudent::getTcId, tcId));
        if (tc.getCapacity() != null && count >= tc.getCapacity()) {
            throw new BizException("已达教学班容量上限 " + tc.getCapacity() + " 人，无法继续添加");
        }
        TcStudent row = new TcStudent();
        row.setTcId(tcId);
        row.setStuId(stu.getUserId());
        row.setAddTime(LocalDateTime.now());
        tcStudentMapper.insert(row);
        syncActualNum(tcId);
        auditService.record("01", "tc_student:" + tcId + "-" + stu.getUserId(), null,
                Map.of("tcId", tcId, "stuId", stu.getUserId()));
    }

    /** 从教学班名单中移除学生 */
    @Transactional
    public void removeStudent(String tcId, String stuId) {
        TeachingClass tc = requireTc(tcId);
        requireOwnership(tc);
        long exists = tcStudentMapper.selectCount(Wrappers.<TcStudent>lambdaQuery()
                .eq(TcStudent::getTcId, tcId)
                .eq(TcStudent::getStuId, stuId));
        if (exists == 0) {
            throw new BizException("该学生不在教学班名单中");
        }
        tcStudentMapper.delete(Wrappers.<TcStudent>lambdaQuery()
                .eq(TcStudent::getTcId, tcId)
                .eq(TcStudent::getStuId, stuId));
        syncActualNum(tcId);
        auditService.record("03", "tc_student:" + tcId + "-" + stuId,
                Map.of("tcId", tcId, "stuId", stuId), null);
    }

    private TeachingClass requireTc(String tcId) {
        TeachingClass tc = tcMapper.selectById(tcId);
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        return tc;
    }

    /** 名单变动后回写实际选课人数，便于后续开课/停开判断 */
    private void syncActualNum(String tcId) {
        long count = tcStudentMapper.selectCount(Wrappers.<TcStudent>lambdaQuery()
                .eq(TcStudent::getTcId, tcId));
        TeachingClass update = new TeachingClass();
        update.setTcId(tcId);
        update.setActualNum((int) count);
        tcMapper.updateById(update);
    }

    private Map<String, String> classNameMap(List<SysUser> users) {
        List<String> classIds = users.stream()
                .map(SysUser::getClassId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, String> map = new HashMap<>();
        if (classIds.isEmpty()) {
            return map;
        }
        for (ClassInfo ci : classInfoMapper.selectList(Wrappers.<ClassInfo>lambdaQuery()
                .in(ClassInfo::getClassId, classIds))) {
            map.put(ci.getClassId(), ci.getClassName());
        }
        return map;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
