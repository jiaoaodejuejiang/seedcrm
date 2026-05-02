<template>
  <div class="stack-page">
    <section class="panel compact-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="自动分配" name="assignment" />
        <el-tab-pane label="去重配置" name="dedup" />
      </el-tabs>
    </section>

    <template v-if="activeTab === 'assignment'">
      <section class="metrics-row">
        <article class="metric-card">
          <span>策略状态</span>
          <strong>{{ strategy.enabled === 1 ? '已启用' : '已停用' }}</strong>
          <small>启用后，新进入系统的客资会自动轮询分配给当值客服。</small>
        </article>
        <article class="metric-card">
          <span>当值客服</span>
          <strong>{{ onDutyCount }}</strong>
          <small>仅当值且未请假的客服会进入自动分配池。</small>
        </article>
        <article class="metric-card">
          <span>上次轮询</span>
          <strong>{{ lastAssignedLabel }}</strong>
          <small>自动分配会以上次分配的客服为游标继续轮询。</small>
        </article>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>自动分配策略</h3>
            <p>V1 固定为“自动轮询当值客服”，不接入外部模型，也不做复杂条件编排。</p>
          </div>
        </div>

        <div class="detail-grid">
          <article class="detail-card">
            <h3>开关状态</h3>
            <el-switch
              v-model="strategyEnabled"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
            />
            <p class="table-note">关闭后，新客资入库时不会执行自动轮询分配。</p>
          </article>
          <article class="detail-card">
            <h3>分配模式</h3>
            <p>当前模式：自动轮询当值客服</p>
            <p>模式编码：{{ strategy.assignmentMode || 'ROUND_ROBIN' }}</p>
          </article>
          <article class="detail-card">
            <h3>策略更新时间</h3>
            <p>{{ formatDateTime(strategy.updatedAt) }}</p>
            <p>更新人：{{ strategy.updatedBy || '--' }}</p>
          </article>
        </div>

        <div class="action-group">
          <el-button type="primary" :loading="savingAssignment" @click="handleSaveAssignment">保存策略</el-button>
          <el-button @click="loadData">刷新数据</el-button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>当前参与轮询的客服</h3>
            <p>值班名单来源于“值班客服”配置页，这里只展示当前自动分配会用到的人员。</p>
          </div>
        </div>

        <el-table :data="pagination.rows" stripe>
          <el-table-column label="客服" min-width="180">
            <template #default="{ row }">
              <div class="table-primary">
                <strong>{{ row.userName }}</strong>
                <span>{{ row.accountName || '--' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="班次" min-width="180" prop="shiftLabel" />
          <el-table-column label="当值" width="100">
            <template #default="{ row }">
              <el-tag :type="row.onDuty === 1 ? 'success' : 'info'">{{ row.onDuty === 1 ? '当值' : '休息' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="请假" width="100">
            <template #default="{ row }">
              <el-tag :type="row.onLeave === 1 ? 'warning' : 'success'">{{ row.onLeave === 1 ? '请假中' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="200" prop="remark" />
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="pagination.total"
            :current-page="pagination.currentPage"
            :page-size="pagination.pageSize"
            :page-sizes="pagination.pageSizes"
            @size-change="pagination.handleSizeChange"
            @current-change="pagination.handleCurrentChange"
          />
        </div>
      </section>
    </template>

    <template v-else>
      <section class="metrics-row">
        <article class="metric-card">
          <span>去重状态</span>
          <strong>{{ dedupForm.enabled === 1 ? '已启用' : '已停用' }}</strong>
          <small>默认开启，避免接口重叠拉取时生成重复客资记录。</small>
        </article>
        <article class="metric-card">
          <span>去重窗口</span>
          <strong>{{ dedupForm.windowDays }} 天</strong>
          <small>窗口内相同来源、同手机号或微信号会合并到同一条客资。</small>
        </article>
        <article class="metric-card">
          <span>记录策略</span>
          <strong>合并记录</strong>
          <small>客户基础信息保留一条，订单和动作写入客资详情。</small>
        </article>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>去重配置</h3>
            <p>适用于巨量 OceanEngine 客资接口同步入库；关闭后不按时间窗主动合并，手机号/微信号唯一保护仍生效。</p>
          </div>
        </div>

        <el-alert
          v-if="!dedupConfigSupported"
          class="compat-alert"
          type="warning"
          show-icon
          :closable="false"
          title="当前后端版本暂不支持保存去重配置，页面已按默认启用 90 天展示；升级后端后可保存。"
        />
        <el-alert
          v-else-if="dedupConfigLoadError"
          class="compat-alert"
          type="error"
          show-icon
          :closable="false"
          :title="dedupConfigLoadError"
        />

        <div class="detail-grid">
          <article class="detail-card">
            <h3>启用去重</h3>
            <el-switch
              v-model="dedupEnabled"
              :disabled="!canSaveDedupConfig"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
            />
            <p class="table-note">开启后，同来源同手机号/微信号在窗口期内优先合并到同一条基础客资。</p>
          </article>
          <article class="detail-card">
            <h3>窗口天数</h3>
            <el-input-number
              v-model="dedupForm.windowDays"
              :min="1"
              :max="3650"
              :step="1"
              :disabled="!canSaveDedupConfig"
              controls-position="right"
            />
            <p class="table-note">默认 90 天；超出窗口仍保留客户身份唯一，订单/动作继续进入客资记录。</p>
          </article>
          <article class="detail-card">
            <h3>配置更新时间</h3>
            <p>{{ formatDateTime(dedupForm.updatedAt) || '--' }}</p>
            <p>客资记录会在详情抽屉中按时间展示。</p>
          </article>
        </div>

        <div class="action-group">
          <el-button type="primary" :loading="savingDedup" :disabled="!canSaveDedupConfig" @click="handleSaveDedup">保存去重配置</el-button>
          <el-button @click="loadData">刷新数据</el-button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchAssignmentStrategy,
  fetchDedupConfig,
  fetchDutyCustomerServices,
  saveAssignmentStrategy,
  saveDedupConfig
} from '../api/clueManagement'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'

const activeTab = ref('assignment')
const strategy = ref({
  enabled: 1,
  assignmentMode: 'ROUND_ROBIN',
  lastAssignedUserId: null,
  updatedAt: null,
  updatedBy: null
})
const dedupForm = reactive({
  enabled: 1,
  windowDays: 90,
  updatedAt: null
})
const dutyStaff = ref([])
const pagination = useTablePagination(dutyStaff)
const savingAssignment = ref(false)
const savingDedup = ref(false)
const strategyEnabled = ref(true)
const dedupConfigSupported = ref(true)
const dedupConfigLoadError = ref('')

const dedupEnabled = computed({
  get: () => dedupForm.enabled === 1,
  set: (value) => {
    dedupForm.enabled = value ? 1 : 0
  }
})
const canSaveDedupConfig = computed(() => dedupConfigSupported.value && !dedupConfigLoadError.value)

const onDutyCount = computed(() => dutyStaff.value.filter((item) => item.onDuty === 1 && item.onLeave !== 1).length)
const lastAssignedLabel = computed(() => {
  const current = dutyStaff.value.find((item) => item.userId === strategy.value.lastAssignedUserId)
  return current?.userName || '--'
})

async function loadData() {
  const [strategyResponse, dutyStaffResponse] = await Promise.all([
    fetchAssignmentStrategy(),
    fetchDutyCustomerServices()
  ])
  strategy.value = strategyResponse || strategy.value
  dutyStaff.value = dutyStaffResponse || []
  const dedupResponse = await loadDedupConfig()
  if (!dedupResponse) {
    Object.assign(dedupForm, {
      enabled: 1,
      windowDays: 90,
      updatedAt: null
    })
  } else {
    Object.assign(dedupForm, {
      enabled: dedupResponse?.enabled ?? 1,
      windowDays: dedupResponse?.windowDays ?? 90,
      updatedAt: dedupResponse?.updatedAt || null
    })
  }
  pagination.reset()
  strategyEnabled.value = strategy.value.enabled === 1
}

async function loadDedupConfig() {
  try {
    const response = await fetchDedupConfig({ silentError: true })
    dedupConfigSupported.value = true
    dedupConfigLoadError.value = ''
    return response
  } catch (error) {
    const status = error?.response?.status
    if (status === 404 || status === 501) {
      dedupConfigSupported.value = false
      dedupConfigLoadError.value = ''
      return null
    }
    dedupConfigSupported.value = true
    dedupConfigLoadError.value = '去重配置加载失败，请稍后刷新重试；自动分配功能不受影响。'
    return null
  }
}

function applyDedupResponse(response) {
  Object.assign(dedupForm, {
    enabled: response?.enabled ?? dedupForm.enabled,
    windowDays: response?.windowDays ?? dedupForm.windowDays,
    updatedAt: response?.updatedAt || dedupForm.updatedAt
  })
}

async function handleSaveAssignment() {
  savingAssignment.value = true
  try {
    strategy.value = await saveAssignmentStrategy({
      enabled: strategyEnabled.value ? 1 : 0,
      assignmentMode: 'ROUND_ROBIN'
    })
    strategyEnabled.value = strategy.value.enabled === 1
    ElMessage.success('自动分配策略已保存')
  } finally {
    savingAssignment.value = false
  }
}

async function handleSaveDedup() {
  if (!dedupConfigSupported.value) {
    ElMessage.warning('当前后端版本暂不支持保存去重配置，升级后端后可保存')
    return
  }
  if (dedupConfigLoadError.value) {
    ElMessage.warning('去重配置尚未加载成功，请刷新后再保存')
    return
  }
  savingDedup.value = true
  try {
    const response = await saveDedupConfig({
      enabled: dedupForm.enabled,
      windowDays: dedupForm.windowDays
    })
    dedupConfigSupported.value = true
    applyDedupResponse(response)
    ElMessage.success('客资去重配置已保存')
  } finally {
    savingDedup.value = false
  }
}

loadData()
</script>
