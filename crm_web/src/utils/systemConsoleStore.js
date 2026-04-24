const STORAGE_KEY = 'seedcrm.system-console'

const DEFAULT_STATE = {
  departments: [
    {
      id: 1,
      departmentCode: 'HQ',
      departmentName: '总部管理',
      parentCode: '',
      managerRoleCode: 'ADMIN',
      dataScopeRule: '可查看全部有效数据并进行系统配置',
      isEnabled: 1,
      remark: '系统管理员负责整体系统治理'
    },
    {
      id: 2,
      departmentCode: 'CLUE',
      departmentName: '客资中心',
      parentCode: 'HQ',
      managerRoleCode: 'CLUE_MANAGER',
      dataScopeRule: '仅查看并分配客资中心有效数据',
      isEnabled: 1,
      remark: '负责客资接入、分配和转化'
    },
    {
      id: 3,
      departmentCode: 'STORE',
      departmentName: '门店服务',
      parentCode: 'HQ',
      managerRoleCode: 'STORE_SERVICE',
      dataScopeRule: '仅处理门店范围内订单与服务单',
      isEnabled: 1,
      remark: '负责客户到店确认和服务履约'
    },
    {
      id: 4,
      departmentCode: 'FINANCE',
      departmentName: '财务中心',
      parentCode: 'HQ',
      managerRoleCode: 'FINANCE',
      dataScopeRule: '查看财务、薪酬和结算数据',
      isEnabled: 1,
      remark: '负责结算、提现与财务复核'
    },
    {
      id: 5,
      departmentCode: 'PRIVATE_DOMAIN',
      departmentName: '私域客服',
      parentCode: 'HQ',
      managerRoleCode: 'PRIVATE_DOMAIN_SERVICE',
      dataScopeRule: '查看绑定客户的企微与服务触达记录',
      isEnabled: 1,
      remark: '负责企业微信触达与私域维护'
    }
  ],
  positions: [
    {
      id: 1,
      positionCode: 'SYSTEM_ADMIN',
      positionName: '系统管理员岗',
      departmentCode: 'HQ',
      isEnabled: 1,
      remark: '管理菜单、角色、字典和参数'
    },
    {
      id: 2,
      positionCode: 'CLUE_SUPERVISOR',
      positionName: '客资主管岗',
      departmentCode: 'CLUE',
      isEnabled: 1,
      remark: '维护自动分配和值班客服'
    },
    {
      id: 3,
      positionCode: 'ONLINE_CS',
      positionName: '在线客服岗',
      departmentCode: 'CLUE',
      isEnabled: 1,
      remark: '负责客资首轮响应'
    },
    {
      id: 4,
      positionCode: 'STORE_SERVICE',
      positionName: '门店服务岗',
      departmentCode: 'STORE',
      isEnabled: 1,
      remark: '负责确认单和服务履约'
    },
    {
      id: 5,
      positionCode: 'FINANCE_SPECIALIST',
      positionName: '财务专员岗',
      departmentCode: 'FINANCE',
      isEnabled: 1,
      remark: '负责结算和打款'
    },
    {
      id: 6,
      positionCode: 'PRIVATE_DOMAIN_CS',
      positionName: '私域客服岗',
      departmentCode: 'PRIVATE_DOMAIN',
      isEnabled: 1,
      remark: '负责企微客户触达'
    }
  ],
  roles: [
    {
      id: 1,
      roleCode: 'ADMIN',
      roleName: '管理员',
      dataScope: 'ALL',
      moduleCodes: ['CLUE', 'ORDER', 'PLANORDER', 'SALARY', 'FINANCE', 'SYSTEM', 'SETTING', 'WECOM'],
      isEnabled: 1,
      remark: '拥有系统全量菜单和配置权限'
    },
    {
      id: 2,
      roleCode: 'CLUE_MANAGER',
      roleName: '客资主管',
      dataScope: 'ALL',
      moduleCodes: ['CLUE', 'SYSTEM'],
      isEnabled: 1,
      remark: '管理客资中心与本部门人员'
    },
    {
      id: 3,
      roleCode: 'ONLINE_CUSTOMER_SERVICE',
      roleName: '在线客服',
      dataScope: 'TEAM',
      moduleCodes: ['CLUE'],
      isEnabled: 1,
      remark: '处理分配给自己的客资'
    },
    {
      id: 4,
      roleCode: 'STORE_SERVICE',
      roleName: '门店服务',
      dataScope: 'STORE',
      moduleCodes: ['ORDER', 'PLANORDER'],
      isEnabled: 1,
      remark: '查看订单、确认单和服务单'
    },
    {
      id: 5,
      roleCode: 'FINANCE',
      roleName: '财务',
      dataScope: 'ALL',
      moduleCodes: ['ORDER', 'SALARY', 'FINANCE'],
      isEnabled: 1,
      remark: '查看结算与财务数据'
    },
    {
      id: 6,
      roleCode: 'PRIVATE_DOMAIN_SERVICE',
      roleName: '私域服务',
      dataScope: 'SELF',
      moduleCodes: ['WECOM'],
      isEnabled: 1,
      remark: '负责企业微信客户触达'
    }
  ],
  employees: [
    {
      id: 1,
      accountName: 'admin',
      userName: '系统管理员',
      departmentCode: 'HQ',
      positionCode: 'SYSTEM_ADMIN',
      roleCode: 'ADMIN',
      status: 'ACTIVE',
      ownedDataCount: 0,
      canLogin: 1
    },
    {
      id: 2,
      accountName: 'clue_manager',
      userName: '客资主管',
      departmentCode: 'CLUE',
      positionCode: 'CLUE_SUPERVISOR',
      roleCode: 'CLUE_MANAGER',
      status: 'ACTIVE',
      ownedDataCount: 23,
      canLogin: 1
    },
    {
      id: 3,
      accountName: 'online_cs',
      userName: '在线客服A',
      departmentCode: 'CLUE',
      positionCode: 'ONLINE_CS',
      roleCode: 'ONLINE_CUSTOMER_SERVICE',
      status: 'ACTIVE',
      ownedDataCount: 15,
      canLogin: 1
    },
    {
      id: 4,
      accountName: 'store_service',
      userName: '门店服务A',
      departmentCode: 'STORE',
      positionCode: 'STORE_SERVICE',
      roleCode: 'STORE_SERVICE',
      status: 'ACTIVE',
      ownedDataCount: 8,
      canLogin: 1
    },
    {
      id: 5,
      accountName: 'finance',
      userName: '财务专员',
      departmentCode: 'FINANCE',
      positionCode: 'FINANCE_SPECIALIST',
      roleCode: 'FINANCE',
      status: 'ACTIVE',
      ownedDataCount: 4,
      canLogin: 1
    },
    {
      id: 6,
      accountName: 'private_domain',
      userName: '私域客服A',
      departmentCode: 'PRIVATE_DOMAIN',
      positionCode: 'PRIVATE_DOMAIN_CS',
      roleCode: 'PRIVATE_DOMAIN_SERVICE',
      status: 'ACTIVE',
      ownedDataCount: 12,
      canLogin: 1
    }
  ],
  menuConfigs: [
    { id: 1, menuGroup: '客资中心', menuName: '客资列表', routePath: '/clues', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 2, menuGroup: '客资管理', menuName: '自动分配', routePath: '/clue-management/auto-assign', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 3, menuGroup: '客资管理', menuName: '值班客服', routePath: '/clue-management/duty-cs', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 4, menuGroup: '门店服务', menuName: '订单列表', routePath: '/store-service/orders', roleCodes: ['ADMIN', 'STORE_SERVICE'], moduleCode: 'ORDER', isEnabled: 1 },
    { id: 5, menuGroup: '系统管理', menuName: '部门管理', routePath: '/system/departments', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 6, menuGroup: '系统管理', menuName: '员工管理', routePath: '/system/employees', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 7, menuGroup: '系统管理', menuName: '岗位管理', routePath: '/system/positions', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 8, menuGroup: '系统管理', menuName: '角色管理', routePath: '/system/roles', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 9, menuGroup: '系统设置', menuName: '菜单管理', routePath: '/settings/menu', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 10, menuGroup: '系统设置', menuName: '三方接口', routePath: '/settings/integration/third-party', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 11, menuGroup: '系统设置', menuName: '回调接口', routePath: '/settings/integration/callback', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 12, menuGroup: '系统设置', menuName: '任务调度', routePath: '/settings/integration/jobs', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 13, menuGroup: '系统设置', menuName: '对外接口', routePath: '/settings/integration/public-api', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 14, menuGroup: '系统设置', menuName: '字典管理', routePath: '/settings/dictionaries', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 15, menuGroup: '系统设置', menuName: '参数管理', routePath: '/settings/parameters', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 16, menuGroup: '私域客服', menuName: '企业微信', routePath: '/private-domain/wecom', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 17, menuGroup: '私域客服', menuName: '活码配置', routePath: '/private-domain/live-code', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 }
  ],
  thirdPartyApis: [
    {
      id: 1,
      apiName: '客资中心抖音拉取',
      moduleCode: 'CLUE',
      baseUrl: 'https://open.douyin.example/api/v1/leads',
      method: 'GET',
      authType: 'Bearer Token',
      enabled: 1,
      syncMode: '增量同步',
      scheduleJobCode: 'DOUYIN_CLUE_INCREMENTAL'
    }
  ],
  callbackApis: [
    {
      id: 1,
      callbackName: '客资回流状态回调',
      callbackUrl: 'https://seedcrm.example.com/api/callback/clue-status',
      signatureMode: 'HMAC-SHA256',
      enabled: 1,
      remark: '同步三方客资处理状态'
    }
  ],
  publicApis: [
    {
      id: 1,
      apiName: '门店订单联合查询',
      sourceTable: 'order + customer + plan_order',
      outputFields: 'orderNo, customerName, amount, statusLabel',
      authMode: '签名 + 令牌',
      rateLimit: '60 次/分钟',
      cachePolicy: '30 秒缓存',
      enabled: 1
    }
  ],
  dictionaries: [
    { id: 1, dictType: 'order_status', itemCode: 'APPOINTMENT', itemLabel: '已预约', sortOrder: 10, isEnabled: 1 },
    { id: 2, dictType: 'order_status', itemCode: 'COMPLETED', itemLabel: '已完成', sortOrder: 20, isEnabled: 1 },
    { id: 3, dictType: 'clue_channel', itemCode: 'DOUYIN', itemLabel: '抖音', sortOrder: 10, isEnabled: 1 },
    { id: 4, dictType: 'clue_channel', itemCode: 'DISTRIBUTOR', itemLabel: '分销', sortOrder: 20, isEnabled: 1 }
  ],
  parameters: [
    { id: 1, paramKey: 'clue.autoPull.enabled', paramValue: 'true', category: '客资', remark: '控制客资中心是否启用自动拉取' },
    { id: 2, paramKey: 'order.confirmation.readonlyWhenCompleted', paramValue: 'true', category: '订单', remark: '已完成订单确认单进入只读查看' },
    { id: 3, paramKey: 'system.defaultStoreId', paramValue: '10', category: '系统', remark: '默认门店 ID' }
  ],
  wecomContacts: [
    { id: 1, contactName: '企微客服A', externalUserId: 'wx_external_a', scene: '术前咨询', isEnabled: 1 },
    { id: 2, contactName: '企微客服B', externalUserId: 'wx_external_b', scene: '术后回访', isEnabled: 1 }
  ],
  wecomRules: [
    { id: 1, ruleName: '预约后 1 小时触达', triggerScene: 'APPOINTMENT', template: '您好，已为您登记到店预约，如需改期请及时联系。', isEnabled: 1 },
    { id: 2, ruleName: '服务完成后回访', triggerScene: 'COMPLETED', template: '您好，感谢到店服务，欢迎在企业微信继续咨询恢复与复诊安排。', isEnabled: 1 }
  ]
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function mergeCollectionByKey(items, defaults, resolveKey) {
  const currentItems = Array.isArray(items) ? items : []
  const defaultItems = Array.isArray(defaults) ? defaults : []
  const existingKeys = new Set(currentItems.map((item) => resolveKey(item)))

  return [
    ...currentItems,
    ...defaultItems.filter((item) => !existingKeys.has(resolveKey(item)))
  ]
}

function migrateSystemConsoleState(state) {
  const defaults = clone(DEFAULT_STATE)
  const nextState = {
    ...defaults,
    ...state
  }

  nextState.menuConfigs = mergeCollectionByKey(
    state?.menuConfigs,
    defaults.menuConfigs,
    (item) => item?.routePath || item?.id
  )

  if (!Array.isArray(nextState.wecomLiveCodeConfigs)) {
    nextState.wecomLiveCodeConfigs = []
  }

  return nextState
}

export function loadSystemConsoleState() {
  if (typeof window === 'undefined') {
    return migrateSystemConsoleState(DEFAULT_STATE)
  }
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    const seeded = migrateSystemConsoleState(DEFAULT_STATE)
    saveSystemConsoleState(seeded)
    return seeded
  }

  try {
    const parsed = JSON.parse(raw)
    const migrated = migrateSystemConsoleState(parsed)
    if (JSON.stringify(migrated) !== JSON.stringify(parsed)) {
      saveSystemConsoleState(migrated)
    }
    return migrated
  } catch {
    const seeded = migrateSystemConsoleState(DEFAULT_STATE)
    saveSystemConsoleState(seeded)
    return seeded
  }
}

export function saveSystemConsoleState(state) {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

export function nextSystemId(items = []) {
  return items.reduce((max, item) => Math.max(max, Number(item?.id || 0)), 0) + 1
}

export function getDepartmentName(departments, departmentCode) {
  return departments.find((item) => item.departmentCode === departmentCode)?.departmentName || '--'
}

export function getPositionName(positions, positionCode) {
  return positions.find((item) => item.positionCode === positionCode)?.positionName || '--'
}

export function getManagedDepartmentCodes(roleCode) {
  const normalized = String(roleCode || '').trim().toUpperCase()
  if (normalized === 'ADMIN') {
    return null
  }
  if (normalized === 'CLUE_MANAGER') {
    return ['CLUE']
  }
  return []
}

export function filterEmployeesByRole(state, roleCode) {
  const managedDepartmentCodes = getManagedDepartmentCodes(roleCode)
  if (managedDepartmentCodes === null) {
    return state.employees
  }
  return state.employees.filter((item) => managedDepartmentCodes.includes(item.departmentCode))
}
