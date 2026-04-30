import http from './http'

export function fetchIntegrationProviders() {
  return http.get('/scheduler/providers')
}

export function saveIntegrationProvider(payload) {
  return http.post('/scheduler/provider/save', payload)
}

export function testIntegrationProvider(payload) {
  return http.post('/scheduler/provider/test', payload)
}

export function fetchIntegrationCallbacks() {
  return http.get('/scheduler/callbacks')
}

export function fetchIntegrationCallbackLogs(providerCode) {
  return http.get('/scheduler/callback/logs', {
    params: {
      providerCode: providerCode || undefined
    }
  })
}

export function fetchSchedulerIdempotencyHealth(providerCode) {
  return http.get('/scheduler/idempotency-health', {
    params: {
      providerCode: providerCode || undefined
    }
  })
}

export function fetchSchedulerMonitorSummary(providerCode) {
  return http.get('/scheduler/monitor/summary', {
    params: {
      providerCode: providerCode || undefined
    }
  })
}

export function saveIntegrationCallback(payload) {
  return http.post('/scheduler/callback/save', payload)
}

export function fetchSchedulerJobs() {
  return http.get('/scheduler/jobs')
}

export function fetchSchedulerLogs(jobCode) {
  return http.get('/scheduler/logs', {
    params: {
      jobCode: jobCode || undefined
    }
  })
}

export function fetchSchedulerAuditLogs(jobCode) {
  return http.get('/scheduler/audit-logs', {
    params: {
      jobCode: jobCode || undefined
    }
  })
}

export function saveSchedulerJob(payload) {
  return http.post('/scheduler/job/save', payload)
}

export function triggerSchedulerJob(payload) {
  return http.post('/scheduler/trigger', payload)
}

export function retrySchedulerJob(jobCode) {
  return http.post('/scheduler/retry', null, {
    params: {
      jobCode
    }
  })
}

export function retrySchedulerLog(logId) {
  return http.post('/scheduler/retry-log', null, {
    params: {
      logId
    }
  })
}

export function debugSchedulerCallback(payload) {
  return http.post('/scheduler/callback/debug', payload)
}

export function debugSchedulerInterface(payload) {
  return http.post('/scheduler/interface/debug', payload)
}

export function fetchSchedulerOutboxEvents(status) {
  return http.get('/scheduler/outbox/events', {
    params: {
      status: status || undefined
    }
  })
}

export function retrySchedulerOutboxEvent(id, remark) {
  return http.post('/scheduler/outbox/retry', {
    id,
    remark
  })
}

export function processSchedulerOutbox(limit = 20) {
  return http.post('/scheduler/outbox/process', null, {
    params: {
      limit
    }
  })
}

export function fetchDistributionExceptions(status) {
  return http.get('/scheduler/distribution/exceptions', {
    params: {
      status: status || undefined
    }
  })
}

export function retryDistributionException(id, remark) {
  return http.post('/scheduler/distribution/exceptions/retry', {
    id,
    remark
  })
}

export function markDistributionExceptionHandled(id, remark) {
  return http.post('/scheduler/distribution/exceptions/handled', {
    id,
    remark
  })
}

export function processDistributionExceptionRetries(limit = 10) {
  return http.post('/scheduler/distribution/exceptions/process', null, {
    params: {
      limit
    }
  })
}

export function processDistributionStatusCheck(limit = 20) {
  return http.post('/scheduler/distribution/status-check/process', null, {
    params: {
      limit
    }
  })
}

export function processDistributionReconciliation(limit = 20) {
  return http.post('/scheduler/distribution/reconcile/process', null, {
    params: {
      limit
    }
  })
}
