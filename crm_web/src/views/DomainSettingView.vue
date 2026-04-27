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
          <el-button type="primary" @click="saveDomains">保存配置</el-button>
        </div>
      </div>

      <div class="form-grid">
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
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { buildSystemUrl, getDomainSettings, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const form = reactive(getDomainSettings(state))

const baseDomainStatus = computed(() => (String(form.systemBaseUrl || '').trim() ? '已设置' : '待设置'))
const apiDomainStatus = computed(() => (String(form.apiBaseUrl || '').trim() ? '已设置' : '待设置'))

function saveDomains() {
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
  Object.assign(state, loadSystemConsoleState())
  Object.assign(form, getDomainSettings(state))
  ElMessage.success('域名配置已保存')
}
</script>
