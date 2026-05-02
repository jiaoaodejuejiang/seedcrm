<template>
  <div class="stack-page">
    <section v-if="verificationWarning" class="panel compact-panel">
      <el-alert :title="verificationWarning" type="warning" show-icon :closable="false" />
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>抖音接入</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" :loading="savingProvider" @click="handleSaveProvider">保存配置</el-button>
          <el-button :loading="testingProvider" @click="handleTestProvider">测试连接</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="概览" name="overview">
          <div class="overview-grid">
            <article class="detail-card">
              <h3>授权信息</h3>
              <p>授权码状态：{{ formatResultStatus(provider.authCodeStatus || '--') }}</p>
              <p>授权码有效期：{{ provider.authCodeExpiresAt ? formatDateTime(provider.authCodeExpiresAt) : '--' }}</p>
              <p v-if="provider.authCodeWarning" :class="{ 'danger-text': provider.authCodeExpired }">{{ provider.authCodeWarning }}</p>
              <p>Access Token：{{ provider.accessTokenMasked || '--' }}</p>
              <p>Refresh Token：{{ provider.refreshTokenMasked || '--' }}</p>
              <p>到期时间：{{ formatDateTime(provider.tokenExpiresAt) || '--' }}</p>
              <p>刷新到期：{{ formatDateTime(provider.refreshTokenExpiresAt) || '--' }}</p>
            </article>

            <article class="detail-card">
              <h3>同步与回调</h3>
              <p>最近测试：{{ formatResultStatus(provider.lastTestStatus || '--') }}</p>
              <p>最近回调：{{ formatCallbackProcessStatus(provider.lastCallbackStatus || '--') }}</p>
              <p>最近同步：{{ formatDateTime(provider.lastSyncTime) || '--' }}</p>
              <p>下次执行：{{ formatDateTime(jobForm.nextRunTime) || '--' }}</p>
              <p>队列名称：{{ jobForm.queueName || '--' }}</p>
            </article>

            <article class="detail-card">
              <h3>线索拉取</h3>
              <p>线索接口：{{ provider.endpointPath || '--' }}</p>
              <p>本地账号：{{ provider.localAccountIds || '--' }}</p>
              <p>来客账号：{{ provider.lifeAccountIds || '--' }}</p>
              <p>拉取窗口：{{ provider.pullWindowMinutes || 0 }} 分钟</p>
              <p>超时时间：{{ provider.requestTimeoutMs || 0 }} 毫秒</p>
            </article>

            <article class="detail-card">
              <h3>券核销</h3>
              <p>
                就绪状态：
                <el-tag :type="voucherReadiness.type" effect="plain">{{ voucherReadiness.label }}</el-tag>
              </p>
              <p>{{ voucherReadiness.message }}</p>
              <p>预查询接口：{{ provider.voucherPreparePath || '--' }}</p>
              <p>核销接口：{{ provider.voucherVerifyPath || '--' }}</p>
              <p>撤销接口：{{ provider.voucherCancelPath || '--' }}</p>
              <p>默认 POI：{{ provider.poiId || '--' }}</p>
              <p>券码字段：{{ provider.verifyCodeField || '--' }}</p>
            </article>

            <article class="detail-card">
              <h3>退款接口</h3>
              <p>退款申请：{{ provider.refundApplyPath || '--' }}</p>
              <p>退款查询：{{ provider.refundQueryPath || '--' }}</p>
              <p>退款回调：{{ refundNotifyUrl }}</p>
              <p>状态映射：{{ provider.refundStatusMapping || '--' }}</p>
            </article>
          </div>

          <el-table :data="callbackPagination.rows" stripe>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.receivedAt) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                {{ formatCallbackProcessStatus(row.processStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="验签" width="120">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.signatureStatus)">
                  {{ formatCallbackSignatureStatus(row.signatureStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="信任" width="120">
              <template #default="{ row }">
                {{ formatCallbackTrustLevel(row.trustLevel) }}
              </template>
            </el-table-column>
            <el-table-column label="幂等" width="120">
              <template #default="{ row }">
                {{ formatCallbackIdempotencyStatus(row.idempotencyStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="事件" min-width="160" prop="eventType" />
            <el-table-column label="结果" min-width="220" prop="processMessage" />
          </el-table>

          <div class="table-pagination">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="callbackPagination.total"
              :current-page="callbackPagination.currentPage"
              :page-size="callbackPagination.pageSize"
              :page-sizes="callbackPagination.pageSizes"
              @size-change="callbackPagination.handleSizeChange"
              @current-change="callbackPagination.handleCurrentChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="接入配置" name="access">
          <div class="form-grid">
            <div class="full-span form-group-title">基础信息</div>
            <label>
              <span>供应商编码</span>
              <el-input v-model="provider.providerCode" readonly />
            </label>
            <label>
              <span>供应商名称</span>
              <el-input v-model="provider.providerName" readonly />
            </label>
            <label>
              <span>模块编码</span>
              <el-input v-model="provider.moduleCode" readonly />
            </label>
            <label>
              <span>启用状态</span>
              <el-select v-model="provider.enabled">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>运行模式</span>
              <el-select v-model="provider.executionMode">
                <el-option label="模拟" value="MOCK" />
                <el-option label="真实" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>授权方式</span>
              <el-input :model-value="formatAuthType(provider.authType)" readonly />
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="provider.appId" placeholder="请输入抖音开放平台 AppId" />
            </label>
            <label>
              <span>客户端 Key</span>
              <el-input v-model="provider.clientKey" placeholder="兼容旧配置时可补录" />
            </label>
            <label class="full-span">
              <span>客户端 Secret</span>
              <el-input
                v-model="provider.clientSecret"
                type="password"
                show-password
                :placeholder="provider.clientSecretMasked || '留空则保持原值'"
              />
            </label>

            <div class="full-span form-group-title">请求地址</div>
            <label>
              <span>开放平台域名</span>
              <el-input v-model="provider.baseUrl" placeholder="例如 https://api.oceanengine.com" />
            </label>
            <label>
              <span>换取 Token 地址</span>
              <el-input v-model="provider.tokenUrl" placeholder="https://api.oceanengine.com/open_api/oauth2/access_token/" />
            </label>
            <label>
              <span>系统基础域名</span>
              <span class="readonly-prefix">{{ systemBaseUrl }}</span>
            </label>
            <label>
              <span>API 域名</span>
              <span class="readonly-prefix">{{ apiBaseUrl }}</span>
            </label>
            <label class="full-span">
              <span>授权回跳登记地址</span>
              <span class="readonly-prefix">{{ provider.callbackUrl || backendCallbackUrl }}</span>
              <small>填到巨量引擎开放平台应用的授权回跳/回调地址；携带 auth_code 或 code 时会写入本系统 auth_code。</small>
            </label>
            <label class="full-span">
              <span>固定后端回调地址</span>
              <span class="readonly-prefix">{{ backendCallbackUrl }}</span>
              <small>线索拉取不依赖事件回调；退款、订单等业务回调只记录回调日志，不会写 auth_code。</small>
            </label>
            <label class="full-span">
              <span>授权回跳地址</span>
              <el-input v-model="provider.redirectUri" placeholder="请输入平台授权回跳地址" />
            </label>
            <label>
              <span>授权范围</span>
              <el-input v-model="provider.scope" placeholder="可选" />
            </label>
            <label>
              <span>授权码</span>
              <el-input v-model="provider.authCode" :placeholder="provider.authCodeMasked || '回调后自动回填，也可手动补录'" />
              <small :class="{ 'danger-text': provider.authCodeExpired }">{{ provider.authCodeWarning || 'auth_code 通常为短时效一次性授权码，过期后请重新授权' }}</small>
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="线索拉取" name="clue">
          <div class="form-grid">
            <div class="full-span form-group-title">拉取范围</div>
            <label class="full-span">
              <span>拉取账号 ID（必填）</span>
              <el-input v-model="provider.localAccountIds" placeholder="必填，对应接口 local_account_ids，数字 ID 多个用英文逗号分隔" />
              <small>不填不会全量拉取，会导致同步任务失败；这里只用于指定巨量接口拉哪些账号的客资，不是授权配置。</small>
            </label>

            <div class="full-span form-group-title">拉取参数</div>
            <label class="full-span">
              <span>线索接口路径</span>
              <el-input v-model="provider.endpointPath" placeholder="/open_api/2/tools/clue/life/get/" />
              <small>当前客资列表固定使用 POST {{ cluePullUrl }}，暂不启用抖音生活服务 clue/query 接口。</small>
            </label>
            <label>
              <span>每页数量</span>
              <el-input-number v-model="provider.pageSize" :min="1" :max="100" controls-position="right" />
            </label>
            <label>
              <span>拉取窗口（分钟）</span>
              <el-input-number v-model="provider.pullWindowMinutes" :min="1" controls-position="right" />
            </label>
            <label>
              <span>重叠窗口（分钟）</span>
              <el-input-number v-model="provider.overlapMinutes" :min="0" controls-position="right" />
            </label>
            <label>
              <span>超时时间（毫秒）</span>
              <el-input-number v-model="provider.requestTimeoutMs" :min="1000" :step="1000" controls-position="right" />
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="券核销" name="voucher">
          <el-alert
            class="config-alert"
            :title="voucherReadiness.message"
            :type="voucherAlertType"
            show-icon
            :closable="false"
          />
          <div class="form-grid">
            <div class="full-span form-group-title">接口路径</div>
            <label class="full-span">
              <span>核销预查询路径</span>
              <el-input v-model="provider.voucherPreparePath" placeholder="/goodlife/v1/fulfilment/certificate/prepare/" />
            </label>
            <label class="full-span">
              <span>核销提交路径</span>
              <el-input v-model="provider.voucherVerifyPath" placeholder="/goodlife/v1/fulfilment/certificate/verify/" />
            </label>
            <label class="full-span">
              <span>核销撤销路径</span>
              <el-input v-model="provider.voucherCancelPath" placeholder="/goodlife/v1/fulfilment/certificate/cancel/" />
            </label>

            <div class="full-span form-group-title">门店参数</div>
            <label>
              <span>默认 POI ID</span>
              <el-input v-model="provider.poiId" placeholder="按门店或接口要求填写" />
            </label>
            <label>
              <span>券码字段名</span>
              <el-input v-model="provider.verifyCodeField" placeholder="例如 encrypted_codes" />
            </label>
            <div class="full-span action-group action-group--section">
              <el-button plain @click="handleTestVoucherReadiness">测试券核销配置（模拟）</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="退款配置" name="refund">
          <div class="form-grid">
            <div class="full-span form-group-title">退款接口路径</div>
            <label class="full-span">
              <span>退款申请路径</span>
              <el-input v-model="provider.refundApplyPath" placeholder="/api/apps/trade/v2/refund/create_refund" />
            </label>
            <label class="full-span">
              <span>退款查询路径</span>
              <el-input v-model="provider.refundQueryPath" placeholder="/api/apps/trade/v2/refund/query_refund" />
            </label>
            <label class="full-span">
              <span>退款列表路径</span>
              <el-input v-model="provider.refundListPath" placeholder="/api/apps/trade/v2/refund/refund_list" />
            </label>

            <div class="full-span form-group-title">回调地址</div>
            <label class="full-span">
              <span>退款通知地址</span>
              <el-input :model-value="refundNotifyUrl" readonly />
            </label>
            <label class="full-span">
              <span>退款审核回调</span>
              <el-input :model-value="refundAuditCallbackUrl" readonly />
            </label>

            <div class="full-span form-group-title">字段映射</div>
            <label>
              <span>平台订单字段</span>
              <el-input v-model="provider.refundOrderIdField" placeholder="order_id" />
            </label>
            <label>
              <span>退款金额字段</span>
              <el-input v-model="provider.refundAmountField" placeholder="refund_amount" />
            </label>
            <label>
              <span>退款原因字段</span>
              <el-input v-model="provider.refundReasonField" placeholder="reason" />
            </label>
            <label>
              <span>商户订单字段</span>
              <el-input v-model="provider.refundOutOrderNoField" placeholder="out_order_no" />
            </label>
            <label>
              <span>商户退款单字段</span>
              <el-input v-model="provider.refundOutRefundNoField" placeholder="out_refund_no" />
            </label>
            <label>
              <span>平台退款单字段</span>
              <el-input v-model="provider.refundExternalRefundIdField" placeholder="refund_id" />
            </label>
            <label>
              <span>券/子订单字段</span>
              <el-input v-model="provider.refundItemOrderIdField" placeholder="item_order_id" />
            </label>
            <label>
              <span>通知地址字段</span>
              <el-input v-model="provider.refundNotifyUrlField" placeholder="notify_url" />
            </label>
            <label>
              <span>金额单位</span>
              <el-select v-model="provider.refundAmountUnit">
                <el-option label="分（平台常用）" value="CENT" />
                <el-option label="元" value="YUAN" />
              </el-select>
            </label>
            <label class="full-span">
              <span>状态映射</span>
              <el-input
                v-model="provider.refundStatusMapping"
                placeholder="INIT,AUDITING,AUDITED,REJECTED,ARBITRATE,CANCEL,SUCCESS,FAIL"
              />
            </label>
          </div>
        </el-tab-pane>

      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  fetchIntegrationCallbackLogs,
  fetchIntegrationProviders,
  fetchSchedulerJobs,
  saveIntegrationProvider,
  saveSchedulerJob,
  testIntegrationProvider,
  triggerSchedulerJob
} from '../api/scheduler'
import { useTablePagination } from '../composables/useTablePagination'
import {
  formatCallbackIdempotencyStatus,
  formatCallbackProcessStatus,
  formatCallbackSignatureStatus,
  formatCallbackTrustLevel,
  formatDateTime,
  formatResultStatus,
  statusTagType
} from '../utils/format'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'

const DOUYIN_CODE = 'DOUYIN_LAIKE'
const DOUYIN_JOB_CODE = 'DOUYIN_CLUE_INCREMENTAL'
const DOUYIN_BASE_URL = 'https://api.oceanengine.com'
const DOUYIN_TOKEN_URL = 'https://api.oceanengine.com/open_api/oauth2/access_token/'
const DOUYIN_CLUE_ENDPOINT = '/open_api/2/tools/clue/life/get/'
const LEGACY_DOUYIN_BASE_URLS = ['https://open.douyin.com']
const LEGACY_DOUYIN_TOKEN_URLS = [
  'https://open.douyin.com/oauth/access_token/',
  'https://open.douyin.com/oauth/client_token/'
]
const LEGACY_DOUYIN_CLUE_ENDPOINTS = [
  '/goodlife/v1/clue/douyin/list/',
  '/goodlife/v1/open_api/crm/clue/query/'
]

const route = useRoute()
const activeTab = ref(resolveInitialTab())
const savingProvider = ref(false)
const testingProvider = ref(false)
const savingJob = ref(false)
const triggering = ref(false)
const callbackLogs = ref([])
const callbackPagination = useTablePagination(callbackLogs)
const providerSnapshot = ref(null)
const systemState = loadSystemConsoleState()

const provider = reactive(createProvider())
const jobForm = reactive(createJobForm())
const systemBaseUrl = computed(() => String(systemState.domainSettings?.systemBaseUrl || '').trim() || '--')
const apiBaseUrl = computed(() => String(systemState.domainSettings?.apiBaseUrl || '').trim() || '--')
const cluePullUrl = computed(() => buildProviderUrl(provider.baseUrl, provider.endpointPath))
const voucherReadiness = computed(() => resolveVoucherReadiness(provider))
const voucherAlertType = computed(() => (voucherReadiness.value.type === 'danger' ? 'error' : voucherReadiness.value.type))

const backendCallbackUrl = computed(() => {
  return buildSystemUrl(systemState, 'callback', '/scheduler/oauth/douyin/callback')
})
const refundNotifyUrl = computed(() =>
  buildSystemUrl(systemState, 'callback', provider.refundNotifyPath || '/scheduler/callback/douyin/refund')
)
const refundAuditCallbackUrl = computed(() =>
  buildSystemUrl(systemState, 'callback', provider.refundAuditCallbackPath || '/scheduler/callback/douyin/refund-audit')
)

function resolveInitialTab() {
  const tab = String(route.query.tab || '').trim()
  return ['overview', 'clue', 'voucher', 'refund', 'runtime'].includes(tab) ? tab : 'overview'
}

const verificationWarning = computed(() => {
  if (provider.executionMode !== 'LIVE') {
    return ''
  }
  const latest = callbackLogs.value[0]
  if (!latest) {
    return ''
  }
  if (latest.signatureStatus === 'NOT_VERIFIED' || latest.signatureStatus === 'LOCAL_BYPASS') {
    return '当前真实模式仍在本地回调或跳过验签状态，正式接入前请确认平台侧验签与公网回调。'
  }
  return ''
})

onMounted(async () => {
  await loadView()
})

function createProvider() {
  return {
    id: null,
    providerCode: DOUYIN_CODE,
    providerName: '抖音来客',
    moduleCode: 'CLUE',
    executionMode: 'MOCK',
    authType: 'AUTH_CODE',
    appId: '',
    baseUrl: DOUYIN_BASE_URL,
    tokenUrl: DOUYIN_TOKEN_URL,
    endpointPath: DOUYIN_CLUE_ENDPOINT,
    voucherPreparePath: '/goodlife/v1/fulfilment/certificate/prepare/',
    voucherVerifyPath: '/goodlife/v1/fulfilment/certificate/verify/',
    voucherCancelPath: '/goodlife/v1/fulfilment/certificate/cancel/',
    refundApplyPath: '/api/apps/trade/v2/refund/create_refund',
    refundQueryPath: '/api/apps/trade/v2/refund/query_refund',
    refundListPath: '/api/apps/trade/v2/refund/refund_list',
    refundNotifyPath: '/scheduler/callback/douyin/refund',
    refundAuditCallbackPath: '/scheduler/callback/douyin/refund-audit',
    refundOrderIdField: 'order_id',
    refundAmountField: 'refund_amount',
    refundReasonField: 'reason',
    refundOutOrderNoField: 'out_order_no',
    refundOutRefundNoField: 'out_refund_no',
    refundExternalRefundIdField: 'refund_id',
    refundItemOrderIdField: 'item_order_id',
    refundNotifyUrlField: 'notify_url',
    refundAmountUnit: 'CENT',
    refundStatusMapping: 'INIT,AUDITING,AUDITED,REJECTED,ARBITRATE,CANCEL,SUCCESS,FAIL',
    clientKey: '',
    clientSecret: '',
    clientSecretMasked: '',
    clientSecretConfigured: false,
    redirectUri: '',
    scope: '',
    authCode: '',
    authCodeMasked: '',
    authCodeStatus: '',
    authCodeExpiresAt: '',
    authCodeExpired: false,
    authCodeSecondsRemaining: 0,
    authCodeWarning: '',
    accessTokenMasked: '',
    refreshTokenMasked: '',
    tokenExpiresAt: '',
    refreshTokenExpiresAt: '',
    accountId: '',
    lifeAccountIds: '',
    localAccountIds: '',
    openId: '',
    poiId: '',
    verifyCodeField: 'encrypted_codes',
    pageSize: 20,
    pullWindowMinutes: 60,
    overlapMinutes: 10,
    requestTimeoutMs: 10000,
    callbackUrl: '',
    enabled: 1,
    remark: '',
    authStatus: '',
    lastTestStatus: '',
    lastTestMessage: '',
    lastTestAt: '',
    lastCallbackStatus: '',
    lastCallbackMessage: '',
    lastCallbackAt: '',
    lastSyncTime: ''
  }
}

function createJobForm() {
  return {
    jobCode: DOUYIN_JOB_CODE,
    moduleCode: 'CLUE',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 5,
    retryLimit: 3,
    queueName: 'douyin-clue-sync',
    providerId: null,
    endpoint: '/clue/add',
    status: 'ENABLED',
    nextRunTime: '',
    lastRunTime: ''
  }
}

function applyProvider(payload) {
  const defaults = createProvider()
  Object.assign(provider, defaults, payload || {})
  normalizeDouyinClueDefaults(provider)
  normalizeDouyinPullAccountIds(provider)
  for (const key of [
    'refundApplyPath',
    'refundQueryPath',
    'refundListPath',
    'refundNotifyPath',
    'refundAuditCallbackPath',
    'refundOrderIdField',
    'refundAmountField',
    'refundReasonField',
    'refundOutOrderNoField',
    'refundOutRefundNoField',
    'refundExternalRefundIdField',
    'refundItemOrderIdField',
    'refundNotifyUrlField',
    'refundAmountUnit',
    'refundStatusMapping'
  ]) {
    if (!provider[key]) {
      provider[key] = defaults[key]
    }
  }
  provider.clientSecret = ''
  providerSnapshot.value = JSON.parse(JSON.stringify({
    executionMode: provider.executionMode,
    appId: provider.appId,
    callbackUrl: provider.callbackUrl,
    localAccountIds: provider.localAccountIds,
    voucherVerifyPath: provider.voucherVerifyPath,
    refundApplyPath: provider.refundApplyPath,
    refundQueryPath: provider.refundQueryPath,
    refundNotifyPath: provider.refundNotifyPath,
    refundAmountUnit: provider.refundAmountUnit,
    poiId: provider.poiId,
    verifyCodeField: provider.verifyCodeField
  }))
}

function normalizeDouyinPullAccountIds(target) {
  if (!target || String(target.localAccountIds || '').trim()) {
    return
  }
  target.localAccountIds = firstNonBlank(target.lifeAccountIds, target.accountId)
}

function normalizeDouyinClueDefaults(target) {
  if (!target) {
    return
  }
  const baseUrl = String(target.baseUrl || '').trim()
  const tokenUrl = String(target.tokenUrl || '').trim()
  const endpointPath = String(target.endpointPath || '').trim()
  if (!baseUrl || LEGACY_DOUYIN_BASE_URLS.includes(trimTrailingSlash(baseUrl))) {
    target.baseUrl = DOUYIN_BASE_URL
  }
  if (!tokenUrl || LEGACY_DOUYIN_TOKEN_URLS.includes(tokenUrl)) {
    target.tokenUrl = DOUYIN_TOKEN_URL
  }
  if (!endpointPath || LEGACY_DOUYIN_CLUE_ENDPOINTS.includes(endpointPath)) {
    target.endpointPath = DOUYIN_CLUE_ENDPOINT
  }
}

function buildProviderUrl(baseUrl, endpointPath) {
  const path = String(endpointPath || DOUYIN_CLUE_ENDPOINT).trim()
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  const base = trimTrailingSlash(String(baseUrl || DOUYIN_BASE_URL).trim())
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

function resolveVoucherReadiness(target) {
  if (!target || Number(target.enabled) === 0) {
    return {
      type: 'info',
      label: '未启用',
      message: '抖音接口未启用，门店端抖音团购券核销会被阻断，不会自动推进服务单'
    }
  }
  if (String(target.executionMode || '').toUpperCase() !== 'LIVE') {
    return {
      type: 'warning',
      label: '联调模式',
      message: '当前为 MOCK 联调模式，不代表真实核销成功；正式上线前必须切换 LIVE 并完成连接测试'
    }
  }
  const missing = []
  if (!String(target.baseUrl || '').trim()) {
    missing.push('开放平台域名')
  }
  if (!String(target.voucherVerifyPath || '').trim()) {
    missing.push('核销提交路径')
  }
  if (!String(target.verifyCodeField || '').trim()) {
    missing.push('券码字段名')
  }
  if (!['AUTHORIZED', 'CONNECTED'].includes(String(target.authStatus || '').toUpperCase())) {
    missing.push('有效授权状态')
  }
  if (!firstNonBlank(target.accessTokenMasked, target.refreshTokenMasked, target.authCodeMasked, target.authCode)) {
    missing.push('授权 Token 或 auth_code')
  }
  if (target.authCodeExpired && !firstNonBlank(target.accessTokenMasked, target.refreshTokenMasked)) {
    missing.push('未过期授权')
  }
  if (missing.length) {
    return {
      type: 'warning',
      label: '未配置',
      message: `缺少 ${missing.join('、')}；门店端抖音团购券核销将被阻断，不会自动推进服务单`
    }
  }
  if (target.lastTestStatus && ['FAIL', 'FAILED', 'ERROR'].includes(String(target.lastTestStatus).toUpperCase())) {
    return {
      type: 'danger',
      label: '测试失败',
      message: `最近连接测试失败：${target.lastTestMessage || '请检查授权和接口地址'}`
    }
  }
  if (String(target.lastTestStatus || '').toUpperCase() !== 'SUCCESS') {
    return {
      type: 'warning',
      label: '待测试',
      message: '请先完成抖音连接测试；测试通过前，门店端抖音团购券核销会被阻断'
    }
  }
  return {
    type: 'success',
    label: '已就绪',
    message: '抖音券核销配置已就绪；门店端只有外部接口返回成功后才会进入服务确认单流程'
  }
}

function trimTrailingSlash(value) {
  return value.endsWith('/') ? value.slice(0, -1) : value
}

function firstNonBlank(...values) {
  return values.map((value) => String(value || '').trim()).find(Boolean) || ''
}

function applyJob(payload) {
  Object.assign(jobForm, createJobForm(), payload || {})
}

async function loadView() {
  const [providers, jobs, callbacks] = await Promise.all([
    fetchIntegrationProviders(),
    fetchSchedulerJobs(),
    fetchIntegrationCallbackLogs(DOUYIN_CODE)
  ])
  applyProvider(providers.find((item) => item.providerCode === DOUYIN_CODE) || createProvider())
  applyJob(jobs.find((item) => item.jobCode === DOUYIN_JOB_CODE) || createJobForm())
  if (!jobForm.providerId && provider.id) {
    jobForm.providerId = provider.id
  }
  callbackLogs.value = callbacks
  callbackPagination.reset()
}

function buildProviderDangerList() {
  if (!providerSnapshot.value) {
    return []
  }
  return [
    ['executionMode', '运行模式'],
    ['appId', 'AppId'],
    ['callbackUrl', '回调登记地址'],
    ['localAccountIds', '本地账号映射'],
    ['voucherVerifyPath', '核销提交路径'],
    ['refundApplyPath', '退款申请路径'],
    ['refundQueryPath', '退款查询路径'],
    ['refundNotifyPath', '退款通知路径'],
    ['refundAmountUnit', '退款金额单位'],
    ['poiId', '默认 POI ID'],
    ['verifyCodeField', '券码字段名']
  ]
    .filter(([key]) => providerSnapshot.value[key] !== provider[key])
    .map(([, label]) => label)
}

function formatAuthType(value) {
  if (value === 'AUTH_CODE') {
    return '授权码模式'
  }
  if (value === 'CLIENT_TOKEN') {
    return '应用凭证模式'
  }
  return value || '--'
}

async function handleSaveProvider() {
  if (provider.executionMode === 'LIVE' && !firstNonBlank(provider.localAccountIds, provider.lifeAccountIds, provider.accountId)) {
    activeTab.value = 'clue'
    ElMessage.warning('请先填写拉取账号 ID；不填不会全量拉取，真实同步会失败')
    return
  }
  const changedDangerFields = buildProviderDangerList()
  if (changedDangerFields.length) {
    await ElMessageBox.confirm(
      `本次会修改 ${changedDangerFields.join('、')}，可能影响真实授权、回调或核销链路，确认继续保存吗？`,
      '确认保存',
      {
        type: 'warning',
        confirmButtonText: '继续保存',
        cancelButtonText: '取消'
      }
    )
  }

  savingProvider.value = true
  try {
    const payload = await saveIntegrationProvider({
      ...provider,
      callbackUrl: backendCallbackUrl.value
    })
    applyProvider(payload)
    if (!jobForm.providerId && provider.id) {
      jobForm.providerId = provider.id
    }
    ElMessage.success('抖音来客配置已保存')
  } finally {
    savingProvider.value = false
  }
}

async function handleTestProvider() {
  testingProvider.value = true
  try {
    applyProvider(await testIntegrationProvider({ ...provider, callbackUrl: backendCallbackUrl.value }))
    ElMessage.success(provider.lastTestMessage || '抖音来客连接测试完成')
  } finally {
    testingProvider.value = false
  }
}

function handleTestVoucherReadiness() {
  if (voucherReadiness.value.type === 'success') {
    ElMessage.success('抖音券核销配置模拟检查通过；真实核销仍以门店核销时外部接口返回为准')
    return
  }
  activeTab.value = 'voucher'
  ElMessage.warning(voucherReadiness.value.message)
}

async function handleSaveJob() {
  savingJob.value = true
  try {
    await saveSchedulerJob({
      ...jobForm,
      providerId: provider.id || jobForm.providerId
    })
    await loadView()
    ElMessage.success('调度配置已保存')
  } finally {
    savingJob.value = false
  }
}

async function handleTrigger() {
  triggering.value = true
  try {
    await triggerSchedulerJob({
      jobCode: jobForm.jobCode,
      payload: JSON.stringify({ source: 'platform-douyin' })
    })
    await loadView()
    ElMessage.success('调度任务已触发')
  } finally {
    triggering.value = false
  }
}
</script>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.action-group--section {
  margin-top: 20px;
}

.config-alert {
  margin-bottom: 16px;
}

.danger-text {
  color: #dc2626;
  font-weight: 700;
}

.platform-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
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

@media (max-width: 1280px) {
  .metrics-row--six {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .metrics-row--six,
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
