<template>
  <div class="stack-page">
    <section v-if="callbackRisk" class="panel compact-panel">
      <el-alert :title="callbackRisk" type="error" show-icon :closable="false" />
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>{{ isPrivateDomainWorkspace ? '企业微信工作台' : '企业微信接入' }}</h3>
        </div>
        <div v-if="!isPrivateDomainWorkspace" class="action-group">
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          <el-button :loading="testing" @click="handleTest">测试连接</el-button>
        </div>
      </div>

      <template v-if="isPrivateDomainWorkspace">
        <div class="workspace-link-grid">
          <RouterLink v-for="item in workspaceLinks" :key="item.to" :to="item.to" class="workspace-link-card">
            <strong>{{ item.label }}</strong>
          </RouterLink>
        </div>

        <div class="status-strip">
          <div class="status-pill">
            <span>接入模式</span>
            <strong>{{ authModeLabel }}</strong>
          </div>
          <div class="status-pill">
            <span>运行模式</span>
            <strong>{{ formatExecutionMode(config.executionMode || 'MOCK') }}</strong>
          </div>
          <div class="status-pill">
            <span>授权状态</span>
            <strong>{{ formatAuthStatus(config.authStatus || 'UNAUTHORIZED') }}</strong>
          </div>
          <div class="status-pill">
            <span>最近回调</span>
            <strong>{{ formatDateTime(config.lastCallbackAt) || '--' }}</strong>
          </div>
        </div>

        <div class="overview-grid">
          <article class="detail-card">
            <h3>凭证状态</h3>
            <p>Suite Ticket：{{ config.suiteTicketMasked || '--' }}</p>
            <p>Permanent Code：{{ config.permanentCodeMasked || '--' }}</p>
            <p>Access Token：{{ config.accessTokenMasked || '--' }}</p>
            <p>Suite Access Token：{{ config.suiteAccessTokenMasked || '--' }}</p>
            <p>企业 Access Token：{{ config.corpAccessTokenMasked || '--' }}</p>
          </article>

          <article class="detail-card">
            <h3>有效期</h3>
            <p>授权码时间：{{ formatDateTime(config.lastAuthCodeAt) || '--' }}</p>
            <p>Suite Ticket 时间：{{ formatDateTime(config.suiteTicketAt) || '--' }}</p>
            <p>Token 到期：{{ formatDateTime(config.tokenExpiresAt) || '--' }}</p>
            <p>Suite Token 到期：{{ formatDateTime(config.suiteAccessTokenExpiresAt) || '--' }}</p>
            <p>企业 Token 到期：{{ formatDateTime(config.corpAccessTokenExpiresAt) || '--' }}</p>
          </article>
        </div>

        <el-table :data="callbackLogPagination.rows" stripe>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.receivedAt) || '--' }}
            </template>
          </el-table-column>
          <el-table-column label="应用编码" min-width="140" prop="appCode" />
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
            :total="callbackLogPagination.total"
            :current-page="callbackLogPagination.currentPage"
            :page-size="callbackLogPagination.pageSize"
            :page-sizes="callbackLogPagination.pageSizes"
            @size-change="callbackLogPagination.handleSizeChange"
            @current-change="callbackLogPagination.handleCurrentChange"
          />
        </div>
      </template>

      <el-tabs v-else v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="概览" name="overview">
          <div class="status-strip">
            <div class="status-pill">
              <span>接入模式</span>
              <strong>{{ authModeLabel }}</strong>
            </div>
            <div class="status-pill">
              <span>运行模式</span>
              <strong>{{ formatExecutionMode(config.executionMode || 'MOCK') }}</strong>
            </div>
            <div class="status-pill">
              <span>授权状态</span>
              <strong>{{ formatAuthStatus(config.authStatus || 'UNAUTHORIZED') }}</strong>
            </div>
            <div class="status-pill">
              <span>最近回调</span>
              <strong>{{ formatDateTime(config.lastCallbackAt) || '--' }}</strong>
            </div>
          </div>

          <div class="overview-grid">
            <article class="detail-card">
              <h3>凭证状态</h3>
              <p>Suite Ticket：{{ config.suiteTicketMasked || '--' }}</p>
              <p>Permanent Code：{{ config.permanentCodeMasked || '--' }}</p>
              <p>Access Token：{{ config.accessTokenMasked || '--' }}</p>
              <p>Suite Access Token：{{ config.suiteAccessTokenMasked || '--' }}</p>
              <p>企业 Access Token：{{ config.corpAccessTokenMasked || '--' }}</p>
            </article>

            <article class="detail-card">
              <h3>有效期</h3>
              <p>授权码时间：{{ formatDateTime(config.lastAuthCodeAt) || '--' }}</p>
              <p>Suite Ticket 时间：{{ formatDateTime(config.suiteTicketAt) || '--' }}</p>
              <p>Token 到期：{{ formatDateTime(config.tokenExpiresAt) || '--' }}</p>
              <p>Suite Token 到期：{{ formatDateTime(config.suiteAccessTokenExpiresAt) || '--' }}</p>
              <p>企业 Token 到期：{{ formatDateTime(config.corpAccessTokenExpiresAt) || '--' }}</p>
            </article>
          </div>

          <el-table :data="callbackLogPagination.rows" stripe>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.receivedAt) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="应用编码" min-width="140" prop="appCode" />
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
              :total="callbackLogPagination.total"
              :current-page="callbackLogPagination.currentPage"
              :page-size="callbackLogPagination.pageSize"
              :page-sizes="callbackLogPagination.pageSizes"
              @size-change="callbackLogPagination.handleSizeChange"
              @current-change="callbackLogPagination.handleCurrentChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="接入配置" name="auth">
          <div class="form-grid">
            <div class="full-span form-group-title">模式选择</div>
            <label>
              <span>授权模式</span>
              <el-select v-model="config.authMode">
                <el-option label="自有企业微信" value="SELF_BUILT" />
                <el-option label="服务商模式" value="SERVICE_PROVIDER" />
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
              <span>启用状态</span>
              <el-select v-model="config.enabled">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>应用编码</span>
              <el-input v-model="config.appCode" placeholder="默认 PRIVATE_DOMAIN" />
            </label>

            <template v-if="config.authMode === 'SELF_BUILT'">
              <div class="full-span form-group-title">自建企业微信</div>
              <label>
                <span>企业 CorpId</span>
                <el-input v-model="config.corpId" placeholder="请输入企业 CorpId" />
              </label>
              <label>
                <span>应用 AgentId</span>
                <el-input v-model="config.agentId" placeholder="请输入应用 AgentId" />
              </label>
              <label>
                <span>AppId</span>
                <el-input v-model="config.appId" placeholder="可选" />
              </label>
              <label>
                <span>应用 Secret</span>
                <el-input
                  v-model="config.appSecret"
                  type="password"
                  show-password
                  :placeholder="config.appSecretMasked || '留空则保持原值'"
                />
              </label>
            </template>

            <template v-else>
              <div class="full-span form-group-title">服务商授权</div>
              <label>
                <span>服务商 SuiteId</span>
                <el-input v-model="config.suiteId" placeholder="请输入服务商 SuiteId" />
              </label>
              <label>
                <span>服务商 Secret</span>
                <el-input
                  v-model="config.suiteSecret"
                  type="password"
                  show-password
                  :placeholder="config.suiteSecretMasked || '留空则保持原值'"
                />
              </label>
              <label>
                <span>授权企业 CorpId</span>
                <el-input v-model="config.authCorpId" placeholder="回调后自动回填，也可手动补录" />
              </label>
              <label>
                <span>应用 AgentId</span>
                <el-input v-model="config.agentId" placeholder="请输入应用 AgentId" />
              </label>
              <label>
                <span>授权码</span>
                <el-input v-model="config.authCode" :placeholder="config.authCodeMasked || '回调后自动回填'" />
              </label>
              <label>
                <span>永久授权码</span>
                <el-input v-model="config.permanentCode" :placeholder="config.permanentCodeMasked || '换取后自动回填'" />
              </label>
            </template>

            <div class="full-span form-group-title">只读令牌</div>
            <label>
              <span>Access Token</span>
              <el-input :model-value="config.accessTokenMasked || '--'" readonly />
            </label>
            <label>
              <span>Refresh Token</span>
              <el-input :model-value="config.refreshTokenMasked || '--'" readonly />
            </label>
            <label>
              <span>Suite Access Token</span>
              <el-input :model-value="config.suiteAccessTokenMasked || '--'" readonly />
            </label>
            <label>
              <span>企业 Access Token</span>
              <el-input :model-value="config.corpAccessTokenMasked || '--'" readonly />
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="回调与安全" name="callback">
          <div class="form-grid">
            <label>
              <span>系统基础域名</span>
              <span class="readonly-prefix">{{ systemBaseUrl }}</span>
            </label>
            <label>
              <span>API 域名</span>
              <span class="readonly-prefix">{{ apiBaseUrl }}</span>
            </label>
            <label class="full-span">
              <span>回调地址</span>
              <span class="readonly-prefix">{{ wecomCallbackUrl }}</span>
            </label>
            <label>
              <span>授权回跳地址</span>
              <el-input v-model="config.redirectUri" placeholder="服务商授权回跳地址" />
            </label>
            <label>
              <span>受信域名</span>
              <el-input v-model="config.trustedDomain" placeholder="例如 crm.example.com" />
            </label>
            <label>
              <span>回调 Token</span>
              <el-input
                v-model="config.callbackToken"
                type="password"
                show-password
                :placeholder="config.callbackTokenMasked || '留空则保持原值'"
              />
            </label>
            <label>
              <span>EncodingAESKey</span>
              <el-input
                v-model="config.encodingAesKey"
                type="password"
                show-password
                :placeholder="config.encodingAesKeyMasked || '留空则保持原值'"
              />
            </label>
            <label>
              <span>跳过验签</span>
              <el-select v-model="config.skipVerify">
                <el-option :value="1" label="是" />
                <el-option :value="0" label="否" />
              </el-select>
            </label>
            <label class="full-span">
              <span>State 模板</span>
              <el-input v-model="config.stateTemplate" placeholder="例如 seedcrm:{scene}:{strategy}:{codeName}" />
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="业务默认" name="business">
          <div class="form-grid">
            <label>
              <span>联系我类型</span>
              <el-select v-model="config.liveCodeType">
                <el-option :value="1" label="单人" />
                <el-option :value="2" label="多人" />
              </el-select>
            </label>
            <label>
              <span>联系我场景</span>
              <el-select v-model="config.liveCodeScene">
                <el-option :value="1" label="小程序" />
                <el-option :value="2" label="二维码" />
              </el-select>
            </label>
            <label>
              <span>活码样式</span>
              <el-input-number v-model="config.liveCodeStyle" :min="1" controls-position="right" />
            </label>
            <label class="full-span">
              <span>来源备注</span>
              <el-input v-model="config.markSource" placeholder="例如 客资中心活码轮询" />
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
import { RouterLink } from 'vue-router'
import { fetchWecomCallbackLogs, fetchWecomConfig, saveWecomConfig, testWecomConfig } from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import { currentUser } from '../utils/auth'
import { formatAuthStatus, formatCallbackProcessStatus, formatCallbackSignatureStatus, formatDateTime, formatExecutionMode } from '../utils/format'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'

const activeTab = ref('overview')
const saving = ref(false)
const testing = ref(false)
const callbackLogs = ref([])
const callbackLogPagination = useTablePagination(callbackLogs)
const loadedSnapshot = ref(null)
const systemState = loadSystemConsoleState()

const config = reactive(createConfig())

const authModeLabel = computed(() => (config.authMode === 'SERVICE_PROVIDER' ? '服务商模式' : '自有企业微信'))
const isPrivateDomainWorkspace = computed(() => currentUser.value?.roleCode === 'PRIVATE_DOMAIN_SERVICE')
const systemBaseUrl = computed(() => String(systemState.domainSettings?.systemBaseUrl || '').trim() || '--')
const apiBaseUrl = computed(() => String(systemState.domainSettings?.apiBaseUrl || '').trim() || '--')
const workspaceLinks = computed(() => [
  { to: '/private-domain/live-code', label: '活码配置', meta: '去生成轮询活码' },
  { to: '/private-domain/customer-profile', label: '客户画像', meta: '查看画像能力开关' },
  { to: '/private-domain/moments', label: '朋友圈群发', meta: '进入企业微信群发任务' },
  { to: '/private-domain/tags', label: '标签管理', meta: '维护客户标签与统计' }
])
const wecomCallbackUrl = computed(() => buildSystemUrl(systemState, 'callback', '/wecom/callback'))
const callbackRisk = computed(() => {
  if (config.executionMode !== 'LIVE' || config.skipVerify !== 1) {
    return ''
  }
  if (!config.callbackUrl || /localhost|127\.0\.0\.1|0\.0\.0\.0/.test(config.callbackUrl)) {
    return ''
  }
  return 'LIVE 模式下公网回调不能跳过验签，请改为开启验签后再保存。'
})

onMounted(async () => {
  await loadConfig()
})

function createConfig() {
  return {
    id: null,
    appCode: 'PRIVATE_DOMAIN',
    appId: '',
    suiteId: '',
    authMode: 'SELF_BUILT',
    corpId: '',
    authCorpId: '',
    agentId: '',
    appSecret: '',
    appSecretMasked: '',
    suiteSecret: '',
    suiteSecretMasked: '',
    authCode: '',
    authCodeMasked: '',
    suiteTicket: '',
    suiteTicketMasked: '',
    permanentCode: '',
    permanentCodeMasked: '',
    accessToken: '',
    accessTokenMasked: '',
    refreshToken: '',
    refreshTokenMasked: '',
    suiteAccessToken: '',
    suiteAccessTokenMasked: '',
    corpAccessToken: '',
    corpAccessTokenMasked: '',
    executionMode: 'MOCK',
    callbackUrl: '',
    redirectUri: '',
    trustedDomain: '',
    callbackToken: '',
    callbackTokenMasked: '',
    encodingAesKey: '',
    encodingAesKeyMasked: '',
    liveCodeType: 2,
    liveCodeScene: 2,
    liveCodeStyle: 1,
    skipVerify: 1,
    stateTemplate: 'seedcrm:{scene}:{strategy}:{codeName}',
    markSource: '',
    enabled: 1,
    lastTokenStatus: '',
    lastTokenMessage: '',
    authStatus: '',
    lastCallbackStatus: '',
    lastCallbackMessage: '',
    lastTokenCheckedAt: '',
    suiteTicketAt: '',
    lastAuthCodeAt: '',
    tokenExpiresAt: '',
    suiteAccessTokenExpiresAt: '',
    corpAccessTokenExpiresAt: '',
    lastCallbackAt: ''
  }
}

function applyConfig(payload) {
  Object.assign(config, createConfig(), payload || {})
  config.appSecret = ''
  config.suiteSecret = ''
  config.callbackToken = ''
  config.encodingAesKey = ''
  loadedSnapshot.value = JSON.parse(JSON.stringify({
    authMode: config.authMode,
    executionMode: config.executionMode,
    corpId: config.corpId,
    authCorpId: config.authCorpId,
    agentId: config.agentId,
    suiteId: config.suiteId,
    callbackUrl: config.callbackUrl
  }))
}

async function loadConfig() {
  applyConfig(await fetchWecomConfig())
  callbackLogs.value = await fetchWecomCallbackLogs(config.appCode || undefined)
  callbackLogPagination.reset()
}

function buildDangerList() {
  if (!loadedSnapshot.value) {
    return []
  }
  const dangerKeys = [
    ['authMode', '授权模式'],
    ['executionMode', '运行模式'],
    ['corpId', 'CorpId'],
    ['authCorpId', '授权企业 CorpId'],
    ['agentId', 'AgentId'],
    ['suiteId', 'SuiteId'],
    ['callbackUrl', '回调地址']
  ]
  return dangerKeys.filter(([key]) => loadedSnapshot.value[key] !== config[key]).map(([, label]) => label)
}

async function handleSave() {
  const changedDangerFields = buildDangerList()
  if (changedDangerFields.length) {
    await ElMessageBox.confirm(
      `本次会修改 ${changedDangerFields.join('、')}，可能影响真实回调与授权状态，确认继续保存吗？`,
      '确认保存',
      {
        type: 'warning',
        confirmButtonText: '继续保存',
        cancelButtonText: '取消'
      }
    )
  }
  saving.value = true
  try {
    applyConfig(await saveWecomConfig({ ...config, callbackUrl: wecomCallbackUrl.value }))
    callbackLogs.value = await fetchWecomCallbackLogs(config.appCode || undefined)
    callbackLogPagination.reset()
    ElMessage.success('企业微信配置已保存')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    applyConfig(await testWecomConfig({ ...config, callbackUrl: wecomCallbackUrl.value }))
    ElMessage.success(config.lastTokenMessage || '企业微信连接测试完成')
  } finally {
    testing.value = false
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

.workspace-link-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.workspace-link-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 72px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #ffffff;
  color: inherit;
  text-decoration: none;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.workspace-link-card::after {
  content: '›';
  color: var(--el-color-primary);
  font-size: 22px;
  line-height: 1;
}

.workspace-link-card:hover {
  transform: translateY(-2px);
  border-color: rgba(37, 99, 235, 0.2);
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.08);
}

.workspace-link-card strong {
  font-size: 15px;
}

@media (max-width: 1280px) {
  .metrics-row--six {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .workspace-link-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .metrics-row--six,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .workspace-link-grid {
    grid-template-columns: 1fr;
  }
}
</style>
