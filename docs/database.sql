-- =====================================================================
-- 专业设置及排课管理系统（MIS）数据库脚本
-- 依据《系统分析、设计、规划报告》2.2 数据字典与 3.4 数据库设计编制
-- DBMS：MySQL 8.0   引擎：InnoDB   字符集：utf8mb4
-- 执行方式：mysql -uroot -p < docs/database.sql
-- =====================================================================

DROP DATABASE IF EXISTS mis;
CREATE DATABASE mis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mis;

-- ---------------------------------------------------------------------
-- 一、基础档案（T01～T03）
-- 学院、专业、课程为全系统共用主数据；班级、教师、教室由外部子系统维护，
-- 本库保存只读镜像表，供排课与课表展示使用（一数一源）。
-- ---------------------------------------------------------------------

CREATE TABLE college (
    college_id     VARCHAR(2)  NOT NULL COMMENT '学院编号',
    college_name   VARCHAR(20) NOT NULL COMMENT '学院名称',
    college_short  VARCHAR(10)          COMMENT '学院简称，用于课表与教室显示',
    college_head   VARCHAR(20)          COMMENT '分管教学负责人',
    contact_phone  VARCHAR(20)          COMMENT '联系电话',
    college_status VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '0停用/1启用',
    PRIMARY KEY (college_id),
    UNIQUE KEY uk_college_name (college_name)
) ENGINE=InnoDB COMMENT='学院表';

CREATE TABLE major (
    major_id     VARCHAR(8)   NOT NULL COMMENT '专业编号（教育部专业代码）',
    major_name   VARCHAR(30)  NOT NULL COMMENT '专业名称',
    college_id   VARCHAR(2)   NOT NULL COMMENT '所属学院',
    duration     INT          NOT NULL DEFAULT 4 COMMENT '学制（年）',
    degree       VARCHAR(10)           COMMENT '授予学位',
    min_credit   DECIMAL(4,1) NOT NULL DEFAULT 160.0 COMMENT '毕业最低学分',
    major_status VARCHAR(1)   NOT NULL DEFAULT '1' COMMENT '0停招/1在招',
    PRIMARY KEY (major_id),
    KEY idx_major_college (college_id),
    CONSTRAINT fk_major_college FOREIGN KEY (college_id) REFERENCES college (college_id)
) ENGINE=InnoDB COMMENT='专业表';

CREATE TABLE course (
    course_id     VARCHAR(8)   NOT NULL COMMENT '课程号（学院2位+流水5位）',
    course_name   VARCHAR(40)  NOT NULL COMMENT '课程名称',
    course_type   VARCHAR(2)   NOT NULL COMMENT '11校级必修/12院级必修/13专业必修/14专业选修/15通识/16实践/17素质拓展',
    credit        DECIMAL(3,1) NOT NULL COMMENT '学分',
    total_hours   INT          NOT NULL COMMENT '总学时',
    theory_hours  INT          NOT NULL DEFAULT 0 COMMENT '理论学时（按2学时递增）',
    practice_hours INT         NOT NULL DEFAULT 0 COMMENT '实践学时（0或不少于8且按4学时递增）',
    practice_room_type VARCHAR(20)       COMMENT '实践教室类型：41机房/42物理实验室/43化学实验室/44生物实验室/45操场/46金工实习场地/47其他',
    assess_type   VARCHAR(1)   NOT NULL DEFAULT '1' COMMENT '1考试/2考查',
    open_college  VARCHAR(2)   NOT NULL COMMENT '开课学院',
    pre_course_id VARCHAR(8)            COMMENT '先修课程',
    syllabus      VARCHAR(200)          COMMENT '教学大纲存放地址',
    course_status VARCHAR(1)   NOT NULL DEFAULT '1' COMMENT '0停用/1在库',
    PRIMARY KEY (course_id),
    UNIQUE KEY uk_course_name_college (course_name, open_college),
    KEY idx_course_college (open_college),
    CONSTRAINT fk_course_college FOREIGN KEY (open_college) REFERENCES college (college_id),
    CONSTRAINT fk_course_pre FOREIGN KEY (pre_course_id) REFERENCES course (course_id)
) ENGINE=InnoDB COMMENT='课程表';

CREATE TABLE class_info (
    class_id    VARCHAR(10) NOT NULL COMMENT '班级编号',
    class_name  VARCHAR(20) NOT NULL COMMENT '班级名称',
    major_id    VARCHAR(8)  NOT NULL COMMENT '所属专业',
    grade       VARCHAR(4)  NOT NULL COMMENT '年级',
    student_num INT         NOT NULL DEFAULT 0 COMMENT '在册人数',
    PRIMARY KEY (class_id),
    KEY idx_class_major (major_id),
    CONSTRAINT fk_class_major FOREIGN KEY (major_id) REFERENCES major (major_id)
) ENGINE=InnoDB COMMENT='班级镜像表（来源：学籍管理子系统）';

CREATE TABLE teacher (
    teacher_id   VARCHAR(8)  NOT NULL COMMENT '教师编号',
    teacher_name VARCHAR(20) NOT NULL COMMENT '教师姓名',
    college_id   VARCHAR(2)  NOT NULL COMMENT '所属学院',
    title        VARCHAR(10)          COMMENT '职称',
    teacher_status VARCHAR(1) NOT NULL DEFAULT '1' COMMENT '0停用/1在职',
    PRIMARY KEY (teacher_id),
    KEY idx_teacher_college (college_id),
    CONSTRAINT fk_teacher_college FOREIGN KEY (college_id) REFERENCES college (college_id)
) ENGINE=InnoDB COMMENT='教师镜像表（来源：师资管理子系统）';

-- ---------------------------------------------------------------------
-- 二、培养方案（T04～T06）
-- ---------------------------------------------------------------------

CREATE TABLE major_program (
    major_id     VARCHAR(8)   NOT NULL COMMENT '专业编号',
    grade        VARCHAR(4)   NOT NULL COMMENT '适用年级',
    program_type VARCHAR(1)   NOT NULL COMMENT '1主修/2双学位',
    total_credit DECIMAL(4,1) NOT NULL COMMENT '毕业总学分（取自培养方案）',
    total_hours  INT                   COMMENT '总学时（取自培养方案）',
    goal_desc    TEXT                  COMMENT '培养目标（取自培养方案文本，长文本）',
    version      VARCHAR(10)  NOT NULL DEFAULT 'V1.0' COMMENT '版本号',
    plan_status  VARCHAR(1)   NOT NULL DEFAULT '0' COMMENT '0编制/1初审/2审议/3已发布',
    dual_quota   INT                   COMMENT '双学位名额（仅双学位方案）',
    review_opinion VARCHAR(200)        COMMENT '审核/审议意见',
    publish_date DATE                  COMMENT '发布日期',
    require_desc TEXT                  COMMENT '选课要求（取自培养方案文本）',
    graduate_desc TEXT                 COMMENT '毕业要求（取自培养方案文本）',
    plan_desc    TEXT                  COMMENT '方案说明（取自培养方案文本）',
    source_plan_no VARCHAR(32)         COMMENT '来源培养方案计划编号（仅留痕）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (major_id, grade, program_type),
    CONSTRAINT fk_program_major FOREIGN KEY (major_id) REFERENCES major (major_id)
) ENGINE=InnoDB COMMENT='专业培养方案表（含双学位）';

CREATE TABLE major_program_version (
    major_id       VARCHAR(8)   NOT NULL COMMENT '专业编号',
    grade          VARCHAR(4)   NOT NULL COMMENT '适用年级',
    program_type   VARCHAR(1)   NOT NULL COMMENT '1主修/2双学位',
    version        VARCHAR(10)  NOT NULL COMMENT '版本号',
    plan_status    VARCHAR(1)   NOT NULL COMMENT '归档时的方案状态',
    total_credit   DECIMAL(5,1)          COMMENT '毕业总学分',
    goal_desc      VARCHAR(500)          COMMENT '培养目标',
    dual_quota     INT                   COMMENT '双学位名额',
    review_opinion VARCHAR(200)          COMMENT '审核意见',
    publish_date   DATE                  COMMENT '发布时间',
    course_count   INT                   COMMENT '归档时课程门数',
    archive_time   DATETIME     NOT NULL COMMENT '归档时间',
    PRIMARY KEY (major_id, grade, program_type, version)
) ENGINE=InnoDB COMMENT='培养方案历史版本快照（只读留档）';

CREATE TABLE program_course (
    major_id      VARCHAR(8)   NOT NULL COMMENT '专业编号',
    grade         VARCHAR(4)   NOT NULL COMMENT '适用年级',
    program_type  VARCHAR(1)   NOT NULL COMMENT '1主修/2双学位',
    course_id     VARCHAR(8)   NOT NULL COMMENT '课程号',
    course_type   VARCHAR(2)   NOT NULL COMMENT '课程性质',
    credit        DECIMAL(3,1) NOT NULL COMMENT '方案学分（取自培养方案课程明细）',
    hours         INT          NOT NULL COMMENT '方案学时',
    course_attribute VARCHAR(4)         COMMENT '课程属性：必修/选修（取自培养方案课程明细）',
    assess_type   VARCHAR(4)            COMMENT '考核方式：1考试/2考查（由培养方案考核方式映射）',
    source_course_no VARCHAR(20)        COMMENT '来源培养方案课程编号（仅留痕，不用作本系统课程号）',
    open_semester VARCHAR(20)  NOT NULL COMMENT '开课学期（格式 YYYY-YYYY-N，如 2024-2025-1）',
    pre_course_id VARCHAR(8)            COMMENT '先修课程',
    cross_status  VARCHAR(1)   NOT NULL DEFAULT '2' COMMENT '0未确认/1已确认/2无需确认',
    PRIMARY KEY (major_id, grade, program_type, course_id),
    KEY idx_pc_course (course_id),
    CONSTRAINT fk_pc_program FOREIGN KEY (major_id, grade, program_type)
        REFERENCES major_program (major_id, grade, program_type) ON DELETE CASCADE,
    CONSTRAINT fk_pc_course FOREIGN KEY (course_id) REFERENCES course (course_id)
) ENGINE=InnoDB COMMENT='培养方案课程表（含双学位）';

CREATE TABLE general_plan (
    school_year       VARCHAR(9) NOT NULL COMMENT '学年',
    course_id         VARCHAR(8) NOT NULL COMMENT '课程号',
    suggest_hours     INT        NOT NULL COMMENT '建议学时',
    undertake_college VARCHAR(2) NOT NULL COMMENT '承接学院',
    teacher_id        VARCHAR(8)          COMMENT '拟任教师',
    confirm_status    VARCHAR(1) NOT NULL DEFAULT '0' COMMENT '0待确认/1已承接/2不承接',
    plan_status       VARCHAR(1) NOT NULL DEFAULT '0' COMMENT '0草案/1汇总调整/2已发布',
    PRIMARY KEY (school_year, course_id),
    KEY idx_gp_college (undertake_college),
    CONSTRAINT fk_gp_course FOREIGN KEY (course_id) REFERENCES course (course_id),
    CONSTRAINT fk_gp_college FOREIGN KEY (undertake_college) REFERENCES college (college_id)
) ENGINE=InnoDB COMMENT='通识课程计划表';

-- ---------------------------------------------------------------------
-- 三、课程与教学任务（T07～T08）
-- ---------------------------------------------------------------------

CREATE TABLE new_course_apply (
    apply_id       VARCHAR(10) NOT NULL COMMENT '申请编号（NC+年份+流水）',
    apply_user     VARCHAR(11) NOT NULL COMMENT '申请人工号（教师/学院教务/教务处）',
    course_name    VARCHAR(40) NOT NULL COMMENT '拟开课程名称',
    course_type    VARCHAR(2)  NOT NULL DEFAULT '14' COMMENT '拟设课程性质：教师限17/学院教务限13,14/教务处限15',
    dispatch_college VARCHAR(2)         COMMENT '下发学院：教务处申报通识课程后下发至该学院补充',
    apply_reason   VARCHAR(200)         COMMENT '开设理由',
    credit         DECIMAL(3,1) NOT NULL COMMENT '拟定学分',
    hours          INT          NOT NULL DEFAULT 32 COMMENT '拟定学时',
    college_opinion VARCHAR(100)        COMMENT '学院审核意见',
    audit_status   VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0学院待审/3教务处待审/4待学院补充/1批准/2驳回',
    course_id      VARCHAR(8)           COMMENT '生成课程号',
    audit_opinion  VARCHAR(200)         COMMENT '审核意见',
    apply_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    audit_time     DATETIME             COMMENT '审核时间',
    PRIMARY KEY (apply_id),
    KEY idx_nca_user (apply_user),
    KEY idx_nca_college (dispatch_college)
) ENGINE=InnoDB COMMENT='新增课程申请表';

CREATE TABLE teaching_class (
    tc_id            VARCHAR(20) NOT NULL COMMENT '教学班编号（学年4+学期1+流水4）',
    term             VARCHAR(11) NOT NULL COMMENT '学期 YYYY-YYYY-S',
    course_id        VARCHAR(8)  NOT NULL COMMENT '课程号',
    teacher_id       VARCHAR(8)           COMMENT '任课教师',
    class_ids        VARCHAR(100)         COMMENT '面向班级，逗号分隔',
    student_num      INT         NOT NULL DEFAULT 0 COMMENT '面向人数',
    capacity         INT         NOT NULL DEFAULT 60 COMMENT '容量上限',
    tc_type          VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1必修/2选修/3双学位或辅修',
    teach_type       VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1讲授/2实验/3上机',
    hours            INT         NOT NULL DEFAULT 48 COMMENT '计划学时',
    theory_hours     INT         NOT NULL DEFAULT 0 COMMENT '理论学时（匹配普通/多媒体教室）',
    practice_hours   INT         NOT NULL DEFAULT 0 COMMENT '实践学时（匹配实验室/机房）',
    week_count       INT         NOT NULL DEFAULT 16 COMMENT '上课周数：16 周或 8 周',
    week_start       INT         NOT NULL DEFAULT 1 COMMENT '起始周：1=从第1周起（16周/前8周）；9=后8周',
    prefer_slot      VARCHAR(20)          COMMENT '偏好节次',
    unavailable_time VARCHAR(40)          COMMENT '不可用时间',
    multiclass_prefer VARCHAR(1) NOT NULL DEFAULT '1' COMMENT '连排偏好 0否/1是',
    tc_status        VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0待确认/1确认开课/2不开课/3已排课/4已发布',
    source           VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1方案生成/2新增课程/3选修上报',
    actual_num       INT                  COMMENT '实际选课人数',
    open_lower       INT         NOT NULL DEFAULT 10 COMMENT '开课下限',
    no_open_reason   VARCHAR(100)         COMMENT '停开原因',
    PRIMARY KEY (tc_id),
    KEY idx_tc_term (term),
    KEY idx_tc_course (course_id),
    KEY idx_tc_teacher (teacher_id),
    CONSTRAINT fk_tc_course FOREIGN KEY (course_id) REFERENCES course (course_id),
    CONSTRAINT fk_tc_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (teacher_id)
) ENGINE=InnoDB COMMENT='教学班表';

-- 教学班学生名单：任课教师可在本人教学班内添加/移除学生（重修、跨专业选课等）
CREATE TABLE tc_student (
    tc_id    VARCHAR(20) NOT NULL COMMENT '教学班编号',
    stu_id   VARCHAR(11) NOT NULL COMMENT '学号',
    add_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (tc_id, stu_id),
    KEY idx_tcs_stu (stu_id),
    KEY idx_tcs_tc (tc_id),
    CONSTRAINT fk_tcs_tc FOREIGN KEY (tc_id) REFERENCES teaching_class (tc_id)
) ENGINE=InnoDB COMMENT='教学班学生名单';

-- ---------------------------------------------------------------------
-- 四、排课（T09～T13）
-- ---------------------------------------------------------------------

CREATE TABLE classroom (
    room_id        VARCHAR(6)  NOT NULL COMMENT '教室编号',
    room_name      VARCHAR(20) NOT NULL COMMENT '教室名称',
    building       VARCHAR(20)          COMMENT '楼宇',
    college_id     VARCHAR(2)           COMMENT '管理学院编号：学院专属教室填学院号（如计算机楼属计通学院09），公共教学楼为空由教务处统一调配',
    capacity       INT         NOT NULL COMMENT '容量',
    room_type      VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1普通/2多媒体/3机房/4实验室',
    available_time VARCHAR(100)         COMMENT '可用时段',
    room_status    VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '0维修停用/1可用',
    PRIMARY KEY (room_id)
) ENGINE=InnoDB COMMENT='教室表（来源：固定资产管理子系统）';

CREATE TABLE schedule_rule (
    rule_id      VARCHAR(4)   NOT NULL COMMENT '规则编号 R+2位流水',
    rule_name    VARCHAR(30)  NOT NULL COMMENT '规则名称',
    rule_type    VARCHAR(2)   NOT NULL COMMENT '01优先级/02教室匹配/03时间约束/04冲突检测',
    rule_content VARCHAR(200) NOT NULL COMMENT '规则内容',
    priority_no  INT          NOT NULL DEFAULT 1 COMMENT '生效次序，数值小者先执行',
    enable       VARCHAR(1)   NOT NULL DEFAULT '1' COMMENT '0停用/1启用',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id)
) ENGINE=InnoDB COMMENT='排课规则表';

CREATE TABLE schedule_result (
    sched_id     VARCHAR(10) NOT NULL COMMENT '排课编号 S+年份+流水',
    tc_id        VARCHAR(20) NOT NULL COMMENT '教学班编号',
    room_id      VARCHAR(6)           COMMENT '教室编号（体育课等操场课程为空）',
    week_range   VARCHAR(10) NOT NULL DEFAULT '1-16' COMMENT '周次范围 a-b',
    week_day     VARCHAR(1)  NOT NULL COMMENT '星期1～7',
    start_slot   INT         NOT NULL COMMENT '开始节次1～12',
    end_slot     INT         NOT NULL COMMENT '结束节次',
    sched_status VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0待审核/1已审核/2已发布/3已调课',
    lock_flag    VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0可调整/1锁定',
    conflict_desc VARCHAR(100)         COMMENT '冲突说明',
    PRIMARY KEY (sched_id),
    KEY idx_sr_tc (tc_id),
    KEY idx_sr_room (room_id),
    KEY idx_sr_time (week_day, start_slot, end_slot),
    CONSTRAINT fk_sr_tc FOREIGN KEY (tc_id) REFERENCES teaching_class (tc_id) ON DELETE CASCADE,
    CONSTRAINT fk_sr_room FOREIGN KEY (room_id) REFERENCES classroom (room_id)
) ENGINE=InnoDB COMMENT='排课结果表';

CREATE TABLE schedule_conflict (
    conflict_id   VARCHAR(10) NOT NULL COMMENT '冲突编号',
    tc_id         VARCHAR(20) NOT NULL COMMENT '教学班编号',
    other_tc_id   VARCHAR(20)          COMMENT '对方教学班',
    conflict_type VARCHAR(2)  NOT NULL COMMENT '01教师冲突/02教室冲突/03班级冲突/04教室容量/05教室类型/06时段不可用',
    conflict_desc VARCHAR(150)         COMMENT '冲突描述',
    suggest_plan  VARCHAR(150)         COMMENT '建议方案',
    handle_status VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0未处理/1已处理',
    found_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conflict_id),
    KEY idx_sc_tc (tc_id),
    CONSTRAINT fk_sc_tc FOREIGN KEY (tc_id) REFERENCES teaching_class (tc_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='排课冲突记录表';

CREATE TABLE adjust_record (
    adjust_id      VARCHAR(10) NOT NULL COMMENT '调课编号 AD+年份+流水',
    sched_id       VARCHAR(10) NOT NULL COMMENT '原排课编号',
    adjust_type    VARCHAR(1)  NOT NULL COMMENT '1一次性/2长期',
    old_value      VARCHAR(100)         COMMENT '原安排快照',
    new_value      VARCHAR(100)         COMMENT '新安排',
    old_room_id    VARCHAR(6)           COMMENT '原教室',
    old_week_day   VARCHAR(1)           COMMENT '原星期',
    old_start_slot INT                  COMMENT '原开始节次',
    old_end_slot   INT                  COMMENT '原结束节次',
    old_week_range VARCHAR(10)          COMMENT '原周次范围',
    new_room_id    VARCHAR(6)           COMMENT '新教室',
    new_week_day   VARCHAR(1)           COMMENT '新星期',
    new_start_slot INT                  COMMENT '新开始节次',
    new_end_slot   INT                  COMMENT '新结束节次',
    reason         VARCHAR(150)         COMMENT '调课原因',
    effect_date    DATE                 COMMENT '生效日期',
    approve_status VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0学院待审/4待教务处备案/1已通过(备案生效)/2驳回/3申请人撤回',
    approver       VARCHAR(11)          COMMENT '审批人',
    approve_opinion VARCHAR(150)        COMMENT '审批意见',
    restore_flag   VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0未恢复/1已恢复',
    notify_status  VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0未通知/1已通知',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (adjust_id),
    KEY idx_ar_sched (sched_id),
    CONSTRAINT fk_ar_sched FOREIGN KEY (sched_id) REFERENCES schedule_result (sched_id)
) ENGINE=InnoDB COMMENT='调课记录表';

-- ---------------------------------------------------------------------
-- 五、延伸业务（T14～T16）
-- ---------------------------------------------------------------------

CREATE TABLE dual_enroll (
    enroll_id     VARCHAR(10) NOT NULL COMMENT '报名编号 DE+年份+流水',
    stu_id        VARCHAR(10) NOT NULL COMMENT '学号',
    major_id      VARCHAR(8)  NOT NULL COMMENT '双学位专业',
    enroll_year   VARCHAR(9)  NOT NULL COMMENT '学年',
    apply_date    DATE        NOT NULL COMMENT '报名日期',
    score_avg     DECIMAL(4,1)         COMMENT '主修平均分',
    audit_status  VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0待审核/1已录取/2未录取',
    finish_status VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0未开始/1修读中/2已退修/3已结业',
    enroll_date   DATE                 COMMENT '录取日期',
    remark        VARCHAR(100)         COMMENT '备注',
    PRIMARY KEY (enroll_id),
    KEY idx_de_major (major_id),
    CONSTRAINT fk_de_major FOREIGN KEY (major_id) REFERENCES major (major_id)
) ENGINE=InnoDB COMMENT='双学位报名表';

CREATE TABLE study_record (
    rec_id          VARCHAR(10) NOT NULL COMMENT '记录编号 SR+年份+流水',
    stu_id          VARCHAR(10) NOT NULL COMMENT '学号',
    program_type    VARCHAR(1)  NOT NULL COMMENT '1辅修/2双学位',
    major_id        VARCHAR(8)  NOT NULL COMMENT '修读专业',
    course_id       VARCHAR(8)  NOT NULL COMMENT '课程号',
    tc_id           VARCHAR(20)          COMMENT '教学班编号（随班修读/独立开班）',
    study_type      VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1随班修读/2单独开班/3免修认定',
    source_course_id VARCHAR(8)          COMMENT '免修原课程',
    credit          DECIMAL(3,1)         COMMENT '认定学分',
    conflict_type   VARCHAR(2)           COMMENT '01允许时段重合/02顺延下一年/03无冲突',
    audit_status    VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0待认定/1已认定/2不认定',
    remark          VARCHAR(150)         COMMENT '冲突处理说明',
    PRIMARY KEY (rec_id),
    KEY idx_sr2_stu (stu_id),
    CONSTRAINT fk_sr2_major FOREIGN KEY (major_id) REFERENCES major (major_id),
    CONSTRAINT fk_sr2_course FOREIGN KEY (course_id) REFERENCES course (course_id)
) ENGINE=InnoDB COMMENT='修读记录表（辅修/双学位共用，以修读类型区分）';

CREATE TABLE credit_apply (
    apply_id      VARCHAR(10) NOT NULL COMMENT '申请编号 CT+年份+流水',
    stu_id        VARCHAR(10) NOT NULL COMMENT '学号',
    from_major    VARCHAR(8)  NOT NULL COMMENT '原专业',
    to_major      VARCHAR(8)  NOT NULL COMMENT '转入专业',
    apply_date    DATE        NOT NULL COMMENT '申请日期',
    apply_credit  DECIMAL(4,1)         COMMENT '申请转换学分（明细汇总回写）',
    college_opinion VARCHAR(150)       COMMENT '学院审核意见',
    audit_status  VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0待审核/1通过/2驳回',
    approver      VARCHAR(11)          COMMENT '审核人',
    audit_opinion VARCHAR(150)         COMMENT '审核意见',
    audit_date    DATE                 COMMENT '审核日期',
    PRIMARY KEY (apply_id),
    KEY idx_ca_stu (stu_id),
    CONSTRAINT fk_ca_from FOREIGN KEY (from_major) REFERENCES major (major_id),
    CONSTRAINT fk_ca_to FOREIGN KEY (to_major) REFERENCES major (major_id)
) ENGINE=InnoDB COMMENT='学分转换申请表';

CREATE TABLE credit_item (
    apply_id        VARCHAR(10) NOT NULL COMMENT '申请编号',
    seq_no          INT         NOT NULL COMMENT '明细序号',
    source_course_id VARCHAR(8) NOT NULL COMMENT '原修课程',
    source_credit   DECIMAL(3,1) NOT NULL COMMENT '原课程学分',
    target_course_id VARCHAR(8)          COMMENT '认定课程（可空）',
    target_credit   DECIMAL(3,1)         COMMENT '认定学分',
    transfer_type   VARCHAR(2)  NOT NULL DEFAULT '01' COMMENT '01同名课程/02相近课程降学分/03实践项目',
    item_status     VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0待认定/1同意/2不同意',
    PRIMARY KEY (apply_id, seq_no),
    KEY idx_ci_source (source_course_id),
    CONSTRAINT fk_ci_apply FOREIGN KEY (apply_id) REFERENCES credit_apply (apply_id) ON DELETE CASCADE,
    CONSTRAINT fk_ci_source FOREIGN KEY (source_course_id) REFERENCES course (course_id)
) ENGINE=InnoDB COMMENT='学分转换明细表';

-- ---------------------------------------------------------------------
-- 六、评价与监督（T17～T19）
-- ---------------------------------------------------------------------

CREATE TABLE eval_plan (
    plan_id        VARCHAR(8)  NOT NULL COMMENT '方案编号 EP+学年+学期',
    term           VARCHAR(11) NOT NULL COMMENT '适用学期',
    plan_name      VARCHAR(50) NOT NULL COMMENT '方案名称',
    indicator_json VARCHAR(500) NOT NULL COMMENT '评价指标（JSON）',
    open_date      DATE        NOT NULL COMMENT '开始日期',
    close_date     DATE        NOT NULL COMMENT '结束日期',
    anonymity      VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '0实名/1匿名',
    min_sample     INT         NOT NULL DEFAULT 10 COMMENT '有效样本下限',
    plan_status    VARCHAR(1)  NOT NULL DEFAULT '0' COMMENT '0草案/1已发布/2已结束/3结果已发布（教务处发布给教师）',
    PRIMARY KEY (plan_id)
) ENGINE=InnoDB COMMENT='课程评价方案表';

CREATE TABLE eval_record (
    record_id   VARCHAR(10) NOT NULL COMMENT '记录编号 ER+年份+流水',
    plan_id     VARCHAR(8)  NOT NULL COMMENT '方案编号',
    tc_id       VARCHAR(20) NOT NULL COMMENT '教学班编号',
    stu_id      VARCHAR(10) NOT NULL COMMENT '学号（匿名评教时脱敏存储）',
    score       DECIMAL(4,1) NOT NULL COMMENT '综合得分',
    item_score  VARCHAR(200)         COMMENT '分项得分（JSON）',
    comment     VARCHAR(500)         COMMENT '文字意见',
    valid_flag  VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '0无效/1有效',
    submit_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_er_once (plan_id, tc_id, stu_id),
    KEY idx_er_tc (tc_id),
    CONSTRAINT fk_er_plan FOREIGN KEY (plan_id) REFERENCES eval_plan (plan_id),
    CONSTRAINT fk_er_tc FOREIGN KEY (tc_id) REFERENCES teaching_class (tc_id)
) ENGINE=InnoDB COMMENT='课程评价记录表';

-- ---------------------------------------------------------------------
-- 七、支撑数据（T21～T22）
-- ---------------------------------------------------------------------

CREATE TABLE sys_role (
    role_id     VARCHAR(4)  NOT NULL COMMENT '角色编号 R+2位流水',
    role_name   VARCHAR(20) NOT NULL COMMENT '角色名称',
    data_scope  VARCHAR(1)  NOT NULL DEFAULT '3' COMMENT '1全校/2本学院/3本人',
    func_perm   VARCHAR(200)         COMMENT '功能权限码，逗号分隔',
    role_status VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '0停用/1启用',
    remark      VARCHAR(100)         COMMENT '适用岗位',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_name (role_name)
) ENGINE=InnoDB COMMENT='角色权限表';

CREATE TABLE sys_user (
    user_id    VARCHAR(20) NOT NULL COMMENT '用户编号（工号/学号）',
    user_name  VARCHAR(20) NOT NULL COMMENT '登录名',
    password   VARCHAR(64) NOT NULL COMMENT '口令摘要（SHA-256加盐）',
    real_name  VARCHAR(20) NOT NULL COMMENT '姓名',
    role_id    VARCHAR(4)  NOT NULL COMMENT '主角色',
    college_id VARCHAR(2)           COMMENT '所属学院（数据范围）',
    major_id   VARCHAR(8)           COMMENT '所属专业',
    class_id   VARCHAR(10)          COMMENT '所属班级（学生）',
    teacher_id VARCHAR(8)           COMMENT '关联教师编号',
    status     VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '0停用/1启用',
    last_login DATETIME             COMMENT '最近登录时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_name (user_name),
    KEY idx_user_role (role_id),
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
) ENGINE=InnoDB COMMENT='系统用户表';

CREATE TABLE sys_user_role (
    user_id VARCHAR(20) NOT NULL COMMENT '用户编号',
    role_id VARCHAR(4)  NOT NULL COMMENT '角色编号',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表（支持一人多角色）';

CREATE TABLE audit_log (
    log_id       VARCHAR(20) NOT NULL COMMENT '日志编号 L+时间戳',
    user_id      VARCHAR(20) NOT NULL COMMENT '操作人',
    role_id      VARCHAR(4)           COMMENT '操作角色',
    oper_type    VARCHAR(2)  NOT NULL COMMENT '01新增/02修改/03删除/04审批/05发布/06导出/07登录/08登出',
    oper_object  VARCHAR(60)          COMMENT '操作对象 表名:主键',
    before_value VARCHAR(300)         COMMENT '变更前值（JSON）',
    after_value  VARCHAR(300)         COMMENT '变更后值（JSON）',
    oper_result  VARCHAR(1)  NOT NULL DEFAULT '1' COMMENT '1成功/0失败',
    client_ip    VARCHAR(20)          COMMENT '客户端地址',
    oper_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (log_id),
    KEY idx_log_user (user_id),
    KEY idx_log_time (oper_time)
) ENGINE=InnoDB COMMENT='操作日志表（只追加）';

-- ---------------------------------------------------------------------
-- 八、完整课表视图（T20）
-- ---------------------------------------------------------------------

CREATE VIEW v_full_schedule AS
SELECT tc.term            AS term,
       tc.tc_id           AS tc_id,
       c.course_id        AS course_id,
       c.course_name      AS course_name,
       c.credit           AS credit,
       tc.teacher_id      AS teacher_id,
       t.teacher_name     AS teacher_name,
       tc.class_ids       AS class_ids,
       tc.student_num     AS student_num,
       sr.week_range      AS week_range,
       sr.week_day        AS week_day,
       sr.start_slot      AS start_slot,
       sr.end_slot        AS end_slot,
       r.room_id          AS room_id,
       r.room_name        AS room_name,
       r.room_type        AS room_type,
       sr.sched_status    AS sched_status,
       sr.sched_id        AS sched_id
FROM teaching_class tc
JOIN course c        ON c.course_id = tc.course_id
LEFT JOIN teacher t  ON t.teacher_id = tc.teacher_id
JOIN schedule_result sr ON sr.tc_id = tc.tc_id
JOIN classroom r     ON r.room_id = sr.room_id;

-- =====================================================================
-- 九、初始数据
-- 课程表存在自引用（先修课程），批量导入时临时关闭外键检查
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO college (college_id, college_name, college_short, college_head, contact_phone, college_status) VALUES
('01','资源与安全工程学院','资安学院','高峰','010-62332101','1'),
('02','未来城市学院','未来城市','刘建国','010-62332102','1'),
('03','冶金与生态工程学院','冶金学院','张海涛','010-62332103','1'),
('04','材料科学与工程学院','材料学院','王立新','010-62332104','1'),
('05','机械工程学院','机械学院','李春生','010-62332105','1'),
('06','能源与环境工程学院','能环学院','赵恒','010-62332106','1'),
('07','自动化学院','自动化','陈国栋','010-62332107','1'),
('08','人工智能学院','智能学院','孙立平','010-62332108','1'),
('09','计算机与通信工程学院','计通学院','周志强','010-62332109','1'),
('10','数理学院','数理学院','吴敏','010-62332110','1'),
('11','化学与生物工程学院','化生学院','郑海','010-62332111','1'),
('12','经济管理学院','经管学院','冯晓东','010-62332112','1'),
('13','文法学院','文法学院','韩梅','010-62332113','1'),
('14','马克思主义学院','马院','沈国华','010-62332114','1'),
('15','外国语学院','外语学院','何芳','010-62332115','1'),
('16','国家卓越工程师学院','卓工学院','杨帆','010-62332116','1'),
('17','创新创业学院','创院','罗红','010-62332117','1'),
('18','体育学院','体育学院','曹宇','010-62332118','1'),
('19','高精尖学院','高精尖','邓超','010-62332119','1'),
('20','未来技术学院','未来技术','范军','010-62332120','1');

INSERT INTO major (major_id, major_name, college_id, duration, degree, min_credit, major_status) VALUES
('080901','计算机科学与技术','09',4,'工学',165.0,'1'),
('080903','物联网工程','09',4,'工学',162.0,'1'),
('080703','通信工程','09',4,'工学',163.0,'1'),
('080717T','人工智能','08',4,'工学',165.0,'1'),
('120102','信息管理与信息系统','12',4,'管理学',160.0,'1'),
('120203','会计学','12',4,'管理学',158.0,'1'),
('020401','国际经济与贸易','12',4,'经济学',158.0,'1'),
('080801','自动化','07',4,'工学',164.0,'1'),
('080201','机械工程','05',4,'工学',166.0,'1'),
('080401','材料科学与工程','04',4,'工学',165.0,'1'),
('070101','数学与应用数学','10',4,'理学',160.0,'1'),
('030101','法学','13',4,'法学',160.0,'1'),
('050201','英语','15',4,'文学',160.0,'1'),
('081001','土木工程','02',4,'工学',165.0,'1'),
('080404','冶金工程','03',4,'工学',166.0,'1');

INSERT INTO course (course_id, course_name, course_type, credit, total_hours, assess_type, open_college, pre_course_id, syllabus, course_status) VALUES
('0901001','数据结构','13',4.0,64,'1','09','0901002','/files/syllabus/0901001.pdf','1'),
('0901002','程序设计基础','13',4.0,64,'1','09',NULL,'/files/syllabus/0901002.pdf','1'),
('0901003','计算机组成原理','13',4.0,64,'1','09','0901002','/files/syllabus/0901003.pdf','1'),
('0901004','操作系统','13',4.0,64,'1','09','0901003','/files/syllabus/0901004.pdf','1'),
('0901005','计算机网络','13',3.0,48,'1','09','0901004','/files/syllabus/0901005.pdf','1'),
('0901006','数据库系统原理','13',3.5,56,'1','09','0901001','/files/syllabus/0901006.pdf','1'),
('0901007','软件工程','13',3.0,48,'1','09','0901001','/files/syllabus/0901007.pdf','1'),
('0901008','人工智能导论','14',2.0,32,'2','09',NULL,'/files/syllabus/0901008.pdf','1'),
('0901009','编译原理','14',3.0,48,'1','09','0901003','/files/syllabus/0901009.pdf','1'),
('0901010','信息安全基础','14',2.0,32,'2','09',NULL,'/files/syllabus/0901010.pdf','1'),
('0901011','计算机程序设计实践','16',2.0,32,'2','09',NULL,NULL,'1'),
('0901012','毕业设计','16',8.0,128,'2','09',NULL,NULL,'1'),
('1001001','高等数学A','11',6.0,96,'1','10',NULL,'/files/syllabus/1001001.pdf','1'),
('1001002','线性代数','11',3.0,48,'1','10','1001001','/files/syllabus/1001002.pdf','1'),
('1001003','概率论与数理统计','11',3.0,48,'1','10','1001001','/files/syllabus/1001003.pdf','1'),
('1001004','大学物理','11',4.0,64,'1','10',NULL,'/files/syllabus/1001004.pdf','1'),
('1501001','大学英语（一）','11',3.0,48,'1','15',NULL,'/files/syllabus/1501001.pdf','1'),
('1501002','大学英语（二）','11',3.0,48,'1','15','1501001','/files/syllabus/1501002.pdf','1'),
('1801001','体育（一）','11',1.0,32,'2','18',NULL,NULL,'1'),
('1801002','体育（二）','11',1.0,32,'2','18','1801001',NULL,'1'),
('1401001','思想道德与法治','11',3.0,48,'2','14',NULL,'/files/syllabus/1401001.pdf','1'),
('1401002','中国近现代史纲要','11',3.0,48,'2','14',NULL,'/files/syllabus/1401002.pdf','1'),
('1401003','马克思主义基本原理','11',3.0,48,'1','14',NULL,'/files/syllabus/1401003.pdf','1'),
('1401004','毛泽东思想和中国特色社会主义理论体系概论','11',3.0,48,'1','14',NULL,'/files/syllabus/1401004.pdf','1'),
('1201001','管理学原理','13',3.0,48,'1','12',NULL,'/files/syllabus/1201001.pdf','1'),
('1201002','微观经济学','13',3.0,48,'1','12',NULL,'/files/syllabus/1201002.pdf','1'),
('1201003','管理信息系统','13',3.0,48,'1','12','1201001','/files/syllabus/1201003.pdf','1'),
('1201004','会计学原理','13',3.0,48,'1','12',NULL,'/files/syllabus/1201004.pdf','1'),
('1201005','数据分析与商务智能','14',2.5,40,'2','12','1201003','/files/syllabus/1201005.pdf','1'),
('1201006','电子商务概论','14',2.0,32,'2','12',NULL,'/files/syllabus/1201006.pdf','1'),
('1201007','组织行为学','14',2.0,32,'2','12','1201001','/files/syllabus/1201007.pdf','1'),
('0801001','机器学习','14',3.0,48,'1','08','1001003','/files/syllabus/0801001.pdf','1'),
('0801002','模式识别','14',2.5,40,'1','08','0801001','/files/syllabus/0801002.pdf','1'),
('1701001','创新创业基础','15',2.0,32,'2','17',NULL,'/files/syllabus/1701001.pdf','1'),
('1701002','大学生职业发展','15',1.0,16,'2','17',NULL,NULL,'1'),
('1701003','艺术鉴赏','15',2.0,32,'2','17',NULL,'/files/syllabus/1701003.pdf','1'),
('1701004','中国传统文化概论','15',2.0,32,'2','17',NULL,'/files/syllabus/1701004.pdf','1'),
('1301001','经济法','14',2.0,32,'2','13',NULL,'/files/syllabus/1301001.pdf','1'),
('0501001','工程制图','13',3.0,48,'1','05',NULL,'/files/syllabus/0501001.pdf','1'),
('0701001','自动控制原理','13',4.0,64,'1','07','1001001','/files/syllabus/0701001.pdf','1');

INSERT INTO class_info (class_id, class_name, major_id, grade, student_num) VALUES
('计2601','计算机2601班','080901','2026',32),
('计2602','计算机2602班','080901','2026',30),
('计2501','计算机2501班','080901','2025',31),
('信管2601','信管2601班','120102','2026',35),
('信管2602','信管2602班','120102','2026',33),
('会计2601','会计2601班','120203','2026',40),
('智能2601','人工智能2601班','080717T','2026',30),
('自动2601','自动化2601班','080801','2026',34),
('物联2601','物联网2601班','080903','2026',28);

INSERT INTO teacher (teacher_id, teacher_name, college_id, title, teacher_status) VALUES
('A260001','李建国','09','教授','1'),
('A260002','王敏','09','副教授','1'),
('A260003','张伟','09','讲师','1'),
('A260004','刘洋','09','副教授','1'),
('A260005','陈静','10','教授','1'),
('A260006','赵磊','10','讲师','1'),
('A260007','孙晓明','15','副教授','1'),
('A260008','周倩','14','教授','1'),
('A260009','吴强','18','讲师','1'),
('A260010','郑海涛','12','教授','1'),
('A260011','冯丽','12','副教授','1'),
('A260012','蒋文博','12','讲师','1'),
('A260013','韩雪','08','副教授','1'),
('A260014','沈立','17','副教授','1'),
('A260015','何军','05','教授','1');

-- 培养方案：计算机科学与技术 2026 级主修（已发布）
INSERT INTO major_program (major_id, grade, program_type, total_credit, goal_desc, version, plan_status, dual_quota, review_opinion, publish_date) VALUES
('080901','2026','1',100.0,'培养具备扎实计算机理论基础、较强工程实践能力与创新意识，能够在信息技术领域从事研发与管理工作的高级工程技术人才。','V3.0','3',NULL,'校教学委员会审议通过','2026-06-30'),
('120102','2026','1',100.0,'培养掌握信息系统分析与设计方法，能够在企事业单位从事信息化建设与管理工作的复合型管理人才。','V2.0','0',NULL,NULL,NULL),
('080901','2026','2',40.5,'面向学有余力的本科生开展计算机科学与技术双学位培养，强化系统能力与工程素养。','V1.0','3',30,'校教学委员会审议通过','2026-06-30'),
('080717T','2026','1',100.0,'培养掌握人工智能基础理论与关键技术，能够从事智能系统研发的拔尖创新人才。','V1.0','0',NULL,NULL,NULL);

INSERT INTO program_course (major_id, grade, program_type, course_id, course_type, credit, hours, open_semester, pre_course_id, cross_status) VALUES
('080901','2026','1','0901002','13',4.0,64,'2026-2027-1',NULL,'2'),
('080901','2026','1','1001001','11',6.0,96,'2026-2027-1',NULL,'1'),
('080901','2026','1','1501001','11',3.0,48,'2026-2027-1',NULL,'1'),
('080901','2026','1','1801001','11',1.0,32,'2026-2027-1',NULL,'1'),
('080901','2026','1','1401001','11',3.0,48,'2026-2027-1',NULL,'1'),
('080901','2026','1','1001002','11',3.0,48,'2026-2027-2','1001001','1'),
('080901','2026','1','1001004','11',4.0,64,'2026-2027-2',NULL,'1'),
('080901','2026','1','1501002','11',3.0,48,'2026-2027-2','1501001','1'),
('080901','2026','1','1801002','11',1.0,32,'2026-2027-2','1801001','1'),
('080901','2026','1','1401002','11',3.0,48,'2026-2027-2',NULL,'1'),
('080901','2026','1','0901001','13',4.0,64,'2027-2028-1','0901002','2'),
('080901','2026','1','0901003','13',4.0,64,'2027-2028-1','0901002','2'),
('080901','2026','1','1001003','11',3.0,48,'2027-2028-1','1001001','1'),
('080901','2026','1','1401003','11',3.0,48,'2027-2028-1',NULL,'1'),
('080901','2026','1','0901004','13',4.0,64,'2027-2028-2','0901003','2'),
('080901','2026','1','0901006','13',3.5,56,'2027-2028-2','0901001','2'),
('080901','2026','1','1401004','11',3.0,48,'2027-2028-2',NULL,'1'),
('080901','2026','1','0901005','13',3.0,48,'2028-2029-1','0901004','2'),
('080901','2026','1','0901007','13',3.0,48,'2028-2029-1','0901001','2'),
('080901','2026','1','0901010','14',2.0,32,'2028-2029-1',NULL,'2'),
('080901','2026','1','0901008','14',2.0,32,'2028-2029-2',NULL,'2'),
('080901','2026','1','0901009','14',3.0,48,'2028-2029-2','0901003','2'),
('080901','2026','1','1701001','15',2.0,32,'2027-2028-1',NULL,'1'),
('080901','2026','1','0901011','16',2.0,32,'2026-2027-2',NULL,'2'),
('080901','2026','1','0901012','16',8.0,128,'2029-2030-2',NULL,'2'),
('120102','2026','1','1201001','13',3.0,48,'2026-2027-1',NULL,'2'),
('120102','2026','1','1001001','11',6.0,96,'2026-2027-1',NULL,'1'),
('120102','2026','1','1501001','11',3.0,48,'2026-2027-1',NULL,'1'),
('120102','2026','1','1801001','11',1.0,32,'2026-2027-1',NULL,'1'),
('120102','2026','1','1401001','11',3.0,48,'2026-2027-1',NULL,'1'),
('120102','2026','1','1201002','13',3.0,48,'2026-2027-2',NULL,'2'),
('120102','2026','1','1201004','13',3.0,48,'2026-2027-2',NULL,'2'),
('120102','2026','1','1001002','11',3.0,48,'2026-2027-2','1001001','1'),
('120102','2026','1','1201003','13',3.0,48,'2027-2028-2','1201001','2'),
('120102','2026','1','1201005','14',2.5,40,'2028-2029-1','1201003','2'),
('120102','2026','1','1201006','14',2.0,32,'2028-2029-2',NULL,'2'),
('120102','2026','1','1201007','14',2.0,32,'2028-2029-1','1201001','2'),
('080901','2026','1','0801001','14',3.0,48,'2029-2030-1','1001003','1'),
('080901','2026','1','0801002','14',2.5,40,'2029-2030-1','0801001','1'),
('080901','2026','1','1301001','14',2.0,32,'2029-2030-2',NULL,'1'),
('080901','2026','1','1701002','15',1.0,16,'2026-2027-2',NULL,'1'),
('080901','2026','1','1701003','15',2.0,32,'2027-2028-2',NULL,'1'),
('080901','2026','1','1701004','15',2.0,32,'2028-2029-2',NULL,'1'),
('080901','2026','1','0501001','13',3.0,48,'2026-2027-2',NULL,'1'),
('080901','2026','1','0701001','13',4.0,64,'2027-2028-2','1001001','1'),
('080901','2026','2','0901002','13',4.0,64,'2026-2027-1',NULL,'2'),
('080901','2026','2','0901001','13',4.0,64,'2027-2028-1','0901002','2'),
('080901','2026','2','0901003','13',4.0,64,'2027-2028-1','0901002','2'),
('080901','2026','2','0901004','13',4.0,64,'2027-2028-2','0901003','2'),
('080901','2026','2','0901005','13',3.0,48,'2028-2029-1','0901004','2'),
('080901','2026','2','0901006','13',3.5,56,'2027-2028-2','0901001','2'),
('080901','2026','2','0901007','13',3.0,48,'2028-2029-1','0901001','2'),
('080901','2026','2','0901008','14',2.0,32,'2028-2029-2',NULL,'2'),
('080901','2026','2','0801001','14',3.0,48,'2028-2029-2',NULL,'2'),
('080901','2026','2','0901011','16',2.0,32,'2028-2029-2',NULL,'2'),
('080901','2026','2','0901012','16',8.0,128,'2029-2030-2',NULL,'2'),
('080717T','2026','1','1001001','11',6.0,96,'2026-2027-1',NULL,'1'),
('080717T','2026','1','1501001','11',3.0,48,'2026-2027-1',NULL,'1'),
('080717T','2026','1','1001003','11',3.0,48,'2026-2027-2','1001001','0'),
('080717T','2026','1','0801001','14',3.0,48,'2028-2029-1',NULL,'2'),
('080717T','2026','1','0801002','14',2.5,40,'2028-2029-2','0801001','2');

INSERT INTO general_plan (school_year, course_id, suggest_hours, undertake_college, teacher_id, confirm_status, plan_status) VALUES
('2026-2027','1701001',32,'17','A260014','1','2'),
('2026-2027','1701002',16,'17','A260014','1','2'),
('2026-2027','1701003',32,'17','A260014','0','1'),
('2026-2027','1701004',32,'17','A260014','0','1');

INSERT INTO new_course_apply (apply_id, apply_user, course_name, course_type, dispatch_college, apply_reason, credit, hours, college_opinion, audit_status, course_id, audit_opinion, apply_time) VALUES
('NC20260001','A260001','智能硬件导论','17',NULL,'面向人工智能方向的新设素质拓展课程，衔接嵌入式与智能系统内容。',2.0,32,'课程内容与专业培养目标一致，同意上报教务处','3',NULL,NULL,'2026-11-10 09:20:00'),
('NC20260002','B220002','商业数据分析实务','14',NULL,'补充信管专业商务智能方向的实践类课程。',2.5,40,NULL,'3',NULL,NULL,'2026-11-12 14:05:00'),
('NC20260003','B220001','中国传统文化概论','15','09','面向全校开设的通识课程，下发计通学院补充培养要求。',2.0,32,NULL,'4',NULL,NULL,'2026-11-15 10:30:00'),
('NC20250009','A260001','云原生技术基础','17',NULL,'面向计算机专业高年级开设的工程实践课程。',2.0,32,'同意申报','1','0901013','内容与学时匹配，同意入库','2025-05-06 10:00:00');

INSERT INTO teaching_class (tc_id, term, course_id, teacher_id, class_ids, student_num, capacity, tc_type, teach_type, hours, week_count, prefer_slot, unavailable_time, tc_status, source, actual_num, open_lower) VALUES
('202510001','2025-2026-1','1001001','A260004','计2601',32,90,'1','1',96,16,'1-2 节',NULL,'4',1,32,10),
('202510002','2025-2026-1','1501001','A260009','计2601',32,60,'1','1',48,16,'3-4 节',NULL,'4',1,32,10),
('202510003','2025-2026-1','0901005','A260005','计2601',32,45,'1','1',48,16,'5-6 节',NULL,'4',1,32,10),
('202520001','2025-2026-2','0901002','A260001','计2601',32,60,'1','1',64,16,'1-2 节',NULL,'4',1,32,10),
('202520002','2025-2026-2','1501002','A260009','计2601',32,60,'1','1',48,16,'3-4 节',NULL,'4',1,32,10),
('202610001','2026-2027-1','0901002','A260001','计2601',32,60,'1','1',64,16,'1-2 节','周三下午','4',1,32,10),
('202610002','2026-2027-1','0901001','A260002','计2501',31,60,'1','1',64,16,'1-4 节','周五下午','4',1,31,10),
('202610003','2026-2027-1','0901003','A260003','计2501',31,60,'1','1',64,16,'3-4 节',NULL,'3',1,31,10),
('202610004','2026-2027-1','0901006','A260004','计2501',31,60,'1','3',56,16,'5-6 节',NULL,'3',1,31,10),
('202610005','2026-2027-1','1001001','A260005','计2601',32,90,'1','1',96,16,'1-2 节',NULL,'4',1,32,10),
('202610006','2026-2027-1','1001001','A260006','计2602',30,90,'1','1',96,16,'1-2 节',NULL,'3',1,30,10),
('202610007','2026-2027-1','1501001','A260007','计2601,计2602',62,90,'1','1',48,16,'3-4 节',NULL,'4',1,62,10),
('202610008','2026-2027-1','1401001','A260008','计2601,计2602',62,120,'1','1',48,16,'5-6 节',NULL,'4',1,62,10),
('202610009','2026-2027-1','1801001','A260009','计2601,计2602',62,60,'1','1',32,16,'7-8 节',NULL,'3',1,62,10),
('202610010','2026-2027-1','1201001','A260010','信管2601',35,60,'1','1',48,16,'1-2 节',NULL,'3',1,35,10),
('202610011','2026-2027-1','1201003','A260011','信管2601,信管2602',68,90,'1','1',48,16,'3-4 节',NULL,'0',1,NULL,10),
('202610012','2026-2027-1','0901008','A260013','智能2601',30,60,'2','1',32,16,'5-6 节',NULL,'3',3,28,10),
('202610013','2026-2027-1','1701001','A260014','计2601',32,60,'2','1',32,16,'7-8 节',NULL,'3',3,26,10),
('202610014','2026-2027-1','0901001','A260001','080901-2026-2',5,30,'3','1',64,16,'9-10 节',NULL,'0',1,NULL,5);

INSERT INTO classroom (room_id, room_name, building, college_id, capacity, room_type, available_time, room_status) VALUES
('A101','逸夫楼A101','逸夫楼','12',60,'1','周一至周五 1-12 节','1'),
('A102','逸夫楼A102','逸夫楼','12',60,'2','周一至周五 1-12 节','1'),
('A201','教学楼A201','教学楼',NULL,90,'2','周一至周五 1-12 节','1'),
('A202','教学楼A202','教学楼',NULL,90,'2','周一至周五 1-12 节','1'),
('B101','教学楼B101','教学楼',NULL,120,'2','周一至周五 1-12 节','1'),
('B201','教学楼B201','教学楼',NULL,120,'2','周一至周五 1-12 节','1'),
('JS101','计算机楼机房101','计算机楼','09',60,'3','周一至周五 1-12 节','1'),
('JS102','计算机楼机房102','计算机楼','09',60,'3','周一至周五 1-12 节','1'),
('SY101','实验楼101','实验楼','08',40,'4','周一至周五 1-12 节','1'),
('C301','教学楼C301','教学楼',NULL,45,'1','周一至周日 1-12 节','1'),
('C302','教学楼C302','教学楼',NULL,45,'1','周一至周日 1-12 节','0');

INSERT INTO schedule_rule (rule_id, rule_name, rule_type, rule_content, priority_no, enable) VALUES
('R01','公共基础课优先上午','01','校级必修课与公共基础课优先安排在第1-4节',1,'1'),
('R02','教室容量匹配','02','教室容量不小于教学班面向人数',2,'1'),
('R04','教师不可用时间回避','03','不占用教师填报的不可用时间',3,'1'),
('R05','三重冲突检测','04','教师、教室、面向班级均不得在同一时段重复安排',5,'1'),
('R06','教学班固定教室','01','同一教学班的多段安排尽量使用同一教室',6,'1');

INSERT INTO schedule_result (sched_id, tc_id, room_id, week_range, week_day, start_slot, end_slot, sched_status, lock_flag, conflict_desc) VALUES
('S20260001','202610001','JS101','1-16','1',1,2,'2','1',NULL),
('S20260002','202610001','JS101','1-16','4',3,4,'2','1',NULL),
('S20260003','202610002','A201','1-16','2',1,2,'2','1',NULL),
('S20260004','202610002','A201','1-16','4',1,2,'2','1',NULL),
('S20260005','202610005','B101','1-16','1',3,4,'2','1',NULL),
('S20260006','202610005','B101','1-16','3',1,2,'2','1',NULL),
('S20260007','202610007','A201','1-16','3',3,4,'2','1',NULL),
('S20260008','202610008','B101','1-16','5',5,6,'2','1',NULL),
('S20260009','202610008','B101','1-16','2',5,6,'2','1',NULL),
('S20260010','202610003','A101','1-16','3',3,4,'3','0',NULL),
('S20260011','202610004','JS102','1-16','5',5,6,'3','0',NULL),
('S20260012','202610009','C301','1-16','4',7,8,'3','0',NULL),
('S20260013','202610010','A102','1-16','1',1,2,'3','0',NULL),
('S20260014','202610003','A201','1-16','5',1,2,'3','0',NULL),
('S20260015','202610004','JS101','1-16','2',3,4,'3','0',NULL),
('S20260016','202610005','B101','1-16','5',7,8,'3','0',NULL),
('S20260017','202610006','A201','1-16','1',7,8,'3','0',NULL),
('S20260018','202610006','A201','1-16','2',7,8,'3','0',NULL),
('S20260019','202610006','A201','1-16','3',7,8,'3','0',NULL),
('S20260020','202610007','A201','1-16','2',3,4,'3','0',NULL),
('S20260021','202610010','A102','1-16','4',5,6,'3','0',NULL),
('S20260022','202610012','A201','1-16','1',5,6,'3','0',NULL),
('S20260023','202610013','A101','1-16','4',9,10,'3','0',NULL);

INSERT INTO schedule_conflict (conflict_id, tc_id, other_tc_id, conflict_type, conflict_desc, suggest_plan, handle_status) VALUES
('CF20260001','202610009','202610008','04','体育（一）面向62人，C301容量45','改用B101（120座）周四7-8节','0');

INSERT INTO adjust_record (adjust_id, sched_id, adjust_type, old_value, new_value, new_room_id, new_week_day, new_start_slot, new_end_slot, reason, effect_date, approve_status, approver, approve_opinion, restore_flag, notify_status, create_time) VALUES
('AD20260001','S20260010','1','周三 3-4节 A101','周四 9-10节 A102','A102','4',9,10,'教师出差，申请一次性调课','2026-11-26','1','B220001','同意，已通知学生','0','1','2026-11-18 10:20:00'),
('AD20260002','S20260013','2','周一 1-2节 A102','周二 3-4节 A102','A102','2',3,4,'长期调整，避开学院例会','2026-11-23','0',NULL,NULL,'0','0','2026-11-20 15:40:00');

INSERT INTO dual_enroll (enroll_id, stu_id, major_id, enroll_year, apply_date, score_avg, audit_status, finish_status, enroll_date, remark) VALUES
('DE20260001','U202341224','080901','2026-2027','2026-09-10',88.5,'0','0',NULL,'计算机双学位'),
('DE20260002','U202341225','080901','2026-2027','2026-09-10',85.0,'1','1','2026-09-20','计算机双学位'),
('DE20260003','U202341226','120102','2026-2027','2026-09-11',79.5,'2','2',NULL,'名额已满');

INSERT INTO study_record (rec_id, stu_id, program_type, major_id, course_id, tc_id, study_type, source_course_id, credit, conflict_type, audit_status, remark) VALUES
('SR20260001','U202341225','2','080901','0901001','202610002','1',NULL,4.0,'03','1',NULL),
('SR20260002','U202341225','2','080901','0901006','202610004','1',NULL,3.5,'01','0','与主修课程时段重合，备案允许'),
('SR20260003','U202341226','1','120102','1201003','202610011','1',NULL,3.0,'02','0','冲突，顺延至下一年开课');

INSERT INTO credit_apply (apply_id, stu_id, from_major, to_major, apply_date, apply_credit, college_opinion, audit_status, approver, audit_opinion, audit_date) VALUES
('CA2600001','U202341227','080903','080901','2026-10-08',6.5,'课程内容相近，同意转换','1','B220001','同意认定，计入转入专业学分','2026-10-15'),
('CA2600002','U202341228','120203','120102','2026-10-12',3.0,NULL,'0',NULL,NULL,NULL);

INSERT INTO credit_item (apply_id, seq_no, source_course_id, source_credit, target_course_id, target_credit, transfer_type, item_status) VALUES
('CA2600001',1,'0901001',4.0,'0901001',4.0,'01','1'),
('CA2600001',2,'0901007',3.0,'0901007',2.5,'02','1'),
('CA2600002',1,'1201004',3.0,'1201004',3.0,'01','0');

INSERT INTO eval_plan (plan_id, term, plan_name, indicator_json, open_date, close_date, anonymity, min_sample, plan_status) VALUES
('EP202601','2026-2027-1','2026-2027学年秋季学期学生评教','[{"name":"教学态度","w":0.3},{"name":"内容组织","w":0.3},{"name":"教学方法","w":0.2},{"name":"考核合理","w":0.2}]','2026-11-15','2026-12-10','1',5,'1'),
('EP202502','2025-2026-2','2025-2026学年春季学期学生评教','[{"name":"教学态度","w":0.4},{"name":"内容组织","w":0.3},{"name":"教学方法","w":0.3}]','2026-05-10','2026-06-10','1',5,'2');

INSERT INTO eval_record (record_id, plan_id, tc_id, stu_id, score, item_score, comment, valid_flag, submit_time) VALUES
('ER20260001','EP202601','202610001','U2023***01','94.0','{"教学态度":95,"内容组织":94,"教学方法":93,"考核合理":94}','讲解清晰，例题充分','1','2026-11-16 20:15:00'),
('ER20260002','EP202601','202610001','U2023***02','90.0','{"教学态度":92,"内容组织":90,"教学方法":88,"考核合理":90}','作业量适中','1','2026-11-17 09:30:00'),
('ER20260003','EP202601','202610001','U2023***03','88.0','{"教学态度":90,"内容组织":88,"教学方法":86,"考核合理":88}','希望增加实践环节','1','2026-11-17 21:05:00'),
('ER20260004','EP202601','202610002','U2023***04','96.0','{"教学态度":97,"内容组织":96,"教学方法":95,"考核合理":96}','课程难度合适，收获大','1','2026-11-18 10:00:00'),
('ER20260005','EP202601','202610007','U2023***05','85.0','{"教学态度":86,"内容组织":85,"教学方法":84,"考核合理":85}','口语练习可以再多一些','1','2026-11-18 19:40:00'),
('ER20250001','EP202502','202520001','U2023***06','91.0','{"教学态度":92,"内容组织":90,"教学方法":91}','总体满意','1','2026-06-01 10:00:00'),
('ER20250002','EP202502','202520001','U2023***07','87.5','{"教学态度":88,"内容组织":87,"教学方法":87}','希望增加随堂练习','1','2026-06-01 11:20:00'),
('ER20250003','EP202502','202520001','U2023***08','93.0','{"教学态度":94,"内容组织":93,"教学方法":92}','案例贴合工程实践','1','2026-06-02 09:10:00'),
('ER20250004','EP202502','202520001','U2023***09','89.0','{"教学态度":90,"内容组织":89,"教学方法":88}','节奏稍快','1','2026-06-02 15:40:00'),
('ER20250005','EP202502','202520001','U2023***10','95.0','{"教学态度":96,"内容组织":95,"教学方法":94}','答疑及时，收获很大','1','2026-06-03 20:05:00');

INSERT INTO sys_role (role_id, role_name, data_scope, func_perm, role_status, remark) VALUES
('R01','系统管理员','1','P01,P02,P03,P04,P05,P06,P07,P08,P09,P10,P11,P12,P13,P14,P15,P16,P17,P18,P19,P20,P21,P22,P23,P24,P25','1','信息化办运维岗'),
('R02','教务处管理员','1','P01,P02,P03,P04,P05,P07,P08,P09,P11,P12,P13,P14,P15,P16,P17,P18,P19,P23,P24,P25','1','教务处教务科、教学研究科、质量科'),
('R03','学院教务员','2','P02,P04,P05,P06,P08,P10,P12,P13,P14,P19,P21,P22,P23,P24,P25','1','学院教务科'),
('R04','专业负责人/教师','3','P05,P06,P08,P10,P12,P15,P25','1','系主任、专业负责人、任课教师'),
('R05','学生','3','P12,P13,P14,P15,P25','1','在校本科生'),
('R06','教室管理员/审计员','1','P12,P16','1','后勤集团、审计岗（仅日志审计，不含角色配置）');

-- 演示账号（口令摘要 = SHA-256("mis@" + 明文口令)，与 AuthService.hash 一致）
-- 口令规则：系统管理员 admin 为 123456；
--           职工（教务处/学院教务员/教室管理员）工号 B + 入职年份后两位 + 4 位顺序号，口令 USTB@ + 工号后 6 位；
--           教师工号 A + 入职年份后两位 + 4 位顺序号，口令 USTB@ + 工号后 6 位；
--           学生学号 U(本科)/M(硕士)/D(博士) + 入学年份 4 位 + 类别 1 位（4 正常录取、5 单列类、6 留学生）+ 4 位顺序号，
--           口令 USTB@ + 学号后 6 位。
INSERT INTO sys_user (user_id, user_name, password, real_name, role_id, college_id, major_id, class_id, teacher_id, status) VALUES
('admin','admin','a4078b2a5a4643dd9513962207c22d19719fde6cc62b34604fba481549b8800a','系统管理员','R01',NULL,NULL,NULL,NULL,'1'),
('B220001','B220001','06391ab0433f0512b14fb0ac50b48091c67b630611d83bba1d8b136267465726','教务处管理员','R02',NULL,NULL,NULL,NULL,'1'),
('B220002','B220002','ef9b38dd9303468e87cff1b56f8c34587e140276f214cd9ae52ededaa73572f4','计通学院教务员','R03','09',NULL,NULL,NULL,'1'),
('B220003','B220003','cd96c01678f6a08cc9b753f0e0cd117a9fd898b9fe3af1cb14ce3178ff4d3c20','经管学院教务员','R03','12',NULL,NULL,NULL,'1'),
('B220025','B220025','f2e8f98ecdd543d9314255f9aec1faf9f60b48e8d8579924d25e721f54475576','未来城市学院教务员','R03','02',NULL,NULL,NULL,'1'),
('B220005','B220005','e467e6142f8f6c8e10e761cf633dd66ceec5cb64176aeb60c243ec543e95c38e','资源与安全工程学院教务员','R03','01',NULL,NULL,NULL,'1'),
('B220006','B220006','55afaecabaf96f54ee5cff4136d817b51149cd2660d5951739157b92abdc9dc2','冶金与生态工程学院教务员','R03','03',NULL,NULL,NULL,'1'),
('B220007','B220007','6e5ecb7a6426bd7817de3c8d09a1d58ea891719f3d35b3e5a5eaade8ae52e756','材料科学与工程学院教务员','R03','04',NULL,NULL,NULL,'1'),
('B220008','B220008','64784afa501f7eeb4c22f5881753f43cde931d653f2fa1d067284e3d867de971','机械工程学院教务员','R03','05',NULL,NULL,NULL,'1'),
('B220009','B220009','833ad222bc2c981732be73c2eaf9be05009ad7b8083ac78b1926aee00d62b67e','能源与环境工程学院教务员','R03','06',NULL,NULL,NULL,'1'),
('B220010','B220010','bfae4cca18ba59a2309adca7d203f32351a0f242981f851bf44930bb27979942','自动化学院教务员','R03','07',NULL,NULL,NULL,'1'),
('B220011','B220011','f84716d84fb5b7b1a352f0c973e93dcee3f042fca8f00bd3153850591e246ef8','人工智能学院教务员','R03','08',NULL,NULL,NULL,'1'),
('B220012','B220012','6bf11a06e05ea22a66b2666c3d564e850d22162595cd0310d8ad88beb58b4112','数理学院教务员','R03','10',NULL,NULL,NULL,'1'),
('B220013','B220013','352c8f8e3c50b0e127797e504ef301469e87f939ba069191ca5cd09bbaf0182f','化学与生物工程学院教务员','R03','11',NULL,NULL,NULL,'1'),
('B220014','B220014','02d4dd295ae399757732af14571d418b000dd748f35820b45fd35284d1b843ab','文法学院教务员','R03','13',NULL,NULL,NULL,'1'),
('B220015','B220015','b3a3d79c64651814fca0eefca671911f16abc1968a15e1a0f33b647f330655bd','马克思主义学院教务员','R03','14',NULL,NULL,NULL,'1'),
('B220016','B220016','c2bddac038f02be53037cb0465b7d1202bdc9b4c8eb8740387f91b2415912afe','外国语学院教务员','R03','15',NULL,NULL,NULL,'1'),
('B220017','B220017','75f8afd67ad5a8e414df929d41ab3358eee7825145abd94d01f9d7fbe7c60513','国家卓越工程师学院教务员','R03','16',NULL,NULL,NULL,'1'),
('B220018','B220018','bf7170269641bbc46662529afeb45e638ef08b7f67707e848b3609544687e352','创新创业学院教务员','R03','17',NULL,NULL,NULL,'1'),
('B220019','B220019','00aebb93751637d9c8c6ed89e643aad9748fdf6bba6c0633f86fe6561e07ecd1','体育学院教务员','R03','18',NULL,NULL,NULL,'1'),
('B220020','B220020','5ebf315cf009dfda4d9cbbf34f9350afed06e243052f69e5630bd7bbe8f0c582','高精尖学院教务员','R03','19',NULL,NULL,NULL,'1'),
('B220021','B220021','0e8ca84e29ede3a0d02f99791009f28748c7c2aa3f0afc6699b59b9d204f097f','未来技术学院教务员','R03','20',NULL,NULL,NULL,'1'),
('B220022','B220022','ecf205df3a47f8d9535efe968c63c12de837140c3258b4f2f8cdc4ebfe387717','军事教务员','R03','22',NULL,NULL,NULL,'1'),
('B220023','B220023','f9a571eaf6ced93f5297a167a912eae88e802d20789604a6307451654c1b32d7','人文素质中心教务员','R03','29',NULL,NULL,NULL,'1'),
('B220024','B220024','766b33f7c8e5342aa161393631bb1c2a90c74d7f176fe504a7de63c7936fde6b','碳中和学院教务员','R03','30',NULL,NULL,NULL,'1'),
('A260001','A260001','6f81abfbbbd208d51d9bccdbc67be17ed441dd659517278be7f4c83e1198c2bd','李建国','R04','09',NULL,NULL,'A260001','1'),
('A260002','A260002','8af0bcb2424ac8cec7efcbdcd0fd2a9d731083bdad3fc2dc377a32f62f13c987','王敏','R04','09',NULL,NULL,'A260002','1'),
('A260003','A260003','492a2da5eaefce1c71ca1fa35e1aafea182dc40a77ba26eeff4b75e3603014b3','张伟','R04','09',NULL,NULL,'A260003','1'),
('A260004','A260004','95cf33fcbebfcc1d44505684e84905564a6d49d16f42d522fd914baef4370747','刘洋','R04','09',NULL,NULL,'A260004','1'),
('A260005','A260005','d763b4b59cf629a692dfad2ef53169cd39082d560cdc16a9b160b71c656c2004','陈静','R04','10',NULL,NULL,'A260005','1'),
('A260006','A260006','7876b5f2f043dd729b94f319db69b3235d60493278564ba644f14dd01cd9353c','赵磊','R04','10',NULL,NULL,'A260006','1'),
('A260007','A260007','17a3db24b2230f68e78dbc777b3545cb2c337040e03c9ef00a19630b336738d3','孙晓明','R04','15',NULL,NULL,'A260007','1'),
('A260008','A260008','1ee98d7e9db59d13e9c1bc4ad07353352f45faaa8a5b03a80d677a3fc118f982','周倩','R04','14',NULL,NULL,'A260008','1'),
('A260009','A260009','1b666639ce0ca3da72baba5a6fe91ff3e6cd7b01adf380c17de958064e44db89','吴强','R04','18',NULL,NULL,'A260009','1'),
('A260010','A260010','885701bebd41251d712356bf3d948708ebe69878b5c820741e7d389f7f177723','郑海涛','R04','12',NULL,NULL,'A260010','1'),
('A260011','A260011','1dd62208da4bf519cbda8539b261e524c636e17dc8c4b82c32010bdfbb1da8be','冯丽','R04','12',NULL,NULL,'A260011','1'),
('A260012','A260012','14163d728a9f70855b4bf570482edbbd12a404a71b9286c170d8a5016977e020','蒋文博','R04','12',NULL,NULL,'A260012','1'),
('A260013','A260013','a76f6d477964f4e8880ac72cbabc753e5c4bc1ce9ffb9ec815df93ba4a7e027d','韩雪','R04','08',NULL,NULL,'A260013','1'),
('A260014','A260014','6e9399692492d67e3f44f379e9b59db2b8f9cbd40eedb3d77a462a180beb6342','沈立','R04','17',NULL,NULL,'A260014','1'),
('A260015','A260015','c50c18162dbcb67f74faf7bec2e0442b7b1adfc261e4df62f4adde774e471115','何军','R04','05',NULL,NULL,'A260015','1'),
('U202341224','U202341224','b11db76eb702bf9b9409f7118aa626090e25c7c53e85b320399fd1156ba3974b','赵一鸣','R05','09','080901','计2601',NULL,'1'),
('U202341225','U202341225','deb503d2a8d8829611a167d11754636ed9b89df1f43067f3218d683d3df93299','张思远','R05','09','080901','计2601',NULL,'1'),
('U202341226','U202341226','7603521e08e97f8db5a4e4065ba5c5c9c1b1f25f63e287c3a843d3baa74ec03c','刘雨桐','R05','09','080901','计2601',NULL,'1'),
('U202341227','U202341227','36fb5e238e373b5a39e7dc2a1066a541668ff83078aced5246c5d8d42abf23c3','陈嘉豪','R05','09','080901','计2601',NULL,'1'),
('B220004','B220004','da4be3997054b1287faed729a3caa6ea5afb9122b54228a06e91d8f65f8084c3','教室管理员','R06',NULL,NULL,NULL,NULL,'1');

INSERT INTO sys_user_role (user_id, role_id) VALUES
('admin','R01'),('B220001','R02'),('B220002','R03'),('B220003','R03'),('B220025','R03'),('A260001','R04'),('A260002','R04'),
('A260003','R04'),('A260004','R04'),('A260005','R04'),('A260006','R04'),('A260007','R04'),('A260008','R04'),
('A260009','R04'),('A260010','R04'),('A260011','R04'),('A260012','R04'),('A260013','R04'),('A260014','R04'),
('A260015','R04'),
('U202341224','R05'),('U202341225','R05'),('U202341226','R05'),('U202341227','R05'),('B220004','R06');

INSERT INTO audit_log (log_id, user_id, role_id, oper_type, oper_object, before_value, after_value, oper_result, client_ip, oper_time) VALUES
('L20260630100001','B220001','R02','05','major_program:080901-2026-1','{"plan_status":"2"}','{"plan_status":"3"}','1','10.10.1.21','2026-06-30 10:00:01'),
('L20261114093001','B220001','R02','05','schedule_result:2026-2027-1','{"sched_status":"1"}','{"sched_status":"2"}','1','10.10.1.23','2026-11-14 09:30:01'),
('L20261118102001','A260001','R04','01','adjust_record:AD20260001',NULL,'{"new_value":"周四 9-10节 A102"}','1','10.10.2.15','2026-11-18 10:20:00'),
('L20261120154001','A260001','R04','01','adjust_record:AD20260002',NULL,'{"new_value":"周二 3-4节 A102"}','1','10.10.2.15','2026-11-20 15:40:00');

SET FOREIGN_KEY_CHECKS = 1;

-- 视图校验
SELECT (SELECT COUNT(*) FROM college) AS college_cnt,
       (SELECT COUNT(*) FROM major) AS major_cnt,
       (SELECT COUNT(*) FROM course) AS course_cnt,
       (SELECT COUNT(*) FROM teaching_class) AS tc_cnt,
       (SELECT COUNT(*) FROM schedule_result) AS sched_cnt,
       (SELECT COUNT(*) FROM v_full_schedule) AS view_cnt;
