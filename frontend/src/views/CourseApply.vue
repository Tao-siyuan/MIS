<template>
  <div>
    <h2 class="page-title">新增课程申报</h2>
    <p class="page-desc">
      教务处可申报校级必修与通识课程（通识课程可下发学院补充），学院教务可申报院级必修、专业必修、
      专业选修、实践教学课程，教师仅可申报素质拓展课程；申报经学院初审、教务处审批后按编码规则生成课程号入库。
    </p>

    <div class="panel">
      <div class="toolbar">
        <el-radio-group v-model="scope" @change="load">
          <el-radio-button value="mine">我的申报</el-radio-button>
          <el-radio-button v-if="isCollegeOfficer" value="college">本学院申报</el-radio-button>
          <el-radio-button v-if="isSchoolOfficer" value="all">全部申报</el-radio-button>
        </el-radio-group>
        <el-select v-model="status" placeholder="审核状态" clearable style="width: 140px" @change="load">
          <el-option label="学院待审" value="0" />
          <el-option label="待学院补充" value="4" />
          <el-option label="教务处待审" value="3" />
          <el-option label="已批准" value="1" />
          <el-option label="已驳回" value="2" />
        </el-select>
        <span class="spacer" />
        <el-button v-if="canApply" type="primary" @click="openApply">课程申报</el-button>
      </div>

      <el-table :data="pageRows" border size="small">
        <el-table-column prop="applyId" label="申请编号" width="120" />
        <el-table-column prop="courseName" label="拟开课程" min-width="170" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="申报人" width="100" />
        <el-table-column label="性质" width="110">
          <template #default="{ row }">{{ typeName(row.courseType) }}</template>
        </el-table-column>
        <el-table-column label="下发学院" width="110">
          <template #default="{ row }">{{ collegeName(row.dispatchCollege) }}</template>
        </el-table-column>
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column prop="hours" label="学时" width="70" />
        <el-table-column prop="applyReason" label="开设理由" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applyTime" label="申报时间" width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="{ 0: 'warning', 4: 'info', 3: 'primary', 1: 'success', 2: 'danger' }[row.auditStatus]">
              {{ { 0: '学院待审', 4: '待学院补充', 3: '教务处待审', 1: '已批准', 2: '已驳回' }[row.auditStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditOpinion" label="审核意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="课程号" width="110">
          <template #default="{ row }">{{ row.courseId || '-' }}</template>
        </el-table-column>
        <!-- 操作列仅对具备审核/补充权限的学院、教务处角色展示，申报教师侧不展示操作列 -->
        <el-table-column v-if="canAudit || isCollegeOfficer" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === '0' && isCollegeOfficer">
              <el-button link type="success" size="small" @click="collegeAudit(row, '1')">初审通过</el-button>
              <el-button link type="danger" size="small" @click="collegeAudit(row, '0')">初审驳回</el-button>
            </template>
            <el-button
              v-if="canSupplement(row)"
              link
              type="warning"
              size="small"
              @click="openSupplement(row)"
            >补充并提交</el-button>
            <el-button
              v-if="row.auditStatus === '3' && isSchoolOfficer"
              link
              type="primary"
              size="small"
              @click="openAudit(row)"
            >终审</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 30, 50]"
        :total="rows.length"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 14px; justify-content: flex-end"
      />
    </div>

    <el-dialog v-model="applyDialog" title="新增课程申报" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="课程名称" required>
          <el-input v-model="form.courseName" maxlength="40" />
        </el-form-item>
        <el-form-item label="课程性质" required>
          <el-select v-model="form.courseType" style="width: 100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isSchoolOfficer && form.courseType === '15'" label="下发学院" required>
          <el-select v-model="form.dispatchCollege" filterable style="width: 100%" placeholder="通识课程下发学院补充">
            <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="拟定学分">
          <el-input-number v-model="form.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="拟定学时">
          <el-input-number v-model="form.hours" :min="8" :max="200" :step="8" />
        </el-form-item>
        <el-form-item label="开设理由">
          <el-input v-model="form.applyReason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交申报</el-button>
      </template>
    </el-dialog>

    <!-- 学院补充：教务处下发的通识课程由学院教务补充培养要求后交教务处审核 -->
    <el-dialog v-model="supplementDialog" title="补充通识课程培养要求" width="520px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="申请编号">{{ current?.applyId }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ current?.courseName }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item label="学分">
          <el-input-number v-model="supplementForm.credit" :min="0.5" :max="20" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="学时">
          <el-input-number v-model="supplementForm.hours" :min="8" :max="200" :step="8" />
        </el-form-item>
        <el-form-item label="开设理由">
          <el-input v-model="supplementForm.applyReason" type="textarea" :rows="3" maxlength="200" />
        </el-form-item>
        <el-form-item label="学院意见">
          <el-input v-model="supplementForm.opinion" type="textarea" :rows="2" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supplementDialog = false">取消</el-button>
        <el-button type="primary" @click="doSupplement">补充并提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="auditDialog" title="新增课程审批" width="520px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="申请编号">{{ current?.applyId }}</el-descriptions-item>
        <el-descriptions-item label="申报教师">{{ current?.teacherName }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ current?.courseName }}</el-descriptions-item>
        <el-descriptions-item label="学分/学时">{{ current?.credit }} / {{ current?.hours }}</el-descriptions-item>
        <el-descriptions-item label="开设理由" :span="2">{{ current?.applyReason }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item label="审批结论">
          <el-radio-group v-model="auditForm.pass">
            <el-radio-button value="1">批准入库</el-radio-button>
            <el-radio-button value="0">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.pass === '1'" label="开课学院" required>
          <el-select v-model="auditForm.openCollege" style="width: 100%">
            <el-option v-for="c in colleges" :key="c.collegeId" :label="c.collegeName" :value="c.collegeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核意见" :required="auditForm.pass === '0'">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialog = false">取消</el-button>
        <el-button type="primary" @click="doAudit">提交</el-button>
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
const page = ref(1)
const size = ref(20)
const pageRows = computed(() => {
  const start = (page.value - 1) * size.value
  return rows.value.slice(start, start + size.value)
})
watch(size, () => { page.value = 1 })
/** 教务处（校级）：申报通识并下发学院，终审入库 */
const isSchoolOfficer = computed(() => hasPerm('P07'))
/** 学院教务：申报专业必修/选修，初审本学院申报并补充下发的通识课程 */
const isCollegeOfficer = computed(() => !isSchoolOfficer.value && hasPerm('P21'))
const canApply = computed(() => hasPerm('P06') || isCollegeOfficer.value || isSchoolOfficer.value)
/** 操作列只承载初审/补充/终审动作，教师侧不展示 */
const canAudit = computed(() => isCollegeOfficer.value || isSchoolOfficer.value)
const rows = ref([])
const colleges = ref([])
const scope = ref('mine')
const status = ref('')
const applyDialog = ref(false)
const auditDialog = ref(false)
const supplementDialog = ref(false)
const form = reactive({})
const auditForm = reactive({ pass: '1', opinion: '', openCollege: '' })
const supplementForm = reactive({ credit: 2, hours: 32, applyReason: '', opinion: '' })
const current = ref(null)

const TYPE_NAMES = {
  11: '11 校级必修',
  12: '12 院级必修',
  13: '13 专业必修',
  14: '14 专业选修',
  15: '15 通识',
  16: '16 实践教学',
  17: '17 素质拓展'
}
/** 课程性质按角色限定：教务处 11/15；学院教务 12/13/14/16；教师 17 */
const types = computed(() => {
  let codes
  if (isSchoolOfficer.value) codes = [11, 15]
  else if (isCollegeOfficer.value) codes = [12, 13, 14, 16]
  else codes = [17]
  return codes.map((c) => ({ value: String(c), label: TYPE_NAMES[c] }))
})
const typeName = (t) => TYPE_NAMES[t] || t
const collegeName = (id) => colleges.value.find((c) => c.collegeId === id)?.collegeName || '-'

/** 下发到本学院的通识课程由本学院教务补充 */
const canSupplement = (row) =>
  isCollegeOfficer.value &&
  row.auditStatus === '4' &&
  (!user.user?.collegeId || row.dispatchCollege === user.user.collegeId)

async function load() {
  rows.value = await api.applies({
    status: status.value || undefined,
    mine: scope.value === 'mine' ? '1' : undefined
  })
  page.value = 1
}

function openApply() {
  Object.keys(form).forEach((k) => delete form[k])
  Object.assign(form, { courseType: types.value[0].value, credit: 2, hours: 32, dispatchCollege: '' })
  applyDialog.value = true
}

function openSupplement(row) {
  current.value = row
  Object.assign(supplementForm, {
    credit: Number(row.credit) || 2,
    hours: row.hours || 32,
    applyReason: row.applyReason || '',
    opinion: ''
  })
  supplementDialog.value = true
}

async function doSupplement() {
  await api.supplementApply(current.value.applyId, { ...supplementForm })
  ElMessage.success('已补充并提交教务处审核')
  supplementDialog.value = false
  await load()
}

async function submitApply() {
  if (isSchoolOfficer.value && form.courseType === '15' && !form.dispatchCollege) {
    ElMessage.warning('通识课程须指定下发学院')
    return
  }
  await api.applyCourse({ ...form })
  ElMessage.success(
    isSchoolOfficer.value
      ? form.courseType === '15'
        ? '通识课程已下发学院，待学院补充后由教务处审定'
        : '校级必修申报已提交，等待教务处审定'
      : isCollegeOfficer.value
        ? '申报已提交，等待教务处审定'
        : '申报已提交，等待学院初审'
  )
  applyDialog.value = false
  await load()
}

function openAudit(row) {
  current.value = row
  auditForm.pass = '1'
  auditForm.opinion = ''
  auditForm.openCollege = ''
  auditDialog.value = true
}

async function doAudit() {
  await api.auditApply(current.value.applyId, { ...auditForm })
  ElMessage.success('审批完成')
  auditDialog.value = false
  await load()
}

/** 学院初审：填写学院意见后转教务处终审（P1-12） */
async function collegeAudit(row, pass) {
  const { value } = await ElMessageBox.prompt(
    pass === '1' ? '请填写学院初审意见（通过后转教务处审定）' : '请填写驳回理由',
    `${pass === '1' ? '初审通过' : '初审驳回'}：${row.courseName}`,
    { inputValue: pass === '1' ? '课程内容符合专业培养目标，同意上报教务处' : '' }
  )
  await api.collegeAuditApply(row.applyId, { pass, opinion: value })
  ElMessage.success('学院初审已提交')
  await load()
}

onMounted(async () => {
  colleges.value = await api.colleges()
  // 学院教务默认展示本学院申报，教务处默认展示全部申报
  if (isCollegeOfficer.value) scope.value = 'college'
  else if (isSchoolOfficer.value) scope.value = 'all'
  await load()
})
</script>
