import http from './http'

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
