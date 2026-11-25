package com.ustb.mis.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 跨表只读查询（列表页与课表视图），字段统一以驼峰别名返回。
 */
@Mapper
public interface ViewMapper {

    @Select("""
            <script>
            SELECT m.major_id AS majorId, m.major_name AS majorName, m.college_id AS collegeId,
                   c.college_name AS collegeName, m.duration AS duration, m.degree AS degree,
                   m.min_credit AS minCredit, m.major_status AS majorStatus,
                   (SELECT COUNT(*) FROM major_program p WHERE p.major_id = m.major_id) AS programCount
            FROM major m LEFT JOIN college c ON c.college_id = m.college_id
            WHERE 1 = 1
            <if test="collegeId != null and collegeId != ''"> AND m.college_id = #{collegeId}</if>
            <if test="keyword != null and keyword != ''">
                AND (m.major_name LIKE CONCAT('%', #{keyword}, '%') OR m.major_id LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY m.major_id
            </script>
            """)
    List<Map<String, Object>> listMajors(@Param("collegeId") String collegeId, @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT c.course_id AS courseId, c.course_name AS courseName, c.course_type AS courseType,
                   c.credit AS credit, c.total_hours AS totalHours, c.assess_type AS assessType,
                   c.theory_hours AS theoryHours, c.practice_hours AS practiceHours,
                   c.practice_room_type AS practiceRoomType,
                   c.open_college AS openCollege, co.college_name AS collegeName,
                   c.pre_course_id AS preCourseId, p.course_name AS preCourseName,
                   c.course_status AS courseStatus, c.syllabus AS syllabus
            FROM course c
            LEFT JOIN college co ON co.college_id = c.open_college
            LEFT JOIN course p ON p.course_id = c.pre_course_id
            WHERE 1 = 1
            <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') OR c.course_id LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="courseType != null and courseType != ''"> AND c.course_type = #{courseType}</if>
            <if test="collegeId != null and collegeId != ''"> AND c.open_college = #{collegeId}</if>
            <if test="status != null and status != ''"> AND c.course_status = #{status}</if>
            ORDER BY c.course_id
            </script>
            """)
    List<Map<String, Object>> listCourses(@Param("keyword") String keyword,
                                          @Param("courseType") String courseType,
                                          @Param("collegeId") String collegeId,
                                          @Param("status") String status);

    @Select("""
            <script>
            SELECT p.major_id AS majorId, m.major_name AS majorName, p.grade AS grade,
                   p.program_type AS programType, p.total_credit AS totalCredit, p.total_hours AS totalHours,
                   p.goal_desc AS goalDesc, p.require_desc AS requireDesc,
                   p.graduate_desc AS graduateDesc, p.plan_desc AS planDesc,
                   p.source_plan_no AS sourcePlanNo,
                   p.version AS version, p.plan_status AS planStatus, p.dual_quota AS dualQuota,
                   p.review_opinion AS reviewOpinion, p.publish_date AS publishDate,
                   m.college_id AS collegeId, c.college_name AS collegeName,
                   (SELECT COUNT(*) FROM program_course pc
                     WHERE pc.major_id = p.major_id AND pc.grade = p.grade AND pc.program_type = p.program_type) AS courseCount,
                   (SELECT IFNULL(SUM(pc.credit), 0) FROM program_course pc
                     WHERE pc.major_id = p.major_id AND pc.grade = p.grade AND pc.program_type = p.program_type) AS sumCredit
            FROM major_program p
            JOIN major m ON m.major_id = p.major_id
            LEFT JOIN college c ON c.college_id = m.college_id
            WHERE 1 = 1
            <if test="collegeId != null and collegeId != ''"> AND m.college_id = #{collegeId}</if>
            <if test="majorId != null and majorId != ''"> AND p.major_id = #{majorId}</if>
            <if test="planStatus != null and planStatus != ''"> AND p.plan_status = #{planStatus}</if>
            <if test="keyword != null and keyword != ''"> AND m.major_name LIKE CONCAT('%', #{keyword}, '%')</if>
            ORDER BY p.grade DESC, p.major_id
            </script>
            """)
    List<Map<String, Object>> listPrograms(@Param("collegeId") String collegeId,
                                           @Param("majorId") String majorId,
                                           @Param("planStatus") String planStatus,
                                           @Param("keyword") String keyword);

    @Select("""
            SELECT pc.major_id AS majorId, pc.grade AS grade, pc.program_type AS programType,
                   pc.course_id AS courseId, c.course_name AS courseName, pc.course_type AS courseType,
                   pc.course_attribute AS courseAttribute, pc.assess_type AS assessType,
                   pc.source_course_no AS sourceCourseNo,
                   pc.credit AS credit, pc.hours AS hours, pc.open_semester AS openSemester,
                   pc.pre_course_id AS preCourseId, p.course_name AS preCourseName,
                   pc.cross_status AS crossStatus, c.open_college AS openCollege,
                   co.college_name AS openCollegeName
            FROM program_course pc
            JOIN course c ON c.course_id = pc.course_id
            LEFT JOIN course p ON p.course_id = pc.pre_course_id
            LEFT JOIN college co ON co.college_id = c.open_college
            WHERE pc.major_id = #{majorId} AND pc.grade = #{grade} AND pc.program_type = #{programType}
            ORDER BY pc.open_semester, pc.course_id
            """)
    List<Map<String, Object>> listProgramCourses(@Param("majorId") String majorId,
                                                 @Param("grade") String grade,
                                                 @Param("programType") String programType);

    @Select("""
            <script>
            SELECT g.school_year AS schoolYear, g.course_id AS courseId, c.course_name AS courseName,
                   c.credit AS credit, g.plan_category AS planCategory, g.suggest_hours AS suggestHours,
                   g.undertake_college AS undertakeCollege, co.college_name AS collegeName,
                   g.teacher_id AS teacherId, t.teacher_name AS teacherName,
                   g.confirm_status AS confirmStatus, g.plan_status AS planStatus
            FROM general_plan g
            JOIN course c ON c.course_id = g.course_id
            LEFT JOIN college co ON co.college_id = g.undertake_college
            LEFT JOIN teacher t ON t.teacher_id = g.teacher_id
            WHERE 1 = 1
            <if test="schoolYear != null and schoolYear != ''"> AND g.school_year = #{schoolYear}</if>
            <if test="collegeId != null and collegeId != ''"> AND g.undertake_college = #{collegeId}</if>
            ORDER BY g.school_year DESC, g.course_id
            </script>
            """)
    List<Map<String, Object>> listGeneralPlans(@Param("schoolYear") String schoolYear,
                                               @Param("collegeId") String collegeId);

    @Select("""
            <script>
            SELECT a.apply_id AS applyId, a.apply_user AS applyUser,
                   COALESCE(u.real_name, t.teacher_name) AS teacherName,
                   u.college_id AS collegeId, a.course_name AS courseName, a.course_type AS courseType,
                   a.dispatch_college AS dispatchCollege, a.apply_reason AS applyReason,
                   a.credit AS credit, a.hours AS hours,
                   a.college_opinion AS collegeOpinion, a.audit_status AS auditStatus,
                   a.course_id AS courseId, a.audit_opinion AS auditOpinion,
                   a.apply_time AS applyTime, a.audit_time AS auditTime
            FROM new_course_apply a
            LEFT JOIN sys_user u ON u.user_id = a.apply_user
            LEFT JOIN teacher t ON t.teacher_id = u.teacher_id
            WHERE 1 = 1
            <if test="status != null and status != ''"> AND a.audit_status = #{status}</if>
            <if test="applyUser != null and applyUser != ''"> AND a.apply_user = #{applyUser}</if>
            <if test="collegeId != null and collegeId != ''">
                AND (u.college_id = #{collegeId} OR a.dispatch_college = #{collegeId})
            </if>
            ORDER BY a.apply_time DESC
            </script>
            """)
    List<Map<String, Object>> listNewCourseApplies(@Param("status") String status,
                                                   @Param("applyUser") String applyUser,
                                                   @Param("collegeId") String collegeId);

    @Select("""
            <script>
            SELECT tc.tc_id AS tcId, tc.term AS term, tc.course_id AS courseId, c.course_name AS courseName,
                   c.credit AS credit, c.course_type AS courseType, tc.teacher_id AS teacherId,
                   t.teacher_name AS teacherName, tc.class_ids AS classIds, tc.student_num AS studentNum,
                   tc.capacity AS capacity, tc.tc_type AS tcType, tc.teach_type AS teachType,
                   tc.hours AS hours,
                   COALESCE(tc.theory_hours, c.theory_hours, tc.hours, 0) AS theoryHours,
                   COALESCE(tc.practice_hours, c.practice_hours, 0) AS practiceHours,
                   tc.week_count AS weekCount, tc.week_start AS weekStart, tc.prefer_slot AS preferSlot,
                   tc.unavailable_time AS unavailableTime, tc.tc_status AS tcStatus, tc.source AS source,
                   tc.actual_num AS actualNum, tc.open_lower AS openLower, tc.no_open_reason AS noOpenReason,
                   c.open_college AS collegeId, co.college_name AS collegeName,
                   (SELECT COUNT(*) FROM schedule_result sr WHERE sr.tc_id = tc.tc_id) AS schedCount
            FROM teaching_class tc
            JOIN course c ON c.course_id = tc.course_id
            LEFT JOIN teacher t ON t.teacher_id = tc.teacher_id
            LEFT JOIN college co ON co.college_id = c.open_college
            WHERE 1 = 1
            <if test="term != null and term != ''"> AND tc.term = #{term}</if>
            <if test="keyword != null and keyword != ''">
                AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') OR tc.tc_id LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="tcStatus != null and tcStatus != ''"> AND tc.tc_status = #{tcStatus}</if>
            <if test="teacherId != null and teacherId != ''"> AND tc.teacher_id = #{teacherId}</if>
            <if test="collegeId != null and collegeId != ''"> AND c.open_college = #{collegeId}</if>
            <if test="classId != null and classId != ''"> AND tc.class_ids LIKE CONCAT('%', #{classId}, '%')</if>
            ORDER BY tc.tc_id
            </script>
            """)
    List<Map<String, Object>> listTeachingClasses(@Param("term") String term,
                                                  @Param("keyword") String keyword,
                                                  @Param("tcStatus") String tcStatus,
                                                  @Param("teacherId") String teacherId,
                                                  @Param("collegeId") String collegeId,
                                                  @Param("classId") String classId);

    @Select("""
            <script>
            SELECT sr.sched_id AS schedId, sr.tc_id AS tcId, tc.term AS term, c.course_name AS courseName,
                   c.credit AS credit, tc.teacher_id AS teacherId, t.teacher_name AS teacherName,
                   tc.class_ids AS classIds, tc.student_num AS studentNum, tc.tc_type AS tcType,
                   sr.room_id AS roomId, r.room_name AS roomName, r.capacity AS roomCapacity,
                   sr.week_range AS weekRange, sr.week_day AS weekDay, sr.start_slot AS startSlot,
                   sr.end_slot AS endSlot, sr.sched_status AS schedStatus, sr.lock_flag AS lockFlag,
                   sr.conflict_desc AS conflictDesc
            FROM schedule_result sr
            JOIN teaching_class tc ON tc.tc_id = sr.tc_id
            JOIN course c ON c.course_id = tc.course_id
            LEFT JOIN teacher t ON t.teacher_id = tc.teacher_id
            LEFT JOIN classroom r ON r.room_id = sr.room_id
            WHERE 1 = 1
            <if test="term != null and term != ''"> AND tc.term = #{term}</if>
            <if test="tcId != null and tcId != ''"> AND sr.tc_id = #{tcId}</if>
            <if test="schedStatus != null and schedStatus != ''"> AND sr.sched_status = #{schedStatus}</if>
            ORDER BY sr.sched_id
            </script>
            """)
    List<Map<String, Object>> listScheduleResults(@Param("term") String term,
                                                  @Param("tcId") String tcId,
                                                  @Param("schedStatus") String schedStatus);

    @Select("""
            <script>
            SELECT cf.conflict_id AS conflictId, cf.tc_id AS tcId, c.course_name AS courseName,
                   cf.other_tc_id AS otherTcId, c2.course_name AS otherCourseName,
                   cf.conflict_type AS conflictType, cf.conflict_desc AS conflictDesc,
                   cf.suggest_plan AS suggestPlan, cf.handle_status AS handleStatus,
                   cf.found_time AS foundTime, tc.term AS term
            FROM schedule_conflict cf
            JOIN teaching_class tc ON tc.tc_id = cf.tc_id
            JOIN course c ON c.course_id = tc.course_id
            LEFT JOIN teaching_class tc2 ON tc2.tc_id = cf.other_tc_id
            LEFT JOIN course c2 ON c2.course_id = tc2.course_id
            WHERE 1 = 1
            <if test="term != null and term != ''"> AND tc.term = #{term}</if>
            <if test="handleStatus != null and handleStatus != ''"> AND cf.handle_status = #{handleStatus}</if>
            ORDER BY cf.handle_status, cf.found_time DESC
            </script>
            """)
    List<Map<String, Object>> listConflicts(@Param("term") String term, @Param("handleStatus") String handleStatus);

    @Select("""
            <script>
            SELECT a.adjust_id AS adjustId, a.sched_id AS schedId, sr.tc_id AS tcId,
                   c.course_name AS courseName, c.open_college AS collegeId, tc.teacher_id AS teacherId,
                   t.teacher_name AS teacherName, tc.class_ids AS classIds,
                   a.adjust_type AS adjustType, a.old_value AS oldValue, a.new_value AS newValue,
                   a.reason AS reason, a.effect_date AS effectDate, a.approve_status AS approveStatus,
                   a.approver AS approver, a.approve_opinion AS approveOpinion,
                   a.notify_status AS notifyStatus, a.restore_flag AS restoreFlag, a.create_time AS createTime
            FROM adjust_record a
            JOIN schedule_result sr ON sr.sched_id = a.sched_id
            JOIN teaching_class tc ON tc.tc_id = sr.tc_id
            JOIN course c ON c.course_id = tc.course_id
            LEFT JOIN teacher t ON t.teacher_id = tc.teacher_id
            WHERE 1 = 1
            <if test="term != null and term != ''"> AND tc.term = #{term}</if>
            <if test="approveStatus != null and approveStatus != ''"> AND a.approve_status = #{approveStatus}</if>
            <if test="teacherId != null and teacherId != ''"> AND tc.teacher_id = #{teacherId}</if>
            ORDER BY a.create_time DESC
            </script>
            """)
    List<Map<String, Object>> listAdjustments(@Param("term") String term,
                                              @Param("approveStatus") String approveStatus,
                                              @Param("teacherId") String teacherId);

    @Select("""
            <script>
            SELECT r.rec_id AS recId, r.stu_id AS stuId, r.program_type AS programType,
                   r.major_id AS majorId, m.major_name AS majorName, r.course_id AS courseId,
                   c.course_name AS courseName, c.credit AS courseCredit, r.tc_id AS tcId,
                   r.study_type AS studyType, r.credit AS credit, r.conflict_type AS conflictType,
                   r.audit_status AS auditStatus, r.remark AS remark
            FROM study_record r
            JOIN course c ON c.course_id = r.course_id
            LEFT JOIN major m ON m.major_id = r.major_id
            WHERE 1 = 1
            <if test="programType != null and programType != ''"> AND r.program_type = #{programType}</if>
            <if test="stuId != null and stuId != ''"> AND r.stu_id = #{stuId}</if>
            <if test="majorId != null and majorId != ''"> AND r.major_id = #{majorId}</if>
            ORDER BY r.rec_id
            </script>
            """)
    List<Map<String, Object>> listStudyRecords(@Param("programType") String programType,
                                               @Param("stuId") String stuId,
                                               @Param("majorId") String majorId);

    @Select("""
            <script>
            SELECT a.apply_id AS applyId, a.stu_id AS stuId, a.from_major AS fromMajor,
                   m1.major_name AS fromMajorName, a.to_major AS toMajor, m2.major_name AS toMajorName,
                   a.apply_date AS applyDate, a.apply_credit AS applyCredit,
                   a.college_opinion AS collegeOpinion, a.audit_status AS auditStatus,
                   a.approver AS approver, a.audit_opinion AS auditOpinion, a.audit_date AS auditDate
            FROM credit_apply a
            LEFT JOIN major m1 ON m1.major_id = a.from_major
            LEFT JOIN major m2 ON m2.major_id = a.to_major
            WHERE 1 = 1
            <if test="stuId != null and stuId != ''"> AND a.stu_id = #{stuId}</if>
            <if test="auditStatus != null and auditStatus != ''"> AND a.audit_status = #{auditStatus}</if>
            ORDER BY a.apply_date DESC
            </script>
            """)
    List<Map<String, Object>> listCreditApplies(@Param("stuId") String stuId,
                                                @Param("auditStatus") String auditStatus);

    @Select("""
            SELECT i.apply_id AS applyId, i.seq_no AS seqNo, i.source_course_id AS sourceCourseId,
                   c1.course_name AS sourceCourseName, i.source_credit AS sourceCredit,
                   i.target_course_id AS targetCourseId, c2.course_name AS targetCourseName,
                   i.target_credit AS targetCredit, i.transfer_type AS transferType, i.item_status AS itemStatus
            FROM credit_item i
            JOIN course c1 ON c1.course_id = i.source_course_id
            LEFT JOIN course c2 ON c2.course_id = i.target_course_id
            WHERE i.apply_id = #{applyId}
            ORDER BY i.seq_no
            """)
    List<Map<String, Object>> listCreditItems(@Param("applyId") String applyId);

    @Select("""
            <script>
            SELECT e.enroll_id AS enrollId, e.stu_id AS stuId, e.major_id AS majorId,
                   m.major_name AS majorName, e.enroll_year AS enrollYear, e.apply_date AS applyDate,
                   e.score_avg AS scoreAvg, e.audit_status AS auditStatus, e.finish_status AS finishStatus,
                   e.enroll_date AS enrollDate, e.remark AS remark
            FROM dual_enroll e
            LEFT JOIN major m ON m.major_id = e.major_id
            WHERE 1 = 1
            <if test="majorId != null and majorId != ''"> AND e.major_id = #{majorId}</if>
            <if test="stuId != null and stuId != ''"> AND e.stu_id = #{stuId}</if>
            <if test="auditStatus != null and auditStatus != ''"> AND e.audit_status = #{auditStatus}</if>
            ORDER BY e.apply_date DESC
            </script>
            """)
    List<Map<String, Object>> listDualEnrolls(@Param("majorId") String majorId,
                                              @Param("stuId") String stuId,
                                              @Param("auditStatus") String auditStatus);

    @Select("""
            <script>
            SELECT r.record_id AS recordId, r.plan_id AS planId, r.tc_id AS tcId,
                   c.course_name AS courseName, c.credit AS credit, t.teacher_name AS teacherName,
                   co.college_id AS collegeId, co.college_name AS collegeName,
                   r.stu_id AS stuId, r.score AS score, r.item_score AS itemScore,
                   r.comment AS comment, r.valid_flag AS validFlag, r.submit_time AS submitTime
            FROM eval_record r
            JOIN teaching_class tc ON tc.tc_id = r.tc_id
            JOIN course c ON c.course_id = tc.course_id
            LEFT JOIN teacher t ON t.teacher_id = tc.teacher_id
            LEFT JOIN college co ON co.college_id = c.open_college
            WHERE r.plan_id = #{planId}
            <if test="tcId != null and tcId != ''"> AND r.tc_id = #{tcId}</if>
            ORDER BY r.tc_id, r.record_id
            </script>
            """)
    List<Map<String, Object>> listEvalRecords(@Param("planId") String planId, @Param("tcId") String tcId);

    @Select("""
            <script>
            SELECT DISTINCT v.term AS term, v.tc_id AS tcId, v.course_id AS courseId,
                   v.course_name AS courseName, v.credit AS credit, v.teacher_id AS teacherId,
                   v.teacher_name AS teacherName, v.class_ids AS classIds, v.student_num AS studentNum,
                   v.week_range AS weekRange, v.week_day AS weekDay, v.start_slot AS startSlot,
                   v.end_slot AS endSlot, v.room_id AS roomId, v.room_name AS roomName,
                   v.room_type AS roomType, v.sched_status AS schedStatus, v.sched_id AS schedId
            FROM v_full_schedule v
            JOIN course c ON c.course_id = v.course_id
            WHERE v.term = #{term}
              AND v.sched_status IN ('2', '3')
            <if test="teacherId != null and teacherId != ''"> AND v.teacher_id = #{teacherId}</if>
            <if test="classId != null and classId != ''"> AND v.class_ids LIKE CONCAT('%', #{classId}, '%')</if>
            <if test="roomId != null and roomId != ''"> AND v.room_id = #{roomId}</if>
            <if test="openCollege != null and openCollege != ''"> AND c.open_college = #{openCollege}</if>
            ORDER BY v.week_day, v.start_slot
            </script>
            """)
    List<Map<String, Object>> timetable(@Param("term") String term,
                                       @Param("teacherId") String teacherId,
                                       @Param("classId") String classId,
                                       @Param("roomId") String roomId,
                                       @Param("openCollege") String openCollege);
}
