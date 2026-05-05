import http from './http'

export function fetchFinanceRefundRecords(params = {}) {
  return http.get('/finance/refund-records', { params })
}

export function fetchFinanceLedgerBoundary(options = {}) {
  return http.get('/finance/ledger-boundary', {
    silentError: options.silentError === true
  })
}
