import http from './http'

export function createClue(payload) {
  return http.post('/clue/add', payload)
}

export function assignClue(payload) {
  return http.post('/clue/assign', payload)
}

export function createOrder(payload) {
  return http.post('/order/create', payload)
}

export function createPlanOrder(payload) {
  return http.post('/planOrder/create', payload)
}

export function arrivePlanOrder(payload) {
  return http.post('/planOrder/arrive', payload)
}

export function startPlanOrder(payload) {
  return http.post('/planOrder/start', payload)
}

export function finishPlanOrder(payload) {
  return http.post('/planOrder/finish', payload)
}

export function assignPlanOrderRole(payload) {
  return http.post('/planOrder/assignRole', payload)
}

export function sendWecomMessage(payload) {
  return http.post('/wecom/send', payload)
}
