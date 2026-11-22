<template>
  <el-container class="layout">
    <el-aside width="238px" class="aside">
      <div class="logo">
        <div class="logo-mark">MIS</div>
        <div class="logo-text">
          <div class="logo-title">专业设置及排课</div>
          <div class="logo-sub">管理系统 · 北京科技大学</div>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="transparent"
        text-color="#c3d3e8"
        active-text-color="#ffffff"
        class="menu"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </el-menu-item>

        <el-sub-menu v-if="show(['P01', 'P02', 'P03', 'P04'])" index="plan">
          <template #title><el-icon><Files /></el-icon><span>专业设置</span></template>
          <el-menu-item v-if="show(['P01'])" index="/colleges">学院与专业</el-menu-item>
          <el-menu-item v-if="show(['P02', 'P03'])" index="/programs">培养方案</el-menu-item>
          <el-menu-item v-if="show(['P04'])" index="/general-plan">通识课程计划</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="show(['P05', 'P06', 'P07'])" index="course">
          <template #title><el-icon><Notebook /></el-icon><span>课程设置</span></template>
          <el-menu-item v-if="show(['P05'])" index="/courses">课程库</el-menu-item>
          <el-menu-item v-if="show(['P06', 'P07'])" index="/course-apply">新增课程申报</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="show(['P08', 'P09', 'P10', 'P11', 'P12'])" index="sched">
          <template #title><el-icon><Calendar /></el-icon><span>排课与课表</span></template>
          <el-menu-item v-if="show(['P08'])" index="/teaching-class">教学班管理</el-menu-item>
          <el-menu-item v-if="show(['P09'])" index="/schedule">排课工作台</el-menu-item>
          <el-menu-item v-if="show(['P10', 'P11'])" index="/adjust">调课管理</el-menu-item>
          <el-menu-item v-if="show(['P12'])" index="/timetable">课表查询</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="show(['P13', 'P14'])" index="ext">
          <template #title><el-icon><Reading /></el-icon><span>延伸业务</span></template>
          <el-menu-item v-if="show(['P13'])" index="/dual">辅修 / 双学位</el-menu-item>
          <el-menu-item v-if="show(['P14'])" index="/credit">转专业学分转换</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="show(['P15', 'P16'])" index="eval">
          <template #title><el-icon><DataAnalysis /></el-icon><span>评价与监督</span></template>
          <el-menu-item v-if="show(['P15'])" index="/eval">课程评价</el-menu-item>
          <el-menu-item v-if="show(['P16'])" index="/roles">角色与权限</el-menu-item>
          <el-menu-item v-if="show(['P16'])" index="/logs">操作日志</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="crumb">
          <span class="system-name">教务管理 · 专业设置及排课</span>
          <el-divider direction="vertical" />
          <span>{{ route.meta.title }}</span>
        </div>
        <div class="user-area">
          <el-tag size="small" effect="plain" type="info">{{ user.user?.roleName }}</el-tag>
          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-avatar :size="26" class="avatar">{{ (user.user?.realName || 'U').slice(0, 1) }}</el-avatar>
              {{ user.user?.realName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ user.user?.userId }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { clearSession, hasAnyPerm, userStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const user = userStore()

const activeMenu = computed(() => route.path)
const show = (perms) => hasAnyPerm(perms)

async function onCommand(command) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确认退出当前账号？', '提示', { type: 'warning' })
    clearSession()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: linear-gradient(180deg, #1c2c46 0%, #223b5e 100%);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-mark {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4b8bd6, #2b5c9b);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 1px;
}

.logo-title {
  color: #fff;
  font-weight: 600;
  font-size: 15px;
}

.logo-sub {
  color: #8ea6c4;
  font-size: 11px;
  margin-top: 2px;
}

.menu {
  border-right: none;
  flex: 1;
  padding: 8px 0;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(84, 146, 220, 0.28) !important;
  border-left: 3px solid #6fb0ff;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
}

.header {
  background: #fff;
  border-bottom: 1px solid var(--mis-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 58px;
}

.crumb {
  color: var(--mis-muted);
  font-size: 13px;
}

.system-name {
  color: var(--mis-primary);
  font-weight: 600;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-name {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--mis-text);
  font-size: 13px;
}

.avatar {
  background: var(--mis-primary);
}

.main {
  padding: 20px 22px;
  overflow-y: auto;
}
</style>
