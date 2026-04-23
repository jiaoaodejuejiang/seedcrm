import http from './http'

export function fetchPermissionPolicies() {
  return http.get('/permission/policies')
}

export function savePermissionPolicy(payload) {
  return http.post('/permission/policy/save', payload)
}

export function checkPermission(payload) {
  return http.post('/permission/check', payload)
}
