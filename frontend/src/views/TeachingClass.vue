<template>
  <div>
    <h2 class="page-title">教学班管理</h2>
    <p class="page-desc">
      教学班是排课与选课的最小单位。按“方案课程 × 面向班级”批量生成时，容量上限不得小于面向人数（BR-04）；
      未落实教师的可先保存为待确认状态。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="term" style="width: 150px" @change="load">
          <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="tcStatus" placeholder="教学班状态" clearable style="width: 150px" @change="load">
          <el-option label="待确认" value="0" />
          <el-option label="确认开课" value="1" />
          <el-option label="不开课" value="2" />
          <el-option label="已排课" value="3" />
          <el-option label="已发布" value="4" />
        </el-select>
        <el-input v-model="keyword" placeholder="课程名称 / 教学班号" clearable style="width: 210px" @change="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <span class="spacer" />
        <el-button v-if="canMaintain" @click="openBatch">批量生成</el-button>
        <el-button v-if="canMaintain" type="primary" @click="openEdit()">新增教学班</el-button>
      </div>

      <el-table :data="rows" border size="small">
        <el-table-column prop="tcId" label="教学班号" width="120" />
        <el-table-column prop="courseName" label="课程" min-width="170" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="任课教师" width="100">
          <template #default="{ row }">
            <span v-if="row.teacherName">{{ row.teacherName }}</span>
            <el-tag v-else size="small" type="danger" effect="plain">未落实</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="classIds" label="面向班级" min-width="130" show-overflow-tooltip />
        <el-table-column prop="studentNum" label="面向人数" width="90" />
        <el-table-column prop="capacity" label="容量" width="70" />
        <el-table-column label="形式" width="80">
          <template #default="{ row }">{{ teachTypeName(row.teachType) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ tcTypeName(row.tcType) }}</template>
        </el-table-column>
        <el-table-column prop="schedCount" label="已排段数" width="90" />
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.tcStatus)">{{ statusName(row.tcStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canMaintain && row.tcStatus === '0'"
              link
              type="success"
              size="small"
              @click="changeStatus(row, '1')"
            >确认开课</el-button>
            <el-button
              v-if="canMaintain && ['0', '1', '3'].includes(row.tcStatus)"
              link
              type="warning"
              size="small"
              @click="cancel(row)"
            >停开</el-button>
            <el-button v-if="canMaintain && row.schedCount === 0" link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
          title="仅已发布的培养方案可生成教学班；未落实教师的课程将保存为待确认状态。"
        />
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="doBatch">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog" :title="form.isNew ? '新增教学班' : '编辑教学班'" width="580px">
      <el-form :model="form" label-width="100px" :disabled="!canMaintain">
        <el-alert
          v-if="!canMaintain"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          title="教师仅可维护本人教学班的偏好节次与不可用时间，其余字段由教务处/学院教务员维护。"
        />
        <el-form-item label="学期" required>
          <el-input v-model="form.term" :disabled="!form.isNew" />
        </el-form-item>
        <el-form-item label="课程" required>
          <el-select v-model="form.courseId" filterable :disabled="!form.isNew" style="width: 100%" @change="onCoursePick">
            <el-option
              v-for="c in courses.filter((x) => x.courseStatus === '1')"
              :key="c.courseId"
              :label="`${c.courseName}（${c.courseId}）`"
              :value="c.courseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任课教师">
          <el-select v-model="form.teacherId" clearable filterable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.teacherId" :label="`${t.teacherName}（${t.teacherId}）`" :value="t.teacherId" />
          </el-select>
        </el-form-item>
        <el-form-item label="面向班级">
          <el-select v-model="classIds" multiple filterable style="width: 100%" @change="onClassChange">
            <el-option v-for="c in classList" :key="c.classId" :label="`${c.className}（${c.studentNum}人）`" :value="c.classId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="面向人数">
              <el-input-number v-model="form.studentNum" :min="1" :max="300" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="容量上限">
              <el-input-number v-model="form.capacity" :min="1" :max="300" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="教学形式">
              <el-select v-model="form.teachType" style="width: 100%">
                <el-option label="讲授" value="1" />
                <el-option label="实验" value="2" />
                <el-option label="上机" value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="教学班类型">
              <el-select v-model="form.tcType" style="width: 100%">
                <el-option label="必修" value="1" />
                <el-option label="选修" value="2" />
                <el-option label="双学位/辅修" value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划学时">
              <el-input-number v-model="form.hours" :min="8" :max="200" :step="8" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="周数">
              <el-input-number v-model="form.weekCount" :min="8" :max="20" />
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { userStore } from '../store/user'

const user = userStore()
/** 教学班维护（批量生成/新增/停开/删除）仅授予 P19；教师只可调整本人教学班的偏好与不可用时间（P0-03） */
const canMaintain = computed(() => user.hasPerm('P19'))
const rows = ref([])
const terms = ref([])
const majors = ref([])
const courses = ref([])
const teachers = ref([])
const classList = ref([])
const term = ref('2026-2027-1')
const tcStatus = ref('')
const keyword = ref('')

const batchDialog = ref(false)
const batchLoading = ref(false)
const batch = reactive({ term: '2026-2027-1', programType: '1' })
const editDialog = ref(false)
const form = reactive({})
const classIds = ref([])

const grades = computed(() => {
  const set = new Set(classList.value.map((c) => c.grade))
  return Array.from(set).sort().reverse()
})

const statusName = (s) => ({ 0: '待确认', 1: '确认开课', 2: '不开课', 3: '已排课', 4: '已发布' }[s] || '-')
const statusType = (s) => ({ 0: 'info', 1: 'primary', 2: 'danger', 3: 'warning', 4: 'success' }[s] || 'info')
const teachTypeName = (t) => ({ 1: '讲授', 2: '实验', 3: '上机' }[t] || t)
const tcTypeName = (t) => ({ 1: '必修', 2: '选修', 3: '双学位' }[t] || t)

async function load() {
  rows.value = await api.teachingClasses({
    term: term.value,
    tcStatus: tcStatus.value || undefined,
    keyword: keyword.value || undefined
  })
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

function openEdit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  if (row) {
    Object.assign(form, { ...row })
    classIds.value = row.classIds ? row.classIds.split(',') : []
  } else {
    Object.assign(form, {
      isNew: true,
      term: term.value,
      teachType: '1',
      tcType: '1',
      hours: 48,
      weekCount: 16,
      capacity: 60,
      studentNum: 30
    })
    classIds.value = []
  }
  editDialog.value = true
}

async function onCoursePick(id) {
  const c = courses.value.find((x) => x.courseId === id)
  if (c) {
    form.hours = c.totalHours
    const firstTeacher = teachers.value.find((t) => t.collegeId === c.openCollege)
    if (firstTeacher && !form.teacherId) form.teacherId = firstTeacher.teacherId
  }
}

function onClassChange(ids) {
  form.classIds = ids.join(',')
  form.studentNum = classList.value
    .filter((c) => ids.includes(c.classId))
    .reduce((s, c) => s + (c.studentNum || 0), 0)
  if (!form.capacity || form.capacity < form.studentNum) {
    form.capacity = form.studentNum + 10
  }
}

async function save() {
  const payload = { ...form }
  delete payload.isNew
  delete payload.schedCount
  delete payload.collegeName
  delete payload.courseName
  delete payload.teacherName
  await api.saveTeachingClass(payload)
  ElMessage.success('保存成功')
  editDialog.value = false
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
  teachers.value = await api.teachers()
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
