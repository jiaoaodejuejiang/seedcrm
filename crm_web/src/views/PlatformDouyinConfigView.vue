<template>
  <div class="stack-page">
    <section class="metrics-row metrics-row--six">
      <article v-for="card in overviewCards" :key="card.label" class="metric-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.hint }}</small>
      </article>
    </section>

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
              <h3>授权状态</h3>
              <p>Auth Code 状态：{{ provider.authCodeStatus || '--' }}</p>
              <p>授权状态：{{ provider.authStatus || '--' }}</p>
              <p>Access Token：{{ provider.accessTokenMasked || '--' }}</p>
              <p>Refresh Token：{{ provider.refreshTokenMasked || '--' }}</p>
            </article>

            <article class="detail-card">
              <h3>同步状态</h3>
              <p>最近测试：{{ provider.lastTestStatus || '--' }}</p>
              <p>最近回调：{{ provider.lastCallbackStatus || '--' }}</p>
              <p>最近同步：{{ formatDateTime(provider.lastSyncTime) || '--' }}</p>
              <p>下次执行：{{ formatDateTime(jobForm.nextRunTime) || '--' }}</p>
            </article>
          </div>

          <el-table :data="callbackPagination.rows" stripe>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.receivedAt) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120" prop="processStatus" />
            <el-table-column label="可信度" width="140" prop="signatureStatus" />
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

        <el-tab-pane label="API 接入" name="api">
          <div class="form-grid">
            <div class="full-span form-group-title">基础接入</div>
            <label>
              <span>Provider Code</span>
              <el-input v-model="provider.providerCode" readonly />
            </label>
            <label>
              <span>接口名称</span>
              <el-input :model-value="provider.providerName" readonly />
            </label>
            <label>
              <span>运行模式</span>
              <el-select v-model="provider.executionMode">
                <el-option label="MOCK" value="MOCK" />
                <el-option label="LIVE" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>授权类型</span>
              <el-input :model-value="formatAuthType(provider.authType)" readonly />
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="provider.appId" placeholder="开放平台 app_id" />
            </label>
            <label>
              <span>应用 Secret</span>
              <el-input
                v-model="provider.clientSecret"
                type="password"
                show-password
                :placeholder="provider.clientSecretMasked || '留空则保持原值'"
              />
            </label>
            <label>
              <span>Base URL</span>
              <el-input v-model="provider.baseUrl" placeholder="https://api.oceanengine.com" />
            </label>
            <label>
              <span>Token URL</span>
              <el-input v-model="provider.tokenUrl" placeholder="https://api.oceanengine.com/open_api/oauth2/access_token/" />
            </label>
            <label class="full-span">
              <span>拉取接口</span>
              <el-input v-model="provider.endpointPath" placeholder="/open_api/2/tools/clue/life/get/" />
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="OAuth 回调" name="oauth">
          <div class="form-grid">
            <label class="full-span">
              <span>固定回调入口</span>
              <el-input :model-value="backendCallbackUrl" readonly />
            </label>
            <label class="full-span">
              <span>Redirect URI</span>
              <el-input v-model="provider.redirectUri" placeholder="开放平台授权回跳地址" />
            </label>
            <label class="full-span">
              <span>回调登记地址</span>
              <el-input v-model="provider.callbackUrl" placeholder="用于页面展示和审计的回调地址" />
            </label>
            <label>
              <span>Scope</span>
              <el-input v-model="provider.scope" placeholder="可选" />
            </label>
            <label>
              <span>AuthCode</span>
              <el-input v-model="provider.authCode" :placeholder="provider.authCodeMasked || '回调后自动回填'" />
            </label>
            <label>
              <span>AuthCode 状态</span>
              <el-input :model-value="provider.authCodeStatus || '--'" readonly />
            </label>
            <label>
              <span>Access Token</span>
              <el-input :model-value="provider.accessTokenMasked || '--'" readonly />
            </label>
            <label>
              <span>Refresh Token</span>
              <el-input :model-value="provider.refreshTokenMasked || '--'" readonly />
            </label>
            <label>
              <span>Token 到期</span>
              <el-input :model-value="formatDateTime(provider.tokenExpiresAt) || '--'" readonly />
            </label>
            <label>
              <span>Refresh 到期</span>
              <el-input :model-value="formatDateTime(provider.refreshTokenExpiresAt) || '--'" readonly />
            </label>
            <label class="full-span">
              <span>最近回调结果</span>
              <el-input :model-value="provider.lastCallbackMessage || '--'" readonly />
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="同步任务" name="sync">
          <div class="form-grid">
            <div class="full-span form-group-title">拉取参数</div>
            <label class="full-span">
              <span>Local Account Ids</span>
              <el-input v-model="provider.localAccountIds" placeholder="多个以英文逗号分隔" />
            </label>
            <label>
              <span>兼容 AccountId</span>
              <el-input v-model="provider.accountId" placeholder="兼容旧字段，可留空" />
            </label>
            <label>
              <span>兼容 Life AccountIds</span>
              <el-input v-model="provider.lifeAccountIds" placeholder="兼容旧字段，可留空" />
            </label>
            <label>
              <span>每页条数</span>
              <el-input-number v-model="provider.pageSize" :min="1" :max="100" controls-position="right" />
            </label>
            <label>
              <span>拉取窗口(分钟)</span>
              <el-input-number v-model="provider.pullWindowMinutes" :min="10" controls-position="right" />
            </label>
            <label>
              <span>重叠窗口(分钟)</span>
              <el-input-number v-model="provider.overlapMinutes" :min="0" controls-position="right" />
            </label>
            <label>
              <span>超时(毫秒)</span>
              <el-input-number v-model="provider.requestTimeoutMs" :min="1000" :step="1000" controls-position="right" />
            </label>

            <div class="full-span form-group-title">调度任务</div>
            <label>
              <span>JobCode</span>
              <el-input v-model="jobForm.jobCode" readonly />
            </label>
            <label>
              <span>同步模式</span>
              <el-input v-model="jobForm.syncMode" readonly />
            </label>
            <label>
              <span>间隔(分钟)</span>
              <el-input-number v-model="jobForm.intervalMinutes" :min="1" controls-position="right" />
            </label>
            <label>
              <span>重试次数</span>
              <el-input-number v-model="jobForm.retryLimit" :min="0" controls-position="right" />
            </label>
            <label>
              <span>队列名</span>
              <el-input v-model="jobForm.queueName" />
            </label>
            <label>
              <span>状态</span>
              <el-select v-model="jobForm.status">
                <el-option label="ENABLED" value="ENABLED" />
                <el-option label="DISABLED" value="DISABLED" />
              </el-select>
            </label>
          </div>

          <div class="action-group action-group--section">
            <el-button type="primary" :loading="savingJob" @click="handleSaveJob">保存任务</el-button>
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
import { formatDateTime } from '../utils/format'

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

const provider = reactive(createProvider())
const jobForm = reactive(createJobForm())

const backendCallbackUrl = computed(() => {
  const host = window.location.hostname || '127.0.0.1'
  return `http://${host}:8080/scheduler/oauth/douyin/callback`
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
    return '当前回调可信度仍是本地放行/未验证，请在正式环境完成平台侧回调校验后再作为正式接入。'
  }
  return ''
})

const overviewCards = computed(() => [
  {
    label: '接入模式',
    value: formatAuthType(provider.authType),
    hint: provider.enabled === 1 ? '当前已启用' : '当前已停用'
  },
  {
    label: '后端模式',
    value: provider.executionMode || 'MOCK',
    hint: provider.providerCode || DOUYIN_CODE
  },
  {
    label: '授权状态',
    value: provider.authStatus || 'UNAUTHORIZED',
    hint: provider.authCodeStatus || '未收到授权码'
  },
  {
    label: '最近测试',
    value: provider.lastTestStatus || '--',
    hint: formatDateTime(provider.lastTestAt) || '未测试'
  },
  {
    label: '最近回调',
    value: provider.lastCallbackStatus || '--',
    hint: formatDateTime(provider.lastCallbackAt) || '未收到'
  },
  {
    label: '最近同步',
    value: formatDateTime(provider.lastSyncTime) || '--',
    hint: formatDateTime(jobForm.nextRunTime) || '未排程'
  }
])

onMounted(async () => {
  await loadView()
})

function createProvider() {
  return {
    id: null,
    providerCode: DOUYIN_CODE,
    providerName: '抖音来客线索',
    moduleCode: 'CLUE',
    executionMode: 'MOCK',
    authType: 'CLIENT_TOKEN',
    appId: '',
    baseUrl: 'https://api.oceanengine.com',
    tokenUrl: 'https://api.oceanengine.com/open_api/oauth2/access_token/',
    endpointPath: '/open_api/2/tools/clue/life/get/',
    clientKey: '',
    clientSecret: '',
    clientSecretMasked: '',
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
    localAccountIds: provider.localAccountIds
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
    ['callbackUrl', '回调地址'],
    ['localAccountIds', 'Local Account Ids']
  ].filter(([key]) => providerSnapshot.value[key] !== provider[key]).map(([, label]) => label)
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
      `本次会修改 ${changedDangerFields.join('、')}，可能影响真实授权和同步，确认继续保存吗？`,
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
    const payload = await saveIntegrationProvider({ ...provider })
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
    applyProvider(await testIntegrationProvider({ ...provider }))
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
    ElMessage.success('同步任务已保存')
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
    ElMessage.success('同步任务已触发')
  } finally {
    triggering.value = false
  }
}
</script>

<style scoped>
.metrics-row--six {
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

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
