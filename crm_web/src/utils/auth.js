import { computed, reactive } from 'vue'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'

const STORAGE_KEY = 'seedcrm.auth-token'

const accessibleRoutes = [
  { path: '/clues', moduleCode: 'CLUE' },
  { path: '/clue-management/auto-assign', moduleCode: 'CLUE', roleCodes: ['CLUE_MANAGER', 'ADMIN'] },
  { path: '/store-service/orders', moduleCode: 'ORDER', roleCodes: ['STORE_SERVICE', 'ADMIN'] },
  { path: '/plan-orders', moduleCode: 'PLANORDER' },
  { path: '/scheduler', moduleCode: 'SCHEDULER' },
  { path: '/permission', moduleCode: 'PERMISSION' },
  { path: '/salary', moduleCode: 'SALARY' },
  { path: '/distributors', moduleCode: 'DISTRIBUTOR' },
  { path: '/finance', moduleCode: 'FINANCE' }
]

export const authState = reactive({
  token: window.localStorage.getItem(STORAGE_KEY) || '',
  currentUser: null,
  initialized: false
})

export const currentUser = computed(() => authState.currentUser)

export const demoAccounts = [
  { username: 'admin', password: '123456', title: '管理员', description: '查看所有模块并维护系统规则与权限策略' },
  { username: 'clue_manager', password: '123456', title: '客资主管', description: '管理客资中心、自动分配和值班客服配置' },
  { username: 'online_cs', password: '123456', title: '在线客服', description: '查看分配给自己的客资并跟进转化' },
  { username: 'store_service', password: '123456', title: '门店服务', description: '查看订单、填写确认单并推进门店履约' },
  { username: 'finance', password: '123456', title: '财务', description: '查看财务与薪酬数据，处理结算与提现' },
  { username: 'private_domain', password: '123456', title: '私域服务', description: '查看绑定客户的订单和服务记录' }
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

export function hasRole(roleCodes = []) {
  if (!roleCodes?.length) {
    return true
  }
  const currentRoleCode = String(authState.currentUser?.roleCode || '').trim().toUpperCase()
  return roleCodes.map((item) => String(item).trim().toUpperCase()).includes(currentRoleCode)
}

export function hasModule(moduleCode) {
  if (!moduleCode) {
    return true
  }
  const modules = authState.currentUser?.allowedModules || []
  return modules.includes(moduleCode)
}

export function hasAccess(moduleCode, roleCodes = []) {
  return hasModule(moduleCode) && hasRole(roleCodes)
}

export function getFirstAccessibleRoute() {
  for (const route of accessibleRoutes) {
    if (hasAccess(route.moduleCode, route.roleCodes)) {
      return route.path
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
