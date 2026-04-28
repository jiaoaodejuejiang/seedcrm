import { getDictionaryLabel, loadSystemConsoleState } from './systemConsoleStore'

const CHANNEL_LABELS = {
  DOUYIN: '抖音',
  DISTRIBUTOR: '分销',
  DISTRIBUTION: '分销',
  FORM: '表单'
}

const PRODUCT_SOURCE_LABELS = {
  GROUP_BUY: '团购',
  GROUPBUY: '团购',
  GROUPON: '团购',
  FORM: '表单'
}

const ORDER_TYPE_LABELS = {
  1: '定金',
  2: '团购券',
  DEPOSIT: '定金',
  COUPON: '团购券'
}

const ORDER_STATUS_LABELS = {
  CREATED: '待付款',
  PAID: '已付款',
  PAID_DEPOSIT: '已付定金',
  APPOINTMENT: '已预约',
  ARRIVED: '已到店',
  SERVING: '服务中',
  SERVICING: '服务中',
  USED: '已完成',
  COMPLETED: '已完成',
  FINISHED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
  APPROVED: '已通过',
  PENDING: '待处理',
  INIT: '待确认',
  CONFIRMED: '已确认',
  PAID_OUT: '已打款',
  DRAFT: '草稿',
  ENABLED: '启用',
  DISABLED: '停用',
  ACTIVE: '启用',
  INACTIVE: '停用',
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败'
}

const CLUE_STATUS_LABELS = {
  NEW: '新客资',
  ASSIGNED: '已分配',
  FOLLOWING: '跟进中',
  CONVERTED: '已转化'
}

const CALL_STATUS_LABELS = {
  NOT_CALLED: '未通话',
  CONNECTED: '已接通',
  MISSED: '未接通',
  CALLBACK: '待回拨',
  INVALID: '无效号码'
}

const LEAD_STAGE_LABELS = {
  NEW: '新线索',
  INTENT: '有意向',
  ARRIVED: '到店',
  DEAL: '成交',
  CALLBACK_PENDING: '待再次沟通',
  WECHAT_ADDED: '已加微信',
  DEPOSIT_PAID: '预付定金',
  INVALID: '无效',
  CONTACTED: '已联系',
  APPOINTMENT_PENDING: '待预约',
  APPOINTED: '已预约',
  CLOSED: '已成交'
}

const PLAN_ORDER_STATUS_LABELS = {
  ARRIVED: '待到店',
  SERVICING: '服务中',
  FINISHED: '已完成'
}

const SCHEDULER_STATUS_LABELS = {
  ACTIVE: '启用',
  ENABLED: '启用',
  INACTIVE: '停用',
  DISABLED: '停用',
  QUEUED: '排队中',
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败'
}

const ROLE_LABELS = {
  ADMIN: '管理员',
  ONLINE_CUSTOMER_SERVICE: '在线客服',
  CLUE_MANAGER: '客资主管',
  STORE_SERVICE: '门店服务',
  STORE_MANAGER: '店长',
  PHOTOGRAPHER: '摄影',
  MAKEUP_ARTIST: '化妆师',
  PHOTO_SELECTOR: '选片负责人',
  FINANCE: '财务',
  PRIVATE_DOMAIN_SERVICE: '私域客服',
  NORMAL_CS: '普通客服',
  SENIOR_CS: '资深客服',
  LEADER: '组长',
  CONSULTANT: '顾问',
  DOCTOR: '医生',
  ASSISTANT: '助理'
}

const VERIFICATION_STATUS_LABELS = {
  VERIFIED: '已核销',
  UNVERIFIED: '待核销'
}

const SETTLEMENT_MODE_LABELS = {
  WITHDRAW_AUDIT: '提现审核',
  WITHDRAW_DIRECT: '提现不审核',
  LEDGER_ONLY: '只记账'
}

const EXECUTION_MODE_LABELS = {
  MOCK: '模拟',
  LIVE: '真实'
}

const AUTH_STATUS_LABELS = {
  AUTHORIZED: '已授权',
  AUTH_CODE_RECEIVED: '已收到授权码',
  UNAUTHORIZED: '未授权',
  EXPIRED: '已过期',
  PENDING: '待授权',
  RECEIVED: '已接收'
}

const AUTH_TYPE_LABELS = {
  AUTH_CODE: '授权码模式',
  CLIENT_TOKEN: '应用凭证模式',
  SELF_BUILT: '自建应用',
  SERVICE_PROVIDER: '服务商模式'
}

const CALLBACK_SIGNATURE_LABELS = {
  VERIFIED: '已验签',
  NOT_VERIFIED: '未验签',
  LOCAL_BYPASS: '本地跳过',
  SKIPPED: '已跳过',
  FAILED: '验签失败'
}

const CALLBACK_PROCESS_LABELS = {
  SUCCESS: '成功',
  FAIL: '失败',
  FAILED: '失败',
  RECEIVED: '已接收',
  PROCESSED: '已处理',
  PENDING: '待处理'
}

const RESULT_STATUS_LABELS = {
  SUCCESS: '成功',
  SUCCEEDED: '成功',
  OK: '成功',
  FAIL: '失败',
  FAILED: '失败',
  ERROR: '失败',
  INVALID: '无效',
  PENDING: '待处理',
  RECEIVED: '已接收',
  PROCESSED: '已处理',
  SKIPPED: '已跳过',
  ENABLED: '启用',
  ACTIVE: '启用',
  DISABLED: '停用',
  INACTIVE: '停用',
  VERIFIED: '已核销',
  UNVERIFIED: '待核销',
  EXPIRED: '已过期',
  AUTHORIZED: '已授权',
  UNAUTHORIZED: '未授权',
  EXCHANGED: '已换取',
  LOCAL_BYPASS: '本地跳过',
  NOT_VERIFIED: '未验签'
}

function dictionaryLabel(dictType, value, fallback = '') {
  return getDictionaryLabel(loadSystemConsoleState(), dictType, value, fallback)
}

function pickLabel(map, value, dictType, fallback = '') {
  const normalized = normalize(value)
  return map[normalized] || (dictType ? dictionaryLabel(dictType, normalized, '') : '') || fallback || value || '--'
}

export function normalize(value) {
  return value ? String(value).trim().toUpperCase() : ''
}

export function formatMoney(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

export function formatDateTime(value) {
  if (!value) {
    return '--'
  }

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', {
    hour12: false
  })
}

export function formatChannel(value) {
  return pickLabel(CHANNEL_LABELS, value, 'clue_channel')
}

export function formatProductSourceType(value) {
  return pickLabel(PRODUCT_SOURCE_LABELS, value, 'product_source_type')
}

export function formatOrderType(value) {
  return pickLabel(ORDER_TYPE_LABELS, value)
}

export function formatOrderStatus(status) {
  return pickLabel(ORDER_STATUS_LABELS, status, 'order_status')
}

export function formatOrderStage(status) {
  const normalized = normalize(status)
  if (!normalized) {
    return '--'
  }
  if (['CREATED', 'PAID', 'PAID_DEPOSIT', 'APPOINTMENT', 'ARRIVED', 'SERVING', 'SERVICING'].includes(normalized)) {
    return '进行中'
  }
  if (['USED', 'COMPLETED', 'FINISHED'].includes(normalized)) {
    return '已完成'
  }
  if (normalized === 'CANCELLED') {
    return '已取消'
  }
  if (normalized === 'REFUNDED') {
    return '已退款'
  }
  return formatOrderStatus(status)
}

export function formatClueStatus(status) {
  return pickLabel(CLUE_STATUS_LABELS, status, 'clue_status')
}

export function formatCallStatus(status) {
  return pickLabel(CALL_STATUS_LABELS, status, 'call_status')
}

export function formatLeadStage(stage) {
  return pickLabel(LEAD_STAGE_LABELS, stage, 'lead_stage')
}

export function formatPlanOrderStatus(status) {
  return pickLabel(PLAN_ORDER_STATUS_LABELS, status, 'plan_order_status')
}

export function formatSchedulerStatus(status) {
  return pickLabel(SCHEDULER_STATUS_LABELS, status, 'scheduler_status')
}

export function formatModuleCode(value) {
  return (
    {
      CLUE: '客资',
      ORDER: '订单',
      PLANORDER: '服务单',
      SCHEDULER: '调度',
      SYSTEM: '系统管理',
      SETTING: '系统设置',
      WECOM: '私域客服',
      PERMISSION: '权限',
      SALARY: '薪酬',
      DISTRIBUTOR: '分销',
      FINANCE: '财务'
    }[normalize(value)] || value || '--'
  )
}

export function formatActionCode(value) {
  return (
    {
      VIEW: '查看',
      CREATE: '创建',
      UPDATE: '更新',
      ASSIGN: '分配',
      RECYCLE: '回收',
      FINISH: '完结',
      ASSIGN_ROLE: '分配角色',
      REFUND_STORE: '门店退款',
      REFUND_PAYMENT: '付款退款',
      TRIGGER: '触发',
      DEBUG: '调试',
      CHECK: '核验'
    }[normalize(value)] || value || '--'
  )
}

export function formatSyncMode(value) {
  return (
    {
      INCREMENTAL: '增量同步',
      FULL: '全量同步',
      MANUAL: '手动触发'
    }[normalize(value)] || value || '--'
  )
}

export function formatRoleCode(value) {
  return pickLabel(ROLE_LABELS, value, 'role_code')
}

export function formatVerificationStatus(value) {
  return pickLabel(VERIFICATION_STATUS_LABELS, value, 'verification_status')
}

export function formatSettlementMode(value) {
  return pickLabel(SETTLEMENT_MODE_LABELS, value, 'settlement_mode')
}

export function formatExecutionMode(value) {
  return pickLabel(EXECUTION_MODE_LABELS, value, 'execution_mode')
}

export function formatAuthStatus(value) {
  return pickLabel(AUTH_STATUS_LABELS, value, 'auth_status')
}

export function formatAuthType(value) {
  return pickLabel(AUTH_TYPE_LABELS, value)
}

export function formatCallbackSignatureStatus(value) {
  return pickLabel(CALLBACK_SIGNATURE_LABELS, value, 'callback_signature_status')
}

export function formatCallbackProcessStatus(value) {
  return pickLabel(CALLBACK_PROCESS_LABELS, value, 'callback_process_status')
}

export function formatResultStatus(value) {
  return pickLabel(RESULT_STATUS_LABELS, value)
}

export function formatScope(value) {
  return (
    {
      SELF: '本人',
      TEAM: '团队',
      STORE: '门店',
      ALL: '全部'
    }[normalize(value)] || value || '--'
  )
}

export function formatPermissionResult(response) {
  if (!response) {
    return '--'
  }
  return response.allowed ? `允许 / ${formatScope(response.dataScope)}` : `拒绝 / ${response.reason || '未命中策略'}`
}

export function statusTagType(value) {
  return (
    {
      NEW: 'warning',
      ASSIGNED: 'primary',
      FOLLOWING: '',
      CONVERTED: 'success',
      NOT_CALLED: 'info',
      CONNECTED: 'success',
      MISSED: 'warning',
      CALLBACK: 'warning',
      INVALID: 'danger',
      CONTACTED: 'primary',
      INTENT: 'success',
      DEAL: 'success',
      CALLBACK_PENDING: 'warning',
      WECHAT_ADDED: 'primary',
      DEPOSIT_PAID: 'warning',
      APPOINTMENT_PENDING: 'warning',
      APPOINTED: 'primary',
      PAID: 'warning',
      PAID_DEPOSIT: 'warning',
      APPOINTMENT: 'warning',
      ARRIVED: 'primary',
      SERVING: 'primary',
      SERVICING: 'primary',
      USED: 'success',
      COMPLETED: 'success',
      FINISHED: 'success',
      CREATED: 'info',
      CANCELLED: 'info',
      REFUNDED: 'danger',
      SUCCESS: 'success',
      FAIL: 'danger',
      FAILED: 'danger',
      ACTIVE: 'success',
      INACTIVE: 'info',
      ENABLED: 'success',
      DISABLED: 'info',
      QUEUED: 'warning',
      RUNNING: 'primary',
      APPROVED: 'success',
      VERIFIED: 'success',
      UNVERIFIED: 'warning',
      INIT: 'warning',
      PENDING: 'warning',
      CONFIRMED: 'primary',
      PAID_OUT: 'success',
      DRAFT: 'info'
    }[normalize(value)] || 'info'
  )
}

export function toDateTimeString(value) {
  if (!value) {
    return undefined
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return undefined
  }
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes()
  )}:${pad(date.getSeconds())}`
}
