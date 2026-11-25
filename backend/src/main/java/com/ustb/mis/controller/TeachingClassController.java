package com.ustb.mis.controller;

import com.ustb.mis.common.Result;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.service.TeachingClassService;
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
 * 教学班生成与开课任务确认。
 */
@RestController
@RequestMapping("/api/teaching-classes")
@RequirePerm({"P08", "P09", "P12", "P15", "P19"})
public class TeachingClassController {

    private final TeachingClassService tcService;

    public TeachingClassController(TeachingClassService tcService) {
        this.tcService = tcService;
    }

    @GetMapping
    @RequirePerm({"P08", "P09", "P12", "P15", "P19"})
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String term,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String tcStatus) {
        return Result.ok(tcService.list(term, keyword, tcStatus));
    }

    /** 编排教学班时的候选面向班级（培养方案中该学期需修读该课程、且尚未编排的班级） */
    @GetMapping("/class-candidates")
    public Result<List<Map<String, Object>>> classCandidates(@RequestParam(required = false) String term,
                                                             @RequestParam(required = false) String courseId) {
        return Result.ok(tcService.classCandidates(term, courseId));
    }

    /** 建班候选课程：本学院开设、在用培养方案本学期开课、且仍有未编排班级 */
    @GetMapping("/course-candidates")
    public Result<List<Map<String, Object>>> courseCandidates(@RequestParam(required = false) String term,
                                                              @RequestParam(required = false) String collegeId) {
        return Result.ok(tcService.courseCandidates(term, collegeId));
    }

    /** 选修课建班候选课程：本学院本学期尚无教学班的课程 */
    @GetMapping("/elective-candidates")
    public Result<List<Map<String, Object>>> electiveCandidates(@RequestParam(required = false) String term,
                                                                @RequestParam(required = false) String collegeId) {
        return Result.ok(tcService.electiveCandidates(term, collegeId));
    }

    /** 学院教务分配任课教师 */
    @PutMapping("/{tcId}/assign-teacher")
    @RequirePerm({"P19", "P09"})
    public Result<Void> assignTeacher(@PathVariable String tcId, @RequestParam String teacherId) {
        tcService.assignTeacher(tcId, teacherId);
        return Result.ok();
    }

    /** 教师承接选修课并设定容量 */
    @PutMapping("/{tcId}/claim")
    @RequirePerm("P08")
    public Result<Void> claim(@PathVariable String tcId, @RequestParam Integer capacity) {
        tcService.claim(tcId, capacity);
        return Result.ok();
    }

    /** 教务处审核选修课教学班 */
    @PutMapping("/{tcId}/review")
    @RequirePerm("P19")
    public Result<Void> review(@PathVariable String tcId,
                               @RequestParam boolean pass,
                               @RequestParam(required = false) String reason) {
        tcService.review(tcId, pass, reason);
        return Result.ok();
    }

    /** 任课教师确认承接 */
    @PutMapping("/{tcId}/confirm")
    @RequirePerm("P08")
    public Result<Void> confirm(@PathVariable String tcId) {
        tcService.confirm(tcId);
        return Result.ok();
    }

    /** 任课教师拒绝承接 */
    @PutMapping("/{tcId}/decline")
    @RequirePerm("P08")
    public Result<Void> decline(@PathVariable String tcId, @RequestParam(required = false) String reason) {
        tcService.decline(tcId, reason);
        return Result.ok();
    }

    /** 教务处/学院教务员编排教学班（P19），任课教师随后划分本班理论/实践学时（P08） */
    @PostMapping
    @RequirePerm({"P19", "P08"})
    public Result<Void> save(@RequestBody Map<String, Object> body) {
        tcService.save(body);
        return Result.ok();
    }

    @PostMapping("/batch-generate")
    @RequirePerm("P19")
    public Result<Map<String, Object>> batchGenerate(@RequestBody Map<String, Object> body) {
        return Result.ok(tcService.batchGenerate(str(body.get("term")), str(body.get("majorId")),
                str(body.get("grade")), str(body.get("programType"))));
    }

    @PutMapping("/{tcId}/status")
    @RequirePerm({"P19", "P09"})
    public Result<Void> changeStatus(@PathVariable String tcId, @RequestParam String status,
                                     @RequestParam(required = false) String reason) {
        tcService.changeStatus(tcId, status, reason);
        return Result.ok();
    }

    @DeleteMapping("/{tcId}")
    @RequirePerm("P19")
    public Result<Void> delete(@PathVariable String tcId) {
        tcService.delete(tcId);
        return Result.ok();
    }

    // ---------------- 教学班学生名单 ----------------

    @GetMapping("/{tcId}/students")
    public Result<List<Map<String, Object>>> students(@PathVariable String tcId) {
        return Result.ok(tcService.students(tcId));
    }

    @GetMapping("/{tcId}/student-candidates")
    public Result<List<Map<String, Object>>> studentCandidates(@PathVariable String tcId,
                                                               @RequestParam(required = false) String keyword) {
        return Result.ok(tcService.studentCandidates(tcId, keyword));
    }

    @PostMapping("/{tcId}/students")
    public Result<Void> addStudent(@PathVariable String tcId, @RequestBody Map<String, Object> body) {
        tcService.addStudent(tcId, str(body.get("stuId")), str(body.get("stuName")));
        return Result.ok();
    }

    @DeleteMapping("/{tcId}/students/{stuId}")
    public Result<Void> removeStudent(@PathVariable String tcId, @PathVariable String stuId) {
        tcService.removeStudent(tcId, stuId);
        return Result.ok();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
