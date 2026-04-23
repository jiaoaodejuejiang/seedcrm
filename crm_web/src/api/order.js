import http from './http'

export function saveOrderServiceDetail(payload) {
  return http.post('/order/service-detail', payload)
}
