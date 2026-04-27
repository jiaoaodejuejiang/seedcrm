import http from './http'

export function appointOrder(payload) {
  return http.post('/order/appointment', payload)
}

export function cancelOrderAppointment(payload) {
  return http.post('/order/appointment/cancel', payload)
}

export function verifyOrderVoucher(payload) {
  return http.post('/order/verify', payload)
}

export function saveOrderServiceDetail(payload) {
  return http.post('/order/service-detail', payload)
}

export function refundOrder(payload) {
  return http.post('/order/refund', payload)
}
