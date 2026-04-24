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
      remark: '负责客资接入、分配、预约与转化'
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
      remark: '负责客资首轮响应与预约跟进'
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
      moduleCodes: ['CLUE', 'ORDER', 'SYSTEM'],
      isEnabled: 1,
      remark: '管理客资中心、顾客排档、门店档期与本部门人员'
    },
    {
      id: 3,
      roleCode: 'ONLINE_CUSTOMER_SERVICE',
      roleName: '在线客服',
      dataScope: 'TEAM',
      moduleCodes: ['CLUE', 'ORDER'],
      isEnabled: 1,
      remark: '处理分配给自己的客资与预约'
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
      ownedDataCount: 18,
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
      ownedDataCount: 12,
      canLogin: 1
    },
    {
      id: 4,
      accountName: 'online_cs_b',
      userName: '在线客服B',
      departmentCode: 'CLUE',
      positionCode: 'ONLINE_CS',
      roleCode: 'ONLINE_CUSTOMER_SERVICE',
      status: 'ACTIVE',
      ownedDataCount: 9,
      canLogin: 1
    },
    {
      id: 5,
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
      id: 6,
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
      id: 7,
      accountName: 'private_domain',
      userName: '私域客服A',
      departmentCode: 'PRIVATE_DOMAIN',
      positionCode: 'PRIVATE_DOMAIN_CS',
      roleCode: 'PRIVATE_DOMAIN_SERVICE',
      status: 'ACTIVE',
      ownedDataCount: 12,
      canLogin: 1
    },
    {
      id: 8,
      accountName: 'private_domain_b',
      userName: '私域客服B',
      departmentCode: 'PRIVATE_DOMAIN',
      positionCode: 'PRIVATE_DOMAIN_CS',
      roleCode: 'PRIVATE_DOMAIN_SERVICE',
      status: 'ACTIVE',
      ownedDataCount: 7,
      canLogin: 1
    }
  ],
  storeScheduleConfigs: [
    {
      id: 1,
      storeName: '静安门店',
      morningStart: '09:00',
      morningEnd: '12:00',
      afternoonStart: '13:30',
      afternoonEnd: '18:00',
      slotHours: 1.5,
      remark: '医美咨询与基础皮肤项目排档'
    },
    {
      id: 2,
      storeName: '浦东门店',
      morningStart: '10:00',
      morningEnd: '13:00',
      afternoonStart: '14:00',
      afternoonEnd: '19:00',
      slotHours: 2,
      remark: '植发与术前面诊为主'
    },
    {
      id: 3,
      storeName: '徐汇门店',
      morningStart: '09:30',
      morningEnd: '12:30',
      afternoonStart: '13:30',
      afternoonEnd: '18:30',
      slotHours: 1,
      remark: '团购客资集中承接'
    }
  ],
  clueConsoleProfiles: [],
  menuConfigs: [
    { id: 1, menuGroup: '客资中心', menuName: '客资列表', routePath: '/clues', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 2, menuGroup: '客资中心', menuName: '顾客排档', routePath: '/clues/scheduling', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'], moduleCode: 'ORDER', isEnabled: 1 },
    { id: 26, menuGroup: '客资中心', menuName: '门店档期', routePath: '/clue-management/store-schedules', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 3, menuGroup: '客资中心 / 客资管理', menuName: '自动分配', routePath: '/clue-management/auto-assign', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 4, menuGroup: '客资中心 / 客资管理', menuName: '值班客服', routePath: '/clue-management/duty-cs', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 5, menuGroup: '门店服务', menuName: '订单列表', routePath: '/store-service/orders', roleCodes: ['ADMIN', 'STORE_SERVICE'], moduleCode: 'ORDER', isEnabled: 1 },
    { id: 6, menuGroup: '私域客服', menuName: '企业微信', routePath: '/private-domain/wecom', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 7, menuGroup: '私域客服', menuName: '活码配置', routePath: '/private-domain/live-code', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 8, menuGroup: '私域客服', menuName: '客户画像', routePath: '/private-domain/customer-profile', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 9, menuGroup: '私域客服', menuName: '朋友圈定时群发', routePath: '/private-domain/moments', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 10, menuGroup: '私域客服', menuName: '便签管理', routePath: '/private-domain/tags', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 11, menuGroup: '财务管理', menuName: '财务看板', routePath: '/finance', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'FINANCE', isEnabled: 1 },
    { id: 12, menuGroup: '财务管理', menuName: '薪酬中心', routePath: '/finance/salary-center', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 13, menuGroup: '财务管理 / 薪酬配置', menuName: '薪酬角色', routePath: '/finance/salary-config/roles', roleCodes: ['ADMIN'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 14, menuGroup: '财务管理 / 薪酬配置', menuName: '薪酬档位', routePath: '/finance/salary-config/grades', roleCodes: ['ADMIN'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 15, menuGroup: '系统管理', menuName: '部门管理', routePath: '/system/departments', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 16, menuGroup: '系统管理', menuName: '员工管理', routePath: '/system/employees', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 17, menuGroup: '系统管理', menuName: '岗位管理', routePath: '/system/positions', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 18, menuGroup: '系统管理', menuName: '角色管理', routePath: '/system/roles', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 19, menuGroup: '系统设置', menuName: '菜单管理', routePath: '/settings/menu', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 20, menuGroup: '系统设置 / 调度中心', menuName: '三方接口', routePath: '/settings/integration/third-party', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 21, menuGroup: '系统设置 / 调度中心', menuName: '回调接口', routePath: '/settings/integration/callback', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 22, menuGroup: '系统设置 / 调度中心', menuName: '任务调度', routePath: '/settings/integration/jobs', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 23, menuGroup: '系统设置 / 调度中心', menuName: '对外接口', routePath: '/settings/integration/public-api', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 24, menuGroup: '系统设置', menuName: '字典管理', routePath: '/settings/dictionaries', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 25, menuGroup: '系统设置', menuName: '参数管理', routePath: '/settings/parameters', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 }
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
    },
    {
      id: 2,
      apiName: '客资中心表单拉取',
      moduleCode: 'CLUE',
      baseUrl: 'https://marketing.example.com/open/forms/leads',
      method: 'GET',
      authType: 'App Secret',
      enabled: 1,
      syncMode: '增量同步',
      scheduleJobCode: 'FORM_CLUE_INCREMENTAL'
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
    { id: 4, dictType: 'clue_channel', itemCode: 'DISTRIBUTOR', itemLabel: '分销', sortOrder: 20, isEnabled: 1 },
    { id: 5, dictType: 'product_source_type', itemCode: 'GROUP_BUY', itemLabel: '团购', sortOrder: 10, isEnabled: 1 },
    { id: 6, dictType: 'product_source_type', itemCode: 'FORM', itemLabel: '表单', sortOrder: 20, isEnabled: 1 }
  ],
  parameters: [
    { id: 1, paramKey: 'clue.autoPull.enabled', paramValue: 'true', category: '客资', remark: '控制客资中心是否启用自动拉取' },
    { id: 2, paramKey: 'order.confirmation.readonlyWhenCompleted', paramValue: 'true', category: '订单', remark: '已完成订单确认单进入只读查看' },
    { id: 3, paramKey: 'system.defaultStoreId', paramValue: '10', category: '系统', remark: '默认门店 ID' },
    { id: 4, paramKey: 'wecom.customerProfile.enabled', paramValue: 'true', category: '私域客服', remark: '控制客服画像是否在企业微信展示' },
    { id: 5, paramKey: 'wecom.moments.massSend.enabled', paramValue: 'true', category: '私域客服', remark: '控制朋友圈定时群发功能开关' },
    { id: 6, paramKey: 'wecom.tag.sync.enabled', paramValue: 'true', category: '私域客服', remark: '控制企微标签同步开关' }
  ],
  wecomContacts: [
    { id: 1, contactName: '企微客服A', externalUserId: 'wx_external_a', scene: '术前咨询', isEnabled: 1 },
    { id: 2, contactName: '企微客服B', externalUserId: 'wx_external_b', scene: '术后回访', isEnabled: 1 }
  ],
  wecomRules: [
    { id: 1, ruleName: '预约后 1 小时触达', triggerScene: 'APPOINTMENT', template: '您好，已为您登记到店预约，如需改期请及时联系。', isEnabled: 1 },
    { id: 2, ruleName: '服务完成后回访', triggerScene: 'COMPLETED', template: '您好，感谢到店服务，欢迎在企业微信继续咨询恢复与复诊安排。', isEnabled: 1 }
  ],
  wecomLiveCodeConfigs: [],
  wecomCustomerPortraits: [
    {
      id: 1,
      customerName: '王女士',
      wecomNickname: '王女士-静安店',
      primaryDemand: '皮肤管理',
      tags: ['高意向', '团购已付款', '可私聊'],
      lastContactAt: '2026-04-22 14:20:00',
      isEnabled: 1
    },
    {
      id: 2,
      customerName: '李先生',
      wecomNickname: '李先生-浦东店',
      primaryDemand: '植发咨询',
      tags: ['表单留资', '待预约'],
      lastContactAt: '2026-04-23 19:10:00',
      isEnabled: 1
    }
  ],
  wecomMomentsCampaigns: [
    {
      id: 1,
      campaignName: '五一活动预热',
      contentSummary: '发布五一门店福利和预约提醒',
      scheduleTime: '2026-04-28 10:00:00',
      targetScope: '全部好友',
      status: 'ENABLED',
      createdBy: '私域客服A'
    },
    {
      id: 2,
      campaignName: '术后关怀提醒',
      contentSummary: '对已到店客户分组群发术后注意事项',
      scheduleTime: '2026-04-29 18:30:00',
      targetScope: '术后客户标签',
      status: 'DRAFT',
      createdBy: '私域客服B'
    }
  ],
  wecomTagConfigs: [
    {
      id: 1,
      tagName: '高意向',
      tagCode: 'HIGH_INTENT',
      customerCount: 38,
      newToday: 6,
      syncStatus: 'SUCCESS',
      createdAt: '2026-04-18 11:00:00'
    },
    {
      id: 2,
      tagName: '团购已付款',
      tagCode: 'GROUP_BUY_PAID',
      customerCount: 21,
      newToday: 3,
      syncStatus: 'SUCCESS',
      createdAt: '2026-04-19 09:30:00'
    },
    {
      id: 3,
      tagName: '表单待预约',
      tagCode: 'FORM_WAIT_APPOINTMENT',
      customerCount: 17,
      newToday: 4,
      syncStatus: 'PENDING',
      createdAt: '2026-04-20 15:40:00'
    }
  ],
  salaryRoles: [
    {
      id: 1,
      roleName: '普通客服',
      roleCode: 'NORMAL_CS',
      employeeIds: [3, 4],
      isEnabled: 1,
      remark: '参与个人档位计算'
    },
    {
      id: 2,
      roleName: '资深客服',
      roleCode: 'SENIOR_CS',
      employeeIds: [2, 7],
      isEnabled: 1,
      remark: '参与高阶个人档位计算'
    },
    {
      id: 3,
      roleName: '组长',
      roleCode: 'LEADER',
      employeeIds: [2],
      isEnabled: 1,
      remark: '参与团队奖计算'
    }
  ],
  salaryGrades: [
    {
      id: 1,
      category: 'INDIVIDUAL',
      gradeName: '达标档',
      metricLabel: '到店率',
      startNode: '已付款',
      endNode: '已到店',
      targetRate: '60%',
      targetPeople: '120人',
      rewardAmount: '120元/人',
      isEnabled: 1
    },
    {
      id: 2,
      category: 'INDIVIDUAL',
      gradeName: '优秀档',
      metricLabel: '有效到店率',
      startNode: '已付款',
      endNode: '已完成',
      targetRate: '72%',
      targetPeople: '160人',
      rewardAmount: '160元/人',
      isEnabled: 1
    },
    {
      id: 3,
      category: 'INDIVIDUAL',
      gradeName: '冠军档',
      metricLabel: '到店率',
      startNode: '已付款',
      endNode: '已到店',
      targetRate: '82%',
      targetPeople: '220人',
      rewardAmount: '220元/人',
      isEnabled: 1
    },
    {
      id: 4,
      category: 'INDIVIDUAL',
      gradeName: '爆发档',
      metricLabel: '有效到店率',
      startNode: '已付款',
      endNode: '已完成',
      targetRate: '90%',
      targetPeople: '300人',
      rewardAmount: '300元/人',
      isEnabled: 1
    },
    {
      id: 5,
      category: 'TEAM',
      gradeName: '团队达标档',
      metricLabel: '到店率',
      startNode: '已付款',
      endNode: '已到店',
      targetRate: '65%',
      targetPeople: '120人',
      rewardAmount: '组长团队达标奖 600元',
      isEnabled: 1
    },
    {
      id: 6,
      category: 'TEAM',
      gradeName: '团队优秀档',
      metricLabel: '有效到店率',
      startNode: '已付款',
      endNode: '已完成',
      targetRate: '78%',
      targetPeople: '160人',
      rewardAmount: '组长团队优秀奖 1200元',
      isEnabled: 1
    }
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

  const normalizedMenuConfigs = (state?.menuConfigs || []).map((item) => {
    if (item?.routePath === '/clues/payments') {
      return {
        ...item,
        menuName: '顾客排档',
        routePath: '/clues/scheduling'
      }
    }
    return item
  })
  nextState.menuConfigs = mergeCollectionByKey(normalizedMenuConfigs, defaults.menuConfigs, (item) => item?.routePath || item?.id)
  nextState.parameters = mergeCollectionByKey(state?.parameters, defaults.parameters, (item) => item?.paramKey || item?.id)
  nextState.wecomLiveCodeConfigs = mergeCollectionByKey(
    state?.wecomLiveCodeConfigs,
    defaults.wecomLiveCodeConfigs,
    (item) => item?.id
  )
  nextState.wecomCustomerPortraits = mergeCollectionByKey(
    state?.wecomCustomerPortraits,
    defaults.wecomCustomerPortraits,
    (item) => item?.id
  )
  nextState.wecomMomentsCampaigns = mergeCollectionByKey(
    state?.wecomMomentsCampaigns,
    defaults.wecomMomentsCampaigns,
    (item) => item?.id
  )
  nextState.wecomTagConfigs = mergeCollectionByKey(
    state?.wecomTagConfigs,
    defaults.wecomTagConfigs,
    (item) => item?.tagCode || item?.id
  )
  nextState.storeScheduleConfigs = mergeCollectionByKey(
    state?.storeScheduleConfigs,
    defaults.storeScheduleConfigs,
    (item) => item?.storeName || item?.id
  )
  nextState.clueConsoleProfiles = mergeCollectionByKey(
    state?.clueConsoleProfiles,
    defaults.clueConsoleProfiles,
    (item) => item?.clueId || item?.id
  )
  nextState.salaryRoles = mergeCollectionByKey(state?.salaryRoles, defaults.salaryRoles, (item) => item?.roleCode || item?.id)
  nextState.salaryGrades = mergeCollectionByKey(state?.salaryGrades, defaults.salaryGrades, (item) => item?.gradeName || item?.id)

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

export function getSystemParameterValue(state, paramKey, fallback = '') {
  const matched = (state?.parameters || []).find((item) => item.paramKey === paramKey)
  return matched?.paramValue ?? fallback
}

export function isSystemParameterEnabled(state, paramKey) {
  return String(getSystemParameterValue(state, paramKey, 'false')).trim().toLowerCase() === 'true'
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

function parseScheduleTime(value) {
  if (!value || !String(value).includes(':')) {
    return 0
  }
  const [hour, minute] = String(value).split(':').map((item) => Number(item))
  return (Number.isFinite(hour) ? hour : 0) * 60 + (Number.isFinite(minute) ? minute : 0)
}

export function getStoreScheduleConfig(state, storeName) {
  return (state?.storeScheduleConfigs || []).find((item) => item.storeName === storeName) || null
}

export function calculateStoreCapacity(config) {
  if (!config) {
    return 0
  }
  const morningMinutes = Math.max(parseScheduleTime(config.morningEnd) - parseScheduleTime(config.morningStart), 0)
  const afternoonMinutes = Math.max(parseScheduleTime(config.afternoonEnd) - parseScheduleTime(config.afternoonStart), 0)
  const slotMinutes = Number(config.slotHours || 0) * 60
  if (slotMinutes <= 0) {
    return 0
  }
  return Math.max(Math.floor((morningMinutes + afternoonMinutes) / slotMinutes), 0)
}

export function listStoreNames(state) {
  return (state?.storeScheduleConfigs || []).map((item) => item.storeName).filter(Boolean)
}
