<template>
  <div class="stack-page">
    <section class="panel compact-panel">
      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="activeTab">
            <el-radio-button value="config">任务配置</el-radio-button>
            <el-radio-button value="monitor">执行监控</el-radio-button>
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
              <span>任务编码</span>
              <el-input v-model="jobForm.jobCode" placeholder="如 DOUYIN_CLUE_INCREMENTAL" />
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
              <span>队列名称</span>
              <el-input v-model="jobForm.queueName" placeholder="如 douyin-clue-sync" />
            </label>
            <label class="full-span">
              <span>接口地址</span>
              <el-input v-model="jobForm.endpoint" placeholder="如 /clue/add" />
            </label>
          </div>

          <div class="action-group">
            <el-button type="primary" :loading="savingJob" @click="handleSaveJob">保存任务</el-button>
            <el-button @click="resetJobForm">重置</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="jobPagination.rows" stripe>
          <el-table-column label="任务编码" min-width="200" prop="jobCode" />
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
          <el-table-column label="周期" width="100">
            <template #default="{ row }">{{ row.intervalMinutes }} 分钟</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下次执行" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.nextRunTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" @click="pickJob(row)">编辑</el-button>
                <el-button size="small" type="primary" plain :loading="triggeringJob === row.jobCode" @click="handleTrigger(row)">
                  立即执行
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

    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <h3>执行监控</h3>
        </div>
        <el-select v-model="selectedJobCode" clearable placeholder="按任务筛选" style="width: 240px" @change="loadLogs">
          <el-option v-for="job in jobs" :key="job.jobCode" :label="job.jobCode" :value="job.jobCode" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="logPagination.rows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="任务编码" min-width="200" prop="jobCode" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试次数" width="110" prop="retryCount" />
        <el-table-column label="下次重试" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.nextRetryTime) }}
          </template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="280">
          <template #default="{ row }">
            {{ row.errorMessage || row.payload || '--' }}
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSchedulerJobs, fetchSchedulerLogs, retrySchedulerJob, saveSchedulerJob, triggerSchedulerJob } from '../api/scheduler'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, formatModuleCode, formatSchedulerStatus, formatSyncMode, statusTagType } from '../utils/format'

const activeTab = ref('config')
const loading = ref(false)
const savingJob = ref(false)
const triggeringJob = ref('')
const retryingJob = ref('')
const selectedJobCode = ref('')
const jobs = ref([])
const logs = ref([])
const jobPagination = useTablePagination(jobs)
const logPagination = useTablePagination(logs)

const moduleOptions = [
  { label: '客资', value: 'CLUE' },
  { label: '订单', value: 'ORDER' },
  { label: '服务单', value: 'PLANORDER' },
  { label: '调度', value: 'SCHEDULER' },
  { label: '分销', value: 'DISTRIBUTOR' },
  { label: '私域客服', value: 'WECOM' }
]

const jobForm = reactive(createJobForm())

onMounted(loadData)

function createJobForm() {
  return {
    jobCode: 'DOUYIN_CLUE_INCREMENTAL',
    moduleCode: 'CLUE',
    syncMode: 'INCREMENTAL',
    intervalMinutes: 1,
    retryLimit: 3,
    queueName: 'douyin-clue-sync',
    endpoint: '/clue/add',
    status: 'ENABLED'
  }
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadJobs(), loadLogs()])
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

function resetJobForm() {
  Object.assign(jobForm, createJobForm())
}

function pickJob(row) {
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
    ElMessage.success('任务已触发')
    await loadData()
  } finally {
    triggeringJob.value = ''
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
</script>

<style scoped>
.scheduler-workspace {
  display: grid;
  gap: 18px;
}

.scheduler-editor {
  margin-bottom: 0;
}
</style>
