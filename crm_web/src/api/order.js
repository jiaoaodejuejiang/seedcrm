import http from './http'

export function appointOrder(payload) {
  return http.post('/order/appointment', payload)
}

export function saveOrderServiceDetail(payload) {
  return http.post('/order/service-detail', payload)
}
