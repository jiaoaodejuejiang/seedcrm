import http from './http'

export function fetchAssignmentStrategy() {
  return http.get('/clue-management/assignment-strategy')
}

export function saveAssignmentStrategy(payload) {
  return http.post('/clue-management/assignment-strategy', payload)
}

export function fetchDedupConfig(options = {}) {
  return http.get('/clue-management/dedup-config', {
    silentError: options.silentError === true
  })
}

export function saveDedupConfig(payload, options = {}) {
  return http.post('/clue-management/dedup-config', payload, {
    silentError: options.silentError === true
  })
}

export function fetchDutyCustomerServices() {
  return http.get('/clue-management/duty-cs')
}

export function saveDutyCustomerServices(payload) {
  return http.post('/clue-management/duty-cs', payload)
}
