import http from './http'

export function fetchWecomConfig() {
  return http.get('/wecom/config')
}

export function saveWecomConfig(payload) {
  return http.post('/wecom/config/save', payload)
}

export function testWecomConfig(payload) {
  return http.post('/wecom/config/test', payload)
}

export function fetchWecomRules() {
  return http.get('/wecom/rules')
}

export function saveWecomRule(payload) {
  return http.post('/wecom/rule/save', payload)
}

export function toggleWecomRule(ruleId) {
  return http.post('/wecom/rule/toggle', null, {
    params: {
      ruleId
    }
  })
}

export function fetchWecomLogs() {
  return http.get('/wecom/logs')
}

export function fetchWecomCallbackLogs(appCode) {
  return http.get('/wecom/callback/logs', {
    params: {
      appCode: appCode || undefined
    }
  })
}

export function debugWecomCallback(payload) {
  return http.post('/wecom/callback/debug', payload)
}

export function sendWecomMessage(payload) {
  return http.post('/wecom/send', payload)
}

export function fetchWecomLiveCodeConfigs() {
  return http.get('/wecom/live-code/configs')
}

export function saveWecomLiveCodeConfig(payload) {
  return http.post('/wecom/live-code/config/save', payload)
}

export function generateWecomLiveCode(payload) {
  return http.post('/wecom/live-code/generate', payload)
}

export function publishWecomLiveCode(payload) {
  return http.post('/wecom/live-code/publish', payload)
}
