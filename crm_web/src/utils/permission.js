export const roleOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '在线客服', value: 'ONLINE_CUSTOMER_SERVICE' },
  { label: '线索主管', value: 'CLUE_MANAGER' },
  { label: '门店服务', value: 'STORE_SERVICE' },
  { label: '财务', value: 'FINANCE' },
  { label: '私域服务', value: 'PRIVATE_DOMAIN_SERVICE' }
]

export const scopeOptions = [
  { label: '本人', value: 'SELF' },
  { label: '团队', value: 'TEAM' },
  { label: '门店', value: 'STORE' },
  { label: '全部', value: 'ALL' }
]

export function parseTeamMemberIds(text = '') {
  return String(text || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item))
}
