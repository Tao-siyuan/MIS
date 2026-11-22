package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.Classroom;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.ScheduleConflict;
import com.ustb.mis.entity.ScheduleResult;
import com.ustb.mis.entity.ScheduleRule;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.ClassroomMapper;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.ScheduleConflictMapper;
import com.ustb.mis.mapper.ScheduleResultMapper;
import com.ustb.mis.mapper.ScheduleRuleMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排课模块：贪心初排 + 冲突检测 + 人工调优 + 课表发布（对应 3.5.3 / 3.5.4）。
 * 硬约束：教师、教室、班级不冲突，教室容量与类型匹配，不占用教师不可用时间；
 * 软约束：公共基础课优先上午、同一教学班尽量固定教室。
 */
@Service
public class ScheduleService {

    private static final int[] PERIOD_START = {1, 3, 5, 7, 9, 11};
    private static final String[] DAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final Map<String, String> CONFLICT_TYPE_NAME = Map.of(
            "01", "教师冲突", "02", "教室冲突", "03", "班级冲突",
            "04", "教室容量不足", "05", "教室类型不匹配", "06", "教师不可用时段",
            "07", "可用时段不足", "08", "教室不可用时段");

    private final TeachingClassMapper tcMapper;
    private final ScheduleResultMapper resultMapper;
    private final ScheduleConflictMapper conflictMapper;
    private final ClassroomMapper classroomMapper;
    private final ScheduleRuleMapper ruleMapper;
    private final CourseMapper courseMapper;
    private final ViewMapper viewMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public ScheduleService(TeachingClassMapper tcMapper, ScheduleResultMapper resultMapper,
                           ScheduleConflictMapper conflictMapper, ClassroomMapper classroomMapper,
                           ScheduleRuleMapper ruleMapper, CourseMapper courseMapper,
                           ViewMapper viewMapper, CodeService codeService, AuditService auditService) {
        this.tcMapper = tcMapper;
        this.resultMapper = resultMapper;
        this.conflictMapper = conflictMapper;
        this.classroomMapper = classroomMapper;
        this.ruleMapper = ruleMapper;
        this.courseMapper = courseMapper;
        this.viewMapper = viewMapper;
        this.codeService = codeService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> results(String term, String tcId, String status) {
        LoginUser user = UserContext.get();
        if (user != null && "3".equals(user.getDataScope()) && user.getTeacherId() != null) {
            List<Map<String, Object>> all = viewMapper.listScheduleResults(term, tcId, status);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> row : all) {
                if (user.getTeacherId().equals(row.get("teacherId"))) {
                    filtered.add(row);
                }
            }
            return filtered;
        }
        return viewMapper.listScheduleResults(term, tcId, status);
    }

    public List<Map<String, Object>> conflicts(String term, String handleStatus) {
        return viewMapper.listConflicts(term, handleStatus);
    }

    public Map<String, Object> workbench(String term) {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> rows = viewMapper.listScheduleResults(term, null, null);
        List<TeachingClass> tcs = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term));
        long published = rows.stream().filter(r -> "2".equals(r.get("schedStatus"))).count();
        long locked = rows.stream().filter(r -> "1".equals(r.get("lockFlag"))).count();
        data.put("tcCount", tcs.size());
        data.put("schedCount", rows.size());
        data.put("publishedCount", published);
        data.put("lockedCount", locked);
        data.put("pendingConflict", viewMapper.listConflicts(term, "0").size());
        data.put("unScheduled", tcs.stream()
                .filter(t -> !Arrays.asList("3", "4").contains(t.getTcStatus())).count());
        // 教室利用率
        List<Classroom> rooms = classroomMapper.selectList(Wrappers.<Classroom>lambdaQuery());
        List<Map<String, Object>> usage = new ArrayList<>();
        for (Classroom room : rooms) {
            long used = rows.stream().filter(r -> room.getRoomId().equals(r.get("roomId"))).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("roomId", room.getRoomId());
            item.put("roomName", room.getRoomName());
            item.put("capacity", room.getCapacity());
            item.put("roomType", room.getRoomType());
            item.put("roomStatus", room.getRoomStatus());
            item.put("used", used);
            item.put("rate", Math.round(used * 1000.0 / (5 * 6 * 2)) / 10.0);
            usage.add(item);
        }
        usage.sort(Comparator.comparing(m -> -((Number) m.get("used")).intValue()));
        data.put("roomUsage", usage);
        return data;
    }

    /** 自动排课：贪心初排 + 未分配项生成冲突记录 */
    @Transactional
    public Map<String, Object> autoSchedule(String term, List<String> tcIds) {
        long start = System.currentTimeMillis();
        List<TeachingClass> tcs = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term)
                .in(TeachingClass::getTcStatus, Arrays.asList("1", "3"))
                .orderByAsc(TeachingClass::getTcId));
        if (tcIds != null && !tcIds.isEmpty()) {
            tcs.removeIf(t -> !tcIds.contains(t.getTcId()));
        }
        Map<String, Course> courseMap = new HashMap<>();
        for (Course c : courseMapper.selectList(Wrappers.<Course>lambdaQuery())) {
            courseMap.put(c.getCourseId(), c);
        }
        List<Classroom> rooms = classroomMapper.selectList(Wrappers.<Classroom>lambdaQuery()
                .eq(Classroom::getRoomStatus, "1"));
        List<ScheduleRule> rules = ruleMapper.selectList(Wrappers.<ScheduleRule>lambdaQuery()
                .eq(ScheduleRule::getEnable, "1").orderByAsc(ScheduleRule::getPriorityNo));
        boolean morningFirst = rules.stream().anyMatch(r -> "R01".equals(r.getRuleId()));

        List<Occ> occ = loadOccupancy(term);
        int assigned = 0;
        int failed = 0;
        List<String> failDetail = new ArrayList<>();

        for (TeachingClass tc : tcs) {
            if (tc.getTeacherId() == null) {
                failed++;
                failDetail.add(tc.getTcId() + " 未落实任课教师");
                continue;
            }
            long existing = occ.stream().filter(o -> o.tcId.equals(tc.getTcId())).count();
            int need = weeklySessions(tc);
            if (existing >= need) {
                continue;
            }
            Course course = courseMap.get(tc.getCourseId());
            List<Integer> periodOrder = buildPeriodOrder(tc, course, morningFirst);
            List<Integer> dayOrder = buildDayOrder(tc);
            List<Classroom> roomCandidates = filterRooms(rooms, tc.getTeachType(), tc.getStudentNum());
            if (roomCandidates.isEmpty()) {
                failed++;
                createConflict(tc.getTcId(), null, "04",
                        "无满足要求的教室（需 " + tc.getStudentNum() + " 座，教学形式 "
                                + teachTypeName(tc.getTeachType()) + "）",
                        "请调整教室类型或申请增开教室");
                failDetail.add(tc.getTcId() + " 无可用教室");
                continue;
            }
            int createdForTc = 0;
            for (int i = 0; i < need - existing; i++) {
                boolean placed = false;
                for (int day : dayOrder) {
                    if (placed) {
                        break;
                    }
                    for (int startSlot : periodOrder) {
                        int endSlot = startSlot + 1;
                        if (unavailable(tc, day, startSlot)) {
                            continue;
                        }
                        for (Classroom room : roomCandidates) {
                            List<String> conflicts = checkOcc(occ, tc, room, day, startSlot, endSlot);
                            if (conflicts.isEmpty()) {
                                ScheduleResult sr = new ScheduleResult();
                                sr.setSchedId(codeService.nextSchedId());
                                sr.setTcId(tc.getTcId());
                                sr.setRoomId(room.getRoomId());
                                sr.setWeekRange("1-" + (tc.getWeekCount() == null ? 16 : tc.getWeekCount()));
                                sr.setWeekDay(String.valueOf(day));
                                sr.setStartSlot(startSlot);
                                sr.setEndSlot(endSlot);
                                sr.setSchedStatus("0");
                                sr.setLockFlag("0");
                                resultMapper.insert(sr);
                                occ.add(new Occ(tc.getTcId(), tc.getTeacherId(), room.getRoomId(),
                                        day, startSlot, endSlot, tc.getClassIds()));
                                placed = true;
                                createdForTc++;
                                break;
                            }
                        }
                        if (placed) {
                            break;
                        }
                    }
                }
                if (!placed) {
                    String desc = describeFail(occ, tc, roomCandidates, periodOrder);
                    createConflict(tc.getTcId(), null, "07", desc, "建议调整该教学班的上课时间或增开教室");
                    failDetail.add(tc.getTcId() + " 可用时段不足");
                    break;
                }
            }
            if (createdForTc > 0) {
                assigned++;
                tcMapper.update(null, Wrappers.<TeachingClass>lambdaUpdate()
                        .eq(TeachingClass::getTcId, tc.getTcId())
                        .set(TeachingClass::getTcStatus, "3"));
            } else if (createdForTc < need - existing) {
                failed++;
            }
        }
        long cost = System.currentTimeMillis() - start;
        Map<String, Object> result = new HashMap<>();
        result.put("assigned", assigned);
        result.put("failed", failed);
        result.put("detail", failDetail);
        result.put("elapsedMs", cost);
        result.put("conflicts", viewMapper.listConflicts(term, "0").size());
        auditService.record("05", "schedule_result:auto:" + term, null,
                Map.of("assigned", assigned, "failed", failed, "elapsedMs", cost));
        return result;
    }

    /** 手工安排/调整一段排课 */
    @Transactional
    public void assign(Map<String, Object> body) {
        String tcId = str(body.get("tcId"));
        TeachingClass tc = tcMapper.selectById(tcId);
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        String roomId = str(body.get("roomId"));
        int day = Integer.parseInt(str(body.get("weekDay")));
        int start = Integer.parseInt(str(body.get("startSlot")));
        int end = Integer.parseInt(str(body.get("endSlot")));
        String schedId = str(body.get("schedId"));
        List<String> conflicts = checkConflict(tc, roomId, day, start, end, schedId);
        if (!conflicts.isEmpty()) {
            throw new BizException("存在冲突：" + String.join("；", conflicts));
        }
        Classroom room = classroomMapper.selectById(roomId);
        if (room == null) {
            throw new BizException("教室不存在");
        }
        if (room.getCapacity() < tc.getStudentNum()) {
            throw new BizException("教室容量不足（需 " + tc.getStudentNum() + " 座，当前 " + room.getCapacity() + " 座）");
        }
        if (schedId == null || schedId.isBlank()) {
            ScheduleResult sr = new ScheduleResult();
            sr.setSchedId(codeService.nextSchedId());
            sr.setTcId(tcId);
            sr.setRoomId(roomId);
            sr.setWeekRange(str(body.get("weekRange")) == null ? "1-16" : str(body.get("weekRange")));
            sr.setWeekDay(String.valueOf(day));
            sr.setStartSlot(start);
            sr.setEndSlot(end);
            sr.setSchedStatus("0");
            sr.setLockFlag("0");
            resultMapper.insert(sr);
            auditService.record("01", "schedule_result:" + sr.getSchedId(), null, sr);
        } else {
            ScheduleResult old = resultMapper.selectById(schedId);
            if (old == null) {
                throw new BizException("排课记录不存在");
            }
            ScheduleResult update = new ScheduleResult();
            update.setSchedId(schedId);
            update.setRoomId(roomId);
            update.setWeekDay(String.valueOf(day));
            update.setStartSlot(start);
            update.setEndSlot(end);
            update.setSchedStatus("1");
            resultMapper.updateById(update);
            auditService.record("02", "schedule_result:" + schedId,
                    Map.of("roomId", old.getRoomId(), "weekDay", old.getWeekDay(),
                            "startSlot", old.getStartSlot()),
                    Map.of("roomId", roomId, "weekDay", day, "startSlot", start));
        }
        tcMapper.update(null, Wrappers.<TeachingClass>lambdaUpdate()
                .eq(TeachingClass::getTcId, tcId).set(TeachingClass::getTcStatus, "3"));
    }

    @Transactional
    public void removeSched(String schedId) {
        ScheduleResult old = resultMapper.selectById(schedId);
        if (old == null) {
            throw new BizException("排课记录不存在");
        }
        if ("1".equals(old.getLockFlag())) {
            throw new BizException("已发布并锁定的排课记录不可删除，请走调课流程");
        }
        resultMapper.deleteById(schedId);
        long left = resultMapper.selectCount(Wrappers.<ScheduleResult>lambdaQuery()
                .eq(ScheduleResult::getTcId, old.getTcId()));
        if (left == 0) {
            tcMapper.update(null, Wrappers.<TeachingClass>lambdaUpdate()
                    .eq(TeachingClass::getTcId, old.getTcId()).set(TeachingClass::getTcStatus, "1"));
        }
        auditService.record("03", "schedule_result:" + schedId, old, null);
    }

    /** 冲突处理：采纳建议或人工指定时段 */
    @Transactional
    public void resolveConflict(String conflictId, Map<String, Object> body) {
        ScheduleConflict conflict = conflictMapper.selectById(conflictId);
        if (conflict == null) {
            throw new BizException("冲突记录不存在");
        }
        if (body.get("roomId") != null && body.get("weekDay") != null) {
            Map<String, Object> assign = new HashMap<>(body);
            assign.put("tcId", conflict.getTcId());
            assign(assign);
        }
        conflictMapper.update(null, Wrappers.<ScheduleConflict>lambdaUpdate()
                .eq(ScheduleConflict::getConflictId, conflictId)
                .set(ScheduleConflict::getHandleStatus, "1"));
        auditService.record("04", "schedule_conflict:" + conflictId,
                Map.of("handleStatus", "0"), Map.of("handleStatus", "1"));
    }

    /**
     * 课表发布：先校验未处理冲突（BR-05），再校验每个教学班排课完整性（P0-05）。
     * 未排满的教学班不参与发布，仅已排满的置为已发布并锁定。
     */
    @Transactional
    public Map<String, Object> publish(String term) {
        if (term == null || !term.matches("\\d{4}-\\d{4}-[12]")) {
            throw new BizException("学期格式不正确，应为 YYYY-YYYY-S");
        }
        List<TeachingClass> tcs = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, term));
        if (tcs.isEmpty()) {
            throw new BizException("该学期没有教学班，无需发布");
        }
        List<String> tcIds = tcs.stream().map(TeachingClass::getTcId).toList();
        long pending = conflictMapper.selectCount(Wrappers.<ScheduleConflict>lambdaQuery()
                .eq(ScheduleConflict::getHandleStatus, "0")
                .in(ScheduleConflict::getTcId, tcIds));
        if (pending > 0) {
            throw new BizException("该学期仍有 " + pending + " 条未处理冲突，不允许发布（BR-05）");
        }
        List<String> incomplete = new ArrayList<>();
        List<TeachingClass> ready = new ArrayList<>();
        for (TeachingClass tc : tcs) {
            if (Arrays.asList("2", "0", "4").contains(tc.getTcStatus())) {
                continue;
            }
            long segments = resultMapper.selectCount(Wrappers.<ScheduleResult>lambdaQuery()
                    .eq(ScheduleResult::getTcId, tc.getTcId()));
            int need = weeklySessions(tc);
            if (segments < need) {
                incomplete.add(tc.getTcId() + "（已排 " + segments + " 段，需 " + need + " 段）");
            } else {
                ready.add(tc);
            }
        }
        if (!incomplete.isEmpty()) {
            throw new BizException("以下教学班排课未完成，不能发布：" + String.join("；", incomplete));
        }
        int count = 0;
        for (TeachingClass tc : ready) {
            count += resultMapper.update(null, Wrappers.<ScheduleResult>lambdaUpdate()
                    .eq(ScheduleResult::getTcId, tc.getTcId())
                    .set(ScheduleResult::getSchedStatus, "2")
                    .set(ScheduleResult::getLockFlag, "1"));
        }
        List<String> readyIds = ready.stream().map(TeachingClass::getTcId).toList();
        if (!readyIds.isEmpty()) {
            tcMapper.update(null, Wrappers.<TeachingClass>lambdaUpdate()
                    .in(TeachingClass::getTcId, readyIds)
                    .set(TeachingClass::getTcStatus, "4"));
        }
        auditService.record("05", "schedule_result:publish:" + term,
                Map.of("schedStatus", "1"), Map.of("schedStatus", "2", "count", count));
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("tcCount", readyIds.size());
        return result;
    }

    /** 冲突检测（供调课申请与人工调整调用） */
    public List<String> checkConflict(TeachingClass tc, String roomId, int day, int start, int end, String excludeSchedId) {
        List<String> messages = new ArrayList<>();
        Classroom room = classroomMapper.selectById(roomId);
        if (room == null) {
            throw new BizException("教室不存在");
        }
        if (!"1".equals(room.getRoomStatus())) {
            messages.add("教室处于维修停用状态");
        }
        List<Occ> occ = loadOccupancy(tc.getTerm());
        occ.removeIf(o -> excludeSchedId != null && excludeSchedId.equals(o.schedId));
        for (Occ o : occ) {
            if (o.day != day || !overlap(o.start, o.end, start, end)) {
                continue;
            }
            if (o.tcId.equals(tc.getTcId())) {
                messages.add("本教学班在该时段已有安排");
            }
            if (tc.getTeacherId() != null && tc.getTeacherId().equals(o.teacherId)) {
                messages.add("教师冲突：与教学班 " + o.tcId + " 时间重合");
            }
            if (roomId.equals(o.roomId)) {
                messages.add("教室冲突：教室已被教学班 " + o.tcId + " 占用");
            }
            if (classOverlap(tc.getClassIds(), o.classIds)) {
                messages.add("班级冲突：面向班级与教学班 " + o.tcId + " 时间重合");
            }
        }
        if (room.getCapacity() < (tc.getStudentNum() == null ? 0 : tc.getStudentNum())) {
            messages.add("教室容量不足（需 " + tc.getStudentNum() + " 座）");
        }
        if (!roomTypeMatch(tc.getTeachType(), room.getRoomType())) {
            messages.add("教室类型与教学形式不匹配");
        }
        if (!roomTimeAvailable(room, day, start)) {
            messages.add("教室在该时段不可用（" + room.getAvailableTime() + "）");
        }
        if (unavailable(tc, day, start)) {
            messages.add("占用了教师不可用时间（" + tc.getUnavailableTime() + "）");
        }
        return messages.stream().distinct().toList();
    }

    public Map<String, Object> check(Map<String, Object> body) {
        TeachingClass tc = tcMapper.selectById(str(body.get("tcId")));
        if (tc == null) {
            throw new BizException("教学班不存在");
        }
        List<String> conflicts = checkConflict(tc, str(body.get("roomId")),
                Integer.parseInt(str(body.get("weekDay"))),
                Integer.parseInt(str(body.get("startSlot"))),
                Integer.parseInt(str(body.get("endSlot"))),
                str(body.get("schedId")));
        Map<String, Object> result = new HashMap<>();
        result.put("conflicts", conflicts);
        result.put("pass", conflicts.isEmpty());
        return result;
    }

    public List<ScheduleRule> rules() {
        return ruleMapper.selectList(Wrappers.<ScheduleRule>lambdaQuery().orderByAsc(ScheduleRule::getPriorityNo));
    }

    @Transactional
    public void saveRule(Map<String, Object> body) {
        ScheduleRule rule = new ScheduleRule();
        rule.setRuleId(str(body.get("ruleId")));
        rule.setRuleName(str(body.get("ruleName")));
        rule.setRuleType(str(body.get("ruleType")));
        rule.setRuleContent(str(body.get("ruleContent")));
        rule.setPriorityNo(body.get("priorityNo") == null ? 99 : Integer.valueOf(str(body.get("priorityNo"))));
        rule.setEnable(str(body.get("enable")) == null ? "1" : str(body.get("enable")));
        rule.setUpdateTime(LocalDateTime.now());
        if (ruleMapper.selectById(rule.getRuleId()) == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        auditService.record("02", "schedule_rule:" + rule.getRuleId(), null, rule);
    }

    // ---------------- 内部工具 ----------------

    private record Occ(String schedId, String tcId, String teacherId, String roomId,
                       int day, int start, int end, String classIds) {
        Occ(String tcId, String teacherId, String roomId, int day, int start, int end, String classIds) {
            this(null, tcId, teacherId, roomId, day, start, end, classIds);
        }
    }

    private List<Occ> loadOccupancy(String term) {
        List<Map<String, Object>> rows = viewMapper.listScheduleResults(term, null, null);
        List<Occ> occ = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            occ.add(new Occ(str(row.get("schedId")), str(row.get("tcId")), str(row.get("teacherId")),
                    str(row.get("roomId")),
                    Integer.parseInt(str(row.get("weekDay"))),
                    Integer.parseInt(str(row.get("startSlot"))),
                    Integer.parseInt(str(row.get("endSlot"))),
                    str(row.get("classIds"))));
        }
        return occ;
    }

    private int weeklySessions(TeachingClass tc) {
        int hours = tc.getHours() == null ? 48 : tc.getHours();
        int weeks = tc.getWeekCount() == null || tc.getWeekCount() == 0 ? 16 : tc.getWeekCount();
        double perWeek = hours * 1.0 / weeks;
        return Math.max(1, (int) Math.round(perWeek / 2));
    }

    private List<Integer> buildPeriodOrder(TeachingClass tc, Course course, boolean morningFirst) {
        List<Integer> order = new ArrayList<>();
        for (int p : PERIOD_START) {
            order.add(p);
        }
        boolean publicCourse = course != null && Arrays.asList("11", "15").contains(course.getCourseType());
        boolean morning = publicCourse
                || (tc.getPreferSlot() != null && (tc.getPreferSlot().contains("1-2") || tc.getPreferSlot().contains("3-4")));
        if (morningFirst && morning) {
            order.sort(Comparator.comparingInt(p -> p));
        } else {
            order.sort(Comparator.comparingInt(p -> -p));
        }
        if (tc.getPreferSlot() != null) {
            for (int p : PERIOD_START) {
                if (tc.getPreferSlot().contains(p + "-" + (p + 1))) {
                    order.remove(Integer.valueOf(p));
                    order.add(0, p);
                }
            }
        }
        return order;
    }

    /**
     * 候选上课日：默认周一至周五；若教师填写了具体星期（未细分上午/下午/晚上），该日整体排除（P2-03）。
     */
    private List<Integer> buildDayOrder(TeachingClass tc) {
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        String unavailable = tc.getUnavailableTime();
        if (unavailable == null || unavailable.isBlank()) {
            return days;
        }
        boolean wholeDay = !unavailable.contains("上午") && !unavailable.contains("下午")
                && !unavailable.contains("晚上");
        if (!wholeDay) {
            return days;
        }
        for (int day = 1; day <= 7 && day < DAY_NAMES.length; day++) {
            if (unavailable.contains(DAY_NAMES[day])) {
                days.remove(Integer.valueOf(day));
            }
        }
        return days;
    }

    /** 教室可用时间校验：支持“周一至周五 1-12 节”“周一至周日 1-12 节”等写法 */
    private boolean roomTimeAvailable(Classroom room, int day, int start) {
        String avail = room.getAvailableTime();
        if (avail == null || avail.isBlank()) {
            return true;
        }
        java.util.regex.Matcher range = java.util.regex.Pattern
                .compile("周([一二三四五六日])至周([一二三四五六日])").matcher(avail);
        if (range.find()) {
            int from = dayIndex(range.group(1));
            int to = dayIndex(range.group(2));
            if (day < from || day > to) {
                return false;
            }
        } else if (avail.contains("周")) {
            boolean matched = false;
            for (int d = 1; d < DAY_NAMES.length; d++) {
                if (avail.contains(DAY_NAMES[d]) && d == day) {
                    matched = true;
                }
            }
            if (!matched) {
                return false;
            }
        }
        java.util.regex.Matcher slot = java.util.regex.Pattern
                .compile("(\\d+)\\s*-\\s*(\\d+)\\s*节").matcher(avail);
        if (slot.find()) {
            int from = Integer.parseInt(slot.group(1));
            int to = Integer.parseInt(slot.group(2));
            return start >= from && start <= to;
        }
        return true;
    }

    private int dayIndex(String dayChar) {
        return switch (dayChar) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            default -> 7;
        };
    }

    private List<Classroom> filterRooms(List<Classroom> rooms, String teachType, Integer studentNum) {
        List<Classroom> list = new ArrayList<>();
        for (Classroom room : rooms) {
            if (!roomTypeMatch(teachType, room.getRoomType())) {
                continue;
            }
            if (studentNum != null && room.getCapacity() < studentNum) {
                continue;
            }
            list.add(room);
        }
        list.sort(Comparator.comparingInt(Classroom::getCapacity));
        return list;
    }

    private boolean roomTypeMatch(String teachType, String roomType) {
        if ("2".equals(teachType)) {
            return "4".equals(roomType);
        }
        if ("3".equals(teachType)) {
            return "3".equals(roomType);
        }
        return "1".equals(roomType) || "2".equals(roomType);
    }

    private String teachTypeName(String teachType) {
        return switch (teachType == null ? "1" : teachType) {
            case "2" -> "实验";
            case "3" -> "上机";
            default -> "讲授";
        };
    }

    private boolean overlap(int s1, int e1, int s2, int e2) {
        return s1 <= e2 && s2 <= e1;
    }

    private boolean classOverlap(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        Set<String> setA = new HashSet<>(Arrays.asList(a.split(",")));
        for (String cls : b.split(",")) {
            if (setA.contains(cls.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean unavailable(TeachingClass tc, int day, int startSlot) {
        String unavailable = tc.getUnavailableTime();
        if (unavailable == null || unavailable.isBlank()) {
            return false;
        }
        String dayName = DAY_NAMES[day];
        if (!unavailable.contains(dayName)) {
            return false;
        }
        if (unavailable.contains("上午") && startSlot <= 4) {
            return true;
        }
        if (unavailable.contains("下午") && startSlot >= 5 && startSlot <= 8) {
            return true;
        }
        if (unavailable.contains("晚上") && startSlot >= 9) {
            return true;
        }
        return !unavailable.contains("上午") && !unavailable.contains("下午") && !unavailable.contains("晚上");
    }

    private List<String> checkOcc(List<Occ> occ, TeachingClass tc, Classroom room, int day, int start, int end) {
        List<String> conflicts = new ArrayList<>();
        for (Occ o : occ) {
            if (o.day != day || !overlap(o.start, o.end, start, end)) {
                continue;
            }
            if (tc.getTeacherId() != null && tc.getTeacherId().equals(o.teacherId)) {
                conflicts.add("教师冲突");
            }
            if (room.getRoomId().equals(o.roomId)) {
                conflicts.add("教室冲突");
            }
            if (classOverlap(tc.getClassIds(), o.classIds)) {
                conflicts.add("班级冲突");
            }
        }
        if (!roomTimeAvailable(room, day, start)) {
            conflicts.add("教室不可用时段");
        }
        return conflicts;
    }

    private String describeFail(List<Occ> occ, TeachingClass tc, List<Classroom> rooms, List<Integer> periods) {
        for (Classroom room : rooms) {
            for (int day = 1; day <= 5; day++) {
                for (int p : periods) {
                    if (checkOcc(occ, tc, room, day, p, p + 1).isEmpty()) {
                        return "可用时段不足";
                    }
                }
            }
        }
        return "教师、教室与面向班级的可选时段已被占满";
    }

    private void createConflict(String tcId, String otherTcId, String type, String desc, String suggestion) {
        ScheduleConflict conflict = new ScheduleConflict();
        conflict.setConflictId(codeService.nextBillId("CF", "schedule_conflict", "conflict_id"));
        conflict.setTcId(tcId);
        conflict.setOtherTcId(otherTcId);
        conflict.setConflictType(type);
        conflict.setConflictDesc(desc.length() > 150 ? desc.substring(0, 150) : desc);
        conflict.setSuggestPlan(suggestion);
        conflict.setHandleStatus("0");
        conflict.setFoundTime(LocalDateTime.now());
        conflictMapper.insert(conflict);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public String typeName(String code) {
        return CONFLICT_TYPE_NAME.getOrDefault(code, "其他");
    }
}
