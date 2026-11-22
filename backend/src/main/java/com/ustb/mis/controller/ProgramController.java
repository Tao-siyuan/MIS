package com.ustb.mis.controller;

import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.MajorProgramVersion;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.ProgramService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 专业设置：培养方案、方案课程明细、通识课程计划。
 */
@RestController
@RequestMapping("/api")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping("/programs")
    @RequirePerm({"P02", "P03", "P08", "P14", "P15", "P19"})
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String majorId,
                                                  @RequestParam(required = false) String planStatus,
                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(programService.list(majorId, planStatus, keyword));
    }

    @PostMapping("/programs")
    @RequirePerm("P02")
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        programService.save(body);
        return Result.ok();
    }

    /** 编制新版本（P0-06） */
    @PostMapping("/programs/revise")
    @RequirePerm("P02")
    public Result<Void> revise(@RequestBody Map<String, Object> body) {
        programService.revise(str(body.get("majorId")), str(body.get("grade")), str(body.get("programType")));
        return Result.ok();
    }

    /** 版本历史 */
    @GetMapping("/programs/versions")
    @RequirePerm({"P02", "P03"})
    public Result<List<MajorProgramVersion>> versions(@RequestParam String majorId,
                                                      @RequestParam String grade,
                                                      @RequestParam String programType) {
        return Result.ok(programService.versions(majorId, grade, programType));
    }

    @GetMapping("/programs/courses")
    @RequirePerm({"P02", "P03", "P08", "P19"})
    public Result<List<Map<String, Object>>> courses(@RequestParam String majorId,
                                                     @RequestParam String grade,
                                                     @RequestParam String programType) {
        return Result.ok(programService.courses(majorId, grade, programType));
    }

    @PostMapping("/programs/courses")
    @RequirePerm("P02")
    public Result<Void> addCourse(@RequestBody Map<String, Object> body) {
        programService.addCourse(body);
        return Result.ok();
    }

    @DeleteMapping("/programs/courses")
    @RequirePerm("P02")
    public Result<Void> removeCourse(@RequestParam String majorId, @RequestParam String grade,
                                     @RequestParam String programType, @RequestParam String courseId) {
        programService.removeCourse(majorId, grade, programType, courseId);
        return Result.ok();
    }

    @GetMapping("/programs/credit-check")
    @RequirePerm({"P02", "P03"})
    public Result<Map<String, Object>> creditCheck(@RequestParam String majorId,
                                                   @RequestParam String grade,
                                                   @RequestParam String programType) {
        return Result.ok(programService.creditCheck(majorId, grade, programType));
    }

    @PostMapping("/programs/submit")
    @RequirePerm("P02")
    public Result<Void> submit(@RequestBody Map<String, Object> body) {
        programService.submit(str(body.get("majorId")), str(body.get("grade")), str(body.get("programType")));
        return Result.ok();
    }

    @PostMapping("/programs/review")
    @RequirePerm({"P02", "P03"})
    public Result<Void> review(@RequestBody Map<String, Object> body) {
        LoginUser user = UserContext.require();
        String status = str(body.get("status"));
        if ("3".equals(status) && !user.hasPerm("P03")) {
            throw new BizException(403, "仅教务处可发布培养方案");
        }
        if ("2".equals(status) && !user.hasPerm("P02") && !user.hasPerm("P03")) {
            throw new BizException(403, "无方案审核权限");
        }
        programService.review(str(body.get("majorId")), str(body.get("grade")), str(body.get("programType")),
                status, str(body.get("opinion")));
        return Result.ok();
    }

    @PutMapping("/programs/cross-confirm")
    @RequirePerm({"P22", "P03"})
    public Result<Void> crossConfirm(@RequestBody Map<String, Object> body) {
        programService.confirmCross(str(body.get("majorId")), str(body.get("grade")),
                str(body.get("programType")), str(body.get("courseId")), str(body.get("status")));
        return Result.ok();
    }

    @GetMapping("/programs/pending-cross")
    @RequirePerm({"P22", "P03"})
    public Result<List<Map<String, Object>>> pendingCross() {
        String collegeId = "2".equals(UserContext.require().getDataScope())
                ? UserContext.require().getCollegeId() : null;
        return Result.ok(programService.pendingCross(collegeId));
    }

    // ---------------- 通识课程计划 ----------------

    @GetMapping("/general-plans")
    @RequirePerm({"P04", "P02", "P03", "P23"})
    public Result<List<Map<String, Object>>> generalPlans(@RequestParam(required = false) String schoolYear) {
        return Result.ok(programService.generalPlans(schoolYear));
    }

    @PostMapping("/general-plans")
    @RequirePerm("P04")
    public Result<Void> saveGeneralPlan(@RequestBody Map<String, Object> body) {
        programService.saveGeneralPlan(body);
        return Result.ok();
    }

    @PutMapping("/general-plans/confirm")
    @RequirePerm({"P23", "P04"})
    public Result<Void> confirmGeneralPlan(@RequestBody Map<String, Object> body) {
        programService.confirmGeneralPlan(str(body.get("schoolYear")), str(body.get("courseId")),
                str(body.get("status")));
        return Result.ok();
    }

    @PutMapping("/general-plans/publish")
    @RequirePerm("P04")
    public Result<Map<String, Object>> publishGeneralPlan(@RequestBody Map<String, Object> body) {
        return Result.ok(programService.publishGeneralPlan(str(body.get("schoolYear")),
                str(body.get("courseId"))));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
