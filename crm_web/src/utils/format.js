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
  const normalized = normalize(value)
  return (
    {
      DOUYIN: '抖音',
      DISTRIBUTION: '分销',
      DISTRIBUTOR: '分销'
    }[normalized] || value || '--'
  )
}

export function formatProductSourceType(value) {
  const normalized = normalize(value)
  return (
    {
      GROUP_BUY: '团购',
      GROUPBUY: '团购',
      GROUPON: '团购',
      FORM: '表单'
    }[normalized] || value || '--'
  )
}

export function formatOrderType(value) {
  const normalized = normalize(value)
  return (
    {
      1: '定金',
      2: '团购券',
      DEPOSIT: '定金',
      COUPON: '团购券'
    }[normalized] || value || '--'
  )
}

export function formatOrderStatus(status) {
  const normalized = normalize(status)
  return (
    {
      CREATED: '待付款',
      PAID: '已付款',
      PAID_DEPOSIT: '已付款',
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
      SUCCESS: '成功',
      FAIL: '失败',
      FAILED: '失败'
    }[normalized] || status || '--'
  )
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
  const normalized = normalize(status)
  return (
    {
      NEW: '新客资',
      ASSIGNED: '已分配',
      FOLLOWING: '跟进中',
      CONVERTED: '已转化'
    }[normalized] || status || '--'
  )
}

export function formatCallStatus(status) {
  const normalized = normalize(status)
  return (
    {
      NOT_CALLED: '未通话',
      CONNECTED: '已接通',
      MISSED: '未接通',
      CALLBACK: '待回拨',
      INVALID: '无效号码'
    }[normalized] || status || '--'
  )
}

export function formatLeadStage(stage) {
  const normalized = normalize(stage)
  return (
    {
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
    }[normalized] || stage || '--'
  )
}

export function formatPlanOrderStatus(status) {
  const normalized = normalize(status)
  return (
    {
      ARRIVED: '已到店',
      SERVICING: '服务中',
      FINISHED: '已完成'
    }[normalized] || status || '--'
  )
}

export function formatSchedulerStatus(status) {
  const normalized = normalize(status)
  return (
    {
      ACTIVE: '启用',
      ENABLED: '启用',
      INACTIVE: '停用',
      DISABLED: '停用',
      QUEUED: '排队中',
      RUNNING: '执行中',
      SUCCESS: '成功',
      FAIL: '失败',
      FAILED: '失败'
    }[normalized] || status || '--'
  )
}

export function formatModuleCode(value) {
  const normalized = normalize(value)
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
    }[normalized] || value || '--'
  )
}

export function formatActionCode(value) {
  const normalized = normalize(value)
  return (
    {
      VIEW: '查看',
      CREATE: '创建',
      UPDATE: '更新',
      ASSIGN: '分配',
      RECYCLE: '回收',
      FINISH: '完结',
      ASSIGN_ROLE: '分配角色',
      TRIGGER: '触发',
      CHECK: '校验'
    }[normalized] || value || '--'
  )
}

export function formatSyncMode(value) {
  const normalized = normalize(value)
  return (
    {
      INCREMENTAL: '增量同步',
      FULL: '全量同步',
      MANUAL: '手动触发'
    }[normalized] || value || '--'
  )
}

export function formatRoleCode(value) {
  const normalized = normalize(value)
  return (
    {
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
    }[normalized] || value || '--'
  )
}

export function formatVerificationStatus(value) {
  const normalized = normalize(value)
  return (
    {
      VERIFIED: '已完成核验',
      UNVERIFIED: '待核验'
    }[normalized] || value || '--'
  )
}

export function formatSettlementMode(value) {
  const normalized = normalize(value)
  return (
    {
      WITHDRAW_AUDIT: '提现审核',
      WITHDRAW_DIRECT: '提现不审核',
      LEDGER_ONLY: '只记账'
    }[normalized] || value || '--'
  )
}

export function formatScope(value) {
  const normalized = normalize(value)
  return (
    {
      SELF: '本人',
      TEAM: '团队',
      STORE: '门店',
      ALL: '全部'
    }[normalized] || value || '--'
  )
}

export function formatPermissionResult(response) {
  if (!response) {
    return '--'
  }
  return response.allowed ? `允许 / ${formatScope(response.dataScope)}` : `拒绝 / ${response.reason || '未命中策略'}`
}

export function statusTagType(value) {
  const normalized = normalize(value)
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
    }[normalized] || 'info'
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
