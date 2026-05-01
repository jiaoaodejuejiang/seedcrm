import { fetchDomainSettings } from '../api/systemConfig'
import { getDomainSettings, loadSystemConsoleState, saveSystemConsoleState } from './systemConsoleStore'

export function applyDomainSettingsToLocal(settings = {}) {
  const state = loadSystemConsoleState()
  const current = getDomainSettings(state)
  state.domainSettings = {
    ...current,
    systemBaseUrl: String(settings.systemBaseUrl || current.systemBaseUrl || '').trim(),
    apiBaseUrl: String(settings.apiBaseUrl || current.apiBaseUrl || '').trim()
  }
  saveSystemConsoleState(state)
  return state.domainSettings
}

export async function syncDomainSettingsFromBackend() {
  const settings = await fetchDomainSettings()
  return applyDomainSettingsToLocal(settings)
}
