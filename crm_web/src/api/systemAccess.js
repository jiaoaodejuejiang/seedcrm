import http from './http'

export function fetchSystemAccessSnapshot() {
  return http.get('/system-access/snapshot')
}

export function saveSystemAccessMenu(payload) {
  return http.post('/system-access/menu/save', payload)
}

export function saveSystemAccessRole(payload) {
  return http.post('/system-access/role/save', payload)
}
