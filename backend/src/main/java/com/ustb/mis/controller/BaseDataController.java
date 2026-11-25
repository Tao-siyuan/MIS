package com.ustb.mis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ustb.mis.common.BizException;
import com.ustb.mis.common.Result;
import com.ustb.mis.entity.ClassInfo;
import com.ustb.mis.entity.College;
import com.ustb.mis.entity.Major;
import com.ustb.mis.entity.MajorProgram;
import com.ustb.mis.entity.SysUser;
import com.ustb.mis.entity.Teacher;
import com.ustb.mis.mapper.ClassInfoMapper;
import com.ustb.mis.mapper.CollegeMapper;
import com.ustb.mis.mapper.MajorMapper;
import com.ustb.mis.mapper.MajorProgramMapper;
import com.ustb.mis.mapper.SysUserMapper;
import com.ustb.mis.mapper.TeacherMapper;
import com.ustb.mis.mapper.ViewMapper;
import com.ustb.mis.security.LoginUser;
import com.ustb.mis.security.RequirePerm;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.AuditService;
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
 * 基础档案维护：学院、专业、班级（镜像）、教师（镜像）。
 */
@RestController
@RequestMapping("/api")
@RequirePerm("P01")
public class BaseDataController {

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClassInfoMapper classInfoMapper;
    private final TeacherMapper teacherMapper;
    private final SysUserMapper sysUserMapper;
    private final MajorProgramMapper programMapper;
    private final ViewMapper viewMapper;
    private final AuditService auditService;

    public BaseDataController(CollegeMapper collegeMapper, MajorMapper majorMapper,
                              ClassInfoMapper classInfoMapper, TeacherMapper teacherMapper,
                              SysUserMapper sysUserMapper, MajorProgramMapper programMapper,
                              ViewMapper viewMapper, AuditService auditService) {
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.classInfoMapper = classInfoMapper;
        this.teacherMapper = teacherMapper;
        this.sysUserMapper = sysUserMapper;
        this.programMapper = programMapper;
        this.viewMapper = viewMapper;
        this.auditService = auditService;
    }

    // ---------------- 学院 ----------------

    @GetMapping("/colleges")
    @RequirePerm({"P01", "P02", "P03", "P04", "P05", "P07", "P08", "P09", "P13", "P14", "P16"})
    public Result<List<College>> colleges(@RequestParam(required = false) String status) {
        return Result.ok(collegeMapper.selectList(Wrappers.<College>lambdaQuery()
                .eq(status != null && !status.isBlank(), College::getCollegeStatus, status)
                .orderByAsc(College::getCollegeId)));
    }

    @PostMapping("/colleges")
    @RequirePerm("P01")
    public Result<Void> addCollege(@RequestBody College college) {
        if (college.getCollegeId() == null || !college.getCollegeId().matches("\\d{2}")) {
            throw new BizException("学院编号须为 2 位数字");
        }
        if (collegeMapper.selectById(college.getCollegeId()) != null) {
            throw new BizException("学院编号重复，请重新输入");
        }
        college.setCollegeStatus("1");
        collegeMapper.insert(college);
        auditService.record("01", "college:" + college.getCollegeId(), null, college);
        return Result.ok();
    }

    @PutMapping("/colleges/{id}")
    @RequirePerm("P01")
    public Result<Void> updateCollege(@PathVariable String id, @RequestBody College college) {
        College old = collegeMapper.selectById(id);
        if (old == null) {
            throw new BizException("学院不存在");
        }
        college.setCollegeId(id);
        collegeMapper.updateById(college);
        auditService.record("02", "college:" + id, old, college);
        return Result.ok();
    }

    @PutMapping("/colleges/{id}/status")
    @RequirePerm("P01")
    public Result<Void> collegeStatus(@PathVariable String id, @RequestParam String status) {
        College old = collegeMapper.selectById(id);
        if (old == null) {
            throw new BizException("学院不存在");
        }
        if ("0".equals(status)) {
            long active = majorMapper.selectCount(Wrappers.<Major>lambdaQuery()
                    .eq(Major::getCollegeId, id).eq(Major::getMajorStatus, "1"));
            if (active > 0) {
                throw new BizException("该学院下存在在招专业，禁止停用");
            }
        }
        College update = new College();
        update.setCollegeId(id);
        update.setCollegeStatus(status);
        collegeMapper.updateById(update);
        auditService.record("02", "college:" + id, Map.of("collegeStatus", old.getCollegeStatus()),
                Map.of("collegeStatus", status));
        return Result.ok();
    }

    // ---------------- 专业 ----------------

    @GetMapping("/majors")
    @RequirePerm({"P01", "P02", "P03", "P05", "P08", "P13", "P14", "P15"})
    public Result<List<Map<String, Object>>> majors(@RequestParam(required = false) String collegeId,
                                                    @RequestParam(required = false) String keyword) {
        return Result.ok(viewMapper.listMajors(collegeId, keyword));
    }

    /** 全量专业浏览（P25）：不做学院过滤，供教师/学生/学院教务只读查看 */
    @GetMapping("/catalog/majors")
    @RequirePerm("P25")
    public Result<List<Map<String, Object>>> catalogMajors(@RequestParam(required = false) String keyword) {
        return Result.ok(viewMapper.listMajors(null, keyword));
    }

    @PostMapping("/majors")
    @RequirePerm({"P01", "P02"})
    public Result<Void> addMajor(@RequestBody Major major) {
        if (major.getMajorId() == null || !major.getMajorId().matches("\\d{6}T?")) {
            throw new BizException("专业编号须为 6 位数字（特设专业可带 T）");
        }
        if (majorMapper.selectById(major.getMajorId()) != null) {
            throw new BizException("专业编号已存在");
        }
        if (collegeMapper.selectById(major.getCollegeId()) == null) {
            throw new BizException("所属学院不存在");
        }
        requireMajorEditable(major.getCollegeId());
        major.setMajorStatus("1");
        majorMapper.insert(major);
        auditService.record("01", "major:" + major.getMajorId(), null, major);
        return Result.ok();
    }

    @PutMapping("/majors/{id}")
    @RequirePerm({"P01", "P02"})
    public Result<Void> updateMajor(@PathVariable String id, @RequestBody Major major) {
        Major old = majorMapper.selectById(id);
        if (old == null) {
            throw new BizException("专业不存在");
        }
        requireMajorEditable(old.getCollegeId());
        requireMajorEditable(major.getCollegeId());
        major.setMajorId(id);
        majorMapper.updateById(major);
        auditService.record("02", "major:" + id, old, major);
        return Result.ok();
    }

    @PutMapping("/majors/{id}/status")
    @RequirePerm({"P01", "P02"})
    public Result<Void> majorStatus(@PathVariable String id, @RequestParam String status) {
        Major old = majorMapper.selectById(id);
        if (old == null) {
            throw new BizException("专业不存在");
        }
        requireMajorEditable(old.getCollegeId());
        if ("0".equals(status)) {
            long active = programMapper.selectCount(Wrappers.<MajorProgram>lambdaQuery()
                    .eq(MajorProgram::getMajorId, id).eq(MajorProgram::getPlanStatus, "3"));
            if (active > 0) {
                throw new BizException("该专业存在在用培养方案，需先停用方案");
            }
        }
        Major update = new Major();
        update.setMajorId(id);
        update.setMajorStatus(status);
        majorMapper.updateById(update);
        auditService.record("02", "major:" + id, Map.of("majorStatus", old.getMajorStatus()),
                Map.of("majorStatus", status));
        return Result.ok();
    }

    @DeleteMapping("/majors/{id}")
    @RequirePerm({"P01", "P02"})
    public Result<Void> deleteMajor(@PathVariable String id) {
        Major major = majorMapper.selectById(id);
        if (major == null) {
            throw new BizException("专业不存在");
        }
        requireMajorEditable(major.getCollegeId());
        if (programMapper.selectCount(Wrappers.<MajorProgram>lambdaQuery()
                .eq(MajorProgram::getMajorId, id)) > 0) {
            throw new BizException("该专业已被培养方案引用，禁止删除，请使用停招");
        }
        if (classInfoMapper.selectCount(Wrappers.<ClassInfo>lambdaQuery()
                .eq(ClassInfo::getMajorId, id)) > 0) {
            throw new BizException("该专业下仍有班级，禁止删除");
        }
        majorMapper.deleteById(id);
        auditService.record("03", "major:" + id, Map.of("majorId", id), null);
        return Result.ok();
    }

    // ---------------- 班级 / 教师（外部系统镜像，只读） ----------------

    @GetMapping("/classes")
    @RequirePerm({"P01", "P02", "P08", "P09", "P12"})
    public Result<List<ClassInfo>> classes(@RequestParam(required = false) String majorId,
                                          @RequestParam(required = false) String grade) {
        LoginUser user = UserContext.require();
        List<String> ownMajorIds = null;
        if ("2".equals(user.getDataScope()) && user.getCollegeId() != null) {
            // 学院教务只能取到本学院专业下的班级
            ownMajorIds = majorMapper.selectList(Wrappers.<Major>lambdaQuery()
                            .eq(Major::getCollegeId, user.getCollegeId()))
                    .stream().map(Major::getMajorId).toList();
            if (ownMajorIds.isEmpty()) {
                return Result.ok(List.of());
            }
        }
        return Result.ok(classInfoMapper.selectList(Wrappers.<ClassInfo>lambdaQuery()
                .eq(majorId != null && !majorId.isBlank(), ClassInfo::getMajorId, majorId)
                .in(ownMajorIds != null, ClassInfo::getMajorId, ownMajorIds)
                .eq(grade != null && !grade.isBlank(), ClassInfo::getGrade, grade)
                .orderByAsc(ClassInfo::getClassId)));
    }

    @GetMapping("/teachers")
    @RequirePerm({"P01", "P02", "P04", "P05", "P07", "P08", "P09", "P12", "P15"})
    public Result<List<Teacher>> teachers(@RequestParam(required = false) String collegeId) {
        LoginUser user = UserContext.require();
        String scopeCollege = collegeId;
        if ("2".equals(user.getDataScope()) && user.getCollegeId() != null) {
            // 学院教务只能取到本学院教师
            scopeCollege = user.getCollegeId();
        }
        List<Teacher> list = teacherMapper.selectList(Wrappers.<Teacher>lambdaQuery()
                .eq(scopeCollege != null && !scopeCollege.isBlank(), Teacher::getCollegeId, scopeCollege)
                .eq(Teacher::getTeacherStatus, "1")
                .orderByAsc(Teacher::getTeacherId));
        // 师资子系统编号不外显，统一以登录工号（sys_user.user_id）展示
        Map<String, String> userIds = new java.util.HashMap<>();
        for (SysUser u : sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .isNotNull(SysUser::getTeacherId))) {
            if (u.getTeacherId() != null) {
                userIds.putIfAbsent(u.getTeacherId(), u.getUserId());
            }
        }
        list.forEach(t -> t.setUserId(userIds.get(t.getTeacherId())));
        return Result.ok(list);
    }

    /** 学院教务员仅可编辑本学院专业；校级账号不限制 */
    private void requireMajorEditable(String collegeId) {
        LoginUser user = UserContext.require();
        if ("2".equals(user.getDataScope())
                && !user.getCollegeId().equals(collegeId)) {
            throw new BizException("学院教务员仅可编辑本学院专业");
        }
    }
}
