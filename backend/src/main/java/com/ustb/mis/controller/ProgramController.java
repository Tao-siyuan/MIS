package com.ustb.mis.controller;

import com.ustb.mis.common.Result;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.ProgramService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @RequirePerm({"P02", "P03", "P08", "P14"})
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

    @PostMapping("/programs/revise")
    @RequirePerm("P02")
    public Result<Void> revise(@RequestBody Map<String, Object> body) {
        programService.revise(str(body.get("majorId")), str(body.get("grade")), str(body.get("programType")));
        return Result.ok();
    }

    @GetMapping("/programs/courses")
    @RequirePerm({"P02", "P03", "P08"})
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
    @RequirePerm("P03")
    public Result<Void> review(@RequestBody Map<String, Object> body) {
        programService.review(str(body.get("majorId")), str(body.get("grade")), str(body.get("programType")),
                str(body.get("status")), str(body.get("opinion")));
        return Result.ok();
    }

    @PutMapping("/programs/cross-confirm")
    @RequirePerm({"P03", "P02"})
    public Result<Void> crossConfirm(@RequestBody Map<String, Object> body) {
        programService.confirmCross(str(body.get("majorId")), str(body.get("grade")),
                str(body.get("programType")), str(body.get("courseId")), str(body.get("status")));
        return Result.ok();
    }

    @GetMapping("/programs/pending-cross")
    @RequirePerm({"P02", "P03"})
    public Result<List<Map<String, Object>>> pendingCross() {
        String collegeId = "2".equals(UserContext.require().getDataScope())
                ? UserContext.require().getCollegeId() : null;
        return Result.ok(programService.pendingCross(collegeId));
    }

    // ---------------- 通识课程计划 ----------------

    @GetMapping("/general-plans")
    @RequirePerm({"P04", "P02", "P03"})
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
    @RequirePerm({"P04", "P02"})
    public Result<Void> confirmGeneralPlan(@RequestBody Map<String, Object> body) {
        programService.confirmGeneralPlan(str(body.get("schoolYear")), str(body.get("courseId")),
                str(body.get("status")));
        return Result.ok();
    }

    @PutMapping("/general-plans/publish")
    @RequirePerm("P04")
    public Result<Void> publishGeneralPlan(@RequestBody Map<String, Object> body) {
        programService.publishGeneralPlan(str(body.get("schoolYear")), str(body.get("courseId")));
        return Result.ok();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
