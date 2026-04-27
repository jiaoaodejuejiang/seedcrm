<template>
  <div class="stack-page order-management-page">
    <section class="panel compact-panel">
      <div class="toolbar toolbar--stack">
        <div class="toolbar__filters">
          <el-input v-model="customerPhoneKeyword" placeholder="手机号搜索" clearable style="width: 180px" @keyup.enter="loadOrders" />
          <el-input v-model="customerNameKeyword" placeholder="姓名搜索" clearable style="width: 180px" @keyup.enter="loadOrders" />
          <el-button type="primary" @click="loadOrders">查询</el-button>
        </div>

        <div class="toolbar__meta">
          <div class="status-chip-group">
            <button
              v-for="option in workflowOptions"
              :key="option.value"
              type="button"
              class="status-chip"
              :class="{ 'is-active': statusFilter === option.value }"
              @click="handleFilterChange(option.value)"
            >
              <span>{{ option.label }}</span>
            </button>
          </div>

        </div>
      </div>
    </section>

    <section class="panel">
      <el-table v-loading="loading" :data="pagination.rows" stripe>
        <el-table-column label="手机号" min-width="150">
          <template #default="{ row }">
            <strong>{{ row.customerPhone || '--' }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="姓名" min-width="120">
          <template #default="{ row }">
            {{ row.customerName || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="门店" min-width="140" prop="storeName" />
        <el-table-column label="核销金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(resolveVerificationAmount(row)) }}
          </template>
        </el-table-column>
        <el-table-column label="确认单金额" width="130">
          <template #default="{ row }">
            {{ formatServiceConfirmAmount(row) }}
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核销状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.verificationStatus || 'UNVERIFIED')">
              {{ formatVerificationStatus(row.verificationStatus || 'UNVERIFIED') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务阶段" width="130">
          <template #default="{ row }">
            <el-tag :type="workflowTagType(row)">{{ workflowLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认单" width="130">
          <template #default="{ row }">
            <el-tag :type="serviceConfirmationTagType(row)">{{ serviceConfirmationLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="付款时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="预约时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.appointmentTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="470" fixed="right">
          <template #default="{ row }">
            <div class="action-group action-group--wrap">
              <el-button type="primary" size="small" :disabled="!canOpenServiceForm(row)" @click="openServiceForm(row)">
                {{ serviceButtonLabel(row) }}
              </el-button>
              <el-button v-if="canRefundOrder(row)" type="danger" size="small" plain @click="handleRefund(row)">登记门店退款</el-button>
              <el-button size="small" plain @click="openWecomDialog(row)">企微活码</el-button>
              <el-button v-if="row.planOrderId && workflowStatus(row) === 'pending-service'" size="small" plain @click="openQrDialog(row)">
                客户扫码签单
              </el-button>
              <el-button v-if="row.customerId" link @click="router.push(`/customers/${row.customerId}`)">客户详情</el-button>
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

    <el-dialog v-model="qrDialogVisible" title="客户扫码签单" width="420px">
      <div v-if="qrPreview.orderNo" class="stack-page">
        <section class="panel compact-panel qr-panel">
          <div class="qr-panel__code">
            <img v-if="qrPreview.image" :src="qrPreview.image" alt="客户扫码签单二维码" />
          </div>
          <div class="qr-panel__meta">
            <strong>{{ qrPreview.customerName || '未绑定客户' }}</strong>
            <span>{{ qrPreview.customerPhone || '--' }} / {{ qrPreview.storeName || '--' }}</span>
            <small>订单号：{{ qrPreview.orderNo || '--' }}</small>
            <el-button text type="primary" @click="copyQrLink">复制签单页链接</el-button>
          </div>
        </section>
      </div>
    </el-dialog>

    <el-dialog v-model="wecomDialogVisible" title="添加企业微信" width="440px">
      <div v-loading="wecomLoading" class="wecom-dialog">
        <template v-if="wecomPreview">
          <div class="qr-panel__code">
            <img :src="wecomPreview.qrCodeUrl" alt="企业微信活码" />
          </div>
          <div class="wecom-dialog__meta">
            <strong>{{ activeOrderForWecom?.customerName || '未命名客户' }}</strong>
            <span>{{ activeOrderForWecom?.customerPhone || '--' }}</span>
            <span>{{ wecomPreview.storeName }}</span>
            <span>活码：{{ wecomPreview.codeName || '--' }}</span>
            <el-tag type="success" effect="light">门店活码已就绪</el-tag>
          </div>
          <div class="action-group action-group--center">
            <el-button text type="primary" @click="copyWecomLink">复制添加链接</el-button>
            <el-button text @click="router.push('/private-domain/live-code')">去活码配置</el-button>
          </div>
        </template>

        <el-empty v-else description="当前门店未发布企业微信活码">
          <el-button type="primary" @click="router.push('/private-domain/live-code')">去活码配置</el-button>
        </el-empty>
      </div>
    </el-dialog>

    <el-dialog v-model="legacyServiceDialogVisible" title="补录服务确认单" width="560px">
      <div class="legacy-service-empty">
        <el-empty description="该订单暂无服务确认单记录" />
        <div v-if="legacyServiceOrder" class="legacy-service-empty__summary">
          <span>客户：{{ legacyServiceOrder.customerName || legacyServiceOrder.customerPhone || '--' }}</span>
          <span>门店：{{ legacyServiceOrder.storeName || '--' }}</span>
          <span>核销金额：{{ formatMoney(resolveVerificationAmount(legacyServiceOrder)) }}</span>
          <span>确认单金额：{{ formatServiceConfirmAmount(legacyServiceOrder) }}</span>
        </div>
        <el-alert
          title="该入口用于历史数据补录，只保存服务确认单内容，不改变订单已完成状态。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div class="action-group action-group--center">
          <el-button @click="legacyServiceDialogVisible = false">稍后处理</el-button>
          <el-button type="primary" @click="handleBackfillLegacyServiceOrder">补录确认单</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="refundDialogVisible" title="门店服务退款" width="600px">
      <div class="refund-dialog">
        <el-alert
          title="本次仅登记门店服务内容退款，不会发起微信、银行或原路资金退款。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div class="refund-summary">
          <span>客户：{{ refundForm.order?.customerName || refundForm.order?.customerPhone || '--' }}</span>
          <span>门店：{{ refundForm.order?.storeName || '--' }}</span>
          <span>确认单金额：{{ formatServiceConfirmAmount(refundForm.order) }}</span>
          <span>影响范围：门店人员整体绩效</span>
        </div>
        <el-form label-width="110px">
          <el-form-item label="退款金额">
            <el-input-number
              v-model="refundForm.serviceRefundAmount"
              :min="0"
              :max="resolveServiceConfirmAmount(refundForm.order) || 999999999"
              :precision="2"
              controls-position="right"
              placeholder="请输入门店服务内容退款金额"
            />
          </el-form-item>
          <el-form-item label="原因类型">
            <el-select v-model="refundForm.reasonType" placeholder="请选择退款原因">
              <el-option label="客户主动取消" value="CUSTOMER_CANCEL" />
              <el-option label="服务无法履约" value="SERVICE_UNAVAILABLE" />
              <el-option label="门店协商退款" value="STORE_NEGOTIATED" />
              <el-option label="订单信息错误" value="ORDER_INFO_ERROR" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="退款原因">
            <el-input v-model="refundForm.reason" type="textarea" :rows="3" placeholder="请填写退款原因，选择“其他”时必须填写详细说明" />
          </el-form-item>
          <el-form-item label="冲正处理">
            <div class="refund-dialog__checks">
              <el-checkbox v-model="refundForm.reverseStorePerformance" disabled>冲正门店人员整体绩效薪资</el-checkbox>
              <small>门店端仅登记服务确认单线下金额退款，不处理团购券/定金原路退款，也不冲正客服或分销绩效。</small>
            </div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="refundForm.remark" placeholder="选填" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">确认登记退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { useRouter } from 'vue-router'
import { createPlanOrder } from '../api/actions'
import { refundOrder } from '../api/order'
import { fetchOrderWecomLiveCode, fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'
import { formatDateTime, formatMoney, formatOrderStatus, formatVerificationStatus, normalize, statusTagType } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])
const statusFilter = ref('pending-verification')
const customerNameKeyword = ref('')
const customerPhoneKeyword = ref('')
const qrDialogVisible = ref(false)
const wecomDialogVisible = ref(false)
const legacyServiceDialogVisible = ref(false)
const refundDialogVisible = ref(false)
const refundSubmitting = ref(false)
const wecomLoading = ref(false)
const activeOrderForWecom = ref(null)
const wecomPreview = ref(null)
const legacyServiceOrder = ref(null)
const systemState = loadSystemConsoleState()
const qrPreview = ref({
  orderNo: '',
  customerName: '',
  customerPhone: '',
  storeName: '',
  url: '',
  image: ''
})
const refundForm = reactive({
  order: null,
  serviceRefundAmount: null,
  reasonType: '',
  reason: '',
  reverseStorePerformance: true,
  remark: ''
})

const storeServiceOrders = computed(() => orders.value.filter(isStoreServiceOrder))
const filteredOrders = computed(() =>
  storeServiceOrders.value.filter(matchesStatusFilter)
)
const pagination = useTablePagination(filteredOrders)

const pendingVerificationCount = computed(
  () => storeServiceOrders.value.filter((item) => workflowStatus(item) === 'pending-verification').length
)
const pendingServiceCount = computed(() => storeServiceOrders.value.filter((item) => workflowStatus(item) === 'pending-service').length)
const servingCount = computed(() => storeServiceOrders.value.filter((item) => workflowStatus(item) === 'serving').length)
const completedCount = computed(() => storeServiceOrders.value.filter((item) => workflowStatus(item) === 'completed').length)
const backfillCount = computed(() => storeServiceOrders.value.filter(needsServiceBackfill).length)

const workflowOptions = computed(() => [
  { value: 'all', label: '全部', count: storeServiceOrders.value.length },
  { value: 'pending-verification', label: '待核销', count: pendingVerificationCount.value },
  { value: 'pending-service', label: '待确认服务', count: pendingServiceCount.value },
  { value: 'serving', label: '服务中', count: servingCount.value },
  { value: 'backfill', label: '待补确认单', count: backfillCount.value },
  { value: 'completed', label: '已完成', count: completedCount.value }
])

function isStoreServiceOrder(row) {
  return [
    'PAID',
    'PAID_DEPOSIT',
    'APPOINTMENT',
    'ARRIVED',
    'SERVING',
    'SERVICING',
    'COMPLETED',
    'FINISHED',
    'USED',
    'REFUNDED'
  ].includes(normalize(row?.status))
}

function workflowStatus(row) {
  const orderStatus = normalize(row?.status)
  const planOrderStatus = normalize(row?.planOrderStatus)
  const verificationStatus = normalize(row?.verificationStatus || 'UNVERIFIED')

  if (['COMPLETED', 'FINISHED', 'USED', 'REFUNDED'].includes(orderStatus) || planOrderStatus === 'FINISHED') {
    return 'completed'
  }
  if (['SERVING', 'SERVICING'].includes(orderStatus) || planOrderStatus === 'SERVICING') {
    return 'serving'
  }
  if (verificationStatus !== 'VERIFIED') {
    return 'pending-verification'
  }
  return 'pending-service'
}

function workflowLabel(row) {
  return (
    {
      'pending-verification': '待核销',
      'pending-service': '待确认服务',
      serving: '服务中',
      completed: '已完成'
    }[workflowStatus(row)] || '--'
  )
}

function workflowTagType(row) {
  return (
    {
      'pending-verification': 'warning',
      'pending-service': 'primary',
      serving: 'success',
      completed: 'info'
    }[workflowStatus(row)] || 'info'
  )
}

function matchesStatusFilter(row) {
  if (statusFilter.value === 'all') {
    return true
  }
  if (statusFilter.value === 'backfill') {
    return needsServiceBackfill(row)
  }
  return workflowStatus(row) === statusFilter.value
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders({
      customerName: customerNameKeyword.value || undefined,
      customerPhone: customerPhoneKeyword.value || undefined
    })
    ensureAvailableStatusFilter()
    pagination.reset()
  } catch {
    orders.value = []
    statusFilter.value = 'all'
  } finally {
    loading.value = false
  }
}

function handleFilterChange(nextStatus) {
  statusFilter.value = nextStatus
  pagination.reset()
}

function ensureAvailableStatusFilter() {
  const counts = {
    'pending-verification': pendingVerificationCount.value,
    'pending-service': pendingServiceCount.value,
    serving: servingCount.value,
    backfill: backfillCount.value,
    completed: completedCount.value,
    all: storeServiceOrders.value.length
  }
  if (counts[statusFilter.value] > 0) {
    return
  }
  statusFilter.value =
    ['pending-verification', 'pending-service', 'serving', 'backfill', 'completed', 'all'].find((key) => counts[key] > 0) || 'all'
}

async function ensurePlanOrder(row) {
  if (row.planOrderId) {
    return row.planOrderId
  }
  if (!canCreatePlanOrder(row)) {
    throw new Error('当前订单状态暂不支持创建服务单')
  }
  const planOrder = await createPlanOrder({ orderId: row.id })
  await loadOrders()
  ElMessage.success('服务单已创建')
  return planOrder.id
}

async function openServiceForm(row) {
  if (!canOpenServiceForm(row)) {
    ElMessage.warning(serviceButtonLabel(row))
    return
  }
  if (needsServiceBackfill(row)) {
    legacyServiceOrder.value = row
    legacyServiceDialogVisible.value = true
    return
  }
  const planOrderId = await ensurePlanOrder(row)
  if (workflowStatus(row) === 'completed') {
    await router.push({
      path: `/plan-orders/${planOrderId}`,
      query: {
        mode: 'view'
      }
    })
    return
  }
  await router.push(`/plan-orders/${planOrderId}`)
}

async function handleBackfillLegacyServiceOrder() {
  const row = legacyServiceOrder.value
  if (!row?.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '将为该历史已完成订单补建服务确认单。此操作只用于补录确认单内容，不改变订单状态，是否继续？',
      '补录确认单',
      {
        confirmButtonText: '继续补录',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  legacyServiceDialogVisible.value = false
  try {
    const planOrderId = row.planOrderId || (await ensurePlanOrder(row))
    await router.push({
      path: `/plan-orders/${planOrderId}`,
      query: {
        mode: 'backfill'
      }
    })
  } catch (error) {
    ElMessage.error(error?.message || '补录确认单失败，请稍后重试')
  }
}

async function openQrDialog(row) {
  if (!row.planOrderId) {
    ElMessage.warning('请先创建服务单，再生成客户扫码签单页')
    return
  }
  const url = buildSystemUrl(systemState, 'scan', `/service-scan/${row.planOrderId}`)
  qrPreview.value = {
    orderNo: row.orderNo,
    customerName: row.customerName,
    customerPhone: row.customerPhone,
    storeName: row.storeName,
    url,
    image: await QRCode.toDataURL(url, { margin: 1, width: 240 })
  }
  qrDialogVisible.value = true
}

async function openWecomDialog(row) {
  activeOrderForWecom.value = row
  wecomDialogVisible.value = true
  wecomLoading.value = true
  try {
    wecomPreview.value = await fetchOrderWecomLiveCode(row.id)
  } finally {
    wecomLoading.value = false
  }
}

function serviceButtonLabel(row) {
  if (!canOpenServiceForm(row)) {
    return isAwaitingPayment(row) ? '待付款' : '暂不可用'
  }
  if (needsServiceBackfill(row)) {
    return '补录确认单'
  }
  const currentWorkflow = workflowStatus(row)
  if (currentWorkflow === 'pending-verification') {
    return '核销订单'
  }
  if (currentWorkflow === 'pending-service') {
    return row.serviceDetailJson ? '继续签单' : '请客户签确认单'
  }
  if (currentWorkflow === 'serving') {
    return '完成服务'
  }
  return '查看服务单'
}

function serviceConfirmationLabel(row) {
  if (needsServiceBackfill(row)) {
    return '待补确认单'
  }
  if (row.serviceDetailJson) {
    return '已生成'
  }
  if (row.planOrderId) {
    return '待填写'
  }
  return '待生成'
}

function serviceConfirmationTagType(row) {
  if (needsServiceBackfill(row)) {
    return 'warning'
  }
  if (row.serviceDetailJson) {
    return 'success'
  }
  if (row.planOrderId) {
    return 'primary'
  }
  return 'info'
}

function canRefundOrder(row) {
  return ['COMPLETED', 'FINISHED', 'USED'].includes(normalize(row?.status)) && Number(resolveServiceConfirmAmount(row) || 0) > 0
}

function canOpenServiceForm(row) {
  return workflowStatus(row) === 'completed' || Boolean(row?.planOrderId) || canCreatePlanOrder(row)
}

function canCreatePlanOrder(row) {
  const normalizedStatus = normalize(row?.status)
  return ['PAID', 'PAID_DEPOSIT', 'APPOINTMENT', 'ARRIVED', 'SERVING', 'SERVICING', 'COMPLETED', 'FINISHED', 'USED'].includes(
    normalizedStatus
  )
}

function needsServiceBackfill(row) {
  return workflowStatus(row) === 'completed' && (!row.planOrderId || !row.serviceDetailJson)
}

function isAwaitingPayment(row) {
  return normalize(row?.status) === 'CREATED'
}

function handleRefund(row) {
  Object.assign(refundForm, {
    order: row,
    serviceRefundAmount: resolveServiceConfirmAmount(row),
    reasonType: '',
    reason: '',
    reverseStorePerformance: true,
    remark: ''
  })
  refundDialogVisible.value = true
}

async function submitRefund() {
  if (!refundForm.order?.id) {
    return
  }
  if (Number(refundForm.serviceRefundAmount || 0) <= 0) {
    ElMessage.warning('请填写本次门店服务内容退款金额')
    return
  }
  if (!refundForm.reasonType) {
    ElMessage.warning('请选择退款原因类型')
    return
  }
  if (!String(refundForm.reason || '').trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  refundSubmitting.value = true
  try {
    await refundOrder({
      orderId: refundForm.order.id,
      refundScene: 'STORE_SERVICE',
      serviceRefundAmount: refundForm.serviceRefundAmount,
      idempotencyKey: buildStoreRefundIdempotencyKey(),
      outOrderNo: refundForm.order.orderNo,
      itemOrderId: refundForm.order.verificationCode,
      refundReasonType: refundForm.reasonType,
      refundReason: refundForm.reason,
      reverseSalary: true,
      reverseStorePerformance: refundForm.reverseStorePerformance,
      reverseCustomerService: false,
      reverseDistributor: false,
      remark: buildRefundRemark()
    })
    refundDialogVisible.value = false
    ElMessage.success('退款记录已登记')
    await loadOrders()
  } finally {
    refundSubmitting.value = false
  }
}

function buildRefundRemark() {
  const parts = [`门店服务内容退款：${refundReasonTypeLabel(refundForm.reasonType)} / ${refundForm.reason}`]
  if (refundForm.remark) {
    parts.push(`备注：${refundForm.remark}`)
  }
  parts.push('仅冲正门店人员整体绩效薪资')
  return parts.join('；')
}

function buildStoreRefundIdempotencyKey() {
  return [
    'STORE_REFUND',
    refundForm.order?.id || '',
    Number(refundForm.serviceRefundAmount || 0).toFixed(2),
    refundForm.reasonType || '',
    String(refundForm.reason || '').trim()
  ].join(':')
}

function refundReasonTypeLabel(value) {
  return (
    {
      CUSTOMER_CANCEL: '客户主动取消',
      SERVICE_UNAVAILABLE: '服务无法履约',
      STORE_NEGOTIATED: '门店协商退款',
      ORDER_INFO_ERROR: '订单信息错误',
      OTHER: '其他'
    }[value] || '未选择'
  )
}

function parseServiceDetail(row) {
  if (!row?.serviceDetailJson) {
    return null
  }
  try {
    return JSON.parse(row.serviceDetailJson)
  } catch {
    return null
  }
}

function toAmount(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

function resolveVerificationAmount(row) {
  const deposit = toAmount(row?.deposit)
  if (deposit && deposit > 0) {
    return deposit
  }
  return toAmount(row?.amount) || 0
}

function resolveServiceConfirmAmount(row) {
  const parsed = parseServiceDetail(row)
  const amount = toAmount(parsed?.serviceConfirmAmount)
  return amount && amount > 0 ? amount : null
}

function formatServiceConfirmAmount(row) {
  const amount = resolveServiceConfirmAmount(row)
  return amount ? formatMoney(amount) : '待填写'
}

async function copyQrLink() {
  if (!qrPreview.value.url) {
    return
  }
  try {
    await navigator.clipboard.writeText(qrPreview.value.url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制')
  }
}

async function copyWecomLink() {
  if (!wecomPreview.value?.shortLink && !wecomPreview.value?.qrCodeUrl) {
    return
  }
  try {
    await navigator.clipboard.writeText(wecomPreview.value.shortLink || wecomPreview.value.qrCodeUrl)
    ElMessage.success('企业微信链接已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制')
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.toolbar--stack {
  display: grid;
  gap: 14px;
}

.toolbar__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 14px;
}

.status-chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.status-chip.is-active {
  color: #14532d;
  border-color: rgba(22, 163, 74, 0.24);
  background: #effbf4;
  box-shadow: 0 12px 24px rgba(22, 163, 74, 0.08);
}

.action-group--wrap {
  flex-wrap: wrap;
}

.wecom-dialog {
  display: grid;
  gap: 18px;
}

.wecom-dialog__meta {
  display: grid;
  gap: 8px;
  text-align: center;
  color: #64748b;
}

.action-group--center {
  justify-content: center;
}

.legacy-service-empty,
.refund-dialog {
  display: grid;
  gap: 16px;
}

.legacy-service-empty__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  color: #475569;
}

.refund-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fbff;
  border: 1px solid #e5edf4;
  color: #334155;
  font-size: 13px;
}

.refund-dialog__checks {
  display: grid;
  gap: 6px;
}

.refund-dialog__checks small {
  color: #94a3b8;
  line-height: 1.6;
}

.qr-panel {
  display: grid;
  gap: 16px;
  justify-items: center;
}

.qr-panel__code {
  padding: 12px;
  border-radius: 20px;
  background: #f6f8fa;
  justify-self: center;
}

.qr-panel__code img {
  display: block;
  width: 240px;
  height: 240px;
  object-fit: contain;
}

.qr-panel__meta {
  display: grid;
  gap: 6px;
  justify-items: center;
  text-align: center;
  color: #69788a;
  word-break: break-all;
}

.qr-panel__meta small {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .toolbar__meta {
    flex-direction: column;
  }

  .refund-summary {
    grid-template-columns: 1fr;
  }
}
</style>
