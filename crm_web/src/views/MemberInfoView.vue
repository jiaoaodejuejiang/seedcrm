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
          <el-input v-model="filters.name" clearable placeholder="客户姓名" @keyup.enter="loadMembers" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="filters.phone" clearable placeholder="手机号" @keyup.enter="loadMembers" />
        </el-form-item>
        <el-form-item label="外部会员ID">
          <el-input v-model="filters.externalMemberId" clearable placeholder="外部会员ID" @keyup.enter="loadMembers" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadMembers">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-heading member-heading">
        <div>
          <h3>会员信息</h3>
        </div>
        <el-tag effect="light">{{ pagination.total }} 位成交会员</el-tag>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        class="crm-table"
        height="calc(100vh - 320px)"
        empty-text="暂无会员信息"
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
        <el-table-column prop="externalMemberRole" label="外部身份" min-width="110">
          <template #default="{ row }">{{ row.externalMemberRole || '--' }}</template>
        </el-table-column>
        <el-table-column prop="externalMemberId" label="外部会员ID" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.externalMemberId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="latestOrderNo" label="最近订单" min-width="170" show-overflow-tooltip />
        <el-table-column prop="latestOrderStatus" label="最近状态" min-width="110">
          <template #default="{ row }">
            <el-tag v-if="row.latestOrderStatus" effect="light">{{ formatOrderStatus(row.latestOrderStatus) }}</el-tag>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestOrderAmount" label="最近金额" min-width="110">
          <template #default="{ row }">{{ formatMoney(row.latestOrderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="latestOrderTime" label="最近成交时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.latestOrderTime) }}</template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" min-width="90" />
        <el-table-column prop="totalOrderAmount" label="累计金额" min-width="110">
          <template #default="{ row }">{{ formatMoney(row.totalOrderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="wecomBound" label="企微绑定" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.wecomBound ? 'success' : 'info'" effect="light">
              {{ row.wecomBound ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="primaryTag" label="标签" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.primaryTag || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCustomer(row)">客户详情</el-button>
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
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMembers } from '../api/member'
import { formatDateTime, formatOrderStatus, normalize } from '../utils/format'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const sourceTabs = [
  { label: '全部会员', value: 'all' },
  { label: '分销成交', value: 'distribution' },
  { label: '抖音成交', value: 'douyin' }
]
const filters = reactive({
  sourceTab: 'all',
  name: '',
  phone: '',
  externalMemberId: ''
})
const pagination = reactive({
  page: 1,
  pageSize: 30,
  total: 0
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

function openCustomer(row) {
  if (row?.customerId) {
    router.push(`/customers/${row.customerId}`)
  }
}

function formatMoney(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

function isDistributionRow(row) {
  return ['DISTRIBUTION', 'DISTRIBUTOR'].includes(normalize(row?.source)) || ['DISTRIBUTION', 'DISTRIBUTOR'].includes(normalize(row?.sourceChannel))
}

function formatSourceDisplayName(row) {
  if (row?.sourceDisplayName && !['DISTRIBUTION', 'DISTRIBUTOR', 'DOUYIN'].includes(normalize(row.sourceDisplayName))) {
    return row.sourceDisplayName
  }
  if (isDistributionRow(row)) {
    return '分销成交'
  }
  if (normalize(row?.source) === 'DOUYIN' || normalize(row?.sourceChannel) === 'DOUYIN') {
    return '抖音成交'
  }
  return '其他成交'
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
  border: 1px solid rgba(46, 64, 87, 0.12);
  border-radius: 999px;
  background: #f7f4ec;
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
  color: #fff;
  box-shadow: 0 10px 24px rgba(23, 59, 51, 0.18);
}

.member-heading {
  align-items: center;
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
