import { createRouter, createWebHashHistory } from 'vue-router'
import { hasAnyPerm } from '../store/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'colleges', component: () => import('../views/CollegeMajor.vue'), meta: { title: '学院与专业', perm: ['P01'] } },
      { path: 'programs', component: () => import('../views/Program.vue'), meta: { title: '培养方案', perm: ['P02', 'P03'] } },
      { path: 'general-plan', component: () => import('../views/GeneralPlan.vue'), meta: { title: '通识课程计划', perm: ['P04'] } },
      { path: 'courses', component: () => import('../views/Course.vue'), meta: { title: '课程库', perm: ['P05'] } },
      { path: 'course-apply', component: () => import('../views/CourseApply.vue'), meta: { title: '新增课程申报', perm: ['P06', 'P07', 'P21'] } },
      { path: 'teaching-class', component: () => import('../views/TeachingClass.vue'), meta: { title: '教学班管理', perm: ['P08', 'P19'] } },
      { path: 'schedule', component: () => import('../views/Schedule.vue'), meta: { title: '排课工作台', perm: ['P09'] } },
      { path: 'adjust', component: () => import('../views/Adjust.vue'), meta: { title: '调课管理', perm: ['P10', 'P11'] } },
      { path: 'timetable', component: () => import('../views/Timetable.vue'), meta: { title: '课表查询', perm: ['P12'] } },
      { path: 'dual', component: () => import('../views/Dual.vue'), meta: { title: '辅修/双学位', perm: ['P13'] } },
      { path: 'credit', component: () => import('../views/CreditTransfer.vue'), meta: { title: '转专业学分转换', perm: ['P14'] } },
      { path: 'eval', component: () => import('../views/Eval.vue'), meta: { title: '课程评价', perm: ['P15'] } },
      { path: 'roles', component: () => import('../views/Role.vue'), meta: { title: '角色与权限', perm: ['P20'] } },
      { path: 'logs', component: () => import('../views/Log.vue'), meta: { title: '操作日志', perm: ['P16'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = sessionStorage.getItem('mis_token')
  if (to.meta.public) {
    return true
  }
  if (!token) {
    return { path: '/login' }
  }
  if (to.meta.perm && !hasAnyPerm(to.meta.perm)) {
    return { path: '/dashboard' }
  }
  document.title = `${to.meta.title || ''} · 专业设置及排课管理系统`
  return true
})

export default router
