package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.DualEnroll;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.ProgramCourse;
import com.ustb.mis.entity.StudyRecord;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.DualEnrollMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.ProgramCourseMapper;
import com.ustb.mis.mapper.StudyRecordMapper;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 辅修/二学位：报名与录取、修读记录、课表协调与学分认定（对应 3.5.5）。
 * 报名查询 P13（学生可用）；录取审批 P17（仅教务处/管理员）。
 */
@RestController
@RequestMapping("/api/dual")
@RequirePerm("P13")
public class DualController {

    private final DualEnrollMapper enrollMapper;
    private final StudyRecordMapper studyMapper;
    private final MajorProgramMapper programMapper;
    private final ProgramCourseMapper programCourseMapper;
    private final TeachingClassMapper tcMapper;
    private final CourseMapper courseMapper;
    private final ViewMapper viewMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public DualController(DualEnrollMapper enrollMapper, StudyRecordMapper studyMapper,
                          MajorProgramMapper programMapper, ProgramCourseMapper programCourseMapper,
                          TeachingClassMapper tcMapper, CourseMapper courseMapper,
                          ViewMapper viewMapper, CodeService codeService, AuditService auditService) {
        this.enrollMapper = enrollMapper;
        this.studyMapper = studyMapper;
        this.programMapper = programMapper;
        this.programCourseMapper = programCourseMapper;
        this.tcMapper = tcMapper;
        this.courseMapper = courseMapper;
        this.viewMapper = viewMapper;
        this.codeService = codeService;
        this.auditService = auditService;
    }

    @GetMapping("/enrolls")
    public Result<List<Map<String, Object>>> enrolls(@RequestParam(required = false) String majorId,
                                                     @RequestParam(required = false) String auditStatus) {
        LoginUser user = UserContext.require();
        String stuId = user.getClassId() != null ? user.getUserId() : null;
        return Result.ok(viewMapper.listDualEnrolls(majorId, stuId, auditStatus));
    }

    @PostMapping("/enrolls")
    public Result<Void> enroll(@RequestBody Map<String, Object> body) {
        LoginUser user = UserContext.require();
        String stuId = user.getClassId() != null ? user.getUserId() : str(body.get("stuId"));
        String majorId = str(body.get("majorId"));
        String enrollYear = str(body.get("enrollYear"));
        if (stuId == null || stuId.isBlank()) {
            throw new BizException("学号不能为空");
        }
        long sameYear = enrollMapper.selectCount(Wrappers.<DualEnroll>lambdaQuery()
                .eq(DualEnroll::getStuId, stuId)
                .eq(DualEnroll::getEnrollYear, enrollYear)
                .ne(DualEnroll::getAuditStatus, "2"));
        if (sameYear > 0) {
            throw new BizException("同一学年只能报名一个双学位专业");
        }
        MajorProgram program = programMapper.selectOne(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getGrade, enrollYear.substring(0, 4))
                .eq(MajorProgram::getProgramType, "2")
                .eq(MajorProgram::getPlanStatus, "3"));
        if (program == null) {
            throw new BizException("该专业本学年不招收双学位");
        }
        dualEnroll(stuId, majorId, enrollYear, body.get("scoreAvg"), program);
        return Result.ok();
    }

    private void dualEnroll(String stuId, String majorId, String enrollYear, Object scoreAvg, MajorProgram program) {
        long admitted = enrollMapper.selectCount(Wrappers.<DualEnroll>lambdaQuery()
                .eq(DualEnroll::getMajorId, majorId)
                .eq(DualEnroll::getEnrollYear, enrollYear)
                .eq(DualEnroll::getAuditStatus, "1"));
        if (program.getDualQuota() != null && admitted >= program.getDualQuota()) {
            throw new BizException("该专业本学年双学位名额已满");
        }
        DualEnroll enroll = new DualEnroll();
        enroll.setEnrollId(codeService.nextBillId("DE", "dual_enroll", "enroll_id"));
        enroll.setStuId(stuId);
        enroll.setMajorId(majorId);
        enroll.setEnrollYear(enrollYear);
        enroll.setApplyDate(LocalDate.now());
        if (scoreAvg != null) {
            enroll.setScoreAvg(new BigDecimal(String.valueOf(scoreAvg)));
        }
        enroll.setAuditStatus("0");
        enroll.setFinishStatus("0");
        enrollMapper.insert(enroll);
        auditService.record("01", "dual_enroll:" + enroll.getEnrollId(), null, enroll);
    }

    /** 录取审批：仅 P17，禁止自审（P0-01） */
    @PostMapping("/enrolls/{id}/audit")
    @RequirePerm("P17")
    public Result<Void> auditEnroll(@PathVariable String id, @RequestBody Map<String, Object> body) {
        DualEnroll enroll = enrollMapper.selectById(id);
        if (enroll == null) {
            throw new BizException("报名记录不存在");
        }
        LoginUser user = UserContext.require();
        if (enroll.getStuId().equals(user.getUserId())) {
            throw new BizException(403, "不能审批本人的双学位报名");
        }
        if (!"0".equals(enroll.getAuditStatus())) {
            throw new BizException("该报名已处理");
        }
        String opinion = str(body.get("opinion"));
        DualEnroll update = new DualEnroll();
        update.setEnrollId(id);
        if ("1".equals(str(body.get("pass")))) {
            MajorProgram program = programMapper.selectOne(Wrappers.<MajorProgram>lambdaQuery()
                    .eq(MajorProgram::getMajorId, enroll.getMajorId())
                    .eq(MajorProgram::getProgramType, "2")
                    .orderByDesc(MajorProgram::getGrade));
            long admitted = enrollMapper.selectCount(Wrappers.<DualEnroll>lambdaQuery()
                    .eq(DualEnroll::getMajorId, enroll.getMajorId())
                    .eq(DualEnroll::getEnrollYear, enroll.getEnrollYear())
                    .eq(DualEnroll::getAuditStatus, "1"));
            if (program != null && program.getDualQuota() != null && admitted >= program.getDualQuota()) {
                throw new BizException("名额已满");
            }
            update.setAuditStatus("1");
            update.setEnrollDate(LocalDate.now());
            // 录取后修读状态联动为“修读中”（P2-10）
            update.setFinishStatus("0");
        } else {
            if (opinion == null || opinion.isBlank()) {
                throw new BizException("不录取时须填写审批意见");
            }
            update.setAuditStatus("2");
            update.setFinishStatus("2");
        }
        enrollMapper.updateById(update);
        auditService.record("04", "dual_enroll:" + id, Map.of("auditStatus", enroll.getAuditStatus()),
                Map.of("auditStatus", update.getAuditStatus(), "approver", user.getUserId(),
                        "opinion", opinion == null ? "" : opinion));
        return Result.ok();
    }

    @PutMapping("/enrolls/{id}/finish")
    @RequirePerm("P17")
    public Result<Void> finish(@PathVariable String id, @RequestParam String status) {
        DualEnroll enroll = enrollMapper.selectById(id);
        if (enroll == null) {
            throw new BizException("报名记录不存在");
        }
        if (!"1".equals(enroll.getAuditStatus())) {
            throw new BizException("未录取的报名不能变更修读状态");
        }
        DualEnroll update = new DualEnroll();
        update.setEnrollId(id);
        update.setFinishStatus(status);
        enrollMapper.updateById(update);
        auditService.record("02", "dual_enroll:" + id, Map.of("finishStatus", enroll.getFinishStatus()),
                Map.of("finishStatus", status));
        return Result.ok();
    }

    @GetMapping("/study-records")
    public Result<List<Map<String, Object>>> studyRecords(@RequestParam(required = false) String programType,
                                                          @RequestParam(required = false) String stuId) {
        LoginUser user = UserContext.require();
        String queryStu = user.getClassId() != null ? user.getUserId() : stuId;
        return Result.ok(viewMapper.listStudyRecords(programType, queryStu, null));
    }

    /**
     * 按双学位方案课程匹配当前学期教学班生成修读记录，并做真实时段比对（P1-06）。
     * 前置条件：学生已被该专业双学位录取。
     */
    @PostMapping("/study-records/generate")
    @RequirePerm("P17")
    public Result<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String stuId = str(body.get("stuId"));
        String majorId = str(body.get("majorId"));
        String programType = str(body.get("programType"));
        String grade = str(body.get("grade"));
        String term = str(body.get("term"));
        if (stuId == null || majorId == null || term == null) {
            throw new BizException("学号、专业与学期不能为空");
        }
        long admitted = enrollMapper.selectCount(Wrappers.<DualEnroll>lambdaQuery()
                .eq(DualEnroll::getStuId, stuId)
                .eq(DualEnroll::getMajorId, majorId)
                .eq(DualEnroll::getAuditStatus, "1"));
        if (admitted == 0) {
            throw new BizException("该学生尚未被该专业录取，不能生成修读记录");
        }
        List<ProgramCourse> courses = programCourseMapper.selectList(Wrappers.<ProgramCourse>lambdaQuery()
                .eq(ProgramCourse::getMajorId, majorId)
                .eq(ProgramCourse::getGrade, grade)
                .eq(ProgramCourse::getProgramType, "2".equals(programType) ? "2" : "1")
                .orderByAsc(ProgramCourse::getOpenSemester));
        if (courses.isEmpty()) {
            throw new BizException("该专业方案没有课程，无法生成修读记录");
        }
        String myClass = str(body.get("classId"));
        if (myClass == null) {
            myClass = UserContext.require().getClassId();
        }
        final String studentClass = myClass;
        List<Map<String, Object>> mainSched = studentClass == null ? List.of()
                : viewMapper.listScheduleResults(term, null, null).stream()
                .filter(r -> r.get("classIds") != null
                        && String.valueOf(r.get("classIds")).contains(studentClass))
                .toList();
        int created = 0;
        int conflicted = 0;
        for (ProgramCourse pc : courses) {
            long exists = studyMapper.selectCount(Wrappers.<StudyRecord>lambdaQuery()
                    .eq(StudyRecord::getStuId, stuId)
                    .eq(StudyRecord::getProgramType, programType)
                    .eq(StudyRecord::getCourseId, pc.getCourseId()));
            if (exists > 0) {
                continue;
            }
            TeachingClass tc = tcMapper.selectOne(Wrappers.<TeachingClass>lambdaQuery()
                    .eq(TeachingClass::getTerm, term)
                    .eq(TeachingClass::getCourseId, pc.getCourseId())
                    .orderByAsc(TeachingClass::getTcId)
                    .last("limit 1"));
            StudyRecord record = new StudyRecord();
            record.setRecId(codeService.nextBillId("SR", "study_record", "rec_id"));
            record.setStuId(stuId);
            record.setProgramType(programType);
            record.setMajorId(majorId);
            record.setCourseId(pc.getCourseId());
            record.setStudyType("1");
            record.setCredit(pc.getCredit());
            record.setAuditStatus("0");
            if (tc == null) {
                record.setConflictType("02");
                record.setRemark("本学期未开设该课程，顺延开课");
            } else {
                record.setTcId(tc.getTcId());
                boolean overlap = hasOverlap(mainSched, term, tc.getTcId());
                record.setConflictType(overlap ? "01" : "03");
                if (overlap) {
                    record.setRemark("与主修课程时段重合，需课表协调");
                    conflicted++;
                }
            }
            studyMapper.insert(record);
            created++;
        }
        auditService.record("01", "study_record:generate:" + stuId, null,
                Map.of("created", created, "conflicted", conflicted, "term", term));
        return Result.ok(Map.of("created", created, "conflicted", conflicted));
    }

    /** 与主修课表做星期 + 节次区间比对 */
    private boolean hasOverlap(List<Map<String, Object>> mainSched, String term, String tcId) {
        List<Map<String, Object>> subSched = viewMapper.listScheduleResults(term, null, null).stream()
                .filter(r -> tcId.equals(str(r.get("tcId")))).toList();
        for (Map<String, Object> a : mainSched) {
            for (Map<String, Object> b : subSched) {
                if (tcId.equals(str(a.get("tcId")))) {
                    continue;
                }
                if (String.valueOf(a.get("weekDay")).equals(String.valueOf(b.get("weekDay")))
                        && Integer.parseInt(String.valueOf(a.get("startSlot")))
                        <= Integer.parseInt(String.valueOf(b.get("endSlot")))
                        && Integer.parseInt(String.valueOf(b.get("startSlot")))
                        <= Integer.parseInt(String.valueOf(a.get("endSlot")))) {
                    return true;
                }
            }
        }
        return false;
    }

    @PostMapping("/study-records/{id}/approve")
    @RequirePerm("P17")
    public Result<Void> approveRecord(@PathVariable String id, @RequestBody Map<String, Object> body) {
        StudyRecord record = studyMapper.selectById(id);
        if (record == null) {
            throw new BizException("修读记录不存在");
        }
        Course course = courseMapper.selectById(record.getCourseId());
        StudyRecord update = new StudyRecord();
        update.setRecId(id);
        String status = str(body.get("auditStatus"));
        if ("1".equals(status)) {
            BigDecimal credit = body.get("credit") == null ? record.getCredit() : new BigDecimal(str(body.get("credit")));
            if (course != null && credit != null && credit.compareTo(course.getCredit()) > 0) {
                throw new BizException("认定学分不得超过替代课程学分（BR-09）");
            }
            update.setCredit(credit);
        }
        update.setAuditStatus(status);
        if (body.get("conflictType") != null) {
            update.setConflictType(str(body.get("conflictType")));
        }
        if (body.get("remark") != null) {
            update.setRemark(str(body.get("remark")));
        }
        studyMapper.updateById(update);
        auditService.record("04", "study_record:" + id, Map.of("auditStatus", record.getAuditStatus()),
                Map.of("auditStatus", status));
        return Result.ok();
    }

    /** 结业预审：按方案学分要求汇总已认定学分 */
    @GetMapping("/credit-summary")
    public Result<Map<String, Object>> creditSummary(@RequestParam String stuId, @RequestParam String majorId,
                                                     @RequestParam String programType) {
        List<StudyRecord> records = studyMapper.selectList(Wrappers.<StudyRecord>lambdaQuery()
                .eq(StudyRecord::getStuId, stuId)
                .eq(StudyRecord::getProgramType, programType)
                .eq(StudyRecord::getMajorId, majorId));
        BigDecimal sum = records.stream()
                .filter(r -> "1".equals(r.getAuditStatus()) && r.getCredit() != null)
                .map(StudyRecord::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MajorProgram> programs = programMapper.selectList(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getMajorId, majorId)
                .eq(MajorProgram::getProgramType, "2".equals(programType) ? "2" : "1")
                .orderByDesc(MajorProgram::getGrade));
        BigDecimal required = programs.isEmpty() ? BigDecimal.ZERO : programs.get(0).getTotalCredit();
        Map<String, Object> data = new HashMap<>();
        data.put("required", required);
        data.put("earned", sum);
        data.put("gap", required.subtract(sum));
        data.put("finish", sum.compareTo(required) >= 0);
        return Result.ok(data);
    }

    /** 课表协调：按修读记录关联的教学班与主修课表比对，输出冲突清单 */
    @GetMapping("/conflicts")
    public Result<List<Map<String, Object>>> conflicts(@RequestParam String stuId, @RequestParam String term,
                                                       @RequestParam(required = false) String classId) {
        LoginUser user = UserContext.require();
        String myClass = classId != null ? classId : user.getClassId();
        List<Map<String, Object>> mainRows = myClass == null ? List.of()
                : viewMapper.timetable(term, null, myClass, null);
        List<StudyRecord> records = studyMapper.selectList(Wrappers.<StudyRecord>lambdaQuery()
                .eq(StudyRecord::getStuId, stuId)
                .isNotNull(StudyRecord::getTcId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudyRecord record : records) {
            TeachingClass tc = tcMapper.selectById(record.getTcId());
            if (tc == null) {
                continue;
            }
            List<Map<String, Object>> subTc = viewMapper.listScheduleResults(term, null, null).stream()
                    .filter(r -> tc.getTcId().equals(str(r.get("tcId")))).toList();
            for (Map<String, Object> a : mainRows) {
                if (tc.getTcId().equals(str(a.get("tcId")))) {
                    continue;
                }
                for (Map<String, Object> b : subTc) {
                    if (String.valueOf(a.get("weekDay")).equals(String.valueOf(b.get("weekDay")))
                            && Integer.parseInt(String.valueOf(a.get("startSlot")))
                            <= Integer.parseInt(String.valueOf(b.get("endSlot")))
                            && Integer.parseInt(String.valueOf(b.get("startSlot")))
                            <= Integer.parseInt(String.valueOf(a.get("endSlot")))) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("recId", record.getRecId());
                        item.put("mainCourse", a.get("courseName"));
                        item.put("subCourse", b.get("courseName"));
                        item.put("weekDay", a.get("weekDay"));
                        item.put("startSlot", a.get("startSlot"));
                        item.put("endSlot", a.get("endSlot"));
                        item.put("conflictType", record.getConflictType());
                        result.add(item);
                    }
                }
            }
        }
        return Result.ok(result);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
