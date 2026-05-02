<template>
  <div class="stack-page go-live-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>运行环境</span>
        <strong>{{ summary.environmentMode || '--' }}</strong>
      </article>
      <article class="summary-pill">
        <span>API 域名</span>
        <strong>{{ summary.domainSettings?.apiBaseUrl || '--' }}</strong>
      </article>
      <article class="summary-pill">
        <span>清理保护</span>
        <strong>{{ summary.safeToClearTestData ? '允许真实清理' : '真实清理阻断' }}</strong>
      </article>
      <article class="summary-pill">
        <span>上线结论</span>
        <strong>{{ readinessConclusion }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>上线工具</h3>
        </div>
        <div class="action-group">
          <el-button plain @click="openDoc('/docs/deployment-runbook.html')">快速部署手册</el-button>
          <el-button plain @click="openDoc('/docs/baota-panel-docker-deployment.html')">宝塔部署说明</el-button>
          <el-button :loading="loading" @click="loadSummary">刷新预检</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="上线预检" name="summary">
          <div v-if="summary.warnings?.length" class="warning-list">
            <el-alert
              v-for="item in summary.warnings"
              :key="item"
              type="warning"
              show-icon
              :closable="false"
              :title="item"
            />
          </div>

          <div class="readiness-grid" v-loading="loading">
            <article v-for="item in summary.readinessItems || []" :key="item.key" class="readiness-card">
              <el-tag :type="item.status === 'PASS' ? 'success' : 'warning'" effect="light">
                {{ item.status === 'PASS' ? '通过' : '待确认' }}
              </el-tag>
              <strong>{{ item.label }}</strong>
              <span>{{ item.message }}</span>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="一键初始化" name="initialize">
          <div class="form-grid">
            <label>
              <span>目标环境</span>
              <el-select v-model="initForm.targetEnvironment">
                <el-option label="测试环境" value="TEST" />
                <el-option label="预发环境" value="STAGING" />
                <el-option label="正式环境" value="PROD" />
              </el-select>
            </label>
            <label>
              <span>当前服务器环境</span>
              <span class="readonly-prefix">{{ summary.environmentMode || '--' }}</span>
            </label>
            <label>
              <span>系统基础域名</span>
              <el-input v-model="initForm.systemBaseUrl" placeholder="https://crm.example.com" />
            </label>
            <label>
              <span>API 域名</span>
              <el-input v-model="initForm.apiBaseUrl" placeholder="https://api.example.com" />
            </label>
            <label>
              <span>确认词</span>
              <el-input v-model="initForm.confirmText" :placeholder="initConfirmText" />
            </label>
            <label class="switch-row">
              <span>集成配置切回 MOCK</span>
              <el-switch v-model="initForm.resetIntegrationToMock" :disabled="initForm.targetEnvironment === 'PROD'" />
            </label>
            <label class="switch-row">
              <span>初始化后停用调度任务</span>
              <el-switch v-model="initForm.disableSchedulers" />
            </label>
          </div>
          <div class="ops-footer">
            <el-button type="primary" :loading="operating" @click="handleInitialize">执行初始化</el-button>
            <span>测试/预发确认词为 INIT_SYSTEM；正式环境确认词为 INIT_PROD_SYSTEM。</span>
          </div>
        </el-tab-pane>

        <el-tab-pane label="清理测试数据" name="clear">
          <el-alert
            class="queue-alert"
            type="warning"
            show-icon
            :closable="false"
            title="真实清理默认被服务端硬保护阻断。必须先预览，确认非生产库，并显式开启 SEEDCRM_ALLOW_CLEAR_TEST_DATA=true 后才可执行。"
          />
          <div class="clear-toolbar">
            <el-checkbox v-model="clearForm.dryRun">只预览，不删除</el-checkbox>
            <el-checkbox v-model="clearForm.includeOperationalLogs">同时清理操作日志</el-checkbox>
            <el-input v-model="clearForm.confirmText" class="confirm-input" placeholder="输入 CLEAR_TEST_DATA" />
            <el-button :type="clearForm.dryRun ? 'default' : 'danger'" :disabled="!canSubmitClear" :loading="operating" @click="handleClear">
              {{ clearForm.dryRun ? '预览清理' : '执行清理' }}
            </el-button>
          </div>

          <el-table :data="summary.tableCounts || []" stripe height="420" empty-text="暂无可清理数据表">
            <el-table-column prop="tableName" label="数据表" min-width="190" />
            <el-table-column prop="category" label="类型" min-width="160" />
            <el-table-column prop="rowCount" label="当前数据量" width="120" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.exists ? 'info' : 'warning'" effect="light">
                  {{ row.exists ? '已检测' : '不存在' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="lastOperation.tables?.length" class="operation-result">
            <strong>{{ lastOperation.dryRun ? '预览结果' : '清理结果' }}</strong>
            <span>影响行数：{{ lastOperation.affectedRows || 0 }}</span>
            <el-table :data="lastOperation.tables" size="small" stripe max-height="260">
              <el-table-column prop="tableName" label="数据表" min-width="180" />
              <el-table-column prop="rowCountBefore" label="清理前" width="100" />
              <el-table-column prop="affectedRows" label="影响行数" width="100" />
              <el-table-column prop="message" label="结果" min-width="160" />
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearGoLiveTestData, fetchGoLiveSummary, initializeGoLive } from '../api/systemConfig'

const loading = ref(false)
const operating = ref(false)
const activeTab = ref('summary')
const summary = reactive({})
const lastOperation = reactive({})
const clearPreviewSignature = ref('')

const initForm = reactive({
  targetEnvironment: 'TEST',
  systemBaseUrl: '',
  apiBaseUrl: '',
  resetIntegrationToMock: true,
  disableSchedulers: false,
  confirmText: ''
})

const clearForm = reactive({
  confirmText: '',
  includeOperationalLogs: false,
  dryRun: true
})

const initConfirmText = computed(() => (initForm.targetEnvironment === 'PROD' ? 'INIT_PROD_SYSTEM' : 'INIT_SYSTEM'))
const canSubmitClear = computed(() => clearForm.dryRun || summary.safeToClearTestData)
const currentClearSignature = computed(() => JSON.stringify({
  includeOperationalLogs: clearForm.includeOperationalLogs
}))
const lastClearPreviewValid = computed(() => (
  lastOperation.operation === 'CLEAR_TEST_DATA'
  && lastOperation.dryRun
  && clearPreviewSignature.value === currentClearSignature.value
))
const readinessConclusion = computed(() => {
  const items = summary.readinessItems || []
  const hasWarn = items.some((item) => item.status !== 'PASS')
  if (!summary.domainSettings?.apiBaseUrl || !summary.domainSettings?.systemBaseUrl) {
    return '不可上线'
  }
  return hasWarn ? '待处理' : '可上线'
})

onMounted(loadSummary)

watch(() => clearForm.includeOperationalLogs, () => {
  clearPreviewSignature.value = ''
})

async function loadSummary() {
  loading.value = true
  try {
    const data = await fetchGoLiveSummary()
    Object.assign(summary, data || {})
    initForm.systemBaseUrl = data?.domainSettings?.systemBaseUrl || ''
    initForm.apiBaseUrl = data?.domainSettings?.apiBaseUrl || ''
    initForm.targetEnvironment = data?.environmentMode || 'TEST'
  } finally {
    loading.value = false
  }
}

function openDoc(path) {
  window.open(path, '_blank', 'noopener,noreferrer')
}

async function handleInitialize() {
  const isProd = initForm.targetEnvironment === 'PROD'
  await ElMessageBox.confirm(
    `当前服务器环境：${summary.environmentMode || '--'}；目标环境：${initForm.targetEnvironment}。本操作会写入域名、环境模式，并可能影响集成模式或调度状态。`,
    isProd ? '确认初始化正式环境' : '确认执行上线初始化',
    {
      type: isProd ? 'error' : 'warning',
      confirmButtonText: isProd ? '确认初始化 PROD' : '确认初始化',
      cancelButtonText: '取消'
    }
  )
  operating.value = true
  try {
    const result = await initializeGoLive({ ...initForm })
    Object.assign(lastOperation, result || {})
    ElMessage.success('上线初始化已完成')
    await loadSummary()
  } finally {
    operating.value = false
  }
}

async function handleClear() {
  if (!clearForm.dryRun && !lastClearPreviewValid.value) {
    ElMessage.warning('请先按当前清理范围执行一次“预览清理”，确认命中范围后再执行真实清理。')
    return
  }
  if (!clearForm.dryRun) {
    await ElMessageBox.confirm(`当前环境：${summary.environmentMode || '--'}。将清理业务数据、队列数据${clearForm.includeOperationalLogs ? '和操作日志' : ''}；上次预览命中 ${lastOperation.tables?.length || 0} 个表。`, '确认清理测试数据', {
      type: 'warning',
      confirmButtonText: '确认清理',
      cancelButtonText: '取消'
    })
  }
  operating.value = true
  try {
    const result = await clearGoLiveTestData({ ...clearForm })
    Object.assign(lastOperation, result || {})
    clearPreviewSignature.value = result?.dryRun ? currentClearSignature.value : ''
    ElMessage.success(clearForm.dryRun ? '清理预览已生成' : '测试数据已清理')
    await loadSummary()
  } finally {
    operating.value = false
  }
}
</script>

<style scoped>
.go-live-page .summary-pill strong {
  font-size: 15px;
  word-break: break-all;
}

.warning-list {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}

.readiness-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.readiness-card {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
}

.readiness-card strong {
  color: #111827;
}

.readiness-card span:last-child {
  color: #64748b;
  line-height: 1.55;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ops-footer,
.clear-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.ops-footer span {
  color: #64748b;
}

.confirm-input {
  width: 260px;
}

.operation-result {
  display: grid;
  gap: 12px;
  margin-top: 18px;
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
}
</style>
