<template>
  <div class="stack-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>系统基础域名</span>
        <strong>{{ baseDomainStatus }}</strong>
      </article>
      <article class="summary-pill">
        <span>API 域名</span>
        <strong>{{ apiDomainStatus }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>域名配置</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" :loading="saving" @click="saveDomains">保存配置</el-button>
        </div>
      </div>

      <el-alert
        class="domain-alert"
        type="info"
        show-icon
        :closable="false"
        title="API 域名会用于分销入站、支付回调、抖音回调、企微回调和 Swagger/OpenAPI 地址；上线前请确认外网可访问。"
      />

      <div v-loading="loading" class="form-grid">
        <label class="full-span">
          <span>系统基础域名</span>
          <el-input v-model="form.systemBaseUrl" placeholder="例如 https://crm.seedcrm.com" />
        </label>
        <label class="full-span">
          <span>API 域名</span>
          <el-input v-model="form.apiBaseUrl" placeholder="例如 https://api.seedcrm.com" />
        </label>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDomainSettings, saveDomainSettings } from '../api/systemConfig'
import { applyDomainSettingsToLocal } from '../utils/domainSettings'
import { buildSystemUrl, getDomainSettings, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const form = reactive(getDomainSettings(state))
const loading = ref(false)
const saving = ref(false)

const baseDomainStatus = computed(() => (String(form.systemBaseUrl || '').trim() ? '已设置' : '待设置'))
const apiDomainStatus = computed(() => (String(form.apiBaseUrl || '').trim() ? '已设置' : '待设置'))

onMounted(loadDomains)

async function loadDomains() {
  loading.value = true
  try {
    const settings = await fetchDomainSettings()
    const synced = applyDomainSettingsToLocal(settings)
    Object.assign(state, loadSystemConsoleState())
    Object.assign(form, synced)
  } catch (error) {
    // Keep existing local fallback when the backend is offline.
  } finally {
    loading.value = false
  }
}

async function saveDomains() {
  saving.value = true
  try {
    const saved = await saveDomainSettings({
      systemBaseUrl: String(form.systemBaseUrl || '').trim(),
      apiBaseUrl: String(form.apiBaseUrl || '').trim()
    })
    applyDomainSettingsToLocal(saved)
    syncDerivedLocalUrls()
    Object.assign(state, loadSystemConsoleState())
    Object.assign(form, getDomainSettings(state))
    ElMessage.success('域名配置已保存，分销接口页联调地址已同步更新')
  } finally {
    saving.value = false
  }
}

function syncDerivedLocalUrls() {
  const nextState = loadSystemConsoleState()
  nextState.domainSettings = {
    systemBaseUrl: String(form.systemBaseUrl || '').trim(),
    apiBaseUrl: String(form.apiBaseUrl || '').trim()
  }
  if (nextState.paymentSettings?.wechatPay) {
    nextState.paymentSettings.wechatPay.notifyUrl = buildSystemUrl(nextState, 'callback', nextState.paymentSettings.wechatPay.notifyPath)
    nextState.paymentSettings.wechatPay.refundNotifyUrl = buildSystemUrl(
      nextState,
      'callback',
      nextState.paymentSettings.wechatPay.refundNotifyPath
    )
  }
  if (nextState.paymentSettings?.wechatPayout) {
    nextState.paymentSettings.wechatPayout.notifyUrl = buildSystemUrl(nextState, 'callback', nextState.paymentSettings.wechatPayout.notifyPath)
  }
  nextState.callbackApis = (nextState.callbackApis || []).map((item) => ({
    ...item,
    callbackUrl: buildSystemUrl(nextState, 'callback', item.callbackPath || '/api/callback/default')
  }))
  saveSystemConsoleState(nextState)
}
</script>

<style scoped>
.domain-alert {
  margin-bottom: 16px;
  border-radius: 14px;
}
</style>
