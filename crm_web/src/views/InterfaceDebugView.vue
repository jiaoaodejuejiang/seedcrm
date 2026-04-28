<template>
  <div class="stack-page">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>接口调试</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" :loading="testing" @click="handleTest">发送测试</el-button>
        </div>
      </div>

      <div class="debug-layout">
        <div class="inline-editor-shell">
          <div class="form-grid">
            <label>
              <span>运行模式</span>
              <el-segmented v-model="form.mode" :options="modeOptions" />
            </label>
            <label>
              <span>接口类型</span>
              <el-select v-model="selectedTemplateKey" @change="applyTemplate">
                <el-option v-for="item in interfaceTemplates" :key="item.key" :label="item.label" :value="item.key" />
              </el-select>
            </label>
            <label>
              <span>平台编码</span>
              <el-input v-model="form.providerCode" />
            </label>
            <label>
              <span>请求方式</span>
              <el-select v-model="form.requestMethod">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
            </label>
            <label class="full-span">
              <span>接口路径</span>
              <el-input v-model="form.path" />
            </label>
            <label class="full-span">
              <span>调试目标</span>
              <span class="readonly-prefix">{{ requestTargetLabel }}</span>
            </label>
            <label class="full-span">
              <span>传递参数</span>
              <el-input v-model="form.payload" type="textarea" :rows="12" />
            </label>
          </div>
        </div>

        <div class="debug-result">
          <h4>返回结果</h4>
          <pre>{{ resultText }}</pre>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { debugSchedulerInterface } from '../api/scheduler'

const modeOptions = [
  { label: '模拟', value: 'MOCK' },
  { label: '真实', value: 'LIVE' }
]

const interfaceTemplates = [
  {
    key: 'DOUYIN_CLUE_PULL',
    label: '抖音客资拉取',
    providerCode: 'DOUYIN_LAIKE',
    requestMethod: 'POST',
    path: '/goodlife/v1/clue/douyin/list/',
    payload: {
      account_id: 'life_account_id',
      start_time: '2026-04-27 00:00:00',
      end_time: '2026-04-27 23:59:59',
      page: 1,
      page_size: 30
    }
  },
  {
    key: 'DOUYIN_VOUCHER_VERIFY',
    label: '抖音券核销',
    providerCode: 'DOUYIN_LAIKE',
    requestMethod: 'POST',
    path: '/goodlife/v1/fulfilment/certificate/verify/',
    payload: {
      encrypted_codes: ['mock-voucher-code'],
      poi_id: 'mock-poi-id'
    }
  },
  {
    key: 'DISTRIBUTION_LEAD_PULL',
    label: '分销客资接口',
    providerCode: 'DISTRIBUTION',
    requestMethod: 'POST',
    path: '/open/distribution/leads',
    payload: {
      app_id: 'distribution-app-id',
      timestamp: '2026-04-27T10:00:00+08:00',
      page: 1,
      page_size: 30,
      sign: 'mock-sign'
    }
  },
  {
    key: 'WECOM_STATE_CALLBACK',
    label: '企微 state 回调',
    providerCode: 'WECOM',
    requestMethod: 'POST',
    path: '/wecom/callback/PRIVATE_DOMAIN',
    payload: {
      Event: 'add_external_contact',
      State: 'sc.order.user.customer.sign',
      ExternalUserID: 'wm_mock_customer',
      UserID: 'store_service'
    }
  }
]

const selectedTemplateKey = ref(interfaceTemplates[0].key)
const testing = ref(false)
const result = ref(null)
const form = reactive({
  mode: 'MOCK',
  interfaceCode: '',
  providerCode: '',
  requestMethod: 'POST',
  path: '',
  payload: ''
})

const resultText = computed(() => (result.value ? JSON.stringify(result.value, null, 2) : '暂无调试结果'))
const requestTargetLabel = computed(() =>
  form.mode === 'LIVE'
    ? `真实模式：将校验 ${form.providerCode || '--'} ${form.requestMethod || 'POST'} ${form.path || '--'}`
    : `模拟模式：仅在系统内生成 ${form.providerCode || '--'} ${form.interfaceCode || '--'} 的模拟响应`
)

applyTemplate()

function applyTemplate() {
  const template = interfaceTemplates.find((item) => item.key === selectedTemplateKey.value) || interfaceTemplates[0]
  Object.assign(form, {
    mode: form.mode || 'MOCK',
    interfaceCode: template.key,
    providerCode: template.providerCode,
    requestMethod: template.requestMethod,
    path: template.path,
    payload: JSON.stringify(template.payload, null, 2)
  })
  result.value = null
}

async function handleTest() {
  if (form.mode === 'LIVE') {
    try {
      await ElMessageBox.confirm(
        '真实模式会按当前接口配置发起联调校验。请确认配置无误后继续；本系统不会在接口调试中直接改写业务数据。',
        '确认真实接口调试',
        {
          confirmButtonText: '确认发送',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }
  testing.value = true
  try {
    result.value = await debugSchedulerInterface({
      mode: form.mode,
      interfaceCode: form.interfaceCode,
      providerCode: form.providerCode,
      requestMethod: form.requestMethod,
      path: form.path,
      payload: form.payload
    })
    ElMessage.success('接口调试完成')
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.debug-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 18px;
}

.debug-result {
  border: 1px solid #e5edf4;
  border-radius: 18px;
  padding: 16px;
  background: #0f172a;
  color: #dbeafe;
  min-height: 320px;
}

.debug-result h4 {
  margin: 0 0 12px;
  color: #ffffff;
}

.debug-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.65;
}

.readonly-prefix {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 0 12px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #475569;
  word-break: break-all;
}

@media (max-width: 1100px) {
  .debug-layout {
    grid-template-columns: 1fr;
  }
}
</style>
