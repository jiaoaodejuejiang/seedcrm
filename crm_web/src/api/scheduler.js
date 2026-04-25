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
