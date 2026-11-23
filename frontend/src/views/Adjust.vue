<template>
  <div>
    <h2 class="page-title">调课管理</h2>
    <p class="page-desc">
      教师提交一次性或长期调课申请，系统先做冲突检测并给出候选方案；申请先由学院教务初审，
      初审通过后交教务处备案，备案完成即生效，并保留变更前后快照；
      待审批的申请可由申请人撤回，一次性调课到期可撤销恢复原安排。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="term" style="width: 150px" @change="load">
          <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="status" placeholder="审批状态" clearable style="width: 140px" @change="load">
          <el-option label="学院待审" value="0" />
          <el-option label="待教务处备案" value="4" />
          <el-option label="已备案" value="1" />
          <el-option label="已驳回" value="2" />
          <el-option label="已撤回" value="3" />
        </el-select>
        <span class="spacer" />
        <el-button v-if="canApply" type="primary" @click="openApply">申请调课</el-button>
      </div>

      <el-table :data="rows" border size="small">
        <el-table-column prop="adjustId" label="调课编号" width="120" />
        <el-table-column prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="教师" width="95" />
        <el-table-column prop="classIds" label="班级" min-width="110" show-overflow-tooltip />
        <el-table-column label="类型" width="85">
          <template #default="{ row }">{{ row.adjustType === '2' ? '长期' : '一次性' }}</template>
        </el-table-column>
        <el-table-column prop="oldValue" label="原安排" width="160" />
        <el-table-column prop="newValue" label="新安排" width="160" />
        <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
        <el-table-column prop="effectDate" label="生效日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.approveStatus)">
              {{ statusName(row.approveStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <!-- 学院教务初审：通过后转教务处备案 -->
            <template v-if="row.approveStatus === '0' && isCollegeOfficer">
              <el-button link type="success" size="small" @click="collegeReview(row, '1')">初审通过</el-button>
              <el-button link type="danger" size="small" @click="collegeReview(row, '0')">初审驳回</el-button>
            </template>
            <!-- 教务处备案：学院初审通过（或直接提交）的申请最终生效 -->
            <template v-if="(row.approveStatus === '4' || row.approveStatus === '0') && hasPerm('P11')">
              <el-button link type="success" size="small" @click="approve(row)">备案通过</el-button>
              <el-button link type="danger" size="small" @click="reject(row)">驳回</el-button>
            </template>
            <!-- 待审批的申请，申请人（本人教学班）可自行撤回 -->
            <el-button
              v-if="row.approveStatus === '0' && canWithdraw(row)"
              link
              type="info"
              size="small"
              @click="withdraw(row)"
            >撤回</el-button>
            <el-button
              v-if="row.approveStatus === '1' && row.restoreFlag !== '1' && (hasPerm('P11') || hasPerm('P09'))"
              link
              type="warning"
              size="small"
              @click="restore(row)"
            >撤销调课</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="applyDialog" title="调课申请" width="560px">
      <el-form label-width="96px">
        <el-form-item label="原排课" required>
          <el-select v-model="form.schedId" filterable style="width: 100%" placeholder="选择本人/本学期的排课安排">
            <el-option
              v-for="s in schedules"
              :key="s.schedId"
              :label="`${s.courseName} · ${dayName(s.weekDay)} ${s.startSlot}-${s.endSlot}节 ${s.roomName}`"
              :value="s.schedId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调课类型">
          <el-radio-group v-model="form.adjustType">
            <el-radio-button value="1">一次性</el-radio-button>
            <el-radio-button value="2">长期</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="新星期">
          <el-select v-model="form.weekDay" style="width: 130px">
            <el-option v-for="d in 5" :key="d" :label="dayName(String(d))" :value="String(d)" />
          </el-select>
        </el-form-item>
        <el-form-item label="新节次">
          <el-select v-model="form.startSlot" style="width: 130px">
            <el-option v-for="p in periods" :key="p" :label="`${p}-${p + 1} 节`" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="新教室">
          <el-select
            v-model="form.roomId"
            filterable
            style="width: 100%"
            :placeholder="form.schedId ? `当前时段可用教室 ${roomOptions.length} 间` : '请先选择原排课与新时间'"
            :disabled="!form.schedId"
          >
            <el-option
              v-for="r in roomOptions"
              :key="r.roomId"
              :label="`${r.roomName}（${r.capacity}座 · ${roomTypeName(r.roomType)}）`"
              :value="r.roomId"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="roomHint && roomHint.conflictStudents > 0"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 14px"
          :title="`新时段与 ${roomHint.conflictStudents} 名学生的已有课程冲突：${(roomHint.conflictDetails || []).join('；')}`"
        />
        <el-alert
          v-else-if="roomHint"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 14px"
          title="新时段学生无课程冲突"
        />
        <el-form-item v-if="form.adjustType === '1'" label="生效日期">
          <el-date-picker v-model="form.effectDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调课原因">
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="150" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交（含冲突检测）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { hasPerm, userStore } from '../store/user'

const user = userStore()
/** 学院教务（P21，且非教务处）负责调课初审，通过后交教务处备案 */
const isCollegeOfficer = computed(() => hasPerm('P21') && !hasPerm('P11'))
/** 任课教师本人可申请调课，教务处可代为发起 */
const canApply = computed(() => !!user.user?.teacherId || hasPerm('P11'))

const rows = ref([])
const schedules = ref([])
const roomOptions = ref([])
const roomHint = ref(null)
const terms = ref(['2026-2027-1'])
const term = ref('2026-2027-1')
const status = ref('')
const applyDialog = ref(false)
const form = reactive({ adjustType: '1', weekDay: '1', startSlot: 1, roomId: '' })
const periods = [1, 3, 5, 7, 9, 11]
const dayName = (d) => ({ 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五' }[d] || '')
const roomTypeName = (t) => ({ 1: '普通教室', 2: '多媒体教室', 3: '机房', 4: '实验室' }[t] || '教室')
const statusName = (s) =>
  ({ 0: '学院待审', 4: '待教务处备案', 1: '已备案', 2: '已驳回', 3: '已撤回' }[s] || s)
const STATUS_TYPES = { 0: 'warning', 4: 'info', 1: 'success', 2: 'danger', 3: 'info' }
const statusType = (s) => STATUS_TYPES[s] || 'info'
/** 待审批申请：本人任教的教学班可自行撤回；具备审批权限的角色亦可代为撤回 */
const canWithdraw = (row) =>
  hasPerm('P11') || (!!user.user?.teacherId && user.user.teacherId === row.teacherId)

/** 选定原排课与新时间后，只拉取该时段可用且满足教学形式/人数的教室，并给出学生冲突人数 */
async function loadAvailableRooms() {
  if (!form.schedId) {
    roomOptions.value = []
    roomHint.value = null
    return
  }
  try {
    const res = await api.adjustAvailableRooms({
      schedId: form.schedId,
      weekDay: form.weekDay,
      startSlot: form.startSlot,
      endSlot: Number(form.startSlot) + 1
    })
    roomOptions.value = res.rooms || []
    roomHint.value = res
    if (!roomOptions.value.some((r) => r.roomId === form.roomId)) {
      form.roomId = roomOptions.value[0]?.roomId || ''
    }
  } catch (e) {
    roomOptions.value = []
    roomHint.value = null
  }
}

watch(
  () => [form.schedId, form.weekDay, form.startSlot],
  () => loadAvailableRooms()
)

async function load() {
  rows.value = await api.adjustments({ term: term.value, approveStatus: status.value || undefined })
}

async function openApply() {
  Object.assign(form, { adjustType: '1', weekDay: '1', startSlot: 1, roomId: '', schedId: '', reason: '', effectDate: '' })
  roomOptions.value = []
  roomHint.value = null
  applyDialog.value = true
  schedules.value = await api.scheduleResults({ term: term.value })
}

async function submit() {
  if (!form.schedId || !form.roomId) {
    ElMessage.warning('请先选择原排课、新时间与可用教室')
    return
  }
  await api.applyAdjust({ ...form, endSlot: form.startSlot + 1 })
  ElMessage.success('调课申请已提交，等待审批')
  applyDialog.value = false
  await load()
}

/** 学院教务初审：通过后转教务处备案，驳回须填理由 */
async function collegeReview(row, pass) {
  const { value } = await ElMessageBox.prompt(
    pass === '1' ? '学院初审意见' : '请填写驳回理由',
    `${pass === '1' ? '初审通过' : '初审驳回'}：${row.courseName}`,
    {
      inputValue: pass === '1' ? '调课理由充分，同意上报教务处备案' : '',
      inputValidator: (v) => (pass === '1' || (v && v.trim()) ? true : '驳回理由必填')
    }
  )
  await api.collegeReviewAdjust(row.adjustId, { pass, opinion: value })
  ElMessage.success(pass === '1' ? '已通过学院初审，等待教务处备案' : '已驳回该调课申请')
  await load()
}

async function approve(row) {
  const { value } = await ElMessageBox.prompt('审批意见（可选）', '审批通过', { inputPlaceholder: '同意' })
  await api.approveAdjust(row.adjustId, { opinion: value })
  ElMessage.success('已备案，课表已更新并通知师生')
  await load()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请填写驳回理由', '驳回申请', {
    inputValidator: (v) => (v && v.trim() ? true : '驳回理由必填')
  })
  await api.rejectAdjust(row.adjustId, { opinion: value })
  ElMessage.success('已驳回')
  await load()
}

async function withdraw(row) {
  await ElMessageBox.confirm('撤回后该调课申请将失效，确认撤回？', '撤回申请', { type: 'warning' })
  await api.withdrawAdjust(row.adjustId)
  ElMessage.success('已撤回调课申请')
  await load()
}

async function restore(row) {
  await ElMessageBox.confirm('确认按原安排快照恢复课表？', '撤销调课', { type: 'warning' })
  await api.restoreAdjust(row.adjustId)
  ElMessage.success('已恢复原安排')
  await load()
}

onMounted(async () => {
  try {
    terms.value = await api.terms()
  } catch (e) {
    terms.value = [term.value]
  }
  await load()
})
</script>
