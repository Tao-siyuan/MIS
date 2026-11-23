import { reactive } from 'vue'

/**
 * 会话保存在 sessionStorage 中：每个浏览器标签页拥有独立会话，
 * 因此可以在多个标签页分别登录不同账号（互不覆盖）。
 */
const TOKEN_KEY = 'mis_token'
const USER_KEY = 'mis_user'

function readUser() {
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

const state = reactive({
  token: sessionStorage.getItem(TOKEN_KEY) || '',
  user: readUser(),
  /** 是否具备某功能权限码 */
  hasPerm(code) {
    return !!(this.user && this.user.perms && this.user.perms.includes(code))
  },
  /** 具备任一权限码 */
  hasAnyPerm(codes) {
    if (!codes || codes.length === 0) return true
    return codes.some((c) => this.hasPerm(c))
  },
  isAdmin() {
    return !!(this.user && (this.user.roleId === 'R01' || this.hasPerm('P20')))
  },
  isStudent() {
    return !!(this.user && this.user.classId && !this.user.teacherId)
  },
  isTeacher() {
    return !!(this.user && this.user.teacherId)
  }
})

export function setSession(data) {
  state.token = data.token
  state.user = data
  sessionStorage.setItem(TOKEN_KEY, data.token)
  sessionStorage.setItem(USER_KEY, JSON.stringify(data))
}

export function clearSession() {
  state.token = ''
  state.user = null
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}

export function userStore() {
  return state
}

export function hasPerm(code) {
  return state.hasPerm(code)
}

export function hasAnyPerm(codes) {
  return state.hasAnyPerm(codes)
}
