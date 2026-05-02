import http from './http'

export function fetchClues(params) {
  return http.get('/workbench/clues', { params })
}

export function fetchCluePage(params) {
  return http.get('/workbench/clues/page', { params })
}

export function fetchClueSyncStatus() {
  return http.get('/workbench/clues/sync-status')
}

export function fetchOrders(params) {
  return http.get('/workbench/orders', { params })
}

export function fetchOrderWecomLiveCode(orderId) {
  return http.get(`/workbench/orders/${orderId}/wecom-live-code`)
}

export function fetchPlanOrders(params) {
  return http.get('/workbench/plan-orders', { params })
}

export function fetchPlanOrderDetail(planOrderId) {
  return http.get(`/workbench/plan-orders/${planOrderId}`)
}

export function fetchCustomerDetail(customerId) {
  return http.get(`/workbench/customers/${customerId}`)
}

export function fetchDistributors() {
  return http.get('/workbench/distributors')
}

export function fetchFinanceOverview() {
  return http.get('/workbench/finance-overview')
}

export function fetchStaffOptions() {
  return http.get('/workbench/staff-options')
}
