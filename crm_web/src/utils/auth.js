import { computed, reactive } from 'vue'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'

const STORAGE_KEY = 'seedcrm.auth-token'

const routeMap = {
  CLUE: '/clues',
  ORDER: '/orders',
  PLANORDER: '/plan-orders',
  SCHEDULER: '/scheduler',
  PERMISSION: '/permission',
  SALARY: '/salary',
  DISTRIBUTOR: '/distributors',
  FINANCE: '/finance'
}

export const authState = reactive({
  token: window.localStorage.getItem(STORAGE_KEY) || '',
  currentUser: null,
  initialized: false
})

export const currentUser = computed(() => authState.currentUser)

export const demoAccounts = [
  { username: 'admin', password: '123456', title: '管理员', description: '可查看全部模块并维护权限策略。' },
  { username: 'clue_manager', password: '123456', title: '线索主管', description: '可查看线索池和调度同步。' },
  { username: 'online_cs', password: '123456', title: '在线客服', description: '仅查看本人或团队已分配线索。' },
  { username: 'store_service', password: '123456', title: '门店服务', description: '处理订单与服务单主链。' },
  { username: 'finance', password: '123456', title: '财务', description: '查看财务、薪酬和订单完结权限。' },
  { username: 'private_domain', password: '123456', title: '私域服务', description: '查看绑定客户的订单与服务单。' }
]

export async function initializeAuth() {
  if (authState.initialized) {
    return authState.currentUser
  }
  if (!authState.token) {
    authState.initialized = true
    return null
  }
  try {
    authState.currentUser = await fetchCurrentUser(authState.token)
    return authState.currentUser
  } catch {
    clearAuthSession()
    return null
  } finally {
    authState.initialized = true
  }
}

export async function login(form) {
  const result = await loginRequest(form)
  authState.token = result?.token || ''
  authState.currentUser = result?.user || null
  authState.initialized = true
  persistToken()
  return authState.currentUser
}

export async function logout() {
  try {
    if (authState.token) {
      await logoutRequest(authState.token)
    }
  } catch {
    // 忽略退出链路的网络错误，优先清理本地状态。
  } finally {
    clearAuthSession()
  }
}

export function getAuthToken() {
  return authState.token
}

export function clearAuthSession() {
  authState.token = ''
  authState.currentUser = null
  authState.initialized = true
  window.localStorage.removeItem(STORAGE_KEY)
}

export function hasAccess(moduleCode) {
  if (!moduleCode) {
    return true
  }
  const modules = authState.currentUser?.allowedModules || []
  return modules.includes(moduleCode)
}

export function getFirstAccessibleRoute() {
  const modules = authState.currentUser?.allowedModules || []
  for (const moduleCode of modules) {
    if (routeMap[moduleCode]) {
      return routeMap[moduleCode]
    }
  }
  return '/login'
}

function persistToken() {
  if (authState.token) {
    window.localStorage.setItem(STORAGE_KEY, authState.token)
  } else {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}
