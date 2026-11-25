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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                return listForTeacher(user.getTeacherId(), term, keyword, tcStatus);
            }
            classId = user.getClassId();
        }
        return viewMapper.listTeachingClasses(term, keyword, tcStatus, teacherId, collegeId, classId);
    }

    /** 教师视角：本人教学班 + 本学院待承接的选修课（状态 6） */
    private List<Map<String, Object>> listForTeacher(String teacherId, String term, String keyword,
                                                     String tcStatus) {
        List<Map<String, Object>> mine = new ArrayList<>(
                viewMapper.listTeachingClasses(term, keyword, tcStatus, teacherId, null, null));
        if (tcStatus != null && !tcStatus.isBlank() && !"6".equals(tcStatus)) {
            return mine;
        }
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || teacher.getCollegeId() == null) {
            return mine;
        }
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> row : mine) {
            ids.add(String.valueOf(row.get("tcId")));
        }
        List<Map<String, Object>> pool = viewMapper.listTeachingClasses(term, keyword, "6", null,
                teacher.getCollegeId(), null);
        for (Map<String, Object> row : pool) {
            if (ids.add(String.valueOf(row.get("tcId")))) {
                mine.add(row);
            }
        }
        mine.sort((a, b) -> String.valueOf(a.get("tcId")).compareTo(String.valueOf(b.get("tcId"))));
        return mine;
    }

    public List<TeachingClass> listByTerm(String term) {
        return tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term)
                .orderByAsc(TeachingClass::getTcId));
    }

    /**
     * 编排教学班时可选的面向班级：取培养方案中该学期需要修读该课程的班级，
     * 并剔除该学期该课程已编排过教学班的班级。
     */
    public List<Map<String, Object>> classCandidates(String term, String courseId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (term == null || term.isBlank() || courseId == null || courseId.isBlank()) {
            return rows;
        }
        Set<String> published = publishedProgramKeys();
        List<ProgramCourse> planCourses = programCourseMapper.selectList(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getCourseId, courseId));
        Set<String> majorGrades = new LinkedHashSet<>();
        for (ProgramCourse pc : planCourses) {
            if (pc.getMajorId() == null || pc.getGrade() == null) {
                continue;
            }
            if (!published.isEmpty()
                    && !published.contains(pc.getMajorId() + "|" + pc.getGrade() + "|" + pc.getProgramType())) {
                continue;
            }
            if (!term.equals(termFor(pc.getGrade(), pc.getOpenSemester(), term))) {
                continue;
            }
            majorGrades.add(pc.getMajorId() + "|" + pc.getGrade());
        }
        if (majorGrades.isEmpty()) {
            return rows;
        }
        Set<String> scheduled = scheduledClassIds(term, courseId);
        List<ClassInfo> classes = classInfoMapper.selectList(Wrappers.<ClassInfo>lambdaQuery()
                .orderByAsc(ClassInfo::getClassId));
        for (ClassInfo ci : classes) {
            if (ci.getMajorId() == null || ci.getGrade() == null) {
                continue;
            }
            if (!majorGrades.contains(ci.getMajorId() + "|" + ci.getGrade())) {
                continue;
            }
            if (scheduled.contains(ci.getClassId())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("classId", ci.getClassId());
            row.put("className", ci.getClassName());
            row.put("majorId", ci.getMajorId());
            row.put("grade", ci.getGrade());
            row.put("studentNum", ci.getStudentNum() == null ? 0 : ci.getStudentNum());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 教务处建班时可选课程：本学院开设、在用培养方案中本学期开课、且仍有班级未编排教学班。
     */
    public List<Map<String, Object>> courseCandidates(String term, String collegeId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (term == null || term.isBlank() || collegeId == null || collegeId.isBlank()) {
            return rows;
        }
        Set<String> published = publishedProgramKeys();
        Map<String, Set<String>> courseMajorGrades = new HashMap<>();
        for (ProgramCourse pc : programCourseMapper.selectList(Wrappers.<ProgramCourse>lambdaQuery())) {
            if (pc.getMajorId() == null || pc.getGrade() == null) {
                continue;
            }
            String key = pc.getMajorId() + "|" + pc.getGrade() + "|" + pc.getProgramType();
            if (!published.isEmpty() && !published.contains(key)) {
                continue;
            }
            if (!term.equals(termFor(pc.getGrade(), pc.getOpenSemester(), term))) {
                continue;
            }
            courseMajorGrades.computeIfAbsent(pc.getCourseId(), k -> new LinkedHashSet<>())
                    .add(pc.getMajorId() + "|" + pc.getGrade());
        }
        if (courseMajorGrades.isEmpty()) {
            return rows;
        }
        List<ClassInfo> classes = classInfoMapper.selectList(Wrappers.<ClassInfo>lambdaQuery());
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getOpenCollege, collegeId)
                .eq(Course::getCourseStatus, "1")
                .orderByAsc(Course::getCourseId));
        for (Course course : courses) {
            Set<String> majorGrades = courseMajorGrades.get(course.getCourseId());
            if (majorGrades == null || majorGrades.isEmpty()) {
                continue;
            }
            Set<String> scheduled = scheduledClassIds(term, course.getCourseId());
            int unassigned = 0;
            for (ClassInfo ci : classes) {
                if (ci.getMajorId() == null || ci.getGrade() == null) {
                    continue;
                }
                if (!majorGrades.contains(ci.getMajorId() + "|" + ci.getGrade())) {
                    continue;
                }
                if (scheduled.contains(ci.getClassId())) {
                    continue;
                }
                unassigned++;
            }
            if (unassigned == 0) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("courseId", course.getCourseId());
            row.put("courseName", course.getCourseName());
            row.put("courseType", course.getCourseType());
            row.put("totalHours", course.getTotalHours());
            row.put("theoryHours", course.getTheoryHours());
            row.put("practiceHours", course.getPracticeHours());
            row.put("practiceRoomType", course.getPracticeRoomType());
            row.put("openCollege", course.getOpenCollege());
            row.put("unassignedClassCount", unassigned);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 选修课建班候选课程：开课学院在用的全部课程中，本学期尚未建立教学班的课程。
     * 选修课不匹配行政班，建班后直接下发教师承接。
     */
    public List<Map<String, Object>> electiveCandidates(String term, String collegeId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (term == null || term.isBlank() || collegeId == null || collegeId.isBlank()) {
            return rows;
        }
        Set<String> scheduled = new HashSet<>();
        for (TeachingClass tc : tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term))) {
            if (tc.getCourseId() != null) {
                scheduled.add(tc.getCourseId());
            }
        }
        // 选修课建班仅限专业选修（14）与素质拓展（17）类课程
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getOpenCollege, collegeId)
                .eq(Course::getCourseStatus, "1")
                .in(Course::getCourseType, "14", "17")
                .orderByAsc(Course::getCourseId));
        for (Course course : courses) {
            if (scheduled.contains(course.getCourseId())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("courseId", course.getCourseId());
            row.put("courseName", course.getCourseName());
            row.put("courseType", course.getCourseType());
            row.put("totalHours", course.getTotalHours());
            row.put("theoryHours", course.getTheoryHours());
            row.put("practiceHours", course.getPracticeHours());
            row.put("practiceRoomType", course.getPracticeRoomType());
            row.put("openCollege", course.getOpenCollege());
            rows.add(row);
        }
        return rows;
    }

    /** 在用（已发布）培养方案的 专业|年级|类型 集合 */
    private Set<String> publishedProgramKeys() {
        Set<String> keys = new HashSet<>();
        for (MajorProgram p : programMapper.selectList(Wrappers.<MajorProgram>lambdaQuery())) {
            if (!"3".equals(p.getPlanStatus())) {
                continue;
            }
            keys.add(p.getMajorId() + "|" + p.getGrade() + "|" + p.getProgramType());
        }
        return keys;
    }

    /** 某学期某课程已编排教学班覆盖的班级 */
    private Set<String> scheduledClassIds(String term, String courseId) {
        Set<String> scheduled = new HashSet<>();
        for (TeachingClass tc : tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term)
                .eq(TeachingClass::getCourseId, courseId))) {
            if (tc.getClassIds() == null || tc.getClassIds().isBlank()) {
                continue;
            }
            for (String id : tc.getClassIds().split("[,，]")) {
                if (!id.isBlank()) {
                    scheduled.add(id.trim());
                }
            }
        }
        return scheduled;
    }

    @Transactional
    public void save(Map<String, Object> body) {
        String tcId = str(body.get("tcId"));
        TeachingClass tc;
        boolean isNew = tcId == null || tcId.isBlank();
        // 选修课教学班不匹配行政班，由教师承接并设定容量后报教务处审核
        boolean elective = "2".equals(str(body.get("tcType")));
        LoginUser current = UserContext.require();
        // 教师（本人数据范围）负责本人教学班的学时划分与偏好设置
        boolean isTeacher = !isNew && "3".equals(current.getDataScope());
        if (isNew) {
            // 教学班由教务处统一建立，学院教务负责分配教师
            if (!"1".equals(current.getDataScope())) {
                throw new BizException(403, "教学班由教务处统一建立，学院教务负责分配教师");
            }
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
        // 任课教师由学院教务通过分配接口指派，保存时不做变更

        if (elective) {
            // 选修课仅可选用专业选修（14）与素质拓展（17）类课程
            if (isNew) {
                Course course = courseMapper.selectById(tc.getCourseId());
                if (course == null) {
                    throw new BizException("课程不存在");
                }
                if (!"14".equals(course.getCourseType()) && !"17".equals(course.getCourseType())) {
                    throw new BizException("选修课教学班仅可选择专业选修或素质拓展类课程");
                }
            }
            // 选修课不匹配行政班，容量由承接教师设定
            tc.setClassIds(null);
            if (isNew) {
                tc.setStudentNum(0);
                tc.setCapacity(0);
            } else if (body.get("capacity") != null) {
                tc.setCapacity(Integer.valueOf(str(body.get("capacity"))));
            }
        } else {
            if (body.get("classIds") != null) {
                tc.setClassIds(str(body.get("classIds")));
            }
            if (body.get("studentNum") != null) {
                tc.setStudentNum(Integer.valueOf(str(body.get("studentNum"))));
            }
            if (body.get("capacity") != null) {
                tc.setCapacity(Integer.valueOf(str(body.get("capacity"))));
            } else if (isNew) {
                // 容量上限由排课教室决定，新建时置 0 表示待定
                tc.setCapacity(0);
            }
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
        // 上课周数：16 周或 8 周；8 周可选前 8 周（起始周 1）或后 8 周（起始周 9）
        // 权限：选修课由任课教师选择，必修课由开课学院教务/教务处选择
        boolean weekEditable = isNew || "1".equals(current.getDataScope()) || "2".equals(current.getDataScope())
                || (isTeacher && "2".equals(tc.getTcType()));
        if (weekEditable) {
            int weekCount = body.get("weekCount") == null
                    ? (tc.getWeekCount() == null ? 16 : tc.getWeekCount())
                    : Integer.parseInt(str(body.get("weekCount")));
            int weekStart = body.get("weekStart") == null
                    ? (tc.getWeekStart() == null ? 1 : tc.getWeekStart())
                    : Integer.parseInt(str(body.get("weekStart")));
            validateWeeks(weekCount, weekStart);
            tc.setWeekCount(weekCount);
            tc.setWeekStart(weekStart);
        }
        // 新建时沿用课程库的学时划分；教师可在本人教学班内调整；教务人员编辑时不改学时
        if (isNew) {
            Course course = courseMapper.selectById(tc.getCourseId());
            if (course != null && course.getTheoryHours() != null) {
                tc.setHours(course.getTotalHours());
                tc.setTheoryHours(course.getTheoryHours());
                tc.setPracticeHours(course.getPracticeHours() == null ? 0 : course.getPracticeHours());
            } else {
                applyAutoHours(tc);
            }
        } else if (isTeacher) {
            if (body.get("theoryHours") != null) {
                tc.setTheoryHours(Integer.valueOf(str(body.get("theoryHours"))));
            }
            if (body.get("practiceHours") != null) {
                tc.setPracticeHours(Integer.valueOf(str(body.get("practiceHours"))));
            }
        }
        if (tc.getTheoryHours() == null) {
            tc.setTheoryHours(0);
        }
        if (tc.getPracticeHours() == null) {
            tc.setPracticeHours(0);
        }
        if (tc.getHours() != null && tc.getTheoryHours() + tc.getPracticeHours() != tc.getHours()) {
            throw new BizException("理论学时与实践学时之和应等于计划学时");
        }
        // 建班时校验：每 1 学分不少于 16 学时（8 节课 × 2 学时）
        if (isNew) {
            validateCreditHours(tc);
        }
        // 教师划分实践学时：出现实践学时则不少于 8 学时，且按 4 学时递增
        if (isTeacher && tc.getPracticeHours() != null && tc.getPracticeHours() > 0) {
            if (tc.getPracticeHours() < 8) {
                throw new BizException("实践学时不少于 8 学时");
            }
            if (tc.getPracticeHours() % 4 != 0) {
                throw new BizException("实践学时须按 4 学时递增");
            }
        }
        if (body.get("preferSlot") != null) {
            tc.setPreferSlot(str(body.get("preferSlot")));
        }
        if (body.get("unavailableTime") != null) {
            tc.setUnavailableTime(str(body.get("unavailableTime")));
        }
        if (tc.getCapacity() != null && tc.getCapacity() > 0 && tc.getStudentNum() != null
                && tc.getCapacity() < tc.getStudentNum()) {
            throw new BizException("容量上限不得小于面向班级人数（BR-04）");
        }
        if (isNew) {
            // 必修：待学院教务分配教师；选修：下发教师承接并设定容量
            tc.setTcStatus(elective ? "6" : "0");
            tcMapper.insert(tc);
            auditService.record("01", "teaching_class:" + tc.getTcId(), null, tc);
            return;
        }
        // 教师（本人数据范围）负责理论/实践学时划分与偏好时段、不可用时间；其余字段由教务处/学院教务员维护（P0-03）
        if (isTeacher) {
            TeachingClass limited = new TeachingClass();
            limited.setTcId(tc.getTcId());
            limited.setTheoryHours(tc.getTheoryHours());
            limited.setPracticeHours(tc.getPracticeHours());
            limited.setPreferSlot(tc.getPreferSlot());
            limited.setUnavailableTime(tc.getUnavailableTime());
            // 选修课教师可自行选择上课周数（16 周 / 前 8 周 / 后 8 周）
            if ("2".equals(tc.getTcType())) {
                limited.setWeekCount(tc.getWeekCount());
                limited.setWeekStart(tc.getWeekStart());
            }
            tcMapper.updateById(limited);
            auditService.record("02", "teaching_class:" + tc.getTcId(), null,
                    Map.of("theoryHours", tc.getTheoryHours() == null ? 0 : tc.getTheoryHours(),
                            "practiceHours", tc.getPracticeHours() == null ? 0 : tc.getPracticeHours(),
                            "preferSlot", tc.getPreferSlot() == null ? "" : tc.getPreferSlot(),
                            "unavailableTime", tc.getUnavailableTime() == null ? "" : tc.getUnavailableTime()));
            return;
        }
        tcMapper.updateById(tc);
        auditService.record("02", "teaching_class:" + tc.getTcId(), null, tc);
    }

    /** 周数校验：仅支持 16 周（第 1 周起）或 8 周（前 8 周 / 后 8 周） */
    private void validateWeeks(int weekCount, int weekStart) {
        if (weekCount != 8 && weekCount != 16) {
            throw new BizException("上课周数只能为 8 周或 16 周");
        }
        if (weekCount == 16 && weekStart != 1) {
            throw new BizException("16 周课程须从第 1 周开始");
        }
        if (weekCount == 8 && weekStart != 1 && weekStart != 9) {
            throw new BizException("8 周课程只能安排在前 8 周或后 8 周");
        }
    }

    /** 学分学时下限：每 1 学分不少于 16 学时（集中实践、创新创业类按周安排，不适用） */
    private void validateCreditHours(TeachingClass tc) {
        Course course = courseMapper.selectById(tc.getCourseId());
        if (course == null || course.getCredit() == null || tc.getHours() == null) {
            return;
        }
        if (Arrays.asList("16", "17").contains(course.getCourseType())) {
            return;
        }
        int minHours = course.getCredit().multiply(BigDecimal.valueOf(16)).intValue();
        if (tc.getHours() < minHours) {
            throw new BizException("计划学时不足：" + course.getCourseName() + " 为 "
                    + course.getCredit().stripTrailingZeros().toPlainString()
                    + " 学分，每学分不少于 16 学时，至少需 " + minHours + " 学时");
        }
    }

    /** 按课程类型自动划分学时：实践类课程全部计入实践学时，其余全部计入理论学时 */
    private void applyAutoHours(TeachingClass tc) {
        Course course = courseMapper.selectById(tc.getCourseId());
        int total = tc.getHours() == null ? 0 : tc.getHours();
        if (course != null && "16".equals(course.getCourseType())) {
            tc.setTheoryHours(0);
            tc.setPracticeHours(total);
        } else {
            tc.setTheoryHours(total);
            tc.setPracticeHours(0);
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
            // 容量上限由排课教室决定，批量生成时置 0 待定
            tc.setCapacity(0);
            tc.setTcType("1");
            Course course = courseMapper.selectById(pc.getCourseId());
            tc.setTeachType(course != null && "0901006".equals(course.getCourseId()) ? "3" : "1");
            int hours = course != null && course.getTotalHours() != null
                    ? course.getTotalHours()
                    : (pc.getHours() == null ? 0 : pc.getHours());
            tc.setHours(hours);
            if (course != null && course.getTheoryHours() != null) {
                tc.setTheoryHours(course.getTheoryHours());
                tc.setPracticeHours(course.getPracticeHours() == null ? 0 : course.getPracticeHours());
            } else {
                applyAutoHours(tc);
            }
            tc.setWeekCount(16);
            tc.setMulticlassPrefer("1");
            tc.setOpenLower(10);
            tc.setSource("1");
            // 教师由学院教务分配、教师确认承接，批量生成统一进入待分配教师状态
            tc.setTcStatus("0");
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

    /** 学院教务为教学班分配本学院教师，分配后等待教师确认承接 */
    @Transactional
    public void assignTeacher(String tcId, String teacherId) {
        TeachingClass tc = requireTc(tcId);
        LoginUser user = UserContext.require();
        Course course = courseMapper.selectById(tc.getCourseId());
        if (course == null) {
            throw new BizException("课程不存在");
        }
        if ("2".equals(user.getDataScope())) {
            if (user.getCollegeId() == null || !user.getCollegeId().equals(course.getOpenCollege())) {
                throw new BizException(403, "只能为开课学院为本学院的教学班分配教师");
            }
        } else if (!"1".equals(user.getDataScope())) {
            throw new BizException(403, "仅学院教务可分配任课教师");
        }
        if (!"0".equals(tc.getTcStatus()) && !"5".equals(tc.getTcStatus())) {
            throw new BizException("该教学班当前状态不支持分配教师");
        }
        if (teacherId == null || teacherId.isBlank()) {
            throw new BizException("请选择任课教师");
        }
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"1".equals(teacher.getTeacherStatus())) {
            throw new BizException("教师不存在或已不在职");
        }
        if (course.getOpenCollege() != null && !course.getOpenCollege().equals(teacher.getCollegeId())) {
            throw new BizException("任课教师须为开课学院教师");
        }
        TeachingClass update = new TeachingClass();
        update.setTcId(tcId);
        update.setTeacherId(teacherId);
        update.setTcStatus("5");
        tcMapper.updateById(update);
        auditService.record("02", "teaching_class:" + tcId,
                Map.of("teacherId", tc.getTeacherId() == null ? "" : tc.getTeacherId(),
                        "tcStatus", tc.getTcStatus() == null ? "" : tc.getTcStatus()),
                Map.of("teacherId", teacherId, "tcStatus", "5"));
    }

    /** 任课教师确认承接：教学班建立完毕，可进入排课 */
    @Transactional
    public void confirm(String tcId) {
        TeachingClass tc = requireTc(tcId);
        LoginUser user = UserContext.require();
        if (user.getTeacherId() == null || !user.getTeacherId().equals(tc.getTeacherId())) {
            throw new BizException(403, "只能确认本人承接的教学班");
        }
        if (!"5".equals(tc.getTcStatus()) && !"0".equals(tc.getTcStatus())) {
            throw new BizException("该教学班当前状态不支持确认承接");
        }
        TeachingClass update = new TeachingClass();
        update.setTcId(tcId);
        update.setTcStatus("1");
        tcMapper.updateById(update);
        auditService.record("04", "teaching_class:" + tcId,
                Map.of("tcStatus", tc.getTcStatus() == null ? "" : tc.getTcStatus()),
                Map.of("tcStatus", "1"));
    }

    /** 任课教师拒绝承接：退回学院教务重新分配 */
    @Transactional
    public void decline(String tcId, String reason) {
        TeachingClass tc = requireTc(tcId);
        LoginUser user = UserContext.require();
        if (user.getTeacherId() == null || !user.getTeacherId().equals(tc.getTeacherId())) {
            throw new BizException(403, "只能处理本人承接的教学班");
        }
        if (!"5".equals(tc.getTcStatus()) && !"0".equals(tc.getTcStatus())) {
            throw new BizException("该教学班当前状态不支持拒绝承接");
        }
        tcMapper.update(null, Wrappers.<TeachingClass>lambdaUpdate()
                .eq(TeachingClass::getTcId, tcId)
                .setSql("teacher_id = NULL")
                .set(TeachingClass::getTcStatus, "0"));
        auditService.record("04", "teaching_class:" + tcId,
                Map.of("tcStatus", tc.getTcStatus() == null ? "" : tc.getTcStatus(),
                        "teacherId", tc.getTeacherId() == null ? "" : tc.getTeacherId()),
                Map.of("tcStatus", "0", "reason", reason == null ? "" : reason));
    }

    /** 教师承接选修课并设定课程容量，提交教务处审核 */
    @Transactional
    public void claim(String tcId, Integer capacity) {
        TeachingClass tc = requireTc(tcId);
        LoginUser user = UserContext.require();
        if (user.getTeacherId() == null) {
            throw new BizException(403, "仅任课教师可承接选修课程");
        }
        Teacher teacher = teacherMapper.selectById(user.getTeacherId());
        Course course = tc.getCourseId() == null ? null : courseMapper.selectById(tc.getCourseId());
        if (teacher == null || course == null) {
            throw new BizException("教师或课程信息不存在");
        }
        if (course.getOpenCollege() != null && !course.getOpenCollege().equals(teacher.getCollegeId())) {
            throw new BizException(403, "只能承接本学院开设的选修课程");
        }
        if (!"6".equals(tc.getTcStatus())) {
            throw new BizException("该教学班当前状态不支持承接");
        }
        if (capacity == null || capacity <= 0) {
            throw new BizException("请设置课程容量");
        }
        TeachingClass update = new TeachingClass();
        update.setTcId(tcId);
        update.setTeacherId(user.getTeacherId());
        update.setCapacity(capacity);
        update.setTcStatus("7");
        tcMapper.updateById(update);
        auditService.record("04", "teaching_class:" + tcId,
                Map.of("tcStatus", tc.getTcStatus() == null ? "" : tc.getTcStatus()),
                Map.of("tcStatus", "7", "teacherId", user.getTeacherId(), "capacity", capacity));
    }

    /** 教务处审核选修课教学班：通过后可排课，驳回则退回教师重新设定 */
    @Transactional
    public void review(String tcId, boolean pass, String reason) {
        TeachingClass tc = requireTc(tcId);
        LoginUser user = UserContext.require();
        if (!"1".equals(user.getDataScope())) {
            throw new BizException(403, "仅教务处可审核选修课教学班");
        }
        if (!"7".equals(tc.getTcStatus())) {
            throw new BizException("该教学班不在待审核状态");
        }
        if (pass) {
            TeachingClass update = new TeachingClass();
            update.setTcId(tcId);
            update.setTcStatus("1");
            tcMapper.updateById(update);
            auditService.record("03", "teaching_class:" + tcId,
                    Map.of("tcStatus", "7"), Map.of("tcStatus", "1"));
            return;
        }
        tcMapper.update(null, Wrappers.<TeachingClass>lambdaUpdate()
                .eq(TeachingClass::getTcId, tcId)
                .setSql("teacher_id = NULL")
                .set(TeachingClass::getCapacity, 0)
                .set(TeachingClass::getTcStatus, "6"));
        auditService.record("03", "teaching_class:" + tcId,
                Map.of("tcStatus", "7"), Map.of("tcStatus", "6", "reason", reason == null ? "" : reason));
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
