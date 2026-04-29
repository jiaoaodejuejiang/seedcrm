import http from './http'

export function fetchMembers(params) {
  return http.get('/members', { params })
}
