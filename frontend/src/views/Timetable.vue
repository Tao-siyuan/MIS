<template>
  <div>
    <h2 class="page-title">课表查询</h2>
    <p class="page-desc">
      课表按学期、教学班、教师、班级、教室多维发布，数据统一来源于完整课表视图，保证课表口径唯一。
      导出行为将写入操作日志。
    </p>

    <div class="panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="周课表" name="week">
          <div class="toolbar">
            <el-select v-model="term" style="width: 150px" @change="load">
              <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
            </el-select>
            <el-select v-model="week" style="width: 110px">
              <el-option v-for="w in weeks" :key="w" :label="`第 ${w} 周`" :value="w" />
            </el-select>
            <el-radio-group v-model="view" @change="onViewChange">
              <el-radio-button value="self">我的课表</el-radio-button>
              <el-radio-button v-if="canQueryOthers" value="class">班级</el-radio-button>
              <el-radio-button v-if="canQueryOthers" value="teacher">教师</el-radio-button>
              <el-radio-button v-if="canQueryOthers" value="room">教室</el-radio-button>
            </el-radio-group>
            <el-select v-if="view === 'class'" v-model="key" filterable placeholder="选择班级" style="width: 180px" @change="load">
              <el-option v-for="c in classes" :key="c.classId" :label="c.className" :value="c.classId" />
            </el-select>
            <el-select v-if="view === 'teacher' && canQueryOthers" v-model="key" filterable placeholder="选择教师" style="width: 180px" @change="load">
              <el-option v-for="t in teachers" :key="t.teacherId" :label="`${t.teacherName}（${t.userId || t.teacherId}）`" :value="t.teacherId" />
            </el-select>
            <el-select v-if="view === 'room'" v-model="key" filterable placeholder="选择教室" style="width: 180px" @change="load">
              <el-option v-for="r in rooms" :key="r.roomId" :label="r.roomName" :value="r.roomId" />
            </el-select>
            <span class="spacer" />
            <span class="muted">当前显示：第 {{ week }} 周</span>
            <el-button :loading="exporting" @click="exportXlsx">导出课表（xlsx）</el-button>
          </div>

          <table class="timetable">
            <thead>
              <tr>
                <th style="width: 80px">节次</th>
                <th v-for="d in 5" :key="d">{{ dayName(d) }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in periods" :key="p">
                <th>{{ p }}-{{ p + 1 }} 节</th>
                <td v-for="d in 5" :key="d" class="cell">
                  <div
                    v-for="item in cellItems(d, p)"
                    :key="item.schedId"
                    class="tt-item"
                    :class="{ conflict: cellItems(d, p).length > 1 }"
                    @click="openDetail(item)"
                  >
                    <b>{{ item.courseName }}</b>
                    <span>{{ item.teacherName || '待定' }} · {{ item.roomName }}</span>
                    <span>{{ item.classIds }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </el-tab-pane>

        <el-tab-pane v-if="isStudent" label="已修课程" name="history">
          <div class="toolbar">
            <span class="muted">以下为往期学期已完成（课表已发布）的课程，共 {{ history.length }} 门</span>
          </div>
          <el-table :data="history" border size="small" max-height="520">
            <el-table-column prop="term" label="学期" width="130" />
            <el-table-column prop="courseId" label="课程号" width="100" />
            <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="credit" label="学分" width="80" />
            <el-table-column prop="hours" label="学时" width="80" />
            <el-table-column label="考核方式" width="100">
              <template #default="{ row }">{{ { 1: '考试', 2: '考查' }[row.assessType] || '-' }}</template>
            </el-table-column>
            <el-table-column prop="teacherName" label="任课教师" width="120" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="detailDialog" title="课程安排详情" width="460px">
      <el-descriptions v-if="current" :column="1" border size="small">
        <el-descriptions-item label="课程">{{ current.courseName }}（{{ current.courseId }}）</el-descriptions-item>
        <el-descriptions-item label="教学班">{{ current.tcId }}</el-descriptions-item>
        <el-descriptions-item label="教师">{{ current.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ current.classIds }}</el-descriptions-item>
        <el-descriptions-item label="时间">
          第 {{ week }} 周（{{ current.weekRange }} 周）：{{ dayName(Number(current.weekDay)) }} {{ current.startSlot }}-{{ current.endSlot }} 节
        </el-descriptions-item>
        <el-descriptions-item label="教室">{{ current.roomName }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ current.credit }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { userStore } from '../store/user'

const user = userStore()
const tab = ref('week')
const term = ref('2026-2027-1')
const terms = ref(['2026-2027-1'])
const view = ref('self')
const key = ref('')
const rows = ref([])
const history = ref([])
const historyLoaded = ref(false)
const classes = ref([])
const teachers = ref([])
const rooms = ref([])
const detailDialog = ref(false)
const current = ref(null)
const exporting = ref(false)
const periods = [1, 3, 5, 7, 9, 11]
const week = ref(1)
const weeks = Array.from({ length: 16 }, (_, i) => i + 1)

const isStudent = computed(() => user.isStudent())
const isTeacher = computed(() => user.isTeacher())
/** 只有教务类角色（校级/院级）可以按班级、教师、教室查询他人课表 */
const canQueryOthers = computed(() => !isStudent.value && !isTeacher.value)

const dayName = (d) => ({ 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五' }[d] || '')

/** 解析“1-16”“9-16”形式的周次范围，缺省按 1-16 处理 */
function parseWeekRange(range) {
  if (!range) return [1, 16]
  const parts = String(range)
    .split('-')
    .map((s) => parseInt(s.trim(), 10))
  if (parts.some(Number.isNaN)) return [1, 16]
  const a = parts[0]
  const b = parts.length > 1 ? parts[1] : a
  return [Math.min(a, b), Math.max(a, b)]
}

function inWeek(range, w) {
  const [start, end] = parseWeekRange(range)
  return w >= start && w <= end
}

const weekRows = computed(() => rows.value.filter((r) => inWeek(r.weekRange, week.value)))

function cellItems(day, period) {
  return weekRows.value.filter((r) => Number(r.weekDay) === day && Number(r.startSlot) === period)
}

async function onViewChange() {
  if (view.value === 'class' && !key.value) key.value = classes.value[0]?.classId
  if (view.value === 'teacher' && !key.value) key.value = teachers.value[0]?.teacherId
  if (view.value === 'room' && !key.value) key.value = rooms.value[0]?.roomId
  await load()
}

async function load() {
  if (view.value !== 'self' && !key.value) {
    rows.value = []
    return
  }
  const params = { term: term.value, view: view.value }
  if (view.value !== 'self') params.key = key.value
  try {
    const data = await api.timetable(params)
    rows.value = data.rows || []
  } catch (e) {
    // 学生可能没有班级或教师没有分配教学班，此处友好提示而非空白报错
    rows.value = []
    ElMessage.warning(e?.response?.data?.msg || '暂无可查询的课表数据')
  }
}

async function loadHistory() {
  try {
    history.value = await api.timetableHistory()
  } catch (e) {
    history.value = []
  }
  historyLoaded.value = true
}

function openDetail(item) {
  current.value = item
  detailDialog.value = true
}

/**
 * 导出 xlsx：第一个工作表与页面周课表一致（节次 × 星期网格），第二个工作表为课程明细。
 */
async function exportXlsx() {
  exporting.value = true
  try {
    await api.timetable({
      term: term.value,
      view: view.value,
      key: view.value === 'self' ? undefined : key.value,
      export: 1
    })
    const XLSX = await import('xlsx')
    const days = [1, 2, 3, 4, 5]
    const grid = [['节次', ...days.map((d) => dayName(d))]]
    for (const p of periods) {
      const line = [`${p}-${p + 1} 节`]
      for (const d of days) {
        const items = cellItems(d, p)
        line.push(
          items
            .map((i) => `${i.courseName}\n${i.teacherName || '待定'} · ${i.roomName}\n${i.classIds}`)
            .join('\n')
        )
      }
      grid.push(line)
    }
    const detail = [['课程', '教师', '班级', '星期', '节次', '周次', '教室', '学分']]
    weekRows.value.forEach((r) =>
      detail.push([
        r.courseName,
        r.teacherName,
        r.classIds,
        dayName(Number(r.weekDay)),
        `${r.startSlot}-${r.endSlot}`,
        r.weekRange,
        r.roomName,
        r.credit
      ])
    )
    const wb = XLSX.utils.book_new()
    const ws = XLSX.utils.aoa_to_sheet(grid)
    ws['!cols'] = [{ wch: 10 }, ...days.map(() => ({ wch: 24 }))]
    ws['!rows'] = [{ hpt: 20 }, ...periods.map(() => ({ hpt: 60 }))]
    XLSX.utils.book_append_sheet(wb, ws, `第${week.value}周课表`)
    const ws2 = XLSX.utils.aoa_to_sheet(detail)
    ws2['!cols'] = [22, 12, 14, 8, 10, 10, 10, 8].map((wch) => ({ wch }))
    XLSX.utils.book_append_sheet(wb, ws2, '课程明细')
    const suffix = view.value === 'self' ? '' : `_${key.value || ''}`
    XLSX.writeFile(wb, `课表_${term.value}_第${week.value}周${suffix}.xlsx`)
    ElMessage.success('课表已导出为 Excel，导出行为已记录')
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  try {
    terms.value = await api.terms()
  } catch (e) {
    terms.value = [term.value]
  }
  if (canQueryOthers.value) {
    classes.value = await api.classes()
    teachers.value = await api.teachers()
    rooms.value = await api.rooms()
  }
  // 默认视图按角色决定：教师只看本人课表，教务看班级课表
  if (isTeacher.value) {
    view.value = 'self'
    key.value = user.user.teacherId
  } else if (canQueryOthers.value) {
    view.value = 'class'
    key.value = classes.value[0]?.classId || ''
  }
  await load()
  if (isStudent.value) {
    await loadHistory()
  }
})
</script>
