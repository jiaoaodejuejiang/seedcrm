<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>任务数</span>
        <strong>{{ jobs.length }}</strong>
        <small>支持外部调用、增量同步、队列处理与失败重试。</small>
      </article>
      <article class="metric-card">
        <span>启用任务</span>
        <strong>{{ activeCount }}</strong>
        <small>当前处于启用状态的调度任务数量。</small>
      </article>
      <article class="metric-card">
        <span>失败日志</span>
        <strong>{{ failedLogCount }}</strong>
        <small>失败日志可继续触发重试链路。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>任务配置</h3>
          <p>默认预置为抖音线索 1 分钟增量同步任务。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>任务编码</span>
          <el-input v-model="jobForm.jobCode" />
        </label>
        <label>
          <span>模块</span>
          <el-select v-model="jobForm.moduleCode">
            <el-option v-for="option in moduleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label>
          <span>同步方式</span>
          <el-select v-model="jobForm.syncMode">
            <el-option label="增量同步" value="INCREMENTAL" />
            <el-option label="全量同步" value="FULL" />
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
          <span>队列名</span>
          <el-input v-model="jobForm.queueName" />
        </label>
        <label class="full-span">
          <span>接口地址</span>
          <el-input v-model="jobForm.endpoint" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="handleSaveJob">保存任务</el-button>
        <el-button @click="loadData">刷新</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>调度任务列表</h3>
          <p>系统会自动入队和执行，页面每 10 秒自动刷新一次。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="jobs" stripe>
        <el-table-column label="任务编码" min-width="180" prop="jobCode" />
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            {{ formatModuleCode(row.moduleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="同步方式" width="130">
          <template #default="{ row }">
            {{ formatSyncMode(row.syncMode) }}
          </template>
        </el-table-column>
        <el-table-column label="周期" width="120">
          <template #default="{ row }">
            {{ row.intervalMinutes }} 分钟
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次执行" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.lastRunTime) }}
          </template>
        </el-table-column>
        <el-table-column label="下次执行" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.nextRunTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="240" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" type="primary" @click="handleTrigger(row)">立即触发</el-button>
              <el-button size="small" plain @click="handleRetry(row)">重试失败</el-button>
              <el-button size="small" @click="handlePickJob(row)">载入表单</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>执行日志</h3>
            <p>可按任务过滤，查看载荷、错误信息和重试时间。</p>
          </div>
        <el-select v-model="selectedJobCode" clearable placeholder="按任务筛选" style="width: 220px" @change="loadLogs">
          <el-option v-for="job in jobs" :key="job.jobCode" :label="job.jobCode" :value="job.jobCode" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column label="任务编码" min-width="180" prop="jobCode" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试次数" width="110" prop="retryCount" />
        <el-table-column label="载荷" min-width="220" prop="payload" />
        <el-table-column label="错误信息" min-width="220" prop="errorMessage" />
        <el-table-column label="下次重试" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.nextRetryTime) }}
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSchedulerJobs, fetchSchedulerLogs, retrySchedulerJob, saveSchedulerJob, triggerSchedulerJob } from '../api/scheduler'
import { formatDateTime, formatModuleCode, formatSchedulerStatus, formatSyncMode, statusTagType } from '../utils/format'

const loading = ref(true)
const jobs = ref([])
const logs = ref([])
const selectedJobCode = ref('')
let refreshTimer = null

const moduleOptions = [{ label: '线索', value: 'CLUE' }]

const jobForm = reactive({
  jobCode: 'DOUYIN_CLUE_INCREMENTAL',
  moduleCode: 'CLUE',
  syncMode: 'INCREMENTAL',
  intervalMinutes: 1,
  retryLimit: 3,
  queueName: 'douyin-clue-sync',
  endpoint: '/clue/add',
  status: 'ENABLED'
})

const activeCount = computed(() => jobs.value.filter((item) => ['ACTIVE', 'ENABLED'].includes(item.status)).length)
const failedLogCount = computed(() => logs.value.filter((item) => ['FAIL', 'FAILED'].includes(item.status)).length)

async function loadJobs() {
  try {
    jobs.value = await fetchSchedulerJobs()
  } catch {
    jobs.value = []
  }
}

async function loadLogs() {
  try {
    logs.value = await fetchSchedulerLogs(selectedJobCode.value || undefined)
  } catch {
    logs.value = []
  }
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadJobs(), loadLogs()])
  } catch {
    // 子方法已经处理了降级状态。
  } finally {
    loading.value = false
  }
}

async function handleSaveJob() {
  try {
    await saveSchedulerJob({ ...jobForm })
    ElMessage.success('调度任务已保存')
    await loadData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleTrigger(row) {
  try {
    await triggerSchedulerJob({
      jobCode: row.jobCode,
      payload: JSON.stringify({ source: 'manual' })
    })
    ElMessage.success('任务已入队')
    selectedJobCode.value = row.jobCode
    await loadData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleRetry(row) {
  try {
    await retrySchedulerJob(row.jobCode)
    ElMessage.success('失败日志已重新入队')
    selectedJobCode.value = row.jobCode
    await loadData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

function handlePickJob(row) {
  Object.assign(jobForm, {
    jobCode: row.jobCode,
    moduleCode: row.moduleCode,
    syncMode: row.syncMode,
    intervalMinutes: row.intervalMinutes,
    retryLimit: row.retryLimit,
    queueName: row.queueName,
    endpoint: row.endpoint,
    status: row.status
  })
}

onMounted(async () => {
  await loadData()
  refreshTimer = window.setInterval(loadData, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>
