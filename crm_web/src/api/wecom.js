import http from './http'

export function sendWecomMessage(payload) {
  return http.post('/wecom/send', payload)
}
