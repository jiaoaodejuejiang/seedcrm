<template>
  <div class="stack-page">
    <section class="panel compact-panel">
      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="activeTab">
            <el-radio-button value="config">任务配置</el-radio-button>
            <el-radio-button value="monitor">执行监控</el-radio-button>
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
                <el-button size="small" type="primary" plain :loading="triggeringJob === row.jobCode" @click="handleTrigger(row)">
                  执行一次
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
          <el-option v-for="job in jobs" :key="job.jobCode" :label="job.jobCode" :value="job.jobCode" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="logPagination.rows" stripe>
        <el-table-column label="入队时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="任务编码" min-width="220" prop="jobCode" />
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
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              size="small"
              plain
              :loading="retryingJob === row.jobCode"
              @click="handleRetryLog(row)"
            >
              重新入队
            </el-button>
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
          <el-option v-for="job in jobs" :key="job.jobCode" :label="job.jobCode" :value="job.jobCode" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="auditPagination.rows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="任务编码" min-width="220" prop="jobCode" />
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
import { ElMessage } from 'element-plus'
import {
  fetchSchedulerAuditLogs,
  fetchSchedulerJobs,
  fetchSchedulerLogs,
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

const activeTab = ref('config')
const loading = ref(false)
const savingJob = ref(false)
const triggeringJob = ref('')
const retryingJob = ref('')
const selectedJobCode = ref('')
const jobPreset = ref('DOUYIN_CLUE_INCREMENTAL')
const jobs = ref([])
const logs = ref([])
const auditLogs = ref([])
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
    await Promise.all([loadJobs(), loadLogs(), loadAuditLogs()])
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

async function handleTrigger(row) {
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
  if (row.importedCount !== null && row.importedCount !== undefined) {
    return `执行完成，处理 ${row.importedCount} 条记录`
  }
  return row.payload || '--'
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
</style>
