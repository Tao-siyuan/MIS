package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.NewCourseApply;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.NewCourseApplyMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.SysUserMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.AuditService;
import com.ustb.mis.service.CodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课程设置与课程库维护模块（对应 3.5.2）。
 */
@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseMapper courseMapper;
    private final NewCourseApplyMapper applyMapper;
    private final ProgramCourseMapper programCourseMapper;
    private final TeachingClassMapper tcMapper;
    private final ScheduleResultMapper schedMapper;
    private final ViewMapper viewMapper;
    private final SysUserMapper sysUserMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public CourseController(CourseMapper courseMapper, NewCourseApplyMapper applyMapper,
                            ProgramCourseMapper programCourseMapper, TeachingClassMapper tcMapper,
                            ScheduleResultMapper schedMapper, ViewMapper viewMapper,
                            SysUserMapper sysUserMapper, CodeService codeService,
                            AuditService auditService) {
        this.courseMapper = courseMapper;
        this.applyMapper = applyMapper;
        this.programCourseMapper = programCourseMapper;
        this.tcMapper = tcMapper;
        this.schedMapper = schedMapper;
        this.viewMapper = viewMapper;
        this.sysUserMapper = sysUserMapper;
        this.codeService = codeService;
        this.auditService = auditService;
    }

    @GetMapping("/courses")
    @RequirePerm({"P05", "P02", "P08", "P09", "P13", "P14", "P15", "P06"})
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String courseType,
                                                  @RequestParam(required = false) String collegeId,
                                                  @RequestParam(required = false) String status) {
        LoginUser user = UserContext.get();
        if (user != null && "2".equals(user.getDataScope()) && (collegeId == null || collegeId.isBlank())) {
            collegeId = null;
        }
        return Result.ok(viewMapper.listCourses(keyword, courseType, collegeId, status));
    }

    @PostMapping("/courses")
    @RequirePerm("P05")
    public Result<Void> add(@RequestBody Course course) {
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new BizException("课程名称不能为空");
        }
        requireCourseTypeAllowed(course.getCourseType());
        requireOpenCollegeAllowed(course.getOpenCollege());
        if (courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseName, course.getCourseName())) > 0) {
            throw new BizException("课程名称已存在");
        }
        if (course.getCourseId() == null || !course.getCourseId().matches("\\d{7}")) {
            course.setCourseId(codeService.nextCourseId(course.getOpenCollege()));
        }
        if (course.getCredit() != null && course.getTotalHours() != null
                && "16".equals(course.getCourseType())
                && course.getCredit().multiply(new BigDecimal(16)).compareTo(
                BigDecimal.valueOf(course.getTotalHours())) != 0) {
            throw new BizException("学分与学时不符合 1∶16 的换算关系，请确认");
        }
        // 未指定学时划分时默认全部计入理论学时
        if (course.getTheoryHours() == null && course.getPracticeHours() == null) {
            int total = course.getTotalHours() == null ? 0 : course.getTotalHours();
            course.setTheoryHours(total);
            course.setPracticeHours(0);
        }
        validateHours(course);
        course.setCourseStatus("1");
        courseMapper.insert(course);
        auditService.record("01", "course:" + course.getCourseId(), null, course);
        return Result.ok();
    }

    /** 已被方案引用的课程仅允许修改大纲与考核方式；学时划分仅系统管理员与教务处管理员可调整（P1-05） */
    @PutMapping("/courses/{id}")
    @RequirePerm("P05")
    public Result<Void> update(@PathVariable String id, @RequestBody Course course) {
        Course old = courseMapper.selectById(id);
        if (old == null) {
            throw new BizException("课程不存在");
        }
        boolean hoursChanged = (course.getTheoryHours() != null
                && !course.getTheoryHours().equals(old.getTheoryHours()))
                || (course.getPracticeHours() != null
                && !course.getPracticeHours().equals(old.getPracticeHours()))
                || (course.getPracticeRoomType() != null
                && !course.getPracticeRoomType().equals(old.getPracticeRoomType()));
        if (hoursChanged && !isSchoolAdmin(UserContext.require())) {
            throw new BizException(403, "仅系统管理员与教务处管理员可调整课程学时划分");
        }
        // 未提交的学时字段沿用原值，便于统一校验
        if (course.getTheoryHours() == null) {
            course.setTheoryHours(old.getTheoryHours() == null ? 0 : old.getTheoryHours());
        }
        if (course.getPracticeHours() == null) {
            course.setPracticeHours(old.getPracticeHours() == null ? 0 : old.getPracticeHours());
        }
        if (course.getPracticeRoomType() == null) {
            course.setPracticeRoomType(old.getPracticeRoomType());
        }
        validateHours(course);
        long used = programCourseMapper.selectCount(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getCourseId, id));
        if (used > 0) {
            boolean locked = (course.getCredit() != null && old.getCredit() != null
                    && course.getCredit().compareTo(old.getCredit()) != 0)
                    || (course.getTotalHours() != null && !course.getTotalHours().equals(old.getTotalHours()))
                    || (course.getCourseName() != null && !course.getCourseName().equals(old.getCourseName()))
                    || (course.getCourseType() != null && !course.getCourseType().equals(old.getCourseType()))
                    || (course.getOpenCollege() != null && !course.getOpenCollege().equals(old.getOpenCollege()));
            if (locked) {
                throw new BizException("该课程已被培养方案引用，仅允许修改课程大纲与考核方式");
            }
            Course update = new Course();
            update.setCourseId(id);
            update.setSyllabus(course.getSyllabus());
            update.setAssessType(course.getAssessType());
            update.setTheoryHours(course.getTheoryHours());
            update.setPracticeHours(course.getPracticeHours());
            update.setPracticeRoomType(course.getPracticeRoomType());
            courseMapper.updateById(update);
            if (hoursChanged) {
                syncTeachingClassHours(id, course);
            }
            auditService.record("02", "course:" + id, old, update);
            return Result.ok();
        }
        course.setCourseId(id);
        courseMapper.updateById(course);
        if (hoursChanged) {
            syncTeachingClassHours(id, course);
        }
        auditService.record("02", "course:" + id, old, course);
        return Result.ok();
    }

    /** 课程库学时划分变更后同步到该课程的教学班，保证教学班学时与课程库一致 */
    private void syncTeachingClassHours(String courseId, Course course) {
        var wrapper = Wrappers.<TeachingClass>lambdaUpdate()
                .eq(TeachingClass::getCourseId, courseId)
                .set(TeachingClass::getTheoryHours, course.getTheoryHours())
                .set(TeachingClass::getPracticeHours, course.getPracticeHours());
        if (course.getTotalHours() != null) {
            wrapper.set(TeachingClass::getHours, course.getTotalHours());
        }
        tcMapper.update(null, wrapper);
    }

    /** 院级教务员与教师仅可为本学院新增课程；校级账号不限制 */
    private void requireOpenCollegeAllowed(String collegeId) {
        LoginUser user = UserContext.require();
        if ("1".equals(user.getDataScope())) {
            return;
        }
        if (!user.getCollegeId().equals(collegeId)) {
            throw new BizException("仅可为本学院新增课程");
        }
    }

    /** 系统管理员(R01)与教务处管理员(R02)保留学时划分的修改权限 */
    private boolean isSchoolAdmin(LoginUser user) {
        return "R01".equals(user.getRoleId()) || "R02".equals(user.getRoleId());
    }

    /**
     * 新增课程类别按岗位限制（系统管理员不限制）：
     * 校级教务处→校级必修/通识；学院教务员→院级必修/专业必修/专业选修/实践教学；教师→素质拓展。
     */
    private void requireCourseTypeAllowed(String courseType) {
        LoginUser user = UserContext.require();
        if (isSchoolAdmin(user) && !"R02".equals(user.getRoleId())) {
            return;
        }
        String allowed;
        String label;
        switch (user.getDataScope() == null ? "" : user.getDataScope()) {
            case "1" -> {
                allowed = "11,15";
                label = "校级教务处仅可新增校级必修、通识课程";
            }
            case "2" -> {
                allowed = "12,13,14,16";
                label = "学院教务员仅可新增院级必修、专业必修、专业选修、实践教学课程";
            }
            default -> {
                allowed = "17";
                label = "教师仅可新增素质拓展课程";
            }
        }
        if (courseType == null || !java.util.Arrays.asList(allowed.split(",")).contains(courseType)) {
            throw new BizException(label);
        }
    }

    /** 学时划分校验：理论按 2 学时递增；实践为 0 或不少于 8 学时且按 4 学时递增，并须指定实践教室类型 */
    private void validateHours(Course course) {
        int total = course.getTotalHours() == null ? 0 : course.getTotalHours();
        int theory = course.getTheoryHours() == null ? 0 : course.getTheoryHours();
        int practice = course.getPracticeHours() == null ? 0 : course.getPracticeHours();
        if (theory < 0 || practice < 0) {
            throw new BizException("学时不能为负数");
        }
        if (theory + practice != total) {
            throw new BizException("理论学时与实践学时之和应等于总学时 " + total);
        }
        if (theory % 2 != 0) {
            throw new BizException("理论学时须按 2 学时递增");
        }
        if (practice > 0) {
            if (practice < 8) {
                throw new BizException("实践学时不少于 8 学时");
            }
            if (practice % 4 != 0) {
                throw new BizException("实践学时须按 4 学时递增");
            }
            String roomType = course.getPracticeRoomType();
            if (roomType == null || roomType.isBlank()) {
                throw new BizException("请选择实践学时所需教室类型");
            }
            if (!List.of("41", "42", "43", "44", "45", "46", "47").contains(roomType)) {
                throw new BizException("实践教室类型不正确");
            }
        } else {
            // 无实践学时则清空实践场地要求
            course.setPracticeRoomType("");
        }
    }

    /** 停开校验：覆盖 0/1/3/4 全部在用状态，并叠加排课结果判断（P1-04） */
    @PutMapping("/courses/{id}/status")
    @RequirePerm("P05")
    public Result<Map<String, Object>> changeStatus(@PathVariable String id, @RequestParam String status) {
        Course old = courseMapper.selectById(id);
        if (old == null) {
            throw new BizException("课程不存在");
        }
        List<String> affected = new ArrayList<>();
        if ("0".equals(status)) {
            List<TeachingClass> running = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                    .eq(TeachingClass::getCourseId, id)
                    .in(TeachingClass::getTcStatus, List.of("0", "1", "3", "4")));
            for (TeachingClass tc : running) {
                long sched = schedMapper.selectCount(Wrappers.<ScheduleResult>lambdaQuery()
                        .eq(ScheduleResult::getTcId, tc.getTcId()));
                affected.add(tc.getTcId() + "（状态 " + tc.getTcStatus() + "，排课 " + sched + " 段）");
            }
            if (!affected.isEmpty()) {
                throw new BizException("存在进行中或已发布的教学班，不能停开：" + String.join("；", affected));
            }
        }
        Course update = new Course();
        update.setCourseId(id);
        update.setCourseStatus(status);
        courseMapper.updateById(update);
        auditService.record("02", "course:" + id, Map.of("courseStatus", old.getCourseStatus()),
                Map.of("courseStatus", status));
        return Result.ok(Map.of("affected", affected));
    }

    // ---------------- 新增课程申报（教师申报 → 学院初审 → 教务处终审） ----------------

    /**
     * 申报列表：mine=1 只看本人；学院教务（数据范围 2）默认可看本学院教师申报与下发到本学院的通识课程。
     */
    @GetMapping("/new-course-applies")
    @RequirePerm({"P06", "P07", "P21", "P05"})
    public Result<List<Map<String, Object>>> applies(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String mine) {
        LoginUser user = UserContext.require();
        String applyUser = "1".equals(mine) ? user.getUserId() : null;
        String collegeId = null;
        if ("2".equals(user.getDataScope()) && user.getCollegeId() != null) {
            collegeId = user.getCollegeId();
        }
        return Result.ok(new ArrayList<>(viewMapper.listNewCourseApplies(status, applyUser, collegeId)));
    }

    /**
     * 课程申报：教师限素质拓展（17）；学院教务限院级必修（12）/专业必修（13）/专业选修（14）/实践教学（16）；
     * 教务处限校级必修（11）/通识（15），通识须指定下发学院，由学院教务补充后再交教务处审核。
     */
    @PostMapping("/new-course-applies")
    @RequirePerm({"P06", "P21", "P07"})
    public Result<Void> apply(@RequestBody Map<String, Object> body) {
        LoginUser user = UserContext.require();
        String courseType = str(body.get("courseType"));
        if (courseType == null || courseType.isBlank()) {
            courseType = "17";
        }
        boolean schoolLevel = user.hasPerm("P07");
        boolean collegeLevel = !schoolLevel && user.hasPerm("P21");
        if (schoolLevel) {
            if (!"11".equals(courseType) && !"15".equals(courseType)) {
                throw new BizException("教务处仅可申报校级必修（11）或通识课程（15）");
            }
        } else if (collegeLevel) {
            if (!List.of("12", "13", "14", "16").contains(courseType)) {
                throw new BizException("学院教务仅可申报院级必修（12）、专业必修（13）、专业选修（14）或实践教学（16）课程");
            }
        } else {
            if (user.getTeacherId() == null) {
                throw new BizException("仅教师可申报新增课程");
            }
            if (!"17".equals(courseType)) {
                throw new BizException("教师仅可申报素质拓展课程（17）");
            }
        }
        NewCourseApply apply = new NewCourseApply();
        apply.setApplyId(codeService.nextBillId("NC", "new_course_apply", "apply_id"));
        apply.setApplyUser(user.getUserId());
        apply.setCourseName(str(body.get("courseName")));
        apply.setCourseType(courseType);
        apply.setApplyReason(str(body.get("applyReason")));
        apply.setCredit(new BigDecimal(str(body.get("credit"))));
        apply.setHours(body.get("hours") == null ? 32 : Integer.valueOf(str(body.get("hours"))));
        if (schoolLevel) {
            if ("15".equals(courseType)) {
                String dispatch = str(body.get("dispatchCollege"));
                if (dispatch == null || dispatch.isBlank()) {
                    throw new BizException("通识课程申报须指定下发学院");
                }
                apply.setDispatchCollege(dispatch);
                // 教务处申报的通识课程先下发学院补充，补充后由教务处审核
                apply.setAuditStatus("4");
            } else {
                // 校级必修由教务处直接进入终审
                apply.setAuditStatus("3");
            }
        } else if (collegeLevel) {
            // 学院教务自报课程直接进入教务处待审
            apply.setAuditStatus("3");
        } else {
            apply.setAuditStatus("0");
        }
        apply.setApplyTime(LocalDateTime.now());
        if (courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseName, apply.getCourseName())) > 0) {
            throw new BizException("课程已存在，可直接申请开课");
        }
        applyMapper.insert(apply);
        auditService.record("01", "new_course_apply:" + apply.getApplyId(), null, apply);
        return Result.ok();
    }

    /** 学院初审：填写学院意见，通过后转教务处待审（P1-12） */
    @PostMapping("/new-course-applies/{id}/college-audit")
    @RequirePerm("P21")
    public Result<Void> collegeAudit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        NewCourseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申报记录不存在");
        }
        if (!"0".equals(apply.getAuditStatus())) {
            throw new BizException("该申报不在学院待审状态");
        }
        LoginUser user = UserContext.require();
        requireOwnCollege(user, apply);
        String opinion = str(body.get("opinion"));
        if (opinion == null || opinion.isBlank()) {
            throw new BizException("请填写学院审核意见");
        }
        NewCourseApply update = new NewCourseApply();
        update.setApplyId(id);
        update.setCollegeOpinion(opinion);
        update.setAuditStatus("1".equals(str(body.get("pass"))) ? "3" : "2");
        update.setAuditTime(LocalDateTime.now());
        applyMapper.updateById(update);
        auditService.record("04", "new_course_apply:" + id, Map.of("auditStatus", "0"),
                Map.of("auditStatus", update.getAuditStatus(), "collegeOpinion", opinion,
                        "collegeId", user.getCollegeId() == null ? "" : user.getCollegeId()));
        return Result.ok();
    }

    /** 学院补充：教务处下发的通识课程，由下发学院教务补充学分/学时/理由后提交教务处审核 */
    @PostMapping("/new-course-applies/{id}/supplement")
    @RequirePerm("P21")
    public Result<Void> supplement(@PathVariable String id, @RequestBody Map<String, Object> body) {
        NewCourseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申报记录不存在");
        }
        if (!"4".equals(apply.getAuditStatus())) {
            throw new BizException("该申报不在待学院补充状态");
        }
        LoginUser user = UserContext.require();
        if (!"1".equals(user.getDataScope())) {
            requireOwnCollege(user, apply);
            if (apply.getDispatchCollege() != null
                    && !apply.getDispatchCollege().equals(user.getCollegeId())) {
                throw new BizException(403, "仅下发学院可补充该通识课程");
            }
        }
        NewCourseApply update = new NewCourseApply();
        update.setApplyId(id);
        if (body.get("credit") != null) {
            update.setCredit(new BigDecimal(str(body.get("credit"))));
        }
        if (body.get("hours") != null) {
            update.setHours(Integer.valueOf(str(body.get("hours"))));
        }
        if (body.get("applyReason") != null) {
            update.setApplyReason(str(body.get("applyReason")));
        }
        String opinion = str(body.get("opinion"));
        update.setCollegeOpinion(opinion == null || opinion.isBlank() ? "学院已补充培养要求" : opinion);
        update.setAuditStatus("3");
        update.setAuditTime(LocalDateTime.now());
        applyMapper.updateById(update);
        auditService.record("04", "new_course_apply:" + id, Map.of("auditStatus", "4"),
                Map.of("auditStatus", "3", "collegeOpinion", update.getCollegeOpinion()));
        return Result.ok();
    }

    /** 学院教务只能处理本学院（或下发到本学院）的申报 */
    private void requireOwnCollege(LoginUser user, NewCourseApply apply) {
        if (!"2".equals(user.getDataScope()) || user.getCollegeId() == null) {
            return;
        }
        String college = user.getCollegeId();
        boolean dispatched = college.equals(apply.getDispatchCollege());
        SysUser applicant = apply.getApplyUser() == null ? null : sysUserMapper.selectById(apply.getApplyUser());
        boolean sameCollege = applicant != null && college.equals(applicant.getCollegeId());
        if (!dispatched && !sameCollege) {
            throw new BizException(403, "只能处理本学院的课程申报");
        }
    }

    /** 教务处终审：通过后生成课程号入库（P1-12） */
    @PostMapping("/new-course-applies/{id}/audit")
    @RequirePerm("P07")
    public Result<Void> audit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        NewCourseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申报记录不存在");
        }
        if (!"3".equals(apply.getAuditStatus())) {
            throw new BizException("该申报尚未通过学院初审或已处理");
        }
        String pass = str(body.get("pass"));
        String opinion = str(body.get("opinion"));
        NewCourseApply update = new NewCourseApply();
        update.setApplyId(id);
        update.setAuditOpinion(opinion);
        update.setAuditTime(LocalDateTime.now());
        if ("1".equals(pass)) {
            String openCollege = str(body.get("openCollege"));
            if (openCollege == null || openCollege.isBlank()) {
                throw new BizException("请指定开课学院");
            }
            Course course = new Course();
            String courseId = codeService.nextCourseId(openCollege);
            course.setCourseId(courseId);
            course.setCourseName(apply.getCourseName());
            course.setCourseType(apply.getCourseType());
            course.setCredit(apply.getCredit());
            course.setTotalHours(apply.getHours());
            course.setAssessType("2");
            course.setOpenCollege(openCollege);
            course.setCourseStatus("1");
            courseMapper.insert(course);
            update.setAuditStatus("1");
            update.setCourseId(courseId);
        } else {
            if (opinion == null || opinion.isBlank()) {
                throw new BizException("驳回时审核意见必填");
            }
            update.setAuditStatus("2");
        }
        applyMapper.updateById(update);
        auditService.record("04", "new_course_apply:" + id,
                Map.of("auditStatus", apply.getAuditStatus()), Map.of("auditStatus", update.getAuditStatus()));
        return Result.ok();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
