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
      <el-col :span="14">
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

        <div class="panel">
          <div class="panel-title">系统流程</div>
          <el-steps :active="4" align-center>
            <el-step title="专业设置" description="培养方案制定与修订" />
            <el-step title="课程设置" description="课程库与开课审核" />
            <el-step title="排课" description="自动排课与冲突处理" />
            <el-step title="课表服务" description="多维查询与调课" />
            <el-step title="评价闭环" description="评教反馈方案修订" />
          </el-steps>
        </div>
      </el-col>

      <el-col :span="10">
        <div class="panel">
          <div class="panel-title">最近操作</div>
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
import { onMounted, ref } from 'vue'
import api from '../api'

const data = ref({})

const operTypeName = (code) =>
  ({ '01': '新增', '02': '修改', '03': '删除', '04': '审批', '05': '发布', '06': '导出' }[code] || code)

onMounted(async () => {
  data.value = await api.overview()
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
