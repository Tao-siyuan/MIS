package com.ustb.mis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.mis.common.BizException;
import com.ustb.mis.entity.EvalPlan;
import com.ustb.mis.entity.EvalRecord;
import com.ustb.mis.entity.TeachingClass;
import com.ustb.mis.mapper.EvalPlanMapper;
import com.ustb.mis.mapper.EvalRecordMapper;
import com.ustb.mis.mapper.TeachingClassMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程评价模块（对应 3.5.7）：方案配置、匿名提交、无效样本剔除与结果汇总。
 */
@Service
public class EvalService {

    private final EvalPlanMapper planMapper;
    private final EvalRecordMapper recordMapper;
    private final TeachingClassMapper tcMapper;
    private final ViewMapper viewMapper;
    private final ObjectMapper objectMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public EvalService(EvalPlanMapper planMapper, EvalRecordMapper recordMapper,
                       TeachingClassMapper tcMapper, ViewMapper viewMapper,
                       ObjectMapper objectMapper, CodeService codeService, AuditService auditService) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.tcMapper = tcMapper;
        this.viewMapper = viewMapper;
        this.objectMapper = objectMapper;
        this.codeService = codeService;
        this.auditService = auditService;
    }

    public List<EvalPlan> plans(String term) {
        LoginUser user = UserContext.get();
        // 教师只看到教务处已发布结果的方案，未发布前不开放评教结果
        boolean teacher = user != null && user.getTeacherId() != null;
        return planMapper.selectList(Wrappers.<EvalPlan>lambdaQuery()
                .eq(term != null && !term.isBlank(), EvalPlan::getTerm, term)
                .eq(teacher, EvalPlan::getPlanStatus, "3")
                .orderByDesc(EvalPlan::getPlanId));
    }

    @Transactional
    public void savePlan(Map<String, Object> body) {
        String planId = str(body.get("planId"));
        EvalPlan plan = new EvalPlan();
        plan.setPlanId(planId);
        plan.setTerm(str(body.get("term")));
        plan.setPlanName(str(body.get("planName")));
        plan.setIndicatorJson(str(body.get("indicatorJson")));
        plan.setOpenDate(LocalDate.parse(str(body.get("openDate"))));
        plan.setCloseDate(LocalDate.parse(str(body.get("closeDate"))));
        plan.setAnonymity(str(body.get("anonymity")) == null ? "1" : str(body.get("anonymity")));
        plan.setMinSample(body.get("minSample") == null ? 10 : Integer.valueOf(str(body.get("minSample"))));
        plan.setPlanStatus("0");
        if (!plan.getCloseDate().isAfter(plan.getOpenDate())) {
            throw new BizException("结束日期必须晚于开始日期");
        }
        double sum = weights(plan.getIndicatorJson()).values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new BizException("各指标权重之和必须等于 1（当前 " + sum + "）");
        }
        if (planMapper.selectById(planId) == null) {
            planMapper.insert(plan);
            auditService.record("01", "eval_plan:" + planId, null, plan);
        } else {
            planMapper.updateById(plan);
            auditService.record("02", "eval_plan:" + planId, null, plan);
        }
    }

    @Transactional
    public void publishPlan(String planId) {
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        EvalPlan update = new EvalPlan();
        update.setPlanId(planId);
        update.setPlanStatus("1");
        planMapper.updateById(update);
        auditService.record("05", "eval_plan:" + planId, Map.of("planStatus", plan.getPlanStatus()),
                Map.of("planStatus", "1"));
    }

    @Transactional
    public void finishPlan(String planId) {
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        if (LocalDate.now().isBefore(plan.getCloseDate())) {
            throw new BizException("评教尚未结束，暂不汇总");
        }
        List<Map<String, Object>> records = viewMapper.listEvalRecords(planId, null);
        for (Map<String, Object> row : records) {
            String itemScore = str(row.get("itemScore"));
            String comment = str(row.get("comment"));
            if (sameItems(itemScore) && (comment == null || comment.isBlank())) {
                recordMapper.update(null, Wrappers.<EvalRecord>lambdaUpdate()
                        .eq(EvalRecord::getRecordId, str(row.get("recordId")))
                        .set(EvalRecord::getValidFlag, "0"));
            }
        }
        EvalPlan update = new EvalPlan();
        update.setPlanId(planId);
        update.setPlanStatus("2");
        planMapper.updateById(update);
        auditService.record("06", "eval_plan:" + planId, Map.of("planStatus", "1"),
                Map.of("planStatus", "2"));
    }

    /** 教务处将评教结果发布给各任课教师：发布后教师仅能看到本人教学班的汇总结果 */
    @Transactional
    public void publishResult(String planId) {
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        if (!"2".equals(plan.getPlanStatus())) {
            throw new BizException("评教尚未结束并汇总，暂不能发布结果");
        }
        EvalPlan update = new EvalPlan();
        update.setPlanId(planId);
        update.setPlanStatus("3");
        planMapper.updateById(update);
        auditService.record("05", "eval_plan:" + planId, Map.of("planStatus", "2"),
                Map.of("planStatus", "3"));
    }

    /** 撤回已发布的评教结果：撤回后教师端立即不可再查看，方案回到“已结束”状态 */
    @Transactional
    public void revokeResult(String planId) {
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        if (!"3".equals(plan.getPlanStatus())) {
            throw new BizException("评教结果尚未发布，无需撤回");
        }
        EvalPlan update = new EvalPlan();
        update.setPlanId(planId);
        update.setPlanStatus("2");
        planMapper.updateById(update);
        auditService.record("05", "eval_plan:" + planId, Map.of("planStatus", "3"),
                Map.of("planStatus", "2"));
    }

    /** 当前用户的评教任务：学生按面向班级匹配，教师按本人教学班匹配 */
    public List<Map<String, Object>> tasks(String planId) {
        LoginUser user = UserContext.require();
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        // 教师不参与学生评教，仅可查看教务处反馈的最终结果（教师端修改意见第 6 条）
        if (user.getTeacherId() != null) {
            return List.of();
        }
        List<TeachingClass> tcs = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, plan.getTerm()));
        List<Map<String, Object>> all = viewMapper.listTeachingClasses(plan.getTerm(), null, null, null, null, null);
        Map<String, Map<String, Object>> tcInfo = new HashMap<>();
        for (Map<String, Object> row : all) {
            tcInfo.put(str(row.get("tcId")), row);
        }
        List<EvalRecord> records = recordMapper.selectList(Wrappers.<EvalRecord>lambdaQuery()
                .eq(EvalRecord::getPlanId, planId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeachingClass tc : tcs) {
            boolean mine;
            if ("3".equals(user.getDataScope()) && user.getTeacherId() != null) {
                mine = user.getTeacherId().equals(tc.getTeacherId());
            } else if (user.getClassId() != null) {
                mine = tc.getClassIds() != null && tc.getClassIds().contains(user.getClassId());
            } else {
                mine = true;
            }
            if (!mine) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(tcInfo.getOrDefault(tc.getTcId(), Map.of()));
            boolean submitted = records.stream()
                    .anyMatch(r -> r.getTcId().equals(tc.getTcId()) && "1".equals(r.getValidFlag()));
            item.put("submitted", submitted);
            result.add(item);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> submit(Map<String, Object> body) {
        LoginUser user = UserContext.require();
        if (user.getTeacherId() != null) {
            throw new BizException(403, "教师不参与学生评教，只能查看教务处反馈的评教结果");
        }
        String planId = str(body.get("planId"));
        String tcId = str(body.get("tcId"));
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(plan.getOpenDate()) || today.isAfter(plan.getCloseDate())) {
            throw new BizException("不在评教时间内，无法评教");
        }
        if (!"1".equals(plan.getPlanStatus())) {
            throw new BizException("该评教方案未在进行中");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) body.getOrDefault("items", new HashMap<>());
        Map<String, Double> weights = weights(plan.getIndicatorJson());
        double score = 0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            Object raw = items.get(e.getKey());
            if (raw == null) {
                throw new BizException("请完成所有评价指标：" + e.getKey());
            }
            double v = Double.parseDouble(String.valueOf(raw));
            if (v < 0 || v > 100) {
                throw new BizException("分项得分应在 0～100 之间");
            }
            score += v * e.getValue();
        }
        String stuId = "1".equals(plan.getAnonymity()) ? anonymize(user.getUserId()) : user.getUserId();
        long dup = recordMapper.selectCount(Wrappers.<EvalRecord>lambdaQuery()
                .eq(EvalRecord::getPlanId, planId)
                .eq(EvalRecord::getTcId, tcId)
                .eq(EvalRecord::getStuId, stuId));
        if (dup > 0) {
            throw new BizException("已提交，请勿重复评教");
        }
        EvalRecord record = new EvalRecord();
        record.setRecordId(codeService.nextBillId("ER", "eval_record", "record_id"));
        record.setPlanId(planId);
        record.setTcId(tcId);
        record.setStuId(stuId);
        record.setScore(BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP));
        record.setItemScore(toJson(items));
        record.setComment(str(body.get("comment")));
        record.setValidFlag("1");
        record.setSubmitTime(LocalDateTime.now());
        recordMapper.insert(record);
        auditService.record("01", "eval_record:" + record.getRecordId(), null,
                Map.of("planId", planId, "tcId", tcId));
        Map<String, Object> result = new HashMap<>();
        result.put("score", record.getScore());
        return result;
    }

    /** 结果统计：按教学班汇总均值，样本低于下限不参与排名 */
    public Map<String, Object> stats(String planId) {
        EvalPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException("评教方案不存在");
        }
        // 教师只有教务处点击“发布结果”后才能查看本人教学班的评教结果
        LoginUser current = UserContext.get();
        if (current != null && current.getTeacherId() != null && !"3".equals(plan.getPlanStatus())) {
            throw new BizException(403, "评教结果尚未发布，请等待教务处在评教结束后发布");
        }
        List<Map<String, Object>> records = visibleRecords(plan, viewMapper.listEvalRecords(planId, null));
        Map<String, List<Map<String, Object>>> byTc = new LinkedHashMap<>();
        for (Map<String, Object> row : records) {
            byTc.computeIfAbsent(str(row.get("tcId")), k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> tcStats = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byTc.entrySet()) {
            List<Map<String, Object>> list = e.getValue();
            List<Map<String, Object>> valid = list.stream()
                    .filter(r -> "1".equals(r.get("validFlag"))).toList();
            if (valid.isEmpty()) {
                continue;
            }
            double avg = valid.stream().mapToDouble(r -> Double.parseDouble(str(r.get("score")))).average().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tcId", e.getKey());
            item.put("courseName", valid.get(0).get("courseName"));
            item.put("teacherName", valid.get(0).get("teacherName"));
            item.put("collegeName", valid.get(0).get("collegeName"));
            item.put("sample", valid.size());
            item.put("avgScore", Math.round(avg * 10) / 10.0);
            item.put("insufficient", valid.size() < plan.getMinSample());
            item.put("minSample", plan.getMinSample());
            Map<String, Double> itemAvg = new LinkedHashMap<>();
            for (Map<String, Object> r : valid) {
                Map<String, Double> parsed = parseItemScore(str(r.get("itemScore")));
                parsed.forEach((k, v) -> itemAvg.merge(k, v, Double::sum));
            }
            int n = valid.size();
            Map<String, Double> finalAvg = new LinkedHashMap<>();
            itemAvg.forEach((k, v) -> finalAvg.put(k, Math.round(v / n * 10) / 10.0));
            item.put("itemAvg", finalAvg);
            tcStats.add(item);
        }
        tcStats.sort(Comparator.comparing(m -> -((Double) m.get("avgScore"))));
        double all = records.stream().filter(r -> "1".equals(r.get("validFlag")))
                .mapToDouble(r -> Double.parseDouble(str(r.get("score")))).average().orElse(0);
        long[] buckets = new long[5];
        for (Map<String, Object> r : records) {
            if (!"1".equals(r.get("validFlag"))) {
                continue;
            }
            double s = Double.parseDouble(str(r.get("score")));
            if (s >= 95) {
                buckets[0]++;
            } else if (s >= 90) {
                buckets[1]++;
            } else if (s >= 85) {
                buckets[2]++;
            } else if (s >= 80) {
                buckets[3]++;
            } else {
                buckets[4]++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("plan", plan);
        data.put("totalRecord", records.size());
        data.put("validRecord", records.stream().filter(r -> "1".equals(r.get("validFlag"))).count());
        data.put("avgScore", Math.round(all * 10) / 10.0);
        data.put("tcStats", tcStats);
        data.put("distribution", Map.of("95以上", buckets[0], "90-94", buckets[1],
                "85-89", buckets[2], "80-84", buckets[3], "80以下", buckets[4]));
        return data;
    }

    public List<Map<String, Object>> records(String planId, String tcId) {
        LoginUser current = UserContext.get();
        // 学生原始评教记录不向教师开放，教师仅看汇总结果（教师端修改意见第 6 条）
        if (current != null && current.getTeacherId() != null) {
            return List.of();
        }
        EvalPlan plan = planMapper.selectById(planId);
        return visibleRecords(plan, viewMapper.listEvalRecords(planId, tcId));
    }

    /**
     * 数据范围过滤（P1-09）：全校数据范围可见全部；教师仅本人教学班；学院教务员仅本学院。
     */
    private List<Map<String, Object>> visibleRecords(EvalPlan plan, List<Map<String, Object>> rows) {
        LoginUser user = UserContext.get();
        if (user == null || "1".equals(user.getDataScope())) {
            return rows;
        }
        if (plan == null) {
            return List.of();
        }
        List<TeachingClass> tcs = tcMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTerm, plan.getTerm()));
        Map<String, TeachingClass> tcMap = new HashMap<>();
        for (TeachingClass tc : tcs) {
            tcMap.put(tc.getTcId(), tc);
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            TeachingClass tc = tcMap.get(str(row.get("tcId")));
            if (tc == null) {
                continue;
            }
            boolean visible;
            if (user.getTeacherId() != null && "3".equals(user.getDataScope())) {
                visible = user.getTeacherId().equals(tc.getTeacherId());
            } else if ("2".equals(user.getDataScope()) && user.getCollegeId() != null) {
                visible = user.getCollegeId().equals(str(row.get("collegeId")));
            } else {
                visible = true;
            }
            if (visible) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private Map<String, Double> weights(String json) {
        Map<String, Double> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> item : list) {
                map.put(String.valueOf(item.get("name")), Double.parseDouble(String.valueOf(item.get("w"))));
            }
        } catch (Exception e) {
            throw new BizException("评价指标格式不正确，应为 JSON 数组");
        }
        return map;
    }

    private Map<String, Double> parseItemScore(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private boolean sameItems(String json) {
        Map<String, Double> map = parseItemScore(json);
        if (map.size() < 2) {
            return false;
        }
        return map.values().stream().distinct().count() == 1;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String anonymize(String userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(userId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("U");
            for (int i = 0; i < 5; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.substring(0, 10);
        } catch (Exception e) {
            return "U000000000";
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
