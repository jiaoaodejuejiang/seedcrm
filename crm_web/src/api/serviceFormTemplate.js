import http from './http'

export function fetchServiceFormTemplates() {
  return http.get('/planOrder/service-form-templates/templates')
}

export function fetchServiceFormBindings(params = {}) {
  return http.get('/planOrder/service-form-templates/bindings', { params })
}

export function previewServiceFormTemplate(params = {}) {
  return http.get('/planOrder/service-form-templates/preview', { params })
}

export function saveServiceFormTemplateDraft(payload) {
  return http.post('/planOrder/service-form-templates/templates/save-draft', payload)
}

export function publishServiceFormTemplate(payload) {
  return http.post('/planOrder/service-form-templates/templates/publish', payload)
}

export function disableServiceFormTemplate(payload) {
  return http.post('/planOrder/service-form-templates/templates/disable', payload)
}

export function saveServiceFormBinding(payload) {
  return http.post('/planOrder/service-form-templates/bindings/save', payload)
}

export function disableServiceFormBinding(payload) {
  return http.post('/planOrder/service-form-templates/bindings/disable', payload)
}
