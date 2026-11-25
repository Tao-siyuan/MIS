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

    /** 可排节次：一节课占两小节（1-2、3-4…11-12），起点为奇数 */
    private static final int[] PERIOD_START = {1, 3, 5, 7, 9, 11};
    /** 每天最后一小节，用于两节连排的边界判断 */
    private static final int LAST_SLOT = 12;
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
        // 仅统计已由教师承接、尚未排课的教学班
        data.put("unScheduled", tcs.stream()
                .filter(t -> "1".equals(t.getTcStatus())).count());
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
        // 同教师对不同教学班上同一门课程时，尽量沿用同一教室
        Map<String, String> teacherCourseRoom = new HashMap<>();
        for (TeachingClass t : tcs) {
            if (t.getTeacherId() == null) {
                continue;
            }
            String key = t.getTeacherId() + "|" + t.getCourseId();
            occ.stream().filter(o -> o.tcId.equals(t.getTcId()) && o.roomId != null)
                    .findFirst().ifPresent(o -> teacherCourseRoom.putIfAbsent(key, o.roomId));
        }
        int assigned = 0;
        int failed = 0;
        List<String> failDetail = new ArrayList<>();

        for (TeachingClass tc : tcs) {
            if (tc.getTeacherId() == null) {
                failed++;
                failDetail.add(tc.getTcId() + " 未落实任课教师");
                continue;
            }
            Course course = courseMap.get(tc.getCourseId());
            int weeks = tc.getWeekCount() == null || tc.getWeekCount() == 0 ? 16 : tc.getWeekCount();
            // 理论课按单节排课，实践课两节连排；每周次数由学时与周数决定
            int theoryNeed = weeklyCount(tc.getTheoryHours(), weeks);
            int practiceNeed = practiceCount(tc.getPracticeHours(), weeks);
            if (theoryNeed + practiceNeed == 0) {
                theoryNeed = weeklyCount(tc.getHours(), weeks);
            }
            int need = theoryNeed + practiceNeed;
            long existing = occ.stream().filter(o -> o.tcId.equals(tc.getTcId())).count();
            if (existing >= need) {
                continue;
            }
            // 体育课不安排教室，全部安排在操场
            boolean pe = isPeCourse(course);
            List<Integer> periodOrder = buildPeriodOrder(tc, course, morningFirst);
            List<Integer> dayOrder = buildDayOrder(tc);
            int createdForTc = 0;
            int existingTheory = (int) Math.min(existing, theoryNeed);
            String preferRoom = tc.getTeacherId() == null ? null
                    : teacherCourseRoom.get(tc.getTeacherId() + "|" + tc.getCourseId());
            // 理论学时 -> 普通/多媒体教室
            if (theoryNeed > existingTheory) {
                createdForTc += placeSessions(tc, rooms, false, theoryNeed - existingTheory,
                        dayOrder, periodOrder, occ, failDetail, null, 2, preferRoom, pe);
            }
            // 实践学时 -> 按课程要求的实践场地类型（机房/实验室等）匹配场地
            if (practiceNeed > 0) {
                createdForTc += placeSessions(tc, rooms, true, practiceNeed,
                        dayOrder, periodOrder, occ, failDetail,
                        course == null ? null : course.getPracticeRoomType(), 2, null, false);
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
        if (!Arrays.asList("1", "3").contains(tc.getTcStatus())) {
            throw new BizException("教学班尚未由任课教师确认承接，不能排课");
        }
        String roomId = str(body.get("roomId"));
        int day = Integer.parseInt(str(body.get("weekDay")));
        int start = Integer.parseInt(str(body.get("startSlot")));
        int end = Integer.parseInt(str(body.get("endSlot")));
        String schedId = str(body.get("schedId"));
        // 周次范围默认取教学班设置（16 周 / 前 8 周 / 后 8 周）
        String weekRange = str(body.get("weekRange"));
        if (weekRange == null || weekRange.isBlank()) {
            weekRange = weekRangeOf(tc);
        }
        List<String> conflicts = checkConflict(tc, roomId, day, start, end, schedId);
        if (!conflicts.isEmpty()) {
            throw new BizException("存在冲突：" + String.join("；", conflicts));
        }
        Classroom room = classroomMapper.selectById(roomId);
        if (room == null) {
            throw new BizException("教室不存在");
        }
        if (room.getCapacity() < needSeats(tc)) {
            throw new BizException("教室容量不足（需 " + needSeats(tc) + " 座，当前 " + room.getCapacity() + " 座）");
        }
        if (schedId == null || schedId.isBlank()) {
            ScheduleResult sr = new ScheduleResult();
            sr.setSchedId(codeService.nextSchedId());
            sr.setTcId(tcId);
            sr.setRoomId(roomId);
            sr.setWeekRange(weekRange);
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
            update.setWeekRange(weekRange);
            update.setSchedStatus("1");
            resultMapper.updateById(update);
            auditService.record("02", "schedule_result:" + schedId,
                    Map.of("roomId", old.getRoomId(), "weekDay", old.getWeekDay(),
                            "startSlot", old.getStartSlot()),
                    Map.of("roomId", roomId, "weekDay", day, "startSlot", start));
        }
        // 容量上限由排课教室决定
        TeachingClass capacityUpdate = new TeachingClass();
        capacityUpdate.setTcId(tcId);
        capacityUpdate.setCapacity(room.getCapacity());
        tcMapper.updateById(capacityUpdate);
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
            if (Arrays.asList("2", "0", "4", "5", "6", "7").contains(tc.getTcStatus())) {
                continue;
            }
            long segments = resultMapper.selectCount(Wrappers.<ScheduleResult>lambdaQuery()
                    .eq(ScheduleResult::getTcId, tc.getTcId()));
            int need = needSegments(tc);
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
        int[] wr = parseWeekRange(weekRangeOf(tc));
        for (Occ o : occ) {
            if (o.day != day || !overlap(o.start, o.end, start, end) || !o.weekOverlap(wr[0], wr[1])) {
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
        if (room.getCapacity() < needSeats(tc)) {
            messages.add("教室容量不足（需 " + needSeats(tc) + " 座）");
        }
        if (!roomMatchByHours(tc, room.getRoomType())) {
            messages.add("教室类型与理论/实践学时要求不匹配");
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
                       int day, int start, int end, String classIds, int weekStart, int weekEnd) {
        Occ(String tcId, String teacherId, String roomId, int day, int start, int end, String classIds,
            int weekStart, int weekEnd) {
            this(null, tcId, teacherId, roomId, day, start, end, classIds, weekStart, weekEnd);
        }

        /** 两段排课仅在上课周次重叠时才构成冲突（前 8 周与后 8 周可共用同一时段） */
        boolean weekOverlap(int otherStart, int otherEnd) {
            return weekStart <= otherEnd && otherStart <= weekEnd;
        }
    }

    private List<Occ> loadOccupancy(String term) {
        List<Map<String, Object>> rows = viewMapper.listScheduleResults(term, null, null);
        List<Occ> occ = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int[] wr = parseWeekRange(str(row.get("weekRange")));
            occ.add(new Occ(str(row.get("schedId")), str(row.get("tcId")), str(row.get("teacherId")),
                    str(row.get("roomId")),
                    Integer.parseInt(str(row.get("weekDay"))),
                    Integer.parseInt(str(row.get("startSlot"))),
                    Integer.parseInt(str(row.get("endSlot"))),
                    str(row.get("classIds")), wr[0], wr[1]));
        }
        return occ;
    }

    /** 解析“1-8”“9-16”形式的周次范围，缺省按 1-16 处理 */
    private int[] parseWeekRange(String weekRange) {
        if (weekRange == null || weekRange.isBlank()) {
            return new int[]{1, 16};
        }
        String[] parts = weekRange.split("-");
        try {
            int a = Integer.parseInt(parts[0].trim());
            int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : a;
            return new int[]{Math.max(1, Math.min(a, b)), Math.max(1, Math.max(a, b))};
        } catch (NumberFormatException ex) {
            return new int[]{1, 16};
        }
    }

    /** 教学班的上课周次范围，如 16 周为“1-16”，后 8 周为“9-16” */
    private String weekRangeOf(TeachingClass tc) {
        int weeks = tc.getWeekCount() == null || tc.getWeekCount() == 0 ? 16 : tc.getWeekCount();
        int start = tc.getWeekStart() == null || tc.getWeekStart() < 1 ? 1 : tc.getWeekStart();
        return start + "-" + (start + weeks - 1);
    }

    /** 理论课每周节数：每节按 2 学时计（8 节课 = 16 学时） */
    private int weeklyCount(Integer hours, int weeks) {
        if (hours == null || hours <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(hours / 2.0 / weeks));
    }

    /** 教学班需要排的周内次数（理论单节 + 实践两节连排） */
    private int needSegments(TeachingClass tc) {
        int weeks = tc.getWeekCount() == null || tc.getWeekCount() == 0 ? 16 : tc.getWeekCount();
        int theory = weeklyCount(tc.getTheoryHours(), weeks);
        int practice = practiceCount(tc.getPracticeHours(), weeks);
        if (theory + practice == 0) {
            theory = weeklyCount(tc.getHours(), weeks);
        }
        return theory + practice;
    }

    /** 实践课每周次数：每次两节连排（2 小节 = 2 学时） */
    private int practiceCount(Integer hours, int weeks) {
        if (hours == null || hours <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(hours / 2.0 / weeks));
    }

    /** 体育课：体育部（18）开设或课程名含“体育”，不占用教室、安排在操场 */
    private boolean isPeCourse(Course course) {
        return course != null && ("18".equals(course.getOpenCollege())
                || (course.getCourseName() != null && course.getCourseName().contains("体育")));
    }

    /** 排课需要的座位数：以面向人数为准；无面向班级的选修课用教师设定容量 */
    private int needSeats(TeachingClass tc) {
        int studentNum = tc.getStudentNum() == null ? 0 : tc.getStudentNum();
        if (studentNum > 0) {
            return studentNum;
        }
        return tc.getCapacity() == null ? 0 : tc.getCapacity();
    }

    /** 按理论/实践匹配教室类型：理论->普通/多媒体，实践->课程指定的实践场地类型 */
    private List<Classroom> filterRoomsByPart(List<Classroom> rooms, boolean practice, Integer studentNum,
                                              String practiceRoomType) {
        List<Classroom> list = new ArrayList<>();
        for (Classroom room : rooms) {
            boolean typeMatch = practice
                    ? practiceRoomMatches(practiceRoomType, room.getRoomType())
                    : ("1".equals(room.getRoomType()) || "2".equals(room.getRoomType()));
            if (!typeMatch) {
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

    /** 为教学班的某一类学时排 count 次，返回成功次数（理论单节、实践两节连排） */
    private int placeSessions(TeachingClass tc, List<Classroom> rooms, boolean practice, int count,
                              List<Integer> dayOrder, List<Integer> periodOrder, List<Occ> occ,
                              List<String> failDetail, String practiceRoomType,
                              int sessionLen, String preferredRoomId, boolean pe) {
        List<Classroom> candidates;
        if (pe) {
            // 体育课不占用教室，直接在操场（时间占用）排课
            candidates = new ArrayList<>();
            candidates.add(null);
        } else {
            candidates = filterRoomsByPart(rooms, practice, needSeats(tc), practiceRoomType);
            if (candidates.isEmpty()) {
                createConflict(tc.getTcId(), null, "04",
                        "无满足要求的" + (practice ? "实践" : "理论") + "场地（需 " + needSeats(tc) + " 座）",
                        "请调整场地类型或申请增开教室");
                failDetail.add(tc.getTcId() + (practice ? " 无可用实践场地" : " 无可用理论教室"));
                return 0;
            }
        }
        Map<String, String> buildingOf = new HashMap<>();
        for (Classroom r : rooms) {
            buildingOf.put(r.getRoomId(), r.getBuilding());
        }
        int created = 0;
        // 上课周次范围（16 周：1-16；前 8 周：1-8；后 8 周：9-16）
        String weekRange = weekRangeOf(tc);
        // 同一教学班两次课至少间隔一天（每周 2 次为周一/周三，3 次为周一/三/五）
        Set<Integer> usedDays = new HashSet<>();
        for (Occ o : occ) {
            if (o.tcId.equals(tc.getTcId())) {
                usedDays.add(o.day);
            }
        }
        for (int i = 0; i < count; i++) {
            // 优先选择与前几次课至少间隔一天的日期，且保证剩余次数仍排得下（否则 3 次/周会变成周二/四/五）
            List<Integer> candidateDays = new ArrayList<>();
            int remaining = count - i - 1;
            for (int d : dayOrder) {
                boolean gapOk = true;
                for (int used : usedDays) {
                    if (Math.abs(d - used) < 2) {
                        gapOk = false;
                        break;
                    }
                }
                if (gapOk && feasibleRemaining(d, usedDays, remaining, dayOrder)) {
                    candidateDays.add(d);
                }
            }
            if (candidateDays.isEmpty()) {
                // 放宽：仅保证间隔一天，周内天数不足时允许
                for (int d : dayOrder) {
                    boolean gapOk = true;
                    for (int used : usedDays) {
                        if (Math.abs(d - used) < 2) {
                            gapOk = false;
                            break;
                        }
                    }
                    if (gapOk) {
                        candidateDays.add(d);
                    }
                }
            }
            if (candidateDays.isEmpty()) {
                candidateDays.addAll(dayOrder);
            }
            boolean placed = false;
            // 先在所有可用日期的 1-10 节寻找位置，最后才落到 11-12 节（晚上非必要不排）
            for (int startSlot : periodOrder) {
                if (placed) {
                    break;
                }
                int endSlot = startSlot + sessionLen - 1;
                if (endSlot > LAST_SLOT) {
                    continue;
                }
                for (int day : candidateDays) {
                    if (unavailable(tc, day, startSlot)) {
                        continue;
                    }
                    // 同班级换教室尽量同楼栋；同教师同课程优先沿用原教室
                    List<Classroom> ordered = orderRooms(candidates, tc, day, occ, preferredRoomId, buildingOf);
                    for (Classroom room : ordered) {
                        List<String> conflicts = checkOcc(occ, tc, room, day, startSlot, endSlot);
                        if (!conflicts.isEmpty()) {
                            continue;
                        }
                        ScheduleResult sr = new ScheduleResult();
                        sr.setSchedId(codeService.nextSchedId());
                        sr.setTcId(tc.getTcId());
                        sr.setRoomId(room == null ? null : room.getRoomId());
                        sr.setWeekRange(weekRange);
                        sr.setWeekDay(String.valueOf(day));
                        sr.setStartSlot(startSlot);
                        sr.setEndSlot(endSlot);
                        sr.setSchedStatus("0");
                        sr.setLockFlag("0");
                        resultMapper.insert(sr);
                        // 容量上限由排课教室决定；已有容量不覆盖，避免后续时段要求的座位数越来越大
                        if (room != null && (tc.getCapacity() == null || tc.getCapacity() == 0)) {
                            TeachingClass capacityUpdate = new TeachingClass();
                            capacityUpdate.setTcId(tc.getTcId());
                            capacityUpdate.setCapacity(room.getCapacity());
                            tcMapper.updateById(capacityUpdate);
                            tc.setCapacity(room.getCapacity());
                        }
                        int[] wr = parseWeekRange(weekRange);
                        occ.add(new Occ(tc.getTcId(), tc.getTeacherId(),
                                room == null ? null : room.getRoomId(),
                                day, startSlot, endSlot, tc.getClassIds(), wr[0], wr[1]));
                        usedDays.add(day);
                        placed = true;
                        created++;
                        break;
                    }
                    if (placed) {
                        break;
                    }
                }
            }
            if (!placed) {
                String desc = describeFail(occ, tc, candidates, periodOrder);
                createConflict(tc.getTcId(), null, "07",
                        (practice ? "实践" : "理论") + "学时" + desc,
                        "建议调整该教学班的上课时间或增开场地");
                failDetail.add(tc.getTcId() + " 可用时段不足");
                break;
            }
        }
        return created;
    }

    /**
     * 选择某天后是否仍能排下剩余次数：按间隔一天贪心统计后续可用的天数，
     * 避免出现周二/周四之后再无间隔天、只能排成周二/四/五的情况。
     */
    private boolean feasibleRemaining(int candidateDay, Set<Integer> usedDays, int remaining,
                                      List<Integer> dayOrder) {
        if (remaining <= 0) {
            return true;
        }
        Set<Integer> taken = new HashSet<>(usedDays);
        taken.add(candidateDay);
        int capacity = 0;
        for (int d : dayOrder) {
            boolean ok = true;
            for (int u : taken) {
                if (Math.abs(d - u) < 2) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                capacity++;
                taken.add(d);
            }
        }
        return capacity >= remaining;
    }

    /** 教室排序：本教学班已用/指定教室优先 > 同班当天同楼栋 > 其他，同档按容量升序 */
    private List<Classroom> orderRooms(List<Classroom> candidates, TeachingClass tc, int day, List<Occ> occ,
                                       String preferredRoomId, Map<String, String> buildingOf) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        // 本教学班已排教室（尽量固定教室）；同班当天其他课程所在楼宇（换教室距离相近）
        Set<String> ownRooms = new HashSet<>();
        Set<String> sameBuilding = new HashSet<>();
        for (Occ o : occ) {
            if (o.roomId == null) {
                continue;
            }
            if (o.tcId.equals(tc.getTcId())) {
                ownRooms.add(o.roomId);
            } else if (o.day == day && classOverlap(tc.getClassIds(), o.classIds)) {
                sameBuilding.add(buildingOf.getOrDefault(o.roomId, ""));
            }
        }
        List<Classroom> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt((Classroom r) -> {
                    if (ownRooms.contains(r.getRoomId())
                            || (preferredRoomId != null && preferredRoomId.equals(r.getRoomId()))) {
                        return 0;
                    }
                    String b = buildingOf.getOrDefault(r.getRoomId(), "");
                    return !b.isEmpty() && sameBuilding.contains(b) ? 1 : 2;
                })
                .thenComparingInt(r -> r.getCapacity() == null ? 0 : r.getCapacity()));
        return ordered;
    }

    /** 手动选教室时按学时判断教室类型是否匹配 */
    private boolean roomMatchByHours(TeachingClass tc, String roomType) {
        int theory = tc.getTheoryHours() == null ? 0 : tc.getTheoryHours();
        int practice = tc.getPracticeHours() == null ? 0 : tc.getPracticeHours();
        if (theory == 0 && practice == 0) {
            return roomTypeMatch(tc.getTeachType(), roomType);
        }
        Course course = tc.getCourseId() == null ? null : courseMapper.selectById(tc.getCourseId());
        String practiceRoomType = course == null ? null : course.getPracticeRoomType();
        boolean theoryRoom = "1".equals(roomType) || "2".equals(roomType);
        boolean practiceRoom = practiceRoomMatches(practiceRoomType, roomType);
        if (theory > 0 && practice > 0) {
            return theoryRoom || practiceRoom;
        }
        return theory > 0 ? theoryRoom : practiceRoom;
    }

    /** 实践场地与教室类型匹配：机房(41)->机房3；实验室/金工(42/43/44/46)->实验室4；操场(45)不占教室 */
    private boolean practiceRoomMatches(String practiceRoomType, String roomType) {
        if (practiceRoomType == null || practiceRoomType.isBlank()) {
            return "3".equals(roomType) || "4".equals(roomType);
        }
        return switch (practiceRoomType) {
            case "41" -> "3".equals(roomType);
            case "42", "43", "44", "46" -> "4".equals(roomType);
            case "45" -> false;
            default -> "3".equals(roomType) || "4".equals(roomType);
        };
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
        // 第 11-12 节（晚上）非必要不排，放到最后再尝试
        boolean has11 = order.remove(Integer.valueOf(11));
        boolean has12 = order.remove(Integer.valueOf(12));
        if (has11) {
            order.add(11);
        }
        if (has12) {
            order.add(12);
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

    /** 教学形式与教室类型是否匹配（调课选教室时复用） */
    public boolean roomTypeMatch(String teachType, String roomType) {
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
        int[] wr = parseWeekRange(weekRangeOf(tc));
        for (Occ o : occ) {
            if (o.day != day || !overlap(o.start, o.end, start, end) || !o.weekOverlap(wr[0], wr[1])) {
                continue;
            }
            if (tc.getTeacherId() != null && tc.getTeacherId().equals(o.teacherId)) {
                conflicts.add("教师冲突");
            }
            if (room != null && room.getRoomId().equals(o.roomId)) {
                conflicts.add("教室冲突");
            }
            if (classOverlap(tc.getClassIds(), o.classIds)) {
                conflicts.add("班级冲突");
            }
        }
        if (room != null && !roomTimeAvailable(room, day, start)) {
            conflicts.add("教室不可用时段");
        }
        return conflicts;
    }

    private String describeFail(List<Occ> occ, TeachingClass tc, List<Classroom> rooms, List<Integer> periods) {
        for (Classroom room : rooms) {
            if (room == null) {
                continue; // 体育课等无教室课程只校验时段占用
            }
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
