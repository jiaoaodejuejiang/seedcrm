<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>客资总数</span>
        <strong>{{ mergedClues.length }}</strong>
        <small>自动拉取的线索会统一进入客资列表，由客服继续跟进与整理。</small>
      </article>
      <article class="metric-card">
        <span>已付款客资</span>
        <strong>{{ paidClueCount }}</strong>
        <small>已进入付款链路的客资可直接安排门店档期。</small>
      </article>
      <article class="metric-card">
        <span>待回拨</span>
        <strong>{{ callbackCount }}</strong>
        <small>通话状态为待回拨的线索需要优先继续联系。</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar-tabs">
          <el-radio-group v-model="productSourceFilter" @change="loadClues">
            <el-radio-button value="ALL">全部来源形式</el-radio-button>
            <el-radio-button value="GROUP_BUY">团购</el-radio-button>
            <el-radio-button value="FORM">表单</el-radio-button>
          </el-radio-group>
        </div>

        <div class="action-group">
          <span class="text-secondary">系统已接入自动拉取客资，本页每 15 秒自动刷新一次。</span>
          <el-button v-if="canCreateClue" type="primary" @click="createDialogVisible = true">新增客资</el-button>
        </div>
      </div>

      <div class="toolbar toolbar--compact">
        <div class="toolbar__filters">
          <el-select v-model="filters.sourceChannel" clearable placeholder="来源渠道" style="width: 160px">
            <el-option label="抖音" value="DOUYIN" />
            <el-option label="分销" value="DISTRIBUTOR" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="线索状态" style="width: 160px">
            <el-option label="新客资" value="NEW" />
            <el-option label="已分配" value="ASSIGNED" />
            <el-option label="跟进中" value="FOLLOWING" />
            <el-option label="已转化" value="CONVERTED" />
          </el-select>
          <el-button @click="loadClues">筛选</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="pagination.rows" stripe>
        <el-table-column label="姓名" min-width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.editName"
              size="small"
              :placeholder="`线索#${row.id}`"
              @blur="handleInlineUpdate(row, { displayName: row.editName })"
            />
          </template>
        </el-table-column>
        <el-table-column label="电话" min-width="160">
          <template #default="{ row }">
            <el-input
              v-model="row.editPhone"
              size="small"
              placeholder="请输入联系电话"
              @blur="handleInlineUpdate(row, { phone: row.editPhone })"
            />
          </template>
        </el-table-column>
        <el-table-column label="通话状态" width="140">
          <template #default="{ row }">
            <el-select
              v-model="row.callStatus"
              size="small"
              placeholder="请选择"
              @change="(value) => handleInlineUpdate(row, { callStatus: value })"
            >
              <el-option v-for="item in callStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="线索阶段" width="140">
          <template #default="{ row }">
            <el-select
              v-model="row.leadStage"
              size="small"
              placeholder="请选择"
              @change="(value) => handleInlineUpdate(row, { leadStage: value })"
            >
              <el-option v-for="item in leadStageOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="线索标签" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.leadTags"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              size="small"
              placeholder="可直接打标签"
              @change="(value) => handleInlineUpdate(row, { leadTags: value })"
            >
              <el-option v-for="item in tagOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="意向门店" min-width="150">
          <template #default="{ row }">
            <el-select
              v-model="row.intendedStoreName"
              size="small"
              placeholder="请选择门店"
              @change="(value) => handleInlineUpdate(row, { intendedStoreName: value })"
            >
              <el-option v-for="item in storeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="线索分配时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.assignedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="线索创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="openDetailDrawer(row)">查看详情</el-button>
              <el-button
                v-if="row.isPaidCustomer"
                type="primary"
                size="small"
                @click="goToScheduling(row)"
              >
                预约门店档期
              </el-button>
              <el-dropdown v-if="canShowMoreActions(row)">
                <el-button size="small" plain>
                  更多操作
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canAssignClue" @click="openAssignDialog(row)">分配客服</el-dropdown-item>
                    <el-dropdown-item v-if="canRecycleClue" :disabled="!row.currentOwnerId" @click="handleRecycle(row)">回收线索</el-dropdown-item>
                    <el-dropdown-item v-if="canCreateOrderFromClue && !row.latestOrderId" @click="openOrderDialog(row)">转订单</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <el-drawer v-model="detailDrawerVisible" title="线索详情" size="560px">
      <template v-if="detailRow">
        <div class="stack-page">
          <section class="panel">
            <div class="detail-grid">
              <article class="detail-card">
                <h3>基础信息</h3>
                <p>线索编号：#{{ detailRow.id }}</p>
                <p>姓名：{{ displayName(detailRow) }}</p>
                <p>电话：{{ detailRow.editPhone || '--' }}</p>
                <p>来源形式：{{ formatProductSourceType(detailRow.productSourceType) }}</p>
              </article>
              <article class="detail-card">
                <h3>跟进状态</h3>
                <p>通话状态：{{ formatCallStatus(detailRow.callStatus) }}</p>
                <p>线索阶段：{{ formatLeadStage(detailRow.leadStage) }}</p>
                <p>意向门店：{{ detailRow.intendedStoreName || '--' }}</p>
                <p>分配客服：{{ detailRow.currentOwnerName || '未分配' }}</p>
              </article>
            </div>
          </section>

          <section class="panel">
            <div class="panel-heading compact">
              <div>
                <h3>标签与备注</h3>
                <p>这里展示客服在列表上补充的标签以及系统同步过来的线索信息。</p>
              </div>
            </div>
            <div class="chip-row">
              <el-tag v-for="tag in detailRow.leadTags" :key="tag" effect="plain" type="success">{{ tag }}</el-tag>
              <span v-if="!detailRow.leadTags.length" class="text-secondary">暂无标签</span>
            </div>
            <div class="table-note">
              最近订单状态：{{ detailRow.latestOrderId ? detailRow.latestOrderStageLabel : '暂无订单' }}
            </div>
          </section>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="createDialogVisible" title="新增客资" width="560px">
      <el-form :model="clueForm" label-width="92px">
        <el-form-item label="姓名">
          <el-input v-model="clueForm.name" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="clueForm.phone" placeholder="手机号和微信至少填写一个" />
        </el-form-item>
        <el-form-item label="微信">
          <el-input v-model="clueForm.wechat" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="clueForm.sourceChannel" style="width: 100%">
            <el-option label="抖音" value="DOUYIN" />
            <el-option label="分销" value="DISTRIBUTOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源 ID">
          <el-input v-model="clueForm.sourceId" placeholder="外部来源 ID 或分销商 ID" />
        </el-form-item>
        <el-form-item label="原始数据">
          <el-input
            v-model="clueForm.rawData"
            type="textarea"
            :rows="4"
            placeholder="建议保留上游返回的 JSON 文本，便于后续追溯。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateClue">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" title="选择分配客服" width="520px">
      <div class="quick-button-row">
        <el-button
          v-for="staff in assignableStaff"
          :key="staff.userId"
          type="primary"
          plain
          @click="handleAssign(assignTarget, staff.userId)"
        >
          {{ staff.userName }}
        </el-button>
      </div>
      <template #footer>
        <el-button @click="assignDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="从线索创建订单" width="560px">
      <el-form :model="orderForm" label-width="92px">
        <el-form-item label="线索">
          <el-input :model-value="selectedClueLabel" disabled />
        </el-form-item>
        <el-form-item label="订单类型">
          <el-select v-model="orderForm.type" style="width: 100%">
            <el-option label="定金" :value="1" />
            <el-option label="卡券" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单金额">
          <el-input-number v-model="orderForm.amount" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="定金金额">
          <el-input-number
            v-model="orderForm.deposit"
            :min="0"
            :precision="2"
            :disabled="orderForm.type === 2"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="orderForm.remark" type="textarea" :rows="3" placeholder="填写转化说明或订单备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateOrder">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { assignClue, createClue, createOrder, recycleClue } from '../api/actions'
import { fetchDutyCustomerServices } from '../api/clueManagement'
import { fetchClues } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { currentUser, hasAccess } from '../utils/auth'
import {
  formatCallStatus,
  formatDateTime,
  formatLeadStage,
  formatOrderStage,
  formatProductSourceType,
  normalize
} from '../utils/format'
import { listStoreNames, loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const router = useRouter()
const consoleState = reactive(loadSystemConsoleState())
const loading = ref(true)
const clues = ref([])
const dutyStaff = ref([])
const createDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const orderDialogVisible = ref(false)
const detailDrawerVisible = ref(false)
const assignTarget = ref(null)
const selectedClue = ref(null)
const detailRow = ref(null)
const productSourceFilter = ref('ALL')
const mergedClues = computed(() => clues.value.map((item) => buildClueRow(item)))
const pagination = useTablePagination(mergedClues)
let refreshTimer = null

const filters = reactive({
  sourceChannel: '',
  status: ''
})

const clueForm = reactive({
  name: '',
  phone: '',
  wechat: '',
  sourceChannel: 'DOUYIN',
  sourceId: '',
  rawData: ''
})

const orderForm = reactive({
  type: 1,
  amount: 1999,
  deposit: 199,
  remark: ''
})

const roleCode = computed(() => currentUser.value?.roleCode || '')
const storeOptions = computed(() => {
  const values = [...listStoreNames(consoleState), ...clues.value.map((item) => item.storeName).filter(Boolean)]
  return [...new Set(values)]
})
const assignableStaff = computed(() => dutyStaff.value.filter((item) => item.onLeave !== 1))
const canCreateClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(roleCode.value))
const canAssignClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(roleCode.value))
const canRecycleClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(roleCode.value))
const canCreateOrderFromClue = computed(() => hasAccess('ORDER'))
const paidClueCount = computed(() => mergedClues.value.filter((item) => item.isPaidCustomer).length)
const callbackCount = computed(() => mergedClues.value.filter((item) => item.callStatus === 'CALLBACK').length)

const selectedClueLabel = computed(() => {
  if (!selectedClue.value) {
    return ''
  }
  return `${displayName(selectedClue.value)} / ${selectedClue.value.editPhone || '--'}`
})

const callStatusOptions = [
  { label: '未通话', value: 'NOT_CALLED' },
  { label: '已接通', value: 'CONNECTED' },
  { label: '未接通', value: 'MISSED' },
  { label: '待回拨', value: 'CALLBACK' },
  { label: '无效号码', value: 'INVALID' }
]

const leadStageOptions = [
  { label: '新线索', value: 'NEW' },
  { label: '已联系', value: 'CONTACTED' },
  { label: '高意向', value: 'INTENT' },
  { label: '待预约', value: 'APPOINTMENT_PENDING' },
  { label: '已预约', value: 'APPOINTED' },
  { label: '已到店', value: 'ARRIVED' },
  { label: '已成交', value: 'CLOSED' }
]

const tagOptions = ['高意向', '待回拨', '团购', '表单', '已付款', '待预约', '复诊']

function replaceConsoleState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(consoleState, loadSystemConsoleState())
}

function currentTimestampString() {
  const value = new Date()
  const offset = value.getTimezoneOffset() * 60000
  return new Date(value.getTime() - offset).toISOString().slice(0, 19).replace('T', ' ')
}

function defaultCallStatus(row) {
  if (isPaidCustomer(row.latestOrderStatus)) {
    return 'CONNECTED'
  }
  if (['ASSIGNED', 'FOLLOWING', 'CONVERTED'].includes(normalize(row.status))) {
    return 'CALLBACK'
  }
  return 'NOT_CALLED'
}

function defaultLeadStage(row) {
  if (isPaidCustomer(row.latestOrderStatus)) {
    if (['USED', 'COMPLETED', 'FINISHED'].includes(normalize(row.latestOrderStatus))) {
      return 'CLOSED'
    }
    if (['ARRIVED', 'SERVING'].includes(normalize(row.latestOrderStatus))) {
      return 'ARRIVED'
    }
    if (normalize(row.latestOrderStatus) === 'APPOINTMENT') {
      return 'APPOINTED'
    }
    return 'APPOINTMENT_PENDING'
  }
  if (normalize(row.status) === 'CONVERTED') {
    return 'INTENT'
  }
  if (['ASSIGNED', 'FOLLOWING'].includes(normalize(row.status))) {
    return 'CONTACTED'
  }
  return 'NEW'
}

function defaultLeadTags(row) {
  const values = []
  if (row.productSourceType) {
    values.push(formatProductSourceType(row.productSourceType))
  }
  if (row.currentOwnerId) {
    values.push('已分配')
  }
  if (isPaidCustomer(row.latestOrderStatus)) {
    values.push('已付款')
  }
  return [...new Set(values.filter(Boolean))]
}

function findClueProfile(clueId) {
  return (consoleState.clueConsoleProfiles || []).find((item) => item.clueId === clueId) || null
}

function ensureClueProfile(row) {
  const existing = findClueProfile(row.id)
  if (existing) {
    return existing
  }
  return {
    id: nextSystemId(consoleState.clueConsoleProfiles || []),
    clueId: row.id,
    displayName: row.name || '',
    phone: row.phone || '',
    callStatus: defaultCallStatus(row),
    leadStage: defaultLeadStage(row),
    leadTags: defaultLeadTags(row),
    intendedStoreName: row.storeName || storeOptions.value[0] || '静安门店',
    assignedAt: row.currentOwnerId ? row.createdAt || currentTimestampString() : '',
    updatedAt: currentTimestampString()
  }
}

function buildClueRow(row) {
  const profile = ensureClueProfile(row)
  const orderStageLabel = row.latestOrderId ? formatOrderStage(row.latestOrderStatus) : '暂无订单'
  return {
    ...row,
    editName: profile.displayName || row.name || '',
    editPhone: profile.phone || row.phone || '',
    callStatus: profile.callStatus,
    leadStage: profile.leadStage,
    leadTags: [...(profile.leadTags || [])],
    intendedStoreName: profile.intendedStoreName || row.storeName || '',
    assignedAt: row.currentOwnerId ? profile.assignedAt || row.createdAt : '',
    latestOrderStageLabel: orderStageLabel,
    isPaidCustomer: isPaidCustomer(row.latestOrderStatus)
  }
}

function displayName(row) {
  return row.editName || row.name || `线索#${row.id}`
}

function isPaidCustomer(status) {
  return ['PAID', 'PAID_DEPOSIT', 'APPOINTMENT', 'ARRIVED', 'SERVING', 'USED', 'COMPLETED', 'FINISHED'].includes(
    normalize(status)
  )
}

function canShowMoreActions(row) {
  return canAssignClue.value || canRecycleClue.value || (canCreateOrderFromClue.value && !row.latestOrderId)
}

function handleInlineUpdate(row, patch, options = {}) {
  const nextPatch = { ...patch }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'displayName')) {
    nextPatch.displayName = String(nextPatch.displayName || '').trim()
  }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'phone')) {
    nextPatch.phone = String(nextPatch.phone || '').trim()
  }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'leadTags')) {
    nextPatch.leadTags = [...new Set((nextPatch.leadTags || []).map((item) => String(item || '').trim()).filter(Boolean))]
  }

  const profile = ensureClueProfile(row)
  const changed = Object.entries(nextPatch).some(([key, value]) => JSON.stringify(profile[key]) !== JSON.stringify(value))
  if (!changed) {
    return
  }

  const nextProfiles = [...(consoleState.clueConsoleProfiles || [])]
  const index = nextProfiles.findIndex((item) => item.clueId === row.id)
  const nextProfile = {
    ...profile,
    ...nextPatch,
    updatedAt: currentTimestampString()
  }
  if (index >= 0) {
    nextProfiles[index] = nextProfile
  } else {
    nextProfiles.push(nextProfile)
  }
  replaceConsoleState({
    ...consoleState,
    clueConsoleProfiles: nextProfiles
  })
  if (!options.silent) {
    ElMessage.success('线索信息已更新')
  }
}

async function loadClues() {
  loading.value = true
  try {
    clues.value = await fetchClues({
      sourceChannel: filters.sourceChannel || undefined,
      productSourceType: productSourceFilter.value === 'ALL' ? undefined : productSourceFilter.value,
      status: filters.status || undefined
    })
    pagination.reset()
  } catch {
    clues.value = []
  } finally {
    loading.value = false
  }
}

async function loadDutyStaff() {
  if (!canAssignClue.value) {
    dutyStaff.value = []
    return
  }
  try {
    dutyStaff.value = await fetchDutyCustomerServices()
  } catch {
    dutyStaff.value = []
  }
}

async function handleCreateClue() {
  await createClue({
    name: clueForm.name || undefined,
    phone: clueForm.phone || undefined,
    wechat: clueForm.wechat || undefined,
    sourceChannel: clueForm.sourceChannel,
    sourceId: clueForm.sourceId ? Number(clueForm.sourceId) : undefined,
    rawData: clueForm.rawData || undefined
  })
  ElMessage.success('客资已创建')
  createDialogVisible.value = false
  Object.assign(clueForm, {
    name: '',
    phone: '',
    wechat: '',
    sourceChannel: 'DOUYIN',
    sourceId: '',
    rawData: ''
  })
  await loadClues()
}

function openAssignDialog(row) {
  assignTarget.value = row
  assignDialogVisible.value = true
}

async function handleAssign(row, userId) {
  if (!row?.id) {
    return
  }
  await assignClue({
    clueId: row.id,
    userId
  })
  handleInlineUpdate(row, {
    assignedAt: currentTimestampString(),
    callStatus: row.callStatus === 'NOT_CALLED' ? 'CALLBACK' : row.callStatus
  }, { silent: true })
  assignDialogVisible.value = false
  ElMessage.success('线索已分配')
  await loadClues()
}

async function handleRecycle(row) {
  await recycleClue(row.id)
  handleInlineUpdate(row, {
    assignedAt: '',
    callStatus: 'NOT_CALLED'
  }, { silent: true })
  ElMessage.success('线索已回收到公海')
  await loadClues()
}

function openOrderDialog(row) {
  selectedClue.value = row
  orderForm.type = 1
  orderForm.amount = 1999
  orderForm.deposit = 199
  orderForm.remark = ''
  orderDialogVisible.value = true
}

async function handleCreateOrder() {
  if (!selectedClue.value) {
    return
  }

  const payload = {
    clueId: selectedClue.value.id,
    type: orderForm.type,
    amount: orderForm.amount,
    remark: orderForm.remark || undefined
  }
  if (orderForm.type === 1) {
    payload.deposit = orderForm.deposit
  }

  const order = await createOrder(payload)
  ElMessage.success(`订单已创建：${order.orderNo}`)
  orderDialogVisible.value = false
  await loadClues()
}

function openDetailDrawer(row) {
  detailRow.value = row
  detailDrawerVisible.value = true
}

function goToScheduling(row) {
  if (!row.latestOrderId) {
    ElMessage.warning('当前线索还没有可排档的订单')
    return
  }
  router.push({
    path: '/clues/scheduling',
    query: {
      orderId: row.latestOrderId,
      clueId: row.id
    }
  })
}

onMounted(async () => {
  await Promise.all([loadClues(), loadDutyStaff()])
  refreshTimer = window.setInterval(loadClues, 15000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>
