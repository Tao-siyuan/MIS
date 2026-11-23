import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('mis_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function gotoLogin() {
  sessionStorage.removeItem('mis_token')
  sessionStorage.removeItem('mis_user')
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    if (body && body.code === 401) {
      gotoLogin()
    }
    ElMessage.error((body && body.msg) || '请求失败')
    return Promise.reject(new Error((body && body.msg) || '请求失败'))
  },
  (error) => {
    const msg = error?.response?.data?.msg || error.message || '网络异常'
    if (error?.response?.status === 401) {
      gotoLogin()
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export const api = {
  // 认证
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),

  // 基础档案
  colleges: (params) => http.get('/colleges', { params }),
  addCollege: (data) => http.post('/colleges', data),
  updateCollege: (id, data) => http.put(`/colleges/${id}`, data),
  collegeStatus: (id, status) => http.put(`/colleges/${id}/status`, null, { params: { status } }),
  majors: (params) => http.get('/majors', { params }),
  addMajor: (data) => http.post('/majors', data),
  updateMajor: (id, data) => http.put(`/majors/${id}`, data),
  majorStatus: (id, status) => http.put(`/majors/${id}/status`, null, { params: { status } }),
  deleteMajor: (id) => http.delete(`/majors/${id}`),
  classes: (params) => http.get('/classes', { params }),
  teachers: (params) => http.get('/teachers', { params }),

  // 培养方案
  programs: (params) => http.get('/programs', { params }),
  saveProgram: (data) => http.post('/programs', data),
  reviseProgram: (data) => http.post('/programs/revise', data),
  programCourses: (params) => http.get('/programs/courses', { params }),
  addProgramCourse: (data) => http.post('/programs/courses', data),
  removeProgramCourse: (params) => http.delete('/programs/courses', { params }),
  creditCheck: (params) => http.get('/programs/credit-check', { params }),
  submitProgram: (data) => http.post('/programs/submit', data),
  reviewProgram: (data) => http.post('/programs/review', data),
  confirmCross: (data) => http.put('/programs/cross-confirm', data),
  reviseProgram: (data) => http.post('/programs/revise', data),
  programVersions: (params) => http.get('/programs/versions', { params }),
  pendingCross: () => http.get('/programs/pending-cross'),
  generalPlans: (params) => http.get('/general-plans', { params }),
  saveGeneralPlan: (data) => http.post('/general-plans', data),
  confirmGeneralPlan: (data) => http.put('/general-plans/confirm', data),
  publishGeneralPlan: (data) => http.put('/general-plans/publish', data),

  // 课程
  courses: (params) => http.get('/courses', { params }),
  addCourse: (data) => http.post('/courses', data),
  updateCourse: (id, data) => http.put(`/courses/${id}`, data),
  courseStatus: (id, status) => http.put(`/courses/${id}/status`, null, { params: { status } }),
  applies: (params) => http.get('/new-course-applies', { params }),
  applyCourse: (data) => http.post('/new-course-applies', data),
  auditApply: (id, data) => http.post(`/new-course-applies/${id}/audit`, data),
  collegeAuditApply: (id, data) => http.post(`/new-course-applies/${id}/college-audit`, data),

  // 教学班与排课
  teachingClasses: (params) => http.get('/teaching-classes', { params }),
  saveTeachingClass: (data) => http.post('/teaching-classes', data),
  batchGenerate: (data) => http.post('/teaching-classes/batch-generate', data),
  tcStatus: (tcId, status, reason) =>
    http.put(`/teaching-classes/${tcId}/status`, null, { params: { status, reason } }),
  deleteTc: (tcId) => http.delete(`/teaching-classes/${tcId}`),
  scheduleResults: (params) => http.get('/schedule/results', { params }),
  workbench: (params) => http.get('/schedule/workbench', { params }),
  autoSchedule: (data) => http.post('/schedule/auto', data),
  assignSchedule: (data) => http.post('/schedule/assign', data),
  checkSchedule: (data) => http.post('/schedule/check', data),
  removeSchedule: (schedId) => http.delete(`/schedule/results/${schedId}`),
  conflicts: (params) => http.get('/schedule/conflicts', { params }),
  resolveConflict: (id, data) => http.post(`/schedule/conflicts/${id}/resolve`, data),
  publishSchedule: (data) => http.post('/schedule/publish', data),
  rules: () => http.get('/schedule/rules'),
  saveRule: (data) => http.post('/schedule/rules', data),
  rooms: (params) => http.get('/schedule/rooms', { params }),
  saveRoom: (data) => http.post('/schedule/rooms', data),
  roomStatus: (id, status) => http.put(`/schedule/rooms/${id}/status`, null, { params: { status } }),

  // 调课
  adjustments: (params) => http.get('/adjustments', { params }),
  applyAdjust: (data) => http.post('/adjustments', data),
  approveAdjust: (id, data) => http.post(`/adjustments/${id}/approve`, data),
  rejectAdjust: (id, data) => http.post(`/adjustments/${id}/reject`, data),
  restoreAdjust: (id) => http.post(`/adjustments/${id}/restore`),

  // 课表
  terms: () => http.get('/timetable/terms'),
  timetable: (params) => http.get('/timetable', { params }),
  timetableHistory: (params) => http.get('/timetable/history', { params }),

  // 辅修/双学位
  dualEnrolls: (params) => http.get('/dual/enrolls', { params }),
  dualEnroll: (data) => http.post('/dual/enrolls', data),
  auditDualEnroll: (id, data) => http.post(`/dual/enrolls/${id}/audit`, data),
  finishDual: (id, status) => http.put(`/dual/enrolls/${id}/finish`, null, { params: { status } }),
  studyRecords: (params) => http.get('/dual/study-records', { params }),
  generateStudy: (data) => http.post('/dual/study-records/generate', data),
  approveStudy: (id, data) => http.post(`/dual/study-records/${id}/approve`, data),
  creditSummary: (params) => http.get('/dual/credit-summary', { params }),
  dualConflicts: (params) => http.get('/dual/conflicts', { params }),

  // 学分转换
  creditApplies: (params) => http.get('/credit-transfers', { params }),
  creditItems: (id) => http.get(`/credit-transfers/${id}/items`),
  applyCredit: (data) => http.post('/credit-transfers', data),
  updateCreditItem: (applyId, seqNo, data) => http.put(`/credit-transfers/${applyId}/items/${seqNo}`, data),
  auditCredit: (id, data) => http.post(`/credit-transfers/${id}/audit`, data),

  // 评价
  evalPlans: (params) => http.get('/eval/plans', { params }),
  saveEvalPlan: (data) => http.post('/eval/plans', data),
  publishEvalPlan: (id) => http.post(`/eval/plans/${id}/publish`),
  finishEvalPlan: (id) => http.post(`/eval/plans/${id}/finish`),
  evalTasks: (id) => http.get(`/eval/plans/${id}/tasks`),
  submitEval: (data) => http.post('/eval/submit', data),
  evalStats: (id) => http.get(`/eval/plans/${id}/stats`),
  evalRecords: (id, params) => http.get(`/eval/plans/${id}/records`, { params }),

  // 角色与日志
  roles: () => http.get('/roles'),
  saveRole: (data) => http.post('/roles', data),
  updateRole: (id, data) => http.put(`/roles/${id}`, data),
  roleStatus: (id, status) => http.put(`/roles/${id}/status`, null, { params: { status } }),
  users: (params) => http.get('/users', { params }),
  changeUserRole: (id, roleId) => http.put(`/users/${id}/role`, null, { params: { roleId } }),
  changeUserRoles: (id, roleIds) => http.put(`/users/${id}/roles`, { roleIds }),
  changeUserStatus: (id, status) => http.put(`/users/${id}/status`, null, { params: { status } }),
  resetPassword: (id, password) => http.put(`/users/${id}/password`, { password }),

  logs: (params) => http.get('/logs', { params }),

  // 首页
  overview: () => http.get('/dashboard/overview')
}

export default api
