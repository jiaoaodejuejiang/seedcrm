export function formatMoney(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

function normalize(value) {
  return value ? String(value).trim().toUpperCase() : ''
}

export function formatDateTime(value) {
  if (!value) {
    return '--'
  }

  const date = new Date(value)
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
      DISTRIBUTOR: '分销',
      OTHER: '其他'
    }[normalized] || value || '--'
  )
}

export function formatOrderStage(status) {
  const normalized = normalize(status)
  if (!normalized) {
    return '--'
  }

  if (['CREATED', 'PAID_DEPOSIT', 'APPOINTMENT'].includes(normalized)) {
    return '待到店'
  }

  if (['ARRIVED', 'SERVING'].includes(normalized)) {
    return '服务中'
  }

  if (normalized === 'COMPLETED') {
    return '已完成'
  }

  if (normalized === 'CANCELLED') {
    return '已取消'
  }

  if (normalized === 'REFUNDED') {
    return '已退款'
  }

  return status
}

export function formatClueStatus(status) {
  const normalized = normalize(status)
  return (
    {
      NEW: '未跟进',
      ASSIGNED: '已认领',
      FOLLOWING: '跟进中',
      CONVERTED: '已转订单'
    }[normalized] || status || '--'
  )
}

export function formatPlanOrderStatus(status) {
  const normalized = normalize(status)
  return (
    {
      ARRIVED: '待到店',
      SERVICING: '服务中',
      FINISHED: '已完成'
    }[normalized] || status || '--'
  )
}

export function statusTagType(value) {
  const normalized = normalize(value)
  return {
    CREATED: 'warning',
    PAID_DEPOSIT: 'warning',
    APPOINTMENT: 'warning',
    ARRIVED: 'primary',
    SERVING: 'primary',
    COMPLETED: 'success',
    FINISHED: 'success',
    ASSIGNED: 'primary',
    CONVERTED: 'success',
    CANCELLED: 'info',
    REFUNDED: 'danger',
    FAIL: 'danger',
    SUCCESS: 'success',
    ACTIVE: 'success'
  }[normalized]
}
