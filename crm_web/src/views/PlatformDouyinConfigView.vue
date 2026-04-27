<template>
  <div class="stack-page">
    <section v-if="verificationWarning" class="panel compact-panel">
      <el-alert :title="verificationWarning" type="warning" show-icon :closable="false" />
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>抖音来客接入</h3>
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
              <p>预查询接口：{{ provider.voucherPreparePath || '--' }}</p>
              <p>核销接口：{{ provider.voucherVerifyPath || '--' }}</p>
              <p>撤销接口：{{ provider.voucherCancelPath || '--' }}</p>
              <p>默认 POI：{{ provider.poiId || '--' }}</p>
              <p>券码字段：{{ provider.verifyCodeField || '--' }}</p>
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
                {{ formatCallbackSignatureStatus(row.signatureStatus) }}
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
              <el-input v-model="provider.baseUrl" placeholder="例如 https://open.douyin.com" />
            </label>
            <label>
              <span>换取 Token 地址</span>
              <el-input v-model="provider.tokenUrl" placeholder="请输入 access_token 接口地址" />
            </label>
            <label>
              <span>系统基础域名</span>
              <el-input :model-value="systemBaseUrl" readonly />
            </label>
            <label>
              <span>API 域名</span>
              <el-input :model-value="apiBaseUrl" readonly />
            </label>
            <label class="full-span">
              <span>回调登记地址</span>
              <el-input :model-value="provider.callbackUrl || backendCallbackUrl" readonly />
            </label>
            <label class="full-span">
              <span>固定后端回调地址</span>
              <el-input :model-value="backendCallbackUrl" readonly />
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
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="线索拉取" name="clue">
          <div class="form-grid">
            <div class="full-span form-group-title">账号配置</div>
            <label>
              <span>平台账号 ID</span>
              <el-input v-model="provider.accountId" placeholder="可按平台实际字段填写" />
            </label>
            <label>
              <span>来客账号 ID</span>
              <el-input v-model="provider.lifeAccountIds" placeholder="多个账号用英文逗号分隔" />
            </label>
            <label class="full-span">
              <span>本地账号映射</span>
              <el-input v-model="provider.localAccountIds" placeholder="多个账号用英文逗号分隔" />
            </label>

            <div class="full-span form-group-title">拉取参数</div>
            <label class="full-span">
              <span>线索接口路径</span>
              <el-input v-model="provider.endpointPath" placeholder="/goodlife/v1/clue/douyin/list/" />
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
          </div>
        </el-tab-pane>

        <el-tab-pane label="任务调度" name="job">
          <div class="form-grid">
            <label>
              <span>任务编码</span>
              <el-input v-model="jobForm.jobCode" readonly />
            </label>
            <label>
              <span>同步方式</span>
              <el-input v-model="jobForm.syncMode" readonly />
            </label>
            <label>
              <span>执行间隔（分钟）</span>
              <el-input-number v-model="jobForm.intervalMinutes" :min="1" controls-position="right" />
            </label>
            <label>
              <span>重试次数</span>
              <el-input-number v-model="jobForm.retryLimit" :min="0" controls-position="right" />
            </label>
            <label>
              <span>队列名称</span>
              <el-input v-model="jobForm.queueName" placeholder="请输入队列名称" />
            </label>
            <label>
              <span>任务状态</span>
              <el-select v-model="jobForm.status">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </label>
          </div>

          <div class="action-group action-group--section">
            <el-button type="primary" :loading="savingJob" @click="handleSaveJob">保存调度</el-button>
            <el-button :loading="triggering" @click="handleTrigger">立即执行</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  formatCallbackProcessStatus,
  formatCallbackSignatureStatus,
  formatDateTime,
  formatResultStatus
} from '../utils/format'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'

const DOUYIN_CODE = 'DOUYIN_LAIKE'
const DOUYIN_JOB_CODE = 'DOUYIN_CLUE_INCREMENTAL'

const activeTab = ref('overview')
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

const backendCallbackUrl = computed(() => {
  return buildSystemUrl(systemState, 'callback', '/scheduler/oauth/douyin/callback')
})

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
    baseUrl: 'https://open.douyin.com',
    tokenUrl: 'https://open.douyin.com/oauth/access_token/',
    endpointPath: '/goodlife/v1/clue/douyin/list/',
    voucherPreparePath: '/goodlife/v1/fulfilment/certificate/prepare/',
    voucherVerifyPath: '/goodlife/v1/fulfilment/certificate/verify/',
    voucherCancelPath: '/goodlife/v1/fulfilment/certificate/cancel/',
    clientKey: '',
    clientSecret: '',
    clientSecretMasked: '',
    clientSecretConfigured: false,
    redirectUri: '',
    scope: '',
    authCode: '',
    authCodeMasked: '',
    authCodeStatus: '',
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
  Object.assign(provider, createProvider(), payload || {})
  provider.clientSecret = ''
  providerSnapshot.value = JSON.parse(JSON.stringify({
    executionMode: provider.executionMode,
    appId: provider.appId,
    callbackUrl: provider.callbackUrl,
    localAccountIds: provider.localAccountIds,
    voucherVerifyPath: provider.voucherVerifyPath,
    poiId: provider.poiId,
    verifyCodeField: provider.verifyCodeField
  }))
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

.platform-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
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
