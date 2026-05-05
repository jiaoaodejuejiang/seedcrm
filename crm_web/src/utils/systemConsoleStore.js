const STORAGE_KEY = 'seedcrm.system-console'
const SYSTEM_CONSOLE_SCHEMA_VERSION = 5
const SELF_SALARY_ROLE_CODES = [
  'ADMIN',
  'FINANCE',
  'CLUE_MANAGER',
  'ONLINE_CUSTOMER_SERVICE',
  'STORE_SERVICE',
  'STORE_MANAGER',
  'PHOTOGRAPHER',
  'MAKEUP_ARTIST',
  'PHOTO_SELECTOR',
  'PRIVATE_DOMAIN_SERVICE'
]

const DEFAULT_DOMAIN_SETTINGS = {
  systemBaseUrl: 'http://127.0.0.1:8003',
  apiBaseUrl: 'http://127.0.0.1:8004'
}

const DEFAULT_DICTIONARIES = [
  { id: 1, dictType: 'order_status', itemCode: 'APPOINTMENT', itemLabel: '已预约', sortOrder: 10, isEnabled: 1 },
  { id: 2, dictType: 'order_status', itemCode: 'COMPLETED', itemLabel: '已完成', sortOrder: 20, isEnabled: 1 },
  { id: 3, dictType: 'clue_channel', itemCode: 'DOUYIN', itemLabel: '抖音', sortOrder: 10, isEnabled: 1 },
  { id: 4, dictType: 'clue_channel', itemCode: 'DISTRIBUTOR', itemLabel: '分销', sortOrder: 20, isEnabled: 1 },
  { id: 5, dictType: 'product_source_type', itemCode: 'GROUP_BUY', itemLabel: '团购', sortOrder: 10, isEnabled: 1 },
  { id: 6, dictType: 'product_source_type', itemCode: 'FORM', itemLabel: '定金', sortOrder: 20, isEnabled: 1 },
  { id: 7, dictType: 'order_status', itemCode: 'CREATED', itemLabel: '待付款', sortOrder: 30, isEnabled: 1 },
  { id: 8, dictType: 'order_status', itemCode: 'PAID', itemLabel: '已付款', sortOrder: 40, isEnabled: 1 },
  { id: 9, dictType: 'order_status', itemCode: 'PAID_DEPOSIT', itemLabel: '已付定金', sortOrder: 50, isEnabled: 1 },
  { id: 10, dictType: 'order_status', itemCode: 'ARRIVED', itemLabel: '已到店', sortOrder: 60, isEnabled: 1 },
  { id: 11, dictType: 'order_status', itemCode: 'SERVING', itemLabel: '服务中', sortOrder: 70, isEnabled: 1 },
  { id: 12, dictType: 'order_status', itemCode: 'SERVICING', itemLabel: '服务中', sortOrder: 80, isEnabled: 1 },
  { id: 13, dictType: 'order_status', itemCode: 'USED', itemLabel: '已完成', sortOrder: 90, isEnabled: 1 },
  { id: 14, dictType: 'order_status', itemCode: 'FINISHED', itemLabel: '已完成', sortOrder: 100, isEnabled: 1 },
  { id: 15, dictType: 'order_status', itemCode: 'CANCELLED', itemLabel: '已取消', sortOrder: 110, isEnabled: 1 },
  { id: 16, dictType: 'order_status', itemCode: 'REFUNDED', itemLabel: '已退款', sortOrder: 120, isEnabled: 1 },
  { id: 17, dictType: 'order_status', itemCode: 'APPROVED', itemLabel: '已通过', sortOrder: 130, isEnabled: 1 },
  { id: 18, dictType: 'order_status', itemCode: 'PENDING', itemLabel: '待处理', sortOrder: 140, isEnabled: 1 },
  { id: 19, dictType: 'order_status', itemCode: 'INIT', itemLabel: '待确认', sortOrder: 150, isEnabled: 1 },
  { id: 20, dictType: 'order_status', itemCode: 'CONFIRMED', itemLabel: '已确认', sortOrder: 160, isEnabled: 1 },
  { id: 21, dictType: 'order_status', itemCode: 'PAID_OUT', itemLabel: '已线下结清', sortOrder: 170, isEnabled: 1 },
  { id: 22, dictType: 'order_status', itemCode: 'DRAFT', itemLabel: '草稿', sortOrder: 180, isEnabled: 1 },
  { id: 23, dictType: 'order_status', itemCode: 'ENABLED', itemLabel: '启用', sortOrder: 190, isEnabled: 1 },
  { id: 24, dictType: 'order_status', itemCode: 'DISABLED', itemLabel: '停用', sortOrder: 200, isEnabled: 1 },
  { id: 25, dictType: 'order_status', itemCode: 'SUCCESS', itemLabel: '成功', sortOrder: 210, isEnabled: 1 },
  { id: 26, dictType: 'order_status', itemCode: 'FAIL', itemLabel: '失败', sortOrder: 220, isEnabled: 1 },
  { id: 27, dictType: 'order_status', itemCode: 'FAILED', itemLabel: '失败', sortOrder: 230, isEnabled: 1 },
  { id: 28, dictType: 'clue_status', itemCode: 'NEW', itemLabel: '新客资', sortOrder: 10, isEnabled: 1 },
  { id: 29, dictType: 'clue_status', itemCode: 'ASSIGNED', itemLabel: '已分配', sortOrder: 20, isEnabled: 1 },
  { id: 30, dictType: 'clue_status', itemCode: 'FOLLOWING', itemLabel: '跟进中', sortOrder: 30, isEnabled: 1 },
  { id: 31, dictType: 'clue_status', itemCode: 'CONVERTED', itemLabel: '已转化', sortOrder: 40, isEnabled: 1 },
  { id: 32, dictType: 'call_status', itemCode: 'NOT_CALLED', itemLabel: '未通话', sortOrder: 10, isEnabled: 1 },
  { id: 33, dictType: 'call_status', itemCode: 'CONNECTED', itemLabel: '已接通', sortOrder: 20, isEnabled: 1 },
  { id: 34, dictType: 'call_status', itemCode: 'MISSED', itemLabel: '未接通', sortOrder: 30, isEnabled: 1 },
  { id: 35, dictType: 'call_status', itemCode: 'CALLBACK', itemLabel: '待回拨', sortOrder: 40, isEnabled: 1 },
  { id: 36, dictType: 'call_status', itemCode: 'INVALID', itemLabel: '无效号码', sortOrder: 50, isEnabled: 1 },
  { id: 37, dictType: 'lead_stage', itemCode: 'NEW', itemLabel: '新线索', sortOrder: 10, isEnabled: 1 },
  { id: 38, dictType: 'lead_stage', itemCode: 'INTENT', itemLabel: '有意向', sortOrder: 20, isEnabled: 1 },
  { id: 39, dictType: 'lead_stage', itemCode: 'ARRIVED', itemLabel: '到店', sortOrder: 30, isEnabled: 1 },
  { id: 40, dictType: 'lead_stage', itemCode: 'DEAL', itemLabel: '成交', sortOrder: 40, isEnabled: 1 },
  { id: 41, dictType: 'lead_stage', itemCode: 'CALLBACK_PENDING', itemLabel: '待再次沟通', sortOrder: 50, isEnabled: 1 },
  { id: 42, dictType: 'lead_stage', itemCode: 'WECHAT_ADDED', itemLabel: '已加微信', sortOrder: 60, isEnabled: 1 },
  { id: 43, dictType: 'lead_stage', itemCode: 'DEPOSIT_PAID', itemLabel: '预付定金', sortOrder: 70, isEnabled: 1 },
  { id: 44, dictType: 'lead_stage', itemCode: 'INVALID', itemLabel: '无效', sortOrder: 80, isEnabled: 1 },
  { id: 45, dictType: 'lead_stage', itemCode: 'CONTACTED', itemLabel: '已联系', sortOrder: 90, isEnabled: 1 },
  { id: 46, dictType: 'lead_stage', itemCode: 'APPOINTMENT_PENDING', itemLabel: '待预约', sortOrder: 100, isEnabled: 1 },
  { id: 47, dictType: 'lead_stage', itemCode: 'APPOINTED', itemLabel: '已预约', sortOrder: 110, isEnabled: 1 },
  { id: 48, dictType: 'lead_stage', itemCode: 'CLOSED', itemLabel: '已成交', sortOrder: 120, isEnabled: 1 },
  { id: 49, dictType: 'plan_order_status', itemCode: 'ARRIVED', itemLabel: '已到店', sortOrder: 10, isEnabled: 1 },
  { id: 50, dictType: 'plan_order_status', itemCode: 'SERVICING', itemLabel: '服务中', sortOrder: 20, isEnabled: 1 },
  { id: 51, dictType: 'plan_order_status', itemCode: 'FINISHED', itemLabel: '已完成', sortOrder: 30, isEnabled: 1 },
  { id: 52, dictType: 'scheduler_status', itemCode: 'ACTIVE', itemLabel: '启用', sortOrder: 10, isEnabled: 1 },
  { id: 53, dictType: 'scheduler_status', itemCode: 'ENABLED', itemLabel: '启用', sortOrder: 20, isEnabled: 1 },
  { id: 54, dictType: 'scheduler_status', itemCode: 'INACTIVE', itemLabel: '停用', sortOrder: 30, isEnabled: 1 },
  { id: 55, dictType: 'scheduler_status', itemCode: 'DISABLED', itemLabel: '停用', sortOrder: 40, isEnabled: 1 },
  { id: 56, dictType: 'scheduler_status', itemCode: 'QUEUED', itemLabel: '排队中', sortOrder: 50, isEnabled: 1 },
  { id: 57, dictType: 'scheduler_status', itemCode: 'RUNNING', itemLabel: '执行中', sortOrder: 60, isEnabled: 1 },
  { id: 58, dictType: 'scheduler_status', itemCode: 'SUCCESS', itemLabel: '成功', sortOrder: 70, isEnabled: 1 },
  { id: 59, dictType: 'scheduler_status', itemCode: 'FAIL', itemLabel: '失败', sortOrder: 80, isEnabled: 1 },
  { id: 60, dictType: 'scheduler_status', itemCode: 'FAILED', itemLabel: '失败', sortOrder: 90, isEnabled: 1 },
  { id: 61, dictType: 'verification_status', itemCode: 'VERIFIED', itemLabel: '已核销', sortOrder: 10, isEnabled: 1 },
  { id: 62, dictType: 'verification_status', itemCode: 'UNVERIFIED', itemLabel: '待核销', sortOrder: 20, isEnabled: 1 },
  { id: 63, dictType: 'settlement_mode', itemCode: 'WITHDRAW_AUDIT', itemLabel: '线下结清需审核', sortOrder: 10, isEnabled: 1 },
  { id: 64, dictType: 'settlement_mode', itemCode: 'WITHDRAW_DIRECT', itemLabel: '外部处理直接登记', sortOrder: 20, isEnabled: 1 },
  { id: 65, dictType: 'settlement_mode', itemCode: 'LEDGER_ONLY', itemLabel: '只记账', sortOrder: 30, isEnabled: 1 },
  { id: 66, dictType: 'role_code', itemCode: 'ADMIN', itemLabel: '管理员', sortOrder: 10, isEnabled: 1 },
  { id: 67, dictType: 'role_code', itemCode: 'ONLINE_CUSTOMER_SERVICE', itemLabel: '在线客服', sortOrder: 20, isEnabled: 1 },
  { id: 68, dictType: 'role_code', itemCode: 'CLUE_MANAGER', itemLabel: '客资主管', sortOrder: 30, isEnabled: 1 },
  { id: 69, dictType: 'role_code', itemCode: 'STORE_SERVICE', itemLabel: '门店服务', sortOrder: 40, isEnabled: 1 },
  { id: 70, dictType: 'role_code', itemCode: 'STORE_MANAGER', itemLabel: '店长', sortOrder: 50, isEnabled: 1 },
  { id: 71, dictType: 'role_code', itemCode: 'PHOTOGRAPHER', itemLabel: '摄影', sortOrder: 60, isEnabled: 1 },
  { id: 72, dictType: 'role_code', itemCode: 'MAKEUP_ARTIST', itemLabel: '化妆师', sortOrder: 70, isEnabled: 1 },
  { id: 73, dictType: 'role_code', itemCode: 'PHOTO_SELECTOR', itemLabel: '选片负责人', sortOrder: 80, isEnabled: 1 },
  { id: 74, dictType: 'role_code', itemCode: 'FINANCE', itemLabel: '财务', sortOrder: 90, isEnabled: 1 },
  { id: 75, dictType: 'role_code', itemCode: 'PRIVATE_DOMAIN_SERVICE', itemLabel: '私域客服', sortOrder: 100, isEnabled: 1 },
  { id: 76, dictType: 'role_code', itemCode: 'NORMAL_CS', itemLabel: '普通客服', sortOrder: 110, isEnabled: 1 },
  { id: 77, dictType: 'role_code', itemCode: 'SENIOR_CS', itemLabel: '资深客服', sortOrder: 120, isEnabled: 1 },
  { id: 78, dictType: 'role_code', itemCode: 'LEADER', itemLabel: '组长', sortOrder: 130, isEnabled: 1 },
  { id: 79, dictType: 'appointment_reason_type', itemCode: 'CUSTOMER_REQUEST', itemLabel: '客户主动预约', sortOrder: 10, isEnabled: 1 },
  { id: 80, dictType: 'appointment_reason_type', itemCode: 'RESCHEDULE', itemLabel: '客户改约', sortOrder: 20, isEnabled: 1 },
  { id: 81, dictType: 'appointment_reason_type', itemCode: 'STORE_ADJUST', itemLabel: '门店调整', sortOrder: 30, isEnabled: 1 },
  { id: 82, dictType: 'appointment_reason_type', itemCode: 'TRAFFIC_DELAY', itemLabel: '到店延迟', sortOrder: 40, isEnabled: 1 },
  { id: 83, dictType: 'appointment_reason_type', itemCode: 'CUSTOMER_CANCEL', itemLabel: '客户取消', sortOrder: 50, isEnabled: 1 }
]

const DEFAULT_STATE = {
  permissionBaselineVersion: SYSTEM_CONSOLE_SCHEMA_VERSION,
  domainSettings: DEFAULT_DOMAIN_SETTINGS,
  departments: [
    { id: 1, departmentCode: 'HQ', departmentName: '总部管理', parentCode: '', managerRoleCode: 'ADMIN', dataScopeRule: '可查看全部有效数据并进行系统配置', isEnabled: 1, remark: '系统管理员负责整体系统治理' },
    { id: 2, departmentCode: 'CLUE', departmentName: '客资中心', parentCode: 'HQ', managerRoleCode: 'CLUE_MANAGER', dataScopeRule: '仅查看并分配客资中心有效数据', isEnabled: 1, remark: '负责客资接入、分配、预约与转化' },
    { id: 3, departmentCode: 'STORE', departmentName: '门店服务', parentCode: 'HQ', managerRoleCode: 'STORE_MANAGER', dataScopeRule: '仅处理门店范围内订单与服务单', isEnabled: 1, remark: '负责客户到店确认和服务履约' },
    { id: 4, departmentCode: 'FINANCE', departmentName: '财务中心', parentCode: 'HQ', managerRoleCode: 'FINANCE', dataScopeRule: '查看财务、薪酬和结算数据', isEnabled: 1, remark: '负责结算、线下结清登记与财务复核' },
    { id: 5, departmentCode: 'PRIVATE_DOMAIN', departmentName: '私域客服', parentCode: 'HQ', managerRoleCode: 'PRIVATE_DOMAIN_SERVICE', dataScopeRule: '查看绑定客户的企微与触达记录', isEnabled: 1, remark: '负责企业微信触达与私域维护' }
  ],
  positions: [
    { id: 1, positionCode: 'SYSTEM_ADMIN', positionName: '系统管理员岗', departmentCode: 'HQ', isEnabled: 1, remark: '管理菜单、角色、字典和参数' },
    { id: 2, positionCode: 'CLUE_SUPERVISOR', positionName: '客资主管岗', departmentCode: 'CLUE', isEnabled: 1, remark: '维护自动分配和值班客服' },
    { id: 3, positionCode: 'ONLINE_CS', positionName: '在线客服岗', departmentCode: 'CLUE', isEnabled: 1, remark: '负责客资首轮响应与预约跟进' },
    { id: 4, positionCode: 'STORE_SERVICE', positionName: '门店服务岗', departmentCode: 'STORE', isEnabled: 1, remark: '负责核销和服务履约' },
    { id: 5, positionCode: 'STORE_MANAGER', positionName: '店长岗', departmentCode: 'STORE', isEnabled: 1, remark: '负责门店排班、模板与账号管理' },
    { id: 6, positionCode: 'PHOTOGRAPHER', positionName: '摄影岗', departmentCode: 'STORE', isEnabled: 1, remark: '负责摄影服务' },
    { id: 7, positionCode: 'MAKEUP_ARTIST', positionName: '化妆师岗', departmentCode: 'STORE', isEnabled: 1, remark: '负责化妆与造型服务' },
    { id: 8, positionCode: 'PHOTO_SELECTOR', positionName: '选片负责人岗', departmentCode: 'STORE', isEnabled: 1, remark: '负责选片与确认服务' },
    { id: 9, positionCode: 'FINANCE_SPECIALIST', positionName: '财务专员岗', departmentCode: 'FINANCE', isEnabled: 1, remark: '负责结算和线下处理登记' },
    { id: 10, positionCode: 'PRIVATE_DOMAIN_CS', positionName: '私域客服岗', departmentCode: 'PRIVATE_DOMAIN', isEnabled: 1, remark: '负责企微客户触达' }
  ],
  roles: [
    { id: 1, roleCode: 'ADMIN', roleName: '管理员', dataScope: 'ALL', moduleCodes: ['CLUE', 'ORDER', 'PLANORDER', 'SALARY', 'FINANCE', 'SYSTEM', 'SETTING', 'WECOM'], isEnabled: 1, remark: '拥有系统全量菜单和配置权限' },
    { id: 2, roleCode: 'CLUE_MANAGER', roleName: '客资主管', dataScope: 'ALL', moduleCodes: ['CLUE', 'ORDER', 'SYSTEM', 'SALARY'], isEnabled: 1, remark: '管理客资中心、顾客排档和客资配置' },
    { id: 3, roleCode: 'ONLINE_CUSTOMER_SERVICE', roleName: '在线客服', dataScope: 'TEAM', moduleCodes: ['CLUE', 'ORDER', 'SALARY'], isEnabled: 1, remark: '处理已分配客资与排档跟进' },
    { id: 4, roleCode: 'STORE_SERVICE', roleName: '门店服务', dataScope: 'STORE', moduleCodes: ['ORDER', 'PLANORDER', 'SALARY'], isEnabled: 1, remark: '查看订单、核销和服务单' },
    { id: 5, roleCode: 'STORE_MANAGER', roleName: '店长', dataScope: 'STORE', moduleCodes: ['ORDER', 'PLANORDER', 'SYSTEM', 'SALARY'], isEnabled: 1, remark: '管理门店服务、人员与角色' },
    { id: 6, roleCode: 'PHOTOGRAPHER', roleName: '摄影', dataScope: 'STORE', moduleCodes: ['ORDER', 'PLANORDER', 'SALARY'], isEnabled: 1, remark: '参与门店服务履约' },
    { id: 7, roleCode: 'MAKEUP_ARTIST', roleName: '化妆师', dataScope: 'STORE', moduleCodes: ['ORDER', 'PLANORDER', 'SALARY'], isEnabled: 1, remark: '参与门店服务履约' },
    { id: 8, roleCode: 'PHOTO_SELECTOR', roleName: '选片负责人', dataScope: 'STORE', moduleCodes: ['ORDER', 'PLANORDER', 'SALARY'], isEnabled: 1, remark: '参与门店服务履约' },
    { id: 9, roleCode: 'FINANCE', roleName: '财务', dataScope: 'ALL', moduleCodes: ['ORDER', 'SALARY', 'FINANCE'], isEnabled: 1, remark: '查看结算与财务数据' },
    { id: 10, roleCode: 'PRIVATE_DOMAIN_SERVICE', roleName: '私域客服', dataScope: 'SELF', moduleCodes: ['WECOM', 'SALARY'], isEnabled: 1, remark: '负责企业微信客户触达' }
  ],
  employees: [
    { id: 1, accountName: 'admin', userName: '系统管理员', departmentCode: 'HQ', positionCode: 'SYSTEM_ADMIN', roleCode: 'ADMIN', status: 'ACTIVE', ownedDataCount: 0, canLogin: 1 },
    { id: 2, accountName: 'clue_manager', userName: '客资主管', departmentCode: 'CLUE', positionCode: 'CLUE_SUPERVISOR', roleCode: 'CLUE_MANAGER', status: 'ACTIVE', ownedDataCount: 18, canLogin: 1 },
    { id: 3, accountName: 'online_cs', userName: '在线客服A', departmentCode: 'CLUE', positionCode: 'ONLINE_CS', roleCode: 'ONLINE_CUSTOMER_SERVICE', status: 'ACTIVE', ownedDataCount: 12, canLogin: 1 },
    { id: 4, accountName: 'online_cs_b', userName: '在线客服B', departmentCode: 'CLUE', positionCode: 'ONLINE_CS', roleCode: 'ONLINE_CUSTOMER_SERVICE', status: 'ACTIVE', ownedDataCount: 9, canLogin: 1 },
    { id: 5, accountName: 'store_service', userName: '门店服务A', departmentCode: 'STORE', positionCode: 'STORE_SERVICE', roleCode: 'STORE_SERVICE', status: 'ACTIVE', ownedDataCount: 8, canLogin: 1, storeName: '静安门店' },
    { id: 6, accountName: 'store_manager', userName: '静安店长', departmentCode: 'STORE', positionCode: 'STORE_MANAGER', roleCode: 'STORE_MANAGER', status: 'ACTIVE', ownedDataCount: 6, canLogin: 1, storeName: '静安门店' },
    { id: 7, accountName: 'photo_a', userName: '摄影A', departmentCode: 'STORE', positionCode: 'PHOTOGRAPHER', roleCode: 'PHOTOGRAPHER', status: 'ACTIVE', ownedDataCount: 4, canLogin: 1, storeName: '静安门店' },
    { id: 8, accountName: 'makeup_a', userName: '化妆师A', departmentCode: 'STORE', positionCode: 'MAKEUP_ARTIST', roleCode: 'MAKEUP_ARTIST', status: 'ACTIVE', ownedDataCount: 3, canLogin: 1, storeName: '静安门店' },
    { id: 9, accountName: 'selector_a', userName: '选片负责人A', departmentCode: 'STORE', positionCode: 'PHOTO_SELECTOR', roleCode: 'PHOTO_SELECTOR', status: 'ACTIVE', ownedDataCount: 5, canLogin: 1, storeName: '静安门店' },
    { id: 10, accountName: 'finance', userName: '财务专员', departmentCode: 'FINANCE', positionCode: 'FINANCE_SPECIALIST', roleCode: 'FINANCE', status: 'ACTIVE', ownedDataCount: 4, canLogin: 1 },
    { id: 11, accountName: 'private_domain', userName: '私域客服A', departmentCode: 'PRIVATE_DOMAIN', positionCode: 'PRIVATE_DOMAIN_CS', roleCode: 'PRIVATE_DOMAIN_SERVICE', status: 'ACTIVE', ownedDataCount: 12, canLogin: 1 },
    { id: 12, accountName: 'private_domain_b', userName: '私域客服B', departmentCode: 'PRIVATE_DOMAIN', positionCode: 'PRIVATE_DOMAIN_CS', roleCode: 'PRIVATE_DOMAIN_SERVICE', status: 'ACTIVE', ownedDataCount: 7, canLogin: 1 }
  ],
  storeScheduleConfigs: [
    { id: 1, storeName: '静安门店', morningStart: '09:00', morningEnd: '12:00', afternoonStart: '13:30', afternoonEnd: '18:00', slotHours: 1.5, remark: '医美咨询与基础皮肤项目排档' },
    { id: 2, storeName: '浦东门店', morningStart: '10:00', morningEnd: '13:00', afternoonStart: '14:00', afternoonEnd: '19:00', slotHours: 2, remark: '植发与术前面诊为主' },
    { id: 3, storeName: '徐汇门店', morningStart: '09:30', morningEnd: '12:30', afternoonStart: '13:30', afternoonEnd: '18:30', slotHours: 1, remark: '团购客资集中承接' }
  ],
  clueConsoleProfiles: [],
  menuConfigs: [
    { id: 1, menuGroup: '客资中心 / 线上客服工作台', menuName: '客资列表', routePath: '/clues', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 2, menuGroup: '客资中心 / 线上客服工作台', menuName: '顾客排档', routePath: '/clues/scheduling', roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'], moduleCode: 'ORDER', isEnabled: 1 },
    { id: 3, menuGroup: '客资中心 / 客资管理', menuName: '客资配置', routePath: '/clue-management/auto-assign', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 4, menuGroup: '客资中心 / 客资管理', menuName: '值班客服', routePath: '/clue-management/duty-cs', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'CLUE', isEnabled: 1 },
    { id: 5, menuGroup: '门店服务', menuName: '门店档期', routePath: '/store-service/schedules', roleCodes: ['ADMIN', 'STORE_MANAGER'], moduleCode: 'PLANORDER', isEnabled: 1 },
    { id: 6, menuGroup: '门店服务', menuName: '订单列表', routePath: '/store-service/orders', roleCodes: ['ADMIN', 'STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR'], moduleCode: 'ORDER', isEnabled: 1 },
    { id: 7, menuGroup: '门店服务', menuName: '服务单设计', routePath: '/store-service/service-design', roleCodes: ['ADMIN', 'STORE_MANAGER'], moduleCode: 'PLANORDER', isEnabled: 1 },
    { id: 8, menuGroup: '门店服务', menuName: '人员管理', routePath: '/store-service/personnel', roleCodes: ['ADMIN', 'STORE_MANAGER'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 9, menuGroup: '门店服务', menuName: '门店角色', routePath: '/store-service/roles', roleCodes: ['ADMIN', 'STORE_MANAGER'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 10, menuGroup: '系统设置 / 基础配置', menuName: '企业微信', routePath: '/settings/base/wecom', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 11, menuGroup: '私域客服', menuName: '活码配置', routePath: '/private-domain/live-code', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 12, menuGroup: '私域客服', menuName: '客户画像', routePath: '/private-domain/customer-profile', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 13, menuGroup: '私域客服', menuName: '朋友圈定时群发', routePath: '/private-domain/moments', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 14, menuGroup: '私域客服', menuName: '标签管理', routePath: '/private-domain/tags', roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'], moduleCode: 'WECOM', isEnabled: 1 },
    { id: 15, menuGroup: '财务管理', menuName: '财务看板', routePath: '/finance', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'FINANCE', isEnabled: 1 },
    { id: 16, menuGroup: '财务管理 / 薪酬中心', menuName: '我的薪酬', routePath: '/finance/salary/my', roleCodes: SELF_SALARY_ROLE_CODES, moduleCode: 'SALARY', isEnabled: 1 },
    { id: 17, menuGroup: '财务管理 / 薪酬结算', menuName: '结算单管理', routePath: '/finance/salary/settlements', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 18, menuGroup: '财务管理 / 薪酬结算', menuName: '结算配置', routePath: '/finance/salary/settlement-config', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 19, menuGroup: '财务管理 / 薪酬配置', menuName: '薪酬角色', routePath: '/finance/salary-config/roles', roleCodes: ['ADMIN'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 20, menuGroup: '财务管理 / 薪酬配置', menuName: '薪酬档位', routePath: '/finance/salary-config/grades', roleCodes: ['ADMIN'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 21, menuGroup: '财务管理 / 薪酬配置', menuName: '分销配置', routePath: '/finance/salary-config/distributor', roleCodes: ['ADMIN'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 22, menuGroup: '系统管理', menuName: '部门管理', routePath: '/system/departments', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 23, menuGroup: '系统管理', menuName: '员工管理', routePath: '/system/employees', roleCodes: ['ADMIN', 'CLUE_MANAGER'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 24, menuGroup: '系统管理', menuName: '岗位管理', routePath: '/system/positions', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 25, menuGroup: '系统管理', menuName: '角色管理', routePath: '/system/roles', roleCodes: ['ADMIN'], moduleCode: 'SYSTEM', isEnabled: 1 },
    { id: 26, menuGroup: '系统设置 / 基础配置', menuName: '域名配置', routePath: '/settings/base/domain', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 27, menuGroup: '系统设置 / 基础配置', menuName: '菜单管理', routePath: '/settings/menu', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 41, menuGroup: '系统设置 / 基础配置', menuName: '配置发布中心', routePath: '/settings/base/config-audit', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 28, menuGroup: '系统设置 / 调度中心', menuName: '抖音接口', routePath: '/settings/integration/third-party', roleCodes: ['ADMIN', 'INTEGRATION_ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 29, menuGroup: '系统设置 / 调度中心', menuName: '回调接口', routePath: '/settings/integration/callback', roleCodes: ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 30, menuGroup: '系统设置 / 调度中心', menuName: '任务调度', routePath: '/settings/integration/jobs', roleCodes: ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 31, menuGroup: '系统设置 / 调度中心', menuName: '联调工作台', routePath: '/settings/integration/debug', roleCodes: ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 32, menuGroup: '系统设置 / 调度中心', menuName: '对外接口', routePath: '/settings/integration/public-api', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 33, menuGroup: '系统设置 / 调度中心', menuName: '分销接口', routePath: '/settings/integration/distribution-api', roleCodes: ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 34, menuGroup: '系统设置 / 基础配置', menuName: '字典管理', routePath: '/settings/dictionaries', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 35, menuGroup: '系统设置 / 基础配置', menuName: '参数管理', routePath: '/settings/parameters', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 36, menuGroup: '系统设置 / 基础配置', menuName: '支付设置', routePath: '/settings/payment', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 37, menuGroup: '财务管理 / 薪酬结算', menuName: '线下结清登记', routePath: '/finance/salary/withdrawals', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 38, menuGroup: '财务管理 / 薪酬结算', menuName: '退款冲正', routePath: '/finance/salary/refund-adjustments', roleCodes: ['ADMIN', 'FINANCE'], moduleCode: 'SALARY', isEnabled: 1 },
    { id: 39, menuGroup: '系统设置 / 流程配置', menuName: '系统流程', routePath: '/settings/system-flow', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 },
    { id: 40, menuGroup: '系统设置 / 流程配置', menuName: '配置化平台', routePath: '/settings/lowcode', roleCodes: ['ADMIN'], moduleCode: 'SETTING', isEnabled: 1 }
  ],
  salaryRoles: [
    { id: 1, roleName: '普通客服', roleCode: 'NORMAL_CS', employeeIds: [3, 4], isEnabled: 1, remark: '参与个人档位计算' },
    { id: 2, roleName: '资深客服', roleCode: 'SENIOR_CS', employeeIds: [2, 7], isEnabled: 1, remark: '参与高阶个人档位计算' },
    { id: 3, roleName: '组长', roleCode: 'LEADER', employeeIds: [2, 6], isEnabled: 1, remark: '参与团队奖计算' }
  ],
  salaryGrades: [
    { id: 1, category: 'INDIVIDUAL', gradeName: '达标档', metricLabel: '到店率', startNode: '已付款', endNode: '已到店', targetRate: '60%', targetPeople: '120人', rewardAmount: '120元/人', isEnabled: 1 },
    { id: 2, category: 'INDIVIDUAL', gradeName: '优秀档', metricLabel: '有效到店率', startNode: '已付款', endNode: '已完成', targetRate: '72%', targetPeople: '160人', rewardAmount: '160元/人', isEnabled: 1 },
    { id: 3, category: 'INDIVIDUAL', gradeName: '冠军档', metricLabel: '到店率', startNode: '已付款', endNode: '已到店', targetRate: '82%', targetPeople: '220人', rewardAmount: '220元/人', isEnabled: 1 },
    { id: 4, category: 'INDIVIDUAL', gradeName: '爆发档', metricLabel: '有效到店率', startNode: '已付款', endNode: '已完成', targetRate: '90%', targetPeople: '300人', rewardAmount: '300元/人', isEnabled: 1 },
    { id: 5, category: 'TEAM', gradeName: '团队达标档', metricLabel: '到店率', startNode: '已付款', endNode: '已到店', targetRate: '65%', targetPeople: '120人', rewardAmount: '组长团队达标奖 600元', isEnabled: 1 },
    { id: 6, category: 'TEAM', gradeName: '团队优秀档', metricLabel: '有效到店率', startNode: '已付款', endNode: '已完成', targetRate: '78%', targetPeople: '160人', rewardAmount: '组长团队优秀奖 1200元', isEnabled: 1 }
  ],
  salarySettlementRules: [
    { id: 1, ruleName: '分销大额线下结清', scopeType: 'AMOUNT', roleCodes: [], amountMin: 3000, amountMax: '', settlementMode: 'WITHDRAW_AUDIT', enabled: 1, remark: '大额分销结清需财务审核登记' },
    { id: 2, ruleName: '分销小额外部处理', scopeType: 'AMOUNT', roleCodes: [], amountMin: 0, amountMax: 2999.99, settlementMode: 'WITHDRAW_DIRECT', enabled: 1, remark: '小额分销由外部平台处理后直接登记' },
    { id: 3, ruleName: '内部员工记账', scopeType: 'ROLE', roleCodes: ['NORMAL_CS', 'SENIOR_CS', 'LEADER'], amountMin: '', amountMax: '', settlementMode: 'LEDGER_ONLY', enabled: 1, remark: '内部员工只记账，不走资金划拨' }
  ],
  distributorConfigs: [
    { id: 1, configName: '团购券分销提成', productType: 'coupon', orderStage: '到店核销', commissionRate: 0.12, settlementBase: 'completed_amount', enabled: 1, remark: '团购券完成核销后按订单金额结算' },
    { id: 2, configName: '定金分销提成', productType: 'deposit', orderStage: '订单完成', commissionRate: 0.08, settlementBase: 'completed_amount', enabled: 1, remark: '定金订单完成后按完成金额结算' }
  ],
  paymentSettings: {
    wechatPay: {
      enabled: 1,
      merchantName: 'SeedCRM 演示商户',
      mchId: '',
      appId: '',
      apiV3Key: '',
      serialNo: '',
      privateKeyPem: '',
      notifyUrl: 'https://crm.example.com/pay/wechat/notify',
      notifyPath: '/pay/wechat/notify',
      refundNotifyUrl: 'https://crm.example.com/pay/wechat/refund-notify',
      refundNotifyPath: '/pay/wechat/refund-notify',
      tradeType: 'JSAPI',
      testStatus: '未测试',
      lastTestTime: ''
    },
    wechatPayout: {
      enabled: 0,
      merchantName: 'SeedCRM 演示代付商户',
      mchId: '',
      appId: '',
      apiV3Key: '',
      serialNo: '',
      privateKeyPem: '',
      transferScene: '营销返佣',
      verifyUserName: 'NO_CHECK',
      notifyUrl: 'https://crm.example.com/pay/wechat/transfer-notify',
      notifyPath: '/pay/wechat/transfer-notify',
      singleLimit: '20000',
      dailyLimitTip: '以商户侧风控和微信支付配置为准',
      testStatus: '未测试',
      lastTestTime: ''
    }
  },
  serviceFormTemplates: [
    { id: 1, templateCode: 'PHOTO_CLASSIC', templateName: '肖像服务经典版', title: '到店服务确认单', industry: '肖像摄影', layoutMode: 'classic', description: '适合摄影、化妆、选片一体流程', enabled: 1, recommended: 1 },
    { id: 2, templateCode: 'MEDICAL_MINI', templateName: '医美到店简洁版', title: '到店项目确认单', industry: '医美咨询', layoutMode: 'compact', description: '适合咨询、到店确认与签字留痕', enabled: 1, recommended: 1 },
    { id: 3, templateCode: 'CUSTOM_PREMIUM', templateName: '门店自定义高级版', title: '门店服务单', industry: '通用', layoutMode: 'premium', description: '适合展示品牌标题和服务步骤', enabled: 1, recommended: 0 }
  ],
  serviceFormBindings: [
    { id: 1, storeName: '静安门店', templateId: 1, effectiveFrom: '2026-04-01', allowOverride: 0, enabled: 1 },
    { id: 2, storeName: '浦东门店', templateId: 2, effectiveFrom: '2026-04-01', allowOverride: 1, enabled: 1 },
    { id: 3, storeName: '徐汇门店', templateId: 3, effectiveFrom: '2026-04-01', allowOverride: 1, enabled: 1 }
  ],
  thirdPartyApis: [
    { id: 1, apiName: '客资中心抖音拉取', moduleCode: 'CLUE', baseUrl: 'https://api.oceanengine.com/open_api/2/tools/clue/life/get/', method: 'POST', authType: 'Access-Token', enabled: 1, syncMode: '增量同步', scheduleJobCode: 'DOUYIN_CLUE_INCREMENTAL' },
    { id: 2, apiName: '分销订单入站', moduleCode: 'ORDER', baseUrl: '/open/distribution/events', method: 'POST', authType: '签名', enabled: 1, syncMode: '事件入站', scheduleJobCode: 'DISTRIBUTION_OUTBOX_PROCESS' }
  ],
  callbackApis: [
    {
      id: 1,
      callbackName: '客资回流状态回调',
      callbackUrl: 'https://seedcrm.example.com/api/callback/clue-status',
      callbackPath: '/api/callback/clue-status',
      signatureMode: 'HMAC-SHA256',
      enabled: 1,
      remark: '同步三方客资处理状态'
    }
  ],
  publicApis: [
    { id: 1, apiName: '门店订单联合查询', sourceTable: 'order + customer + plan_order', outputFields: 'orderNo, customerName, amount, statusLabel', authMode: '签名 + 令牌', rateLimit: '60 次/分钟', cachePolicy: '30 秒缓存', enabled: 1 }
  ],
  dictionaries: DEFAULT_DICTIONARIES,
  parameters: [
    { id: 1, paramKey: 'clue.autoPull.enabled', paramValue: 'true', category: '客资', remark: '控制客资中心是否启用自动拉取' },
    { id: 21, paramKey: 'clue.dedup.enabled', paramValue: 'true', category: '客资', remark: '控制后续接口入库是否启用客资去重合并' },
    { id: 22, paramKey: 'clue.dedup.window_days', paramValue: '90', category: '客资', remark: '同来源同手机号或微信在窗口内合并为一条客资，订单动作进入客资记录' },
    { id: 23, paramKey: 'amount.visibility.store_staff_hidden', paramValue: 'true', category: '财务', remark: '控制门店角色是否隐藏定金、团购和核销金额' },
    { id: 24, paramKey: 'amount.visibility.store_staff_hidden_roles', paramValue: 'STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR', category: '财务', remark: '隐藏前置订单金额的角色编码，英文逗号分隔' },
    { id: 25, paramKey: 'amount.visibility.service_confirm_hidden_roles', paramValue: 'STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR', category: '财务', remark: '隐藏服务确认单金额的角色编码，英文逗号分隔' },
    { id: 26, paramKey: 'amount.visibility.service_confirm_edit_roles', paramValue: 'ADMIN,FINANCE', category: '财务', remark: '允许填写或修改服务确认单金额的角色编码，英文逗号分隔' },
    { id: 31, paramKey: 'finance.ledger.only_mode', paramValue: 'true', category: '财务', remark: '财务管理只做台账、对账、线下处理登记，不发起真实收款、退款、提现或转账' },
    { id: 32, paramKey: 'finance.ledger.refund_salary_reversal_required', paramValue: 'true', category: '财务', remark: '退款流程必须同步生成或登记薪资、分销绩效冲正' },
    { id: 33, paramKey: 'finance.ledger.distributor_withdraw_register_only', paramValue: 'true', category: '财务', remark: '分销提现只同步外部结果或登记线下处理，不在本系统发起打款' },
    { id: 27, paramKey: 'deposit.direct.enabled', paramValue: 'true', category: '门店服务', remark: '定金订单允许免扫码或输码记录到店确认，不发起券码核销' },
    { id: 28, paramKey: 'workflow.system_flow_runtime.enabled', paramValue: 'false', category: '系统流程', remark: '是否记录流程运行态旁路观察；默认关闭，不接管业务状态' },
    { id: 29, paramKey: 'workflow.service_order.enabled', paramValue: 'false', category: '系统流程', remark: '服务确认单旁路观察预留开关；默认关闭，保留现有业务逻辑' },
    { id: 30, paramKey: 'workflow.scheduling.enabled', paramValue: 'false', category: '系统流程', remark: '顾客排档旁路观察预留开关；默认关闭，保留现有预约逻辑' },
    { id: 2, paramKey: 'order.confirmation.readonlyWhenCompleted', paramValue: 'true', category: '订单', remark: '已完成订单确认单进入只读查看' },
    { id: 3, paramKey: 'system.defaultStoreId', paramValue: '10', category: '系统', remark: '默认门店 ID' },
    { id: 4, paramKey: 'wecom.customerProfile.enabled', paramValue: 'true', category: '私域客服', remark: '控制客服画像是否在企业微信展示' },
    { id: 5, paramKey: 'wecom.moments.massSend.enabled', paramValue: 'true', category: '私域客服', remark: '控制朋友圈定时群发功能开关' },
    { id: 6, paramKey: 'wecom.tag.sync.enabled', paramValue: 'true', category: '私域客服', remark: '控制企微标签同步开关' },
    { id: 7, paramKey: 'appointment.reason.allowed_codes', paramValue: 'CUSTOMER_REQUEST,RESCHEDULE,STORE_ADJUST,TRAFFIC_DELAY,CUSTOMER_CANCEL', category: '客资', remark: '顾客排档原因白名单，逗号分隔；只影响约档、改档、取消预约记录口径' },
    { id: 8, paramKey: 'appointment.reason.required_actions', paramValue: '', category: '客资', remark: '需要强制选择原因的排档动作，支持 APPOINTMENT_CREATE/APPOINTMENT_CHANGE/APPOINTMENT_CANCEL' },
    { id: 9, paramKey: 'appointment.reason.default_create', paramValue: 'CUSTOMER_REQUEST', category: '客资', remark: '首次约档默认原因，不影响已保存排档记录' },
    { id: 10, paramKey: 'appointment.reason.default_change', paramValue: 'RESCHEDULE', category: '客资', remark: '改档默认原因，不自动触发改档' },
    { id: 11, paramKey: 'appointment.reason.default_cancel', paramValue: 'CUSTOMER_CANCEL', category: '客资', remark: '取消预约默认原因，不改变订单状态流转规则' },
    { id: 12, paramKey: 'service_form.print.required_before_confirm', paramValue: 'true', category: '门店服务', remark: '打印当前版本后才允许确认纸质服务单' },
    { id: 13, paramKey: 'service_form.confirm.required_before_start', paramValue: 'true', category: '门店服务', remark: '开始服务前必须确认纸质服务单' },
    { id: 14, paramKey: 'service_form.print.stale_policy', paramValue: 'BLOCK_CONFIRM', category: '门店服务', remark: '确认单内容变化后要求重新打印确认' },
    { id: 15, paramKey: 'form_designer.adapter.enabled', paramValue: 'true', category: '门店服务', remark: '服务单设计器适配层预留治理项，当前保持启用默认值' },
    { id: 16, paramKey: 'form_designer.provider', paramValue: 'INTERNAL_SCHEMA', category: '门店服务', remark: '默认服务单设计器适配器预留治理项，当前保持内置 Schema' },
    { id: 17, paramKey: 'form_designer.allowed_engines', paramValue: 'INTERNAL_SCHEMA,FORMILY,VFORM3,LOWCODE_ENGINE,JSON_SCHEMA', category: '门店服务', remark: '允许接入的成熟服务单设计器' },
    { id: 18, paramKey: 'form_designer.blocked_components', paramValue: 'signature,esign,electronicSignature,canvasSignature,html,iframe,script,webview', category: '门店服务', remark: '导入时拦截电子签名和脚本类组件' },
    { id: 19, paramKey: 'form_designer.max_schema_bytes', paramValue: '200000', category: '门店服务', remark: '单个服务单设计器 Schema 最大长度' },
    { id: 20, paramKey: 'form_designer.paper_signature_required', paramValue: 'true', category: '门店服务', remark: '打印版强制保留纸质手写签名位置' }
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
    { id: 1, customerName: '王女士', wecomNickname: '王女士-静安店', primaryDemand: '皮肤管理', tags: ['高意向', '团购已付款', '可私聊'], lastContactAt: '2026-04-22 14:20:00', isEnabled: 1 },
    { id: 2, customerName: '李先生', wecomNickname: '李先生-浦东店', primaryDemand: '植发咨询', tags: ['表单留资', '待预约'], lastContactAt: '2026-04-23 19:10:00', isEnabled: 1 }
  ],
  wecomMomentsCampaigns: [
    { id: 1, campaignName: '五一活动预热', contentSummary: '发布五一门店福利和预约提醒', scheduleTime: '2026-04-28 10:00:00', targetScope: '全部好友', status: 'ENABLED', createdBy: '私域客服A' },
    { id: 2, campaignName: '术后关怀提醒', contentSummary: '对已到店客户分组群发术后注意事项', scheduleTime: '2026-04-29 18:30:00', targetScope: '术后客户标签', status: 'DRAFT', createdBy: '私域客服B' }
  ],
  wecomTagConfigs: [
    { id: 1, tagName: '高意向', tagCode: 'HIGH_INTENT', customerCount: 38, newToday: 6, syncStatus: 'SUCCESS', createdAt: '2026-04-18 11:00:00' },
    { id: 2, tagName: '团购已付款', tagCode: 'GROUP_BUY_PAID', customerCount: 21, newToday: 3, syncStatus: 'SUCCESS', createdAt: '2026-04-19 09:30:00' },
    { id: 3, tagName: '表单待预约', tagCode: 'FORM_WAIT_APPOINTMENT', customerCount: 17, newToday: 4, syncStatus: 'PENDING', createdAt: '2026-04-20 15:40:00' }
  ]
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

function normalizeKey(value) {
  return value ? String(value).trim().toUpperCase() : ''
}

function normalizeBaseUrl(value) {
  return String(value || '')
    .trim()
    .replace(/\/+$/, '')
}

function joinUrl(baseUrl, path) {
  const normalizedBase = normalizeBaseUrl(baseUrl)
  const normalizedPath = String(path || '').trim()
  if (!normalizedBase) {
    return normalizedPath
  }
  if (!normalizedPath) {
    return normalizedBase
  }
  if (/^https?:\/\//i.test(normalizedPath)) {
    return normalizedPath
  }
  const ensuredPath = normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`
  return `${normalizedBase}${ensuredPath}`
}

function extractRelativePath(value, fallback = '') {
  const normalized = String(value || '').trim()
  if (!normalized) {
    return fallback
  }
  if (/^https?:\/\//i.test(normalized)) {
    try {
      const parsed = new URL(normalized)
      return `${parsed.pathname || ''}${parsed.search || ''}${parsed.hash || ''}` || fallback
    } catch {
      return fallback
    }
  }
  return normalized.startsWith('/') ? normalized : fallback || normalized
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

function mergeUniqueList(currentItems = [], requiredItems = []) {
  return Array.from(new Set([...(currentItems || []), ...(requiredItems || [])]))
}

function migrateSystemConsoleState(state) {
  const defaults = clone(DEFAULT_STATE)
  const nextState = {
    ...defaults,
    ...state
  }
  const shouldApplyPermissionBaseline =
    Number(state?.permissionBaselineVersion || 0) < SYSTEM_CONSOLE_SCHEMA_VERSION

  const normalizedMenuConfigs = (state?.menuConfigs || [])
    .map((item) => {
    if (item?.routePath === '/clues/payments') {
      return {
        ...item,
        menuGroup: '客资中心 / 线上客服工作台',
        menuName: '顾客排档',
        routePath: '/clues/scheduling'
      }
    }
    if (item?.routePath === '/clues') {
      return {
        ...item,
        menuGroup: '客资中心 / 线上客服工作台',
        menuName: '客资列表',
        moduleCode: 'CLUE'
      }
    }
    if (item?.routePath === '/clues/scheduling') {
      return {
        ...item,
        menuGroup: '客资中心 / 线上客服工作台',
        menuName: '顾客排档',
        moduleCode: 'ORDER'
      }
    }
    if (item?.routePath === '/clue-management/auto-assign') {
      return {
        ...item,
        menuGroup: '客资中心 / 客资管理',
        menuName: '客资配置',
        moduleCode: 'CLUE'
      }
    }
    if (item?.routePath === '/clue-management/store-schedules') {
      return {
        ...item,
        menuGroup: '门店服务',
        menuName: '门店档期',
        routePath: '/store-service/schedules',
        roleCodes: ['ADMIN', 'STORE_MANAGER'],
        moduleCode: 'PLANORDER'
      }
    }
    if (item?.routePath === '/finance/salary-center') {
      return {
        ...item,
        menuGroup: '财务管理 / 薪酬中心',
        menuName: '我的薪酬',
        routePath: '/finance/salary/my'
      }
    }
    if (item?.routePath === '/private-domain/wecom') {
      return {
        ...item,
        menuGroup: '系统设置 / 基础配置',
        menuName: '企业微信',
        routePath: '/settings/base/wecom',
        roleCodes: ['ADMIN'],
        moduleCode: 'SETTING'
      }
    }
    if (item?.routePath === '/finance/salary/my') {
      return {
        ...item,
        roleCodes: shouldApplyPermissionBaseline ? mergeUniqueList(item.roleCodes, SELF_SALARY_ROLE_CODES) : item.roleCodes
      }
    }
    if (item?.routePath === '/finance/salary/settlements') {
      return {
        ...item,
        menuGroup: '财务管理 / 薪酬结算',
        roleCodes: ['ADMIN', 'FINANCE']
      }
    }
    if (item?.routePath === '/finance/salary/withdrawals') {
      return {
        ...item,
        menuGroup: '财务管理 / 薪酬结算',
        menuName: '线下结清登记',
        roleCodes: ['ADMIN', 'FINANCE'],
        moduleCode: 'SALARY'
      }
    }
    return item
  })
  const normalizedDictionaries = (state?.dictionaries || []).map((item) => {
    if (item?.dictType === 'product_source_type' && item?.itemCode === 'FORM') {
      return {
        ...item,
        itemLabel: '定金'
      }
    }
    if (item?.dictType === 'order_status' && item?.itemCode === 'PAID_OUT') {
      return {
        ...item,
        itemLabel: '已线下结清'
      }
    }
    if (item?.dictType === 'settlement_mode' && item?.itemCode === 'WITHDRAW_AUDIT') {
      return {
        ...item,
        itemLabel: '线下结清需审核'
      }
    }
    if (item?.dictType === 'settlement_mode' && item?.itemCode === 'WITHDRAW_DIRECT') {
      return {
        ...item,
        itemLabel: '外部处理直接登记'
      }
    }
    return item
  })
  const normalizedSalarySettlementRules = (state?.salarySettlementRules || []).map((item) => {
    if (item?.ruleName === '分销大额提现') {
      return {
        ...item,
        ruleName: '分销大额线下结清',
        remark: '大额分销结清需财务审核登记'
      }
    }
    if (item?.ruleName === '分销小额提现') {
      return {
        ...item,
        ruleName: '分销小额外部处理',
        remark: '小额分销由外部平台处理后直接登记'
      }
    }
    if (item?.ruleName === '内部员工记账' && String(item?.remark || '').includes('提现')) {
      return {
        ...item,
        remark: '内部员工只记账，不走资金划拨'
      }
    }
    return item
  })
  const normalizedThirdPartyApis = (state?.thirdPartyApis || []).map((item) => {
    if (String(item?.apiName || '').includes('表单拉取') || item?.scheduleJobCode === 'FORM_CLUE_INCREMENTAL') {
      return {
        ...item,
        apiName: '分销订单入站',
        moduleCode: 'ORDER',
        baseUrl: '/open/distribution/events',
        method: 'POST',
        authType: '签名',
        enabled: 1,
        syncMode: '事件入站',
        scheduleJobCode: 'DISTRIBUTION_OUTBOX_PROCESS'
      }
    }
    return item
  })

  nextState.domainSettings = {
    ...(defaults.domainSettings || {}),
    ...(state?.domainSettings || {})
  }
  nextState.domainSettings.systemBaseUrl =
    nextState.domainSettings.systemBaseUrl || nextState.domainSettings.adminBaseUrl || defaults.domainSettings.systemBaseUrl
  nextState.domainSettings.apiBaseUrl =
    nextState.domainSettings.apiBaseUrl ||
    nextState.domainSettings.callbackBaseUrl ||
    nextState.domainSettings.publicApiBaseUrl ||
    defaults.domainSettings.apiBaseUrl
  nextState.menuConfigs = mergeCollectionByKey(normalizedMenuConfigs, defaults.menuConfigs, (item) => item?.routePath || item?.id)
  if (shouldApplyPermissionBaseline) {
    nextState.roles = (nextState.roles || []).map((role) => {
      const roleCode = normalizeKey(role?.roleCode)
      if (!SELF_SALARY_ROLE_CODES.includes(roleCode)) {
        return role
      }
      return {
        ...role,
        moduleCodes: mergeUniqueList(role.moduleCodes, ['SALARY'])
      }
    })
  }
  nextState.permissionBaselineVersion = SYSTEM_CONSOLE_SCHEMA_VERSION
  nextState.dictionaries = mergeCollectionByKey(normalizedDictionaries, defaults.dictionaries, (item) => `${item?.dictType || ''}:${item?.itemCode || ''}`)
  nextState.parameters = mergeCollectionByKey(state?.parameters, defaults.parameters, (item) => item?.paramKey || item?.id)
  nextState.wecomLiveCodeConfigs = mergeCollectionByKey(state?.wecomLiveCodeConfigs, defaults.wecomLiveCodeConfigs, (item) => item?.id)
  nextState.wecomCustomerPortraits = mergeCollectionByKey(state?.wecomCustomerPortraits, defaults.wecomCustomerPortraits, (item) => item?.id)
  nextState.wecomMomentsCampaigns = mergeCollectionByKey(state?.wecomMomentsCampaigns, defaults.wecomMomentsCampaigns, (item) => item?.id)
  nextState.wecomTagConfigs = mergeCollectionByKey(state?.wecomTagConfigs, defaults.wecomTagConfigs, (item) => item?.tagCode || item?.id)
  nextState.storeScheduleConfigs = mergeCollectionByKey(state?.storeScheduleConfigs, defaults.storeScheduleConfigs, (item) => item?.storeName || item?.id)
  nextState.clueConsoleProfiles = mergeCollectionByKey(state?.clueConsoleProfiles, defaults.clueConsoleProfiles, (item) => item?.clueId || item?.id)
  nextState.salaryRoles = mergeCollectionByKey(state?.salaryRoles, defaults.salaryRoles, (item) => item?.roleCode || item?.id)
  nextState.salaryGrades = mergeCollectionByKey(state?.salaryGrades, defaults.salaryGrades, (item) => item?.gradeName || item?.id)
  nextState.salarySettlementRules = mergeCollectionByKey(normalizedSalarySettlementRules, defaults.salarySettlementRules, (item) => item?.ruleName || item?.id)
  nextState.distributorConfigs = mergeCollectionByKey(state?.distributorConfigs, defaults.distributorConfigs, (item) => item?.configName || item?.id)
  nextState.serviceFormTemplates = mergeCollectionByKey(state?.serviceFormTemplates, defaults.serviceFormTemplates, (item) => item?.templateCode || item?.id)
  nextState.serviceFormBindings = mergeCollectionByKey(state?.serviceFormBindings, defaults.serviceFormBindings, (item) => item?.storeName || item?.id)
  nextState.thirdPartyApis = mergeCollectionByKey(normalizedThirdPartyApis, defaults.thirdPartyApis, (item) => item?.scheduleJobCode || item?.apiName || item?.id)
  nextState.paymentSettings = {
    ...(defaults.paymentSettings || {}),
    ...(state?.paymentSettings || {}),
    wechatPay: {
      ...(defaults.paymentSettings?.wechatPay || {}),
      ...(state?.paymentSettings?.wechatPay || {})
    },
    wechatPayout: {
      ...(defaults.paymentSettings?.wechatPayout || {}),
      ...(state?.paymentSettings?.wechatPayout || {})
    }
  }
  nextState.paymentSettings.wechatPay.notifyPath = extractRelativePath(
    nextState.paymentSettings.wechatPay.notifyPath || nextState.paymentSettings.wechatPay.notifyUrl,
    defaults.paymentSettings.wechatPay.notifyPath
  )
  nextState.paymentSettings.wechatPay.refundNotifyPath = extractRelativePath(
    nextState.paymentSettings.wechatPay.refundNotifyPath || nextState.paymentSettings.wechatPay.refundNotifyUrl,
    defaults.paymentSettings.wechatPay.refundNotifyPath
  )
  nextState.paymentSettings.wechatPayout.notifyPath = extractRelativePath(
    nextState.paymentSettings.wechatPayout.notifyPath || nextState.paymentSettings.wechatPayout.notifyUrl,
    defaults.paymentSettings.wechatPayout.notifyPath
  )
  nextState.paymentSettings.wechatPay.notifyUrl = buildSystemUrl(nextState, 'callback', nextState.paymentSettings.wechatPay.notifyPath)
  nextState.paymentSettings.wechatPay.refundNotifyUrl = buildSystemUrl(nextState, 'callback', nextState.paymentSettings.wechatPay.refundNotifyPath)
  nextState.paymentSettings.wechatPayout.notifyUrl = buildSystemUrl(nextState, 'callback', nextState.paymentSettings.wechatPayout.notifyPath)
  nextState.callbackApis = mergeCollectionByKey(state?.callbackApis, defaults.callbackApis, (item) => item?.callbackName || item?.id).map((item) => {
    const callbackPath = extractRelativePath(item.callbackPath || item.callbackUrl, '/api/callback/default')
    return {
      ...item,
      callbackPath,
      callbackUrl: buildSystemUrl(nextState, 'callback', callbackPath)
    }
  })

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
  window.dispatchEvent(new CustomEvent('seedcrm:system-console-updated'))
}

export function nextSystemId(items = []) {
  return items.reduce((max, item) => Math.max(max, Number(item?.id || 0)), 0) + 1
}

export function getDomainSettings(state) {
  return {
    ...DEFAULT_DOMAIN_SETTINGS,
    ...(state?.domainSettings || {})
  }
}

export function resolveSystemBaseUrl(state, scene = 'system') {
  const config = getDomainSettings(state)
  if (scene === 'api' || scene === 'callback' || scene === 'public-api') {
    return normalizeBaseUrl(config.apiBaseUrl || config.systemBaseUrl)
  }
  return normalizeBaseUrl(config.systemBaseUrl)
}

export function buildSystemUrl(state, scene = 'system', path = '') {
  return joinUrl(resolveSystemBaseUrl(state, scene), path)
}

export function getDictionaryLabel(state, dictType, code, fallback = '') {
  const normalizedDictType = String(dictType || '').trim()
  const normalizedCode = normalizeKey(code)
  if (!normalizedDictType || !normalizedCode) {
    return fallback || code || '--'
  }
  const matched = (state?.dictionaries || []).find(
    (item) => item?.dictType === normalizedDictType && normalizeKey(item?.itemCode) === normalizedCode && item?.isEnabled === 1
  )
  return matched?.itemLabel || fallback || code || '--'
}

export function getDictionaryOptions(state, dictType) {
  return (state?.dictionaries || [])
    .filter((item) => item?.dictType === dictType && item?.isEnabled === 1)
    .sort((left, right) => Number(left?.sortOrder || 0) - Number(right?.sortOrder || 0))
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
