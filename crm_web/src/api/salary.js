import http from './http'

export function fetchSalaryStat(userId) {
  return http.get('/salary/stat', {
    params: {
      userId
    }
  })
}

export function fetchSalaryBalance(userId) {
  return http.get('/salary/balance', {
    params: {
      userId
    }
  })
}

export function fetchSalaryWithdrawable(userId) {
  return http.get('/salary/withdrawable', {
    params: {
      userId
    }
  })
}

export function fetchSalarySettlements(userId) {
  return http.get('/salary/settlements', {
    params: {
      userId
    }
  })
}

export function fetchSalaryWithdraws(userId) {
  return http.get('/salary/withdraws', {
    params: {
      userId
    }
  })
}

export function recalculateSalary(payload) {
  return http.post('/salary/recalculate', payload)
}

export function createSalarySettlement(payload) {
  return http.post('/salary/settlement/create', payload)
}

export function confirmSalarySettlement(payload) {
  return http.post('/salary/settlement/confirm', payload)
}

export function paySalarySettlement(payload) {
  return http.post('/salary/settlement/pay', payload)
}

export function createSalaryWithdraw(payload) {
  return http.post('/salary/withdraw', payload)
}

export function approveSalaryWithdraw(payload) {
  return http.post('/salary/withdraw/approve', payload)
}
