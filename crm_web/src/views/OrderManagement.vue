<template>
  <div class="stack-page order-management-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>订单总数</span>
        <strong>{{ orders.length }}</strong>
      </article>
      <article class="metric-card">
        <span>待核验</span>
        <strong>{{ pendingVerificationCount }}</strong>
      </article>
      <article class="metric-card">
        <span>已完成</span>
        <strong>{{ completedCount }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="statusFilter" clearable placeholder="订单状态" style="width: 160px" @change="loadOrders">
            <el-option label="全部状态" value="" />
            <el-option label="已预约" value="appointment" />
            <el-option label="已完成" value="completed" />
          </el-select>
          <el-input v-model="customerPhoneKeyword" placeholder="手机号搜索" clearable style="width: 180px" />
          <el-input v-model="customerNameKeyword" placeholder="姓名搜索" clearable style="width: 180px" />
        </div>
        <div class="action-group">
          <el-button type="primary" @click="loadOrders">查询</el-button>
        </div>
      </div>

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
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核验状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.verificationStatus || 'UNVERIFIED')">
              {{ formatVerificationStatus(row.verificationStatus || 'UNVERIFIED') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务单状态" min-width="140">
          <template #default="{ row }">
            <span v-if="row.planOrderId">{{ row.serviceDetailJson ? '已填写' : '待填写' }}</span>
            <span v-else>待创建</span>
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
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button type="primary" size="small" :disabled="!canOpenServiceForm(row)" @click="openServiceForm(row)">
                {{ serviceButtonLabel(row) }}
              </el-button>
              <el-button v-if="row.planOrderId" size="small" plain @click="openQrDialog(row)">服务单扫码页</el-button>
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

    <el-dialog v-model="qrDialogVisible" title="服务单扫码页" width="420px">
      <div v-if="qrPreview.orderNo" class="stack-page">
        <section class="panel compact-panel qr-panel">
          <div class="qr-panel__code">
            <img v-if="qrPreview.image" :src="qrPreview.image" alt="服务单扫码页二维码" />
          </div>
          <div class="qr-panel__meta">
            <strong>{{ qrPreview.customerName || '未绑定客户' }}</strong>
            <span>{{ qrPreview.customerPhone || '--' }} / {{ qrPreview.storeName || '--' }}</span>
            <small>订单号 {{ qrPreview.orderNo || '--' }}</small>
            <el-button text type="primary" @click="copyQrLink">复制扫码页链接</el-button>
          </div>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useRouter } from 'vue-router'
import { createPlanOrder } from '../api/actions'
import { fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import {
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatVerificationStatus,
  normalize,
  statusTagType
} from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])
const pagination = useTablePagination(orders)
const statusFilter = ref('')
const customerNameKeyword = ref('')
const customerPhoneKeyword = ref('')
const qrDialogVisible = ref(false)
const qrPreview = ref({
  orderNo: '',
  customerName: '',
  customerPhone: '',
  storeName: '',
  url: '',
  image: ''
})

const pendingVerificationCount = computed(
  () => orders.value.filter((item) => normalize(item.verificationStatus || 'UNVERIFIED') !== 'VERIFIED').length
)
const completedCount = computed(() => orders.value.filter((item) => normalize(item.status) === 'COMPLETED').length)

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders({
      status: statusFilter.value || undefined,
      customerName: customerNameKeyword.value || undefined,
      customerPhone: customerPhoneKeyword.value || undefined
    })
    pagination.reset()
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
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
  const planOrderId = await ensurePlanOrder(row)
  if (row.serviceDetailJson) {
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

async function openQrDialog(row) {
  if (!row.planOrderId) {
    ElMessage.warning('请先打开服务单，创建后再生成扫码页')
    return
  }
  const planOrderId = row.planOrderId
  const url = `${window.location.origin}/service-scan/${planOrderId}`
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

function serviceButtonLabel(row) {
  if (!canOpenServiceForm(row)) {
    if (isAwaitingPayment(row)) {
      return '待付款'
    }
    if (!row.planOrderId) {
      return '无服务单'
    }
    return '暂不可用'
  }
  if (normalize(row.verificationStatus || 'UNVERIFIED') !== 'VERIFIED') {
    return '去核验'
  }
  if (row.serviceDetailJson) {
    return '查看服务单'
  }
  return '填写服务单'
}

function canOpenServiceForm(row) {
  return Boolean(row?.planOrderId) || canCreatePlanOrder(row)
}

function canCreatePlanOrder(row) {
  const normalizedStatus = normalize(row?.status)
  return ['PAID', 'PAID_DEPOSIT', 'APPOINTMENT', 'ARRIVED', 'SERVING', 'SERVICING'].includes(normalizedStatus)
}

function isAwaitingPayment(row) {
  return normalize(row?.status) === 'CREATED'
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

onMounted(loadOrders)
</script>

<style scoped>
.qr-panel {
  display: grid;
  gap: 16px;
  justify-items: center;
}

.qr-panel__code {
  padding: 12px;
  border-radius: 20px;
  background: #f6f8fa;
}

.qr-panel__code img {
  display: block;
  width: 240px;
  height: 240px;
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
</style>
