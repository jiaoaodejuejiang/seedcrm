<template>
  <div class="stack-page member-page">
    <section class="panel member-toolbar">
      <div class="member-tabs">
        <button
          v-for="tab in sourceTabs"
          :key="tab.value"
          class="member-tab"
          :class="{ 'is-active': filters.sourceTab === tab.value }"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <el-form class="compact-filter" :model="filters" inline>
        <el-form-item label="姓名">
          <el-input v-model="filters.name" clearable placeholder="客户姓名" @keyup.enter="submitSearch" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" clearable placeholder="手机号" @keyup.enter="submitSearch" />
        </el-form-item>
        <el-form-item label="平台会员编号">
          <el-input v-model="filters.externalMemberId" clearable placeholder="平台会员编号" @keyup.enter="submitSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-heading member-heading">
        <div>
          <h3>会员信息</h3>
        </div>
        <el-tag effect="light">{{ pagination.total }} 位会员</el-tag>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        class="crm-table"
        height="calc(100vh - 320px)"
        :empty-text="emptyText"
      >
        <el-table-column prop="name" label="姓名" min-width="120" fixed>
          <template #default="{ row }">{{ row.name || '--' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140" fixed>
          <template #default="{ row }">{{ row.phone || '--' }}</template>
        </el-table-column>
        <el-table-column prop="sourceDisplayName" label="成交来源" min-width="120">
          <template #default="{ row }">
            <el-tag :type="isDistributionRow(row) ? 'success' : 'info'" effect="light">
              {{ formatSourceDisplayName(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="externalMemberRole" label="会员身份" min-width="110">
          <template #default="{ row }">{{ memberRoleLabel(row.externalMemberRole) }}</template>
        </el-table-column>
        <el-table-column prop="externalMemberId" label="平台会员编号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.externalMemberId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="latestOrderNo" label="最近订单" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.latestOrderId" link type="primary" @click="openLatestOrder(row)">
              {{ row.latestOrderNo || row.latestOrderId }}
            </el-button>
            <span v-else class="text-secondary">暂无订单</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestOrderStatus" label="订单状态" min-width="110">
          <template #default="{ row }">
            <el-tag v-if="row.latestOrderStatus" effect="light" :type="statusTagType(row.latestOrderStatus)">
              {{ orderStatusLabel(row.latestOrderStatus) }}
            </el-tag>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestPlanOrderStatus" label="履约状态" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.latestPlanOrderStatus" effect="light" :type="planOrderStatusTag(row.latestPlanOrderStatus)">
              {{ planOrderStatusLabel(row.latestPlanOrderStatus) }}
            </el-tag>
            <span v-else class="text-secondary">未排档</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canViewAmounts" prop="latestOrderAmount" label="最近金额" min-width="110">
          <template #default="{ row }">{{ money(row.latestOrderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="latestOrderTime" label="最近成交时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.latestOrderTime) }}</template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" min-width="90" />
        <el-table-column v-if="canViewAmounts" prop="totalOrderAmount" label="累计金额" min-width="110">
          <template #default="{ row }">{{ money(row.totalOrderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="wecomBound" label="企微绑定" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.wecomBound ? 'success' : 'info'" effect="light">
              {{ row.wecomBound ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="privateDomainOwner" label="私域负责人" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.privateDomainOwner || '--' }}</template>
        </el-table-column>
        <el-table-column prop="primaryTag" label="标签" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.primaryTag || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <div class="member-actions">
              <el-button v-if="row.latestOrderId" link type="primary" @click="openLatestOrder(row)">最近订单</el-button>
              <span v-else class="action-hint">暂无订单</span>
              <el-button v-if="row.latestPlanOrderId" link type="primary" @click="openPlanOrder(row)">履约</el-button>
              <span v-else class="action-hint">未排档</span>
              <el-button link type="primary" @click="openCustomer(row)">客户详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="pagination.total"
          :page-size="pagination.pageSize"
          :current-page="pagination.page"
          :page-sizes="[30, 50]"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMembers } from '../api/member'
import { canViewBusinessAmounts } from '../utils/auth'
import { formatDateTime, normalize, statusTagType } from '../utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const rows = ref([])
const canViewAmounts = computed(() => canViewBusinessAmounts())
const sourceTabs = [
  { label: '全部成交会员', value: 'all' },
  { label: '分销成交', value: 'distribution' },
  { label: '抖音成交', value: 'douyin' }
]
const filters = reactive({
  sourceTab: String(route.query.sourceTab || 'all'),
  name: String(route.query.name || ''),
  phone: String(route.query.phone || ''),
  externalMemberId: String(route.query.externalMemberId || '')
})
const pagination = reactive({
  page: Number(route.query.page || 1),
  pageSize: 30,
  total: 0
})

const emptyText = computed(() => {
  if (filters.sourceTab === 'distribution') {
    return '暂无分销成交会员'
  }
  if (filters.sourceTab === 'douyin') {
    return '暂无抖音成交会员'
  }
  return '暂无符合条件的会员，可切换成交来源或重置筛选'
})

async function loadMembers() {
  loading.value = true
  try {
    const result = await fetchMembers({
      ...filters,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    rows.value = result?.records || []
    pagination.total = Number(result?.total || 0)
    pagination.page = Number(result?.page || pagination.page)
    pagination.pageSize = Number(result?.pageSize || pagination.pageSize)
  } finally {
    loading.value = false
  }
}

function submitSearch() {
  pagination.page = 1
  loadMembers()
}

function switchTab(sourceTab) {
  filters.sourceTab = sourceTab
  pagination.page = 1
  loadMembers()
}

function resetFilters() {
  filters.name = ''
  filters.phone = ''
  filters.externalMemberId = ''
  pagination.page = 1
  loadMembers()
}

function handleSizeChange(size) {
  pagination.pageSize = size
  pagination.page = 1
  loadMembers()
}

function handlePageChange(page) {
  pagination.page = page
  loadMembers()
}

function openCustomer(row, extraQuery = {}) {
  if (!row?.customerId) {
    return
  }
  router.push({
    path: `/customers/${row.customerId}`,
    query: {
      from: 'private-domain-members',
      sourceTab: filters.sourceTab,
      page: pagination.page,
      phone: filters.phone || undefined,
      name: filters.name || undefined,
      externalMemberId: filters.externalMemberId || undefined,
      ...extraQuery
    }
  })
}

function openLatestOrder(row) {
  openCustomer(row, {
    orderId: row.latestOrderId || undefined,
    orderNo: row.latestOrderNo || undefined
  })
}

function openPlanOrder(row) {
  if (row?.latestPlanOrderId) {
    router.push(`/plan-orders/${row.latestPlanOrderId}`)
  }
}

function money(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

function planOrderStatusLabel(value) {
  return (
    {
      ARRIVED: '已到店',
      SERVICING: '服务中',
      FINISHED: '已完成'
    }[normalize(value)] || value || '--'
  )
}

function planOrderStatusTag(value) {
  return (
    {
      ARRIVED: 'warning',
      SERVICING: 'primary',
      FINISHED: 'success'
    }[normalize(value)] || 'info'
  )
}

function orderStatusLabel(value) {
  return (
    {
      PAID: '已付款',
      USED: '已核销',
      CANCELLED: '已取消',
      REFUND_PENDING: '退款中',
      REFUNDED: '已退款',
      FINISHED: '已完成',
      SERVICING: '服务中'
    }[normalize(value)] || value || '--'
  )
}

function memberRoleLabel(value) {
  return (
    {
      LEADER: '团长',
      MEMBER: '团员',
      BUYER: '会员'
    }[normalize(value)] || value || '会员'
  )
}

function isDistributionRow(row) {
  return ['DISTRIBUTION', 'DISTRIBUTOR'].includes(normalize(row?.source))
    || ['DISTRIBUTION', 'DISTRIBUTOR'].includes(normalize(row?.sourceChannel))
}

function formatSourceDisplayName(row) {
  if (isDistributionRow(row)) {
    return '分销成交'
  }
  if (normalize(row?.source) === 'DOUYIN' || normalize(row?.sourceChannel) === 'DOUYIN') {
    return '抖音成交'
  }
  return row?.sourceDisplayName || '其他成交'
}

onMounted(loadMembers)
</script>

<style scoped>
.member-toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.member-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.member-tab {
  background: #f7f4ec;
  border: 1px solid rgba(46, 64, 87, 0.12);
  border-radius: 999px;
  color: #4b5563;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  padding: 9px 16px;
  transition: all 0.16s ease;
}

.member-tab.is-active {
  background: #173b33;
  border-color: #173b33;
  box-shadow: 0 10px 24px rgba(23, 59, 51, 0.18);
  color: #fff;
}

.member-heading {
  align-items: center;
}

.member-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.action-hint {
  color: #9ca3af;
  font-size: 12px;
}

.compact-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .member-toolbar {
    gap: 10px;
  }

  .table-pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
