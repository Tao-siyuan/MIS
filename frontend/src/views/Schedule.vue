<template>
  <div>
    <h2 class="page-title">排课工作台</h2>
    <p class="page-desc">
      排课引擎按“硬约束必满足、软约束加权优化”为教学班分配时间与教室：硬约束包括教师、教室、班级不冲突，
      教室容量与类型匹配，不占用教师不可用时间；存在未处理冲突时不允许发布课表（BR-05）。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="term" style="width: 150px" @change="loadAll">
          <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
        </el-select>
        <el-tag effect="plain">教学班 {{ stat.tcCount || 0 }}</el-tag>
        <el-tag effect="plain">排课段 {{ stat.schedCount || 0 }}</el-tag>
        <el-tag effect="plain" type="success">已发布 {{ stat.publishedCount || 0 }}</el-tag>
        <el-tag effect="plain" :type="stat.pendingConflict ? 'danger' : 'info'">未处理冲突 {{ stat.pendingConflict || 0 }}</el-tag>
        <span class="spacer" />
        <el-button @click="auto">自动排课</el-button>
        <el-button type="primary" @click="publish">发布课表</el-button>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="排课结果" name="result">
          <el-table :data="results" border size="small" max-height="520">
            <el-table-column prop="schedId" label="排课编号" width="115" />
            <el-table-column prop="tcId" label="教学班" width="115" />
            <el-table-column prop="courseName" label="课程" min-width="160" show-overflow-tooltip />
            <el-table-column prop="teacherName" label="教师" width="95" />
            <el-table-column prop="classIds" label="班级" min-width="120" show-overflow-tooltip />
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ dayName(row.weekDay) }} {{ row.startSlot }}-{{ row.endSlot }}节</template>
            </el-table-column>
            <el-table-column prop="weekRange" label="周次" width="80" />
            <el-table-column prop="roomName" label="教室" width="130" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="['info', 'warning', 'success', 'primary'][Number(row.schedStatus)]">
                  {{ { 0: '待审核', 1: '已审核', 2: '已发布', 3: '已调课' }[row.schedStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openAssign(row)">调整</el-button>
                <el-button v-if="row.lockFlag !== '1'" link type="danger" size="small" @click="removeSched(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="conflict">
          <template #label>冲突处理 <el-badge v-if="pendingCount" :value="pendingCount" type="danger" /></template>
          <el-table :data="conflictRows" border size="small" max-height="520">
            <el-table-column prop="conflictId" label="冲突编号" width="115" />
            <el-table-column prop="tcId" label="教学班" width="115" />
            <el-table-column prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ conflictTypeName(row.conflictType) }}</template>
            </el-table-column>
            <el-table-column prop="conflictDesc" label="冲突描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="suggestPlan" label="建议方案" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="85">
              <template #default="{ row }">
                <el-tag size="small" :type="row.handleStatus === '1' ? 'success' : 'danger'">
                  {{ row.handleStatus === '1' ? '已处理' : '未处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.handleStatus === '0'" link type="primary" size="small" @click="openResolve(row)">
                  处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="教室资源" name="room">
          <div class="toolbar">
            <span class="muted">教室台账来源：固定资产管理子系统，此处维护可用状态与容量。</span>
            <span class="spacer" />
            <el-button type="primary" size="small" @click="openRoom()">新增教室</el-button>
          </div>
          <el-table :data="rooms" border size="small" max-height="480">
            <el-table-column prop="roomId" label="教室编号" width="110" />
            <el-table-column prop="roomName" label="教室名称" min-width="170" />
            <el-table-column prop="building" label="楼宇" width="110" />
            <el-table-column prop="capacity" label="容量" width="80" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ roomTypeName(row.roomType) }}</template>
            </el-table-column>
            <el-table-column prop="availableTime" label="可用时段" min-width="160" />
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag size="small" :type="row.roomStatus === '1' ? 'success' : 'danger'">
                  {{ row.roomStatus === '1' ? '可用' : '维修停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRoom(row)">编辑</el-button>
                <el-button link :type="row.roomStatus === '1' ? 'danger' : 'success'" size="small" @click="toggleRoom(row)">
                  {{ row.roomStatus === '1' ? '停用' : '恢复' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="排课规则" name="rule">
          <div class="toolbar">
            <span class="muted">规则按生效次序执行，数值小者优先。</span>
            <span class="spacer" />
            <el-button type="primary" size="small" @click="openRule()">新增规则</el-button>
          </div>
          <el-table :data="ruleRows" border size="small">
            <el-table-column prop="ruleId" label="编号" width="80" />
            <el-table-column prop="ruleName" label="规则名称" width="180" />
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ ruleTypeName(row.ruleType) }}</template>
            </el-table-column>
            <el-table-column prop="ruleContent" label="规则内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="priorityNo" label="次序" width="70" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enable === '1' ? 'success' : 'info'">{{ row.enable === '1' ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openRule(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="教室利用率" name="usage">
          <el-table :data="stat.roomUsage || []" border size="small" max-height="480">
            <el-table-column prop="roomId" label="教室" width="110" />
            <el-table-column prop="roomName" label="名称" min-width="170" />
            <el-table-column prop="capacity" label="容量" width="90" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ roomTypeName(row.roomType) }}</template>
            </el-table-column>
            <el-table-column prop="used" label="已排段数" width="100" />
            <el-table-column label="利用率" min-width="220">
              <template #default="{ row }">
                <el-progress :percentage="Math.min(row.rate, 100)" :stroke-width="10" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="assignDialog" title="调整排课安排" width="520px">
      <el-form label-width="90px">
        <el-form-item label="教学班">
          <span>{{ assignForm.tcId }} · {{ assignForm.courseName }}</span>
        </el-form-item>
        <el-form-item label="星期">
          <el-select v-model="assignForm.weekDay" style="width: 120px">
            <el-option v-for="d in 5" :key="d" :label="dayName(String(d))" :value="String(d)" />
          </el-select>
        </el-form-item>
        <el-form-item label="节次">
          <el-select v-model="assignForm.startSlot" style="width: 120px">
            <el-option v-for="p in periods" :key="p" :label="`${p}-${p + 1} 节`" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="assignForm.roomId" filterable style="width: 100%">
            <el-option
              v-for="r in rooms.filter((x) => x.roomStatus === '1')"
              :key="r.roomId"
              :label="`${r.roomName}（${r.capacity}座 · ${roomTypeName(r.roomType)}）`"
              :value="r.roomId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="周次">
          <el-input v-model="assignForm.weekRange" style="width: 140px" />
        </el-form-item>
        <el-alert v-if="checkMsg" :type="checkPass ? 'success' : 'error'" :title="checkMsg" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button @click="check">冲突检测</el-button>
        <el-button @click="assignDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!checkPass" @click="saveAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveDialog" title="冲突处理" width="500px">
      <el-alert type="warning" :closable="false" :title="`${resolveRow?.courseName}：${resolveRow?.conflictDesc}`" show-icon style="margin-bottom: 14px" />
      <el-form label-width="90px">
        <el-form-item label="新星期">
          <el-select v-model="resolveForm.weekDay" style="width: 120px">
            <el-option v-for="d in 5" :key="d" :label="dayName(String(d))" :value="String(d)" />
          </el-select>
        </el-form-item>
        <el-form-item label="新节次">
          <el-select v-model="resolveForm.startSlot" style="width: 120px">
            <el-option v-for="p in periods" :key="p" :label="`${p}-${p + 1} 节`" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室">
          <el-select v-model="resolveForm.roomId" filterable style="width: 100%">
            <el-option
              v-for="r in rooms.filter((x) => x.roomStatus === '1')"
              :key="r.roomId"
              :label="`${r.roomName}（${r.capacity}座）`"
              :value="r.roomId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveDialog = false">取消</el-button>
        <el-button type="primary" @click="doResolve">按此方案处理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roomDialog" title="教室维护" width="500px">
      <el-form :model="roomForm" label-width="90px">
        <el-form-item label="教室编号" required>
          <el-input v-model="roomForm.roomId" :disabled="!roomForm.isNew" maxlength="6" />
        </el-form-item>
        <el-form-item label="教室名称" required>
          <el-input v-model="roomForm.roomName" />
        </el-form-item>
        <el-form-item label="楼宇">
          <el-input v-model="roomForm.building" />
        </el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="roomForm.capacity" :min="10" :max="300" :step="10" />
        </el-form-item>
        <el-form-item label="教室类型">
          <el-select v-model="roomForm.roomType" style="width: 100%">
            <el-option label="普通教室" value="1" />
            <el-option label="多媒体教室" value="2" />
            <el-option label="机房" value="3" />
            <el-option label="实验室" value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用时段">
          <el-input v-model="roomForm.availableTime" placeholder="周一至周五 1-12 节" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRoom">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleDialog" title="排课规则维护" width="520px">
      <el-form :model="ruleForm" label-width="90px">
        <el-form-item label="规则编号" required>
          <el-input v-model="ruleForm.ruleId" :disabled="!ruleForm.isNew" maxlength="4" />
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.ruleName" maxlength="30" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option label="01 优先级" value="01" />
            <el-option label="02 教室匹配" value="02" />
            <el-option label="03 时间约束" value="03" />
            <el-option label="04 冲突检测" value="04" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则内容" required>
          <el-input v-model="ruleForm.ruleContent" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
        <el-form-item label="生效次序">
          <el-input-number v-model="ruleForm.priorityNo" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="ruleForm.enable" active-value="1" inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const term = ref('2026-2027-1')
const terms = ref(['2026-2027-1'])
const tab = ref('result')
const stat = ref({})
const results = ref([])
const conflictRows = ref([])
const rooms = ref([])
const ruleRows = ref([])
const periods = [1, 3, 5, 7, 9, 11]

const assignDialog = ref(false)
const assignForm = reactive({})
const checkMsg = ref('')
const checkPass = ref(false)
const resolveDialog = ref(false)
const resolveRow = ref(null)
const resolveForm = reactive({ weekDay: '1', startSlot: 1, roomId: '' })
const roomDialog = ref(false)
const roomForm = reactive({})
const ruleDialog = ref(false)
const ruleForm = reactive({})

const pendingCount = computed(() => conflictRows.value.filter((c) => c.handleStatus === '0').length)
const dayName = (d) => ({ 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }[d] || '')
const roomTypeName = (t) => ({ 1: '普通', 2: '多媒体', 3: '机房', 4: '实验室' }[t] || t)
const ruleTypeName = (t) => ({ '01': '优先级', '02': '教室匹配', '03': '时间约束', '04': '冲突检测' }[t] || t)
const conflictTypeName = (t) =>
  ({ '01': '教师冲突', '02': '教室冲突', '03': '班级冲突', '04': '教室容量', '05': '教室类型', '06': '时段不可用' }[t] || t)

async function loadAll() {
  stat.value = await api.workbench({ term: term.value })
  results.value = await api.scheduleResults({ term: term.value })
  conflictRows.value = await api.conflicts({ term: term.value })
  rooms.value = await api.rooms()
  ruleRows.value = await api.rules()
}

async function auto() {
  await ElMessageBox.confirm('系统将按硬约束贪心初排，并为无法安排的教学班生成冲突记录。是否开始？', '自动排课', {
    type: 'warning'
  })
  const res = await api.autoSchedule({ term: term.value })
  ElMessage.success(`排课完成：新增安排 ${res.assigned} 个教学班，未分配 ${res.failed} 个，耗时 ${res.elapsedMs} ms`)
  if (res.detail?.length) {
    ElMessage.warning(res.detail.join('；'))
  }
  await loadAll()
}

async function publish() {
  await ElMessageBox.confirm('发布后课表将锁定并对外提供查询，确认发布？', '发布课表', { type: 'warning' })
  const res = await api.publishSchedule({ term: term.value })
  ElMessage.success(`已发布 ${res.count} 段排课，课表已锁定`)
  await loadAll()
}

function openAssign(row) {
  Object.assign(assignForm, {
    schedId: row.schedId,
    tcId: row.tcId,
    courseName: row.courseName,
    weekDay: row.weekDay,
    startSlot: row.startSlot,
    roomId: row.roomId,
    weekRange: row.weekRange
  })
  checkMsg.value = ''
  checkPass.value = false
  assignDialog.value = true
}

async function check() {
  const res = await api.checkSchedule({
    tcId: assignForm.tcId,
    schedId: assignForm.schedId,
    weekDay: assignForm.weekDay,
    startSlot: assignForm.startSlot,
    endSlot: assignForm.startSlot + 1,
    roomId: assignForm.roomId
  })
  checkPass.value = res.pass
  checkMsg.value = res.pass ? '未检测到冲突，可以保存' : res.conflicts.join('；')
}

async function saveAssign() {
  await api.assignSchedule({
    tcId: assignForm.tcId,
    schedId: assignForm.schedId,
    weekDay: assignForm.weekDay,
    startSlot: assignForm.startSlot,
    endSlot: assignForm.startSlot + 1,
    roomId: assignForm.roomId,
    weekRange: assignForm.weekRange
  })
  ElMessage.success('排课安排已更新')
  assignDialog.value = false
  await loadAll()
}

async function removeSched(row) {
  await ElMessageBox.confirm('确认删除该段排课安排？', '提示', { type: 'warning' })
  await api.removeSchedule(row.schedId)
  ElMessage.success('已删除')
  await loadAll()
}

function openResolve(row) {
  resolveRow.value = row
  Object.assign(resolveForm, { weekDay: '1', startSlot: 1, roomId: '' })
  resolveDialog.value = true
}

async function doResolve() {
  await api.resolveConflict(resolveRow.value.conflictId, {
    weekDay: resolveForm.weekDay,
    startSlot: resolveForm.startSlot,
    endSlot: resolveForm.startSlot + 1,
    roomId: resolveForm.roomId
  })
  ElMessage.success('冲突已处理')
  resolveDialog.value = false
  await loadAll()
}

function openRoom(row) {
  Object.keys(roomForm).forEach((k) => delete roomForm[k])
  Object.assign(roomForm, row ? { ...row, isNew: false } : { isNew: true, capacity: 60, roomType: '2', availableTime: '周一至周五 1-12 节' })
  roomDialog.value = true
}

async function saveRoom() {
  const payload = { ...roomForm }
  delete payload.isNew
  await api.saveRoom(payload)
  ElMessage.success('保存成功')
  roomDialog.value = false
  rooms.value = await api.rooms()
}

async function toggleRoom(row) {
  await api.roomStatus(row.roomId, row.roomStatus === '1' ? '0' : '1')
  ElMessage.success('状态已更新')
  rooms.value = await api.rooms()
}

function openRule(row) {
  Object.keys(ruleForm).forEach((k) => delete ruleForm[k])
  Object.assign(ruleForm, row ? { ...row, isNew: false } : { isNew: true, ruleType: '01', priorityNo: 10, enable: '1' })
  ruleDialog.value = true
}

async function saveRule() {
  const payload = { ...ruleForm }
  delete payload.isNew
  delete payload.updateTime
  await api.saveRule(payload)
  ElMessage.success('保存成功')
  ruleDialog.value = false
  ruleRows.value = await api.rules()
}

onMounted(async () => {
  try {
    terms.value = await api.terms()
  } catch (e) {
    terms.value = [term.value]
  }
  if (!terms.value.includes(term.value)) terms.value.unshift(term.value)
  await loadAll()
})
</script>
