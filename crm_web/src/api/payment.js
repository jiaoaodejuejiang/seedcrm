import http from './http'

export function testPaymentConfig(payload) {
  return http.post('/payment/config/test', payload)
}
