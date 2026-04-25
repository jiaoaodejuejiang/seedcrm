<template>
  <div class="stack-page order-management-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>当前列表订单</span>
        <strong>{{ orders.length }}</strong>
        <small>门店服务入口只围绕订单和服务单主链开展工作。</small>
      </article>
      <article class="metric-card">
        <span>已预约</span>
        <strong>{{ appointmentCount }}</strong>
        <small>已完成预约，待到店或待服务的订单。</small>
      </article>
      <article class="metric-card">
        <span>已完成</span>
        <strong>{{ completedCount }}</strong>
        <small>服务已闭环的订单，可继续回看确认单和历史记录。</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar-tabs">
          <el-radio-group v-model="statusFilter" @change="loadOrders">
            <el-radio-button value="">全部状态</el-radio-button>
            <el-radio-button value="appointment">已预约</el-radio-button>
            <el-radio-button value="completed">已完成</el-radio-button>
          </el-radio-group>
        </div>

        <div class="action-group">
          <el-button @click="loadOrders">刷新列表</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="pagination.rows" stripe>
        <el-table-column label="订单" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.orderNo }}</strong>
              <span>#{{ row.id }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="客户" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.customerName || '待绑定客户' }}</strong>
              <span>{{ row.customerPhone || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag>{{ formatOrderType(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预约时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.appointmentTime) }}
          </template>
        </el-table-column>
        <el-table-column label="服务单" min-width="140">
          <template #default="{ row }">
            <span v-if="row.planOrderId">#{{ row.planOrderId }} / {{ formatPlanOrderStatus(row.planOrderStatus) }}</span>
            <span v-else class="text-secondary">未创建</span>
          </template>
        </el-table-column>
        <el-table-column label="确认单" min-width="180">
          <template #default="{ row }">
            <div class="editable-cell">
              <span class="editable-cell__text">{{ confirmationSummary(row) }}</span>
              <el-button link class="editable-cell__trigger" :title="confirmationTriggerTitle(row)" @click="openServiceDrawer(row)">
                <el-icon>
                  <component :is="isCompletedOrder(row) ? View : EditPen" />
                </el-icon>
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button type="primary" size="small" @click="enterService(row)">
                {{ serviceButtonLabel(row) }}
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

    <el-drawer v-model="serviceDrawerVisible" :title="selectedOrderReadOnly ? '订单确认单查看' : '订单确认单确认'" size="560px">
      <template v-if="selectedOrder">
        <div class="stack-page">
          <section class="panel">
            <div class="detail-grid">
              <article class="detail-card">
                <h3>订单信息</h3>
                <p>订单号：{{ selectedOrder.orderNo }}</p>
                <p>订单状态：{{ formatOrderStatus(selectedOrder.status) }}</p>
                <p>订单金额：{{ formatMoney(selectedOrder.amount) }}</p>
              </article>
              <article class="detail-card">
                <h3>客户信息</h3>
                <p>客户姓名：{{ selectedOrder.customerName || '--' }}</p>
                <p>联系电话：{{ selectedOrder.customerPhone || '--' }}</p>
                <p>预约时间：{{ formatDateTime(selectedOrder.appointmentTime) }}</p>
              </article>
            </div>
          </section>

          <section class="panel">
            <div class="panel-heading compact">
              <div>
                <h3>到店详细需求</h3>
                <p>{{ selectedOrderReadOnly ? '该订单已完成，当前仅支持查看确认单内容。' : '此内容会作为门店确认单保存，支持后续服务人员继续补充。' }}</p>
              </div>
            </div>
            <el-input
              v-model="serviceRequirementForm.serviceRequirement"
              type="textarea"
              :rows="8"
              :readonly="selectedOrderReadOnly"
              placeholder="请输入客户到店需求、注意事项、服务偏好、禁忌项或到店确认内容"
            />
          </section>

          <div class="action-group flex-end">
            <el-button @click="serviceDrawerVisible = false">关闭</el-button>
            <el-button v-if="!selectedOrderReadOnly" type="primary" :loading="savingServiceRequirement" @click="saveCurrentOrderDetail">保存确认单</el-button>
            <el-button type="success" @click="enterService(selectedOrder)">
              {{ serviceButtonLabel(selectedOrder, true) }}
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { EditPen, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createPlanOrder } from '../api/actions'
import { saveOrderServiceDetail } from '../api/order'
import { fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, formatMoney, formatOrderStatus, formatOrderType, formatPlanOrderStatus, normalize, statusTagType } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])
const pagination = useTablePagination(orders)
const statusFilter = ref('')
const serviceDrawerVisible = ref(false)
const selectedOrder = ref(null)
const savingServiceRequirement = ref(false)

const serviceRequirementForm = reactive({
  serviceRequirement: ''
})

const appointmentCount = computed(() => orders.value.filter((item) => normalize(item.status) === 'APPOINTMENT').length)
const completedCount = computed(() => orders.value.filter((item) => normalize(item.status) === 'COMPLETED').length)
const selectedOrderReadOnly = computed(() => isCompletedOrder(selectedOrder.value))

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders({
      status: statusFilter.value || undefined
    })
    pagination.reset()
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

function openServiceDrawer(row) {
  selectedOrder.value = { ...row }
  serviceRequirementForm.serviceRequirement = row.remark || ''
  serviceDrawerVisible.value = true
}

async function saveCurrentOrderDetail() {
  if (!selectedOrder.value?.id || selectedOrderReadOnly.value) {
    return
  }
  savingServiceRequirement.value = true
  try {
    await saveOrderServiceDetail({
      orderId: selectedOrder.value.id,
      serviceRequirement: serviceRequirementForm.serviceRequirement || ''
    })
    ElMessage.success('确认单已保存')
    selectedOrder.value.remark = serviceRequirementForm.serviceRequirement || ''
    await loadOrders()
  } finally {
    savingServiceRequirement.value = false
  }
}

async function enterService(row) {
  let planOrderId = row.planOrderId
  if (!planOrderId) {
    if (row.statusCategory !== 'paid') {
      ElMessage.warning('只有已支付链路中的订单才能创建服务单')
      return
    }
    const planOrder = await createPlanOrder({ orderId: row.id })
    planOrderId = planOrder.id
    ElMessage.success('服务单已创建')
    await loadOrders()
  }
  await router.push(`/plan-orders/${planOrderId}`)
}

function isCompletedOrder(row) {
  const status = normalize(row?.status)
  return ['COMPLETED', 'USED', 'FINISHED'].includes(status)
}

function confirmationButtonLabel(row) {
  return isCompletedOrder(row) ? '查看确认单' : '确认单'
}

function confirmationSummary(row) {
  return row?.remark || '未填写'
}

function confirmationTriggerTitle(row) {
  return confirmationButtonLabel(row)
}

function serviceButtonLabel(row, inDrawer = false) {
  if (row?.planOrderId) {
    return isCompletedOrder(row) ? '查看服务单' : '打开服务单'
  }
  return inDrawer ? '创建并进入服务单' : '创建服务单'
}

onMounted(loadOrders)
</script>
