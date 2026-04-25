import { computed, reactive } from 'vue'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'

const STORAGE_KEY = 'seedcrm.auth-token'

const accessibleRoutes = [
  { path: '/clues', moduleCode: 'CLUE' },
  { path: '/clues/scheduling', moduleCode: 'ORDER', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'] },
  { path: '/clue-management/store-schedules', moduleCode: 'CLUE', roleCodes: ['ADMIN', 'CLUE_MANAGER'] },
  { path: '/clue-management/auto-assign', moduleCode: 'CLUE', roleCodes: ['CLUE_MANAGER', 'ADMIN'] },
  { path: '/store-service/orders', moduleCode: 'ORDER', roleCodes: ['STORE_SERVICE', 'ADMIN'] },
  { path: '/private-domain/wecom', moduleCode: 'WECOM', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'] },
  { path: '/finance', moduleCode: 'FINANCE' },
  { path: '/finance/salary-center', moduleCode: 'SALARY' },
  { path: '/system/departments', moduleCode: 'SYSTEM', roleCodes: ['ADMIN'] },
  { path: '/system/employees', moduleCode: 'SYSTEM', roleCodes: ['ADMIN', 'CLUE_MANAGER'] },
  { path: '/settings/menu', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/third-party', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/callback', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/jobs', moduleCode: 'SETTING', roleCodes: ['ADMIN'] }
]

export const authState = reactive({
  token: window.localStorage.getItem(STORAGE_KEY) || '',
  currentUser: null,
  initialized: false
})

export const currentUser = computed(() => authState.currentUser)

export const demoAccounts = [
  { username: 'admin', password: '123456', title: '管理员', description: '查看系统管理、系统设置、私域客服等全部后台模块' },
  { username: 'clue_manager', password: '123456', title: '客资主管', description: '管理客资中心、顾客排档、门店档期与本部门员工' },
  { username: 'online_cs', password: '123456', title: '在线客服', description: '查看已分配客资、顾客排档并跟进预约' },
  { username: 'store_service', password: '123456', title: '门店服务', description: '在订单列表里确认服务项目、查看确认单并进入服务单' },
  { username: 'finance', password: '123456', title: '财务', description: '查看财务与薪酬数据，处理结算与提现' },
  { username: 'private_domain', password: '123456', title: '私域服务', description: '使用企业微信触达客户并维护私域运营功能' }
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
