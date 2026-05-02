<template>
  <div class="stack-page">
    <section class="panel interface-debug-panel">
      <div class="panel-heading compact-heading">
        <div>
          <h3>联调工作台</h3>
          <el-tag effect="plain" type="info">仅做联调预检，不写客户、订单、服务单等核心业务表</el-tag>
        </div>
        <div class="action-group">
          <el-button plain @click="openSwagger">查看接口定义</el-button>
          <el-button type="primary" :loading="testing" @click="handleTest">发送预检请求</el-button>
        </div>
      </div>

      <div class="debug-layout">
        <div class="debug-form">
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
            <label class="full-span">
              <span>模板说明</span>
              <span class="readonly-prefix">{{ selectedTemplate?.description || '用于验证接口配置、字段映射和返回结构。' }}</span>
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
              <span>请求头 / 参数</span>
              <el-input v-model="form.parametersText" type="textarea" :rows="6" />
            </label>
            <label class="full-span">
              <span>请求内容</span>
              <el-input v-model="form.payload" type="textarea" :rows="14" />
            </label>
          </div>
        </div>

        <div class="debug-result">
          <div class="result-title">
            <h4>预检结果</h4>
            <el-tag v-if="result" :type="result.success === false ? 'danger' : 'success'" effect="dark">
              {{ result.success === false ? '预检异常' : '预检通过' }}
            </el-tag>
          </div>
          <div v-if="result" class="result-summary">
            <article v-for="item in resultSummary" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </article>
          </div>
          <pre>{{ resultText }}</pre>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { debugSchedulerInterface } from '../api/scheduler'
import { syncDomainSettingsFromBackend } from '../utils/domainSettings'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'

const systemState = reactive(loadSystemConsoleState())

const modeOptions = [
  { label: '模拟', value: 'MOCK' },
  { label: '真实', value: 'LIVE' }
]

const interfaceTemplates = [
  {
    key: 'DISTRIBUTION_ORDER_PAID',
    label: '分销已支付订单入站',
    providerCode: 'DISTRIBUTION',
    requestMethod: 'POST',
    path: '/open/distribution/events',
    parameters: {
      'X-Partner-Code': 'DISTRIBUTION',
      'X-Idempotency-Key': 'idem-debug-001',
      'X-Timestamp': '2026-04-29T10:00:00Z',
      'X-Nonce': 'nonce-debug-001',
      'X-Signature': 'mock-signature'
    },
    payload: {
      eventType: 'distribution.order.paid',
      eventId: 'evt_debug_001',
      partnerCode: 'DISTRIBUTION',
      occurredAt: '2026-04-29T10:00:00+08:00',
      member: {
        externalMemberId: 'm_10001',
        name: '张三',
        phone: '13800000000',
        role: 'member'
      },
      promoter: {
        externalPromoterId: 'p_90001',
        role: 'leader'
      },
      order: {
        externalOrderId: 'o_20001',
        externalTradeNo: 'pay_30001',
        type: 'coupon',
        amount: 19900,
        paidAt: '2026-04-29T09:58:00+08:00',
        storeCode: 'store_001',
        status: 'paid'
      },
      rawData: {}
    }
  },
  {
    key: 'DISTRIBUTION_STATUS_CHECK',
    label: '分销订单状态回查',
    providerCode: 'DISTRIBUTION',
    requestMethod: 'POST',
    path: '/open/distribution/orders/status',
    parameters: {},
    payload: {
      externalOrderId: 'o_20001',
      status: 'refund_success',
      rawData: {
        source: 'scheduler-debug'
      }
    }
  },
  {
    key: 'DISTRIBUTION_RECONCILE_PULL',
    label: '分销对账拉取',
    providerCode: 'DISTRIBUTION',
    requestMethod: 'POST',
    path: '/open/distribution/orders/reconcile',
    parameters: {},
    payload: {
      limit: 20,
      lastSyncTime: '2026-04-29T00:00:00',
      orders: [
        {
          externalOrderId: 'o_20001',
          externalTradeNo: 'pay_30001',
          status: 'paid',
          amount: 19900,
          member: {
            externalMemberId: 'm_10001',
            phone: '13800000000'
          }
        },
        {
          externalOrderId: 'o_20002',
          status: 'refunded',
          refundAmount: 9900
        }
      ]
    }
  },
  {
    key: 'DOUYIN_CLUE_PULL',
    label: '抖音客资拉取',
    providerCode: 'DOUYIN_LAIKE',
    requestMethod: 'POST',
    path: '/open_api/2/tools/clue/life/get/',
    description: '用于验证客资列表固定拉取口径，local_account_ids 必须填写，不填不会全量拉取。',
    successNextAction: '客资拉取配置链路预检通过，再到抖音接口页开启真实调度。',
    failureNextAction: '去抖音接口-线索拉取补齐 local_account_ids、授权和时间窗口配置。',
    parameters: {},
    payload: {
      local_account_ids: [123456789],
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
    description: '用于验证门店“扫码/输码核销抖音团购券”的配置链路；预检通过不代表真实订单已核销。',
    successNextAction: '配置链路预检通过，仍需在门店订单中使用真实券码完成核销。',
    failureNextAction: '去抖音接口-券核销补齐授权、核销路径、POI 和券码字段配置。',
    parameters: {},
    payload: {
      encrypted_codes: ['mock-voucher-code'],
      poi_id: 'mock-poi-id'
    }
  },
  {
    key: 'DISTRIBUTION_VOUCHER_VERIFY',
    label: '分销券核销',
    providerCode: 'DISTRIBUTION',
    requestMethod: 'POST',
    path: '/open/distribution/vouchers/verify',
    description: '用于验证门店“扫码/输码核销分销团购券”的配置链路；失败时本地订单必须保持待核销。',
    successNextAction: '分销核销配置预检通过，仍需在门店订单中用真实券码完成核销。',
    failureNextAction: '去联调工作台或分销接口配置中检查分销 provider、核销路径、签名和返回成功字段。',
    parameters: {
      'X-Partner-Code': 'DISTRIBUTION',
      'X-Idempotency-Key': 'voucher-debug-001'
    },
    payload: {
      voucherCode: 'mock-distribution-voucher-code',
      externalOrderId: 'o_20001'
    }
  },
  {
    key: 'WECOM_STATE_CALLBACK',
    label: '企微 state 回调',
    providerCode: 'WECOM',
    requestMethod: 'POST',
    path: '/wecom/callback/PRIVATE_DOMAIN',
    parameters: {},
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
  parametersText: '{}',
  payload: ''
})

const resultText = computed(() => (result.value ? JSON.stringify(result.value, null, 2) : '暂无预检结果'))
const resultTraceId = computed(() => resolveTraceId(result.value) || '--')
const resultFailureReason = computed(() => {
  if (!result.value) {
    return '--'
  }
  const message = firstNonBlank(result.value.errorMessage, result.value.message, result.value.reason)
  if (result.value.success === false) {
    return message || '请求未通过预检'
  }
  return message || '无异常'
})
const resultNextAction = computed(() => {
  if (!result.value) {
    return '--'
  }
  if (result.value.success === false) {
    return selectedTemplate.value?.failureNextAction || (resultTraceId.value === '--' ? '查看失败原因，修正配置或报文后重试' : '复制追踪编号，去异常队列或回调记录继续处理')
  }
  return selectedTemplate.value?.successNextAction || (form.mode === 'LIVE' ? '本次只完成真实配置预检；实际入站、回推、日志仍由正式接口或调度任务触发' : '核对字段映射无误后，再切换真实模式预检')
})
const resultSummary = computed(() => [
  { label: '结果状态', value: result.value?.success === false ? '预检异常' : '预检通过' },
  { label: '失败原因', value: resultFailureReason.value },
  { label: '追踪编号', value: resultTraceId.value },
  { label: '下一步操作', value: resultNextAction.value }
])
const requestTargetLabel = computed(() =>
  form.mode === 'LIVE'
    ? `真实模式：只校验 ${form.providerCode || '--'} ${form.requestMethod || 'POST'} ${form.path || '--'} 的配置和报文，不写核心业务表`
    : `模拟模式：仅在系统内预检 ${form.providerCode || '--'} ${form.interfaceCode || '--'}，不落业务数据`
)
const selectedTemplate = computed(() => interfaceTemplates.find((item) => item.key === selectedTemplateKey.value) || null)
const swaggerUiUrl = computed(() => buildSystemUrl(systemState, 'api', '/swagger-ui.html'))

applyTemplate()

onMounted(async () => {
  try {
    const domainSettings = await syncDomainSettingsFromBackend()
    systemState.domainSettings = {
      ...(systemState.domainSettings || {}),
      ...domainSettings
    }
  } catch (error) {
    // Keep local fallback values when backend config is unavailable.
  }
})

function applyTemplate() {
  const template = interfaceTemplates.find((item) => item.key === selectedTemplateKey.value) || interfaceTemplates[0]
  Object.assign(form, {
    mode: form.mode || 'MOCK',
    interfaceCode: template.key,
    providerCode: template.providerCode,
    requestMethod: template.requestMethod,
    path: template.path,
    parametersText: JSON.stringify(template.parameters || {}, null, 2),
    payload: JSON.stringify(template.payload, null, 2)
  })
  result.value = null
}

function parseJsonObject(text, fieldName) {
  if (!text || !text.trim()) {
    return {}
  }
  try {
    const parsed = JSON.parse(text)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error(`${fieldName}必须是 JSON 对象`)
    }
    return parsed
  } catch (error) {
    throw new Error(`${fieldName}格式不是有效 JSON：${error.message}`)
  }
}

async function handleTest() {
  let parameters
  try {
    parameters = parseJsonObject(form.parametersText, '请求头 / 参数')
    JSON.parse(form.payload || '{}')
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  if (form.mode === 'LIVE') {
    try {
      await ElMessageBox.confirm(
        '真实模式只做配置和报文预检，不写客户、订单、服务单等核心业务表；请确认密钥、请求头和参数无误。',
        '确认真实预检',
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
      parameters,
      payload: form.payload
    })
    ElMessage.success('联调预检已完成')
  } finally {
    testing.value = false
  }
}

function openSwagger() {
  window.open(swaggerUiUrl.value, '_blank', 'noopener,noreferrer')
}

function firstNonBlank(...values) {
  return values.find((value) => String(value || '').trim()) || ''
}

function resolveTraceId(data) {
  if (!data || typeof data !== 'object') {
    return ''
  }
  return firstNonBlank(
    data.traceId,
    data.requestTraceId,
    data.callbackLogTraceId,
    data.mockData?.traceId,
    data.latestLog?.traceId,
    data.latestLog?.requestId,
    data.fieldMapping?.traceId,
    data.orderIdempotency?.traceId
  )
}
</script>

<style scoped>
.interface-debug-panel {
  overflow: hidden;
}

.compact-heading {
  align-items: center;
}

.debug-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(380px, 0.95fr);
  gap: 18px;
}

.debug-form {
  border: 1px solid #e5edf4;
  border-radius: 18px;
  padding: 16px;
  background: #ffffff;
}

.debug-result {
  border: 1px solid #0f172a;
  border-radius: 18px;
  padding: 16px;
  background:
    radial-gradient(circle at 15% 10%, rgba(56, 189, 248, 0.18), transparent 26%),
    #0f172a;
  color: #dbeafe;
  min-height: 420px;
}

.result-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.debug-result h4 {
  margin: 0;
  color: #ffffff;
}

.debug-result pre {
  margin: 0;
  max-height: 680px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.65;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.result-summary article {
  display: grid;
  gap: 4px;
  padding: 11px 12px;
  border: 1px solid rgba(191, 219, 254, 0.22);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.72);
}

.result-summary span {
  color: #93c5fd;
  font-size: 12px;
}

.result-summary strong {
  color: #f8fafc;
  font-size: 13px;
  line-height: 1.45;
  word-break: break-word;
}

.readonly-prefix {
  display: inline-flex;
  min-height: 34px;
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

  .result-summary {
    grid-template-columns: 1fr;
  }
}
</style>
