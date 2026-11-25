<template>
  <div>
    <h2 class="page-title">辅修 / 双学位</h2>
    <p class="page-desc">
      接入学籍系统提供的辅修与双学位名单，按方案匹配应修课程并与主修课表比对时段：无冲突直接生成课表，
      存在冲突时可选择“允许时段重合”或“顺延至下一年开课”，结论写入修读记录。
    </p>

    <div class="panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="报名与录取" name="enroll">
          <div class="toolbar">
            <el-select v-model="enrollFilter" placeholder="录取状态" clearable style="width: 140px" @change="loadEnrolls">
              <el-option label="待审核" value="0" />
              <el-option label="已录取" value="1" />
              <el-option label="未录取" value="2" />
            </el-select>
            <span class="spacer" />
            <el-button v-if="isStudent" type="primary" size="small" @click="openEnroll">双学位报名</el-button>
          </div>
          <el-table :data="enrollPageRows" border size="small" max-height="480">
            <el-table-column prop="enrollId" label="报名编号" width="120" />
            <el-table-column prop="stuId" label="学号" width="120" />
            <el-table-column prop="majorName" label="双学位专业" min-width="160" />
            <el-table-column prop="enrollYear" label="学年" width="110" />
            <el-table-column prop="scoreAvg" label="主修平均分" width="110" />
            <el-table-column label="录取状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="['warning', 'success', 'info'][Number(row.auditStatus)]">
                  {{ { 0: '待审核', 1: '已录取', 2: '未录取' }[row.auditStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="修读状态" width="100">
              <template #default="{ row }">
                {{ { 0: '未开始', 1: '修读中', 2: '已退修', 3: '已结业' }[row.finishStatus] || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="enrollDate" label="录取日期" width="110" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="canAudit && row.auditStatus === '0'">
                  <el-button link type="success" size="small" @click="auditEnroll(row, '1')">录取</el-button>
                  <el-button link type="danger" size="small" @click="auditEnroll(row, '0')">不录取</el-button>
                </template>
                <el-button
                  v-if="canAudit && row.auditStatus === '1' && row.finishStatus === '1'"
                  link
                  type="warning"
                  size="small"
                  @click="quit(row)"
                >退出双学位</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="enrollPage"
            v-model:page-size="enrollSize"
            :page-sizes="[10, 20, 30, 50]"
            :total="enrolls.length"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 14px; justify-content: flex-end"
          />
        </el-tab-pane>

        <el-tab-pane label="修读记录与学分认定" name="study">
          <div class="toolbar">
            <el-radio-group v-model="programType" @change="loadStudy">
              <el-radio-button value="2">双学位</el-radio-button>
              <el-radio-button value="1">辅修</el-radio-button>
            </el-radio-group>
            <span class="spacer" />
            <el-button v-if="canAudit" size="small" @click="openGenerate">按方案生成修读记录</el-button>
            <el-button size="small" @click="openSummary">结业预审</el-button>
          </div>
          <el-table :data="studies" border size="small" max-height="480">
            <el-table-column prop="recId" label="记录编号" width="120" />
            <el-table-column prop="stuId" label="学号" width="120" />
            <el-table-column prop="majorName" label="修读专业" min-width="150" show-overflow-tooltip />
            <el-table-column prop="courseName" label="课程" min-width="160" show-overflow-tooltip />
            <el-table-column label="修读方式" width="110">
              <template #default="{ row }">{{ { 1: '随班修读', 2: '单独开班', 3: '免修认定' }[row.studyType] }}</template>
            </el-table-column>
            <el-table-column label="冲突处理" width="120">
              <template #default="{ row }">
                {{ { '01': '允许时段重合', '02': '顺延下一年', '03': '无冲突' }[row.conflictType] || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="credit" label="认定学分" width="90" />
            <el-table-column label="认定状态" width="95">
              <template #default="{ row }">
                <el-tag size="small" :type="['warning', 'success', 'info'][Number(row.auditStatus)]">
                  {{ { 0: '待认定', 1: '已认定', 2: '不认定' }[row.auditStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.auditStatus === '0'" link type="primary" size="small" @click="openApprove(row)">
                  学分认定
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="课表协调" name="conflict">
          <div class="toolbar">
            <el-input v-model="conflictStu" placeholder="学号" style="width: 180px" />
            <el-select v-model="term" style="width: 150px">
              <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
            </el-select>
            <el-button @click="checkConflict">比对课表</el-button>
          </div>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="比对结果基于主修班级课表与辅修/双学位教学班的时段重合情况；冲突课程可申请免听备案或顺延开课。"
            style="margin-bottom: 12px"
          />
          <el-table :data="conflicts" border size="small">
            <el-table-column prop="recId" label="修读记录" width="120" />
            <el-table-column prop="mainCourse" label="主修课程" min-width="160" />
            <el-table-column prop="subCourse" label="辅修课程" min-width="160" />
            <el-table-column label="时段" width="150">
              <template #default="{ row }">周{{ row.weekDay }} {{ row.startSlot }}-{{ row.endSlot }}节</template>
            </el-table-column>
            <el-table-column label="处理方式" width="150">
              <template #default="{ row }">{{ { '01': '允许时段重合', '02': '顺延下一年', '03': '无冲突' }[row.conflictType] }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="enrollDialog" title="双学位报名" width="480px">
      <el-form :model="enrollForm" label-width="100px">
        <el-form-item label="学号" required>
          <el-input v-model="enrollForm.stuId" :disabled="isStudent" />
        </el-form-item>
        <el-form-item label="双学位专业" required>
          <el-select v-model="enrollForm.majorId" filterable style="width: 100%">
            <el-option v-for="p in dualPrograms" :key="p.majorId" :label="p.majorName" :value="p.majorId" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名学年" required>
          <el-input v-model="enrollForm.enrollYear" placeholder="2026-2027" />
        </el-form-item>
        <el-form-item label="主修平均分">
          <el-input-number v-model="enrollForm.scoreAvg" :min="0" :max="100" :precision="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enrollDialog = false">取消</el-button>
        <el-button type="primary" @click="submitEnroll">提交报名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generateDialog" title="按方案生成修读记录" width="480px">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="学号" required>
          <el-input v-model="generateForm.stuId" />
        </el-form-item>
        <el-form-item label="修读类型">
          <el-radio-group v-model="generateForm.programType">
            <el-radio-button value="2">双学位</el-radio-button>
            <el-radio-button value="1">辅修</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="专业" required>
          <el-select v-model="generateForm.majorId" filterable style="width: 100%">
            <el-option v-for="m in majors" :key="m.majorId" :label="m.majorName" :value="m.majorId" />
          </el-select>
        </el-form-item>
        <el-form-item label="方案年级" required>
          <el-input v-model="generateForm.grade" placeholder="2026" />
        </el-form-item>
        <el-form-item label="学期" required>
          <el-input v-model="generateForm.term" placeholder="2026-2027-1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog = false">取消</el-button>
        <el-button type="primary" @click="doGenerate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="approveDialog" title="学分认定" width="460px">
      <el-descriptions :column="1" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="课程">{{ current?.courseName }}</el-descriptions-item>
        <el-descriptions-item label="课程学分">{{ current?.courseCredit }}</el-descriptions-item>
        <el-descriptions-item label="冲突处理">{{ current?.conflictType ? { '01': '允许时段重合', '02': '顺延下一年', '03': '无冲突' }[current.conflictType] : '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="认定学分">
          <el-input-number v-model="approveForm.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="认定结论">
          <el-radio-group v-model="approveForm.auditStatus">
            <el-radio-button value="1">认定</el-radio-button>
            <el-radio-button value="2">不认定</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="approveForm.remark" type="textarea" :rows="2" maxlength="150" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog = false">取消</el-button>
        <el-button type="primary" @click="doApprove">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="summaryDialog" title="结业预审" width="420px">
      <el-descriptions v-if="summary" :column="1" border size="small">
        <el-descriptions-item label="方案要求学分">{{ summary.required }}</el-descriptions-item>
        <el-descriptions-item label="已认定学分">{{ summary.earned }}</el-descriptions-item>
        <el-descriptions-item label="差额">{{ summary.gap }}</el-descriptions-item>
        <el-descriptions-item label="是否达标">
          <el-tag :type="summary.finish ? 'success' : 'danger'" size="small">{{ summary.finish ? '可结业' : '未达标' }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { userStore } from '../store/user'

const user = userStore()
const tab = ref('enroll')
const enrolls = ref([])
const enrollPage = ref(1)
const enrollSize = ref(20)
const enrollPageRows = computed(() => {
  const start = (enrollPage.value - 1) * enrollSize.value
  return enrolls.value.slice(start, start + enrollSize.value)
})
watch(enrollSize, () => { enrollPage.value = 1 })
const studies = ref([])
const conflicts = ref([])
const majors = ref([])
const terms = ref(['2026-2027-1'])
const term = ref('2026-2027-1')
const enrollFilter = ref('')
const programType = ref('2')
const conflictStu = ref('')
const enrollDialog = ref(false)
const generateDialog = ref(false)
const approveDialog = ref(false)
const summaryDialog = ref(false)
const summary = ref(null)
const current = ref(null)
const enrollForm = reactive({})
const generateForm = reactive({ programType: '2', grade: '2026' })
const approveForm = reactive({})

const isStudent = computed(() => user.user?.dataScope === '3' && !user.user?.teacherId)
/** 录取审批与修读记录生成仅对具备 P17 的角色开放（P0-01） */
const canAudit = computed(() => user.hasPerm('P17'))
const dualPrograms = computed(() => {
  const seen = new Set()
  return majors.value.filter((m) => {
    if (seen.has(m.majorId)) return false
    seen.add(m.majorId)
    return true
  })
})

async function loadEnrolls() {
  enrolls.value = await api.dualEnrolls({ auditStatus: enrollFilter.value || undefined })
  enrollPage.value = 1
}

async function loadStudy() {
  studies.value = await api.studyRecords({ programType: programType.value })
}

function openEnroll() {
  Object.keys(enrollForm).forEach((k) => delete enrollForm[k])
  Object.assign(enrollForm, {
    stuId: isStudent.value ? user.user.userId : '',
    enrollYear: '2026-2027',
    scoreAvg: 85
  })
  enrollDialog.value = true
}

async function submitEnroll() {
  await api.dualEnroll({ ...enrollForm })
  ElMessage.success('报名成功，等待录取')
  enrollDialog.value = false
  await loadEnrolls()
}

async function auditEnroll(row, pass) {
  await api.auditDualEnroll(row.enrollId, { pass })
  ElMessage.success('已更新录取状态')
  await loadEnrolls()
}

async function quit(row) {
  await api.finishDual(row.enrollId, '2')
  ElMessage.success('已退出双学位，历史修读记录保留')
  await loadEnrolls()
}

function openGenerate() {
  Object.assign(generateForm, { programType: programType.value, grade: '2026', term: term.value })
  generateDialog.value = true
}

async function doGenerate() {
  const res = await api.generateStudy({ ...generateForm })
  ElMessage.success(`已生成 ${res.created} 条修读记录`)
  generateDialog.value = false
  await loadStudy()
}

function openApprove(row) {
  current.value = row
  Object.assign(approveForm, { credit: Number(row.credit), auditStatus: '1', remark: '' })
  approveDialog.value = true
}

async function doApprove() {
  await api.approveStudy(current.value.recId, { ...approveForm })
  ElMessage.success('认定结果已保存')
  approveDialog.value = false
  await loadStudy()
}

async function openSummary() {
  const majorId = studies.value[0]?.majorId
  const stuId = studies.value[0]?.stuId
  if (!majorId || !stuId) {
    ElMessage.warning('暂无修读记录')
    return
  }
  summary.value = await api.creditSummary({ stuId, majorId, programType: programType.value })
  summaryDialog.value = true
}

async function checkConflict() {
  if (!conflictStu.value) {
    ElMessage.warning('请输入学号')
    return
  }
  conflicts.value = await api.dualConflicts({ stuId: conflictStu.value, term: term.value })
  if (!conflicts.value.length) ElMessage.success('未发现时段冲突')
}

onMounted(async () => {
  majors.value = await api.majors()
  try {
    terms.value = await api.terms()
  } catch (e) {
    terms.value = [term.value]
  }
  await loadEnrolls()
  await loadStudy()
  conflictStu.value = isStudent.value ? user.user.userId : ''
})
</script>
