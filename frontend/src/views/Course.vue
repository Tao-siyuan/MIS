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

      <el-table :data="rows" border size="small">
        <el-table-column prop="courseId" label="课程号" width="100" />
        <el-table-column prop="courseName" label="课程名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="性质" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeName(row.courseType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column prop="totalHours" label="总学时" width="80" />
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
              <el-input-number v-model="form.totalHours" :min="8" :max="200" :step="8" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const rows = ref([])
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

async function load() {
  rows.value = await api.courses({
    keyword: query.keyword || undefined,
    courseType: query.courseType || undefined,
    collegeId: query.collegeId || undefined,
    status: query.status || undefined
  })
}

function openCreate() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { isNew: true, courseType: '13', assessType: '1', credit: 3, totalHours: 48, openCollege: colleges.value[0]?.collegeId })
  dialog.value = true
}

function openEdit(row) {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { ...row, isNew: false })
  dialog.value = true
}

async function save() {
  const payload = { ...form }
  delete payload.isNew
  delete payload.collegeName
  delete payload.preCourseName
  delete payload.courseStatus
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
