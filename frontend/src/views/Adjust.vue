<template>
  <div>
    <h2 class="page-title">调课管理</h2>
    <p class="page-desc">
      教师提交一次性或长期调课申请，系统先做冲突检测并给出候选方案，经学院教务员初审、教务处终审后生效，
      并保留变更前后快照；一次性调课到期可撤销恢复原安排。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="term" style="width: 150px" @change="load">
          <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="status" placeholder="审批状态" clearable style="width: 140px" @change="load">
          <el-option label="待审批" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="已驳回" value="2" />
        </el-select>
        <span class="spacer" />
        <el-button type="primary" @click="openApply">申请调课</el-button>
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
            <el-tag size="small" :type="['warning', 'success', 'danger'][Number(row.approveStatus)]">
              {{ { 0: '待审批', 1: '已通过', 2: '已驳回' }[row.approveStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.approveStatus === '0' && hasPerm('P11')">
              <el-button link type="success" size="small" @click="approve(row)">通过</el-button>
              <el-button link type="danger" size="small" @click="reject(row)">驳回</el-button>
            </template>
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
          <el-select v-model="form.roomId" filterable style="width: 100%">
            <el-option
              v-for="r in rooms"
              :key="r.roomId"
              :label="`${r.roomName}（${r.capacity}座）`"
              :value="r.roomId"
            />
          </el-select>
        </el-form-item>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { hasPerm } from '../store/user'

const rows = ref([])
const schedules = ref([])
const rooms = ref([])
const terms = ref(['2026-2027-1'])
const term = ref('2026-2027-1')
const status = ref('')
const applyDialog = ref(false)
const form = reactive({ adjustType: '1', weekDay: '1', startSlot: 1, roomId: '' })
const periods = [1, 3, 5, 7, 9, 11]
const dayName = (d) => ({ 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五' }[d] || '')

async function load() {
  rows.value = await api.adjustments({ term: term.value, approveStatus: status.value || undefined })
}

async function openApply() {
  Object.assign(form, { adjustType: '1', weekDay: '1', startSlot: 1, roomId: '', schedId: '', reason: '', effectDate: '' })
  applyDialog.value = true
  schedules.value = await api.scheduleResults({ term: term.value })
  rooms.value = (await api.rooms()).filter((r) => r.roomStatus === '1')
}

async function submit() {
  await api.applyAdjust({ ...form, endSlot: form.startSlot + 1 })
  ElMessage.success('调课申请已提交，等待审批')
  applyDialog.value = false
  await load()
}

async function approve(row) {
  const { value } = await ElMessageBox.prompt('审批意见（可选）', '审批通过', { inputPlaceholder: '同意' })
  await api.approveAdjust(row.adjustId, { opinion: value })
  ElMessage.success('已批准，课表已更新并通知师生')
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
