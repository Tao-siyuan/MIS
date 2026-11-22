package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.Course;
import com.ustb.mis.entity.CreditApply;
import com.ustb.mis.entity.CreditItem;
import com.ustb.mis.mapper.CourseMapper;
import com.ustb.mis.mapper.CreditApplyMapper;
import com.ustb.mis.mapper.CreditItemMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.AuditService;
import com.ustb.mis.service.CodeService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转专业学分转换：申请、明细认定与审核（对应 3.5.6）。
 */
@RestController
@RequestMapping("/api/credit-transfers")
@RequirePerm("P14")
public class CreditController {

    private final CreditApplyMapper applyMapper;
    private final CreditItemMapper itemMapper;
    private final CourseMapper courseMapper;
    private final ViewMapper viewMapper;
    private final CodeService codeService;
    private final AuditService auditService;

    public CreditController(CreditApplyMapper applyMapper, CreditItemMapper itemMapper,
                            CourseMapper courseMapper, ViewMapper viewMapper,
                            CodeService codeService, AuditService auditService) {
        this.applyMapper = applyMapper;
        this.itemMapper = itemMapper;
        this.courseMapper = courseMapper;
        this.viewMapper = viewMapper;
        this.codeService = codeService;
        this.auditService = auditService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String auditStatus,
                                                  @RequestParam(required = false) String stuId) {
        LoginUser user = UserContext.require();
        String queryStu = user.getClassId() != null ? user.getUserId() : stuId;
        return Result.ok(viewMapper.listCreditApplies(queryStu, auditStatus));
    }

    @GetMapping("/{id}/items")
    public Result<List<Map<String, Object>>> items(@PathVariable String id) {
        return Result.ok(viewMapper.listCreditItems(id));
    }

    @PostMapping
    @Transactional
    public Result<Void> apply(@RequestBody Map<String, Object> body) {
        LoginUser user = UserContext.require();
        String stuId = user.getClassId() != null ? user.getUserId() : str(body.get("stuId"));
        String fromMajor = str(body.get("fromMajor"));
        String toMajor = str(body.get("toMajor"));
        if (fromMajor == null || toMajor == null) {
            throw new BizException("原专业与转入专业不能为空");
        }
        if (fromMajor.equals(toMajor)) {
            throw new BizException("原专业与转入专业不能相同");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BizException("请至少录入一条转换明细");
        }
        CreditApply apply = new CreditApply();
        apply.setApplyId(codeService.nextBillId("CT", "credit_apply", "apply_id"));
        apply.setStuId(stuId);
        apply.setFromMajor(fromMajor);
        apply.setToMajor(toMajor);
        apply.setApplyDate(LocalDate.now());
        apply.setAuditStatus("0");
        BigDecimal sum = BigDecimal.ZERO;
        int seq = 0;
        applyMapper.insert(apply);
        for (Map<String, Object> item : items) {
            seq++;
            CreditItem ci = new CreditItem();
            ci.setApplyId(apply.getApplyId());
            ci.setSeqNo(seq);
            ci.setSourceCourseId(str(item.get("sourceCourseId")));
            Course source = courseMapper.selectById(ci.getSourceCourseId());
            if (source == null) {
                throw new BizException("原修课程不存在：" + ci.getSourceCourseId());
            }
            ci.setSourceCredit(source.getCredit());
            ci.setTargetCourseId(str(item.get("targetCourseId")));
            BigDecimal targetCredit = item.get("targetCredit") == null
                    ? source.getCredit() : new BigDecimal(str(item.get("targetCredit")));
            if (targetCredit.compareTo(source.getCredit()) > 0) {
                throw new BizException("认定学分不得大于原课程学分（" + source.getCourseName() + "）");
            }
            ci.setTargetCredit(targetCredit);
            ci.setTransferType(str(item.get("transferType")) == null ? "01" : str(item.get("transferType")));
            ci.setItemStatus("0");
            itemMapper.insert(ci);
            sum = sum.add(targetCredit);
        }
        CreditApply update = new CreditApply();
        update.setApplyId(apply.getApplyId());
        update.setApplyCredit(sum);
        applyMapper.updateById(update);
        auditService.record("01", "credit_apply:" + apply.getApplyId(), null, apply);
        return Result.ok();
    }

    @PutMapping("/{applyId}/items/{seqNo}")
    @Transactional
    public Result<Void> updateItem(@PathVariable String applyId, @PathVariable Integer seqNo,
                                   @RequestBody Map<String, Object> body) {
        CreditItem item = itemMapper.selectOne(Wrappers.<CreditItem>lambdaQuery()
                .eq(CreditItem::getApplyId, applyId).eq(CreditItem::getSeqNo, seqNo));
        if (item == null) {
            throw new BizException("明细不存在");
        }
        CreditItem update = new CreditItem();
        update.setApplyId(applyId);
        update.setSeqNo(seqNo);
        if (body.get("targetCredit") != null) {
            BigDecimal credit = new BigDecimal(str(body.get("targetCredit")));
            if (credit.compareTo(item.getSourceCredit()) > 0) {
                throw new BizException("认定学分不得大于原课程学分");
            }
            update.setTargetCredit(credit);
        }
        if (body.get("targetCourseId") != null) {
            update.setTargetCourseId(str(body.get("targetCourseId")));
        }
        if (body.get("transferType") != null) {
            update.setTransferType(str(body.get("transferType")));
        }
        if (body.get("itemStatus") != null) {
            update.setItemStatus(str(body.get("itemStatus")));
        }
        itemMapper.update(update, Wrappers.<CreditItem>lambdaUpdate()
                .eq(CreditItem::getApplyId, applyId).eq(CreditItem::getSeqNo, seqNo));
        auditService.record("02", "credit_item:" + applyId + "-" + seqNo, item, update);
        return Result.ok();
    }

    @PostMapping("/{id}/audit")
    @Transactional
    public Result<Void> audit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CreditApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("申请不存在");
        }
        CreditApply update = new CreditApply();
        update.setApplyId(id);
        update.setApprover(UserContext.require().getUserId());
        update.setAuditOpinion(str(body.get("opinion")));
        update.setAuditDate(LocalDate.now());
        if (body.get("collegeOpinion") != null) {
            update.setCollegeOpinion(str(body.get("collegeOpinion")));
        }
        if ("1".equals(str(body.get("pass")))) {
            update.setAuditStatus("1");
            itemMapper.update(null, Wrappers.<CreditItem>lambdaUpdate()
                    .eq(CreditItem::getApplyId, id)
                    .ne(CreditItem::getItemStatus, "2")
                    .set(CreditItem::getItemStatus, "1"));
        } else {
            if (update.getAuditOpinion() == null || update.getAuditOpinion().isBlank()) {
                throw new BizException("退回时审核意见必填");
            }
            update.setAuditStatus("2");
        }
        applyMapper.updateById(update);
        auditService.record("04", "credit_apply:" + id, Map.of("auditStatus", apply.getAuditStatus()),
                Map.of("auditStatus", update.getAuditStatus()));
        return Result.ok();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
