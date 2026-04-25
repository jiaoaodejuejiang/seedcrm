<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>企微回调</span>
        <strong>{{ wecomLogs.length }}</strong>
        <small>{{ wecomLogs[0]?.processStatus || '暂无记录' }}</small>
      </article>
      <article class="metric-card">
        <span>抖音回调</span>
        <strong>{{ douyinLogs.length }}</strong>
        <small>{{ douyinLogs[0]?.processStatus || '暂无记录' }}</small>
      </article>
      <article class="metric-card">
        <span>调度任务</span>
        <strong>{{ jobs.length }}</strong>
        <small>{{ selectedJobCode || '可查看全部' }}</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>运行监控</h3>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="企微回调" name="wecom">
          <el-table :data="wecomPagination.rows" stripe>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.receivedAt) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120" prop="processStatus" />
            <el-table-column label="验签" width="120" prop="signatureStatus" />
            <el-table-column label="事件" min-width="160" prop="eventType" />
            <el-table-column label="结果" min-width="220" prop="processMessage" />
          </el-table>
          <div class="table-pagination">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="wecomPagination.total"
              :current-page="wecomPagination.currentPage"
              :page-size="wecomPagination.pageSize"
              :page-sizes="wecomPagination.pageSizes"
              @size-change="wecomPagination.handleSizeChange"
              @current-change="wecomPagination.handleCurrentChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="抖音回调" name="douyin">
          <el-table :data="douyinPagination.rows" stripe>
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
              :total="douyinPagination.total"
              :current-page="douyinPagination.currentPage"
              :page-size="douyinPagination.pageSize"
              :page-sizes="douyinPagination.pageSizes"
              @size-change="douyinPagination.handleSizeChange"
              @current-change="douyinPagination.handleCurrentChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="任务调度" name="jobs">
          <div class="toolbar toolbar--compact">
            <label class="toolbar-field">
              <span>任务</span>
              <el-select v-model="selectedJobCode" clearable placeholder="全部任务" @change="loadSchedulerLogs">
                <el-option v-for="job in jobs" :key="job.jobCode" :label="job.jobCode" :value="job.jobCode" />
              </el-select>
            </label>
          </div>

          <el-table :data="jobs" stripe>
            <el-table-column label="任务编码" min-width="180" prop="jobCode" />
            <el-table-column label="模块" width="120" prop="moduleCode" />
            <el-table-column label="状态" width="120" prop="status" />
            <el-table-column label="最近执行" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.lastRunTime) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="下次执行" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.nextRunTime) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button size="small" :loading="triggeringJob === row.jobCode" @click="handleTrigger(row)">立即执行</el-button>
                  <el-button size="small" plain :loading="retryingJob === row.jobCode" @click="handleRetry(row)">重试失败</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="panel-divider" />

          <el-table :data="schedulerLogPagination.rows" stripe>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="任务编码" min-width="180" prop="jobCode" />
            <el-table-column label="状态" width="120" prop="status" />
            <el-table-column label="结果" min-width="260">
              <template #default="{ row }">
                {{ row.errorMessage || row.payload || '--' }}
              </template>
            </el-table-column>
          </el-table>
          <div class="table-pagination">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="schedulerLogPagination.total"
              :current-page="schedulerLogPagination.currentPage"
              :page-size="schedulerLogPagination.pageSize"
              :page-sizes="schedulerLogPagination.pageSizes"
              @size-change="schedulerLogPagination.handleSizeChange"
              @current-change="schedulerLogPagination.handleCurrentChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  fetchIntegrationCallbackLogs,
  fetchSchedulerJobs,
  fetchSchedulerLogs,
  retrySchedulerJob,
  triggerSchedulerJob
} from '../api/scheduler'
import { fetchWecomCallbackLogs } from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const activeTab = ref(resolveRuntimeTab())
const selectedJobCode = ref('')
const triggeringJob = ref('')
const retryingJob = ref('')
const wecomLogs = ref([])
const douyinLogs = ref([])
const jobs = ref([])
const schedulerLogs = ref([])

const wecomPagination = useTablePagination(wecomLogs)
const douyinPagination = useTablePagination(douyinLogs)
const schedulerLogPagination = useTablePagination(schedulerLogs)

onMounted(async () => {
  await loadAll()
})

watch(
  () => route.meta.runtimeTab,
  async () => {
    activeTab.value = resolveRuntimeTab()
    if (activeTab.value === 'jobs') {
      await loadSchedulerLogs()
    }
  }
)

watch(activeTab, async (value) => {
  if (value === 'jobs' && !schedulerLogs.value.length) {
    await loadSchedulerLogs()
  }
})

async function loadAll() {
  const [wecomPayload, douyinPayload, jobPayload] = await Promise.all([
    fetchWecomCallbackLogs(),
    fetchIntegrationCallbackLogs('DOUYIN_LAIKE'),
    fetchSchedulerJobs()
  ])
  wecomLogs.value = wecomPayload
  douyinLogs.value = douyinPayload
  jobs.value = jobPayload
  wecomPagination.reset()
  douyinPagination.reset()
  if (activeTab.value === 'jobs') {
    await loadSchedulerLogs()
  } else {
    schedulerLogs.value = []
    schedulerLogPagination.reset()
  }
}

async function loadSchedulerLogs() {
  schedulerLogs.value = await fetchSchedulerLogs(selectedJobCode.value || undefined)
  schedulerLogPagination.reset()
}

function resolveRuntimeTab() {
  return route.meta.runtimeTab === 'jobs' ? 'jobs' : 'wecom'
}

async function handleTrigger(row) {
  triggeringJob.value = row.jobCode
  try {
    await triggerSchedulerJob({
      jobCode: row.jobCode,
      payload: JSON.stringify({ source: 'platform-runtime' })
    })
    await loadAll()
    ElMessage.success('任务已触发')
  } finally {
    triggeringJob.value = ''
  }
}

async function handleRetry(row) {
  retryingJob.value = row.jobCode
  try {
    await retrySchedulerJob(row.jobCode)
    await loadAll()
    ElMessage.success('失败任务已重新入队')
  } finally {
    retryingJob.value = ''
  }
}
</script>

<style scoped>
.platform-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.panel-divider {
  height: 1px;
  background: var(--el-border-color-light);
  margin: 20px 0;
}

.toolbar-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-field span {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
</style>
