import { computed, reactive } from 'vue'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from '../api/auth'
import { loadSystemConsoleState } from './systemConsoleStore'

const STORAGE_KEY = 'seedcrm.auth-token'

const accessibleRoutes = [
  { path: '/clues', moduleCode: 'CLUE' },
  { path: '/clues/scheduling', moduleCode: 'ORDER', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'] },
  { path: '/clue-management/store-schedules', moduleCode: 'CLUE', roleCodes: ['ADMIN', 'CLUE_MANAGER'] },
  { path: '/clue-management/auto-assign', moduleCode: 'CLUE', roleCodes: ['CLUE_MANAGER', 'ADMIN'] },
  {
    path: '/store-service/orders',
    moduleCode: 'ORDER',
    roleCodes: ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR', 'ADMIN']
  },
  { path: '/store-service/service-design', moduleCode: 'PLANORDER', roleCodes: ['STORE_MANAGER', 'ADMIN'] },
  { path: '/store-service/personnel', moduleCode: 'SYSTEM', roleCodes: ['STORE_MANAGER', 'ADMIN'] },
  { path: '/store-service/roles', moduleCode: 'SYSTEM', roleCodes: ['STORE_MANAGER', 'ADMIN'] },
  { path: '/private-domain/live-code', moduleCode: 'WECOM', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'] },
  { path: '/private-domain/customer-profile', moduleCode: 'WECOM', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'] },
  { path: '/private-domain/moments', moduleCode: 'WECOM', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'] },
  { path: '/private-domain/tags', moduleCode: 'WECOM', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'] },
  { path: '/finance', moduleCode: 'FINANCE' },
  { path: '/finance/salary/my', moduleCode: 'SALARY' },
  { path: '/finance/salary/settlements', moduleCode: 'SALARY', roleCodes: ['ADMIN', 'FINANCE'] },
  { path: '/finance/salary/withdrawals', moduleCode: 'SALARY', roleCodes: ['ADMIN', 'FINANCE'] },
  { path: '/finance/salary/refund-adjustments', moduleCode: 'SALARY', roleCodes: ['ADMIN', 'FINANCE'] },
  { path: '/finance/salary/settlement-config', moduleCode: 'SALARY', roleCodes: ['ADMIN', 'FINANCE'] },
  { path: '/finance/salary-config/distributor', moduleCode: 'SALARY', roleCodes: ['ADMIN'] },
  { path: '/system/departments', moduleCode: 'SYSTEM', roleCodes: ['ADMIN'] },
  { path: '/system/employees', moduleCode: 'SYSTEM', roleCodes: ['ADMIN', 'CLUE_MANAGER'] },
  { path: '/settings/base/domain', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/base/wecom', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/menu', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/payment', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/third-party', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/callback', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/jobs', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/debug', moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
  { path: '/settings/integration/distribution-api', moduleCode: 'SETTING', roleCodes: ['ADMIN'] }
]

export const authState = reactive({
  token: window.localStorage.getItem(STORAGE_KEY) || '',
  currentUser: null,
  initialized: false
})

export const currentUser = computed(() => authState.currentUser)

export const demoAccounts = [
  {
    username: 'admin',
    password: '123456',
    title: '管理员',
    description: '查看系统管理、系统设置、私域客服等全部后台模块',
    loginMode: 'hq'
  },
  {
    username: 'clue_manager',
    password: '123456',
    title: '客资主管',
    description: '管理客资中心、顾客排档、门店档期与值班安排',
    loginMode: 'hq'
  },
  {
    username: 'online_cs',
    password: '123456',
    title: '在线客服',
    description: '跟进已分配客资，处理预约与名单整理',
    loginMode: 'hq'
  },
  {
    username: 'store_service',
    password: '123456',
    title: '门店服务',
    description: '处理订单核销、服务单填写与门店履约',
    loginMode: 'store',
    storeName: '静安门店'
  },
  {
    username: 'store_manager',
    password: '123456',
    title: '店长',
    description: '管理门店人员、角色与服务单模板',
    loginMode: 'store',
    storeName: '静安门店'
  },
  {
    username: 'photo_a',
    password: '123456',
    title: '摄影',
    description: '参与门店服务履约与服务单协作',
    loginMode: 'store',
    storeName: '静安门店'
  },
  {
    username: 'makeup_a',
    password: '123456',
    title: '化妆师',
    description: '参与到店服务与服务单填写',
    loginMode: 'store',
    storeName: '静安门店'
  },
  {
    username: 'selector_a',
    password: '123456',
    title: '选片负责人',
    description: '查看已核销订单并完成选片相关履约',
    loginMode: 'store',
    storeName: '静安门店'
  },
  {
    username: 'finance',
    password: '123456',
    title: '财务',
    description: '查看财务与薪酬数据，处理结算与提现',
    loginMode: 'hq'
  },
  {
    username: 'private_domain',
    password: '123456',
    title: '私域客服',
    description: '使用企业微信触达客户并维护私域运营能力',
    loginMode: 'hq'
  }
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
  if (!isCurrentRoleEnabled()) {
    return false
  }
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
  if (!isCurrentRoleEnabled()) {
    return false
  }
  const configuredRole = getConfiguredCurrentRole()
  if (configuredRole?.moduleCodes?.length) {
    return configuredRole.moduleCodes.includes(moduleCode)
  }
  const modules = authState.currentUser?.allowedModules || []
  return modules.includes(moduleCode)
}

export function hasAccess(moduleCode, roleCodes = []) {
  return hasModule(moduleCode) && hasRole(roleCodes)
}

export function hasRouteAccess(path, moduleCode, roleCodes = []) {
  if (!hasAccess(moduleCode, roleCodes)) {
    return false
  }
  const normalizedPath = normalizePath(path)
  const backendRoutes = getBackendMenuRoutes()
  const hasBackendRouteSource = backendRoutes.length > 0
  if (hasBackendRouteSource && !backendRoutes.includes(normalizedPath)) {
    return false
  }
  const configuredMenu = getConfiguredMenuConfigs().find((item) => normalizePath(item.routePath) === normalizedPath)
  if (configuredMenu) {
    return configuredMenu.isEnabled !== 0
      && hasModule(configuredMenu.moduleCode)
      && hasRole(configuredMenu.roleCodes || [])
  }
  if (hasBackendRouteSource) {
    return true
  }
  return true
}

export function getFirstAccessibleRoute() {
  const defaultRoute = normalizePath(authState.currentUser?.defaultRoute)
  if (defaultRoute && hasRouteAccess(defaultRoute)) {
    return defaultRoute
  }

  const configuredRoute = getConfiguredMenuConfigs()
    .filter((item) => item.isEnabled !== 0)
    .sort((left, right) => Number(left.id || 0) - Number(right.id || 0))
    .find((item) => hasRouteAccess(item.routePath, item.moduleCode, item.roleCodes || []))
  if (configuredRoute?.routePath) {
    return configuredRoute.routePath
  }

  const backendRoute = getBackendMenuRoutes()[0]
  if (backendRoute) {
    return backendRoute
  }

  for (const route of accessibleRoutes) {
    if (hasRouteAccess(route.path, route.moduleCode, route.roleCodes)) {
      return route.path
    }
  }
  return '/login'
}

export function getEffectiveMenuConfigs() {
  const currentRoleCode = getCurrentRoleCode()
  const merged = new Map()
  for (const item of flattenMenuTree(authState.currentUser?.menuTree || [])) {
    if (!item.routePath) {
      continue
    }
    merged.set(normalizePath(item.routePath), {
      id: item.key,
      menuGroup: item.menuGroup || '',
      menuName: item.label,
      routePath: item.routePath,
      moduleCode: item.moduleCode,
      roleCodes: currentRoleCode ? [currentRoleCode] : [],
      isEnabled: 1,
      permissionCode: item.permissionCode,
      source: 'server'
    })
  }
  for (const item of getConfiguredMenuConfigs()) {
    const routePath = normalizePath(item.routePath)
    if (!routePath) {
      continue
    }
    const serverItem = merged.get(routePath)
    if (serverItem) {
      merged.set(routePath, {
        ...serverItem,
        menuGroup: item.menuGroup || serverItem.menuGroup,
        menuName: item.menuName || serverItem.menuName,
        routePath,
        isEnabled: item.isEnabled,
        source: 'server-local'
      })
    } else {
      merged.set(routePath, {
        ...item,
        routePath,
        source: 'local'
      })
    }
  }
  return Array.from(merged.values())
}

function persistToken() {
  if (authState.token) {
    window.localStorage.setItem(STORAGE_KEY, authState.token)
  } else {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}

function getConfiguredCurrentRole() {
  const currentRoleCode = getCurrentRoleCode()
  if (!currentRoleCode) {
    return null
  }
  try {
    return (loadSystemConsoleState().roles || []).find((role) => String(role.roleCode || '').trim().toUpperCase() === currentRoleCode) || null
  } catch {
    return null
  }
}

function isCurrentRoleEnabled() {
  if (!authState.currentUser) {
    return false
  }
  const configuredRole = getConfiguredCurrentRole()
  return configuredRole ? configuredRole.isEnabled !== 0 : true
}

function getConfiguredMenuConfigs() {
  try {
    return loadSystemConsoleState().menuConfigs || []
  } catch {
    return []
  }
}

function getCurrentRoleCode() {
  return String(authState.currentUser?.roleCode || '').trim().toUpperCase()
}

function getBackendMenuRoutes() {
  const routes = authState.currentUser?.menuRoutes || []
  return routes.map(normalizePath).filter(Boolean)
}

function normalizePath(path) {
  const value = String(path || '').trim()
  if (!value) {
    return ''
  }
  return value.startsWith('/') ? value : `/${value}`
}

function flattenMenuTree(nodes = [], parentGroup = '') {
  const entries = []
  for (const node of nodes || []) {
    const nextGroup = node.routePath ? parentGroup : [parentGroup, node.label].filter(Boolean).join(' / ')
    if (node.routePath) {
      entries.push({
        ...node,
        menuGroup: parentGroup
      })
    }
    entries.push(...flattenMenuTree(node.children || [], nextGroup))
  }
  return entries
}
