<template>
  <div>
    <h2 class="page-title">工作台</h2>
    <p class="page-desc">{{ data.welcome }}，以下为专业设置与排课运行概览。</p>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="label">在办学院</div>
        <div class="value">{{ data.collegeCount || 0 }}<span class="unit">个</span></div>
      </div>
      <div class="stat-card">
        <div class="label">在招专业</div>
        <div class="value">{{ data.majorCount || 0 }}<span class="unit">个</span></div>
      </div>
      <div class="stat-card">
        <div class="label">在库课程</div>
        <div class="value">{{ data.courseCount || 0 }}<span class="unit">门</span></div>
      </div>
      <div class="stat-card">
        <div class="label">已发布培养方案</div>
        <div class="value">{{ data.programCount || 0 }}<span class="unit">份</span></div>
      </div>
      <div class="stat-card">
        <div class="label">教学班总数</div>
        <div class="value">{{ data.tcCount || 0 }}<span class="unit">个</span></div>
      </div>
      <div class="stat-card" :style="data.conflictCount ? 'border-left-color:#d9534f' : ''">
        <div class="label">待处理排课冲突</div>
        <div class="value" :style="data.conflictCount ? 'color:#d9534f' : ''">
          {{ data.conflictCount || 0 }}<span class="unit">条</span>
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="isAdmin ? 14 : 24">
        <div class="panel">
          <div class="panel-title">待办事项</div>
          <el-empty v-if="!data.todo || data.todo.length === 0" description="暂无待办，业务运行正常" :image-size="70" />
          <div v-for="item in data.todo" :key="item.title" class="todo" @click="$router.push(item.path)">
            <div>
              <el-icon color="#2b5c9b"><Bell /></el-icon>
              <span class="todo-title">{{ item.title }}</span>
            </div>
            <el-tag type="warning" effect="light">{{ item.count }} 项</el-tag>
          </div>
        </div>

        <div v-if="isStudent" class="panel">
          <div class="panel-title">今日日程 · {{ todayLabel }}</div>
          <el-empty
            v-if="todayCourses.length === 0"
            description="今天没有课程安排"
            :image-size="70"
          />
          <div v-for="(c, i) in todayCourses" :key="i" class="schedule-item">
            <div class="schedule-time">{{ c.startSlot }}-{{ c.endSlot }} 节</div>
            <div class="schedule-main">
              <div class="schedule-course">{{ c.courseName }}</div>
              <div class="muted">
                {{ c.teacherName }} · {{ c.roomName }} · {{ c.weekRange }}
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <el-col v-if="isAdmin" :span="10">
        <div class="panel">
          <div class="panel-title">
            最近操作
            <el-button link type="primary" size="small" style="float: right" @click="$router.push('/logs')">
              查看全部日志
            </el-button>
          </div>
          <el-empty v-if="!data.recentLogs || data.recentLogs.length === 0" description="暂无日志" :image-size="60" />
          <div v-for="log in data.recentLogs" :key="log.logId" class="log-item">
            <div class="log-head">
              <span class="log-user">{{ log.userId }}</span>
              <span class="muted">{{ log.operTime }}</span>
            </div>
            <div class="log-body">
              <el-tag size="small" effect="plain">{{ operTypeName(log.operType) }}</el-tag>
              <span class="log-obj">{{ log.operObject }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'
import { userStore } from '../store/user'

const user = userStore()
const data = ref({})
const todayCourses = ref([])
const term = ref('')

/** 仅系统管理员可在工作台查看最近操作，其余角色只保留待办（可去日志页按权限回溯） */
const isAdmin = computed(() => user.isAdmin())
const isStudent = computed(() => user.isStudent())

const dayNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
const todayWeekday = computed(() => {
  const d = new Date().getDay()
  return d === 0 ? 7 : d
})
const todayLabel = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 ${dayNames[todayWeekday.value]}`
})

const operTypeName = (code) =>
  ({ '01': '新增', '02': '修改', '03': '删除', '04': '审批', '05': '发布', '06': '导出', '07': '登录', '08': '登出' }[code] || code)

async function loadTodayCourses() {
  try {
    const terms = await api.terms()
    term.value = terms?.[0] || ''
    if (!term.value) return
    const res = await api.timetable({ term: term.value, view: 'self' })
    todayCourses.value = (res.rows || [])
      .filter((r) => Number(r.weekDay) === todayWeekday.value)
      .sort((a, b) => Number(a.startSlot) - Number(b.startSlot))
  } catch (e) {
    todayCourses.value = []
  }
}

onMounted(async () => {
  data.value = await api.overview()
  if (isStudent.value) {
    await loadTodayCourses()
  }
})
</script>

<style scoped>
.todo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid var(--mis-border);
  border-radius: 6px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.todo:hover {
  border-color: var(--mis-primary);
  background: #f8fbff;
}

.todo-title {
  margin-left: 8px;
}

.schedule-item {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--mis-border);
  border-radius: 6px;
  margin-bottom: 10px;
}

.schedule-time {
  min-width: 74px;
  font-weight: 600;
  color: var(--mis-primary);
}

.schedule-course {
  font-weight: 600;
  margin-bottom: 2px;
}

.log-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--mis-border);
}

.log-item:last-child {
  border-bottom: none;
}

.log-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 4px;
}

.log-user {
  font-weight: 600;
  color: var(--mis-text);
}

.log-body {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.log-obj {
  color: var(--mis-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
