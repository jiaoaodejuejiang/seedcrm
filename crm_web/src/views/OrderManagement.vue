<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>待到店</span>
        <strong>{{ pendingArrivalCount }}</strong>
        <small>已创建订单，等待客户到店</small>
      </article>
      <article class="metric-card">
        <span>服务中</span>
        <strong>{{ servicingCount }}</strong>
        <small>已到店，当前正在服务流程中</small>
      </article>
      <article class="metric-card">
        <span>已完成</span>
        <strong>{{ completedCount }}</strong>
        <small>已完成服务和订单闭环</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="statusFilter" clearable placeholder="订单状态" style="width: 200px">
            <el-option label="待到店" value="CREATED" />
            <el-option label="已付定金" value="PAID_DEPOSIT" />
            <el-option label="已预约" value="APPOINTMENT" />
            <el-option label="服务中" value="SERVING" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
          <el-button @click="loadOrders">筛选</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="orders" stripe>
        <el-table-column label="订单号" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.orderNo }}</strong>
              <span>订单 #{{ row.id }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="客户" min-width="170">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.customerName || '待绑定客户' }}</strong>
              <span>{{ row.customerPhone || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源渠道" width="110">
          <template #default="{ row }">
            <el-tag>{{ formatChannel(row.sourceChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStage(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到店单" width="110">
          <template #default="{ row }">
            <span v-if="row.planOrderId">#{{ row.planOrderId }}</span>
            <span v-else class="text-secondary">未创建</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" type="primary" @click="enterService(row)">进入到店服务</el-button>
              <el-button
                v-if="row.customerId"
                size="small"
                plain
                @click="router.push(`/customers/${row.customerId}`)"
              >
                客户详情
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createPlanOrder } from '../api/actions'
import { fetchOrders } from '../api/workbench'
import {
  formatChannel,
  formatDateTime,
  formatMoney,
  formatOrderStage,
  statusTagType
} from '../utils/format'

const router = useRouter()
const loading = ref(false)
const orders = ref([])
const statusFilter = ref('')

const pendingArrivalCount = computed(() =>
  orders.value.filter((item) => ['CREATED', 'PAID_DEPOSIT', 'APPOINTMENT'].includes(item.status)).length
)
const servicingCount = computed(() =>
  orders.value.filter((item) => ['ARRIVED', 'SERVING'].includes(item.status)).length
)
const completedCount = computed(() => orders.value.filter((item) => item.status === 'COMPLETED').length)

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders({
      status: statusFilter.value || undefined
    })
  } finally {
    loading.value = false
  }
}

async function enterService(row) {
  let planOrderId = row.planOrderId
  if (!planOrderId) {
    const planOrder = await createPlanOrder({ orderId: row.id })
    planOrderId = planOrder.id
    ElMessage.success('已创建到店服务单')
    await loadOrders()
  }
  await router.push(`/plan-orders/${planOrderId}`)
}

onMounted(loadOrders)
</script>
