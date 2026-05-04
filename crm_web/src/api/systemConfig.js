import http from './http'

const STORE_SCHEDULE_CONFIG_KEY = 'store.schedule.configs'

export function fetchSystemConfigs(prefix) {
  return http.get('/system-config/list', {
    params: {
      prefix: prefix || undefined
    }
  })
}

export function saveSystemConfig(payload) {
  return http.post('/system-config/save', payload)
}

export async function fetchStoreScheduleConfigs() {
  const rows = await fetchSystemConfigs(STORE_SCHEDULE_CONFIG_KEY)
  const config = Array.isArray(rows) ? rows.find((item) => item.configKey === STORE_SCHEDULE_CONFIG_KEY) : null
  if (!config?.configValue) {
    return []
  }
  const parsed = JSON.parse(config.configValue)
  return Array.isArray(parsed) ? parsed : []
}

export function saveStoreScheduleConfigs(configs) {
  return saveSystemConfig({
    configKey: STORE_SCHEDULE_CONFIG_KEY,
    configValue: JSON.stringify(Array.isArray(configs) ? configs : []),
    valueType: 'JSON',
    summary: '更新门店档期配置',
    description: '门店档期配置，用于顾客排档和门店日历'
  })
}

export function previewSystemConfig(payload) {
  return http.post('/system-config/preview', payload)
}

export function fetchSystemConfigChangeLogs(params = {}) {
  return http.get('/system-config/change-logs', {
    params: {
      prefix: params.prefix || undefined,
      configKey: params.configKey || undefined,
      limit: params.limit || undefined
    }
  })
}

export function fetchSystemConfigDrafts(params = {}) {
  return http.get('/system-config/drafts', {
    params: {
      status: params.status || undefined,
      limit: params.limit || undefined
    }
  })
}

export function fetchSystemConfigDraft(draftNo) {
  return http.get(`/system-config/drafts/${encodeURIComponent(draftNo)}`)
}

export function fetchSystemConfigCapabilities() {
  return http.get('/system-config/capabilities')
}

export function fetchSystemConfigRuntimeOverview() {
  return http.get('/system-config/capabilities/runtime-overview')
}

export function createSystemConfigDraft(payload) {
  return http.post('/system-config/drafts', payload)
}

export function validateSystemConfigDraft(draftNo) {
  return http.post(`/system-config/drafts/${encodeURIComponent(draftNo)}/validate`)
}

export function dryRunSystemConfigDraft(draftNo) {
  return http.post(`/system-config/drafts/${encodeURIComponent(draftNo)}/dry-run`)
}

export function publishSystemConfigDraft(draftNo) {
  return http.post(`/system-config/drafts/${encodeURIComponent(draftNo)}/publish`)
}

export function discardSystemConfigDraft(draftNo) {
  return http.post(`/system-config/drafts/${encodeURIComponent(draftNo)}/discard`)
}

export function fetchSystemConfigPublishRecords(params = {}) {
  return http.get('/system-config/publish-records', {
    params: {
      limit: params.limit || undefined
    }
  })
}

export function fetchSystemConfigPublishRecord(publishNo) {
  return http.get(`/system-config/publish-records/${encodeURIComponent(publishNo)}`)
}

export function refreshSystemConfigRuntime(publishNo) {
  return http.post(`/system-config/publish-records/${encodeURIComponent(publishNo)}/runtime-refresh`)
}

export function processSystemConfigRuntimeEvents(publishNo) {
  return http.post(`/system-config/publish-records/${encodeURIComponent(publishNo)}/runtime-events/process`)
}

export function rollbackPreviewSystemConfig(changeLogId) {
  return http.post(`/system-config/change-logs/${changeLogId}/rollback-preview`)
}

export function createRollbackSystemConfigDraft(changeLogId) {
  return http.post(`/system-config/change-logs/${changeLogId}/rollback-draft`)
}

export function fetchDomainSettings() {
  return http.get('/system-config/domain-settings')
}

export function saveDomainSettings(payload) {
  return http.post('/system-config/domain-settings', payload)
}

export function fetchGoLiveSummary() {
  return http.get('/system-config/go-live/summary')
}

export function initializeGoLive(payload) {
  return http.post('/system-config/go-live/initialize', payload)
}

export function clearGoLiveTestData(payload) {
  return http.post('/system-config/go-live/clear-test-data', payload)
}
