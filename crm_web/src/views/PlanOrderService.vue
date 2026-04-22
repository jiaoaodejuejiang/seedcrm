<template>
  <div class="service-layout">
    <section class="panel service-list">
      <div class="panel-heading">
        <div>
          <h3>当前到店单</h3>
          <p>按最短链路处理：到店、开始、完成。</p>
        </div>
      </div>

      <div class="service-list__items" v-loading="listLoading">
        <button
          v-for="item in planOrders"
          :key="item.planOrderId"
          class="service-queue-item"
          :class="{ 'is-active': Number(route.params.id) === item.planOrderId }"
          @click="router.push(`/plan-orders/${item.planOrderId}`)"
        >
          <div>
            <strong>{{ item.customer?.name || '未绑定客户' }}</strong>
            <p>#{{ item.planOrderId }} / {{ item.orderNo || '--' }}</p>
          </div>
          <el-tag :type="statusTagType(item.planOrderStatus)">
            {{ formatPlanOrderStatus(item.planOrderStatus) }}
          </el-tag>
        </button>
      </div>
    </section>

    <section class="service-detail">
      <div v-if="detail" class="stack-page">
        <section class="metrics-row">
          <article class="metric-card">
            <span>客户</span>
            <strong>{{ detail.customer?.name || '未绑定' }}</strong>
            <small>{{ detail.customer?.phone || '--' }}</small>
          </article>
          <article class="metric-card">
            <span>订单金额</span>
            <strong>{{ formatMoney(detail.order?.amount) }}</strong>
            <small>{{ formatChannel(detail.order?.sourceChannel) }}</small>
          </article>
          <article class="metric-card">
            <span>当前状态</span>
            <strong>{{ formatPlanOrderStatus(detail.summary?.planOrderStatus) }}</strong>
            <small>订单状态：{{ formatOrderStage(detail.order?.status) }}</small>
          </article>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <div>
              <h3>服务推进</h3>
              <p>不要跳页面，直接在这里完成整个门店现场流程。</p>
            </div>
            <div class="action-group">
              <el-button
                type="primary"
                :disabled="!canArrive"
                @click="handlePlanAction('arrive')"
              >
                到店
              </el-button>
              <el-button
                type="warning"
                :disabled="!canStart"
                @click="handlePlanAction('start')"
              >
                开始服务
              </el-button>
              <el-button
                type="success"
                :disabled="!canFinish"
                @click="handlePlanAction('finish')"
              >
                完成服务
              </el-button>
              <el-button
                v-if="detail.customer?.customerId"
                plain
                @click="router.push(`/customers/${detail.customer.customerId}`)"
              >
                查看客户详情
              </el-button>
            </div>
          </div>

          <div class="timeline-grid">
            <div class="timeline-item">
              <span>到店时间</span>
              <strong>{{ formatDateTime(detail.summary?.arriveTime) }}</strong>
            </div>
            <div class="timeline-item">
              <span>开始服务</span>
              <strong>{{ formatDateTime(detail.summary?.startTime) }}</strong>
            </div>
            <div class="timeline-item">
              <span>完成服务</span>
              <strong>{{ formatDateTime(detail.summary?.finishTime) }}</strong>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <div>
              <h3>角色分配</h3>
              <p>每个岗位直接点人即可切换，不需要再进二级页面。</p>
            </div>
          </div>

          <div class="role-grid">
            <article v-for="role in roleCards" :key="role.roleCode" class="role-card">
              <div class="role-card__header">
                <strong>{{ role.roleName }}</strong>
                <span>{{ role.current?.userName || '待分配' }}</span>
              </div>
              <div class="quick-button-row">
                <el-button
                  v-for="staff in role.staffOptions"
                  :key="staff.userId"
                  size="small"
                  :type="role.current?.userId === staff.userId ? 'primary' : 'default'"
                  @click="handleAssignRole(role.roleCode, staff.userId)"
                >
                  {{ staff.userName }}
                </el-button>
              </div>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <div>
              <h3>角色记录</h3>
              <p>服务过程内所有角色切换都保留痕迹。</p>
            </div>
          </div>

          <el-table :data="detail.roleRecords" stripe>
            <el-table-column label="岗位" width="120">
              <template #default="{ row }">
                {{ row.roleName }}
              </template>
            </el-table-column>
            <el-table-column label="员工" width="140">
              <template #default="{ row }">
                {{ row.userName }}
              </template>
            </el-table-column>
            <el-table-column label="开始时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column label="结束时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="当前生效" width="100">
              <template #default="{ row }">
                <el-tag :type="row.isCurrent === 1 ? 'success' : 'info'">
                  {{ row.isCurrent === 1 ? '当前' : '历史' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <section v-else class="panel empty-panel">
        <el-empty description="暂无到店单，请先从订单管理页进入服务流程" />
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  arrivePlanOrder,
  assignPlanOrderRole,
  finishPlanOrder,
  startPlanOrder
} from '../api/actions'
import {
  fetchPlanOrderDetail,
  fetchPlanOrders,
  fetchStaffOptions
} from '../api/workbench'
import {
  formatChannel,
  formatDateTime,
  formatMoney,
  formatOrderStage,
  formatPlanOrderStatus,
  statusTagType
} from '../utils/format'

const route = useRoute()
const router = useRouter()

const listLoading = ref(false)
const detailLoading = ref(false)
const planOrders = ref([])
const detail = ref(null)
const staffOptions = ref([])

const roleCards = computed(() => {
  const currentRoleMap = new Map((detail.value?.currentRoles || []).map((item) => [item.roleCode, item]))
  return (staffOptions.value || []).map((role) => ({
    ...role,
    current: currentRoleMap.get(role.roleCode)
  }))
})

const canArrive = computed(
  () => detail.value?.summary?.planOrderStatus === 'ARRIVED' && !detail.value?.summary?.arriveTime
)
const canStart = computed(
  () => detail.value?.summary?.planOrderStatus === 'ARRIVED' && !!detail.value?.summary?.arriveTime
)
const canFinish = computed(
  () => detail.value?.summary?.planOrderStatus === 'SERVICING' && !!detail.value?.summary?.startTime
)

async function loadPlanOrders() {
  listLoading.value = true
  try {
    planOrders.value = await fetchPlanOrders()
    if (!route.params.id && planOrders.value.length) {
      await router.replace(`/plan-orders/${planOrders.value[0].planOrderId}`)
    }
  } finally {
    listLoading.value = false
  }
}

async function loadStaffOptions() {
  staffOptions.value = await fetchStaffOptions()
}

async function loadDetail(planOrderId) {
  if (!planOrderId) {
    detail.value = null
    return
  }

  detailLoading.value = true
  try {
    detail.value = await fetchPlanOrderDetail(planOrderId)
  } finally {
    detailLoading.value = false
  }
}

async function reloadAll() {
  const planOrderId = Number(route.params.id)
  await Promise.all([loadPlanOrders(), loadStaffOptions()])
  if (planOrderId) {
    await loadDetail(planOrderId)
  }
}

async function handlePlanAction(action) {
  const planOrderId = Number(route.params.id)
  const payload = { planOrderId }
  if (action === 'arrive') {
    await arrivePlanOrder(payload)
  } else if (action === 'start') {
    await startPlanOrder(payload)
  } else if (action === 'finish') {
    await finishPlanOrder(payload)
  }
  ElMessage.success('服务状态已更新')
  await reloadAll()
}

async function handleAssignRole(roleCode, userId) {
  await assignPlanOrderRole({
    planOrderId: Number(route.params.id),
    roleCode,
    userId
  })
  ElMessage.success('角色已切换')
  await reloadAll()
}

watch(
  () => route.params.id,
  async (value) => {
    if (value) {
      await loadDetail(Number(value))
    } else {
      detail.value = null
    }
  },
  { immediate: true }
)

onMounted(async () => {
  await Promise.all([loadPlanOrders(), loadStaffOptions()])
  if (route.params.id) {
    await loadDetail(Number(route.params.id))
  }
})
</script>
