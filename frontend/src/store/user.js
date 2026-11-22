import { reactive } from 'vue'

const state = reactive({
  token: localStorage.getItem('mis_token') || '',
  user: JSON.parse(localStorage.getItem('mis_user') || 'null')
})

export function setSession(data) {
  state.token = data.token
  state.user = data
  localStorage.setItem('mis_token', data.token)
  localStorage.setItem('mis_user', JSON.stringify(data))
}

export function clearSession() {
  state.token = ''
  state.user = null
  localStorage.removeItem('mis_token')
  localStorage.removeItem('mis_user')
}

export function userStore() {
  return state
}

export function hasPerm(code) {
  return !!(state.user && state.user.perms && state.user.perms.includes(code))
}

export function hasAnyPerm(codes) {
  if (!codes || codes.length === 0) return true
  return codes.some((c) => hasPerm(c))
}
