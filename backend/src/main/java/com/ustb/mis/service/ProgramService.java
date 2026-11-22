package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.GeneralPlan;
import com.ustb.mis.entity.Major;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.GeneralPlanMapper;
import com.ustb.mis.mapper.MajorMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 培养方案制定与修订模块（对应 3.5.1）。
 */
@Service
public class ProgramService {

    private final MajorProgramMapper programMapper;
    private final ProgramCourseMapper programCourseMapper;
    private final MajorMapper majorMapper;
    private final CourseMapper courseMapper;
    private final GeneralPlanMapper generalPlanMapper;
    private final ViewMapper viewMapper;
    private final AuditService auditService;

    public ProgramService(MajorProgramMapper programMapper, ProgramCourseMapper programCourseMapper,
                          MajorMapper majorMapper, CourseMapper courseMapper,
                          GeneralPlanMapper generalPlanMapper, ViewMapper viewMapper,
                          AuditService auditService) {
        this.programMapper = programMapper;
        this.programCourseMapper = programCourseMapper;
        this.majorMapper = majorMapper;
        this.courseMapper = courseMapper;
        this.generalPlanMapper = generalPlanMapper;
        this.viewMapper = viewMapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list(String majorId, String planStatus, String keyword) {
        LoginUser user = UserContext.get();
        String collegeId = null;
        if (user != null && "2".equals(user.getDataScope())) {
            collegeId = user.getCollegeId();
        }
        return viewMapper.listPrograms(collegeId, majorId, planStatus, keyword);
    }

    public List<Map<String, Object>> courses(String majorId, String grade, String programType) {
        return viewMapper.listProgramCourses(majorId, grade, programType);
    }

    @Transactional
    public void save(Map<String, Object> body) {
        String majorId = str(body.get("majorId"));
        String grade = str(body.get("grade"));
        String programType = str(body.get("programType"));
        if (majorId == null || grade == null || programType == null) {
            throw new BizException("专业、年级与方案类型不能为空");
        }
        Major major = majorMapper.selectById(majorId);
        if (major == null) {
            throw new BizException("专业不存在");
        }
        MajorProgram program = new MajorProgram();
        program.setMajorId(majorId);
        program.setGrade(grade);
        program.setProgramType(programType);
        program.setTotalCredit(dec(body.get("totalCredit")));
        program.setGoalDesc(str(body.get("goalDesc")));
        program.setDualQuota(body.get("dualQuota") == null ? null : Integer.valueOf(str(body.get("dualQuota"))));

        boolean versioned = Boolean.TRUE.equals(body.get("copyFromLast")) || "1".equals(str(body.get("newVersion")));
        MajorProgram current = find(majorId, grade, programType);
        if (current != null) {
            if ("3".equals(current.getPlanStatus())) {
                throw new BizException("该方案已发布，修订请使用“编制新版本”");
            }
            if (versioned) {
                throw new BizException("已存在同版本方案，请直接修改");
            }
        }
        if (!"1".equals(major.getMajorStatus())) {
            throw new BizException("该专业已停招，不能编制新方案");
        }
        if (program.getTotalCredit() == null || program.getTotalCredit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("毕业总学分必须大于 0");
        }
        if ("1".equals(programType)) {
            if (program.getTotalCredit().compareTo(new BigDecimal("100")) < 0
                    || program.getTotalCredit().compareTo(new BigDecimal("200")) > 0) {
                throw new BizException("主修方案毕业总学分应在 100～200 之间");
            }
        } else if (program.getTotalCredit().compareTo(new BigDecimal("40")) < 0
                || program.getTotalCredit().compareTo(new BigDecimal("80")) > 0) {
            throw new BizException("双学位方案毕业总学分应在 40～80 之间");
        }
        program.setVersion(current == null ? "V1.0" : nextVersion(current.getVersion()));
        program.setPlanStatus("0");
        program.setCreateTime(java.time.LocalDateTime.now());
        if (current == null) {
            programMapper.insert(program);
            auditService.record("01", "major_program:" + majorId + "-" + grade + "-" + programType, null, program);
        } else {
            programMapper.update(program, Wrappers.<MajorProgram>lambdaUpdate()
                    .eq(MajorProgram::getMajorId, majorId)
                    .eq(MajorProgram::getGrade, grade)
                    .eq(MajorProgram::getProgramType, programType));
            auditService.record("02", "major_program:" + majorId + "-" + grade + "-" + programType, current, program);
        }
    }

    /** 基于已发布方案修订：保留原方案课程明细，版本号升一级 */
    @Transactional
    public void revise(String majorId, String grade, String programType) {
        MajorProgram current = find(majorId, grade, programType);
        if (current == null) {
            throw new BizException("方案不存在");
        }
        if (!"0".equals(current.getPlanStatus())) {
            throw new BizException("仅编制中的方案可继续修订，已发布方案请编制新版本");
        }
        MajorProgram update = new MajorProgram();
        update.setVersion(nextVersion(current.getVersion()));
        update.setPlanStatus("0");
        update.setPublishDate(null);
        programMapper.update(update, Wrappers.<MajorProgram>lambdaUpdate()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType));
        auditService.record("02", "major_program:" + majorId + "-" + grade + "-" + programType,
                Map.of("version", current.getVersion()), Map.of("version", update.getVersion()));
    }

    @Transactional
    public void addCourse(Map<String, Object> body) {
        String majorId = str(body.get("majorId"));
        String grade = str(body.get("grade"));
        String programType = str(body.get("programType"));
        String courseId = str(body.get("courseId"));
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("培养方案不存在，请先保存方案主信息");
        }
        if (!"0".equals(program.getPlanStatus()) && !"1".equals(program.getPlanStatus())) {
            throw new BizException("方案已进入审议或发布，课程明细不可修改");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException("课程不存在，请先在课程库登记");
        }
        if (!"1".equals(course.getCourseStatus())) {
            throw new BizException("课程已停用，不能列入方案");
        }
        String openSemester = str(body.get("openSemester"));
        if (openSemester == null) {
            throw new BizException("开课学期不能为空");
        }
        if (programCourseMapper.selectCount(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType)
                .eq(ProgramCourse::getCourseId, courseId)) > 0) {
            throw new BizException("该课程已在方案中，不能重复列入");
        }
        String preCourseId = str(body.get("preCourseId"));
        if (preCourseId == null) {
            preCourseId = course.getPreCourseId();
        }
        if (preCourseId != null && !preCourseId.isBlank()) {
            ProgramCourse pre = programCourseMapper.selectOne(Wrappers.<ProgramCourse>lambdaQuery()
                    .eq(ProgramCourse::getMajorId, majorId)
                    .eq(ProgramCourse::getGrade, grade)
                    .eq(ProgramCourse::getProgramType, programType)
                    .eq(ProgramCourse::getCourseId, preCourseId));
            if (pre != null && Integer.parseInt(pre.getOpenSemester()) >= Integer.parseInt(openSemester)) {
                throw new BizException("先修课程须安排在本课程之前学期（" + pre.getOpenSemester() + " 学期）");
            }
        }
        ProgramCourse pc = new ProgramCourse();
        pc.setMajorId(majorId);
        pc.setGrade(grade);
        pc.setProgramType(programType);
        pc.setCourseId(courseId);
        pc.setCourseType(course.getCourseType());
        pc.setCredit(dec(body.get("credit")) == null ? course.getCredit() : dec(body.get("credit")));
        pc.setHours(body.get("hours") == null ? course.getTotalHours() : Integer.valueOf(str(body.get("hours"))));
        pc.setOpenSemester(openSemester);
        pc.setPreCourseId(preCourseId);
        Major major = majorMapper.selectById(majorId);
        if (major != null && !course.getOpenCollege().equals(major.getCollegeId())) {
            pc.setCrossStatus("0");
        } else {
            pc.setCrossStatus("2");
        }
        programCourseMapper.insert(pc);
        auditService.record("01", "program_course:" + majorId + "-" + courseId, null, pc);
    }

    @Transactional
    public void removeCourse(String majorId, String grade, String programType, String courseId) {
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("方案不存在");
        }
        if ("3".equals(program.getPlanStatus())) {
            throw new BizException("已发布方案不可删除课程");
        }
        int used = programCourseMapper.delete(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType)
                .eq(ProgramCourse::getCourseId, courseId));
        if (used == 0) {
            throw new BizException("方案课程不存在");
        }
        auditService.record("03", "program_course:" + majorId + "-" + courseId,
                Map.of("courseId", courseId), null);
    }

    public Map<String, Object> creditCheck(String majorId, String grade, String programType) {
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("方案不存在");
        }
        List<ProgramCourse> list = programCourseMapper.selectList(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType));
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ProgramCourse pc : list) {
            byType.merge(pc.getCourseType(), pc.getCredit(), BigDecimal::add);
            sum = sum.add(pc.getCredit());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalCredit", program.getTotalCredit());
        result.put("sumCredit", sum);
        result.put("gap", program.getTotalCredit().subtract(sum));
        result.put("byType", byType);
        result.put("reach", sum.compareTo(program.getTotalCredit()) >= 0);
        return result;
    }

    @Transactional
    public void submit(String majorId, String grade, String programType) {
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("方案不存在");
        }
        long count = programCourseMapper.selectCount(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType));
        if (count == 0) {
            throw new BizException("方案课程为空，不能提交评审");
        }
        long unconfirmed = programCourseMapper.selectCount(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType)
                .eq(ProgramCourse::getCrossStatus, "0"));
        if (unconfirmed > 0) {
            throw new BizException("存在 " + unconfirmed + " 门未确认的跨院课程，不能提交（BR-07）");
        }
        Map<String, Object> check = creditCheck(majorId, grade, programType);
        if (!Boolean.TRUE.equals(check.get("reach"))) {
            throw new BizException("方案学分合计 " + check.get("sumCredit") + "，未达到毕业总学分 "
                    + check.get("totalCredit"));
        }
        MajorProgram update = new MajorProgram();
        update.setPlanStatus("1");
        programMapper.update(update, Wrappers.<MajorProgram>lambdaUpdate()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType));
        auditService.record("04", "major_program:" + majorId + "-" + grade + "-" + programType,
                Map.of("planStatus", program.getPlanStatus()), Map.of("planStatus", "1"));
    }

    @Transactional
    public void review(String majorId, String grade, String programType, String status, String opinion) {
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("方案不存在");
        }
        if ("3".equals(status)) {
            Map<String, Object> check = creditCheck(majorId, grade, programType);
            if (!Boolean.TRUE.equals(check.get("reach"))) {
                throw new BizException("学分合计未达标，不能发布");
            }
            MajorProgram update = new MajorProgram();
            update.setPlanStatus("3");
            update.setReviewOpinion(opinion);
            update.setPublishDate(LocalDate.now());
            programMapper.update(update, Wrappers.<MajorProgram>lambdaUpdate()
                    .eq(MajorProgram::getMajorId, majorId)
                    .eq(MajorProgram::getGrade, grade)
                    .eq(MajorProgram::getProgramType, programType));
            auditService.record("05", "major_program:" + majorId + "-" + grade + "-" + programType,
                    Map.of("planStatus", program.getPlanStatus()), Map.of("planStatus", "3"));
            return;
        }
        MajorProgram update = new MajorProgram();
        update.setPlanStatus(status);
        update.setReviewOpinion(opinion);
        programMapper.update(update, Wrappers.<MajorProgram>lambdaUpdate()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType));
        auditService.record("04", "major_program:" + majorId + "-" + grade + "-" + programType,
                Map.of("planStatus", program.getPlanStatus()), Map.of("planStatus", status));
    }

    /** 跨院开课确认 */
    @Transactional
    public void confirmCross(String majorId, String grade, String programType, String courseId, String status) {
        int n = programCourseMapper.update(null, Wrappers.<ProgramCourse>lambdaUpdate()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType)
                .eq(ProgramCourse::getCourseId, courseId)
                .set(ProgramCourse::getCrossStatus, status));
        if (n == 0) {
            throw new BizException("方案课程不存在");
        }
        auditService.record("04", "program_course:" + majorId + "-" + courseId,
                Map.of("crossStatus", "0"), Map.of("crossStatus", status));
    }

    public List<Map<String, Object>> generalPlans(String schoolYear) {
        LoginUser user = UserContext.get();
        String collegeId = null;
        if (user != null && "2".equals(user.getDataScope())) {
            collegeId = user.getCollegeId();
        }
        return viewMapper.listGeneralPlans(schoolYear, collegeId);
    }

    @Transactional
    public void saveGeneralPlan(Map<String, Object> body) {
        String schoolYear = str(body.get("schoolYear"));
        String courseId = str(body.get("courseId"));
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException("课程不存在");
        }
        if (!"15".equals(course.getCourseType())) {
            throw new BizException("仅通识类课程可列入通识课程计划");
        }
        GeneralPlan exists = generalPlanMapper.selectOne(Wrappers.<GeneralPlan>lambdaQuery()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId));
        if (exists != null) {
            throw new BizException("该学年该课程已存在计划，请修改原记录");
        }
        GeneralPlan plan = new GeneralPlan();
        plan.setSchoolYear(schoolYear);
        plan.setCourseId(courseId);
        plan.setPlanCategory(str(body.get("planCategory")));
        plan.setSuggestHours(body.get("suggestHours") == null ? course.getTotalHours()
                : Integer.valueOf(str(body.get("suggestHours"))));
        plan.setUndertakeCollege(str(body.get("undertakeCollege")));
        plan.setTeacherId(str(body.get("teacherId")));
        plan.setConfirmStatus("0");
        plan.setPlanStatus("0");
        generalPlanMapper.insert(plan);
        auditService.record("01", "general_plan:" + schoolYear + "-" + courseId, null, plan);
    }

    @Transactional
    public void confirmGeneralPlan(String schoolYear, String courseId, String status) {
        int n = generalPlanMapper.update(null, Wrappers.<GeneralPlan>lambdaUpdate()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId)
                .set(GeneralPlan::getConfirmStatus, status));
        if (n == 0) {
            throw new BizException("通识课程计划不存在");
        }
        auditService.record("04", "general_plan:" + schoolYear + "-" + courseId,
                Map.of("confirmStatus", "0"), Map.of("confirmStatus", status));
    }

    @Transactional
    public void publishGeneralPlan(String schoolYear, String courseId) {
        int n = generalPlanMapper.update(null, Wrappers.<GeneralPlan>lambdaUpdate()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId)
                .eq(GeneralPlan::getConfirmStatus, "1")
                .set(GeneralPlan::getPlanStatus, "2"));
        if (n == 0) {
            throw new BizException("未确认承接，不能发布通识课程计划");
        }
        auditService.record("05", "general_plan:" + schoolYear + "-" + courseId,
                Map.of("planStatus", "1"), Map.of("planStatus", "2"));
    }

    public MajorProgram find(String majorId, String grade, String programType) {
        return programMapper.selectOne(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType));
    }

    public List<MajorProgram> listPublished() {
        return programMapper.selectList(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getPlanStatus, "3"));
    }

    private String nextVersion(String version) {
        if (version == null || !version.startsWith("V")) {
            return "V1.1";
        }
        String num = version.substring(1);
        String[] parts = num.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return "V" + major + "." + (minor + 1);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private BigDecimal dec(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        return new BigDecimal(String.valueOf(o));
    }

    public List<Map<String, Object>> pendingCross(String collegeId) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (MajorProgram p : listPublished()) {
            for (Map<String, Object> pc : viewMapper.listProgramCourses(p.getMajorId(), p.getGrade(), p.getProgramType())) {
                if ("0".equals(String.valueOf(pc.get("crossStatus")))
                        && (collegeId == null || collegeId.equals(pc.get("openCollege")))) {
                    pc.put("grade", p.getGrade());
                    pc.put("programType", p.getProgramType());
                    all.add(pc);
                }
            }
        }
        return all;
    }
}
