<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>订单总数</span>
        <strong>{{ orders.length }}</strong>
        <small>订单必须停留在主链里，并且始终绑定到客户。</small>
      </article>
      <article class="metric-card">
        <span>已支付</span>
        <strong>{{ paidCount }}</strong>
        <small>已支付订单才允许继续创建服务单。</small>
      </article>
      <article class="metric-card">
        <span>已使用</span>
        <strong>{{ usedCount }}</strong>
        <small>已使用订单代表履约闭环已经结束。</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="statusFilter" clearable placeholder="订单状态" style="width: 180px">
            <el-option label="已支付" value="paid" />
            <el-option label="已使用" value="used" />
          </el-select>
          <el-button @click="loadOrders">筛选</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="orders" stripe>
        <el-table-column label="订单" min-width="190">
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
        <el-table-column label="定金" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.deposit) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务单" min-width="150">
          <template #default="{ row }">
            <span v-if="row.planOrderId">#{{ row.planOrderId }} / {{ formatPlanOrderStatus(row.planOrderStatus) }}</span>
            <span v-else class="text-secondary">未创建</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button type="primary" size="small" @click="enterService(row)">
                {{ row.planOrderId ? '打开服务单' : '创建服务单' }}
              </el-button>
              <el-button v-if="row.customerId" size="small" plain @click="router.push(`/customers/${row.customerId}`)">
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
import { formatDateTime, formatMoney, formatOrderStatus, formatOrderType, formatPlanOrderStatus, statusTagType } from '../utils/format'

const router = useRouter()
const loading = ref(true)
const orders = ref([])
const statusFilter = ref('')

const paidCount = computed(() => orders.value.filter((item) => item.status === 'paid').length)
const usedCount = computed(() => orders.value.filter((item) => item.status === 'used').length)

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders({
      status: statusFilter.value || undefined
    })
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function enterService(row) {
  try {
    let planOrderId = row.planOrderId
    if (!planOrderId) {
      if (row.status !== 'paid') {
        ElMessage.warning('只有“已支付”状态的订单才能创建服务单')
        return
      }
      const planOrder = await createPlanOrder({ orderId: row.id })
      planOrderId = planOrder.id
      ElMessage.success('服务单已创建')
      await loadOrders()
    }
    await router.push(`/plan-orders/${planOrderId}`)
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

onMounted(loadOrders)
</script>
