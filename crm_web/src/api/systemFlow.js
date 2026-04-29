import http from './http'

export function fetchSystemFlows() {
  return http.get('/system-flow/list')
}

export function fetchSystemFlowDetail(flowCode, versionId) {
  return http.get('/system-flow/detail', {
    params: {
      flowCode: flowCode || undefined,
      versionId: versionId || undefined
    }
  })
}

export function fetchSystemFlowVersions(flowCode) {
  return http.get('/system-flow/versions', {
    params: {
      flowCode: flowCode || undefined
    }
  })
}

export function saveSystemFlowDraft(payload) {
  return http.post('/system-flow/save-draft', payload)
}

export function previewSystemFlowDiff(payload) {
  return http.post('/system-flow/preview-diff', payload)
}

export function fetchSystemFlowValidationReport(flowCode, versionId) {
  return http.get('/system-flow/validation-report', {
    params: {
      flowCode: flowCode || undefined,
      versionId: versionId || undefined
    }
  })
}

export function fetchSystemFlowTriggerLinkageReport(flowCode, versionId) {
  return http.get('/system-flow/trigger-linkage-report', {
    params: {
      flowCode: flowCode || undefined,
      versionId: versionId || undefined
    }
  })
}

export function publishSystemFlow(payload) {
  return http.post('/system-flow/publish', payload)
}

export function disableSystemFlow(payload) {
  return http.post('/system-flow/disable', payload)
}

export function simulateSystemFlow(payload) {
  return http.post('/system-flow/simulate', payload)
}

export function fetchSystemFlowAuditLogs(flowCode) {
  return http.get('/system-flow/audit-logs', {
    params: {
      flowCode: flowCode || undefined
    }
  })
}
