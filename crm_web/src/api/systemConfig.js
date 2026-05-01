import http from './http'

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
