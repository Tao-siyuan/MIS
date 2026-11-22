<template>
  <div class="login-page">
    <div class="brand">
      <div class="brand-inner">
        <div class="brand-tag">北京科技大学 · 教务处</div>
        <h1>专业设置及排课<br />管理系统</h1>
        <p class="brand-desc">
          覆盖“专业设置—课程设置—排课—课表服务”全流程，以课程主数据为纽带，
          实现方案、教学班、排课结果与课表一次录入、全程复用。
        </p>
        <ul class="feature">
          <li><el-icon><Select /></el-icon> 培养方案版本化与学分结构校核</li>
          <li><el-icon><Select /></el-icon> 四类课程分路径申报与两级审核</li>
          <li><el-icon><Select /></el-icon> 规则化自动排课与三重冲突检测</li>
          <li><el-icon><Select /></el-icon> 辅修/双学位、学分转换与课程评价</li>
        </ul>
      </div>
    </div>

    <div class="form-side">
      <el-card class="login-card" shadow="never">
        <h2>账号登录</h2>
        <p class="muted">请使用工号 / 学号登录，初始口令见下方演示账号</p>
        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="submit">
          <el-form-item label="账号" prop="userName">
            <el-input v-model="form.userName" size="large" placeholder="登录名 / 工号 / 学号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              :prefix-icon="Lock"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <template v-if="showDemo">
          <el-divider content-position="left"><span class="muted">演示账号（点击自动填充账号与口令）</span></el-divider>
          <div class="accounts">
            <el-tag v-for="a in accounts" :key="a.user" class="account" @click="fill(a)">
              {{ a.label }}
            </el-tag>
          </div>
        </template>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import api from '../api'
import { setSession } from '../store/user'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ userName: 'admin', password: '123456' })
const rules = {
  userName: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
/** 演示账号仅在开发环境展示，生产构建不暴露可点选账号（P3-07） */
const showDemo = import.meta.env.DEV
const accounts = [
  { label: '系统管理员 admin', user: 'admin', pwd: '123456' },
  { label: '教务处管理员 B220001', user: 'B220001', pwd: 'USTB@220001' },
  { label: '学院教务员 B220002', user: 'B220002', pwd: 'USTB@220002' },
  { label: '教室管理员 B220004', user: 'B220004', pwd: 'USTB@220004' },
  { label: '教师 A230001', user: 'A230001', pwd: 'USTB@230001' },
  { label: '学生 U202341224', user: 'U202341224', pwd: 'USTB@341224' }
]

function fill(a) {
  form.userName = a.user
  form.password = a.pwd
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await api.login(form)
    setSession(data)
    ElMessage.success(`欢迎回来，${data.realName}`)
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  background: #f4f6fa;
}

.brand {
  flex: 1.15;
  background: linear-gradient(150deg, #172a45 0%, #24486f 55%, #2b5c9b 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
  overflow: hidden;
}

.brand::after {
  content: "";
  position: absolute;
  width: 520px;
  height: 520px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
  right: -160px;
  bottom: -180px;
}

.brand-inner {
  max-width: 520px;
  position: relative;
  z-index: 1;
}

.brand-tag {
  font-size: 13px;
  letter-spacing: 2px;
  color: #a9c6e8;
  margin-bottom: 18px;
}

.brand h1 {
  font-size: 38px;
  line-height: 1.35;
  margin: 0 0 18px;
  font-weight: 700;
}

.brand-desc {
  color: #c6d8ee;
  line-height: 1.9;
  font-size: 14px;
}

.feature {
  list-style: none;
  padding: 0;
  margin: 26px 0 0;
}

.feature li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #dce9f8;
  margin-bottom: 12px;
  font-size: 14px;
}

.feature .el-icon {
  color: #6fb0ff;
}

.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-card {
  width: 420px;
  border: 1px solid var(--mis-border);
  padding: 8px 12px 4px;
}

.login-card h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.login-card .muted {
  margin: 0 0 22px;
  font-size: 13px;
}

.submit {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 4px;
  background: var(--mis-primary);
  border-color: var(--mis-primary);
}

.accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.account {
  cursor: pointer;
}

@media (max-width: 900px) {
  .brand {
    display: none;
  }
}
</style>
