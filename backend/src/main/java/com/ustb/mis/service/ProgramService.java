package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.GeneralPlan;
import com.ustb.mis.entity.Major;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.MajorProgramVersion;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.GeneralPlanMapper;
import com.ustb.mis.mapper.MajorMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.MajorProgramVersionMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 培养方案制定与修订模块（对应 3.5.1）。
 * 状态机：0 编制中 → 1 院系初审 → 2 教务处审核 → 3 已发布；仅 0 状态可编辑，版本仅由“编制新版本”递增。
 */
@Service
public class ProgramService {

    /** 学分结构要求：通识、实践、选修最低学分（主修方案） */
    private static final BigDecimal MIN_GENERAL = new BigDecimal("6");
    private static final BigDecimal MIN_PRACTICE = new BigDecimal("8");
    private static final BigDecimal MIN_ELECTIVE = new BigDecimal("8");
    /** 必修类课程占比下限 */
    private static final BigDecimal MIN_REQUIRED_RATIO = new BigDecimal("0.5");

    private final MajorProgramMapper programMapper;
    private final MajorProgramVersionMapper versionMapper;
    private final ProgramCourseMapper programCourseMapper;
    private final MajorMapper majorMapper;
    private final CourseMapper courseMapper;
    private final GeneralPlanMapper generalPlanMapper;
    private final TeachingClassMapper tcMapper;
    private final ViewMapper viewMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public ProgramService(MajorProgramMapper programMapper, MajorProgramVersionMapper versionMapper,
                          ProgramCourseMapper programCourseMapper, MajorMapper majorMapper,
                          CourseMapper courseMapper, GeneralPlanMapper generalPlanMapper,
                          TeachingClassMapper tcMapper, ViewMapper viewMapper,
                          CodeService codeService, AuditService auditService) {
        this.programMapper = programMapper;
        this.versionMapper = versionMapper;
        this.programCourseMapper = programCourseMapper;
        this.majorMapper = majorMapper;
        this.courseMapper = courseMapper;
        this.generalPlanMapper = generalPlanMapper;
        this.tcMapper = tcMapper;
        this.viewMapper = viewMapper;
        this.codeService = codeService;
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

    public List<MajorProgramVersion> versions(String majorId, String grade, String programType) {
        return versionMapper.selectList(Wrappers.<MajorProgramVersion>lambdaQuery()
                .eq(MajorProgramVersion::getMajorId, majorId)
                .eq(MajorProgramVersion::getGrade, grade)
                .eq(MajorProgramVersion::getProgramType, programType)
                .orderByDesc(MajorProgramVersion::getVersion));
    }

    /**
     * 保存方案主信息。仅“编制中”可编辑；普通编辑不改版本、不改状态（P0-04）。
     */
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
        MajorProgram current = find(majorId, grade, programType);
        if (current != null && !"0".equals(current.getPlanStatus())) {
            throw new BizException("方案已提交评审或已发布，不能直接编辑；如需修订请使用“编制新版本”");
        }
        BigDecimal totalCredit = dec(body.get("totalCredit"));
        if (totalCredit == null || totalCredit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("毕业总学分必须大于 0");
        }
        if ("1".equals(programType)) {
            if (totalCredit.compareTo(new BigDecimal("100")) < 0
                    || totalCredit.compareTo(new BigDecimal("200")) > 0) {
                throw new BizException("主修方案毕业总学分应在 100～200 之间");
            }
        } else if (totalCredit.compareTo(new BigDecimal("40")) < 0
                || totalCredit.compareTo(new BigDecimal("80")) > 0) {
            throw new BizException("双学位方案毕业总学分应在 40～80 之间");
        }
        MajorProgram program = new MajorProgram();
        program.setTotalCredit(totalCredit);
        program.setGoalDesc(str(body.get("goalDesc")));
        program.setDualQuota(body.get("dualQuota") == null ? null : Integer.valueOf(str(body.get("dualQuota"))));
        if (current == null) {
            if (!"1".equals(major.getMajorStatus())) {
                throw new BizException("该专业已停招，不能编制新方案");
            }
            program.setMajorId(majorId);
            program.setGrade(grade);
            program.setProgramType(programType);
            program.setVersion("V1.0");
            program.setPlanStatus("0");
            program.setCreateTime(LocalDateTime.now());
            programMapper.insert(program);
            auditService.record("01", key(majorId, grade, programType), null, program);
            return;
        }
        programMapper.update(program, updateWrapper(majorId, grade, programType));
        auditService.record("02", key(majorId, grade, programType), current, program);
    }

    /**
     * 编制新版本：把当前版本归档为只读快照，版本号+1，状态回到编制中（P0-06）。
     */
    @Transactional
    public void revise(String majorId, String grade, String programType) {
        MajorProgram current = find(majorId, grade, programType);
        if (current == null) {
            throw new BizException("方案不存在");
        }
        if ("0".equals(current.getPlanStatus())) {
            throw new BizException("当前方案正在编制中，可直接编辑，无需编制新版本");
        }
        archive(current);
        MajorProgram update = new MajorProgram();
        update.setVersion(nextVersion(current.getVersion()));
        update.setPlanStatus("0");
        update.setPublishDate(null);
        update.setReviewOpinion(null);
        programMapper.update(update, updateWrapper(majorId, grade, programType));
        auditService.record("02", key(majorId, grade, programType),
                Map.of("version", current.getVersion(), "planStatus", current.getPlanStatus()),
                Map.of("version", update.getVersion(), "planStatus", "0", "action", "编制新版本"));
    }

    /** 归档当前版本快照 */
    private void archive(MajorProgram current) {
        MajorProgramVersion snapshot = new MajorProgramVersion();
        snapshot.setMajorId(current.getMajorId());
        snapshot.setGrade(current.getGrade());
        snapshot.setProgramType(current.getProgramType());
        snapshot.setVersion(current.getVersion());
        snapshot.setPlanStatus(current.getPlanStatus());
        snapshot.setTotalCredit(current.getTotalCredit());
        snapshot.setGoalDesc(current.getGoalDesc());
        snapshot.setDualQuota(current.getDualQuota());
        snapshot.setReviewOpinion(current.getReviewOpinion());
        snapshot.setPublishDate(current.getPublishDate());
        snapshot.setCourseCount(Math.toIntExact(programCourseMapper.selectCount(
                Wrappers.<ProgramCourse>lambdaQuery()
                        .eq(ProgramCourse::getMajorId, current.getMajorId())
                        .eq(ProgramCourse::getGrade, current.getGrade())
                        .eq(ProgramCourse::getProgramType, current.getProgramType()))));
        snapshot.setArchiveTime(LocalDateTime.now());
        versionMapper.delete(Wrappers.<MajorProgramVersion>lambdaQuery()
                .eq(MajorProgramVersion::getMajorId, current.getMajorId())
                .eq(MajorProgramVersion::getGrade, current.getGrade())
                .eq(MajorProgramVersion::getProgramType, current.getProgramType())
                .eq(MajorProgramVersion::getVersion, current.getVersion()));
        versionMapper.insert(snapshot);
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
        requireEditable(program);
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
        // 方案学分、学时统一取课程库值，禁止前端自填（P1-05）
        pc.setCredit(course.getCredit());
        pc.setHours(course.getTotalHours());
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
        requireEditable(program);
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

    /** 学分结构校验：总学分区间 + 合计达标 + 必修/选修/通识/实践结构 + 通识课程来源 */
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
        BigDecimal required = nvl(byType.get("11")).add(nvl(byType.get("12"))).add(nvl(byType.get("13")));
        BigDecimal general = nvl(byType.get("15"));
        BigDecimal practice = nvl(byType.get("16"));
        BigDecimal elective = nvl(byType.get("14"));

        List<String> issues = new ArrayList<>();
        if (sum.compareTo(program.getTotalCredit()) < 0) {
            issues.add("学分合计 " + sum + "，未达到毕业总学分 " + program.getTotalCredit());
        }
        boolean mainProgram = "1".equals(programType);
        if (mainProgram) {
            if (general.compareTo(MIN_GENERAL) < 0) {
                issues.add("通识课程学分不足（" + general + " < " + MIN_GENERAL + "）");
            }
            if (practice.compareTo(MIN_PRACTICE) < 0) {
                issues.add("实践环节学分不足（" + practice + " < " + MIN_PRACTICE + "）");
            }
            if (elective.compareTo(MIN_ELECTIVE) < 0) {
                issues.add("专业选修学分不足（" + elective + " < " + MIN_ELECTIVE + "）");
            }
            BigDecimal minRequired = sum.multiply(MIN_REQUIRED_RATIO);
            if (required.compareTo(minRequired) < 0) {
                issues.add("必修类课程学分占比不足 50%（" + required + " < " + minRequired.setScale(1,
                        java.math.RoundingMode.HALF_UP) + "）");
            }
        }
        // 通识课程须来自本学年通识课程计划（P1-02）
        List<String> generalWarnings = new ArrayList<>();
        for (ProgramCourse pc : list) {
            if (!"15".equals(pc.getCourseType())) {
                continue;
            }
            List<GeneralPlan> plans = generalPlanMapper.selectList(Wrappers.<GeneralPlan>lambdaQuery()
                    .eq(GeneralPlan::getCourseId, pc.getCourseId())
                    .orderByDesc(GeneralPlan::getSchoolYear));
            if (plans.isEmpty()) {
                issues.add("通识课程 " + pc.getCourseId() + " 未列入任何学年通识课程计划");
            } else if (!"2".equals(plans.get(0).getPlanStatus())) {
                generalWarnings.add("通识课程 " + pc.getCourseId() + " 的计划尚未发布");
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalCredit", program.getTotalCredit());
        result.put("sumCredit", sum);
        result.put("gap", program.getTotalCredit().subtract(sum));
        result.put("byType", byType);
        result.put("requiredCredit", required);
        result.put("generalCredit", general);
        result.put("practiceCredit", practice);
        result.put("electiveCredit", elective);
        result.put("issues", issues);
        result.put("warnings", generalWarnings);
        result.put("reach", issues.isEmpty());
        return result;
    }

    @Transactional
    public void submit(String majorId, String grade, String programType) {
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("方案不存在");
        }
        if (!"0".equals(program.getPlanStatus())) {
            throw new BizException("仅编制中的方案可提交评审");
        }
        long count = countCourses(majorId, grade, programType);
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
            Object issues = check.get("issues");
            throw new BizException("学分结构未达标：" + String.join("；", (List<String>) issues));
        }
        updateStatus(program, "1", null);
        auditService.record("04", key(majorId, grade, programType),
                Map.of("planStatus", program.getPlanStatus()), Map.of("planStatus", "1"));
    }

    /**
     * 审核状态机：1 → 2（院系初审通过），2 → 3（教务处审批发布），任意状态可退回 0（P1-02）。
     */
    @Transactional
    public void review(String majorId, String grade, String programType, String status, String opinion) {
        MajorProgram program = find(majorId, grade, programType);
        if (program == null) {
            throw new BizException("方案不存在");
        }
        String current = program.getPlanStatus();
        if ("2".equals(status)) {
            if (!"1".equals(current)) {
                throw new BizException("仅“院系初审”状态的方案可通过院系初审");
            }
        } else if ("3".equals(status)) {
            if (!"2".equals(current)) {
                throw new BizException("仅“教务处审核”状态的方案可发布，请先完成院系初审");
            }
            Map<String, Object> check = creditCheck(majorId, grade, programType);
            if (!Boolean.TRUE.equals(check.get("reach"))) {
                Object issues = check.get("issues");
                throw new BizException("学分结构未达标，不能发布：" + String.join("；", (List<String>) issues));
            }
        } else if ("0".equals(status)) {
            if ("3".equals(current)) {
                throw new BizException("已发布方案不能退回，请使用“编制新版本”");
            }
            if (opinion == null || opinion.isBlank()) {
                throw new BizException("退回时须填写审核意见");
            }
        } else {
            throw new BizException("非法的审核结果");
        }
        MajorProgram update = new MajorProgram();
        update.setPlanStatus(status);
        update.setReviewOpinion(opinion);
        if ("3".equals(status)) {
            update.setPublishDate(LocalDate.now());
        }
        programMapper.update(update, updateWrapper(majorId, grade, programType));
        auditService.record("3".equals(status) ? "05" : "04", key(majorId, grade, programType),
                Map.of("planStatus", current), Map.of("planStatus", status));
    }

    /** 跨院开课确认：必须由开课学院确认（P1-01） */
    @Transactional
    public void confirmCross(String majorId, String grade, String programType, String courseId, String status) {
        ProgramCourse pc = programCourseMapper.selectOne(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType)
                .eq(ProgramCourse::getCourseId, courseId));
        if (pc == null) {
            throw new BizException("方案课程不存在");
        }
        MajorProgram program = find(majorId, grade, programType);
        if (program != null && "3".equals(program.getPlanStatus())) {
            throw new BizException("方案已发布，跨院确认已冻结");
        }
        LoginUser user = UserContext.require();
        if (!"1".equals(user.getDataScope())) {
            Course course = courseMapper.selectById(courseId);
            if (course == null) {
                throw new BizException("课程不存在");
            }
            if (!course.getOpenCollege().equals(user.getCollegeId())) {
                throw new BizException("仅开课学院可确认该门跨院课程");
            }
        }
        ProgramCourse update = new ProgramCourse();
        update.setCrossStatus(status);
        programCourseMapper.update(update, Wrappers.<ProgramCourse>lambdaUpdate()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType)
                .eq(ProgramCourse::getCourseId, courseId));
        auditService.record("04", "program_course:" + majorId + "-" + courseId,
                Map.of("crossStatus", pc.getCrossStatus()), Map.of("crossStatus", status));
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

    /** 承接确认：仅承接学院可确认（P1-03） */
    @Transactional
    public void confirmGeneralPlan(String schoolYear, String courseId, String status) {
        GeneralPlan plan = generalPlanMapper.selectOne(Wrappers.<GeneralPlan>lambdaQuery()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId));
        if (plan == null) {
            throw new BizException("通识课程计划不存在");
        }
        LoginUser user = UserContext.require();
        if (!"1".equals(user.getDataScope())
                && plan.getUndertakeCollege() != null
                && !plan.getUndertakeCollege().equals(user.getCollegeId())) {
            throw new BizException("仅承接学院可确认该门通识课程");
        }
        GeneralPlan update = new GeneralPlan();
        update.setConfirmStatus(status);
        generalPlanMapper.update(update, Wrappers.<GeneralPlan>lambdaUpdate()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId));
        auditService.record("04", "general_plan:" + schoolYear + "-" + courseId,
                Map.of("confirmStatus", plan.getConfirmStatus()), Map.of("confirmStatus", status));
    }

    /** 计划发布：未确认不得发布；发布后生成待排课教学班草稿，作为排课来源（P1-03） */
    @Transactional
    public Map<String, Object> publishGeneralPlan(String schoolYear, String courseId) {
        GeneralPlan plan = generalPlanMapper.selectOne(Wrappers.<GeneralPlan>lambdaQuery()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId));
        if (plan == null) {
            throw new BizException("通识课程计划不存在");
        }
        if (!"1".equals(plan.getConfirmStatus())) {
            throw new BizException("未确认承接，不能发布通识课程计划");
        }
        if ("2".equals(plan.getPlanStatus())) {
            throw new BizException("该计划已发布");
        }
        GeneralPlan update = new GeneralPlan();
        update.setPlanStatus("2");
        generalPlanMapper.update(update, Wrappers.<GeneralPlan>lambdaUpdate()
                .eq(GeneralPlan::getSchoolYear, schoolYear)
                .eq(GeneralPlan::getCourseId, courseId));
        String term = schoolYear + "-1";
        TeachingClass tc = new TeachingClass();
        tc.setTcId(codeService.nextTcId(term));
        tc.setTerm(term);
        tc.setCourseId(courseId);
        tc.setTeacherId(plan.getTeacherId());
        tc.setStudentNum(0);
        tc.setCapacity(90);
        tc.setTcType("1");
        tc.setTeachType("1");
        tc.setHours(plan.getSuggestHours());
        tc.setWeekCount(16);
        tc.setTcStatus(plan.getTeacherId() == null ? "0" : "1");
        tc.setSource("2");
        tcMapper.insert(tc);
        auditService.record("05", "general_plan:" + schoolYear + "-" + courseId,
                Map.of("planStatus", plan.getPlanStatus()),
                Map.of("planStatus", "2", "teachingClass", tc.getTcId()));
        Map<String, Object> result = new HashMap<>();
        result.put("tcId", tc.getTcId());
        return result;
    }

    public MajorProgram find(String majorId, String grade, String programType) {
        return programMapper.selectOne(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType));
    }

    /** 跨院待确认清单：覆盖编制中/初审/审核状态方案，按开课学院过滤（P1-01） */
    public List<Map<String, Object>> pendingCross(String collegeId) {
        List<Map<String, Object>> all = new ArrayList<>();
        List<MajorProgram> programs = programMapper.selectList(Wrappers.<MajorProgram>lambdaQuery()
                .ne(MajorProgram::getPlanStatus, "3")
                .orderByAsc(MajorProgram::getMajorId));
        for (MajorProgram p : programs) {
            for (Map<String, Object> pc : viewMapper.listProgramCourses(p.getMajorId(), p.getGrade(),
                    p.getProgramType())) {
                if ("0".equals(String.valueOf(pc.get("crossStatus")))
                        && (collegeId == null || collegeId.equals(pc.get("openCollege")))) {
                    pc.put("grade", p.getGrade());
                    pc.put("programType", p.getProgramType());
                    pc.put("planStatus", p.getPlanStatus());
                    all.add(pc);
                }
            }
        }
        return all;
    }

    public long countCourses(String majorId, String grade, String programType) {
        return programCourseMapper.selectCount(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, programType));
    }

    private void requireEditable(MajorProgram program) {
        if (!"0".equals(program.getPlanStatus())) {
            throw new BizException("仅“编制中”的方案允许修改课程明细，当前状态不可编辑");
        }
    }

    private void updateStatus(MajorProgram program, String status, String opinion) {
        MajorProgram update = new MajorProgram();
        update.setPlanStatus(status);
        update.setReviewOpinion(opinion);
        programMapper.update(update, updateWrapper(program.getMajorId(), program.getGrade(),
                program.getProgramType()));
    }

    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MajorProgram> updateWrapper(
            String majorId, String grade, String programType) {
        return Wrappers.<MajorProgram>lambdaUpdate()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, grade)
                .eq(MajorProgram::getProgramType, programType);
    }

    private String key(String majorId, String grade, String programType) {
        return "major_program:" + majorId + "-" + grade + "-" + programType;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
}
