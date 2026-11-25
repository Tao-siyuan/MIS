<template>
  <div>
    <h2 class="page-title">课程库维护</h2>
    <p class="page-desc">
      课程号为“开课学院 2 位 + 顺序号 5 位”，由编码服务集中发放。已被培养方案引用的课程只允许修改
      教学大纲与考核方式，学分与学时不可变更；存在进行中教学班的课程不得停开。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="课程名称 / 课程号" clearable style="width: 200px" @change="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.courseType" placeholder="课程性质" clearable style="width: 150px" @change="load">
          <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="query.collegeId" placeholder="开课学院" clearable style="width: 180px" @change="load">
          <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="load">
          <el-option label="在库" value="1" />
          <el-option label="停开" value="0" />
        </el-select>
        <span class="spacer" />
        <el-button type="primary" @click="openCreate">新增课程</el-button>
      </div>

      <el-table :data="pageRows" border size="small">
        <el-table-column prop="courseId" label="课程号" width="100" />
        <el-table-column prop="courseName" label="课程名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="性质" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeName(row.courseType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column prop="totalHours" label="总学时" width="80" />
        <el-table-column prop="theoryHours" label="理论" width="65" />
        <el-table-column prop="practiceHours" label="实践" width="65" />
        <el-table-column label="实践场地" width="110">
          <template #default="{ row }">{{ roomTypeName(row.practiceRoomType) }}</template>
        </el-table-column>
        <el-table-column label="考核" width="75">
          <template #default="{ row }">{{ row.assessType === '1' ? '考试' : '考查' }}</template>
        </el-table-column>
        <el-table-column prop="collegeName" label="开课学院" width="150" show-overflow-tooltip />
        <el-table-column prop="preCourseName" label="先修课程" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.courseStatus === '1' ? 'success' : 'info'">
              {{ row.courseStatus === '1' ? '在库' : '停开' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.courseStatus === '1' ? 'danger' : 'success'" size="small" @click="toggle(row)">
              {{ row.courseStatus === '1' ? '停开' : '恢复' }}
            </el-button>
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

    <el-dialog v-model="dialog" :title="form.isNew ? '新增课程' : '编辑课程'" width="620px">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="课程号">
              <el-input v-model="form.courseId" :disabled="!form.isNew" placeholder="留空自动生成" maxlength="8" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开课学院" required>
              <el-select v-model="form.openCollege" style="width: 100%">
                <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程名称" required>
          <el-input v-model="form.courseName" maxlength="40" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="课程性质" required>
              <el-select v-model="form.courseType" style="width: 100%">
                <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="考核方式">
              <el-radio-group v-model="form.assessType">
                <el-radio-button value="1">考试</el-radio-button>
                <el-radio-button value="2">考查</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="学分">
              <el-input-number v-model="form.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总学时">
              <span class="muted" style="line-height: 32px">{{ totalHours }} 学时</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="理论学时">
              <el-input-number
                v-model="form.theoryHours"
                :min="0"
                :max="200"
                :step="2"
                :disabled="!canEditHours"
                @change="onTheoryChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实践学时">
              <el-input-number
                v-model="form.practiceHours"
                :min="0"
                :max="200"
                :step="4"
                :disabled="!canEditHours"
                @change="onPracticeChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="(form.practiceHours || 0) > 0" label="实践场地" required>
          <el-select v-model="form.practiceRoomType" :disabled="!canEditHours" style="width: 100%" placeholder="请选择实践学时所需教室类型">
            <el-option v-for="r in roomTypes" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="!canEditHours"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          title="学时划分仅系统管理员与教务处管理员可调整：理论按 2 学时递增；实践为 0 或不少于 8 学时且按 4 学时递增。"
        />
        <el-form-item label="先修课程">
          <el-select v-model="form.preCourseId" clearable filterable style="width: 100%">
            <el-option
              v-for="c in rows.filter((x) => x.courseId !== form.courseId)"
              :key="c.courseId"
              :label="`${c.courseName}（${c.courseId}）`"
              :value="c.courseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教学大纲">
          <el-input v-model="form.syllabus" placeholder="文件地址，如 /files/syllabus/0901001.pdf" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
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
const rows = ref([])
const page = ref(1)
const size = ref(20)
const pageRows = computed(() => {
  const start = (page.value - 1) * size.value
  return rows.value.slice(start, start + size.value)
})
watch(size, () => { page.value = 1 })
const colleges = ref([])
const query = reactive({ keyword: '', courseType: '', collegeId: '', status: '' })
const dialog = ref(false)
const form = reactive({})

const types = [
  { value: '11', label: '11 校级必修' },
  { value: '12', label: '12 院级必修' },
  { value: '13', label: '13 专业必修' },
  { value: '14', label: '14 专业选修' },
  { value: '15', label: '15 通识' },
  { value: '16', label: '16 实践教学' },
  { value: '17', label: '17 素质拓展' }
]
const typeName = (t) => (types.find((x) => x.value === t)?.label || t).replace(/^\d+\s/, '')

const roomTypes = [
  { value: '41', label: '机房' },
  { value: '42', label: '物理实验室' },
  { value: '43', label: '化学实验室' },
  { value: '44', label: '生物实验室' },
  { value: '45', label: '操场' },
  { value: '46', label: '金工实习场地' },
  { value: '47', label: '其他实践场地' }
]
const roomTypeName = (t) => roomTypes.find((r) => r.value === t)?.label || '-'
/** 学时划分仅系统管理员(R01)与教务处管理员(R02)可调整 */
const canEditHours = computed(() => ['R01', 'R02'].includes(user.user?.roleId))
const totalHours = computed(() => (form.theoryHours || 0) + (form.practiceHours || 0))

/** 实践学时：出现即不少于 8 学时，且按 4 学时递增 */
function normalizePractice(value) {
  let p = Number(value) || 0
  if (p <= 0) return 0
  p = Math.ceil(p / 4) * 4
  return p < 8 ? 8 : p
}

function onTheoryChange(v) {
  let theory = Number(v) || 0
  if (theory % 2 !== 0) {
    theory = Math.round(theory / 2) * 2
    ElMessage.warning('理论学时须按 2 学时递增')
  }
  form.theoryHours = theory
}

function onPracticeChange(v) {
  const raw = Number(v) || 0
  const practice = normalizePractice(raw)
  if (practice !== raw) {
    ElMessage.warning('实践学时不少于 8 学时，且按 4 学时递增')
  }
  form.practiceHours = practice
  if (!practice) form.practiceRoomType = ''
}

async function load() {
  rows.value = await api.courses({
    keyword: query.keyword || undefined,
    courseType: query.courseType || undefined,
    collegeId: query.collegeId || undefined,
    status: query.status || undefined
  })
  page.value = 1
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, {
    isNew: true,
    courseType: '13',
    assessType: '1',
    credit: 3,
    totalHours: 48,
    theoryHours: 48,
    practiceHours: 0,
    practiceRoomType: '',
    openCollege: colleges.value[0]?.collegeId
  })
  dialog.value = true
}

function openEdit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { ...row, isNew: false })
  form.theoryHours = Number(row.theoryHours) || 0
  form.practiceHours = Number(row.practiceHours) || 0
  form.practiceRoomType = row.practiceRoomType || ''
  dialog.value = true
}

async function save() {
  const theory = form.theoryHours || 0
  const practice = form.practiceHours || 0
  if (theory % 2 !== 0) {
    ElMessage.warning('理论学时须按 2 学时递增')
    return
  }
  if (practice > 0) {
    if (practice < 8) {
      ElMessage.warning('实践学时不少于 8 学时')
      return
    }
    if (practice % 4 !== 0) {
      ElMessage.warning('实践学时须按 4 学时递增')
      return
    }
    if (!form.practiceRoomType) {
      ElMessage.warning('请选择实践学时所需教室类型')
      return
    }
  }
  const payload = { ...form, totalHours: theory + practice }
  delete payload.isNew
  delete payload.collegeName
  delete payload.preCourseName
  delete payload.courseStatus
  if (payload.practiceHours === 0) payload.practiceRoomType = null
  if (form.isNew) {
    await api.addCourse(payload)
  } else {
    await api.updateCourse(form.courseId, payload)
  }
  ElMessage.success('保存成功')
  dialog.value = false
  await load()
}

async function toggle(row) {
  await ElMessageBox.confirm(
    row.courseStatus === '1' ? '停开后不再生成教学班，历史修读记录保留。确认停开？' : '确认恢复该课程？',
    '提示',
    { type: 'warning' }
  )
  await api.courseStatus(row.courseId, row.courseStatus === '1' ? '0' : '1')
  ElMessage.success('状态已更新')
  await load()
}

onMounted(async () => {
  colleges.value = await api.colleges()
  await load()
})
</script>
