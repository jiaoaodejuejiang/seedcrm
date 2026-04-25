<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>活码配置</span>
        <strong>{{ configs.length }}</strong>
        <small>已保存</small>
      </article>
      <article class="metric-card">
        <span>轮询员工</span>
        <strong>{{ availableEmployees.length }}</strong>
        <small>当前可选</small>
      </article>
      <article class="metric-card">
        <span>最近生成</span>
        <strong>{{ generatedCount }}</strong>
        <small>已生成二维码</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>活码配置</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="handleSaveConfig">保存配置</el-button>
          <el-button type="success" :loading="generating" @click="handleGenerate">生成活码</el-button>
          <el-button @click="resetLiveCodeForm">重置</el-button>
        </div>
      </div>

      <div class="status-strip">
        <div class="status-pill">
          <span>企业微信模式</span>
          <strong>{{ wecomConfig.executionMode || 'MOCK' }}</strong>
        </div>
        <div class="status-pill">
          <span>回调状态</span>
          <strong>{{ wecomConfig.lastCallbackStatus || '未收到' }}</strong>
        </div>
        <div class="status-pill">
          <span>联系我参数</span>
          <strong>{{ liveCodeSummary }}</strong>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>活码名称</span>
          <el-input v-model="liveCodeForm.codeName" placeholder="例如：门店引流活码" />
        </label>
        <label>
          <span>应用场景</span>
          <el-input v-model="liveCodeForm.scene" placeholder="例如：活动投放 / 门店承接" />
        </label>
        <label>
          <span>分配策略</span>
          <el-input :model-value="'轮询分配'" readonly />
        </label>
        <label>
          <span>配置状态</span>
          <el-select v-model="liveCodeForm.isEnabled">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </label>
        <label class="full-span">
          <span>轮询员工</span>
          <el-select
            v-model="liveCodeForm.employeeIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择参与轮询接待的私域客服员工"
          >
            <el-option
              v-for="employee in availableEmployees"
              :key="employee.id"
              :label="employeeOptionLabel(employee)"
              :value="employee.id"
            />
          </el-select>
        </label>
        <label class="full-span">
          <span>投放说明</span>
          <el-input v-model="liveCodeForm.remark" type="textarea" :rows="3" placeholder="请输入投放说明" />
        </label>
      </div>

      <div class="chip-row">
        <el-tag
          v-for="employee in selectedEmployees"
          :key="employee.id"
          class="employee-chip"
          effect="plain"
          type="info"
        >
          {{ employee.userName }} / {{ employee.accountName }}
        </el-tag>
      </div>
    </section>

    <section v-if="generatedResult" class="panel">
      <div class="panel-heading">
        <div>
          <h3>生成结果</h3>
        </div>
      </div>

      <div class="live-code-preview-grid">
        <div class="live-code-preview-card">
          <img class="live-code-preview" :src="generatedResult.qrCodeUrl" :alt="generatedResult.codeName" />
        </div>

        <div class="detail-card">
          <h3>{{ generatedResult.codeName }}</h3>
          <p>应用场景：{{ generatedResult.scene }}</p>
          <p>分配策略：{{ strategyLabel(generatedResult.strategy) }}</p>
          <p>联系我 ID：{{ generatedResult.contactWayId }}</p>
          <p>短链地址：{{ generatedResult.shortLink || '--' }}</p>
          <p>生成时间：{{ formatDateTime(generatedResult.generatedAt) }}</p>
          <p>轮询员工：{{ generatedResult.employeeNames.join(' / ') }}</p>
          <p>{{ generatedResult.summary }}</p>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>配置列表</h3>
        </div>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="活码名称" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.codeName }}</strong>
              <span>{{ row.scene || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="轮询员工" min-width="220">
          <template #default="{ row }">
            {{ (row.employeeNames || []).join(' / ') || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="策略" width="120">
          <template #default="{ row }">
            {{ strategyLabel(row.strategy) }}
          </template>
        </el-table-column>
        <el-table-column label="最近生成" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.generatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">
              {{ row.isEnabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickLiveCodeConfig(row)">编辑</el-button>
              <el-button size="small" type="success" :loading="generatingRowId === row.id" @click="handleGenerate(row)">
                生成活码
              </el-button>
              <el-button size="small" plain @click="toggleLiveCodeConfig(row)">
                {{ row.isEnabled === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchWecomConfig, fetchWecomLiveCodeConfigs, generateWecomLiveCode, saveWecomLiveCodeConfig } from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'
import { loadSystemConsoleState } from '../utils/systemConsoleStore'

const STRATEGY_ROUND_ROBIN = 'ROUND_ROBIN'

const state = reactive(loadSystemConsoleState())
const configs = ref([])
const generating = ref(false)
const generatingRowId = ref(null)
const generatedResult = ref(null)
const wecomConfig = ref({
  executionMode: 'MOCK',
  liveCodeType: 2,
  liveCodeScene: 2,
  liveCodeStyle: 1,
  lastCallbackStatus: ''
})
const liveCodeForm = reactive(createLiveCodeForm())
const pagination = useTablePagination(configs)

const availableEmployees = computed(() => {
  const privateDomainEmployees = state.employees.filter(
    (item) => item.roleCode === 'PRIVATE_DOMAIN_SERVICE' && item.status === 'ACTIVE' && item.canLogin === 1
  )
  if (privateDomainEmployees.length) {
    return privateDomainEmployees
  }
  return state.employees.filter((item) => item.status === 'ACTIVE' && item.canLogin === 1)
})

const selectedEmployees = computed(() =>
  availableEmployees.value.filter((item) => liveCodeForm.employeeIds.includes(item.id))
)

const generatedCount = computed(() => configs.value.filter((item) => item.generatedAt && item.qrCodeUrl).length)
const liveCodeSummary = computed(
  () => `${wecomConfig.value.liveCodeType || 2} / ${wecomConfig.value.liveCodeScene || 2} / ${wecomConfig.value.liveCodeStyle || 1}`
)

onMounted(async () => {
  await Promise.all([loadConfigs(), loadWecomConfig()])
})

watch(
  () => availableEmployees.value,
  (employees) => {
    if (!liveCodeForm.employeeIds.length && employees.length) {
      liveCodeForm.employeeIds = employees.slice(0, Math.min(2, employees.length)).map((item) => item.id)
    }
  },
  { immediate: true }
)

function createLiveCodeForm() {
  return {
    id: null,
    codeName: '',
    scene: '门店引流',
    strategy: STRATEGY_ROUND_ROBIN,
    employeeIds: [],
    remark: '',
    isEnabled: 1
  }
}

async function loadConfigs() {
  configs.value = await fetchWecomLiveCodeConfigs()
  pagination.reset()
}

async function loadWecomConfig() {
  wecomConfig.value = (await fetchWecomConfig()) || wecomConfig.value
}

function resetLiveCodeForm() {
  Object.assign(liveCodeForm, createLiveCodeForm())
  generatedResult.value = null
}

function employeeOptionLabel(employee) {
  return `${employee.userName}（${employee.accountName}）`
}

function strategyLabel(strategy) {
  return strategy === STRATEGY_ROUND_ROBIN ? '轮询分配' : strategy || '--'
}

function resolveEmployeeIds(payload) {
  const accountSet = new Set(payload.employeeAccounts || [])
  if (accountSet.size) {
    const ids = availableEmployees.value.filter((item) => accountSet.has(item.accountName)).map((item) => item.id)
    if (ids.length) {
      return ids
    }
  }
  const nameSet = new Set(payload.employeeNames || [])
  return availableEmployees.value.filter((item) => nameSet.has(item.userName)).map((item) => item.id)
}

function buildPayload(formLike) {
  const employees = availableEmployees.value.filter((item) => formLike.employeeIds.includes(item.id))
  return {
    id: formLike.id,
    codeName: formLike.codeName,
    scene: formLike.scene,
    strategy: formLike.strategy || STRATEGY_ROUND_ROBIN,
    employeeNames: employees.map((item) => item.userName),
    employeeAccounts: employees.map((item) => item.accountName),
    remark: formLike.remark,
    isEnabled: formLike.isEnabled
  }
}

async function handleSaveConfig() {
  if (!liveCodeForm.codeName || !liveCodeForm.employeeIds.length) {
    ElMessage.warning('请先填写活码名称并选择轮询员工')
    return
  }
  const saved = await saveWecomLiveCodeConfig(buildPayload(liveCodeForm))
  Object.assign(liveCodeForm, {
    ...saved,
    employeeIds: resolveEmployeeIds(saved)
  })
  await loadConfigs()
  ElMessage.success('活码配置已保存')
}

function pickLiveCodeConfig(row) {
  Object.assign(liveCodeForm, {
    id: row.id,
    codeName: row.codeName,
    scene: row.scene || '门店引流',
    strategy: row.strategy || STRATEGY_ROUND_ROBIN,
    employeeIds: resolveEmployeeIds(row),
    remark: row.remark || '',
    isEnabled: row.isEnabled ?? 1
  })
  generatedResult.value = row.qrCodeUrl
    ? {
        ...row,
        strategyLabel: strategyLabel(row.strategy)
      }
    : null
}

async function toggleLiveCodeConfig(row) {
  await saveWecomLiveCodeConfig({
    ...row,
    isEnabled: row.isEnabled === 1 ? 0 : 1
  })
  await loadConfigs()
  ElMessage.success('活码状态已更新')
}

async function handleGenerate(row = null) {
  const formLike = row
    ? {
        id: row.id,
        codeName: row.codeName,
        scene: row.scene || '门店引流',
        strategy: row.strategy || STRATEGY_ROUND_ROBIN,
        employeeIds: resolveEmployeeIds(row),
        remark: row.remark || '',
        isEnabled: row.isEnabled ?? 1
      }
    : liveCodeForm
  if (!formLike.codeName || !formLike.employeeIds.length) {
    ElMessage.warning('请先填写活码名称并选择轮询员工')
    return
  }

  generating.value = !row
  generatingRowId.value = row?.id || null
  try {
    const saved = await saveWecomLiveCodeConfig(buildPayload(formLike))
    const result = await generateWecomLiveCode({
      codeName: saved.codeName,
      scene: saved.scene,
      strategy: saved.strategy,
      employeeNames: saved.employeeNames,
      employeeAccounts: saved.employeeAccounts
    })
    generatedResult.value = {
      ...result,
      strategyLabel: strategyLabel(result.strategy)
    }
    await loadConfigs()
    const latest = configs.value.find((item) => item.codeName === saved.codeName)
    if (latest) {
      pickLiveCodeConfig(latest)
    }
    ElMessage.success('活码已生成')
  } finally {
    generating.value = false
    generatingRowId.value = null
  }
}
</script>
