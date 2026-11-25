<template>
  <div>
    <h2 class="page-title">通识课程计划</h2>
    <p class="page-desc">
      教务处按学年下发通识课程范围与建议学时，开课学院确认承接并落实授课教师后，计划方可发布，
      作为校级必修课排课的来源。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-input v-model="schoolYear" placeholder="学年，如 2026-2027" style="width: 180px" @change="load" />
        <span class="spacer" />
        <el-button v-if="hasPerm('P04')" type="primary" @click="openCreate">新增计划</el-button>
      </div>

      <el-table :data="pageRows" border size="small">
        <el-table-column prop="schoolYear" label="学年" width="110" />
        <el-table-column prop="courseId" label="课程号" width="100" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="suggestHours" label="建议学时" width="85" />
        <el-table-column prop="collegeName" label="承接学院" width="140" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="拟任教师" width="100" />
        <el-table-column label="承接状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.confirmStatus === '1' ? 'success' : row.confirmStatus === '2' ? 'danger' : 'info'">
              {{ { 0: '待确认', 1: '已承接', 2: '不承接' }[row.confirmStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计划状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.planStatus === '2' ? 'success' : 'info'">
              {{ { 0: '草案', 1: '汇总调整', 2: '已发布' }[row.planStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.confirmStatus === '0'"
              link
              type="success"
              size="small"
              @click="confirm(row, '1')"
            >确认承接</el-button>
            <el-button
              v-if="row.confirmStatus === '0'"
              link
              type="danger"
              size="small"
              @click="confirm(row, '2')"
            >不承接</el-button>
            <el-button
              v-if="hasPerm('P04') && row.planStatus !== '2'"
              link
              type="primary"
              size="small"
              @click="publish(row)"
            >发布</el-button>
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

    <el-dialog v-model="dialog" title="新增通识课程计划" width="540px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="学年" required>
          <el-input v-model="form.schoolYear" placeholder="2026-2027" />
        </el-form-item>
        <el-form-item label="课程" required>
          <el-select v-model="form.courseId" filterable style="width: 100%">
            <el-option
              v-for="c in generalCourses"
              :key="c.courseId"
              :label="`${c.courseName}（${c.courseId}）`"
              :value="c.courseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="建议学时">
          <el-input-number v-model="form.suggestHours" :min="8" :max="64" />
        </el-form-item>
        <el-form-item label="承接学院" required>
          <el-select v-model="form.undertakeCollege" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="拟任教师">
          <el-select v-model="form.teacherId" clearable filterable style="width: 100%">
            <el-option v-for="t in teachers" :key="t.teacherId" :label="`${t.teacherName}（${t.userId || t.teacherId}）`" :value="t.teacherId" />
          </el-select>
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
import { hasPerm } from '../store/user'

const rows = ref([])
const page = ref(1)
const size = ref(20)
const pageRows = computed(() => {
  const start = (page.value - 1) * size.value
  return rows.value.slice(start, start + size.value)
})
watch(size, () => { page.value = 1 })
const courses = ref([])
const colleges = ref([])
const teachers = ref([])
const schoolYear = ref('2026-2027')
const dialog = ref(false)
const form = reactive({})

const generalCourses = computed(() => courses.value.filter((c) => c.courseType === '15' && c.courseStatus === '1'))

async function load() {
  rows.value = await api.generalPlans({ schoolYear: schoolYear.value || undefined })
  page.value = 1
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { schoolYear: schoolYear.value, suggestHours: 32 })
  dialog.value = true
}

async function save() {
  await api.saveGeneralPlan({ ...form })
  ElMessage.success('计划已保存，等待承接学院确认')
  dialog.value = false
  await load()
}

async function confirm(row, status) {
  await api.confirmGeneralPlan({ schoolYear: row.schoolYear, courseId: row.courseId, status })
  ElMessage.success('已更新承接状态')
  await load()
}

async function publish(row) {
  await ElMessageBox.confirm('发布后该课程将纳入校级必修课排课范围，确认发布？', '提示', { type: 'warning' })
  await api.publishGeneralPlan({ schoolYear: row.schoolYear, courseId: row.courseId })
  ElMessage.success('计划已发布')
  await load()
}

onMounted(async () => {
  courses.value = await api.courses()
  colleges.value = await api.colleges()
  teachers.value = await api.teachers()
  await load()
})
</script>
