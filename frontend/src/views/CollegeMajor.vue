<template>
  <div>
    <h2 class="page-title">学院与专业维护</h2>
    <p class="page-desc">
      学院代码按学校院系公布顺序编排，专业编号沿用教育部专业代码。停用学院前须先停招其下专业，
      已被方案引用的专业不可删除，只能停招。
    </p>

    <el-row :gutter="16">
      <el-col :span="10">
        <div class="panel">
          <div class="panel-title">
            <span>学院列表</span>
            <el-button type="primary" size="small" @click="openCollege()">新增学院</el-button>
          </div>
          <div class="toolbar">
            <el-input v-model="collegeKeyword" placeholder="按名称筛选" clearable style="width: 200px">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <span class="spacer" />
            <span class="muted">共 {{ filteredColleges.length }} 个</span>
          </div>
          <el-table
            :data="filteredColleges"
            size="small"
            height="560"
            highlight-current-row
            @current-change="selectCollege"
          >
            <el-table-column prop="collegeId" label="编号" width="60" />
            <el-table-column prop="collegeName" label="学院名称" show-overflow-tooltip />
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.collegeStatus === '1' ? 'success' : 'info'" size="small">
                  {{ row.collegeStatus === '1' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openCollege(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="toggleCollege(row)">
                  {{ row.collegeStatus === '1' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :span="14">
        <div class="panel">
          <div class="panel-title">
            <span>专业列表 <span class="muted">{{ selectedCollege ? '· ' + selectedCollege.collegeName : '' }}</span></span>
            <el-button type="primary" size="small" @click="openMajor()">新增专业</el-button>
          </div>
          <div class="toolbar">
            <el-input v-model="majorKeyword" placeholder="专业名称/代码" clearable style="width: 200px" @change="loadMajors">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <span class="spacer" />
            <span class="muted">共 {{ majors.length }} 个专业</span>
          </div>
          <el-table :data="majors" size="small" height="560">
            <el-table-column prop="majorId" label="专业代码" width="90" />
            <el-table-column prop="majorName" label="专业名称" show-overflow-tooltip />
            <el-table-column prop="collegeName" label="所属学院" width="140" show-overflow-tooltip />
            <el-table-column prop="duration" label="学制" width="60" />
            <el-table-column prop="degree" label="学位" width="70" />
            <el-table-column prop="minCredit" label="最低学分" width="85" />
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.majorStatus === '1' ? 'success' : 'info'" size="small">
                  {{ row.majorStatus === '1' ? '在招' : '停招' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openMajor(row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="toggleMajor(row)">
                  {{ row.majorStatus === '1' ? '停招' : '恢复' }}
                </el-button>
                <el-button link type="danger" size="small" @click="removeMajor(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="collegeDialog" :title="collegeForm.isNew ? '新增学院' : '编辑学院'" width="460px">
      <el-form :model="collegeForm" label-width="90px">
        <el-form-item label="学院编号" required>
          <el-input v-model="collegeForm.collegeId" :disabled="!collegeForm.isNew" maxlength="2" placeholder="2 位数字" />
        </el-form-item>
        <el-form-item label="学院名称" required>
          <el-input v-model="collegeForm.collegeName" maxlength="20" />
        </el-form-item>
        <el-form-item label="学院简称">
          <el-input v-model="collegeForm.collegeShort" maxlength="10" />
        </el-form-item>
        <el-form-item label="教学负责人">
          <el-input v-model="collegeForm.collegeHead" maxlength="20" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="collegeForm.contactPhone" maxlength="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="collegeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCollege">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="majorDialog" :title="majorForm.isNew ? '新增专业' : '编辑专业'" width="500px">
      <el-form :model="majorForm" label-width="100px">
        <el-form-item label="专业代码" required>
          <el-input v-model="majorForm.majorId" :disabled="!majorForm.isNew" maxlength="7" placeholder="教育部专业代码" />
        </el-form-item>
        <el-form-item label="专业名称" required>
          <el-input v-model="majorForm.majorName" maxlength="30" />
        </el-form-item>
        <el-form-item label="所属学院" required>
          <el-select v-model="majorForm.collegeId" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.collegeId" :label="`${c.collegeId} ${c.collegeName}`" :value="c.collegeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="学制（年）">
          <el-input-number v-model="majorForm.duration" :min="2" :max="8" />
        </el-form-item>
        <el-form-item label="授予学位">
          <el-input v-model="majorForm.degree" maxlength="10" placeholder="工学 / 理学 / 管理学" />
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const colleges = ref([])
const majors = ref([])
const selectedCollege = ref(null)
const collegeKeyword = ref('')
const majorKeyword = ref('')

const collegeDialog = ref(false)
const majorDialog = ref(false)
const collegeForm = reactive({})
const majorForm = reactive({})

const filteredColleges = computed(() => {
  if (!collegeKeyword.value) return colleges.value
  return colleges.value.filter((c) => c.collegeName.includes(collegeKeyword.value))
})

async function loadColleges() {
  colleges.value = await api.colleges()
  if (!selectedCollege.value && colleges.value.length) {
    selectedCollege.value = colleges.value[0]
  }
}

async function loadMajors() {
  majors.value = await api.majors({
    collegeId: selectedCollege.value?.collegeId,
    keyword: majorKeyword.value || undefined
  })
}

function selectCollege(row) {
  if (!row) return
  selectedCollege.value = row
  loadMajors()
}

function openCollege(row) {
  Object.keys(collegeForm).forEach((k) => delete collegeForm[k])
  Object.assign(collegeForm, row ? { ...row, isNew: false } : { isNew: true, collegeStatus: '1' })
  collegeDialog.value = true
}

async function saveCollege() {
  const payload = { ...collegeForm }
  delete payload.isNew
  if (collegeForm.isNew) {
    await api.addCollege(payload)
  } else {
    await api.updateCollege(collegeForm.collegeId, payload)
  }
  ElMessage.success('保存成功')
  collegeDialog.value = false
  await loadColleges()
}

async function toggleCollege(row) {
  await api.collegeStatus(row.collegeId, row.collegeStatus === '1' ? '0' : '1')
  ElMessage.success('状态已更新')
  await loadColleges()
}

function openMajor(row) {
  Object.keys(majorForm).forEach((k) => delete majorForm[k])
  Object.assign(
    majorForm,
    row
      ? { ...row, isNew: false }
      : { isNew: true, collegeId: selectedCollege.value?.collegeId, duration: 4, minCredit: 160 }
  )
  majorDialog.value = true
}

async function saveMajor() {
  const payload = { ...majorForm }
  delete payload.isNew
  delete payload.programCount
  if (majorForm.isNew) {
    await api.addMajor(payload)
  } else {
    await api.updateMajor(majorForm.majorId, payload)
  }
  ElMessage.success('保存成功')
  majorDialog.value = false
  await loadMajors()
}

async function toggleMajor(row) {
  await api.majorStatus(row.majorId, row.majorStatus === '1' ? '0' : '1')
  ElMessage.success('状态已更新')
  await loadMajors()
}

async function removeMajor(row) {
  await ElMessageBox.confirm(`确认删除专业「${row.majorName}」？已被方案或班级引用的专业不可删除。`, '提示', {
    type: 'warning'
  })
  await api.deleteMajor(row.majorId)
  ElMessage.success('已删除')
  await loadMajors()
}

onMounted(async () => {
  await loadColleges()
  await loadMajors()
})
</script>
