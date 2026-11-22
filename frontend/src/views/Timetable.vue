<template>
  <div>
    <h2 class="page-title">课表查询</h2>
    <p class="page-desc">
      课表按学期、教学班、教师、班级、教室多维发布，数据统一来源于完整课表视图，保证课表口径唯一。
      导出行为将写入操作日志。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="term" style="width: 150px" @change="load">
          <el-option v-for="t in terms" :key="t" :label="t" :value="t" />
        </el-select>
        <el-radio-group v-model="view" @change="onViewChange">
          <el-radio-button value="self">我的课表</el-radio-button>
          <el-radio-button v-if="!isStudent" value="class">班级</el-radio-button>
          <el-radio-button v-if="!isStudent" value="teacher">教师</el-radio-button>
          <el-radio-button v-if="!isStudent" value="room">教室</el-radio-button>
        </el-radio-group>
        <el-select v-if="view === 'class'" v-model="key" filterable placeholder="选择班级" style="width: 180px" @change="load">
          <el-option v-for="c in classes" :key="c.classId" :label="c.className" :value="c.classId" />
        </el-select>
        <el-select v-if="view === 'teacher'" v-model="key" filterable placeholder="选择教师" style="width: 180px" @change="load">
          <el-option v-for="t in teachers" :key="t.teacherId" :label="`${t.teacherName}（${t.teacherId}）`" :value="t.teacherId" />
        </el-select>
        <el-select v-if="view === 'room'" v-model="key" filterable placeholder="选择教室" style="width: 180px" @change="load">
          <el-option v-for="r in rooms" :key="r.roomId" :label="r.roomName" :value="r.roomId" />
        </el-select>
        <span class="spacer" />
        <el-button @click="exportCsv">导出课表</el-button>
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
    </div>

    <el-dialog v-model="detailDialog" title="课程安排详情" width="460px">
      <el-descriptions v-if="current" :column="1" border size="small">
        <el-descriptions-item label="课程">{{ current.courseName }}（{{ current.courseId }}）</el-descriptions-item>
        <el-descriptions-item label="教学班">{{ current.tcId }}</el-descriptions-item>
        <el-descriptions-item label="教师">{{ current.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ current.classIds }}</el-descriptions-item>
        <el-descriptions-item label="时间">
          {{ dayName(Number(current.weekDay)) }} {{ current.startSlot }}-{{ current.endSlot }} 节（{{ current.weekRange }} 周）
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
const term = ref('2026-2027-1')
const terms = ref(['2026-2027-1'])
const view = ref('self')
const key = ref('')
const rows = ref([])
const classes = ref([])
const teachers = ref([])
const rooms = ref([])
const detailDialog = ref(false)
const current = ref(null)
const periods = [1, 3, 5, 7, 9, 11]

const isStudent = computed(() => !user.user?.teacherId && user.user?.dataScope === '3')

const dayName = (d) => ({ 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五' }[d] || '')

function cellItems(day, period) {
  return rows.value.filter((r) => Number(r.weekDay) === day && Number(r.startSlot) === period)
}

async function onViewChange() {
  if (view.value === 'class' && !key.value) key.value = classes.value[0]?.classId
  if (view.value === 'teacher' && !key.value) key.value = teachers.value[0]?.teacherId
  if (view.value === 'room' && !key.value) key.value = rooms.value[0]?.roomId
  await load()
}

async function load() {
  const params = { term: term.value, view: view.value }
  if (view.value !== 'self') params.key = key.value
  const data = await api.timetable(params)
  rows.value = data.rows || []
}

function openDetail(item) {
  current.value = item
  detailDialog.value = true
}

async function exportCsv() {
  await api.timetable({ term: term.value, view: view.value, key: view.value === 'self' ? undefined : key.value, export: 1 })
  const header = ['课程', '教师', '班级', '星期', '节次', '周次', '教室']
  const lines = rows.value.map((r) =>
    [r.courseName, r.teacherName, r.classIds, dayName(Number(r.weekDay)), `${r.startSlot}-${r.endSlot}`, r.weekRange, r.roomName]
      .map((x) => `"${String(x ?? '').replace(/"/g, '""')}"`)
      .join(',')
  )
  const blob = new Blob(['\ufeff' + [header.join(','), ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `课表_${term.value}.csv`
  link.click()
  ElMessage.success('课表已导出，导出行为已记录')
}

onMounted(async () => {
  try {
    terms.value = await api.terms()
  } catch (e) {
    terms.value = [term.value]
  }
  if (!isStudent.value) {
    classes.value = await api.classes()
    teachers.value = await api.teachers()
    rooms.value = await api.rooms()
  }
  await load()
})
</script>
