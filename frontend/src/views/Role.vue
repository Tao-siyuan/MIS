<template>
  <div>
    <h2 class="page-title">角色与权限</h2>
    <p class="page-desc">
      系统采用基于角色的访问控制（RBAC），按岗位设置六类角色，支持一人多角色。权限分功能权限、数据范围
      与操作权限三层，遵循最小权限与职责分离。
    </p>

    <div class="panel">
      <div class="panel-title">
        <span>角色列表</span>
        <el-button type="primary" size="small" @click="openRole()">新增角色</el-button>
      </div>
      <el-table :data="roles" border size="small">
        <el-table-column prop="roleId" label="编号" width="80" />
        <el-table-column prop="roleName" label="角色名称" width="160" />
        <el-table-column label="数据范围" width="110">
          <template #default="{ row }">
            {{ { 1: '全校', 2: '本学院', 3: '本人' }[row.dataScope] }}
          </template>
        </el-table-column>
        <el-table-column label="功能权限" min-width="300">
          <template #default="{ row }">
            <el-tag v-for="p in (row.funcPerm || '').split(',').filter(Boolean)" :key="p" size="small" effect="plain" style="margin: 2px">
              {{ permName(p) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="适用岗位" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.roleStatus === '1' ? 'success' : 'info'">
              {{ row.roleStatus === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRole(row)">配置</el-button>
            <el-button link :type="row.roleStatus === '1' ? 'danger' : 'success'" size="small" @click="toggleRole(row)">
              {{ row.roleStatus === '1' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel">
      <div class="panel-title">
        <span>用户与角色分配</span>
        <el-input v-model="keyword" placeholder="账号 / 姓名" clearable size="small" style="width: 200px" @change="loadUsers">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <el-table :data="users" border size="small">
        <el-table-column prop="userId" label="用户编号" width="130" />
        <el-table-column prop="userName" label="登录名" width="110" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="roleName" label="角色" width="150" />
        <el-table-column prop="collegeId" label="学院" width="80" />
        <el-table-column prop="classId" label="班级" width="100" />
        <el-table-column prop="lastLogin" label="最近登录" width="170" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '1' ? 'success' : 'info'">{{ row.status === '1' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-select
              :model-value="row.roleId"
              size="small"
              style="width: 110px"
              @change="(v) => changeRole(row, v)"
            >
              <el-option v-for="r in roles" :key="r.roleId" :label="r.roleName" :value="r.roleId" />
            </el-select>
            <el-button link type="warning" size="small" @click="resetPwd(row)">重置密码</el-button>
            <el-button link :type="row.status === '1' ? 'danger' : 'success'" size="small" @click="toggleUser(row)">
              {{ row.status === '1' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="roleDialog" :title="roleForm.isNew ? '新增角色' : '配置角色权限'" width="620px">
      <el-form :model="roleForm" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="角色编号" required>
              <el-input v-model="roleForm.roleId" :disabled="!roleForm.isNew" maxlength="4" placeholder="R07" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" required>
              <el-input v-model="roleForm.roleName" maxlength="20" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="数据范围">
          <el-radio-group v-model="roleForm.dataScope">
            <el-radio-button value="1">全校</el-radio-button>
            <el-radio-button value="2">本学院</el-radio-button>
            <el-radio-button value="3">本人</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="功能权限">
          <el-checkbox-group v-model="selectedPerms" class="perm-group">
            <el-checkbox v-for="p in permList" :key="p.code" :value="p.code" border>{{ p.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="适用岗位">
          <el-input v-model="roleForm.remark" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const roles = ref([])
const users = ref([])
const keyword = ref('')
const roleDialog = ref(false)
const roleForm = reactive({})
const selectedPerms = ref([])

const permList = [
  { code: 'P01', name: '学院与专业维护' },
  { code: 'P02', name: '培养方案编制' },
  { code: 'P03', name: '方案审核发布' },
  { code: 'P04', name: '通识课程计划' },
  { code: 'P05', name: '课程库维护' },
  { code: 'P06', name: '新增课程申报' },
  { code: 'P07', name: '新增课程审批' },
  { code: 'P08', name: '教学班管理' },
  { code: 'P09', name: '排课与课表发布' },
  { code: 'P10', name: '调课申请' },
  { code: 'P11', name: '调课审批' },
  { code: 'P12', name: '课表查询' },
  { code: 'P13', name: '辅修/双学位' },
  { code: 'P14', name: '学分转换' },
  { code: 'P15', name: '课程评价' },
  { code: 'P16', name: '角色与日志审计' }
]
const permName = (code) => permList.find((p) => p.code === code)?.name || code

async function loadRoles() {
  roles.value = await api.roles()
}

async function loadUsers() {
  users.value = await api.users({ keyword: keyword.value || undefined })
}

function openRole(row) {
  Object.keys(roleForm).forEach((k) => delete roleForm[k])
  if (row) {
    Object.assign(roleForm, { ...row, isNew: false })
    selectedPerms.value = (row.funcPerm || '').split(',').filter(Boolean)
  } else {
    Object.assign(roleForm, { isNew: true, dataScope: '3', roleStatus: '1' })
    selectedPerms.value = []
  }
  roleDialog.value = true
}

async function saveRole() {
  const payload = {
    roleId: roleForm.roleId,
    roleName: roleForm.roleName,
    dataScope: roleForm.dataScope,
    funcPerm: selectedPerms.value.join(','),
    remark: roleForm.remark,
    roleStatus: roleForm.roleStatus || '1'
  }
  if (roleForm.isNew) {
    await api.saveRole(payload)
  } else {
    await api.updateRole(roleForm.roleId, payload)
  }
  ElMessage.success('保存成功')
  roleDialog.value = false
  await loadRoles()
  await loadUsers()
}

async function toggleRole(row) {
  await api.roleStatus(row.roleId, row.roleStatus === '1' ? '0' : '1')
  ElMessage.success('状态已更新')
  await loadRoles()
}

async function changeRole(row, roleId) {
  await api.changeUserRole(row.userId, roleId)
  ElMessage.success('角色已调整')
  await loadUsers()
}

async function toggleUser(row) {
  await api.changeUserStatus(row.userId, row.status === '1' ? '0' : '1')
  ElMessage.success('状态已更新')
  await loadUsers()
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt('请输入新密码', `重置 ${row.realName} 的密码`, {
    inputValue: 'tsy050904'
  })
  await api.resetPassword(row.userId, value)
  ElMessage.success('密码已重置')
}

onMounted(async () => {
  await loadRoles()
  await loadUsers()
})
</script>

<style scoped>
.perm-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  width: 100%;
}

.perm-group :deep(.el-checkbox) {
  margin-right: 0;
}
</style>
