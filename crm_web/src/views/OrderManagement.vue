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
        <el-table-column v-if="canViewVerificationAmounts" label="核销金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(resolveVerificationAmount(row)) }}
          </template>
        </el-table-column>
        <el-table-column v-if="canViewServiceConfirmAmounts" label="确认单金额" width="130">
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
        <el-table-column label="最近履约记录" min-width="220">
          <template #default="{ row }">
            <div v-if="latestFulfillmentRecord(row)" class="fulfillment-record-preview">
              <div class="fulfillment-record-preview__head">
                <el-tag size="small" :type="fulfillmentStageTagType(latestFulfillmentRecord(row)?.stage)" effect="light">
                  {{ fulfillmentStageLabel(latestFulfillmentRecord(row)?.stage) }}
                </el-tag>
                <strong>{{ fulfillmentActionLabel(latestFulfillmentRecord(row)?.actionType) }}</strong>
              </div>
              <span>{{ fulfillmentRecordSummary(latestFulfillmentRecord(row)) }}</span>
            </div>
            <span v-else class="text-secondary">暂无履约记录</span>
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
              <el-button v-if="canManageRefunds && canRefundOrder(row)" type="danger" size="small" plain @click="handleRefund(row)">退款</el-button>
              <el-button size="small" plain @click="openWecomDialog(row)">企微活码</el-button>
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

    <el-dialog v-model="wecomDialogVisible" title="请客户扫码添加接待企微" width="440px">
      <div v-loading="wecomLoading" class="wecom-dialog">
        <template v-if="wecomPreview">
          <div class="qr-panel__code">
            <img v-if="wecomQrImage" :src="wecomQrImage" alt="企业微信活码" />
            <div v-else class="qr-panel__empty">暂无可用二维码</div>
          </div>
          <div class="wecom-dialog__meta">
            <strong>{{ activeOrderForWecom?.customerName || '未命名客户' }}</strong>
            <span>{{ activeOrderForWecom?.customerPhone || '--' }}</span>
            <span>{{ wecomPreview.storeName }}</span>
            <span>活码：{{ wecomPreview.codeName || '--' }}</span>
            <span>二维码类型：{{ wecomModeLabel(wecomPreview.bindingStatus) }}</span>
            <span>绑定人：{{ wecomPreview.bindingUserName || '--' }} / {{ maskPhone(wecomPreview.bindingUserPhone) }}</span>
            <span>订单绑定：{{ maskBindingState(wecomPreview.bindingState) }}</span>
            <el-tag :type="wecomStatusTagType(wecomPreview.bindingStatus)" effect="light">
              {{ wecomPreview.bindingMessage || '专属活码已就绪' }}
            </el-tag>
            <small v-if="wecomContactLink" class="wecom-dialog__link">{{ wecomContactLinkLabel }}：{{ wecomContactLink }}</small>
          </div>
          <div class="action-group action-group--center">
            <el-button text type="primary" :disabled="!wecomContactLink" @click="copyWecomLink">{{ wecomCopyButtonLabel }}</el-button>
            <el-button text @click="router.push('/private-domain/live-code')">去活码配置</el-button>
          </div>
        </template>

        <el-empty v-else description="当前门店未发布企业微信活码">
          <el-button type="primary" @click="router.push('/private-domain/live-code')">去活码配置</el-button>
        </el-empty>
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
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useRouter } from 'vue-router'
import { createPlanOrder } from '../api/actions'
import { refundOrder } from '../api/order'
import { fetchOrderWecomLiveCode, fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { canViewBusinessAmounts, canViewServiceAmounts } from '../utils/auth'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'
import { formatDateTime, formatMoney, formatOrderStatus, formatVerificationStatus, normalize, statusTagType } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])
const statusFilter = ref('pending-verification')
const customerNameKeyword = ref('')
const customerPhoneKeyword = ref('')
const wecomDialogVisible = ref(false)
const refundDialogVisible = ref(false)
const refundSubmitting = ref(false)
const canViewVerificationAmounts = computed(() => canViewBusinessAmounts())
const canViewServiceConfirmAmounts = computed(() => canViewServiceAmounts())
const canManageRefunds = computed(() => canViewBusinessAmounts())
const wecomLoading = ref(false)
const activeOrderForWecom = ref(null)
const wecomPreview = ref(null)
const wecomQrImage = ref('')
const systemState = loadSystemConsoleState()
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

const workflowOptions = computed(() => [
  { value: 'all', label: '全部', count: storeServiceOrders.value.length },
  { value: 'pending-verification', label: '待核销', count: pendingVerificationCount.value },
  { value: 'pending-service', label: '待确认服务', count: pendingServiceCount.value },
  { value: 'serving', label: '服务中', count: servingCount.value },
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
    completed: completedCount.value,
    all: storeServiceOrders.value.length
  }
  if (counts[statusFilter.value] > 0) {
    return
  }
  statusFilter.value =
    ['pending-verification', 'pending-service', 'serving', 'completed', 'all'].find((key) => counts[key] > 0) || 'all'
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
  if (workflowStatus(row) === 'completed' && (!row.planOrderId || !row.serviceDetailJson)) {
    ElMessage.warning('该已完成订单缺少服务确认单，请先清理或补齐脏数据')
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

async function openWecomDialog(row) {
  activeOrderForWecom.value = row
  wecomPreview.value = null
  wecomQrImage.value = ''
  wecomDialogVisible.value = true
  wecomLoading.value = true
  try {
    wecomPreview.value = await fetchOrderWecomLiveCode(row.id)
    await refreshWecomQrImage()
  } finally {
    wecomLoading.value = false
  }
}

function serviceButtonLabel(row) {
  if (!canOpenServiceForm(row)) {
    return isAwaitingPayment(row) ? '待付款' : '暂不可用'
  }
  const currentWorkflow = workflowStatus(row)
  if (currentWorkflow === 'pending-verification') {
    return '去核销'
  }
  if (currentWorkflow === 'pending-service') {
    return row.serviceDetailJson ? '继续确认单' : '填确认单'
  }
  if (currentWorkflow === 'serving') {
    return '完成服务'
  }
  return '查看服务单'
}

function serviceConfirmationLabel(row) {
  if (row.serviceDetailJson) {
    return '已生成'
  }
  if (workflowStatus(row) === 'completed') {
    return '暂无记录'
  }
  if (row.planOrderId) {
    return '待填写'
  }
  return '待生成'
}

function serviceConfirmationTagType(row) {
  if (row.serviceDetailJson) {
    return 'success'
  }
  if (workflowStatus(row) === 'completed') {
    return 'info'
  }
  if (row.planOrderId) {
    return 'primary'
  }
  return 'info'
}

function fulfillmentRecords(row) {
  return Array.isArray(row?.fulfillmentRecords) ? row.fulfillmentRecords : []
}

function latestFulfillmentRecord(row) {
  return fulfillmentRecords(row)[0] || null
}

function fulfillmentStageLabel(stage) {
  const labels = {
    SCHEDULE: '排档',
    VERIFY: '核销',
    SERVICE_FORM: '确认单',
    SERVICE: '服务',
    FINANCE: '财务'
  }
  return labels[normalize(stage)] || '履约'
}

function fulfillmentStageTagType(stage) {
  const types = {
    SCHEDULE: 'primary',
    VERIFY: 'success',
    SERVICE_FORM: 'warning',
    SERVICE: 'success',
    FINANCE: 'info'
  }
  return types[normalize(stage)] || 'info'
}

function fulfillmentActionLabel(actionType) {
  const labels = {
    APPOINTMENT_CREATE: '已约档',
    APPOINTMENT_CHANGE: '已改档',
    APPOINTMENT_CANCEL: '取消预约',
    DIRECT_DEPOSIT_VERIFY: '定金免码确认',
    EXTERNAL_VOUCHER_VERIFY: '团购券核销',
    VOUCHER_VERIFY: '团购券核销',
    VOUCHER_VERIFY_FAILED: '核销失败',
    SERVICE_FORM_PRINT: '打印确认单',
    SERVICE_FORM_CONFIRM: '确认纸质单',
    SERVICE_FINISH: '服务完成',
    ORDER_COMPLETE: '订单完成',
    REFUND_REGISTER: '退款冲正'
  }
  return labels[normalize(actionType)] || '履约记录'
}

function fulfillmentRecordSummary(record) {
  if (!record) {
    return '--'
  }
  const parts = []
  const actor = record.operatorUserName || (record.operatorUserId ? `ID ${record.operatorUserId}` : '')
  if (actor) {
    parts.push(actor)
  }
  const safeDetails = Array.isArray(record.detailItems) ? record.detailItems.map((item) => String(item || '').trim()).filter(Boolean) : []
  if (record.summary && fulfillmentActionLabel(record.actionType) === '履约记录') {
    safeDetails.unshift(String(record.summary).trim())
  }
  parts.push(...safeDetails.slice(0, 2))
  const time = formatDateTime(record.createTime)
  if (time) {
    parts.push(time)
  }
  return parts.join(' / ') || '--'
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
  if (amount) {
    return formatMoney(amount)
  }
  return workflowStatus(row) === 'completed' ? '暂无记录' : '待填写'
}

async function copyWecomLink() {
  if (!wecomContactLink.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(wecomContactLink.value)
    ElMessage.success('企业微信链接已复制')
  } catch {
    ElMessage.warning('当前环境不支持自动复制')
  }
}

function maskPhone(phone) {
  const value = String(phone || '').trim()
  if (!value) {
    return '--'
  }
  return value.length >= 7 ? `${value.slice(0, 3)}****${value.slice(-4)}` : '已绑定'
}

function maskBindingState(state) {
  const value = String(state || '').trim()
  if (!value) {
    return '--'
  }
  return `已生成（尾号 ${value.slice(-6)}）`
}

const wecomContactLink = computed(() => resolveWecomContactLink(wecomPreview.value))
const wecomContactLinkLabel = computed(() => (wecomPreview.value?.bindingStatus === 'MOCK_READY' ? '系统 MOCK 地址' : '企业微信地址'))
const wecomCopyButtonLabel = computed(() => (wecomPreview.value?.bindingStatus === 'MOCK_READY' ? '复制内部测试链接' : '复制客户添加链接'))

async function refreshWecomQrImage() {
  const preview = wecomPreview.value
  if (!preview) {
    wecomQrImage.value = ''
    return
  }
  const contactLink = resolveWecomContactLink(preview)
  if (preview.bindingStatus === 'MOCK_READY' && contactLink) {
    wecomQrImage.value = await QRCode.toDataURL(contactLink, {
      width: 260,
      margin: 2,
      color: {
        dark: '#173042',
        light: '#ffffff'
      }
    })
    return
  }
  wecomQrImage.value = preview.qrCodeUrl || ''
}

function resolveWecomContactLink(preview) {
  const link = String(preview?.shortLink || '').trim()
  if (link) {
    return /^https?:\/\//i.test(link) ? link : buildSystemUrl(systemState, 'callback', link)
  }
  const qrCodeUrl = String(preview?.qrCodeUrl || '').trim()
  if (!qrCodeUrl || qrCodeUrl.startsWith('data:image')) {
    return ''
  }
  return qrCodeUrl
}

function wecomStatusTagType(status) {
  if (status === 'LIVE_READY') {
    return 'success'
  }
  if (status === 'MOCK_READY') {
    return 'warning'
  }
  return 'danger'
}

function wecomModeLabel(status) {
  if (status === 'LIVE_READY') {
    return 'LIVE 真实企微活码'
  }
  if (status === 'MOCK_READY') {
    return 'MOCK 演示活码'
  }
  if (status === 'MISSING_ACCOUNT') {
    return '未维护员工企微账号'
  }
  return '企微活码不可用'
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

.fulfillment-record-preview {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.fulfillment-record-preview__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.fulfillment-record-preview__head strong,
.fulfillment-record-preview span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fulfillment-record-preview span {
  color: #64748b;
  font-size: 12px;
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

.wecom-dialog__link {
  word-break: break-all;
  color: #94a3b8;
}

.qr-panel__empty {
  display: grid;
  place-items: center;
  width: 220px;
  height: 220px;
  border-radius: 22px;
  border: 1px dashed #cbd5e1;
  color: #94a3b8;
  background: #f8fafc;
}

.action-group--center {
  justify-content: center;
}

.refund-dialog {
  display: grid;
  gap: 16px;
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
