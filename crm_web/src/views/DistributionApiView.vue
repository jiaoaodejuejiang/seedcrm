<template>
  <div class="stack-page">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>分销接口</h3>
        </div>
        <div class="action-group">
          <el-button @click="loadActiveQueue">刷新当前页</el-button>
          <el-button v-if="canUpdateConfig" type="primary" @click="saveConfig">保存配置</el-button>
          <el-button :loading="testing" @click="testConfig">测试入站映射</el-button>
          <el-button v-if="canTriggerQueues" :loading="processingStatusCheck" @click="handleStatusCheck">状态回查</el-button>
          <el-button v-if="canTriggerQueues" :loading="processingReconcile" @click="handleReconcilePull">对账拉取</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="接入配置" name="config">
          <el-alert
            v-if="!canUpdateConfig"
            class="queue-alert"
            type="info"
            show-icon
            :closable="false"
            title="当前角色仅可查看配置、执行调试与队列处理，不能修改分销接口配置。"
          />
          <div class="form-grid">
            <div class="full-span form-group-title">应用身份</div>
            <label>
              <span>启用状态</span>
              <el-select v-model="config.enabled" :disabled="!canUpdateConfig">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>运行模式</span>
              <el-select v-model="config.executionMode" :disabled="!canUpdateConfig">
                <el-option label="模拟" value="MOCK" />
                <el-option label="真实" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="config.appId" :disabled="!canUpdateConfig" placeholder="外部分销系统 AppId" />
            </label>
            <label>
              <span>AppSecret</span>
              <el-input v-model="config.appSecret" :disabled="!canUpdateConfig" type="password" show-password placeholder="验签与回推签名密钥" />
            </label>

            <div class="full-span form-group-title">接口地址</div>
            <label class="full-span">
              <span>已支付订单入站地址</span>
              <span class="readonly-prefix">{{ eventIngestUrl }}</span>
            </label>
            <label class="full-span">
              <span>履约状态回推目标</span>
              <el-input v-model="config.fulfillmentCallbackUrl" :disabled="!canUpdateConfig" placeholder="https://partner.example.com/open/crm/fulfillment" />
            </label>
            <label>
              <span>认证方式</span>
              <el-select v-model="config.authMode" :disabled="!canUpdateConfig">
                <el-option label="签名 + 幂等键" value="SIGN_TOKEN" />
                <el-option label="AppId + Secret" value="APP_SECRET" />
              </el-select>
            </label>
            <label>
              <span>入站路径</span>
              <el-input v-model="config.eventIngestPath" :disabled="!canUpdateConfig" placeholder="/open/distribution/events" />
            </label>
            <label>
              <span>状态回查路径</span>
              <el-input v-model="config.statusQueryPath" :disabled="!canUpdateConfig" placeholder="/open/distribution/orders/status" />
            </label>
            <label>
              <span>对账拉取路径</span>
              <el-input v-model="config.reconciliationPullPath" :disabled="!canUpdateConfig" placeholder="/open/distribution/orders/reconcile" />
            </label>
            <label class="full-span">
              <span>状态映射</span>
              <el-input
                v-model="config.statusMapping"
                :disabled="!canUpdateConfig"
                placeholder="paid=distribution.order.paid,cancelled=distribution.order.cancelled,refund_pending=distribution.order.refund_pending,refunded=distribution.order.refunded"
              />
            </label>
            <label>
              <span>限流策略</span>
              <el-input v-model="config.rateLimit" :disabled="!canUpdateConfig" placeholder="例如 60 次/分钟" />
            </label>
            <label>
              <span>缓存策略</span>
              <el-input v-model="config.cachePolicy" :disabled="!canUpdateConfig" placeholder="例如 30 秒缓存" />
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
            <article>
              <span>状态回查</span>
              <strong>{{ config.statusQueryPath || 'MOCK 模式使用本地测试数据，LIVE 模式需配置路径' }}</strong>
            </article>
            <article>
              <span>对账拉取</span>
              <strong>{{ config.reconciliationPullPath || 'MOCK 模式使用本地测试数据，LIVE 模式需配置路径' }}</strong>
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
            <el-button v-if="canTriggerQueues" :loading="processingOutbox" type="primary" @click="handleProcessOutbox">立即处理队列</el-button>
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
                  v-if="canTriggerQueues && String(row.status).toUpperCase() !== 'SUCCESS'"
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
            <el-button v-if="canTriggerQueues" :loading="processingExceptions" type="primary" @click="handleProcessExceptions">处理重试队列</el-button>
            <el-button @click="loadDistributionExceptions">刷新</el-button>
          </div>

          <el-alert
            class="queue-alert"
            type="warning"
            show-icon
            :closable="false"
            title="异常队列只用于人工核对、重新入队和标记处理，不会强制覆盖本地订单。"
          />

          <el-table v-loading="exceptionLoading" :data="distributionExceptions" stripe class="exception-table">
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="exception-detail">
                  <div v-if="parseConflictFields(row).length" class="conflict-panel">
                    <h4>冲突字段明细</h4>
                    <el-table :data="parseConflictFields(row)" size="small" border>
                      <el-table-column label="字段" width="120" prop="label" />
                      <el-table-column label="本地值" min-width="180" prop="existingValue" show-overflow-tooltip />
                      <el-table-column label="外部值" min-width="180" prop="incomingValue" show-overflow-tooltip />
                      <el-table-column label="说明" min-width="220" prop="detail" show-overflow-tooltip />
                    </el-table>
                  </div>
                  <div class="detail-grid">
                    <article>
                      <span>异常说明</span>
                      <strong>{{ row.errorMessage || '--' }}</strong>
                    </article>
                    <article>
                      <span>处理建议</span>
                      <strong>{{ exceptionRecommendation(row) }}</strong>
                    </article>
                    <article>
                      <span>事件ID</span>
                      <strong>{{ row.eventId || '--' }}</strong>
                    </article>
                    <article>
                      <span>幂等键</span>
                      <strong>{{ row.idempotencyKey || '--' }}</strong>
                    </article>
                    <article>
                      <span>外部会员</span>
                      <strong>{{ row.externalMemberId || '--' }}</strong>
                    </article>
                    <article>
                      <span>会员手机号</span>
                      <strong>{{ row.phone || '--' }}</strong>
                    </article>
                    <article>
                      <span>更新时间</span>
                      <strong>{{ formatDateTime(row.updatedAt) }}</strong>
                    </article>
                    <article>
                      <span>处理备注</span>
                      <strong>{{ row.handleRemark || '--' }}</strong>
                    </article>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="异常类型" min-width="150">
              <template #default="{ row }">
                <el-tag :type="exceptionErrorTag(row.errorCode)">{{ formatExceptionErrorCode(row.errorCode) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="冲突字段" min-width="230">
              <template #default="{ row }">
                <div v-if="parseConflictFields(row).length" class="conflict-tags">
                  <el-tag v-for="item in parseConflictFields(row)" :key="item.field" type="danger" effect="plain">
                    {{ item.label }}
                  </el-tag>
                </div>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
            <el-table-column label="外部订单号" min-width="150" prop="externalOrderId" />
            <el-table-column label="本地订单号" min-width="160">
              <template #default="{ row }">{{ row.relatedOrderNo || row.relatedOrderId || '--' }}</template>
            </el-table-column>
            <el-table-column label="TraceId" min-width="190" prop="callbackLogTraceId" show-overflow-tooltip />
            <el-table-column label="处理状态" width="120">
              <template #default="{ row }">
                <el-tag :type="exceptionStatusTag(row.handlingStatus)">{{ formatExceptionStatus(row.handlingStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理建议" min-width="280">
              <template #default="{ row }">{{ exceptionRecommendation(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="290" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button
                    v-if="row.callbackLogTraceId"
                    size="small"
                    plain
                    @click="copyTraceId(row)"
                  >
                    复制TraceId
                  </el-button>
                  <el-button
                    v-if="canTriggerQueues && String(row.handlingStatus).toUpperCase() !== 'HANDLED'"
                    size="small"
                    plain
                    :loading="retryingExceptionId === row.id"
                    @click="handleRetryException(row)"
                  >
                    重新入队
                  </el-button>
                  <el-button
                    v-if="canUpdateConfig && String(row.handlingStatus).toUpperCase() !== 'HANDLED'"
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

        <el-tab-pane label="回查对账结果" name="reconcile">
          <div class="queue-toolbar">
            <el-button v-if="canTriggerQueues" :loading="processingStatusCheck" type="primary" @click="handleStatusCheck">执行状态回查</el-button>
            <el-button v-if="canTriggerQueues" :loading="processingReconcile" @click="handleReconcilePull">执行对账拉取</el-button>
            <el-tag type="info">仅展示本页面最近一次执行结果</el-tag>
          </div>

          <el-table :data="reconciliationResults" stripe empty-text="暂无回查或对账结果">
            <el-table-column label="检查时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.checkedAt) }}</template>
            </el-table-column>
            <el-table-column label="任务类型" width="130">
              <template #default="{ row }">{{ formatReconciliationJobType(row.jobType) }}</template>
            </el-table-column>
            <el-table-column label="外部订单" min-width="160" prop="externalOrderId" />
            <el-table-column label="处理动作" width="120">
              <template #default="{ row }">
                <el-tag :type="reconciliationActionTag(row.action)">{{ formatReconciliationAction(row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="映射事件" min-width="210" prop="eventType" show-overflow-tooltip />
            <el-table-column label="结果" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'FAILED' ? 'danger' : 'success'">{{ row.status === 'FAILED' ? '失败' : '成功' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="320" prop="message" show-overflow-tooltip />
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
  processDistributionReconciliation,
  processDistributionStatusCheck,
  processSchedulerOutbox,
  retryDistributionException,
  retrySchedulerOutboxEvent,
  saveIntegrationProvider
} from '../api/scheduler'
import { formatDateTime } from '../utils/format'
import { currentUser } from '../utils/auth'
import { buildSystemUrl, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const activeTab = ref('config')
const testing = ref(false)
const outboxLoading = ref(false)
const exceptionLoading = ref(false)
const processingOutbox = ref(false)
const processingExceptions = ref(false)
const processingStatusCheck = ref(false)
const processingReconcile = ref(false)
const retryingOutboxId = ref(null)
const retryingExceptionId = ref(null)
const handlingExceptionId = ref(null)
const outboxStatus = ref('')
const exceptionStatus = ref('')
const outboxEvents = ref([])
const distributionExceptions = ref([])
const reconciliationResults = ref([])
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
  statusQueryPath: state.distributionApi?.statusQueryPath || '/open/distribution/orders/status',
  reconciliationPullPath: state.distributionApi?.reconciliationPullPath || '/open/distribution/orders/reconcile',
  statusMapping:
    state.distributionApi?.statusMapping ||
    'paid=distribution.order.paid,cancelled=distribution.order.cancelled,refund_pending=distribution.order.refund_pending,refunded=distribution.order.refunded',
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
const currentRoleCode = computed(() => String(currentUser.value?.roleCode || '').trim().toUpperCase())
const canUpdateConfig = computed(() => ['ADMIN', 'INTEGRATION_ADMIN'].includes(currentRoleCode.value))
const canTriggerQueues = computed(() => ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR'].includes(currentRoleCode.value))

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
  if (!canUpdateConfig.value) {
    ElMessage.warning('当前角色不能修改分销接口配置')
    return
  }
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
    statusQueryPath: config.statusQueryPath,
    reconciliationPullPath: config.reconciliationPullPath,
    statusMapping: config.statusMapping,
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

async function handleStatusCheck() {
  processingStatusCheck.value = true
  try {
    const rows = await processDistributionStatusCheck(20)
    reconciliationResults.value = rows
    activeTab.value = 'reconcile'
    ElMessage.success(`状态回查完成，处理 ${rows.length} 条记录`)
  } finally {
    processingStatusCheck.value = false
  }
}

async function handleReconcilePull() {
  processingReconcile.value = true
  try {
    const rows = await processDistributionReconciliation(20)
    reconciliationResults.value = rows
    activeTab.value = 'reconcile'
    ElMessage.success(`对账拉取完成，处理 ${rows.length} 条记录`)
  } finally {
    processingReconcile.value = false
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

function formatExceptionErrorCode(value) {
  return (
    {
      EXTERNAL_ORDER_CONFLICT: '外部订单冲突',
      EXTERNAL_STATUS_CONFLICT: '外部状态冲突',
      INGEST_FAILED: '入站处理失败',
      PROVIDER_INVALID: '接口配置异常',
      PARTNER_MISMATCH: '渠道不一致',
      NONCE_REPLAYED: '重复请求',
      SIGNATURE_INVALID: '签名失败',
      SIGNATURE_MISSING: '签名缺失',
      PAYLOAD_INVALID: '报文异常',
      IDEMPOTENCY_MISSING: '幂等键缺失'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function exceptionErrorTag(value) {
  return (
    {
      EXTERNAL_ORDER_CONFLICT: 'danger',
      EXTERNAL_STATUS_CONFLICT: 'warning',
      INGEST_FAILED: 'danger',
      PROVIDER_INVALID: 'danger',
      PARTNER_MISMATCH: 'danger',
      NONCE_REPLAYED: 'warning',
      SIGNATURE_INVALID: 'danger',
      SIGNATURE_MISSING: 'warning',
      PAYLOAD_INVALID: 'danger',
      IDEMPOTENCY_MISSING: 'warning'
    }[String(value || '').toUpperCase()] || 'info'
  )
}

function parseConflictFields(row) {
  const code = String(row?.errorCode || '').toUpperCase()
  if (!['EXTERNAL_ORDER_CONFLICT', 'EXTERNAL_STATUS_CONFLICT'].includes(code)) {
    return []
  }
  const structured = parseConflictDetailJson(row?.conflictDetailJson)
  if (structured.length) {
    return structured
  }
  const message = String(row?.errorMessage || '')
  if (code === 'EXTERNAL_STATUS_CONFLICT') {
    const localStatus = message.match(/local order status\s+([^,\s]+)/i)?.[1]
    const incomingEvent = message.match(/external\s+([^\s]+)\s+received/i)?.[1]
    return [
      {
        field: 'status',
        label: '订单状态',
        existingValue: localStatus || '--',
        incomingValue: incomingEvent || '--',
        detail: message || '外部状态与本地履约状态不一致'
      }
    ]
  }
  const [, conflictPart = message] = message.split('duplicate external order conflict:')
  return conflictPart
    .split(';')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const field = item.split(/\s+/)[0]
      const existingValue = extractConflictValue(item, 'existing')
      const incomingValue = extractConflictValue(item, 'incoming')
      return {
        field,
        label: conflictFieldLabel(field),
        existingValue: existingValue || '--',
        incomingValue: incomingValue || '--',
        detail: item
      }
    })
}

function parseConflictDetailJson(value) {
  if (!value) {
    return []
  }
  try {
    const rows = JSON.parse(value)
    if (!Array.isArray(rows)) {
      return []
    }
    return rows
      .filter((item) => item && typeof item === 'object')
      .map((item) => ({
        field: item.field || '',
        label: item.fieldLabel || conflictFieldLabel(item.field),
        existingValue: item.existingValue ?? '--',
        incomingValue: item.incomingValue ?? '--',
        detail: item.detail || `${item.field || 'field'} existing=${item.existingValue ?? '--'} incoming=${item.incomingValue ?? '--'}`
      }))
  } catch (error) {
    return []
  }
}

function extractConflictValue(text, key) {
  const pattern = new RegExp(`${key}=([^\\s]+)`)
  const matched = String(text || '').match(pattern)
  return matched?.[1] || ''
}

function conflictFieldLabel(field) {
  return (
    {
      externalTradeNo: '支付流水',
      externalMemberId: '外部会员',
      type: '订单类型',
      amount: '订单金额',
      status: '订单状态'
    }[field] || field || '冲突字段'
  )
}

function exceptionRecommendation(row) {
  const code = String(row?.errorCode || '').toUpperCase()
  if (code === 'EXTERNAL_ORDER_CONFLICT') {
    const fields = parseConflictFields(row)
      .map((item) => item.label)
      .join('、')
    return `请核对外部订单${fields ? `的${fields}` : ''}，修正外部数据或配置后重新推送/重新入队；本系统不会覆盖本地订单。`
  }
  if (code === 'EXTERNAL_STATUS_CONFLICT') {
    const status = parseConflictFields(row)?.[0]
    const suffix = status ? `当前本地状态为 ${status.existingValue}，外部状态为 ${status.incomingValue}。` : ''
    return `${suffix}请核对外部退款/取消状态与本地履约状态，确认后重新入队或标记处理；不要绕过门店履约流程。`
  }
  if (code === 'PROVIDER_INVALID' || code === 'SIGNATURE_INVALID' || code === 'SIGNATURE_MISSING') {
    return '请先检查分销接口配置、签名密钥、回调地址和请求头，再重新入队处理。'
  }
  if (code === 'PAYLOAD_INVALID' || code === 'IDEMPOTENCY_MISSING') {
    return '请检查外部回调报文格式、必填字段和幂等键，修正后重新推送。'
  }
  return '请查看异常说明和原始回调，修正配置或外部数据后重新入队；确认无需处理时再标记处理。'
}

async function copyTraceId(row) {
  const traceId = row?.callbackLogTraceId
  if (!traceId) {
    return
  }
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('TraceId 已复制')
  } catch (error) {
    ElMessage.warning(`TraceId：${traceId}`)
  }
}

function formatReconciliationJobType(value) {
  return (
    {
      STATUS_CHECK: '状态回查',
      RECONCILE_PULL: '对账拉取'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function formatReconciliationAction(value) {
  return (
    {
      REPLAYED: '已重放',
      NO_CHANGE: '无变化',
      FAILED: '失败'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function reconciliationActionTag(value) {
  return (
    {
      REPLAYED: 'primary',
      NO_CHANGE: 'info',
      FAILED: 'danger'
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

.queue-alert {
  margin-bottom: 14px;
  border-radius: 14px;
}

.exception-table :deep(.el-table__expanded-cell) {
  background: #f8fafc;
}

.exception-detail {
  padding: 6px 10px 12px;
}

.conflict-panel {
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #fecaca;
  border-radius: 16px;
  background: #fff7f7;
}

.conflict-panel h4 {
  margin: 0 0 10px;
  color: #991b1b;
  font-size: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid article {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.detail-grid span {
  color: #64748b;
  font-size: 12px;
}

.detail-grid strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  word-break: break-all;
}

.conflict-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.muted {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .endpoint-preview {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
