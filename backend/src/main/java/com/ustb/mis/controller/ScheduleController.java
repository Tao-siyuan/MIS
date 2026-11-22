package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.Classroom;
import com.ustb.mis.entity.ScheduleRule;
import com.ustb.mis.mapper.ClassroomMapper;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.service.AuditService;
import com.ustb.mis.service.ScheduleService;
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
 * 排课工作台：自动排课、冲突处理、课表发布、教室与规则维护。
 */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ClassroomMapper classroomMapper;
    private final AuditService auditService;

    public ScheduleController(ScheduleService scheduleService, ClassroomMapper classroomMapper,
                              AuditService auditService) {
        this.scheduleService = scheduleService;
        this.classroomMapper = classroomMapper;
        this.auditService = auditService;
    }

    @GetMapping("/results")
    @RequirePerm({"P09", "P12", "P15"})
    public Result<List<Map<String, Object>>> results(@RequestParam String term,
                                                     @RequestParam(required = false) String tcId,
                                                     @RequestParam(required = false) String schedStatus) {
        return Result.ok(scheduleService.results(term, tcId, schedStatus));
    }

    @GetMapping("/workbench")
    @RequirePerm("P09")
    public Result<Map<String, Object>> workbench(@RequestParam String term) {
        return Result.ok(scheduleService.workbench(term));
    }

    @PostMapping("/auto")
    @RequirePerm("P09")
    public Result<Map<String, Object>> auto(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> tcIds = (List<String>) body.get("tcIds");
        return Result.ok(scheduleService.autoSchedule(str(body.get("term")), tcIds));
    }

    @PostMapping("/assign")
    @RequirePerm("P09")
    public Result<Void> assign(@RequestBody Map<String, Object> body) {
        scheduleService.assign(body);
        return Result.ok();
    }

    @PostMapping("/check")
    @RequirePerm({"P09", "P10"})
    public Result<Map<String, Object>> check(@RequestBody Map<String, Object> body) {
        return Result.ok(scheduleService.check(body));
    }

    @DeleteMapping("/results/{schedId}")
    @RequirePerm("P09")
    public Result<Void> remove(@PathVariable String schedId) {
        scheduleService.removeSched(schedId);
        return Result.ok();
    }

    @GetMapping("/conflicts")
    @RequirePerm({"P09", "P12"})
    public Result<List<Map<String, Object>>> conflicts(@RequestParam String term,
                                                       @RequestParam(required = false) String handleStatus) {
        return Result.ok(scheduleService.conflicts(term, handleStatus));
    }

    @PostMapping("/conflicts/{id}/resolve")
    @RequirePerm("P09")
    public Result<Void> resolve(@PathVariable String id, @RequestBody Map<String, Object> body) {
        scheduleService.resolveConflict(id, body);
        return Result.ok();
    }

    @PostMapping("/publish")
    @RequirePerm("P09")
    public Result<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        return Result.ok(scheduleService.publish(str(body.get("term"))));
    }

    // ---------------- 排课规则 ----------------

    @GetMapping("/rules")
    @RequirePerm({"P09", "P03"})
    public Result<List<ScheduleRule>> rules() {
        return Result.ok(scheduleService.rules());
    }

    @PostMapping("/rules")
    @RequirePerm("P09")
    public Result<Void> saveRule(@RequestBody Map<String, Object> body) {
        scheduleService.saveRule(body);
        return Result.ok();
    }

    // ---------------- 教室资源（来源：固定资产管理子系统，此处只读维护） ----------------

    @GetMapping("/rooms")
    @RequirePerm({"P09", "P08", "P12"})
    public Result<List<Classroom>> rooms(@RequestParam(required = false) String status) {
        return Result.ok(classroomMapper.selectList(Wrappers.<Classroom>lambdaQuery()
                .eq(status != null && !status.isBlank(), Classroom::getRoomStatus, status)
                .orderByAsc(Classroom::getRoomId)));
    }

    @PostMapping("/rooms")
    @RequirePerm("P09")
    public Result<Void> saveRoom(@RequestBody Classroom room) {
        if (room.getRoomId() == null || room.getRoomId().isBlank()) {
            throw new BizException("教室编号不能为空");
        }
        if (classroomMapper.selectById(room.getRoomId()) == null) {
            if (room.getRoomStatus() == null) {
                room.setRoomStatus("1");
            }
            classroomMapper.insert(room);
            auditService.record("01", "classroom:" + room.getRoomId(), null, room);
        } else {
            Classroom old = classroomMapper.selectById(room.getRoomId());
            classroomMapper.updateById(room);
            auditService.record("02", "classroom:" + room.getRoomId(), old, room);
        }
        return Result.ok();
    }

    @PutMapping("/rooms/{id}/status")
    @RequirePerm("P09")
    public Result<Void> roomStatus(@PathVariable String id, @RequestParam String status) {
        Classroom old = classroomMapper.selectById(id);
        if (old == null) {
            throw new BizException("教室不存在");
        }
        Classroom update = new Classroom();
        update.setRoomId(id);
        update.setRoomStatus(status);
        classroomMapper.updateById(update);
        auditService.record("02", "classroom:" + id, Map.of("roomStatus", old.getRoomStatus()),
                Map.of("roomStatus", status));
        return Result.ok();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
