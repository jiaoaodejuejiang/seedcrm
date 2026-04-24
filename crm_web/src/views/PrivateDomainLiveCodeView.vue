<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>活码配置</span>
        <strong>{{ state.wecomLiveCodeConfigs.length }}</strong>
        <small>已保存的私域活码配置数量</small>
      </article>
      <article class="metric-card">
        <span>在职私域客服</span>
        <strong>{{ availableEmployees.length }}</strong>
        <small>当前可参与轮询接待的私域客服员工</small>
      </article>
      <article class="metric-card">
        <span>最近生成</span>
        <strong>{{ generatedCount }}</strong>
        <small>已生成并保留二维码结果的活码数量</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>活码配置</h3>
          <p>营销投放时可让客户扫码，系统按轮询策略将客户分配给所选私域客服企业微信。</p>
        </div>
      </div>

      <p class="table-note">
        {{ employeeTip }}
      </p>

      <div class="form-grid">
        <label>
          <span>活码名称</span>
          <el-input v-model="liveCodeForm.codeName" placeholder="例如：门店引流活码" />
        </label>
        <label>
          <span>应用场景</span>
          <el-input v-model="liveCodeForm.scene" placeholder="例如：门店服务引流 / 活动投放" />
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
          <span>轮询员工列表</span>
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
          <el-input
            v-model="liveCodeForm.remark"
            type="textarea"
            :rows="3"
            placeholder="例如：门店服务人员在活动现场出示，扫码后轮询添加私域客服企业微信。"
          />
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

      <div class="action-group">
        <el-button type="primary" @click="saveLiveCodeConfig">保存配置</el-button>
        <el-button type="success" :loading="generating" @click="generateLiveCode()">生成活码</el-button>
        <el-button @click="resetLiveCodeForm">重置表单</el-button>
      </div>
    </section>

    <section v-if="generatedResult" class="panel">
      <div class="panel-heading">
        <div>
          <h3>生成结果</h3>
          <p>可将当前活码用于营销投放，由店铺服务人员引导客户扫码后进入私域承接。</p>
        </div>
      </div>

      <div class="live-code-preview-grid">
        <div class="live-code-preview-card">
          <img class="live-code-preview" :src="generatedResult.qrCodeUrl" :alt="generatedResult.codeName" />
        </div>

        <div class="detail-card">
          <h3>{{ generatedResult.codeName }}</h3>
          <p>应用场景：{{ generatedResult.scene }}</p>
          <p>分配策略：{{ generatedResult.strategyLabel }}</p>
          <p>联系我 ID：{{ generatedResult.contactWayId }}</p>
          <p>短链地址：{{ generatedResult.shortLink }}</p>
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
          <p>保存后可反复生成活码，方便不同活动、门店或渠道分别投放。</p>
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
            {{ employeeNamesFromConfig(row).join(' / ') || '--' }}
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
              <el-button size="small" type="success" :loading="generatingRowId === row.id" @click="generateLiveCode(row)">生成活码</el-button>
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
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { generateWecomLiveCode } from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const STRATEGY_ROUND_ROBIN = 'ROUND_ROBIN'

const state = reactive(loadSystemConsoleState())
const generating = ref(false)
const generatingRowId = ref(null)
const generatedResult = ref(null)
const liveCodeForm = reactive(createLiveCodeForm())
const pagination = useTablePagination(computed(() => state.wecomLiveCodeConfigs))

ensureLiveCodeState()

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

const generatedCount = computed(() =>
  state.wecomLiveCodeConfigs.filter((item) => item.generatedAt && item.generatedQrCodeUrl).length
)

const employeeTip = computed(() => {
  if (availableEmployees.value.length >= 2) {
    return '当前默认读取在职私域客服员工，可按轮询策略生成营销活码。'
  }
  return '当前可轮询员工不足 2 人，仍可先生成单人活码；如需真正轮询，请先在系统管理中补充更多私域客服员工。'
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

function ensureLiveCodeState() {
  if (!Array.isArray(state.wecomLiveCodeConfigs)) {
    replaceState({
      ...state,
      wecomLiveCodeConfigs: []
    })
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
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

function employeeNamesFromConfig(config) {
  return availableEmployees.value
    .filter((item) => (config.employeeIds || []).includes(item.id))
    .map((item) => item.userName)
}

function saveLiveCodeConfig() {
  const saved = upsertLiveCodeConfig({
    ...liveCodeForm,
    employeeIds: [...liveCodeForm.employeeIds]
  })
  if (!saved) {
    return
  }
  ElMessage.success('活码配置已保存')
  pickLiveCodeConfig(saved)
}

function upsertLiveCodeConfig(config) {
  if (!config.codeName || !(config.employeeIds || []).length) {
    ElMessage.warning('请先填写活码名称并选择轮询员工')
    return null
  }

  const nextConfigs = [...state.wecomLiveCodeConfigs]
  let saved
  if (config.id) {
    const index = nextConfigs.findIndex((item) => item.id === config.id)
    if (index === -1) {
      return null
    }
    saved = {
      ...nextConfigs[index],
      ...config
    }
    nextConfigs[index] = saved
  } else {
    saved = {
      ...config,
      id: nextSystemId(nextConfigs)
    }
    nextConfigs.push(saved)
  }

  replaceState({
    ...state,
    wecomLiveCodeConfigs: nextConfigs
  })
  pagination.reset()

  return saved
}

function pickLiveCodeConfig(row) {
  Object.assign(liveCodeForm, {
    ...row,
    employeeIds: [...(row.employeeIds || [])]
  })
  if (row.generatedQrCodeUrl) {
    generatedResult.value = normalizeGeneratedResult({
      ...row,
      qrCodeUrl: row.generatedQrCodeUrl,
      employeeNames: employeeNamesFromConfig(row)
    })
  } else {
    generatedResult.value = null
  }
}

function toggleLiveCodeConfig(row) {
  const nextConfigs = state.wecomLiveCodeConfigs.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({
    ...state,
    wecomLiveCodeConfigs: nextConfigs
  })
  ElMessage.success('活码状态已更新')
}

async function generateLiveCode(row = null) {
  const draft = row
    ? {
        ...row,
        employeeIds: [...(row.employeeIds || [])]
      }
    : {
        ...liveCodeForm,
        employeeIds: [...liveCodeForm.employeeIds]
      }

  const selected = availableEmployees.value.filter((item) => draft.employeeIds.includes(item.id))
  if (!draft.codeName || !selected.length) {
    ElMessage.warning('请先填写活码名称并选择轮询员工')
    return
  }

  generating.value = !row
  generatingRowId.value = row?.id || null
  try {
    const result = await generateWecomLiveCode({
      codeName: draft.codeName,
      scene: draft.scene,
      strategy: draft.strategy || STRATEGY_ROUND_ROBIN,
      employeeNames: selected.map((item) => item.userName),
      employeeAccounts: selected.map((item) => item.accountName)
    })

    const saved = upsertLiveCodeConfig({
      ...draft,
      generatedAt: result.generatedAt,
      generatedQrCodeUrl: result.qrCodeUrl,
      contactWayId: result.contactWayId,
      shortLink: result.shortLink,
      summary: result.summary
    })

    if (!saved) {
      return
    }

    generatedResult.value = normalizeGeneratedResult({
      ...result,
      employeeNames: selected.map((item) => item.userName)
    })
    pickLiveCodeConfig(saved)
    ElMessage.success('活码已生成')
  } finally {
    generating.value = false
    generatingRowId.value = null
  }
}

function normalizeGeneratedResult(result) {
  return {
    ...result,
    strategyLabel: strategyLabel(result.strategy)
  }
}
</script>
