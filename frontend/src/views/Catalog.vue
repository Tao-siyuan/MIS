<template>
  <div>
    <h2 class="page-title">专业与培养方案总览</h2>
    <p class="page-desc">
      全校专业与培养方案统一浏览：学院教务员、教师、学生均可查看全部数据；学院教务员仅可编辑本学院的专业与培养方案，
      教师与学生为只读。学院教务员文案编辑在“培养方案”页办理，本页提供方案主信息快速编辑。
    </p>

    <div class="panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="专业目录" name="major">
          <div class="toolbar">
            <el-select v-model="majorFilter.collegeId" placeholder="全部学院" clearable filterable style="width: 200px">
              <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
            </el-select>
            <el-input v-model="majorFilter.keyword" placeholder="专业名称/代码" clearable style="width: 200px" @keyup.enter="loadMajors" />
            <el-button @click="loadMajors">查询</el-button>
            <span class="spacer" />
            <span class="muted">共 {{ filteredMajors.length }} 个专业</span>
          </div>
          <el-table :data="pagedMajors" border size="small" max-height="560">
            <el-table-column prop="majorId" label="专业代码" width="100" />
            <el-table-column prop="majorName" label="专业名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="collegeName" label="所属学院" width="160" show-overflow-tooltip />
            <el-table-column prop="duration" label="学制" width="70" />
            <el-table-column prop="degree" label="学位" width="90" />
            <el-table-column prop="minCredit" label="最低学分" width="90" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.majorStatus === '1' ? 'success' : 'info'" size="small">
                  {{ row.majorStatus === '1' ? '在招' : '停招' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="方案数" width="80" prop="programCount" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEditMajor(row)" link type="primary" size="small" @click="openMajor(row)">编辑</el-button>
                <span v-else class="muted">只读</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="majorPage"
            v-model:page-size="majorSize"
            :page-sizes="[10, 20, 30, 50]"
            :total="filteredMajors.length"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 14px; justify-content: flex-end"
          />
        </el-tab-pane>

        <el-tab-pane label="培养方案" name="program">
          <div class="toolbar">
            <el-select v-model="programFilter.collegeId" placeholder="全部学院" clearable filterable style="width: 180px">
              <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
            </el-select>
            <el-select v-model="programFilter.majorId" placeholder="全部专业" clearable filterable style="width: 190px">
              <el-option v-for="m in allMajors" :key="m.majorId" :label="`${m.majorName}（${m.majorId}）`" :value="m.majorId" />
            </el-select>
            <el-select v-model="programFilter.planStatus" placeholder="全部状态" clearable style="width: 130px">
              <el-option label="编制中" value="0" />
              <el-option label="院系初审" value="1" />
              <el-option label="教务处审核" value="2" />
              <el-option label="已发布" value="3" />
            </el-select>
            <el-input v-model="programFilter.keyword" placeholder="专业名称" clearable style="width: 160px" @keyup.enter="loadPrograms" />
            <el-button @click="loadPrograms">查询</el-button>
            <span class="spacer" />
            <span class="muted">共 {{ filteredPrograms.length }} 套方案</span>
          </div>
          <el-table :data="pagedPrograms" border size="small" max-height="560">
            <el-table-column prop="collegeName" label="所属学院" width="150" show-overflow-tooltip />
            <el-table-column prop="majorName" label="专业" min-width="160" show-overflow-tooltip />
            <el-table-column prop="grade" label="年级" width="80" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">{{ row.programType === '2' ? '双学位' : '主修' }}</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="totalCredit" label="总学分" width="85" />
            <el-table-column prop="sumCredit" label="已列学分" width="90" />
            <el-table-column prop="courseCount" label="课程数" width="80" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.planStatus)" size="small">{{ statusName(row.planStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openProgramDetail(row)">查看明细</el-button>
                <el-button v-if="canEditProgram(row)" link type="warning" size="small" @click="openProgram(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="programPage"
            v-model:page-size="programSize"
            :page-sizes="[10, 20, 30, 50]"
            :total="filteredPrograms.length"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 14px; justify-content: flex-end"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="majorDialog" title="编辑专业" width="500px">
      <el-form :model="majorForm" label-width="100px">
        <el-form-item label="专业代码">
          <el-input v-model="majorForm.majorId" disabled />
        </el-form-item>
        <el-form-item label="专业名称" required>
          <el-input v-model="majorForm.majorName" maxlength="30" />
        </el-form-item>
        <el-form-item label="所属学院">
          <el-input v-model="majorForm.collegeName" disabled />
        </el-form-item>
        <el-form-item label="学制（年）">
          <el-input-number v-model="majorForm.duration" :min="2" :max="8" />
        </el-form-item>
        <el-form-item label="授予学位">
          <el-input v-model="majorForm.degree" maxlength="10" />
        </el-form-item>
        <el-form-item label="毕业最低学分">
          <el-input-number v-model="majorForm.minCredit" :min="100" :max="200" :precision="1" :step="0.5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="majorDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMajor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="programDialog" title="编辑培养方案主信息" width="640px" top="4vh">
      <el-form :model="programForm" label-width="106px">
        <el-form-item label="专业">
          <el-input :model-value="`${programForm.majorName || ''}（${programForm.majorId || ''}）`" disabled />
        </el-form-item>
        <el-form-item label="适用年级">
          <el-input v-model="programForm.grade" disabled style="width: 160px" />
        </el-form-item>
        <el-form-item label="方案类型">
          <el-input :model-value="programForm.programType === '2' ? '双学位' : '主修'" disabled style="width: 160px" />
        </el-form-item>
        <el-form-item label="毕业总学分" required>
          <el-input-number v-model="programForm.totalCredit" :min="40" :max="200" :precision="1" :step="0.5" />
        </el-form-item>
        <el-form-item label="培养目标">
          <el-input v-model="programForm.goalDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
        <el-form-item label="毕业要求">
          <el-input v-model="programForm.graduateDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
        <el-form-item label="选课要求">
          <el-input v-model="programForm.requireDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
        <el-form-item label="方案说明">
          <el-input v-model="programForm.planDesc" type="textarea" :rows="3" maxlength="3000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="programDialog = false">取消</el-button>
        <el-button type="primary" @click="saveProgramInfo">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" size="58%" :title="`方案课程明细 · ${detailTitle}`">
      <el-table :data="detailRows" border size="small" max-height="640">
        <el-table-column prop="openSemester" label="学期" width="110" />
        <el-table-column prop="courseId" label="课程号" width="100" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="性质" width="100">
          <template #default="{ row }">{{ courseTypeName(row.courseType) }}</template>
        </el-table-column>
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column prop="hours" label="学时" width="70" />
        <el-table-column prop="openCollegeName" label="开课学院" width="150" show-overflow-tooltip />
        <el-table-column label="跨院" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.crossStatus === '0'" type="warning" size="small">待确认</el-tag>
            <el-tag v-else-if="row.crossStatus === '1'" type="success" size="small">已确认</el-tag>
            <span v-else class="muted">无需</span>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { hasPerm, userStore } from '../store/user'

const user = userStore()
const tab = ref('major')
const colleges = ref([])
const allMajors = ref([])
const allPrograms = ref([])
const majorFilter = reactive({ collegeId: '', keyword: '' })
const programFilter = reactive({ collegeId: '', majorId: '', planStatus: '', keyword: '' })
const majorPage = ref(1)
const majorSize = ref(20)
const programPage = ref(1)
const programSize = ref(20)
const majorDialog = ref(false)
const programDialog = ref(false)
const detailDrawer = ref(false)
const majorForm = reactive({})
const programForm = reactive({})
const detailRows = ref([])
const detailTitle = ref('')
const currentProgram = ref(null)

const canWrite = computed(() => hasPerm('P01') || hasPerm('P02'))

const filteredMajors = computed(() =>
  allMajors.value.filter(
    (m) =>
      (!majorFilter.collegeId || m.collegeId === majorFilter.collegeId) &&
      (!majorFilter.keyword ||
        m.majorName?.includes(majorFilter.keyword) ||
        m.majorId?.includes(majorFilter.keyword))
  )
)
const pagedMajors = computed(() => {
  const start = (majorPage.value - 1) * majorSize.value
  return filteredMajors.value.slice(start, start + majorSize.value)
})
const filteredPrograms = computed(() =>
  allPrograms.value.filter(
    (p) =>
      (!programFilter.collegeId || p.collegeId === programFilter.collegeId) &&
      (!programFilter.majorId || p.majorId === programFilter.majorId) &&
      (!programFilter.planStatus || p.planStatus === programFilter.planStatus) &&
      (!programFilter.keyword || p.majorName?.includes(programFilter.keyword))
  )
)
const pagedPrograms = computed(() => {
  const start = (programPage.value - 1) * programSize.value
  return filteredPrograms.value.slice(start, start + programSize.value)
})

const statusName = (s) => ({ 0: '编制中', 1: '院系初审', 2: '教务处审核', 3: '已发布' }[s] || '-')
const statusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'warning', 3: 'success' }[s] || 'info')
const courseTypeName = (t) =>
  ({ 11: '校级必修', 12: '院级必修', 13: '专业必修', 14: '专业选修', 15: '通识', 16: '实践', 17: '素质拓展' }[t] || t)

/** 校级账号可编辑全部；学院教务员仅可编辑本学院 */
function canEditCollege(collegeId) {
  if (!canWrite.value) return false
  if (user.user?.dataScope === '2') return user.user?.collegeId === collegeId
  return true
}
const canEditMajor = (row) => canEditCollege(row.collegeId)
const canEditProgram = (row) => canEditCollege(row.collegeId) && row.planStatus === '0'

async function loadMajors() {
  allMajors.value = await api.catalogMajors({})
  majorPage.value = 1
}

async function loadPrograms() {
  allPrograms.value = await api.catalogPrograms({})
  programPage.value = 1
}

function openMajor(row) {
  Object.keys(majorForm).forEach((k) => delete majorForm[k])
  Object.assign(majorForm, { ...row })
  majorDialog.value = true
}

async function saveMajor() {
  await api.updateMajor(majorForm.majorId, {
    majorName: majorForm.majorName,
    collegeId: majorForm.collegeId,
    duration: majorForm.duration,
    degree: majorForm.degree,
    minCredit: majorForm.minCredit
  })
  ElMessage.success('专业已更新')
  majorDialog.value = false
  await loadMajors()
}

function openProgram(row) {
  currentProgram.value = row
  Object.keys(programForm).forEach((k) => delete programForm[k])
  Object.assign(programForm, {
    ...row,
    graduateDesc: row.graduateDesc,
    requireDesc: row.requireDesc,
    planDesc: row.planDesc
  })
  programDialog.value = true
}

async function saveProgramInfo() {
  await api.saveProgram({
    majorId: programForm.majorId,
    grade: programForm.grade,
    programType: programForm.programType,
    totalCredit: programForm.totalCredit,
    goalDesc: programForm.goalDesc,
    graduateDesc: programForm.graduateDesc,
    requireDesc: programForm.requireDesc,
    planDesc: programForm.planDesc,
    dualQuota: programForm.dualQuota
  })
  ElMessage.success('方案主信息已更新')
  programDialog.value = false
  await loadPrograms()
}

async function openProgramDetail(row) {
  detailTitle.value = `${row.majorName} ${row.grade} ${row.programType === '2' ? '双学位' : '主修'}`
  detailRows.value = await api.catalogProgramCourses({
    majorId: row.majorId,
    grade: row.grade,
    programType: row.programType
  })
  detailDrawer.value = true
}

onMounted(async () => {
  colleges.value = await api.colleges()
  await Promise.all([loadMajors(), loadPrograms()])
})
</script>

<style scoped>
.panel :deep(.el-tabs__content) {
  padding-top: 4px;
}
</style>
