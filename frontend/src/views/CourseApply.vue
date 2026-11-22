<template>
  <div>
    <h2 class="page-title">新增课程申报</h2>
    <p class="page-desc">
      教师可提出库外课程的新增申请，经学院初审、教务处审批后按编码规则生成课程号入库；驳回须填写理由，
      教师修改后可重新提交。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-radio-group v-model="scope" @change="load">
          <el-radio-button value="mine">我的申报</el-radio-button>
          <el-radio-button v-if="hasPerm('P07')" value="all">全部申报</el-radio-button>
        </el-radio-group>
        <el-select v-model="status" placeholder="审核状态" clearable style="width: 140px" @change="load">
          <el-option label="待审核" value="0" />
          <el-option label="已批准" value="1" />
          <el-option label="已驳回" value="2" />
        </el-select>
        <span class="spacer" />
        <el-button type="primary" @click="openApply">课程申报</el-button>
      </div>

      <el-table :data="rows" border size="small">
        <el-table-column prop="applyId" label="申请编号" width="120" />
        <el-table-column prop="courseName" label="拟开课程" min-width="170" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="申报教师" width="100" />
        <el-table-column label="性质" width="100">
          <template #default="{ row }">{{ typeName(row.courseType) }}</template>
        </el-table-column>
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column prop="hours" label="学时" width="70" />
        <el-table-column prop="applyReason" label="开设理由" min-width="200" show-overflow-tooltip />
        <el-table-column prop="applyTime" label="申报时间" width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['info', 'success', 'danger'][Number(row.auditStatus)]">
              {{ { 0: '待审核', 1: '已批准', 2: '已驳回' }[row.auditStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditOpinion" label="审核意见" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.auditStatus === '0' && hasPerm('P07')" link type="primary" size="small" @click="openAudit(row)">
              审批
            </el-button>
            <span v-else-if="row.courseId" class="muted">课程号 {{ row.courseId }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="applyDialog" title="新增课程申报" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="课程名称" required>
          <el-input v-model="form.courseName" maxlength="40" />
        </el-form-item>
        <el-form-item label="课程性质" required>
          <el-select v-model="form.courseType" style="width: 100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="拟定学分">
          <el-input-number v-model="form.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="拟定学时">
          <el-input-number v-model="form.hours" :min="8" :max="200" :step="8" />
        </el-form-item>
        <el-form-item label="开设理由">
          <el-input v-model="form.applyReason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交申报</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditDialog" title="新增课程审批" width="520px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="申请编号">{{ current?.applyId }}</el-descriptions-item>
        <el-descriptions-item label="申报教师">{{ current?.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ current?.courseName }}</el-descriptions-item>
        <el-descriptions-item label="学分/学时">{{ current?.credit }} / {{ current?.hours }}</el-descriptions-item>
        <el-descriptions-item label="开设理由" :span="2">{{ current?.applyReason }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item label="审批结论">
          <el-radio-group v-model="auditForm.pass">
            <el-radio-button value="1">批准入库</el-radio-button>
            <el-radio-button value="0">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.pass === '1'" label="开课学院" required>
          <el-select v-model="auditForm.openCollege" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核意见" :required="auditForm.pass === '0'">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialog = false">取消</el-button>
        <el-button type="primary" @click="doAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { hasPerm } from '../store/user'

const rows = ref([])
const colleges = ref([])
const scope = ref('mine')
const status = ref('')
const applyDialog = ref(false)
const auditDialog = ref(false)
const form = reactive({})
const auditForm = reactive({ pass: '1', opinion: '', openCollege: '' })
const current = ref(null)

const types = [
  { value: '14', label: '14 专业选修' },
  { value: '13', label: '13 专业必修' },
  { value: '15', label: '15 通识' },
  { value: '16', label: '16 实践教学' }
]
const typeName = (t) => (types.find((x) => x.value === t)?.label || t)

async function load() {
  rows.value = await api.applies({
    status: status.value || undefined,
    mine: scope.value === 'mine' ? '1' : undefined
  })
}

function openApply() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { courseType: '14', credit: 2, hours: 32 })
  applyDialog.value = true
}

async function submitApply() {
  await api.applyCourse({ ...form })
  ElMessage.success('申报已提交，等待教务处审批')
  applyDialog.value = false
  await load()
}

function openAudit(row) {
  current.value = row
  auditForm.pass = '1'
  auditForm.opinion = ''
  auditForm.openCollege = ''
  auditDialog.value = true
}

async function doAudit() {
  await api.auditApply(current.value.applyId, { ...auditForm })
  ElMessage.success('审批完成')
  auditDialog.value = false
  await load()
}

onMounted(async () => {
  colleges.value = await api.colleges()
  await load()
})
</script>
