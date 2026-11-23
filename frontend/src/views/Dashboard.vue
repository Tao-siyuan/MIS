<template>
  <div>
    <h2 class="page-title">工作台</h2>
    <p class="page-desc">{{ data.welcome }}，以下为专业设置与排课运行概览。</p>

    <!-- 教师端 / 学生端：以个人信息卡替代统计指标（教师端修改意见第 2 条） -->
    <div v-if="showProfile" class="panel profile-panel">
      <el-avatar :size="72" class="profile-avatar">{{ initial }}</el-avatar>
      <div class="profile-body">
        <div class="profile-name">
          {{ profile.realName }}
          <el-tag size="small" effect="plain">{{ profile.roleName }}</el-tag>
          <span class="muted profile-photo">照片待上传</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="isStudent ? '学号' : '工号'">{{ profile.userId }}</el-descriptions-item>
          <el-descriptions-item label="身份">{{ profile.identity }}</el-descriptions-item>
          <el-descriptions-item label="学院">{{ profile.collegeName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="isStudent ? '专业' : '职称'">
            {{ isStudent ? profile.majorName || '-' : profile.title || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="isStudent" label="班级">{{ profile.className || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <div v-else class="stat-grid">
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

        <!-- 教师端与学生端：今日日程（教师端修改意见第 1 条） -->
        <div v-if="showSchedule" class="panel">
          <div class="panel-title">今日日程 · {{ todayLabel }}</div>
          <el-empty v-if="todayCourses.length === 0" description="今天没有课程安排" :image-size="70" />
          <div v-for="(c, i) in todayCourses" :key="i" class="schedule-item">
            <div class="schedule-time">{{ c.startSlot }}-{{ c.endSlot }} 节</div>
            <div class="schedule-main">
              <div class="schedule-course">{{ c.courseName }}</div>
              <div class="muted">
                {{ c.teacherName }} · {{ c.roomName }} · {{ c.classIds }} · {{ c.weekRange }}
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

const isAdmin = computed(() => user.isAdmin())
const isStudent = computed(() => user.isStudent())
const isTeacher = computed(() => user.isTeacher())
/** 教师与学生工作台展示个人信息卡，教务/管理员仍展示统计指标 */
const showProfile = computed(() => isStudent.value || isTeacher.value)
const showSchedule = computed(() => isStudent.value || isTeacher.value)
const profile = computed(() => data.value.profile || {})
const initial = computed(() => (profile.value.realName || 'U').slice(0, 1))

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
  if (showSchedule.value) {
    await loadTodayCourses()
  }
})
</script>

<style scoped>
.profile-panel {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.profile-avatar {
  background: linear-gradient(135deg, #4b8bd6, #2b5c9b);
  color: #fff;
  font-size: 26px;
  flex: none;
}

.profile-body {
  flex: 1;
}

.profile-name {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-photo {
  font-size: 12px;
  font-weight: 400;
}

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
