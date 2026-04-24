import http from './http'

export function sendWecomMessage(payload) {
  return http.post('/wecom/send', payload)
}

export function generateWecomLiveCode(payload) {
  return http.post('/wecom/live-code/generate', payload)
}
