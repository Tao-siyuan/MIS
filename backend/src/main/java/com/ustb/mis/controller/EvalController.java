package com.ustb.mis.controller;

import com.ustb.mis.common.Result;
import com.ustb.mis.entity.EvalPlan;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.service.EvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 课程评价：方案配置、评教任务、匿名提交与结果统计。
 * 评教方案的新增/发布/结束仅授予 P18（教务处），学生与教师只有 P15（提交与查询）。
 */
@RestController
@RequestMapping("/api/eval")
@RequirePerm("P15")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/plans")
    public Result<List<EvalPlan>> plans(@RequestParam(required = false) String term) {
        return Result.ok(evalService.plans(term));
    }

    @PostMapping("/plans")
    @RequirePerm("P18")
    public Result<Void> savePlan(@RequestBody Map<String, Object> body) {
        evalService.savePlan(body);
        return Result.ok();
    }

    @PostMapping("/plans/{id}/publish")
    @RequirePerm("P18")
    public Result<Void> publish(@PathVariable String id) {
        evalService.publishPlan(id);
        return Result.ok();
    }

    @PostMapping("/plans/{id}/finish")
    @RequirePerm("P18")
    public Result<Void> finish(@PathVariable String id) {
        evalService.finishPlan(id);
        return Result.ok();
    }

    @GetMapping("/plans/{id}/tasks")
    public Result<List<Map<String, Object>>> tasks(@PathVariable String id) {
        return Result.ok(evalService.tasks(id));
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return Result.ok(evalService.submit(body));
    }

    @GetMapping("/plans/{id}/stats")
    public Result<Map<String, Object>> stats(@PathVariable String id) {
        return Result.ok(evalService.stats(id));
    }

    @GetMapping("/plans/{id}/records")
    public Result<List<Map<String, Object>>> records(@PathVariable String id,
                                                     @RequestParam(required = false) String tcId) {
        return Result.ok(evalService.records(id, tcId));
    }
}
