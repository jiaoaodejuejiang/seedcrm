<template>
  <div class="stack-page">
    <section class="panel compact-panel">
      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="activeTab">
            <el-radio-button value="monitor">执行监控</el-radio-button>
            <el-radio-button value="config">任务配置</el-radio-button>
            <el-radio-button value="audit">审计记录</el-radio-button>
          </el-radio-group>
        </div>
        <div class="action-group">
          <el-button @click="loadData">刷新</el-button>
        </div>
      </div>
    </section>

    <section v-if="activeTab === 'config'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>任务配置</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="resetJobForm">新增任务</el-button>
        </div>
      </div>

      <div class="scheduler-workspace">
        <div class="inline-editor-shell scheduler-editor">
          <div class="form-grid">
            <label>
              <span>任务类型</span>
              <el-select v-model="jobPreset" @change="applyPreset">
                <el-option label="抖音客资增量拉取" value="DOUYIN_CLUE_INCREMENTAL" />
                <el-option label="分销履约回推队列" value="DISTRIBUTION_OUTBOX_PROCESS" />
                <el-option label="分销异常重试队列" value="DISTRIBUTION_EXCEPTION_RETRY" />
                <el-option label="分销状态回查" value="DISTRIBUTION_STATUS_CHECK" />
                <el-option label="分销对账拉取" value="DISTRIBUTION_RECONCILE_PULL" />
                <el-option label="自定义" value="CUSTOM" />
              </el-select>
            </label>
            <label>
              <span>任务编码</span>
              <el-input v-model="jobForm.jobCode" placeholder="例如 DISTRIBUTION_OUTBOX_PROCESS" />
            </label>
            <label>
              <span>模块</span>
              <el-select v-model="jobForm.moduleCode">
                <el-option label="客资同步" value="CLUE" />
                <el-option label="分销队列" value="DISTRIBUTION" />
              </el-select>
            </label>
            <label>
              <span>同步方式</span>
              <el-select v-model="jobForm.syncMode">
                <el-option label="增量/定时" value="INCREMENTAL" />
                <el-option label="手动触发" value="MANUAL" />
              </el-select>
            </label>
            <label>
              <span>间隔分钟</span>
              <el-input-number v-model="jobForm.intervalMinutes" :min="1" controls-position="right" />
            </label>
            <label>
              <span>重试次数</span>
              <el-input-number v-model="jobForm.retryLimit" :min="0" controls-position="right" />
            </label>
            <label>
              <span>状态</span>
              <el-select v-model="jobForm.status">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </label>
            <label>
              <span>队列名称</span>
              <el-input v-model="jobForm.queueName" placeholder="例如 distribution-outbox" />
            </label>
            <label class="full-span">
              <span>执行入口</span>
              <el-input v-model="jobForm.endpoint" placeholder="/scheduler/outbox/process" />
            </label>
            <label class="full-span">
              <span>任务说明</span>
              <span class="readonly-value">{{ currentJobDescription }}</span>
            </label>
          </div>

          <div class="action-group">
            <el-button type="primary" :loading="savingJob" @click="handleSaveJob">保存任务</el-button>
            <el-button @click="resetJobForm">重置</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="jobPagination.rows" stripe>
          <el-table-column label="任务编码" min-width="220" prop="jobCode" />
          <el-table-column label="模块" width="120">
            <template #default="{ row }">
              {{ formatModuleCode(row.moduleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="同步方式" width="120">
            <template #default="{ row }">
              {{ formatSyncMode(row.syncMode) }}
            </template>
          </el-table-column>
          <el-table-column label="周期" width="100">
            <template #default="{ row }">{{ row.intervalMinutes }} 分钟</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下次执行" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.nextRunTime) }}
            </template>
          </el-table-column>
          <el-table-column label="执行入口" min-width="240" prop="endpoint" show-overflow-tooltip />
          <el-table-column label="操作" width="270" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" @click="pickJob(row)">编辑</el-button>
                <el-button size="small" plain :loading="dryRunningJob === row.jobCode" @click="handleDryRun(row)">预检（不入库）</el-button>
                <el-button size="small" type="warning" plain :loading="triggeringJob === row.jobCode" @click="handleTrigger(row)">
                  立即执行真实任务
                </el-button>
                <el-button size="small" plain :loading="retryingJob === row.jobCode" @click="handleRetry(row)">重试失败</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="jobPagination.total"
            :current-page="jobPagination.currentPage"
            :page-size="jobPagination.pageSize"
            :page-sizes="jobPagination.pageSizes"
            @size-change="jobPagination.handleSizeChange"
            @current-change="jobPagination.handleCurrentChange"
          />
        </div>
      </div>
    </section>

    <section v-else-if="activeTab === 'monitor'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>执行监控</h3>
        </div>
        <el-select v-model="selectedJobCode" clearable placeholder="按任务筛选" style="width: 260px" @change="loadLogs">
          <el-option v-for="job in jobs" :key="job.jobCode" :label="formatJobName(job.jobCode)" :value="job.jobCode" />
        </el-select>
      </div>

      <div v-loading="monitorSummaryLoading" class="monitor-cards">
        <button
          v-for="card in monitorCards"
          :key="card.key"
          class="monitor-card"
          :class="`monitor-card--${card.tone}`"
          type="button"
          @click="openMonitorCard(card)"
        >
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <em>{{ card.hint }}</em>
        </button>
      </div>

      <div v-if="monitorSummary.recommendedActions?.length" class="monitor-actions">
        <span v-for="item in monitorSummary.recommendedActions" :key="item">{{ item }}</span>
      </div>

      <div v-if="monitorSummary.recentBatches?.length" class="recent-batches">
        <div class="recent-batches__heading">
          <strong>最近分销批次</strong>
        </div>
        <el-table :data="monitorSummary.recentBatches" size="small" stripe>
          <el-table-column label="批次" width="90" prop="logId" />
          <el-table-column label="任务" min-width="150" prop="jobName" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理" width="90" prop="processedCount" />
          <el-table-column label="重放/无变化/失败" min-width="150">
            <template #default="{ row }">
              {{ row.replayedCount ?? 0 }} / {{ row.noChangeCount ?? 0 }} / {{ row.failedCount ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="结果" min-width="260" prop="resultSummary" show-overflow-tooltip />
          <el-table-column label="建议动作" min-width="240" prop="recommendedAction" show-overflow-tooltip />
          <el-table-column label="完成时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.finishedAt || row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <el-table v-loading="loading" :data="logPagination.rows" stripe>
        <el-table-column label="入队时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="任务名称" min-width="180">
          <template #default="{ row }">{{ formatJobName(row.jobCode) }}</template>
        </el-table-column>
        <el-table-column label="队列" min-width="150" prop="queueName" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            {{ formatTriggerType(row.triggerType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理数量" width="100">
          <template #default="{ row }">
            {{ row.importedCount ?? '--' }}
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ formatDuration(row.durationMs) }}
          </template>
        </el-table-column>
        <el-table-column label="重试" width="80" prop="retryCount" />
        <el-table-column label="下次重试" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.nextRetryTime) }}
          </template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatLogResult(row) }}
          </template>
        </el-table-column>
        <el-table-column label="失败原因 / 建议动作" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatFailureAdvice(row) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button
                v-if="row.status === 'FAILED'"
                size="small"
                type="primary"
                plain
                :loading="retryingJob === row.jobCode"
                @click="handleRetryLog(row)"
              >
                重新入队
              </el-button>
              <el-button v-if="isDistributionJob(row.jobCode)" size="small" plain @click="openDistributionQueue(row)">
                查看队列
              </el-button>
              <el-button v-if="extractTraceId(row)" size="small" plain @click="copyTraceId(row)">复制追踪编号</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="logPagination.total"
          :current-page="logPagination.currentPage"
          :page-size="logPagination.pageSize"
          :page-sizes="logPagination.pageSizes"
          @size-change="logPagination.handleSizeChange"
          @current-change="logPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <h3>审计记录</h3>
        </div>
        <el-select v-model="selectedJobCode" clearable placeholder="按任务筛选" style="width: 260px" @change="loadAuditLogs">
          <el-option v-for="job in jobs" :key="job.jobCode" :label="formatJobName(job.jobCode)" :value="job.jobCode" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="auditPagination.rows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="任务名称" min-width="180">
          <template #default="{ row }">{{ formatJobName(row.jobCode) }}</template>
        </el-table-column>
        <el-table-column label="动作" width="140">
          <template #default="{ row }">
            {{ formatAuditAction(row.actionType) }}
          </template>
        </el-table-column>
        <el-table-column label="操作者" width="150">
          <template #default="{ row }">
            {{ formatAuditActor(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="240" prop="summary" show-overflow-tooltip />
        <el-table-column label="明细" min-width="300" prop="detail" show-overflow-tooltip />
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="auditPagination.total"
          :current-page="auditPagination.currentPage"
          :page-size="auditPagination.pageSize"
          :page-sizes="auditPagination.pageSizes"
          @size-change="auditPagination.handleSizeChange"
          @current-change="auditPagination.handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  dryRunSchedulerJob,
  fetchSchedulerAuditLogs,
  fetchSchedulerJobs,
  fetchSchedulerLogs,
  fetchSchedulerMonitorSummary,
  retrySchedulerJob,
  retrySchedulerLog,
  saveSchedulerJob,
  triggerSchedulerJob
} from '../api/scheduler'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, formatModuleCode, formatSchedulerStatus, formatSyncMode, statusTagType } from '../utils/format'

const presets = {
  DOUYIN_CLUE_INCREMENTAL: {
    jobCode: 'DOUYIN_CLUE_INCREMENTAL',
    moduleCode: 'CLUE',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 1,
    retryLimit: 3,
    queueName: 'douyin-clue-sync',
    endpoint: '/clue/add',
    description: '抖音客资只进入 Clue，不创建 Customer / Order / PlanOrder。'
  },
  DISTRIBUTION_OUTBOX_PROCESS: {
    jobCode: 'DISTRIBUTION_OUTBOX_PROCESS',
    moduleCode: 'DISTRIBUTION',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 1,
    retryLimit: 5,
    queueName: 'distribution-outbox',
    endpoint: '/scheduler/outbox/process',
    description: '处理分销履约状态 Outbox，异步回推外部分销系统，不回滚本地履约事务。'
  },
  DISTRIBUTION_EXCEPTION_RETRY: {
    jobCode: 'DISTRIBUTION_EXCEPTION_RETRY',
    moduleCode: 'DISTRIBUTION',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 1,
    retryLimit: 5,
    queueName: 'distribution-exception-retry',
    endpoint: '/scheduler/distribution/exceptions/process',
    description: '处理分销异常队列，重试时复用统一入站服务，不绕过 Customer / Order 规则。'
  },
  DISTRIBUTION_STATUS_CHECK: {
    jobCode: 'DISTRIBUTION_STATUS_CHECK',
    moduleCode: 'DISTRIBUTION',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 5,
    retryLimit: 5,
    queueName: 'distribution-status-check',
    endpoint: '/scheduler/distribution/status-check/process',
    description: '回查外部分销订单状态，发现取消或退款后转成分销事件重放，不直接写订单表。'
  },
  DISTRIBUTION_RECONCILE_PULL: {
    jobCode: 'DISTRIBUTION_RECONCILE_PULL',
    moduleCode: 'DISTRIBUTION',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 10,
    retryLimit: 5,
    queueName: 'distribution-reconcile-pull',
    endpoint: '/scheduler/distribution/reconcile/process',
    description: '拉取外部分销对账记录，统一复用入站事件服务处理 paid / cancelled / refund 状态。'
  }
}

const activeTab = ref('monitor')
const router = useRouter()
const loading = ref(false)
const monitorSummaryLoading = ref(false)
const savingJob = ref(false)
const dryRunningJob = ref('')
const triggeringJob = ref('')
const retryingJob = ref('')
const selectedJobCode = ref('')
const jobPreset = ref('DOUYIN_CLUE_INCREMENTAL')
const jobs = ref([])
const logs = ref([])
const auditLogs = ref([])
const monitorSummary = ref({})
const jobPagination = useTablePagination(jobs)
const logPagination = useTablePagination(logs)
const auditPagination = useTablePagination(auditLogs)

const jobForm = reactive(createJobForm())

const currentJobDescription = computed(() => {
  const preset = presets[String(jobForm.jobCode || '').toUpperCase()]
  if (preset) {
    return preset.description
  }
  if (String(jobForm.moduleCode || '').toUpperCase() === 'DISTRIBUTION') {
    return '分销调度任务只能处理队列、重试或补偿，不能直接写 Customer / Order / PlanOrder。'
  }
  return '客资调度任务只进入 Clue 入口。'
})

const monitorCards = computed(() => {
  const summary = monitorSummary.value || {}
  const outbox = summary.outbox || {}
  const exceptions = summary.exceptions || {}
  const jobs = summary.jobs || {}
  const idempotency = summary.idempotency || {}
  return [
    {
      key: 'outbox',
      label: '履约回推待处理',
      value: Number(outbox.totalAttention || 0),
      hint: `${Number(outbox.failed || 0)} 条失败，${Number(outbox.deadLetter || 0)} 条死信`,
      tone: Number(outbox.failed || 0) + Number(outbox.deadLetter || 0) > 0 ? 'danger' : Number(outbox.pending || 0) > 0 ? 'warning' : 'success',
      route: { path: '/settings/integration/distribution-api', query: { tab: 'outbox', status: Number(outbox.failed || 0) > 0 ? 'FAILED' : undefined } }
    },
    {
      key: 'exceptions',
      label: '异常队列待处理',
      value: Number(exceptions.totalAttention || 0),
      hint: `${Number(exceptions.open || 0)} 条待处理，${Number(exceptions.retryQueued || 0)} 条已入队`,
      tone: Number(exceptions.open || 0) > 0 ? 'danger' : Number(exceptions.retryQueued || 0) > 0 ? 'warning' : 'success',
      route: { path: '/settings/integration/distribution-api', query: { tab: 'exceptions', status: Number(exceptions.open || 0) > 0 ? 'OPEN' : undefined } }
    },
    {
      key: 'idempotency',
      label: '接口防重复',
      value: formatHealthStatus(idempotency.status),
      hint: `${Number(idempotency.duplicateGroupCount || 0)} 组重复，${Number(idempotency.affectedLogCount || 0)} 条记录`,
      tone: idempotency.healthy ? 'success' : 'warning',
      route: { path: '/settings/integration/distribution-api', query: { tab: 'health' } }
    },
    {
      key: 'jobs',
      label: '近 24 小时成功率',
      value: jobs.successRate24h === null || jobs.successRate24h === undefined ? '--' : `${jobs.successRate24h}%`,
      hint: `${Number(jobs.failed24h || 0)} 次失败，${Number(jobs.total24h || 0)} 次执行`,
      tone: Number(jobs.failed24h || 0) > 0 ? 'danger' : 'success',
      route: { tab: 'monitor', jobCode: Number(jobs.failed24h || 0) > 0 ? '' : selectedJobCode.value }
    }
  ]
})

onMounted(loadData)

function createJobForm(presetKey = 'DOUYIN_CLUE_INCREMENTAL') {
  return {
    ...presets[presetKey],
    providerId: null,
    status: 'ENABLED'
  }
}

function applyPreset(value) {
  if (value === 'CUSTOM') {
    return
  }
  Object.assign(jobForm, createJobForm(value))
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadJobs(), loadLogs(), loadAuditLogs(), loadMonitorSummary()])
  } finally {
    loading.value = false
  }
}

async function loadJobs() {
  jobs.value = await fetchSchedulerJobs()
  jobPagination.reset()
}

async function loadLogs() {
  logs.value = await fetchSchedulerLogs(selectedJobCode.value || undefined)
  logPagination.reset()
}

async function loadAuditLogs() {
  auditLogs.value = await fetchSchedulerAuditLogs(selectedJobCode.value || undefined)
  auditPagination.reset()
}

async function loadMonitorSummary() {
  monitorSummaryLoading.value = true
  try {
    monitorSummary.value = await fetchSchedulerMonitorSummary('DISTRIBUTION')
  } finally {
    monitorSummaryLoading.value = false
  }
}

function resetJobForm() {
  jobPreset.value = 'DOUYIN_CLUE_INCREMENTAL'
  Object.assign(jobForm, createJobForm())
}

function pickJob(row) {
  const presetKey = presets[String(row.jobCode || '').toUpperCase()] ? String(row.jobCode || '').toUpperCase() : 'CUSTOM'
  jobPreset.value = presetKey
  Object.assign(jobForm, {
    jobCode: row.jobCode,
    moduleCode: row.moduleCode,
    syncMode: row.syncMode,
    intervalMinutes: row.intervalMinutes,
    retryLimit: row.retryLimit,
    queueName: row.queueName,
    providerId: row.providerId || null,
    endpoint: row.endpoint,
    status: row.status
  })
  activeTab.value = 'config'
}

async function handleSaveJob() {
  savingJob.value = true
  try {
    await saveSchedulerJob({ ...jobForm })
    ElMessage.success('任务配置已保存')
    await loadData()
  } finally {
    savingJob.value = false
  }
}

async function handleDryRun(row) {
  dryRunningJob.value = row.jobCode
  try {
    const preview = await dryRunSchedulerJob({
      jobCode: row.jobCode,
      payload: JSON.stringify({ source: 'scheduler-center-dry-run' })
    })
    selectedJobCode.value = row.jobCode
    activeTab.value = 'monitor'
    const payload = parseLogPayload(preview?.payload)
    ElMessage.success(payload?.message || '预检完成：未入队、未调用外部接口、未写核心业务表')
    await loadData()
  } finally {
    dryRunningJob.value = ''
  }
}

async function handleTrigger(row) {
  try {
    await ElMessageBox.confirm(
      '该操作会按当前任务配置执行真实调度，可能处理队列、回查外部状态或生成受控入站重放事件。建议先执行预检，确认继续吗？',
      '确认执行真实任务',
      {
        type: 'warning',
        confirmButtonText: '确认执行',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  triggeringJob.value = row.jobCode
  try {
    await triggerSchedulerJob({
      jobCode: row.jobCode,
      payload: JSON.stringify({ source: 'scheduler-center' })
    })
    selectedJobCode.value = row.jobCode
    activeTab.value = 'monitor'
    ElMessage.success(`${formatModuleCode(row.moduleCode)}任务已提交执行`)
    await loadData()
  } finally {
    triggeringJob.value = ''
  }
}

async function handleRetryLog(row) {
  retryingJob.value = row.jobCode
  try {
    await retrySchedulerLog(row.id)
    selectedJobCode.value = row.jobCode
    ElMessage.success('当前失败记录已重新入队')
    await loadData()
  } finally {
    retryingJob.value = ''
  }
}

async function handleRetry(row) {
  retryingJob.value = row.jobCode
  try {
    await retrySchedulerJob(row.jobCode)
    selectedJobCode.value = row.jobCode
    activeTab.value = 'monitor'
    ElMessage.success('失败任务已重新入队')
    await loadData()
  } finally {
    retryingJob.value = ''
  }
}

function formatTriggerType(value) {
  return (
    {
      AUTO: '自动',
      MANUAL: '手动',
      RETRY: '自动重试',
      MANUAL_RETRY: '手动重试'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function formatJobName(value) {
  return (
    {
      DOUYIN_CLUE_INCREMENTAL: '抖音客资增量拉取',
      DISTRIBUTION_OUTBOX_PROCESS: '分销履约回推',
      DISTRIBUTION_EXCEPTION_RETRY: '分销异常重试',
      DISTRIBUTION_STATUS_CHECK: '分销状态回查',
      DISTRIBUTION_RECONCILE_PULL: '分销对账拉取'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function formatDuration(value) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const number = Number(value)
  if (Number.isNaN(number)) {
    return '--'
  }
  if (number < 1000) {
    return `${number}ms`
  }
  return `${(number / 1000).toFixed(1)}s`
}

function formatLogResult(row) {
  if (row.errorMessage) {
    return row.errorMessage
  }
  const payload = parseLogPayload(row.payload)
  if (payload?.actionCounts) {
    const counts = payload.actionCounts
    return `执行完成，处理 ${payload.processedCount ?? row.importedCount ?? 0} 条：重放 ${counts.replayed ?? 0}，无变化 ${counts.noChange ?? 0}，失败 ${counts.failed ?? 0}`
  }
  if (row.importedCount !== null && row.importedCount !== undefined) {
    return `执行完成，处理 ${row.importedCount} 条记录`
  }
  return row.payload || '--'
}

function formatFailureAdvice(row) {
  if (!row) {
    return '--'
  }
  if (String(row.status || '').toUpperCase() === 'FAILED') {
    const message = row.errorMessage || parseLogPayload(row.payload)?.message || '任务执行失败'
    return `${message}；建议查看对应队列或重新入队。`
  }
  const payload = parseLogPayload(row.payload)
  const failed = Number(payload?.actionCounts?.failed || 0)
  if (failed > 0) {
    return `本次有 ${failed} 条失败记录，建议进入分销接口页查看异常队列。`
  }
  if (String(row.status || '').toUpperCase() === 'SUCCESS') {
    return '执行正常，无需处理。'
  }
  return '等待调度执行或查看审计记录。'
}

function isDistributionJob(jobCode) {
  return String(jobCode || '').toUpperCase().startsWith('DISTRIBUTION_')
}

function openDistributionQueue(row) {
  const jobCode = String(row?.jobCode || '').toUpperCase()
  const tab = jobCode === 'DISTRIBUTION_OUTBOX_PROCESS' ? 'outbox' : jobCode === 'DISTRIBUTION_EXCEPTION_RETRY' ? 'exceptions' : 'reconcile'
  const query = { tab }
  if (String(row?.status || '').toUpperCase() === 'FAILED') {
    if (tab === 'outbox') {
      query.status = 'FAILED'
    }
    if (tab === 'exceptions') {
      query.status = 'OPEN'
    }
  }
  const traceId = extractTraceId(row)
  if (traceId) {
    query.keyword = traceId
  }
  query.source = 'scheduler-monitor'
  query.sourceLabel = `来自任务调度：${formatJobName(row?.jobCode)}`
  query.returnPath = '/settings/integration/jobs'
  query.returnTab = 'monitor'
  router.push({ path: '/settings/integration/distribution-api', query })
}

function openMonitorCard(card) {
  if (card?.route?.path) {
    router.push(card.route)
    return
  }
  activeTab.value = 'monitor'
  if (card?.route?.jobCode) {
    selectedJobCode.value = card.route.jobCode
    loadLogs()
  }
}

function extractTraceId(row) {
  const payload = parseLogPayload(row?.payload)
  return payload?.traceId || payload?.latestTraceId || payload?.samples?.find?.((item) => item?.traceId)?.traceId || ''
}

async function copyTraceId(row) {
  const traceId = extractTraceId(row)
  if (!traceId) {
    return
  }
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('追踪编号已复制')
  } catch (error) {
    ElMessage.warning(`追踪编号：${traceId}`)
  }
}

function parseLogPayload(value) {
  if (!value) {
    return null
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch (error) {
    return null
  }
}

function formatHealthStatus(value) {
  return (
    {
      HEALTHY: '健康',
      DUPLICATE_DATA: '有重复数据',
      INDEX_NOT_READY: '待生效',
      MISSING_TABLE: '缺少日志表'
    }[String(value || '').toUpperCase()] || (value ? '待检查' : '--')
  )
}

function formatAuditAction(value) {
  return (
    {
      JOB_CREATE: '创建配置',
      JOB_UPDATE: '更新配置',
      JOB_TRIGGER: '手动触发',
      JOB_AUTO_ENQUEUE: '自动入队',
      JOB_RETRY: '重新入队',
      JOB_EXECUTE_START: '开始执行',
      JOB_EXECUTE_SUCCESS: '执行成功',
      JOB_EXECUTE_FAILED: '执行失败'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function formatAuditActor(row) {
  if (!row) {
    return '--'
  }
  if (row.actorType === 'SYSTEM') {
    return '系统'
  }
  return row.actorRoleCode ? `${row.actorRoleCode}${row.actorUserId ? ` / ${row.actorUserId}` : ''}` : row.actorUserId || '--'
}
</script>

<style scoped>
.scheduler-workspace {
  display: grid;
  gap: 18px;
}

.scheduler-editor {
  margin-bottom: 0;
}

.monitor-cards {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
}

.monitor-card {
  border: 1px solid rgba(46, 64, 87, 0.1);
  border-radius: 18px;
  background: #fbfaf6;
  box-shadow: 0 10px 24px rgba(30, 41, 59, 0.06);
  color: #1f2937;
  cursor: pointer;
  display: grid;
  gap: 8px;
  min-height: 116px;
  padding: 18px;
  text-align: left;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.monitor-card:hover {
  box-shadow: 0 16px 30px rgba(30, 41, 59, 0.1);
  transform: translateY(-2px);
}

.monitor-card span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.monitor-card strong {
  font-size: 30px;
  line-height: 1;
}

.monitor-card em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.monitor-card--success {
  border-color: rgba(22, 163, 74, 0.2);
}

.monitor-card--warning {
  border-color: rgba(217, 119, 6, 0.26);
  background: #fff8ec;
}

.monitor-card--danger {
  border-color: rgba(220, 38, 38, 0.24);
  background: #fff3f2;
}

.monitor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.monitor-actions span {
  border-radius: 999px;
  background: #edf4ef;
  color: #315b46;
  font-size: 12px;
  padding: 7px 11px;
}

.recent-batches {
  border: 1px solid rgba(46, 64, 87, 0.08);
  border-radius: 16px;
  background: #fff;
  margin-bottom: 14px;
  padding: 14px;
}

.recent-batches__heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.recent-batches__heading strong {
  color: #173b33;
}

.recent-batches__heading span {
  color: #64748b;
  font-size: 12px;
}

.readonly-value {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  padding: 0 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  line-height: 1.5;
}

@media (max-width: 1180px) {
  .monitor-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .monitor-cards {
    grid-template-columns: 1fr;
  }

  .recent-batches__heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
