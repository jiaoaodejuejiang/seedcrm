<template>
  <div class="stack-page">
    <section class="panel compact-panel">
      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="activeTab">
            <el-radio-button value="records">回调记录</el-radio-button>
            <el-radio-button value="debug">回调联调</el-radio-button>
          </el-radio-group>
        </div>
        <div class="action-group">
          <el-button @click="loadLogs">刷新</el-button>
        </div>
      </div>
    </section>

    <section v-if="activeTab === 'records'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>回调记录</h3>
        </div>
        <el-select v-model="providerFilter" clearable placeholder="全部来源" style="width: 220px" @change="loadLogs">
          <el-option label="企业微信" value="WECOM" />
          <el-option label="抖音" value="DOUYIN_LAIKE" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="pagination.rows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.receivedAt) || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="来源接口" min-width="140">
          <template #default="{ row }">
            {{ providerLabel(row.providerCode || row.appCode) }}
          </template>
        </el-table-column>
        <el-table-column label="事件类型" min-width="160" prop="eventType" />
        <el-table-column label="验签" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.signatureStatus)">
              {{ formatCallbackSignatureStatus(row.signatureStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="信任" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.trustLevel)">
              {{ formatCallbackTrustLevel(row.trustLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="幂等" width="120">
          <template #default="{ row }">
            {{ formatCallbackIdempotencyStatus(row.idempotencyStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="策略" width="130">
          <template #default="{ row }">
            {{ formatCallbackProcessPolicy(row.processPolicy) }}
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.processStatus)">{{ formatCallbackProcessStatus(row.processStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理后变化" min-width="260">
          <template #default="{ row }">
            {{ row.processMessage || '已记录回调报文，未触发业务变化' }}
          </template>
        </el-table-column>
        <el-table-column label="TraceId" min-width="220" prop="traceId" />
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="pagination.total"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :page-sizes="pagination.pageSizes"
          @size-change="pagination.handleSizeChange"
          @current-change="pagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <h3>回调联调</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" :loading="debugging" @click="handleDebug">发送模拟回调</el-button>
        </div>
      </div>

      <div class="debug-layout">
        <div class="inline-editor-shell">
          <div class="form-grid">
            <label>
              <span>回调接口</span>
              <el-select v-model="selectedTemplateKey" @change="applyTemplate">
                <el-option v-for="item in callbackTemplates" :key="item.key" :label="item.label" :value="item.key" />
              </el-select>
            </label>
            <label>
              <span>请求方式</span>
              <el-select v-model="debugForm.requestMethod">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
            </label>
            <label>
              <span>来源编码</span>
              <el-input v-model="debugForm.providerCode" />
            </label>
            <label>
              <span>回调名称</span>
              <el-input v-model="debugForm.callbackName" />
            </label>
            <label class="full-span">
              <span>回调路径</span>
              <div class="url-compose">
                <span>{{ apiBaseUrl }}</span>
                <el-input v-model="debugForm.callbackPath" />
              </div>
            </label>
            <label class="full-span">
              <span>Query 参数</span>
              <el-input v-model="debugForm.parametersText" type="textarea" :rows="4" />
            </label>
            <label class="full-span">
              <span>Body 报文</span>
              <el-input v-model="debugForm.payload" type="textarea" :rows="10" />
            </label>
          </div>
        </div>

        <div class="debug-result">
          <h4>联调结果</h4>
          <pre>{{ debugResultText }}</pre>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { debugSchedulerCallback, fetchIntegrationCallbackLogs } from '../api/scheduler'
import { debugWecomCallback, fetchWecomCallbackLogs } from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import {
  formatCallbackIdempotencyStatus,
  formatCallbackProcessStatus,
  formatCallbackProcessPolicy,
  formatCallbackSignatureStatus,
  formatCallbackTrustLevel,
  formatDateTime,
  statusTagType
} from '../utils/format'
import { loadSystemConsoleState } from '../utils/systemConsoleStore'

const systemState = loadSystemConsoleState()
const apiBaseUrl = computed(() => String(systemState.domainSettings?.apiBaseUrl || '').trim() || '--')
const activeTab = ref('records')
const providerFilter = ref('')
const loading = ref(false)
const debugging = ref(false)
const records = ref([])
const debugResult = ref(null)
const pagination = useTablePagination(records)

const callbackTemplates = [
  {
    key: 'wecom-add-contact',
    label: '企业微信客户添加',
    providerCode: 'WECOM',
    callbackName: 'PRIVATE_DOMAIN',
    callbackPath: '/wecom/callback/PRIVATE_DOMAIN',
    parameters: {},
    payload: {
      Event: 'add_external_contact',
      State: 'debug-state',
      ExternalUserID: 'wm_mock_customer',
      UserID: 'store_service'
    }
  },
  {
    key: 'douyin-oauth',
    label: '抖音授权回调',
    providerCode: 'DOUYIN_LAIKE',
    callbackName: '抖音授权回调',
    callbackPath: '/scheduler/oauth/douyin/callback',
    parameters: {
      auth_code: 'mock-auth-code',
      state: 'douyin-auth-state'
    },
    payload: {}
  },
  {
    key: 'douyin-refund',
    label: '抖音退款回调',
    providerCode: 'DOUYIN_LAIKE',
    callbackName: '抖音退款回调',
    callbackPath: '/scheduler/callback/douyin/refund',
    parameters: {},
    payload: {
      event_type: 'refund_status_change',
      order_id: 'mock-order-id',
      refund_id: 'mock-refund-id',
      refund_status: 'SUCCESS'
    }
  },
  {
    key: 'payment-refund',
    label: '支付退款回调',
    providerCode: 'PAYMENT',
    callbackName: '微信支付退款回调',
    callbackPath: '/pay/wechat/refund-notify',
    parameters: {},
    payload: {
      event_type: 'REFUND.SUCCESS',
      out_trade_no: 'mock-order-no',
      out_refund_no: 'mock-refund-no'
    }
  }
]

const selectedTemplateKey = ref(callbackTemplates[0].key)
const debugForm = reactive({
  providerCode: '',
  callbackName: '',
  callbackPath: '',
  requestMethod: 'POST',
  parametersText: '{}',
  payload: '{}'
})

const debugResultText = computed(() => (debugResult.value ? JSON.stringify(debugResult.value, null, 2) : '暂无联调结果'))

onMounted(async () => {
  applyTemplate()
  await loadLogs()
})

async function loadLogs() {
  loading.value = true
  try {
    if (providerFilter.value === 'WECOM') {
      records.value = await fetchWecomCallbackLogs()
    } else if (providerFilter.value) {
      records.value = await fetchIntegrationCallbackLogs(providerFilter.value)
    } else {
      const [wecomLogs, douyinLogs] = await Promise.all([fetchWecomCallbackLogs(), fetchIntegrationCallbackLogs()])
      records.value = [...wecomLogs, ...douyinLogs].sort((left, right) => new Date(right.receivedAt) - new Date(left.receivedAt))
    }
    pagination.reset()
  } finally {
    loading.value = false
  }
}

function applyTemplate() {
  const template = callbackTemplates.find((item) => item.key === selectedTemplateKey.value) || callbackTemplates[0]
  Object.assign(debugForm, {
    providerCode: template.providerCode,
    callbackName: template.callbackName,
    callbackPath: template.callbackPath,
    requestMethod: 'POST',
    parametersText: JSON.stringify(template.parameters, null, 2),
    payload: JSON.stringify(template.payload, null, 2)
  })
  debugResult.value = null
}

async function handleDebug() {
  debugging.value = true
  try {
    const payload = {
      providerCode: debugForm.providerCode,
      callbackName: debugForm.callbackName,
      callbackPath: debugForm.callbackPath,
      requestMethod: debugForm.requestMethod,
      parameters: parseJson(debugForm.parametersText, {}),
      payload: debugForm.payload
    }
    debugResult.value =
      debugForm.providerCode === 'WECOM' ? await debugWecomCallback(payload) : await debugSchedulerCallback(payload)
    providerFilter.value = debugForm.providerCode === 'WECOM' ? 'WECOM' : debugForm.providerCode
    await loadLogs()
    ElMessage.success('回调联调已完成')
  } finally {
    debugging.value = false
  }
}

function parseJson(value, fallback) {
  try {
    return JSON.parse(value || '{}')
  } catch {
    return fallback
  }
}

function providerLabel(value) {
  const code = String(value || '').trim().toUpperCase()
  return (
    {
      WECOM: '企业微信',
      DOUYIN_LAIKE: '抖音',
      PAYMENT: '支付',
      DISTRIBUTION: '分销'
    }[code] || value || '--'
  )
}
</script>

<style scoped>
.debug-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 18px;
}

.url-compose {
  display: grid;
  grid-template-columns: minmax(180px, 0.35fr) minmax(0, 1fr);
  gap: 8px;
}

.url-compose span {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #475569;
  word-break: break-all;
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

@media (max-width: 1100px) {
  .debug-layout,
  .url-compose {
    grid-template-columns: 1fr;
  }
}
</style>
