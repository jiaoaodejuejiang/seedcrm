import http from './http'

export function fetchFinanceRefundRecords(params = {}) {
  return http.get('/finance/refund-records', { params })
}
