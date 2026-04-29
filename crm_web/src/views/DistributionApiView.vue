<template>
  <div class="stack-page">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>分销接口</h3>
        </div>
        <div class="action-group">
          <el-button @click="loadActiveQueue">刷新当前页</el-button>
          <el-button type="primary" @click="saveConfig">保存配置</el-button>
          <el-button :loading="testing" @click="testConfig">测试入站映射</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="接入配置" name="config">
          <div class="form-grid">
            <div class="full-span form-group-title">应用身份</div>
            <label>
              <span>启用状态</span>
              <el-select v-model="config.enabled">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>运行模式</span>
              <el-select v-model="config.executionMode">
                <el-option label="模拟" value="MOCK" />
                <el-option label="真实" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="config.appId" placeholder="外部分销系统 AppId" />
            </label>
            <label>
              <span>AppSecret</span>
              <el-input v-model="config.appSecret" type="password" show-password placeholder="验签与回推签名密钥" />
            </label>

            <div class="full-span form-group-title">接口地址</div>
            <label class="full-span">
              <span>已支付订单入站地址</span>
              <span class="readonly-prefix">{{ eventIngestUrl }}</span>
            </label>
            <label class="full-span">
              <span>履约状态回推目标</span>
              <el-input v-model="config.fulfillmentCallbackUrl" placeholder="https://partner.example.com/open/crm/fulfillment" />
            </label>
            <label>
              <span>认证方式</span>
              <el-select v-model="config.authMode">
                <el-option label="签名 + 幂等键" value="SIGN_TOKEN" />
                <el-option label="AppId + Secret" value="APP_SECRET" />
              </el-select>
            </label>
            <label>
              <span>入站路径</span>
              <el-input v-model="config.eventIngestPath" placeholder="/open/distribution/events" />
            </label>
            <label>
              <span>限流策略</span>
              <el-input v-model="config.rateLimit" placeholder="例如 60 次/分钟" />
            </label>
            <label>
              <span>缓存策略</span>
              <el-input v-model="config.cachePolicy" placeholder="例如 30 秒缓存" />
            </label>
          </div>

          <div class="endpoint-preview">
            <article>
              <span>本系统接收入站</span>
              <strong>{{ eventIngestUrl }}</strong>
            </article>
            <article>
              <span>Outbox 异步回推</span>
              <strong>{{ config.fulfillmentCallbackUrl || '待配置外部分销系统回调地址' }}</strong>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="字段规范" name="fields">
          <el-table :data="config.fields" stripe>
            <el-table-column label="字段名" min-width="180" prop="fieldName" />
            <el-table-column label="来源" min-width="220" prop="source" />
            <el-table-column label="说明" min-width="280" prop="description" />
            <el-table-column label="是否必填" width="110">
              <template #default="{ row }">
                <el-tag :type="row.required ? 'success' : 'info'">{{ row.required ? '必填' : '可选' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="履约回推队列" name="outbox">
          <div class="queue-toolbar">
            <el-select v-model="outboxStatus" clearable placeholder="按状态筛选" style="width: 180px" @change="loadOutboxEvents">
              <el-option label="待推送" value="PENDING" />
              <el-option label="推送中" value="PROCESSING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="死信" value="DEAD_LETTER" />
            </el-select>
            <el-button :loading="processingOutbox" type="primary" @click="handleProcessOutbox">立即处理队列</el-button>
            <el-button @click="loadOutboxEvents">刷新</el-button>
          </div>

          <el-table v-loading="outboxLoading" :data="outboxEvents" stripe>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="事件类型" min-width="150" prop="eventType" />
            <el-table-column label="外部订单" min-width="160" prop="externalOrderId" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="queueStatusTag(row.status)">{{ formatQueueStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="重试次数" width="100" prop="retryCount" />
            <el-table-column label="下次重试" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.nextRetryTime) }}</template>
            </el-table-column>
            <el-table-column label="推送地址" min-width="260" prop="destinationUrl" show-overflow-tooltip />
            <el-table-column label="最近错误" min-width="260" prop="lastError" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="String(row.status).toUpperCase() !== 'SUCCESS'"
                  size="small"
                  plain
                  :loading="retryingOutboxId === row.id"
                  @click="handleRetryOutbox(row)"
                >
                  重新入队
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="异常队列" name="exceptions">
          <div class="queue-toolbar">
            <el-select v-model="exceptionStatus" clearable placeholder="按状态筛选" style="width: 180px" @change="loadDistributionExceptions">
              <el-option label="待处理" value="OPEN" />
              <el-option label="已重新入队" value="RETRY_QUEUED" />
              <el-option label="已处理" value="HANDLED" />
            </el-select>
            <el-button :loading="processingExceptions" type="primary" @click="handleProcessExceptions">处理重试队列</el-button>
            <el-button @click="loadDistributionExceptions">刷新</el-button>
          </div>

          <el-table v-loading="exceptionLoading" :data="distributionExceptions" stripe>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="事件类型" min-width="190" prop="eventType" />
            <el-table-column label="外部订单" min-width="150" prop="externalOrderId" />
            <el-table-column label="会员手机号" min-width="130" prop="phone" />
            <el-table-column label="错误码" min-width="160" prop="errorCode" />
            <el-table-column label="处理状态" width="120">
              <template #default="{ row }">
                <el-tag :type="exceptionStatusTag(row.handlingStatus)">{{ formatExceptionStatus(row.handlingStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="错误信息" min-width="300" prop="errorMessage" show-overflow-tooltip />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button
                    v-if="String(row.handlingStatus).toUpperCase() !== 'HANDLED'"
                    size="small"
                    plain
                    :loading="retryingExceptionId === row.id"
                    @click="handleRetryException(row)"
                  >
                    重新入队
                  </el-button>
                  <el-button
                    v-if="String(row.handlingStatus).toUpperCase() !== 'HANDLED'"
                    size="small"
                    type="success"
                    plain
                    :loading="handlingExceptionId === row.id"
                    @click="handleMarkHandled(row)"
                  >
                    标记处理
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  debugSchedulerInterface,
  fetchDistributionExceptions,
  fetchSchedulerOutboxEvents,
  markDistributionExceptionHandled,
  processDistributionExceptionRetries,
  processSchedulerOutbox,
  retryDistributionException,
  retrySchedulerOutboxEvent,
  saveIntegrationProvider
} from '../api/scheduler'
import { formatDateTime } from '../utils/format'
import { buildSystemUrl, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const activeTab = ref('config')
const testing = ref(false)
const outboxLoading = ref(false)
const exceptionLoading = ref(false)
const processingOutbox = ref(false)
const processingExceptions = ref(false)
const retryingOutboxId = ref(null)
const retryingExceptionId = ref(null)
const handlingExceptionId = ref(null)
const outboxStatus = ref('')
const exceptionStatus = ref('')
const outboxEvents = ref([])
const distributionExceptions = ref([])
const state = reactive(loadSystemConsoleState())
const config = reactive({
  enabled: state.distributionApi?.enabled ?? 1,
  executionMode: state.distributionApi?.executionMode || 'MOCK',
  appId: state.distributionApi?.appId || '',
  appSecret: state.distributionApi?.appSecret || '',
  authMode: state.distributionApi?.authMode || 'SIGN_TOKEN',
  eventIngestPath: state.distributionApi?.eventIngestPath || '/open/distribution/events',
  fulfillmentCallbackUrl:
    state.distributionApi?.fulfillmentCallbackUrl ||
    state.distributionApi?.callbackUrl ||
    state.distributionApi?.fulfillmentPushUrl ||
    '',
  rateLimit: state.distributionApi?.rateLimit || '60 次/分钟',
  cachePolicy: state.distributionApi?.cachePolicy || '30 秒缓存',
  fields: state.distributionApi?.fields || [
    { fieldName: 'eventType', source: '固定 distribution.order.paid', description: '只有已支付订单允许创建或匹配 Customer + Order(paid)', required: true },
    { fieldName: 'eventId', source: '外部分销事件 ID', description: '用于日志追踪与重复事件识别', required: true },
    { fieldName: 'member.phone', source: '购买会员手机号', description: '用于匹配 Customer，手机号不能作为订单幂等键', required: true },
    { fieldName: 'member.externalMemberId', source: '外部会员 ID', description: '与 partnerCode 共同作为会员身份标识', required: true },
    { fieldName: 'order.externalOrderId', source: '外部订单 ID', description: '与 partnerCode 共同作为订单幂等键', required: true },
    { fieldName: 'order.amount', source: '已支付金额，单位分', description: '入库后转换为 Order 金额，订单状态为已支付', required: true },
    { fieldName: 'rawData', source: '三方原始报文', description: '外部数据必须完整保留，便于追踪、补偿和对账', required: true }
  ]
})

const apiBaseUrl = computed(() => String(state.domainSettings?.apiBaseUrl || '').trim() || '--')
const eventIngestUrl = computed(() => buildSystemUrl(state, 'api', config.eventIngestPath))

watch(activeTab, (tab) => {
  if (tab === 'outbox' && outboxEvents.value.length === 0) {
    loadOutboxEvents()
  }
  if (tab === 'exceptions' && distributionExceptions.value.length === 0) {
    loadDistributionExceptions()
  }
})

function loadActiveQueue() {
  if (activeTab.value === 'outbox') {
    return loadOutboxEvents()
  }
  if (activeTab.value === 'exceptions') {
    return loadDistributionExceptions()
  }
  return Promise.resolve()
}

async function saveConfig() {
  const nextState = loadSystemConsoleState()
  nextState.distributionApi = JSON.parse(JSON.stringify(config))
  saveSystemConsoleState(nextState)
  Object.assign(state, nextState)
  await saveIntegrationProvider({
    providerCode: 'DISTRIBUTION',
    providerName: '外部分销系统',
    moduleCode: 'SCHEDULER',
    executionMode: config.executionMode,
    authType: config.authMode,
    appId: config.appId,
    clientSecret: config.appSecret,
    endpointPath: config.eventIngestPath,
    callbackUrl: config.fulfillmentCallbackUrl,
    enabled: config.enabled,
    remark: `方案B：外部分销已支付订单入站；SeedCRM 只负责预约排档、门店履约和履约状态回推；限流：${config.rateLimit || '--'}；缓存：${config.cachePolicy || '--'}`
  })
  ElMessage.success('分销接口配置已保存')
}

async function testConfig() {
  testing.value = true
  try {
    await debugSchedulerInterface({
      mode: config.executionMode,
      providerCode: 'DISTRIBUTION',
      interfaceCode: 'DISTRIBUTION_ORDER_PAID',
      requestMethod: 'POST',
      path: config.eventIngestPath,
      payload: JSON.stringify({
        eventType: 'distribution.order.paid',
        eventId: 'evt_debug_001',
        partnerCode: 'DISTRIBUTION',
        member: {
          externalMemberId: 'm_debug_001',
          name: '测试会员',
          phone: '13800000000',
          role: 'member'
        },
        order: {
          externalOrderId: 'o_debug_001',
          externalTradeNo: 'pay_debug_001',
          type: 'coupon',
          amount: 19900,
          paidAt: new Date().toISOString(),
          status: 'paid'
        },
        rawData: {}
      })
    })
    ElMessage.success('分销入站 dry-run 测试完成，未写入业务表')
  } finally {
    testing.value = false
  }
}

async function loadOutboxEvents() {
  outboxLoading.value = true
  try {
    outboxEvents.value = await fetchSchedulerOutboxEvents(outboxStatus.value || undefined)
  } finally {
    outboxLoading.value = false
  }
}

async function handleProcessOutbox() {
  processingOutbox.value = true
  try {
    await processSchedulerOutbox(20)
    ElMessage.success('已触发 Outbox 队列处理')
    await loadOutboxEvents()
  } finally {
    processingOutbox.value = false
  }
}

async function handleRetryOutbox(row) {
  retryingOutboxId.value = row.id
  try {
    await retrySchedulerOutboxEvent(row.id, 'manual retry from distribution api page')
    ElMessage.success('回推事件已重新入队')
    await loadOutboxEvents()
  } finally {
    retryingOutboxId.value = null
  }
}

async function loadDistributionExceptions() {
  exceptionLoading.value = true
  try {
    distributionExceptions.value = await fetchDistributionExceptions(exceptionStatus.value || undefined)
  } finally {
    exceptionLoading.value = false
  }
}

async function handleRetryException(row) {
  retryingExceptionId.value = row.id
  try {
    await retryDistributionException(row.id, 'manual retry from distribution api page')
    ElMessage.success('异常记录已重新入队，调度器会自动重放')
    await loadDistributionExceptions()
  } finally {
    retryingExceptionId.value = null
  }
}

async function handleProcessExceptions() {
  processingExceptions.value = true
  try {
    await processDistributionExceptionRetries(10)
    ElMessage.success('已触发异常重试队列处理')
    await loadDistributionExceptions()
  } finally {
    processingExceptions.value = false
  }
}

async function handleMarkHandled(row) {
  handlingExceptionId.value = row.id
  try {
    await markDistributionExceptionHandled(row.id, 'manual handled from distribution api page')
    ElMessage.success('异常记录已标记处理')
    await loadDistributionExceptions()
  } finally {
    handlingExceptionId.value = null
  }
}

function formatQueueStatus(value) {
  return (
    {
      PENDING: '待推送',
      PROCESSING: '推送中',
      SUCCESS: '成功',
      FAILED: '失败',
      DEAD_LETTER: '死信'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function queueStatusTag(value) {
  return (
    {
      PENDING: 'warning',
      PROCESSING: 'primary',
      SUCCESS: 'success',
      FAILED: 'danger',
      DEAD_LETTER: 'danger'
    }[String(value || '').toUpperCase()] || 'info'
  )
}

function formatExceptionStatus(value) {
  return (
    {
      OPEN: '待处理',
      RETRY_QUEUED: '已重新入队',
      HANDLED: '已处理'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function exceptionStatusTag(value) {
  return (
    {
      OPEN: 'warning',
      RETRY_QUEUED: 'primary',
      HANDLED: 'success'
    }[String(value || '').toUpperCase()] || 'info'
  )
}
</script>

<style scoped>
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

.endpoint-preview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.endpoint-preview article {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e5edf4;
}

.endpoint-preview span {
  color: #64748b;
  font-size: 13px;
}

.endpoint-preview strong {
  color: #0f172a;
  word-break: break-all;
}

.queue-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

@media (max-width: 900px) {
  .endpoint-preview {
    grid-template-columns: 1fr;
  }
}
</style>
