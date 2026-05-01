import http from './http'

export function createClue(payload) {
  return http.post('/clue/add', payload)
}

export function createDistributionClue(payload) {
  return http.post('/clue/distribution/add', payload)
}

export function assignClue(payload) {
  return http.post('/clue/assign', payload)
}

export function recycleClue(clueId) {
  return http.post('/clue/recycle', null, {
    params: {
      clueId
    }
  })
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

export function confirmPlanOrderServiceForm(payload) {
  return http.post('/planOrder/confirm-service-form', payload)
}

export function printPlanOrderServiceForm(payload) {
  return http.post('/planOrder/print-service-form', payload)
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

export function sendPlanOrderServiceForm(payload) {
  return http.post('/planOrder/send-service-form', payload)
}
