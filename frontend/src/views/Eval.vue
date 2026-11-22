<template>
  <div>
    <h2 class="page-title">课程评价</h2>
    <p class="page-desc">
      教务处按学期配置评价指标与权重并发布评教方案，系统按“课程—教学班—教师—学生”生成评教任务；
      学生匿名提交后剔除无效样本，形成分课程、分教师的评价结果。
    </p>

    <div class="panel">
      <el-tabs v-model="tab">
        <el-tab-pane v-if="canManage" label="评教方案" name="plan">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openPlan">新增方案</el-button>
            <span class="spacer" />
            <span class="muted">指标权重之和须等于 1</span>
          </div>
          <el-table :data="plans" border size="small">
            <el-table-column prop="planId" label="方案编号" width="110" />
            <el-table-column prop="term" label="适用学期" width="130" />
            <el-table-column prop="planName" label="方案名称" min-width="220" show-overflow-tooltip />
            <el-table-column prop="openDate" label="开始" width="110" />
            <el-table-column prop="closeDate" label="结束" width="110" />
            <el-table-column prop="minSample" label="样本下限" width="95" />
            <el-table-column label="匿名" width="80">
              <template #default="{ row }">{{ row.anonymity === '1' ? '匿名' : '实名' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag size="small" :type="['info', 'success', 'warning'][Number(row.planStatus)]">
                  {{ { 0: '草案', 1: '已发布', 2: '已结束' }[row.planStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.planStatus === '0'" link type="primary" size="small" @click="publishPlan(row)">
                  发布方案
                </el-button>
                <el-button
                  v-if="row.planStatus === '1'"
                  link
                  type="warning"
                  size="small"
                  :disabled="!isPastClose(row)"
                  :title="isPastClose(row) ? '' : '未到结束日期，暂不能结束并汇总'"
                  @click="finishPlan(row)"
                >
                  结束并汇总
                </el-button>
                <el-button link type="primary" size="small" @click="viewStats(row)">统计</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的评教" name="task">
          <div class="toolbar">
            <el-select v-model="taskPlanId" placeholder="选择评教方案" style="width: 260px" @change="loadTasks">
              <el-option
                v-for="p in plans.filter((x) => x.planStatus !== '0')"
                :key="p.planId"
                :label="`${p.planName}（${p.term}）`"
                :value="p.planId"
              />
            </el-select>
            <span class="spacer" />
            <span class="muted">评教窗口内匿名提交，提交后不可修改</span>
          </div>
          <el-table :data="tasks" border size="small">
            <el-table-column prop="tcId" label="教学班" width="120" />
            <el-table-column prop="courseName" label="课程" min-width="170" />
            <el-table-column prop="teacherName" label="任课教师" width="110" />
            <el-table-column prop="classIds" label="面向班级" min-width="140" show-overflow-tooltip />
            <el-table-column prop="preferSlot" label="偏好节次" width="110" />
            <el-table-column label="评教状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.submitted ? 'success' : 'warning'">
                  {{ row.submitted ? '已提交' : '待评教' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button :disabled="row.submitted" link type="primary" size="small" @click="openEval(row)">
                  去评教
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="结果统计" name="stats">
          <div class="toolbar">
            <el-select v-model="statsPlanId" placeholder="选择评教方案" style="width: 260px" @change="loadStats">
              <el-option
                v-for="p in plans"
                :key="p.planId"
                :label="`${p.planName}（${p.term}）`"
                :value="p.planId"
              />
            </el-select>
            <span class="spacer" />
          </div>
          <div v-if="stats" class="stat-grid">
            <div class="stat-card">
              <div class="label">有效样本</div>
              <div class="value">{{ stats.validRecord }}<span class="unit">份</span></div>
            </div>
            <div class="stat-card">
              <div class="label">总体均分</div>
              <div class="value">{{ stats.avgScore }}<span class="unit">分</span></div>
            </div>
            <div class="stat-card">
              <div class="label">参评教学班</div>
              <div class="value">{{ stats.tcStats?.length || 0 }}<span class="unit">个</span></div>
            </div>
          </div>
          <el-row v-if="stats" :gutter="16">
            <el-col :span="14">
              <el-table :data="stats.tcStats" border size="small" max-height="420">
                <el-table-column prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
                <el-table-column prop="teacherName" label="教师" width="100" />
                <el-table-column prop="collegeName" label="开课学院" width="140" show-overflow-tooltip />
                <el-table-column prop="sample" label="样本" width="70" />
                <el-table-column prop="avgScore" label="均分" width="80" />
                <el-table-column label="结果" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.insufficient" size="small" type="info">样本不足</el-tag>
                    <el-tag v-else size="small" type="success">有效</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
            <el-col :span="10">
              <div class="panel">
                <div class="panel-title">分数分布</div>
                <div v-for="(v, k) in stats.distribution" :key="k" class="dist-row">
                  <span class="dist-label">{{ k }}</span>
                  <el-progress :percentage="percent(v)" :stroke-width="12" />
                  <span class="dist-value">{{ v }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="planDialog" title="新增评教方案" width="640px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="方案编号" required>
              <el-input v-model="planForm.planId" placeholder="EP202602" maxlength="8" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用学期" required>
              <el-input v-model="planForm.term" placeholder="2026-2027-1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="方案名称" required>
          <el-input v-model="planForm.planName" maxlength="50" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="planForm.openDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="planForm.closeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="样本下限">
              <el-input-number v-model="planForm.minSample" :min="1" :max="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否匿名">
              <el-switch v-model="planAnonymity" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="panel-title">评价指标与权重</div>
      <el-table :data="planForm.indicators" size="small" border>
        <el-table-column label="指标名称">
          <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
        </el-table-column>
        <el-table-column label="权重" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.w" :min="0" :max="1" :step="0.1" :precision="1" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="planForm.indicators.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin-top: 10px" @click="planForm.indicators.push({ name: '', w: 0.2 })">
        新增指标
      </el-button>
      <div class="muted" style="margin-top: 8px">当前权重合计：{{ weightSum }}</div>

      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evalDialog" :title="`课程评教 · ${evalRow?.courseName || ''}`" width="560px">
      <el-alert type="info" :closable="false" show-icon title="匿名提交，评分与意见将汇总用于教学改进，提交后不可修改。" />
      <div v-for="(weight, name) in indicators" :key="name" class="indicator">
        <div class="indicator-head">
          <span>{{ name }}</span>
          <span class="muted">权重 {{ weight }}</span>
        </div>
        <el-rate v-model="evalForm.scores[name]" :max="5" show-score score-template="{value} 星" :texts="rateTexts" />
      </div>
      <el-input v-model="evalForm.comment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="文字意见（选填）" />
      <template #footer>
        <el-button @click="evalDialog = false">取消</el-button>
        <el-button type="primary" @click="submitEval">提交评教（综合 {{ computedScore }} 分）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { userStore } from '../store/user'

const user = userStore()
/** 评教方案管理仅授予 P18（教务处），学生/教师默认进入各自页签（P0-02、P2-11） */
const canManage = computed(() => user.hasPerm('P18'))
const isTeacher = computed(() => !!user.user?.teacherId)
const tab = ref(canManage.value ? 'plan' : isTeacher.value ? 'stats' : 'task')
const plans = ref([])
const tasks = ref([])
const stats = ref(null)
const taskPlanId = ref('')
const statsPlanId = ref('')
const planDialog = ref(false)
const evalDialog = ref(false)
const planForm = reactive({ indicators: [] })
const planAnonymity = ref('1')
const evalForm = reactive({ scores: {}, comment: '' })
const evalRow = ref(null)
const indicators = ref({})
const rateTexts = ['很不满意', '不满意', '一般', '满意', '非常满意']

const weightSum = computed(() =>
  (planForm.indicators || []).reduce((s, i) => s + Number(i.w || 0), 0).toFixed(1)
)

const computedScore = computed(() => {
  let total = 0
  for (const [name, w] of Object.entries(indicators.value)) {
    total += (evalForm.scores[name] || 0) * 20 * w
  }
  return total.toFixed(1)
})

const percent = (v) => {
  const total = Object.values(stats.value?.distribution || {}).reduce((s, x) => s + Number(x), 0) || 1
  return Math.round((Number(v) / total) * 100)
}

async function loadPlans() {
  plans.value = await api.evalPlans()
  if (!taskPlanId.value) taskPlanId.value = plans.value.find((p) => p.planStatus === '1')?.planId || ''
  if (!statsPlanId.value) statsPlanId.value = plans.value[0]?.planId || ''
}

async function loadTasks() {
  if (!taskPlanId.value) return
  tasks.value = await api.evalTasks(taskPlanId.value)
}

async function loadStats() {
  if (!statsPlanId.value) return
  stats.value = await api.evalStats(statsPlanId.value)
}

function openPlan() {
  Object.keys(planForm).forEach((k) => delete planForm[k])
  Object.assign(planForm, {
    term: '2026-2027-1',
    minSample: 5,
    indicators: [
      { name: '教学态度', w: 0.3 },
      { name: '内容组织', w: 0.3 },
      { name: '教学方法', w: 0.2 },
      { name: '考核合理', w: 0.2 }
    ]
  })
  planAnonymity.value = '1'
  planDialog.value = true
}

async function savePlan() {
  await api.saveEvalPlan({
    ...planForm,
    anonymity: planAnonymity.value,
    indicatorJson: JSON.stringify(planForm.indicators)
  })
  ElMessage.success('方案已保存')
  planDialog.value = false
  await loadPlans()
}

/** 未到结束日期不允许提前结束汇总（P2-08） */
function isPastClose(row) {
  if (!row.closeDate) return true
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return String(row.closeDate) <= todayStr
}

async function publishPlan(row) {
  await ElMessageBox.confirm('发布后将按学期教学班生成评教任务并通知学生，确认发布？', '提示', { type: 'warning' })
  await api.publishEvalPlan(row.planId)
  ElMessage.success('评教已开始')
  await loadPlans()
}

async function finishPlan(row) {
  await ElMessageBox.confirm('结束评教并剔除无效样本、汇总统计结果？', '提示', { type: 'warning' })
  await api.finishEvalPlan(row.planId)
  ElMessage.success('汇总完成')
  await loadPlans()
}

async function viewStats(row) {
  statsPlanId.value = row.planId
  tab.value = 'stats'
  await loadStats()
}

async function openEval(row) {
  evalRow.value = row
  const plan = plans.value.find((p) => p.planId === taskPlanId.value)
  indicators.value = {}
  const list = JSON.parse(plan.indicatorJson)
  list.forEach((i) => (indicators.value[i.name] = i.w))
  evalForm.scores = {}
  Object.keys(indicators.value).forEach((k) => (evalForm.scores[k] = 4))
  evalForm.comment = ''
  evalDialog.value = true
}

async function submitEval() {
  await api.submitEval({
    planId: taskPlanId.value,
    tcId: evalRow.value.tcId,
    comment: evalForm.comment,
    items: Object.fromEntries(Object.entries(evalForm.scores).map(([k, v]) => [k, v * 20]))
  })
  ElMessage.success('评教提交成功，感谢参与')
  evalDialog.value = false
  await loadTasks()
}

onMounted(async () => {
  await loadPlans()
  await loadTasks()
})
</script>

<style scoped>
.indicator {
  margin-bottom: 12px;
}

.indicator-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 4px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.dist-label {
  width: 66px;
  font-size: 12px;
  color: var(--mis-muted);
}

.dist-value {
  width: 30px;
  text-align: right;
  font-size: 12px;
}
</style>
