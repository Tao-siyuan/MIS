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

    @PostMapping
    @RequirePerm("P19")
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

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
