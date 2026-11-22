package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.NewCourseApply;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.NewCourseApplyMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
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
    private final ViewMapper viewMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public CourseController(CourseMapper courseMapper, NewCourseApplyMapper applyMapper,
                            ProgramCourseMapper programCourseMapper, TeachingClassMapper tcMapper,
                            ViewMapper viewMapper, CodeService codeService, AuditService auditService) {
        this.courseMapper = courseMapper;
        this.applyMapper = applyMapper;
        this.programCourseMapper = programCourseMapper;
        this.tcMapper = tcMapper;
        this.viewMapper = viewMapper;
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
        course.setCourseStatus("1");
        courseMapper.insert(course);
        auditService.record("01", "course:" + course.getCourseId(), null, course);
        return Result.ok();
    }

    @PutMapping("/courses/{id}")
    @RequirePerm("P05")
    public Result<Void> update(@PathVariable String id, @RequestBody Course course) {
        Course old = courseMapper.selectById(id);
        if (old == null) {
            throw new BizException("课程不存在");
        }
        long used = programCourseMapper.selectCount(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getCourseId, id));
        if (used > 0) {
            if ((course.getCredit() != null && course.getCredit().compareTo(old.getCredit()) != 0)
                    || (course.getTotalHours() != null && !course.getTotalHours().equals(old.getTotalHours()))) {
                throw new BizException("该课程已被培养方案引用，只允许修改课程大纲与考核方式");
            }
        }
        course.setCourseId(id);
        courseMapper.updateById(course);
        auditService.record("02", "course:" + id, old, course);
        return Result.ok();
    }

    @PutMapping("/courses/{id}/status")
    @RequirePerm("P05")
    public Result<Void> changeStatus(@PathVariable String id, @RequestParam String status) {
        Course old = courseMapper.selectById(id);
        if (old == null) {
            throw new BizException("课程不存在");
        }
        if ("0".equals(status)) {
            long running = tcMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()
                    .eq(TeachingClass::getCourseId, id)
                    .in(TeachingClass::getTcStatus, List.of("0", "1", "3")));
            if (running > 0) {
                throw new BizException("存在进行中的教学班，不能停开");
            }
        }
        Course update = new Course();
        update.setCourseId(id);
        update.setCourseStatus(status);
        courseMapper.updateById(update);
        auditService.record("02", "course:" + id, Map.of("courseStatus", old.getCourseStatus()),
                Map.of("courseStatus", status));
        return Result.ok();
    }

    // ---------------- 新增课程申报 ----------------

    @GetMapping("/new-course-applies")
    @RequirePerm({"P06", "P07", "P05"})
    public Result<List<Map<String, Object>>> applies(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String mine) {
        LoginUser user = UserContext.require();
        String applyUser = null;
        if ("1".equals(mine) && user.getTeacherId() != null) {
            applyUser = user.getTeacherId();
        }
        return Result.ok(viewMapper.listNewCourseApplies(status, applyUser));
    }

    @PostMapping("/new-course-applies")
    @RequirePerm({"P06", "P07"})
    public Result<Void> apply(@RequestBody Map<String, Object> body) {
        LoginUser user = UserContext.require();
        NewCourseApply apply = new NewCourseApply();
        apply.setApplyId(codeService.nextBillId("NC", "new_course_apply", "apply_id"));
        apply.setApplyUser(user.getTeacherId() == null ? user.getUserId() : user.getTeacherId());
        apply.setCourseName(str(body.get("courseName")));
        apply.setCourseType(str(body.get("courseType")) == null ? "14" : str(body.get("courseType")));
        apply.setApplyReason(str(body.get("applyReason")));
        apply.setCredit(new BigDecimal(str(body.get("credit"))));
        apply.setHours(body.get("hours") == null ? 32 : Integer.valueOf(str(body.get("hours"))));
        apply.setCollegeOpinion(str(body.get("collegeOpinion")));
        apply.setAuditStatus("0");
        apply.setApplyTime(LocalDateTime.now());
        if (courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseName, apply.getCourseName())) > 0) {
            throw new BizException("课程已存在，可直接申请开课");
        }
        applyMapper.insert(apply);
        auditService.record("01", "new_course_apply:" + apply.getApplyId(), null, apply);
        return Result.ok();
    }

    @PostMapping("/new-course-applies/{id}/audit")
    @RequirePerm("P07")
    public Result<Void> audit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        NewCourseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申报记录不存在");
        }
        if (!"0".equals(apply.getAuditStatus())) {
            throw new BizException("该申报已处理");
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
