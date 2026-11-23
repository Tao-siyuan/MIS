<template>
  <div>
    <h2 class="page-title">转专业学分转换</h2>
    <p class="page-desc">
      学生提出申请并录入成绩单与原课程大纲，转入学院对照新专业培养方案按“同名课程直接认定、相近课程降学分认定、
      实践学分按项目认定”三档判定，形成转换清单后报教务处审核。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="status" placeholder="审核状态" clearable style="width: 140px" @change="load">
          <el-option label="待审核" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="已驳回" value="2" />
        </el-select>
        <span class="spacer" />
        <el-button type="primary" @click="openApply">发起申请</el-button>
      </div>

      <el-table :data="rows" border size="small">
        <el-table-column prop="applyId" label="申请编号" width="120" />
        <el-table-column prop="stuId" label="学号" width="120" />
        <el-table-column prop="fromMajorName" label="原专业" min-width="140" show-overflow-tooltip />
        <el-table-column prop="toMajorName" label="转入专业" min-width="140" show-overflow-tooltip />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column prop="applyCredit" label="申请学分" width="90" />
        <el-table-column prop="collegeOpinion" label="学院意见" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'danger'][Number(row.auditStatus)]">
              {{ { 0: '待审核', 1: '已通过', 2: '已驳回' }[row.auditStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">明细</el-button>
            <el-button
              v-if="row.auditStatus === '0' && hasPerm('P24')"
              link
              type="success"
              size="small"
              @click="openAudit(row)"
            >
              审核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="applyDialog" title="转专业学分转换申请" width="720px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="学号" required>
              <el-input v-model="form.stuId" :disabled="isStudent" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="原专业" required>
              <el-select v-model="form.fromMajor" filterable style="width: 100%">
                <el-option v-for="m in majors" :key="m.majorId" :label="m.majorName" :value="m.majorId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="转入专业" required>
              <el-select v-model="form.toMajor" filterable style="width: 100%">
                <el-option v-for="m in majors" :key="m.majorId" :label="m.majorName" :value="m.majorId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="panel-title">转换明细</div>
      <el-table :data="form.items" size="small" border>
        <el-table-column label="原修课程" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.sourceCourseId" filterable size="small" style="width: 100%">
              <el-option
                v-for="c in courses"
                :key="c.courseId"
                :label="`${c.courseName}（${c.credit}学分）`"
                :value="c.courseId"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="认定课程" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.targetCourseId" filterable clearable size="small" style="width: 100%">
              <el-option
                v-for="c in courses"
                :key="c.courseId"
                :label="`${c.courseName}（${c.credit}学分）`"
                :value="c.courseId"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="转换类型" width="170">
          <template #default="{ row }">
            <el-select v-model="row.transferType" size="small">
              <el-option label="同名课程" value="01" />
              <el-option label="相近课程降学分" value="02" />
              <el-option label="实践项目" value="03" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="认定学分" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.targetCredit" :min="0.5" :max="20" :step="0.5" :precision="1" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="form.items.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button style="margin-top: 10px" size="small" @click="addItem">新增明细</el-button>

      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交申请</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawer" size="52%" :title="`转换明细 · ${current?.applyId || ''}`">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="学号">{{ current?.stuId }}</el-descriptions-item>
        <el-descriptions-item label="申请学分">{{ current?.applyCredit }}</el-descriptions-item>
        <el-descriptions-item label="原专业">{{ current?.fromMajorName }}</el-descriptions-item>
        <el-descriptions-item label="转入专业">{{ current?.toMajorName }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2">{{ current?.auditOpinion || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="items" border size="small">
        <el-table-column prop="seqNo" label="序号" width="60" />
        <el-table-column prop="sourceCourseName" label="原修课程" min-width="160" />
        <el-table-column prop="sourceCredit" label="原学分" width="80" />
        <el-table-column prop="targetCourseName" label="认定课程" min-width="160" />
        <el-table-column prop="targetCredit" label="认定学分" width="90" />
        <el-table-column label="类型" width="140">
          <template #default="{ row }">{{ { '01': '同名课程', '02': '相近课程', '03': '实践项目' }[row.transferType] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="['warning', 'success', 'info'][Number(row.itemStatus)]">
              {{ { 0: '待认定', 1: '同意', 2: '不同意' }[row.itemStatus] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="auditDialog" title="学分转换审核" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审核结论">
          <el-radio-group v-model="auditForm.pass">
            <el-radio-button value="1">通过</el-radio-button>
            <el-radio-button value="0">退回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学院意见">
          <el-input v-model="auditForm.collegeOpinion" type="textarea" :rows="2" maxlength="150" />
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="2" maxlength="150" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="通过后向学籍与成绩系统推送认定结论，学分计入转入专业。"
        />
      </el-form>
      <template #footer>
        <el-button @click="auditDialog = false">取消</el-button>
        <el-button type="primary" @click="doAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { hasPerm, userStore } from '../store/user'

const user = userStore()
const rows = ref([])
const items = ref([])
const majors = ref([])
const courses = ref([])
const status = ref('')
const applyDialog = ref(false)
const detailDrawer = ref(false)
const auditDialog = ref(false)
const current = ref(null)
const form = reactive({ items: [] })
const auditForm = reactive({ pass: '1', opinion: '', collegeOpinion: '' })

const isStudent = computed(() => !!user.user?.classId)

async function load() {
  rows.value = await api.creditApplies({ auditStatus: status.value || undefined })
}

function openApply() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { stuId: isStudent.value ? user.user.userId : '', items: [] })
  addItem()
  applyDialog.value = true
}

function addItem() {
  form.items.push({ sourceCourseId: '', targetCourseId: '', targetCredit: 3, transferType: '01' })
}

async function submit() {
  const payload = {
    stuId: form.stuId,
    fromMajor: form.fromMajor,
    toMajor: form.toMajor,
    items: form.items.filter((i) => i.sourceCourseId)
  }
  await api.applyCredit(payload)
  ElMessage.success('申请已提交')
  applyDialog.value = false
  await load()
}

async function openDetail(row) {
  current.value = row
  items.value = await api.creditItems(row.applyId)
  detailDrawer.value = true
}

function openAudit(row) {
  current.value = row
  Object.assign(auditForm, { pass: '1', opinion: '', collegeOpinion: '' })
  auditDialog.value = true
}

async function doAudit() {
  await api.auditCredit(current.value.applyId, { ...auditForm })
  ElMessage.success('审核完成')
  auditDialog.value = false
  await load()
}

onMounted(async () => {
  majors.value = await api.majors()
  courses.value = await api.courses()
  await load()
})
</script>
