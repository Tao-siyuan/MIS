package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.AdjustRecord;
import com.ustb.mis.entity.Classroom;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.AdjustRecordMapper;
import com.ustb.mis.mapper.ClassroomMapper;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.AuditService;
import com.ustb.mis.service.CodeService;
import com.ustb.mis.service.ScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 教师调课：一次性/长期调课申请、冲突检测、审批与恢复（对应 3.5.3 调课审批）。
 */
@RestController
@RequestMapping("/api/adjustments")
public class AdjustController {

    private static final String[] DAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final AdjustRecordMapper adjustMapper;
    private final ScheduleResultMapper resultMapper;
    private final TeachingClassMapper tcMapper;
    private final ClassroomMapper classroomMapper;
    private final ViewMapper viewMapper;
    private final ScheduleService scheduleService;
    private final CodeService codeService;
    private final AuditService auditService;
    private final CourseMapper courseMapper;

    public AdjustController(AdjustRecordMapper adjustMapper, ScheduleResultMapper resultMapper,
                            TeachingClassMapper tcMapper, ClassroomMapper classroomMapper,
                            ViewMapper viewMapper, ScheduleService scheduleService,
                            CodeService codeService, AuditService auditService, CourseMapper courseMapper) {
        this.adjustMapper = adjustMapper;
        this.resultMapper = resultMapper;
        this.tcMapper = tcMapper;
        this.classroomMapper = classroomMapper;
        this.viewMapper = viewMapper;
        this.scheduleService = scheduleService;
        this.codeService = codeService;
        this.auditService = auditService;
        this.courseMapper = courseMapper;
    }

    @GetMapping
    @RequirePerm({"P10", "P11", "P12"})
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String term,
                                                  @RequestParam(required = false) String approveStatus) {
        LoginUser user = UserContext.require();
        String teacherId = null;
        if (user.getTeacherId() != null && !user.hasPerm("P11")) {
            teacherId = user.getTeacherId();
        }
        List<Map<String, Object>> rows = viewMapper.listAdjustments(term, approveStatus, teacherId);
        // 学院教务只看本学院开课课程的调课申请
        if ("2".equals(user.getDataScope()) && user.getCollegeId() != null) {
            rows = rows.stream()
                    .filter(r -> user.getCollegeId().equals(String.valueOf(r.get("collegeId"))))
                    .toList();
        }
        // 学生只能查看本人班级已批准的调课（P1-09）
        if (user.getClassId() != null) {
            rows = rows.stream()
                    .filter(r -> "1".equals(String.valueOf(r.get("approveStatus"))))
                    .filter(r -> r.get("classIds") != null
                            && String.valueOf(r.get("classIds")).contains(user.getClassId()))
                    .toList();
        }
        return Result.ok(rows);
    }

    @PostMapping
    @RequirePerm({"P10", "P11"})
    public Result<Void> apply(@RequestBody Map<String, Object> body) {
        String schedId = str(body.get("schedId"));
        ScheduleResult sched = resultMapper.selectById(schedId);
        if (sched == null) {
            throw new BizException("排课记录不存在");
        }
        TeachingClass tc = tcMapper.selectById(sched.getTcId());
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        LoginUser current = UserContext.require();
        if (current.getTeacherId() != null && !current.hasPerm("P11")
                && !current.getTeacherId().equals(tc.getTeacherId())) {
            throw new BizException(403, "只能对本人任教的教学班发起调课");
        }
        String roomId = str(body.get("roomId"));
        int day = Integer.parseInt(str(body.get("weekDay")));
        int start = Integer.parseInt(str(body.get("startSlot")));
        int end = Integer.parseInt(str(body.get("endSlot")));
        List<String> conflicts = scheduleService.checkConflict(tc, roomId, day, start, end, schedId);
        if (!conflicts.isEmpty()) {
            throw new BizException("新安排存在冲突，请更换时段或教室：" + String.join("；", conflicts));
        }
        Classroom oldRoom = classroomMapper.selectById(sched.getRoomId());
        Classroom newRoom = classroomMapper.selectById(roomId);

        AdjustRecord record = new AdjustRecord();
        record.setAdjustId(codeService.nextBillId("AD", "adjust_record", "adjust_id"));
        record.setSchedId(schedId);
        record.setAdjustType(str(body.get("adjustType")) == null ? "1" : str(body.get("adjustType")));
        record.setOldValue(format(sched.getWeekDay(), sched.getStartSlot(), sched.getEndSlot(),
                oldRoom == null ? sched.getRoomId() : oldRoom.getRoomName()));
        record.setNewValue(format(String.valueOf(day), start, end,
                newRoom == null ? roomId : newRoom.getRoomName()));
        record.setOldRoomId(sched.getRoomId());
        record.setOldWeekDay(sched.getWeekDay());
        record.setOldStartSlot(sched.getStartSlot());
        record.setOldEndSlot(sched.getEndSlot());
        record.setOldWeekRange(sched.getWeekRange());
        record.setNewRoomId(roomId);
        record.setNewWeekDay(String.valueOf(day));
        record.setNewStartSlot(start);
        record.setNewEndSlot(end);
        record.setReason(str(body.get("reason")));
        if (body.get("effectDate") != null) {
            record.setEffectDate(LocalDate.parse(str(body.get("effectDate"))));
        }
        record.setApproveStatus("0");
        record.setRestoreFlag("0");
        record.setNotifyStatus("0");
        record.setCreateTime(LocalDateTime.now());
        adjustMapper.insert(record);
        auditService.record("01", "adjust_record:" + record.getAdjustId(), null, record);
        return Result.ok();
    }

    /**
     * 调课可选教室：仅返回在“新时间”可用、且满足教学形式（教室类型）与人数要求的教室，
     * 同时给出该时段学生已有课程冲突的涉及人数，便于教师判断（教师端修改意见第 4 条）。
     */
    @PostMapping("/available-rooms")
    @RequirePerm({"P10", "P11"})
    public Result<Map<String, Object>> availableRooms(@RequestBody Map<String, Object> body) {
        String schedId = str(body.get("schedId"));
        ScheduleResult sched = schedId == null ? null : resultMapper.selectById(schedId);
        if (sched == null) {
            throw new BizException("请先选择原排课安排");
        }
        TeachingClass tc = tcMapper.selectById(sched.getTcId());
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        int day = body.get("weekDay") == null ? Integer.parseInt(sched.getWeekDay())
                : Integer.parseInt(str(body.get("weekDay")));
        int start = body.get("startSlot") == null ? sched.getStartSlot()
                : Integer.parseInt(str(body.get("startSlot")));
        int end = body.get("endSlot") == null ? start + 1 : Integer.parseInt(str(body.get("endSlot")));

        // 该时段已被占用的教室
        java.util.Map<String, TeachingClass> tcMap = new java.util.HashMap<>();
        for (TeachingClass item : tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, tc.getTerm()))) {
            tcMap.put(item.getTcId(), item);
        }
        List<ScheduleResult> sameTime = resultMapper.selectList(Wrappers.<ScheduleResult>lambdaQuery()
                .in(ScheduleResult::getTcId, tcMap.keySet())
                .eq(ScheduleResult::getWeekDay, String.valueOf(day))
                .le(ScheduleResult::getStartSlot, end)
                .ge(ScheduleResult::getEndSlot, start));
        java.util.Set<String> busyRooms = new java.util.HashSet<>();
        for (ScheduleResult r : sameTime) {
            if (!r.getSchedId().equals(schedId) && r.getRoomId() != null) {
                busyRooms.add(r.getRoomId());
            }
        }

        // 该时段学生（班级）已有课程的情况
        java.util.List<String> classIds = splitClasses(tc.getClassIds());
        java.util.List<String> conflictDetails = new java.util.ArrayList<>();
        int conflictStudents = 0;
        for (ScheduleResult r : sameTime) {
            if (r.getSchedId().equals(schedId) || r.getTcId().equals(tc.getTcId())) {
                continue;
            }
            TeachingClass other = tcMap.get(r.getTcId());
            if (other == null) {
                continue;
            }
            java.util.List<String> overlap = overlapClasses(classIds, splitClasses(other.getClassIds()));
            if (overlap.isEmpty()) {
                continue;
            }
            conflictDetails.add(String.join("/", overlap) + "《" + courseName(other.getCourseId()) + "》"
                    + other.getStudentNum() + "人");
            conflictStudents += other.getStudentNum() == null ? 0 : other.getStudentNum();
        }

        java.util.List<Map<String, Object>> rooms = new java.util.ArrayList<>();
        for (Classroom room : classroomMapper.selectList(Wrappers.<Classroom>lambdaQuery()
                .eq(Classroom::getRoomStatus, "1")
                .orderByAsc(Classroom::getRoomId))) {
            if (busyRooms.contains(room.getRoomId())) {
                continue;
            }
            if (!scheduleService.roomTypeMatch(tc.getTeachType(), room.getRoomType())) {
                continue;
            }
            if (room.getCapacity() != null && tc.getStudentNum() != null
                    && room.getCapacity() < tc.getStudentNum()) {
                continue;
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("roomId", room.getRoomId());
            item.put("roomName", room.getRoomName());
            item.put("capacity", room.getCapacity());
            item.put("roomType", room.getRoomType());
            item.put("availableTime", room.getAvailableTime());
            rooms.add(item);
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("rooms", rooms);
        data.put("studentNum", tc.getStudentNum());
        data.put("classIds", tc.getClassIds());
        data.put("conflictStudents", conflictStudents);
        data.put("conflictDetails", conflictDetails);
        return Result.ok(data);
    }

    private String courseName(String courseId) {
        Course course = courseId == null ? null : courseMapper.selectById(courseId);
        return course == null ? String.valueOf(courseId) : course.getCourseName();
    }

    private java.util.List<String> splitClasses(String classIds) {
        if (classIds == null || classIds.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String part : classIds.split("[,，/、;；\\s]+")) {
            if (!part.isBlank()) {
                list.add(part.trim());
            }
        }
        return list;
    }

    private java.util.List<String> overlapClasses(java.util.List<String> mine, java.util.List<String> others) {
        java.util.List<String> overlap = new java.util.ArrayList<>();
        for (String c : mine) {
            if (others.contains(c)) {
                overlap.add(c);
            }
        }
        return overlap;
    }

    /**
     * 学院教务初审：通过后转教务处备案（approve_status 0 → 4），驳回置 2；
     * 仅可审核本学院开课课程的调课申请。
     */
    @PostMapping("/{id}/college-review")
    @RequirePerm("P21")
    public Result<Void> collegeReview(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        AdjustRecord record = adjustMapper.selectById(id);
        if (record == null) {
            throw new BizException("调课申请不存在");
        }
        if (!"0".equals(record.getApproveStatus())) {
            throw new BizException("该调课申请不在学院待审状态");
        }
        LoginUser user = UserContext.require();
        ScheduleResult origin = resultMapper.selectById(record.getSchedId());
        TeachingClass tc = origin == null ? null : tcMapper.selectById(origin.getTcId());
        if (tc == null) {
            throw new BizException("教学班已不存在");
        }
        Course course = courseMapper.selectById(tc.getCourseId());
        if ("2".equals(user.getDataScope()) && course != null && user.getCollegeId() != null
                && !user.getCollegeId().equals(course.getOpenCollege())) {
            throw new BizException(403, "只能审核本学院开课课程的调课申请");
        }
        boolean pass = body == null || body.get("pass") == null || "1".equals(str(body.get("pass")));
        String opinion = body == null ? null : str(body.get("opinion"));
        if (!pass && (opinion == null || opinion.isBlank())) {
            throw new BizException("驳回时须填写理由");
        }
        AdjustRecord update = new AdjustRecord();
        update.setAdjustId(id);
        update.setApproveStatus(pass ? "4" : "2");
        update.setApprover(user.getUserId());
        update.setApproveOpinion(opinion);
        adjustMapper.updateById(update);
        auditService.record("04", "adjust_record:" + id, Map.of("approveStatus", "0"),
                Map.of("approveStatus", pass ? "4" : "2", "collegeOpinion", opinion == null ? "" : opinion));
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    @RequirePerm("P11")
    public Result<Void> approve(@PathVariable String id, @RequestBody Map<String, Object> body) {
        AdjustRecord record = adjustMapper.selectById(id);
        if (record == null) {
            throw new BizException("调课申请不存在");
        }
        if (!"0".equals(record.getApproveStatus()) && !"4".equals(record.getApproveStatus())) {
            throw new BizException("该申请已处理");
        }
        // 审批前复核冲突，避免申请后课表变化导致新冲突（P1-13）
        ScheduleResult origin = resultMapper.selectById(record.getSchedId());
        if (origin == null) {
            throw new BizException("原排课记录已不存在，请驳回该申请");
        }
        TeachingClass tc = tcMapper.selectById(origin.getTcId());
        if (tc == null) {
            throw new BizException("教学班已不存在，请驳回该申请");
        }
        List<String> conflicts = scheduleService.checkConflict(tc, record.getNewRoomId(),
                Integer.parseInt(record.getNewWeekDay()), record.getNewStartSlot(),
                record.getNewEndSlot(), record.getSchedId());
        if (!conflicts.isEmpty()) {
            throw new BizException("复核发现新冲突，不能批准：" + String.join("；", conflicts));
        }
        ScheduleResult update = new ScheduleResult();
        update.setSchedId(record.getSchedId());
        update.setRoomId(record.getNewRoomId());
        update.setWeekDay(record.getNewWeekDay());
        update.setStartSlot(record.getNewStartSlot());
        update.setEndSlot(record.getNewEndSlot());
        update.setSchedStatus("3");
        resultMapper.updateById(update);

        AdjustRecord updateRecord = new AdjustRecord();
        updateRecord.setAdjustId(id);
        updateRecord.setApproveStatus("1");
        updateRecord.setApprover(UserContext.require().getUserId());
        updateRecord.setApproveOpinion(str(body.get("opinion")));
        updateRecord.setNotifyStatus("1");
        adjustMapper.updateById(updateRecord);
        auditService.record("04", "adjust_record:" + id, Map.of("approveStatus", "0"),
                Map.of("approveStatus", "1", "newValue", record.getNewValue()));
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    @RequirePerm("P11")
    public Result<Void> reject(@PathVariable String id, @RequestBody Map<String, Object> body) {
        AdjustRecord record = adjustMapper.selectById(id);
        if (record == null) {
            throw new BizException("调课申请不存在");
        }
        String opinion = str(body.get("opinion"));
        if (opinion == null || opinion.isBlank()) {
            throw new BizException("驳回时审批意见必填");
        }
        AdjustRecord update = new AdjustRecord();
        update.setAdjustId(id);
        update.setApproveStatus("2");
        update.setApproveOpinion(opinion);
        update.setApprover(UserContext.require().getUserId());
        adjustMapper.updateById(update);
        auditService.record("04", "adjust_record:" + id, Map.of("approveStatus", "0"),
                Map.of("approveStatus", "2"));
        return Result.ok();
    }

    /** 撤回调课申请：仅待审批（approve_status=0）的申请可撤回，撤回后置为已撤回（3） */
    @PostMapping("/{id}/withdraw")
    @RequirePerm({"P10", "P11"})
    public Result<Void> withdraw(@PathVariable String id) {
        AdjustRecord record = adjustMapper.selectById(id);
        if (record == null) {
            throw new BizException("调课申请不存在");
        }
        if (!"0".equals(record.getApproveStatus())) {
            throw new BizException("仅待审批的调课申请可撤回");
        }
        LoginUser current = UserContext.require();
        if (!current.hasPerm("P11")) {
            ScheduleResult origin = resultMapper.selectById(record.getSchedId());
            TeachingClass tc = origin == null ? null : tcMapper.selectById(origin.getTcId());
            if (tc == null || current.getTeacherId() == null
                    || !current.getTeacherId().equals(tc.getTeacherId())) {
                throw new BizException(403, "只能撤回本人教学班的调课申请");
            }
        }
        AdjustRecord update = new AdjustRecord();
        update.setAdjustId(id);
        update.setApproveStatus("3");
        update.setApproveOpinion("申请人撤回");
        adjustMapper.updateById(update);
        auditService.record("02", "adjust_record:" + id, Map.of("approveStatus", "0"),
                Map.of("approveStatus", "3"));
        return Result.ok();
    }

    /** 撤销调课：按原安排快照恢复排课结果 */
    @PostMapping("/{id}/restore")
    @RequirePerm({"P11", "P09"})
    public Result<Void> restore(@PathVariable String id) {
        AdjustRecord record = adjustMapper.selectById(id);
        if (record == null) {
            throw new BizException("调课记录不存在");
        }
        if (!"1".equals(record.getApproveStatus())) {
            throw new BizException("仅已批准的调课可撤销");
        }
        if ("1".equals(record.getRestoreFlag())) {
            throw new BizException("该调课已恢复");
        }
        ScheduleResult update = new ScheduleResult();
        update.setSchedId(record.getSchedId());
        update.setRoomId(record.getOldRoomId());
        update.setWeekDay(record.getOldWeekDay());
        update.setStartSlot(record.getOldStartSlot());
        update.setEndSlot(record.getOldEndSlot());
        update.setSchedStatus("2");
        resultMapper.updateById(update);

        AdjustRecord updateRecord = new AdjustRecord();
        updateRecord.setAdjustId(id);
        updateRecord.setRestoreFlag("1");
        adjustMapper.updateById(updateRecord);
        auditService.record("02", "adjust_record:" + id, Map.of("restoreFlag", "0"),
                Map.of("restoreFlag", "1", "restored", record.getOldValue()));
        return Result.ok();
    }

    private String format(String weekDay, Integer start, Integer end, String roomName) {
        int day = Integer.parseInt(weekDay);
        String dayName = day >= 1 && day <= 7 ? DAY_NAMES[day] : "";
        return dayName + " " + start + "-" + end + "节 " + roomName;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
