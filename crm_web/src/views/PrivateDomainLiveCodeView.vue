<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>活码配置</span>
        <strong>{{ configs.length }}</strong>
      </article>
      <article class="metric-card">
        <span>轮询员工</span>
        <strong>{{ availableEmployees.length }}</strong>
      </article>
      <article class="metric-card">
        <span>覆盖门店</span>
        <strong>{{ publishedStoreCount }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>活码配置</h3>
        </div>
        <div class="action-group">
          <el-button :loading="savingDraft" @click="handleSaveDraft">保存草稿</el-button>
          <el-button type="primary" :loading="savingConfig" @click="handleSaveConfig">保存并发布到门店</el-button>
          <el-button @click="resetLiveCodeForm">重置</el-button>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>活码名称</span>
          <el-input v-model="liveCodeForm.codeName" placeholder="例如：门店引流活码" />
        </label>
        <label>
          <span>应用场景</span>
          <el-input v-model="liveCodeForm.scene" placeholder="例如：门店接待 / 活动投放" />
        </label>
        <label>
          <span>配置状态</span>
          <el-select v-model="liveCodeForm.isEnabled">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </label>
        <label>
          <span>分配策略</span>
          <el-input :model-value="'自动轮询'" readonly />
        </label>
        <label class="full-span">
          <span>覆盖门店</span>
          <el-select
            v-model="liveCodeForm.storeNames"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择需要覆盖二维码的门店"
          >
            <el-option v-for="store in storeOptions" :key="store" :label="store" :value="store" />
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
        <el-tag v-for="employee in selectedEmployees" :key="employee.id" class="employee-chip" effect="plain" type="info">
          {{ displayEmployeeName(employee) }} / {{ employee.accountName }}
        </el-tag>
        <el-tag v-for="storeName in liveCodeForm.storeNames" :key="storeName" class="employee-chip" effect="plain">
          {{ storeName }}
        </el-tag>
      </div>
    </section>

    <section v-if="generatedResult" class="panel">
      <div class="panel-heading">
        <div>
          <h3>二维码状态</h3>
        </div>
        <el-tag :type="generatedResult.qrCodeUrl ? 'success' : 'info'">
          {{ generatedResult.qrCodeUrl ? '二维码可用' : '待生成' }}
        </el-tag>
      </div>

      <div class="live-code-preview-grid">
        <div class="live-code-preview-card">
          <img class="live-code-preview" :src="generatedQrImage || generatedResult.qrCodeUrl" :alt="generatedResult.codeName" />
        </div>

        <div class="detail-card">
          <h3>{{ generatedResult.codeName }}</h3>
          <p>运行模式：{{ executionModeLabel }}</p>
          <p>应用场景：{{ generatedResult.scene || '--' }}</p>
          <p>分配策略：{{ strategyLabel(generatedResult.strategy) }}</p>
          <p>活码 ID：{{ generatedResult.contactWayId || '--' }}</p>
          <p>基础联调地址：{{ resolveLiveCodeLink(generatedResult) || '--' }}</p>
          <p>生成时间：{{ formatDateTime(generatedResult.generatedAt) }}</p>
          <p>轮询员工：{{ (generatedResult.employeeNames || []).join(' / ') || '--' }}</p>
          <p>覆盖门店：{{ (generatedResult.storeNames || []).join(' / ') || '暂未选择门店' }}</p>
          <p>{{ liveCodePreviewSummary(generatedResult) }}</p>
          <el-button v-if="generatedResult.shortLink || generatedResult.qrCodeUrl" text type="primary" @click="copyLiveCodeLink(generatedResult)">
            复制链接
          </el-button>
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
        <el-table-column label="活码名称" min-width="190">
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
        <el-table-column label="覆盖门店" min-width="220">
          <template #default="{ row }">
            {{ (row.storeNames || []).join(' / ') || '暂未选择' }}
          </template>
        </el-table-column>
        <el-table-column label="二维码状态" min-width="170">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.qrCodeUrl ? '二维码可用' : '待生成' }}</strong>
              <span>{{ formatDateTime(row.generatedAt) || '保存配置后自动同步' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">
              {{ row.isEnabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group action-group--wrap">
              <el-button size="small" @click="pickLiveCodeConfig(row)">编辑</el-button>
              <el-button size="small" plain :disabled="!row.shortLink && !row.qrCodeUrl" @click="copyLiveCodeLink(row)">复制链接</el-button>
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
import QRCode from 'qrcode'
import {
  fetchWecomConfig,
  fetchWecomLiveCodeConfigs,
  generateWecomLiveCode,
  publishWecomLiveCode,
  saveWecomLiveCodeConfig
} from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'

const STRATEGY_ROUND_ROBIN = 'ROUND_ROBIN'
const EMPLOYEE_NAME_MAP = {
  private_domain: '私域客服A',
  private_domain_b: '私域客服B',
  store_service: '门店服务A',
  store_manager: '静安店长',
  photo_a: '摄影A',
  makeup_a: '化妆师A',
  selector_a: '选片负责人A'
}
const storeOptions = ['静安门店', '浦东门店', '徐汇门店']

const state = reactive(loadSystemConsoleState())
const configs = ref([])
const savingDraft = ref(false)
const savingConfig = ref(false)
const generatedResult = ref(null)
const generatedQrImage = ref('')
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

const selectedEmployees = computed(() => availableEmployees.value.filter((item) => liveCodeForm.employeeIds.includes(item.id)))
const publishedStoreCount = computed(() => new Set(configs.value.flatMap((item) => item.storeNames || [])).size)
const executionModeLabel = computed(() => (wecomConfig.value?.executionMode === 'LIVE' ? '真实企业微信' : 'MOCK 联调'))

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
    storeNames: [],
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
  generatedQrImage.value = ''
}

function displayEmployeeName(employee) {
  return EMPLOYEE_NAME_MAP[employee.accountName] || employee.userName || employee.accountName || '--'
}

function employeeOptionLabel(employee) {
  return `${displayEmployeeName(employee)} / ${employee.accountName}`
}

function strategyLabel(strategy) {
  return strategy === STRATEGY_ROUND_ROBIN ? '自动轮询' : strategy || '--'
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
  return availableEmployees.value.filter((item) => nameSet.has(displayEmployeeName(item)) || nameSet.has(item.userName)).map((item) => item.id)
}

function buildPayload(formLike) {
  const employees = availableEmployees.value.filter((item) => formLike.employeeIds.includes(item.id))
  return {
    id: formLike.id,
    codeName: formLike.codeName,
    scene: formLike.scene,
    strategy: formLike.strategy || STRATEGY_ROUND_ROBIN,
    employeeNames: employees.map((item) => displayEmployeeName(item)),
    employeeAccounts: employees.map((item) => item.accountName),
    storeNames: formLike.storeNames || [],
    remark: formLike.remark,
    isEnabled: formLike.isEnabled
  }
}

async function handleSaveConfig() {
  if (!liveCodeForm.codeName || !liveCodeForm.employeeIds.length) {
    ElMessage.warning('请先填写活码名称并选择轮询员工')
    return
  }
  if (!liveCodeForm.storeNames.length) {
    ElMessage.warning('请选择需要发布活码的门店')
    return
  }
  savingConfig.value = true
  try {
    const saved = await saveWecomLiveCodeConfig(buildPayload(liveCodeForm))
    let latest = saved
    try {
      const generated = await generateWecomLiveCode({
        codeName: saved.codeName,
        scene: saved.scene,
        strategy: saved.strategy,
        employeeNames: saved.employeeNames,
        employeeAccounts: saved.employeeAccounts
      })
      latest = {
        ...saved,
        ...generated
      }
      if (saved.id && (saved.storeNames || []).length && generated.qrCodeUrl) {
        latest = await publishWecomLiveCode({
          configId: saved.id,
          storeNames: saved.storeNames
        })
      }
    } catch {
      ElMessage.warning('配置已保存，二维码同步失败，请检查企业微信配置后重试')
    }
    Object.assign(liveCodeForm, {
      ...latest,
      employeeIds: resolveEmployeeIds(latest),
      storeNames: latest.storeNames || saved.storeNames || []
    })
    generatedResult.value = latest
    await refreshGeneratedQrImage()
    await loadConfigs()
    ElMessage.success(resolveSaveSuccessMessage(latest))
  } finally {
    savingConfig.value = false
  }
}

async function handleSaveDraft() {
  if (!liveCodeForm.codeName || !liveCodeForm.employeeIds.length) {
    ElMessage.warning('请先填写活码名称并选择轮询员工')
    return
  }
  savingDraft.value = true
  try {
    const saved = await saveWecomLiveCodeConfig(buildPayload(liveCodeForm))
    Object.assign(liveCodeForm, {
      ...saved,
      employeeIds: resolveEmployeeIds(saved),
      storeNames: saved.storeNames || []
    })
    generatedResult.value = saved.qrCodeUrl ? saved : null
    await refreshGeneratedQrImage()
    await loadConfigs()
    ElMessage.success('草稿已保存，尚未生成或发布到门店')
  } finally {
    savingDraft.value = false
  }
}

function pickLiveCodeConfig(row) {
  Object.assign(liveCodeForm, {
    id: row.id,
    codeName: row.codeName,
    scene: row.scene || '门店引流',
    strategy: row.strategy || STRATEGY_ROUND_ROBIN,
    employeeIds: resolveEmployeeIds(row),
    storeNames: row.storeNames || [],
    remark: row.remark || '',
    isEnabled: row.isEnabled ?? 1
  })
  generatedResult.value = row.qrCodeUrl
    ? {
        ...row,
        strategyLabel: strategyLabel(row.strategy)
      }
    : null
  refreshGeneratedQrImage()
}

async function toggleLiveCodeConfig(row) {
  await saveWecomLiveCodeConfig({
    ...row,
    isEnabled: row.isEnabled === 1 ? 0 : 1
  })
  await loadConfigs()
  ElMessage.success('活码状态已更新')
}

async function copyLiveCodeLink(row) {
  const link = resolveLiveCodeLink(row)
  if (!link) {
    ElMessage.warning('当前活码暂无可复制链接')
    return
  }
  try {
    await navigator.clipboard.writeText(link)
    ElMessage.success('活码链接已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制')
  }
}

async function refreshGeneratedQrImage() {
  const link = resolveLiveCodeLink(generatedResult.value)
  if (wecomConfig.value?.executionMode !== 'LIVE' && link) {
    generatedQrImage.value = await QRCode.toDataURL(link, {
      width: 260,
      margin: 2,
      color: {
        dark: '#173042',
        light: '#ffffff'
      }
    })
    return
  }
  generatedQrImage.value = ''
}

function resolveLiveCodeLink(row) {
  const link = String(row?.shortLink || '').trim()
  if (link) {
    return /^https?:\/\//i.test(link) ? link : buildSystemUrl(state, 'callback', link)
  }
  const qrCodeUrl = String(row?.qrCodeUrl || '').trim()
  if (!qrCodeUrl || qrCodeUrl.startsWith('data:image')) {
    return ''
  }
  return qrCodeUrl
}

function resolveSaveSuccessMessage(latest) {
  if (latest?.qrCodeUrl && (latest?.storeNames || []).length) {
    return '配置已保存，活码已生成并发布到门店'
  }
  if (latest?.qrCodeUrl) {
    return '配置已保存，活码已生成；选择覆盖门店后可发布到门店'
  }
  return '配置已保存，请检查企业微信配置后生成活码'
}

function liveCodePreviewSummary(row) {
  if (wecomConfig.value?.executionMode === 'LIVE') {
    return row?.summary || '真实企业微信码已生成；客户绑定以订单列表生成的专属码为准。'
  }
  return '基础码用于门店发布；客户绑定请在订单列表生成带 state 的专属企微码。'
}
</script>

<style scoped>
.action-group--wrap {
  flex-wrap: wrap;
}
</style>
