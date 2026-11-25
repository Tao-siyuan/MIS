<template>
  <div>
    <h2 class="page-title">教学班管理</h2>
    <p class="page-desc">
      必修课：教务处选择开课学院与方案内待编排课程、匹配面向班级，学院教务分配教师、教师确认承接后进入排课。
      选修课：教务处选择开课学院与本学期尚未建班的课程后直接下发给教师，教师设定容量并提交，教务处审核通过后进入排课。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="term" style="width: 150px" @change="load">
          <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="tcStatus" placeholder="教学班状态" clearable style="width: 150px" @change="load">
          <el-option label="待分配教师" value="0" />
          <el-option label="待教师确认" value="5" />
          <el-option label="待教师承接(选修)" value="6" />
          <el-option label="待审核(选修)" value="7" />
          <el-option label="已承接" value="1" />
          <el-option label="不开课" value="2" />
          <el-option label="已排课" value="3" />
          <el-option label="已发布" value="4" />
        </el-select>
        <el-input v-model="keyword" placeholder="课程名称 / 教学班号" clearable style="width: 210px" @change="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <span class="spacer" />
        <el-button v-if="canCreate" @click="openBatch">批量生成</el-button>
        <el-button v-if="canCreate" type="primary" @click="openEdit()">新增教学班</el-button>
      </div>

      <el-table :data="pageRows" border size="small">
        <el-table-column prop="tcId" label="教学班号" width="120" />
        <el-table-column prop="courseName" label="课程" min-width="170" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="任课教师" width="100">
          <template #default="{ row }">
            <span v-if="row.teacherName">{{ row.teacherName }}</span>
            <el-tag v-else size="small" type="danger" effect="plain">未落实</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面向班级" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.tcType !== '2'">{{ row.classIds }}</span>
            <span v-else class="muted">选修课不限定班级</span>
          </template>
        </el-table-column>
        <el-table-column prop="studentNum" label="面向人数" width="90" />
        <el-table-column label="容量" width="70">
          <template #default="{ row }">{{ row.capacity > 0 ? row.capacity : '待定' }}</template>
        </el-table-column>
        <el-table-column label="形式" width="80">
          <template #default="{ row }">{{ teachTypeName(row.teachType) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ tcTypeName(row.tcType) }}</template>
        </el-table-column>
        <el-table-column label="学时（总/理/实）" width="130">
          <template #default="{ row }">
            {{ row.hours || 0 }} / {{ row.theoryHours || 0 }} / {{ row.practiceHours || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="schedCount" label="已排段数" width="90" />
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.tcStatus)">{{ statusName(row.tcStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canManageRoster(row) && row.tcType !== '2'"
              link
              type="primary"
              size="small"
              @click="openRoster(row)"
            >学生名单</el-button>
            <el-button v-if="canAssignTeacher(row)" link type="success" size="small" @click="openAssign(row)">分配教师</el-button>
            <el-button v-if="canConfirm(row)" link type="success" size="small" @click="confirmTc(row)">确认承接</el-button>
            <el-button v-if="canConfirm(row)" link type="danger" size="small" @click="declineTc(row)">拒绝</el-button>
            <el-button v-if="canClaim(row)" link type="success" size="small" @click="openClaim(row)">承接并设容量</el-button>
            <el-button v-if="canReview(row)" link type="success" size="small" @click="reviewTc(row, true)">审核通过</el-button>
            <el-button v-if="canReview(row)" link type="danger" size="small" @click="reviewTc(row, false)">驳回</el-button>
            <el-button
              v-if="canMaintain && ['0', '5', '6', '7', '1', '3'].includes(row.tcStatus)"
              link
              type="warning"
              size="small"
              @click="cancel(row)"
            >停开</el-button>
            <el-button v-if="canMaintain && row.schedCount === 0" link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 30, 50]"
        :total="rows.length"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 14px; justify-content: flex-end"
      />
    </div>

    <el-dialog v-model="batchDialog" title="按培养方案批量生成教学班" width="520px">
      <el-form :model="batch" label-width="100px">
        <el-form-item label="学期">
          <el-input v-model="batch.term" />
        </el-form-item>
        <el-form-item label="专业" required>
          <el-select v-model="batch.majorId" filterable style="width: 100%" @change="onMajorChange">
            <el-option v-for="m in majors" :key="m.majorId" :label="`${m.majorName}（${m.majorId}）`" :value="m.majorId" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用年级" required>
          <el-select v-model="batch.grade" style="width: 100%">
            <el-option v-for="g in grades" :key="g" :label="g + ' 级'" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="方案类型">
          <el-radio-group v-model="batch.programType">
            <el-radio-button value="1">主修</el-radio-button>
            <el-radio-button value="2">双学位</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="仅已发布的培养方案可生成教学班；生成的班级统一为待分配教师状态，由学院教务指派教师。"
        />
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="doBatch">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog" :title="form.isNew ? '新增教学班' : '编辑教学班'" width="580px">
      <el-form :model="form" label-width="100px">
        <el-alert
          v-if="!canMaintain"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          title="教师仅可维护本人教学班的理论/实践学时、偏好节次、不可用时间与学生名单，其余字段由教务处/学院教务员维护。"
        />
        <el-form-item v-if="form.isNew" label="课程类别" required>
          <el-radio-group v-model="form.tcType" @change="onTcTypeChange">
            <el-radio-button value="1">必修课</el-radio-button>
            <el-radio-button value="2">选修课</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学期" required>
          <el-input v-model="form.term" :disabled="!form.isNew" @change="onTermChange" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开课学院" required>
              <el-select v-model="form.openCollege" filterable :disabled="!form.isNew" style="width: 100%" @change="onOpenCollegeChange">
                <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上课学院" required>
              <el-select v-model="form.studyColleges" multiple filterable :disabled="!form.isNew" style="width: 100%">
                <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程" required>
          <el-select
            v-model="form.courseId"
            filterable
            :disabled="!form.isNew"
            style="width: 100%"
            :placeholder="coursePlaceholder"
            @change="onCoursePick"
          >
            <el-option
              v-for="c in filteredCourses"
              :key="c.courseId"
              :label="`${c.courseName}（${c.courseId}${form.isNew && form.tcType === '1' ? `，待编排 ${c.unassignedClassCount || 0} 个班` : ''}）`"
              :value="c.courseId"
            />
          </el-select>
          <div v-if="form.isNew" class="muted" style="line-height: 1.6">
            <template v-if="form.tcType === '1'">
              仅列出本学院开设、在用培养方案中本学期开课、且仍有班级未编排教学班的课程。
            </template>
            <template v-else>
              选修课列出本学院本学期尚未建立教学班的课程，不限定面向班级，建班后下发教师承接。
            </template>
          </div>
        </el-form-item>
        <el-form-item label="任课教师">
          <span class="muted" style="line-height: 32px">
            {{ form.teacherName || (form.tcType === '2' ? '由教师承接后确定' : '待学院教务分配') }}
          </span>
        </el-form-item>
        <el-form-item v-if="form.tcType !== '2'" label="面向班级">
          <el-select
            v-model="classIds"
            multiple
            filterable
            style="width: 100%"
            :disabled="!canMaintain"
            :placeholder="classPlaceholder"
            @change="onClassChange"
          >
            <el-option v-for="c in filteredClasses" :key="c.classId" :label="`${c.className}（${c.studentNum}人）`" :value="c.classId" />
          </el-select>
          <div v-if="form.isNew && form.courseId" class="muted" style="line-height: 1.6">
            仅列出培养方案中该学期需修读本课程、且尚未编排教学班的班级。
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="面向人数">
              <el-input-number v-model="form.studentNum" :min="0" :max="300" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="容量上限">
              <span class="muted">{{ form.capacity > 0 ? form.capacity + ' 人' : '由排课教室决定' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="教学形式">
              <el-select v-model="form.teachType" :disabled="!canMaintain" style="width: 100%">
                <el-option label="讲授" value="1" />
                <el-option label="实验" value="2" />
                <el-option label="上机" value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="教学班类型">
              <el-select v-model="form.tcType" :disabled="!canMaintain" style="width: 100%">
                <el-option label="必修" value="1" />
                <el-option label="选修" value="2" />
                <el-option label="双学位/辅修" value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="计划学时">
              <span class="muted" style="line-height: 32px">{{ planHours }} 学时</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="理论学时">
              <el-input-number
                v-if="canEditHours"
                v-model="form.theoryHours"
                :min="0"
                :max="planHours || 300"
                @change="onTheoryChange"
              />
              <div v-else class="hours-box">{{ displayTheoryHours }}</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实践学时">
              <el-input-number
                v-if="canEditHours"
                v-model="form.practiceHours"
                :min="0"
                :max="planHours || 300"
                :step="4"
                @change="onPracticeChange"
              />
              <div v-else class="hours-box">{{ displayPracticeHours }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="displayPracticeHours > 0" label="实践场地">
          <span class="muted" style="line-height: 32px">{{ practiceRoomTypeName }}</span>
        </el-form-item>
        <el-alert
          v-if="form.isNew"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          title="学时划分沿用课程库设置；建班后由学院教务分配教师，任课教师可在本人教学班内调整理论/实践学时。"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          :title="canEditHours
            ? '理论/实践学时由任课教师划分：出现实践学时时不少于 8 学时，且按 4 学时递增。'
            : '理论/实践学时由该教学班任课教师划分，教务处/学院教务员不作调整。'"
        />
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="上课周数">
              <el-select v-model="form.weekCount" :disabled="!weekEditable" style="width: 100%">
                <el-option label="16 周（第 1-16 周）" :value="16" />
                <el-option label="8 周" :value="8" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.weekCount === 8" :span="12">
            <el-form-item label="周次段">
              <el-select v-model="form.weekStart" :disabled="!weekEditable" style="width: 100%">
                <el-option label="前 8 周（第 1-8 周）" :value="1" />
                <el-option label="后 8 周（第 9-16 周）" :value="9" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="偏好节次">
              <el-input v-model="form.preferSlot" :disabled="false" placeholder="如 1-2 节" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不可用时间">
              <el-input v-model="form.unavailableTime" :disabled="false" placeholder="如 周三下午" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 教师承接选修课并设定课程容量 -->
    <el-dialog v-model="claimDialog" title="承接选修课" width="460px">
      <el-form label-width="90px">
        <el-form-item label="课程">{{ claimRow?.courseName }}（{{ claimRow?.courseId }}）</el-form-item>
        <el-form-item label="教学班号">{{ claimRow?.tcId }}</el-form-item>
        <el-form-item label="课程容量" required>
          <el-input-number v-model="claimCapacity" :min="1" :max="1000" :step="5" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="设定容量后提交教务处审核，审核通过后由教务处统一排课。"
        />
      </el-form>
      <template #footer>
        <el-button @click="claimDialog = false">取消</el-button>
        <el-button type="primary" :loading="claiming" @click="doClaim">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 学院教务分配任课教师 -->
    <el-dialog v-model="assignDialog" title="分配任课教师" width="480px">
      <el-form label-width="90px">
        <el-form-item label="教学班">{{ assignRow?.courseName }}（{{ assignRow?.tcId }}）</el-form-item>
        <el-form-item label="面向班级">{{ assignRow?.classIds }}</el-form-item>
        <el-form-item label="开课学院">{{ assignRow?.collegeName }}</el-form-item>
        <el-form-item label="任课教师" required>
          <el-select v-model="assignTeacherId" filterable style="width: 100%" placeholder="请选择本学院在职教师">
            <el-option
              v-for="t in assignTeachers"
              :key="t.teacherId"
              :label="`${t.teacherName}（${t.userId || t.teacherId}）`"
              :value="t.teacherId"
            />
          </el-select>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="分配后由任课教师确认承接，确认完成后教学班方可进入排课。"
        />
      </el-form>
      <template #footer>
        <el-button @click="assignDialog = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="doAssign">确定分配</el-button>
      </template>
    </el-dialog>

    <!-- 学生名单：任课教师可在本人教学班内添加/移除学生 -->
    <el-dialog v-model="rosterDialog" :title="`学生名单 · ${rosterTc?.courseName || ''}`" width="640px">
      <el-form inline style="margin-bottom: 8px" @submit.prevent>
        <el-form-item label="学号">
          <el-input
            v-model="addForm.stuId"
            placeholder="如 U202341224"
            clearable
            style="width: 170px"
            @blur="fillStuName"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="addForm.stuName" placeholder="用于核对" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="adding" :disabled="!addForm.stuId.trim()" @click="addStudent">
            添加学生
          </el-button>
        </el-form-item>
        <el-form-item>
          <span class="muted">名单 {{ roster.length }} 人 · 容量 {{ rosterTc?.capacity ?? '-' }}</span>
        </el-form-item>
      </el-form>
      <el-table :data="roster" border size="small" max-height="360">
        <el-table-column prop="stuId" label="学号" width="130" />
        <el-table-column prop="stuName" label="姓名" width="110" />
        <el-table-column prop="className" label="班级" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeStudent(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="rosterDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { userStore } from '../store/user'

const user = userStore()
const page = ref(1)
const size = ref(20)
const pageRows = computed(() => {
  const start = (page.value - 1) * size.value
  return rows.value.slice(start, start + size.value)
})
watch(size, () => { page.value = 1 })
/** 教学班维护（停开/删除/编辑）仅授予 P19；建班由教务处完成（数据范围 1） */
const canMaintain = computed(() => user.hasPerm('P19'))
const canCreate = computed(() => canMaintain.value && user.user?.dataScope === '1')
const rows = ref([])
const terms = ref([])
const majors = ref([])
const courses = ref([])
const classList = ref([])
/** 培养方案中该学期需修读所选课程、且尚未编排教学班的候选班级 */
const planClasses = ref([])
/** 教务处建班候选课程（必修）：本学院开设、在用方案本学期开课、仍有未编排班级 */
const planCourses = ref([])
/** 选修课候选课程：本学院本学期尚未建立教学班的课程 */
const electiveCourses = ref([])
const colleges = ref([])
const filteredCourses = computed(() => {
  const enabled = courses.value.filter((c) => c.courseStatus === '1')
  if (!form.isNew) return enabled
  return form.tcType === '2' ? electiveCourses.value : planCourses.value
})
const coursePlaceholder = computed(() => {
  if (!form.isNew) return ''
  if (!form.openCollege) return '请先选择开课学院'
  return filteredCourses.value.length ? '请选择课程' : '该学院本学期暂无可建班课程'
})
const filteredClasses = computed(() => {
  // 新建教学班：只展示培养方案中该学期需修读该课程、且尚未编排教学班的班级
  if (form.isNew) return form.courseId ? planClasses.value : []
  const selected = form.studyColleges || []
  if (!selected.length) return classList.value
  const ids = new Set(
    majors.value.filter((m) => selected.includes(m.collegeId)).map((m) => m.majorId)
  )
  return classList.value.filter((c) => ids.has(c.majorId))
})
const classIndex = computed(() => {
  const map = new Map()
  ;[...classList.value, ...planClasses.value].forEach((c) => map.set(c.classId, c))
  return map
})
const classPlaceholder = computed(() => {
  if (!form.isNew) return '请选择班级'
  if (!form.courseId) return '请先选择课程'
  return filteredClasses.value.length ? '请选择班级' : '该学期该课程已全部编排'
})
/** 课程库设定的学时划分，用于教学班表单展示与回填 */
const courseHours = computed(() => {
  const c = courses.value.find((x) => x.courseId === form.courseId)
  if (!c) return null
  const total = Number(c.totalHours) || 0
  const theory = c.theoryHours === null || c.theoryHours === undefined ? total : Number(c.theoryHours)
  const practice = Number(c.practiceHours) || 0
  return { total, theory, practice, practiceRoomType: c.practiceRoomType || '' }
})

/** 实践教室类型名称 */
const roomTypeNames = {
  41: '机房',
  42: '物理实验室',
  43: '化学实验室',
  44: '生物实验室',
  45: '操场',
  46: '金工实习场地',
  47: '其他实践场地'
}
const practiceRoomTypeName = computed(() => {
  const type = form.practiceRoomType || courseHours.value?.practiceRoomType
  return type ? roomTypeNames[type] || '由排课教室决定' : '由排课教室决定'
})
/** 计划总学时：优先取教学班计划学时，缺失时用理论+实践之和兜底，保证界面一定有数值 */
const planHours = computed(() => {
  const h = Number(form.hours)
  if (Number.isFinite(h) && h > 0) return h
  return (form.theoryHours || 0) + (form.practiceHours || 0)
})
/** 理论/实践学时划分由该教学班的任课教师完成（P19 教务人员只编排，不改学时） */
const canEditHours = computed(() => !form.isNew && !canMaintain.value)
/** 上课周数：教务（P19）可设；选修课由任课教师自选；必修课由开课学院教务设定 */
const weekEditable = computed(() => canMaintain.value || (canEditHours.value && form.tcType === '2'))


const term = ref('2026-2027-1')
const tcStatus = ref('')
const keyword = ref('')

const batchDialog = ref(false)
const batchLoading = ref(false)
const batch = reactive({ term: '2026-2027-1', programType: '1' })
const editDialog = ref(false)
const form = reactive({})
const classIds = ref([])
const rosterDialog = ref(false)
const rosterTc = ref(null)
const roster = ref([])
const addForm = reactive({ stuId: '', stuName: '' })
const adding = ref(false)

// 教务编排/查看时，把课程库的理论、实践学时直接填入输入框（教师自行划分时不覆盖）
watch(
  () => [
    form.courseId,
    form.hours,
    courseHours.value?.total,
    courseHours.value?.theory,
    courseHours.value?.practice
  ],
  () => {
    if (canEditHours.value) return
    const course = courseHours.value
    if (!form.courseId || !course) return
    const total = Number(form.hours) || course.total
    if (!total) return
    if ((form.theoryHours || 0) + (form.practiceHours || 0) === total) return
    form.hours = total
    if (course.theory + course.practice === total) {
      form.theoryHours = course.theory
      form.practiceHours = course.practice
    } else {
      form.theoryHours = total
      form.practiceHours = 0
    }
    form.practiceRoomType = form.practiceRoomType || course.practiceRoomType || ''
  }
)

/** 只读场景下展示的学时：教学班未划分或与计划学时不一致时展示课程库设置 */
const displayTheoryHours = computed(() => {
  const course = courseHours.value
  const theory = Number(form.theoryHours)
  const practice = Number(form.practiceHours) || 0
  const total = planHours.value
  if (course) {
    return !Number.isFinite(theory) || theory + practice !== total ? course.theory : theory
  }
  return Number.isFinite(theory) && theory + practice === total ? theory : total
})
const displayPracticeHours = computed(() => {
  const course = courseHours.value
  const theory = Number(form.theoryHours) || 0
  const practice = Number(form.practiceHours)
  const total = planHours.value
  if (course) {
    return !Number.isFinite(practice) || theory + practice !== total ? course.practice : practice
  }
  return Number.isFinite(practice) && theory + practice === total ? practice : 0
})

/** 学生名单维护：教务处/学院教务员（P19）或本人任教的教学班 */
const canManageRoster = (row) =>
  canMaintain.value || (!!user.user?.teacherId && user.user.teacherId === row.teacherId)

const assignDialog = ref(false)
const assignRow = ref(null)
const assignTeacherId = ref('')
const assignTeachers = ref([])
const assigning = ref(false)

const isMyClass = (row) => !!user.user?.teacherId && user.user.teacherId === row.teacherId
/** 教师确认承接：待教师确认且为本人教学班（兼容历史待确认数据） */
const canConfirm = (row) => isMyClass(row) && ['0', '5'].includes(row.tcStatus)
/** 学院教务分配教师：待分配教师状态，且为本学院开课教学班 */
const canAssignTeacher = (row) => {
  if (!user.hasPerm('P19') || row.tcStatus !== '0') return false
  if (user.user?.dataScope === '2') return row.collegeId === user.user.collegeId
  return user.user?.dataScope === '1'
}
/** 选修课：教师承接并设定容量（本学院开课） */
const canClaim = (row) =>
  row.tcType === '2' &&
  row.tcStatus === '6' &&
  user.user?.dataScope === '3' &&
  !!user.user?.teacherId &&
  (!user.user?.collegeId || row.collegeId === user.user.collegeId)
/** 选修课：教务处审核 */
const canReview = (row) =>
  row.tcType === '2' && row.tcStatus === '7' && user.user?.dataScope === '1' && user.hasPerm('P19')

const claimDialog = ref(false)
const claimRow = ref(null)
const claimCapacity = ref(40)
const claiming = ref(false)

async function openClaim(row) {
  claimRow.value = row
  claimCapacity.value = row.capacity > 0 ? row.capacity : 40
  claimDialog.value = true
}

async function doClaim() {
  if (!claimCapacity.value || claimCapacity.value <= 0) {
    ElMessage.warning('请设置课程容量')
    return
  }
  claiming.value = true
  try {
    await api.claimTc(claimRow.value.tcId, claimCapacity.value)
    ElMessage.success('已提交，等待教务处审核')
    claimDialog.value = false
    await load()
  } finally {
    claiming.value = false
  }
}

/** 教务处审核选修课教学班 */
async function reviewTc(row, pass) {
  if (pass) {
    await ElMessageBox.confirm(`审核通过《${row.courseName}》，通过后可进入排课？`, '审核通过', { type: 'info' })
    await api.reviewTc(row.tcId, true, '')
    ElMessage.success('审核通过，已进入待排课')
  } else {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回', { inputPlaceholder: '如容量设置不合理' })
    await api.reviewTc(row.tcId, false, value)
    ElMessage.success('已驳回，退回教师重新设定')
  }
  await load()
}

const grades = computed(() => {
  const set = new Set(classList.value.map((c) => c.grade))
  return Array.from(set).sort().reverse()
})

const statusName = (s) =>
  ({
    0: '待分配教师',
    5: '待教师确认',
    6: '待教师承接',
    7: '待教务处审核',
    1: '已承接',
    2: '不开课',
    3: '已排课',
    4: '已发布'
  }[s] || '-')
const statusType = (s) =>
  ({ 0: 'info', 5: 'warning', 6: 'warning', 7: 'warning', 1: 'primary', 2: 'danger', 3: 'warning', 4: 'success' }[s] ||
  'info')
const teachTypeName = (t) => ({ 1: '讲授', 2: '实验', 3: '上机' }[t] || t)
const tcTypeName = (t) => ({ 1: '必修', 2: '选修', 3: '双学位' }[t] || t)

async function load() {
  rows.value = await api.teachingClasses({
    term: term.value,
    tcStatus: tcStatus.value || undefined,
    keyword: keyword.value || undefined
  })
  page.value = 1
}

function openBatch() {
  Object.assign(batch, { term: term.value, programType: '1' })
  batchDialog.value = true
}

async function onMajorChange() {
  const major = majors.value.find((m) => m.majorId === batch.majorId)
  if (major) {
    classList.value = await api.classes({ majorId: major.majorId })
    batch.grade = classList.value[0]?.grade
  }
}

async function doBatch() {
  batchLoading.value = true
  try {
    const res = await api.batchGenerate({ ...batch })
    ElMessage.success(`已生成 ${res.created} 个教学班，跳过 ${res.skipped} 项`)
    if (res.messages?.length) {
      ElMessage.info(res.messages.join('；'))
    }
    batchDialog.value = false
    await load()
  } finally {
    batchLoading.value = false
  }
}

async function openEdit(row) {
  classList.value = await api.classes()
  planClasses.value = []
  Object.keys(form).forEach((k) => delete form[k])
  if (row) {
    Object.assign(form, { ...row })
    // 开课学院取课程的开课学院
    form.openCollege = row.openCollege || row.collegeId || ''
    // 学时统一转为数值，避免输入框显示为空
    form.hours = Number(form.hours) || 0
    form.theoryHours = Number(form.theoryHours) || 0
    form.practiceHours = Number(form.practiceHours) || 0
    const course = courses.value.find((c) => c.courseId === form.courseId)
    // 计划学时缺失时回填，保证界面一定展示总学时
    if (!form.hours) {
      form.hours = Number(course?.totalHours) || form.theoryHours + form.practiceHours
    }
    // 理论/实践学时缺失或与计划学时不一致时，沿用课程库的学时划分
    if (form.hours && form.theoryHours + form.practiceHours !== form.hours) {
      const courseTheory = Number(course?.theoryHours)
      const coursePractice = Number(course?.practiceHours)
      if (course && courseTheory + coursePractice === form.hours) {
        form.theoryHours = courseTheory
        form.practiceHours = coursePractice
      } else {
        form.theoryHours = form.hours
        form.practiceHours = 0
      }
    }
    // 实践场地取自课程库
    form.practiceRoomType = form.practiceRoomType || course?.practiceRoomType || ''
    // 上课周数：16 周或 8 周（8 周区分前/后 8 周）
    form.weekCount = Number(form.weekCount) || 16
    form.weekStart = Number(form.weekStart) || 1
    classIds.value = row.classIds ? row.classIds.split(',') : []
  } else {
    Object.assign(form, {
      isNew: true,
      term: term.value,
      openCollege: '',
      studyColleges: [],
      teachType: '1',
      tcType: '1',
      hours: 0,
      theoryHours: 0,
      practiceHours: 0,
      practiceRoomType: '',
      weekCount: 16,
      weekStart: 1,
      studentNum: 0
    })
    classIds.value = []
    planCourses.value = []
    electiveCourses.value = []
  }
  editDialog.value = true
}

async function onOpenCollegeChange() {
  form.courseId = ''
  form.hours = 0
  form.theoryHours = 0
  form.practiceHours = 0
  form.practiceRoomType = ''
  planClasses.value = []
  planCourses.value = []
  classIds.value = []
  form.classIds = ''
  form.studentNum = 0
  await loadPlanCourses()
}

/** 加载候选课程：必修取方案内待编排课程，选修取本学期尚无教学班的课程 */
async function loadPlanCourses() {
  planCourses.value = []
  electiveCourses.value = []
  if (!form.isNew || !form.openCollege || !form.term) return
  try {
    if (form.tcType === '2') {
      electiveCourses.value = await api.tcElectiveCandidates({ term: form.term, collegeId: form.openCollege })
    } else {
      planCourses.value = await api.tcCourseCandidates({ term: form.term, collegeId: form.openCollege })
    }
  } catch (e) {
    planCourses.value = []
    electiveCourses.value = []
  }
}

/** 切换必修/选修后重新加载候选课程 */
async function onTcTypeChange() {
  form.courseId = ''
  form.hours = 0
  form.theoryHours = 0
  form.practiceHours = 0
  form.practiceRoomType = ''
  classIds.value = []
  form.classIds = ''
  form.studentNum = 0
  planClasses.value = []
  await loadPlanCourses()
}

/** 加载培养方案中该学期需修读该课程、且尚未编排教学班的候选班级 */
async function loadPlanClasses() {
  planClasses.value = []
  if (!form.isNew || !form.courseId || !form.term) return
  try {
    planClasses.value = await api.tcClassCandidates({ term: form.term, courseId: form.courseId })
  } catch (e) {
    planClasses.value = []
  }
}

async function onTermChange() {
  if (!form.isNew) return
  form.courseId = ''
  form.hours = 0
  form.theoryHours = 0
  form.practiceHours = 0
  form.practiceRoomType = ''
  classIds.value = []
  form.classIds = ''
  form.studentNum = 0
  planClasses.value = []
  await loadPlanCourses()
}

async function onCoursePick(id) {
  const c = [...planCourses.value, ...electiveCourses.value].find((x) => x.courseId === id)
  if (c) {
    const total = Number(c.totalHours) || 0
    form.hours = total
    // 沿用课程库的理论/实践学时划分
    form.theoryHours = c.theoryHours === null || c.theoryHours === undefined ? total : Number(c.theoryHours)
    form.practiceHours = Number(c.practiceHours) || 0
    form.practiceRoomType = c.practiceRoomType || ''
  }
  classIds.value = []
  form.classIds = ''
  form.studentNum = 0
  // 选修课不匹配行政班
  if (form.tcType !== '2') {
    await loadPlanClasses()
  }
}

/** 实践学时规则：一旦出现则不少于 8 学时，并按 4 学时递增 */
function normalizePractice(value) {
  const total = planHours.value || 0
  let p = Number(value) || 0
  if (p <= 0) return 0
  if (total > 0 && total < 8) return total
  p = Math.ceil(p / 4) * 4
  if (p < 8) p = 8
  if (total > 0 && p > total) p = total
  return p
}

function onTheoryChange(v) {
  const total = planHours.value || 0
  const theory = Math.min(Math.max(v || 0, 0), total)
  form.theoryHours = theory
  if (!canEditHours.value) return
  const next = normalizePractice(total - theory)
  if (next !== total - theory) {
    ElMessage.warning('实践学时不少于 8 学时，且按 4 学时递增')
  }
  form.practiceHours = next
  form.theoryHours = total - next
}

function onPracticeChange(v) {
  if (!canEditHours.value) return
  const total = planHours.value || 0
  const practice = normalizePractice(v)
  if (practice !== (Number(v) || 0) && (Number(v) || 0) > 0) {
    ElMessage.warning('实践学时不少于 8 学时，且按 4 学时递增')
  }
  form.practiceHours = practice
  form.theoryHours = Math.max(total - practice, 0)
}

function onClassChange(ids) {
  form.classIds = ids.join(',')
  form.studentNum = ids.reduce((s, id) => s + (classIndex.value.get(id)?.studentNum || 0), 0)
}

async function save() {
  if (!form.courseId) {
    ElMessage.warning('请选择课程')
    return
  }
  const total = planHours.value
  if (canEditHours.value && (form.theoryHours || 0) + (form.practiceHours || 0) !== total) {
    ElMessage.warning(`理论学时与实践学时之和应等于计划学时 ${total}`)
    return
  }
  if (canEditHours.value && (form.practiceHours || 0) > 0) {
    if (form.practiceHours < 8) {
      ElMessage.warning('实践学时不少于 8 学时')
      return
    }
    if (form.practiceHours % 4 !== 0) {
      ElMessage.warning('实践学时须按 4 学时递增')
      return
    }
  }
  const weekCount = Number(form.weekCount) || 16
  if (weekCount !== 8 && weekCount !== 16) {
    ElMessage.warning('上课周数只能为 8 周或 16 周')
    return
  }
  const weekStart = weekCount === 16 ? 1 : Number(form.weekStart) || 1
  if (weekCount === 8 && weekStart !== 1 && weekStart !== 9) {
    ElMessage.warning('8 周课程需选择前 8 周或后 8 周')
    return
  }
  form.weekCount = weekCount
  form.weekStart = weekStart
  const payload = { ...form, hours: total }
  delete payload.isNew
  delete payload.schedCount
  delete payload.collegeName
  delete payload.courseName
  delete payload.teacherName
  delete payload.capacity
  await api.saveTeachingClass(payload)
  ElMessage.success('保存成功')
  editDialog.value = false
  await load()
}

/** 学院教务打开分配教师对话框，仅列出本学院在职教师 */
async function openAssign(row) {
  assignRow.value = row
  assignTeacherId.value = ''
  assignTeachers.value = []
  assignDialog.value = true
  const list = await api.teachers({ collegeId: row.collegeId })
  assignTeachers.value = list.filter((t) => t.teacherStatus === '1')
}

async function doAssign() {
  if (!assignTeacherId.value) {
    ElMessage.warning('请选择任课教师')
    return
  }
  assigning.value = true
  try {
    await api.assignTcTeacher(assignRow.value.tcId, assignTeacherId.value)
    ElMessage.success('已分配，等待任课教师确认承接')
    assignDialog.value = false
    await load()
  } finally {
    assigning.value = false
  }
}

/** 教师确认承接，教学班建立完毕并可进入排课 */
async function confirmTc(row) {
  await ElMessageBox.confirm(`确认承接《${row.courseName}》（${row.classIds}）？`, '确认承接', { type: 'info' })
  await api.confirmTc(row.tcId)
  ElMessage.success('已确认承接，教学班建立完成')
  await load()
}

/** 教师拒绝承接，退回学院教务重新分配 */
async function declineTc(row) {
  const { value } = await ElMessageBox.prompt('请填写拒绝承接的原因', '拒绝承接', {
    inputPlaceholder: '如教学任务冲突'
  })
  await api.declineTc(row.tcId, value)
  ElMessage.success('已拒绝，退回学院教务重新分配')
  await load()
}

async function openRoster(row) {
  rosterTc.value = row
  Object.assign(addForm, { stuId: '', stuName: '' })
  roster.value = await api.tcStudents(row.tcId)
  rosterDialog.value = true
}

/** 输入学号后自动带出姓名，便于核对 */
async function fillStuName() {
  const stuId = addForm.stuId.trim()
  if (!stuId || !rosterTc.value) return
  try {
    const list = await api.tcStudentCandidates(rosterTc.value.tcId, { keyword: stuId })
    const hit = list.find((s) => s.stuId === stuId)
    if (hit) addForm.stuName = hit.stuName
  } catch (e) {
    // 检索失败时保留手动输入
  }
}

async function addStudent() {
  const stuId = addForm.stuId.trim()
  if (!stuId) {
    ElMessage.warning('请输入学生学号')
    return
  }
  if (!addForm.stuName.trim()) {
    ElMessage.warning('请输入学生姓名')
    return
  }
  adding.value = true
  try {
    await api.addTcStudent(rosterTc.value.tcId, { stuId, stuName: addForm.stuName.trim() })
    ElMessage.success(`已将 ${addForm.stuName.trim()}（${stuId}）加入教学班`)
    Object.assign(addForm, { stuId: '', stuName: '' })
    roster.value = await api.tcStudents(rosterTc.value.tcId)
    await load()
  } finally {
    adding.value = false
  }
}

async function removeStudent(row) {
  await ElMessageBox.confirm(`确认将 ${row.stuName}（${row.stuId}）移出教学班？`, '移除学生', { type: 'warning' })
  await api.removeTcStudent(rosterTc.value.tcId, row.stuId)
  ElMessage.success('已移除')
  roster.value = await api.tcStudents(rosterTc.value.tcId)
  await load()
}

async function changeStatus(row, status) {
  await api.tcStatus(row.tcId, status)
  ElMessage.success('状态已更新')
  await load()
}

async function cancel(row) {
  const { value } = await ElMessageBox.prompt('请填写停开原因', '停开教学班', {
    inputPlaceholder: '如选课人数不足 10 人'
  })
  await api.tcStatus(row.tcId, '2', value)
  ElMessage.success('已停开')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该教学班？', '提示', { type: 'warning' })
  await api.deleteTc(row.tcId)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  majors.value = await api.majors()
  courses.value = await api.courses()
  colleges.value = await api.colleges()
  try {
    terms.value = await api.terms()
  } catch (e) {
    terms.value = ['2026-2027-1']
  }
  if (!terms.value.includes(term.value)) {
    terms.value.unshift(term.value)
  }
  await load()
})
</script>

<style scoped>
.hours-box {
  width: 150px;
  height: 32px;
  line-height: 30px;
  text-align: center;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
</style>
