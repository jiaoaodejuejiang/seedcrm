<template>
  <div class="service-layout">
    <section class="panel service-list">
      <div class="panel-heading">
        <div>
          <h3>服务单队列</h3>
          <p>严格按照“已到店 -> 服务中 -> 已完成”顺序履约。</p>
        </div>
      </div>

      <div class="toolbar toolbar--compact">
        <el-select v-model="statusFilter" clearable placeholder="服务单状态" style="width: 100%">
          <el-option label="已到店" value="arrived" />
          <el-option label="服务中" value="servicing" />
          <el-option label="已完成" value="finished" />
        </el-select>
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
            <strong>{{ item.customer?.name || '客户缺失' }}</strong>
            <p>#{{ item.planOrderId }} / {{ item.orderNo || '--' }}</p>
          </div>
          <el-tag :type="statusTagType(item.planOrderStatus)">{{ formatPlanOrderStatus(item.planOrderStatus) }}</el-tag>
        </button>
      </div>
    </section>

    <section class="service-detail" v-loading="detailLoading">
      <div v-if="detail" class="stack-page">
        <section class="metrics-row">
          <article class="metric-card">
            <span>客户</span>
            <strong>{{ detail.customer?.name || '未知客户' }}</strong>
            <small>{{ detail.customer?.phone || '--' }}</small>
          </article>
          <article class="metric-card">
            <span>订单</span>
            <strong>{{ formatMoney(detail.order?.amount) }}</strong>
            <small>{{ formatOrderType(detail.order?.type) }} / {{ formatOrderStatus(detail.order?.status) }}</small>
          </article>
          <article class="metric-card">
            <span>服务单状态</span>
            <strong>{{ formatPlanOrderStatus(detail.summary?.planOrderStatus) }}</strong>
            <small>角色记录数：{{ detail.roleRecords?.length || 0 }}</small>
          </article>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <div>
              <h3>履约动作</h3>
              <p>推动服务单流转，并在完成后把订单回写为“已使用”。</p>
            </div>
            <div class="action-group">
              <el-button type="primary" :disabled="!canArrive" @click="handlePlanAction('arrive')">到店</el-button>
              <el-button type="warning" :disabled="!canStart" @click="handlePlanAction('start')">开始服务</el-button>
              <el-button type="success" :disabled="!canFinish" @click="handlePlanAction('finish')">完成服务</el-button>
              <el-button v-if="detail.customer?.customerId" plain @click="router.push(`/customers/${detail.customer.customerId}`)">
                客户详情
              </el-button>
            </div>
          </div>

          <p v-if="showFinishRoleHint" class="table-note">当前服务单还没有“当前角色记录”，因此不能直接完成服务。</p>

          <div class="timeline-grid">
            <div class="timeline-item">
              <span>到店时间</span>
              <strong>{{ formatDateTime(detail.summary?.arriveTime) }}</strong>
            </div>
            <div class="timeline-item">
              <span>开始时间</span>
              <strong>{{ formatDateTime(detail.summary?.startTime) }}</strong>
            </div>
            <div class="timeline-item">
              <span>完成时间</span>
              <strong>{{ formatDateTime(detail.summary?.finishTime) }}</strong>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading">
            <div>
              <h3>当前角色分配</h3>
              <p>order_role_record 必须完整记录，且同角色同时间不可重叠。</p>
            </div>
          </div>

          <div class="role-grid">
            <article v-for="role in roleCards" :key="role.roleCode" class="role-card">
              <div class="role-card__header">
                <strong>{{ formatRoleCode(role.roleCode) }}</strong>
                <span>{{ role.current?.userName || '未分配' }}</span>
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
              <h3>角色历史</h3>
              <p>展示轮转历史，便于后续薪酬结算和履约审计。</p>
            </div>
          </div>

          <el-table :data="detail.roleRecords || []" stripe>
            <el-table-column label="角色" width="140">
              <template #default="{ row }">
                {{ row.roleCode ? formatRoleCode(row.roleCode) : row.roleName || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="人员" width="140" prop="userName" />
            <el-table-column label="开始时间" min-width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column label="结束时间" min-width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="是否当前" width="120">
              <template #default="{ row }">
                <el-tag :type="row.isCurrent === 1 ? 'success' : 'info'">{{ row.isCurrent === 1 ? '当前' : '历史' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <section v-else class="panel empty-panel">
        <el-empty description="请先从左侧选择一个服务单，或先在订单页创建服务单。" />
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { arrivePlanOrder, assignPlanOrderRole, finishPlanOrder, startPlanOrder } from '../api/actions'
import { fetchPlanOrderDetail, fetchPlanOrders, fetchStaffOptions } from '../api/workbench'
import {
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatRoleCode,
  formatOrderType,
  formatPlanOrderStatus,
  statusTagType
} from '../utils/format'

const route = useRoute()
const router = useRouter()
const listLoading = ref(true)
const detailLoading = ref(false)
const planOrders = ref([])
const detail = ref(null)
const staffOptions = ref([])
const statusFilter = ref('')

const roleCards = computed(() => {
  const currentRoleMap = new Map((detail.value?.currentRoles || []).map((item) => [item.roleCode, item]))
  return (staffOptions.value || []).map((role) => ({
    ...role,
    current: currentRoleMap.get(role.roleCode)
  }))
})

const canArrive = computed(() => detail.value?.summary?.planOrderStatus === 'arrived' && !detail.value?.summary?.arriveTime)
const canStart = computed(
  () => detail.value?.summary?.planOrderStatus === 'arrived' && !!detail.value?.summary?.arriveTime && !detail.value?.summary?.startTime
)
const canFinish = computed(
  () =>
    detail.value?.summary?.planOrderStatus === 'servicing' &&
    !!detail.value?.summary?.startTime &&
    !detail.value?.summary?.finishTime &&
    !!detail.value?.currentRoles?.length
)
const showFinishRoleHint = computed(
  () =>
    detail.value?.summary?.planOrderStatus === 'servicing' &&
    !!detail.value?.summary?.startTime &&
    !detail.value?.summary?.finishTime &&
    !detail.value?.currentRoles?.length
)

async function loadPlanOrders() {
  listLoading.value = true
  try {
    planOrders.value = await fetchPlanOrders({
      status: statusFilter.value || undefined
    })
    const currentId = Number(route.params.id)
    const exists = planOrders.value.some((item) => item.planOrderId === currentId)
    if (!route.params.id && planOrders.value.length) {
      await router.replace(`/plan-orders/${planOrders.value[0].planOrderId}`)
    } else if (route.params.id && !exists) {
      if (planOrders.value.length) {
        await router.replace(`/plan-orders/${planOrders.value[0].planOrderId}`)
      } else {
        await router.replace('/plan-orders')
      }
    }
  } catch {
    planOrders.value = []
  } finally {
    listLoading.value = false
  }
}

async function loadStaffOptions() {
  try {
    staffOptions.value = await fetchStaffOptions()
  } catch {
    staffOptions.value = []
  }
}

async function loadDetail(planOrderId) {
  if (!planOrderId) {
    detail.value = null
    return
  }
  detailLoading.value = true
  try {
    detail.value = await fetchPlanOrderDetail(planOrderId)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

async function reloadAll() {
  await Promise.all([loadPlanOrders(), loadStaffOptions()])
  if (route.params.id) {
    await loadDetail(Number(route.params.id))
  }
}

async function handlePlanAction(action) {
  try {
    const payload = {
      planOrderId: Number(route.params.id)
    }
    if (action === 'arrive') {
      await arrivePlanOrder(payload)
    } else if (action === 'start') {
      await startPlanOrder(payload)
    } else if (action === 'finish') {
      await finishPlanOrder(payload)
    }
    ElMessage.success('服务单状态已更新')
    await reloadAll()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleAssignRole(roleCode, userId) {
  try {
    await assignPlanOrderRole({
      planOrderId: Number(route.params.id),
      roleCode,
      userId
    })
    ElMessage.success('角色分配已更新')
    await reloadAll()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

watch(statusFilter, loadPlanOrders)

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
