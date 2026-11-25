<template>
  <div>
    <h2 class="page-title">培养方案</h2>
    <p class="page-desc">
      方案以“专业编号＋年级＋方案类型”唯一标识，双学位方案与主修方案同结构。发布后方可生成教学班；
      存在未确认的跨院课程或学分不达标时不允许提交评审（BR-07）。
    </p>

    <el-tabs v-model="pageTab" style="margin-bottom: 12px">
      <el-tab-pane label="培养方案" name="plan"></el-tab-pane>
      <el-tab-pane v-if="hasPerm('P22')" :label="`待办 (${pendingRows.length})`" name="todo"></el-tab-pane>
    </el-tabs>
    <div v-show="pageTab === 'plan'">
    <div class="panel">
      <div class="toolbar">
        <el-select v-model="query.majorId" placeholder="全部专业" clearable filterable style="width: 220px" @change="load">
          <el-option v-for="m in majors" :key="m.majorId" :label="`${m.majorName}（${m.majorId}）`" :value="m.majorId" />
        </el-select>
        <el-select v-model="query.planStatus" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option label="编制中" value="0" />
          <el-option label="院系初审" value="1" />
          <el-option label="教务处审核" value="2" />
          <el-option label="已发布" value="3" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="专业名称" clearable style="width: 180px" @change="load" />
        <span class="spacer" />
        <el-button v-if="hasPerm('P02')" type="primary" @click="openCreate">新增方案</el-button>
      </div>

      <el-table :data="pageRows" border size="small">
        <el-table-column prop="majorName" label="专业" min-width="150" show-overflow-tooltip />
        <el-table-column prop="grade" label="适用年级" width="90" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.programType === '2' ? 'warning' : 'primary'" effect="plain">
              {{ row.programType === '2' ? '双学位' : '主修' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column prop="totalCredit" label="总学分" width="80" />
        <el-table-column label="方案学分" width="90">
          <template #default="{ row }">
            <span :class="Number(row.sumCredit) < Number(row.totalCredit) ? 'short' : ''">{{ row.sumCredit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseCount" label="课程数" width="75" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.planStatus)">{{ statusName(row.planStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishDate" label="发布日期" width="110" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">课程明细</el-button>
            <el-button
              v-if="canEditRow(row) && row.planStatus === '0'"
              link
              type="primary"
              size="small"
              @click="openEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="canEditRow(row) && row.planStatus === '0'"
              link
              type="success"
              size="small"
              @click="submit(row)"
            >提交评审</el-button>
            <el-button
              v-if="canEditRow(row) && row.planStatus !== '0'"
              link
              type="warning"
              size="small"
              @click="revise(row)"
            >编制新版本</el-button>
            <el-button
              v-if="hasPerm('P02') || hasPerm('P03')"
              link
              type="info"
              size="small"
              @click="openVersions(row)"
            >版本历史</el-button>
            <el-button
              v-if="hasPerm('P03') && ['1', '2'].includes(row.planStatus)"
              link
              type="warning"
              size="small"
              @click="openReview(row)"
            >{{ row.planStatus === '1' ? '院系初审' : '发布' }}</el-button>
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
    </div>

    <div v-show="pageTab === 'todo'">
      <div class="panel">
        <div class="toolbar">
          <el-tag effect="plain" type="warning">跨院系开课待确认 {{ pendingRows.length }} 项</el-tag>
          <span class="muted">通知仅发送给课程所属开课学院教务员，由该学院确认或拒绝</span>
          <span class="spacer" />
          <el-button @click="loadPending">刷新</el-button>
        </div>
        <el-table :data="pendingRows" border size="small" max-height="520">
          <el-table-column prop="courseId" label="课程号" width="100" />
          <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="申请专业" min-width="160">
            <template #default="{ row }">{{ majorName(row.majorId) }}（{{ row.majorId }}）</template>
          </el-table-column>
          <el-table-column prop="grade" label="年级" width="80" />
          <el-table-column label="方案类型" width="90">
            <template #default="{ row }">{{ row.programType === '1' ? '主修' : '双学位' }}</template>
          </el-table-column>
          <el-table-column prop="openCollegeName" label="开课学院" width="150" />
          <el-table-column prop="receiveCollegeName" label="接收方" width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="hasPerm('P22')"
                link
                type="success"
                size="small"
                @click="handleCross(row, '1')"
              >确认</el-button>
              <el-button
                v-if="hasPerm('P22')"
                link
                type="danger"
                size="small"
                @click="handleCross(row, '2')"
              >拒绝</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!pendingRows.length" description="暂无需要本学院确认的跨院系开课通知" />
      </div>
    </div>

    <el-dialog v-model="createDialog" :title="form.isNew ? '新增培养方案' : '编辑培养方案'" width="640px" top="4vh">
      <el-form :model="form" label-width="106px">
        <el-form-item label="专业" required>
          <el-select v-model="form.majorId" :disabled="!form.isNew" filterable style="width: 100%">
            <el-option
              v-for="m in editableMajors"
              :key="m.majorId"
              :label="`${m.majorName}（${m.majorId}）`"
              :value="m.majorId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用年级" required>
          <el-input v-model="form.grade" :disabled="!form.isNew" maxlength="4" placeholder="如 2026" style="width: 160px" />
        </el-form-item>
        <el-form-item label="方案类型" required>
          <el-radio-group v-model="form.programType" :disabled="!form.isNew">
            <el-radio-button value="1">主修</el-radio-button>
            <el-radio-button value="2">双学位</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="毕业总学分" required>
          <el-input-number v-model="form.totalCredit" :min="40" :max="200" :precision="1" :step="0.5" />
          <span class="muted" style="margin-left: 8px">主修 100～200，双学位 40～80</span>
        </el-form-item>
        <el-form-item v-if="form.programType === '2'" label="双学位名额">
          <el-input-number v-model="form.dualQuota" :min="0" :max="200" />
        </el-form-item>
        <el-form-item label="培养目标">
          <el-input v-model="form.goalDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
        <el-form-item label="毕业要求">
          <el-input v-model="form.graduateDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
        <el-form-item label="选课要求">
          <el-input v-model="form.requireDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
        <el-form-item label="方案说明">
          <el-input v-model="form.planDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" size="58%" :title="`方案课程明细 · ${current?.majorName || ''}`">
      <div class="toolbar">
        <el-tag effect="plain">版本 {{ current?.version }}</el-tag>
        <el-tag :type="statusType(current?.planStatus)" effect="light">{{ statusName(current?.planStatus) }}</el-tag>
        <span class="muted">总学分要求 {{ current?.totalCredit }}，已列 {{ detailRows.length }} 门</span>
        <span class="spacer" />
        <el-button size="small" @click="checkCredit">学分核对</el-button>
        <el-button
          v-if="current && canEditRow(current) && current.planStatus === '0'"
          size="small"
          type="primary"
          @click="openAddCourse"
        >新增课程</el-button>
      </div>
      <el-alert
        v-if="current?.planStatus !== '0'"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 10px"
        :title="`方案当前为「${statusName(current?.planStatus)}」，课程明细已锁定只读；如需修订请使用“编制新版本”。`"
      />
      <!-- 培养方案文本：参照《北京科技大学2024-2026级专业培养方案》 -->
      <el-collapse style="margin-bottom: 12px">
        <el-collapse-item title="培养目标 / 毕业要求 / 选课要求 / 方案说明" name="text">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="总学分">{{ current?.totalCredit }}</el-descriptions-item>
            <el-descriptions-item label="总学时">{{ current?.totalHours || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源计划编号" :span="2">{{ current?.sourcePlanNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="培养目标" :span="2"><div class="plan-text">{{ current?.goalDesc || '-' }}</div></el-descriptions-item>
            <el-descriptions-item label="毕业要求" :span="2"><div class="plan-text">{{ current?.graduateDesc || '-' }}</div></el-descriptions-item>
            <el-descriptions-item label="选课要求" :span="2"><div class="plan-text">{{ current?.requireDesc || '-' }}</div></el-descriptions-item>
            <el-descriptions-item label="方案说明" :span="2"><div class="plan-text">{{ current?.planDesc || '-' }}</div></el-descriptions-item>
          </el-descriptions>
        </el-collapse-item>
      </el-collapse>
      <el-table :data="detailRows" border size="small" max-height="600">
        <el-table-column prop="openSemester" label="学期" width="105" />
        <el-table-column prop="courseId" label="课程号" width="100" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="性质" width="90">
          <template #default="{ row }">{{ courseTypeName(row.courseType) }}</template>
        </el-table-column>
        <el-table-column prop="courseAttribute" label="属性" width="70" />
        <el-table-column prop="assessType" label="考核" width="70" />
        <el-table-column prop="credit" label="学分" width="65" />
        <el-table-column prop="hours" label="学时" width="65" />
        <el-table-column prop="preCourseName" label="先修课程" width="140" show-overflow-tooltip />
        <el-table-column label="跨院" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.crossStatus === '0'" type="warning" size="small">待确认</el-tag>
            <el-tag v-else-if="row.crossStatus === '1'" type="success" size="small">已确认</el-tag>
            <span v-else class="muted">无需</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.crossStatus === '0' && hasPerm('P22')"
              link
              type="success"
              size="small"
              @click="confirmCross(row)"
            >确认承接</el-button>
            <el-button
              v-if="current && canEditRow(current) && current.planStatus === '0'"
              link
              type="danger"
              size="small"
              @click="removeCourse(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="courseDialog" title="新增方案课程" width="560px">
      <el-form :model="courseForm" label-width="96px">
        <el-form-item label="课程" required>
          <el-select v-model="courseForm.courseId" filterable style="width: 100%" @change="onCoursePick">
            <el-option
              v-for="c in courses.filter((x) => x.courseStatus === '1')"
              :key="c.courseId"
              :label="`${c.courseName}（${c.courseId} · ${c.credit}学分）`"
              :value="c.courseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开课学期" required>
          <el-select v-model="courseForm.openSemester" style="width: 160px">
            <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="学分 / 学时">
          <el-input-number v-model="courseForm.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
          <el-input-number v-model="courseForm.hours" :min="8" :max="200" style="margin-left: 10px" />
        </el-form-item>
        <el-form-item label="先修课程">
          <el-select v-model="courseForm.preCourseId" clearable filterable style="width: 100%">
            <el-option
              v-for="c in detailRows"
              :key="c.courseId"
              :label="`${c.courseName}（${c.openSemester}）`"
              :value="c.courseId"
            />
          </el-select>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="学分与学时以课程库为准；先修课程须安排在本课程之前学期，跨院课程需承接学院确认后方可随方案发布。"
        />
      </el-form>
      <template #footer>
        <el-button @click="courseDialog = false">取消</el-button>
        <el-button type="primary" @click="addCourse">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialog" title="培养方案审核" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审核结论">
          <el-radio-group v-model="reviewForm.next">
            <el-radio-button value="pass">通过</el-radio-button>
            <el-radio-button value="back">退回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.opinion" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-alert
          v-if="reviewRow?.planStatus === '1'"
          type="info"
          :closable="false"
          show-icon
          title="院教学指导分委员会初审通过后进入校级审议，审议通过即发布执行版本。"
        />
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" @click="doReview">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="creditDialog" title="学分结构核对" width="460px">
      <div v-if="credit">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="要求总学分">{{ credit.totalCredit }}</el-descriptions-item>
          <el-descriptions-item label="已列学分">{{ credit.sumCredit }}</el-descriptions-item>
          <el-descriptions-item label="差额">{{ credit.gap }}</el-descriptions-item>
          <el-descriptions-item label="是否达标">
            <el-tag :type="credit.reach ? 'success' : 'danger'" size="small">{{ credit.reach ? '达标' : '未达标' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="panel" style="margin-top: 14px">
          <div class="panel-title">按课程性质汇总</div>
          <el-table :data="creditRows" size="small" border>
            <el-table-column prop="type" label="课程性质" />
            <el-table-column prop="credit" label="学分合计" />
          </el-table>
        </div>
        <el-alert
          v-if="credit.issues?.length"
          type="error"
          :closable="false"
          show-icon
          style="margin-top: 12px"
          title="未达标项"
        >
          <div v-for="(item, idx) in credit.issues" :key="idx">· {{ item }}</div>
        </el-alert>
        <el-alert
          v-if="credit.warnings?.length"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top: 8px"
          title="提示"
        >
          <div v-for="(item, idx) in credit.warnings" :key="idx">· {{ item }}</div>
        </el-alert>
      </div>
    </el-dialog>

    <el-dialog v-model="versionsDialog" :title="`版本历史 · ${versionTitle}`" width="720px">
      <el-table :data="versions" border size="small" max-height="420">
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column prop="planStatus" label="归档状态" width="110">
          <template #default="{ row }">{{ statusName(row.planStatus) }}</template>
        </el-table-column>
        <el-table-column prop="totalCredit" label="总学分" width="90" />
        <el-table-column prop="courseCount" label="课程门数" width="90" />
        <el-table-column prop="publishDate" label="发布时间" width="110" />
        <el-table-column prop="reviewOpinion" label="审核意见" min-width="160" show-overflow-tooltip />
        <el-table-column prop="archiveTime" label="归档时间" min-width="160" />
      </el-table>
      <p class="muted" style="margin-top: 8px">历史版本只读留档，当前执行版本始终为方案主记录。</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { hasPerm, userStore } from '../store/user'

const user = userStore()
const rows = ref([])
const page = ref(1)
const size = ref(20)
const pageRows = computed(() => {
  const start = (page.value - 1) * size.value
  return rows.value.slice(start, start + size.value)
})
watch(size, () => { page.value = 1 })
const majors = ref([])
const courses = ref([])
const query = reactive({ majorId: '', planStatus: '', keyword: '' })

const createDialog = ref(false)
const detailDrawer = ref(false)
const courseDialog = ref(false)
const reviewDialog = ref(false)
const creditDialog = ref(false)
const form = reactive({})
const pendingRows = ref([])
const pageTab = ref('plan')
const courseForm = reactive({})
const reviewForm = reactive({ next: 'pass', opinion: '' })
const reviewRow = ref(null)
const current = ref(null)
const detailRows = ref([])
const credit = ref(null)
const versionsDialog = ref(false)
const versions = ref([])
const versionTitle = ref('')

/** 新增/编辑方案时可选专业：学院教务员仅限本学院在招专业，校级账号可选全部 */
const editableMajors = computed(() =>
  majors.value.filter(
    (m) =>
      m.majorStatus === '1' &&
      (user.user?.dataScope !== '2' || m.collegeId === user.user?.collegeId)
  )
)

/** 编辑类操作：学院教务员仅可操作本学院方案 */
function canEditRow(row) {
  if (!hasPerm('P02')) return false
  if (user.user?.dataScope === '2') return row.collegeId === user.user?.collegeId
  return true
}

const creditRows = computed(() => {
  if (!credit.value?.byType) return []
  const names = { 11: '校级必修', 12: '院级必修', 13: '专业必修', 14: '专业选修', 15: '通识', 16: '实践教学', 17: '素质拓展' }
  return Object.entries(credit.value.byType).map(([k, v]) => ({ type: names[k] || k, credit: v }))
})

// 学期选项：按方案年级生成 8 个学期（YYYY-YYYY-1/2/3），Excel 中第 3 学期为小学期
const semesterOptions = computed(() => {
  const grade = Number(current.value?.grade)
  if (!grade) return []
  const out = []
  for (let i = 0; i < 8; i++) {
    const year = grade + Math.floor(i / 2)
    const term = (i % 2) + 1
    // 每学年末追加小学期，与培养方案 Excel 的开课学期格式一致
    out.push(`${year}-${year + 1}-${term}`)
    if (term === 2) out.push(`${year}-${year + 1}-3`)
  }
  return out
})

const majorName = (id) => majors.value.find((m) => m.majorId === id)?.majorName || id
const statusName = (s) => ({ 0: '编制中', 1: '院系初审', 2: '教务处审核', 3: '已发布' }[s] || '-')
const statusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success' }[s] || 'info')
const courseTypeName = (t) =>
  ({ 11: '校级必修', 12: '院级必修', 13: '专业必修', 14: '专业选修', 15: '通识', 16: '实践', 17: '素质拓展' }[t] || t)

async function load() {
  rows.value = await api.programs({
    majorId: query.majorId || undefined,
    planStatus: query.planStatus || undefined,
    keyword: query.keyword || undefined
  })
  page.value = 1
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { isNew: true, programType: '1', grade: '2026', totalCredit: 165, dualQuota: 30 })
  createDialog.value = true
}

function openEdit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { ...row, isNew: false })
  createDialog.value = true
}

async function save() {
  const payload = { ...form }
  delete payload.isNew
  await api.saveProgram(payload)
  ElMessage.success('方案已保存')
  createDialog.value = false
  await load()
}

async function openDetail(row) {
  current.value = row
  detailDrawer.value = true
  await loadDetail()
}

async function loadDetail() {
  detailRows.value = await api.programCourses({
    majorId: current.value.majorId,
    grade: current.value.grade,
    programType: current.value.programType
  })
}

function openAddCourse() {
  Object.keys(courseForm).forEach((k) => delete courseForm[k])
  Object.assign(courseForm, { openSemester: semesterOptions.value[0] || '' })
  courseDialog.value = true
}

function onCoursePick(id) {
  const c = courses.value.find((x) => x.courseId === id)
  if (c) {
    courseForm.credit = Number(c.credit)
    courseForm.hours = c.totalHours
  }
}

async function addCourse() {
  await api.addProgramCourse({
    majorId: current.value.majorId,
    grade: current.value.grade,
    programType: current.value.programType,
    ...courseForm
  })
  ElMessage.success('课程已加入方案')
  courseDialog.value = false
  await loadDetail()
  await load()
}

async function removeCourse(row) {
  await ElMessageBox.confirm(`确认从方案中删除「${row.courseName}」？`, '提示', { type: 'warning' })
  await api.removeProgramCourse({
    majorId: current.value.majorId,
    grade: current.value.grade,
    programType: current.value.programType,
    courseId: row.courseId
  })
  ElMessage.success('已删除')
  await loadDetail()
  await load()
}

async function loadPending() {
  pendingRows.value = await api.pendingCross()
}

async function handleCross(row, status) {
  const tip = status === '1' ? '确认开课' : '拒绝开课'
  await ElMessageBox.confirm(
    `${tip}：${row.courseName}（${majorName(row.majorId)} ${row.grade}）？`,
    '跨院系开课确认',
    { type: status === '1' ? 'success' : 'warning' }
  )
  await api.confirmCross({
    majorId: row.majorId,
    grade: row.grade,
    programType: row.programType,
    courseId: row.courseId,
    status
  })
  ElMessage.success(status === '1' ? '已确认开课' : '已拒绝开课')
  await loadPending()
}

async function confirmCross(row) {
  await api.confirmCross({
    majorId: current.value.majorId,
    grade: current.value.grade,
    programType: current.value.programType,
    courseId: row.courseId,
    status: '1'
  })
  ElMessage.success('已确认承接')
  await loadDetail()
}

async function checkCredit() {
  credit.value = await api.creditCheck({
    majorId: current.value.majorId,
    grade: current.value.grade,
    programType: current.value.programType
  })
  creditDialog.value = true
}

/** 编制新版本：归档当前版本，版本号 +1 并回到编制中（P0-06） */
async function revise(row) {
  await ElMessageBox.confirm(
    `将对「${row.majorName} ${row.grade}级 ${row.programType === '2' ? '双学位' : '主修'}」当前版本 ${row.version} 归档，并新建下一版本继续编制，确认？`,
    '编制新版本',
    { type: 'warning' }
  )
  await api.reviseProgram({ majorId: row.majorId, grade: row.grade, programType: row.programType })
  ElMessage.success('已生成新版本，可在编制中状态继续编辑')
  await load()
}

async function openVersions(row) {
  versions.value = await api.programVersions({
    majorId: row.majorId,
    grade: row.grade,
    programType: row.programType
  })
  versionTitle.value = `${row.majorName} ${row.grade}级 ${row.programType === '2' ? '双学位' : '主修'}`
  versionsDialog.value = true
}

async function submit(row) {
  await ElMessageBox.confirm(`提交「${row.majorName} ${row.grade}级」方案评审？提交前将校核学分结构与跨院确认状态。`, '提交评审', {
    type: 'warning'
  })
  await api.submitProgram({ majorId: row.majorId, grade: row.grade, programType: row.programType })
  ElMessage.success('已提交评审')
  await load()
}

function openReview(row) {
  reviewRow.value = row
  reviewForm.next = 'pass'
  reviewForm.opinion = ''
  reviewDialog.value = true
}

async function doReview() {
  const row = reviewRow.value
  let status = '0'
  if (row.planStatus === '1') {
    status = reviewForm.next === 'pass' ? '2' : '0'
  } else if (row.planStatus === '2') {
    status = reviewForm.next === 'pass' ? '3' : '0'
  }
  if (reviewForm.next === 'pass' && status === '3') {
    await ElMessageBox.confirm('发布后方案将成为执行版本，课程与学分等关键字段变更须走变更单。确认发布？', '发布确认', {
      type: 'warning'
    })
  }
  await api.reviewProgram({
    majorId: row.majorId,
    grade: row.grade,
    programType: row.programType,
    status,
    opinion: reviewForm.opinion
  })
  ElMessage.success('审核已提交')
  reviewDialog.value = false
  await load()
}

onMounted(async () => {
  majors.value = await api.majors()
  courses.value = await api.courses()
  await load()
  if (hasPerm('P22')) {
    try {
      await loadPending()
    } catch {
      pendingRows.value = []
    }
  }
})
</script>

<style scoped>
.short {
  color: #d9534f;
  font-weight: 600;
}
.plan-text {
  max-height: 110px;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
