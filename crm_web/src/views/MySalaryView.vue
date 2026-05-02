<template>
  <div class="stack-page">
    <section class="summary-strip">
      <article class="summary-pill">
        <span>今日收入</span>
        <strong>{{ formatMoney(todayIncome) }}</strong>
      </article>
      <article class="summary-pill">
        <span>待结金额</span>
        <strong>{{ formatMoney(balance?.unsettledAmount) }}</strong>
      </article>
      <article class="summary-pill">
        <span>已结金额</span>
        <strong>{{ formatMoney(balance?.settledAmount) }}</strong>
      </article>
      <article class="summary-pill">
        <span>可提现金额</span>
        <strong>{{ formatMoney(withdrawable) }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-tabs
            v-if="salaryScopeTabs.length > 1"
            v-model="activeSalaryScope"
            class="salary-scope-tabs"
            @tab-change="handleScopeChange"
          >
            <el-tab-pane v-for="tab in salaryScopeTabs" :key="tab.name" :label="tab.label" :name="tab.name" />
          </el-tabs>
          <el-select
            v-if="showStaffSelect"
            v-model="selectedUserId"
            :placeholder="staffSelectPlaceholder"
            style="width: 260px"
            @change="loadSalaryView"
          >
            <el-option v-for="staff in selectableStaffMembers" :key="staff.userId" :label="staffLabel(staff)" :value="staff.userId" />
          </el-select>
        </div>
      </div>

      <p class="table-note">{{ salaryScopeNote }}</p>

      <el-table v-if="canDisplayTable" :data="dailyRows" stripe>
        <el-table-column label="日期" min-width="160" prop="date" />
        <el-table-column label="收入笔数" width="120" prop="count" />
        <el-table-column v-if="canViewOrderAmounts" label="订单金额" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.orderAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="薪酬收入" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="角色分布" min-width="220">
          <template #default="{ row }">
            {{ row.roleLabels.join(' / ') || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="emptyDescription" />
    </section>

    <section v-if="deductionRows.length" class="panel compact-panel">
      <div class="panel-heading">
        <div>
          <h3>扣减记录</h3>
        </div>
      </div>
      <el-table :data="deductionRows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag type="warning">{{ detailTypeLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="扣减金额" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="关联退款" width="140">
          <template #default="{ row }">
            {{ row.refundRecordId ? `#${row.refundRecordId}` : '--' }}
          </template>
        </el-table-column>
        <el-table-column label="服务单 ID" width="120" prop="planOrderId" />
      </el-table>
    </section>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="880px">
      <el-table :data="detailRows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.adjustmentType ? 'warning' : 'success'">{{ detailTypeLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column v-if="canViewOrderAmounts" label="订单金额" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.orderAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="薪酬金额" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="服务单 ID" width="120" prop="planOrderId" />
        <el-table-column label="结算单" width="120">
          <template #default="{ row }">
            {{ row.settlementId || '--' }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchSalaryBalance, fetchSalaryDetails, fetchSalaryWithdrawable } from '../api/salary'
import { fetchStaffOptions } from '../api/workbench'
import { canViewBusinessAmounts, currentUser } from '../utils/auth'
import { formatDateTime, formatMoney, formatRoleCode } from '../utils/format'

const staffOptions = ref([])
const salaryDetails = ref([])
const balance = ref(null)
const withdrawable = ref(0)
const selectedUserId = ref(null)
const activeSalaryScope = ref('self')
const detailVisible = ref(false)
const detailTitle = ref('收入明细')
const detailRows = ref([])

const roleCode = computed(() => String(currentUser.value?.roleCode || '').toUpperCase())
const isFinanceOperator = computed(() => ['ADMIN', 'FINANCE'].includes(roleCode.value))
const canViewOrderAmounts = computed(() => canViewBusinessAmounts())
const canViewTeamSalary = computed(() => ['CLUE_MANAGER', 'STORE_MANAGER'].includes(roleCode.value))
const salaryScopeTabs = computed(() => {
  if (isFinanceOperator.value) {
    return [
      { name: 'self', label: '我的薪酬' },
      { name: 'all', label: '全部员工薪酬' }
    ]
  }
  if (canViewTeamSalary.value) {
    return [
      { name: 'self', label: '我的薪酬' },
      { name: 'team', label: '部门薪酬' }
    ]
  }
  return []
})
const staffMembers = computed(() =>
  staffOptions.value.flatMap((role) =>
    (role.staffOptions || []).map((staff) => ({
      ...staff,
      roleCode: role.roleCode || staff.roleCode,
      roleName: role.roleName
    }))
  )
)
const teamMemberIdSet = computed(
  () => new Set((currentUser.value?.teamMemberIds || []).map((item) => Number(item)).filter((item) => Number.isFinite(item)))
)
const selectableStaffMembers = computed(() => {
  const currentUserId = Number(currentUser.value?.userId || 0)
  if (activeSalaryScope.value === 'all' && isFinanceOperator.value) {
    return staffMembers.value
  }
  if (activeSalaryScope.value === 'team' && canViewTeamSalary.value) {
    return staffMembers.value.filter((staff) => {
      const userId = Number(staff.userId || 0)
      return userId !== currentUserId && teamMemberIdSet.value.has(userId)
    })
  }
  return []
})
const selectedStaff = computed(() => staffMembers.value.find((item) => item.userId === selectedUserId.value) || null)
const showStaffSelect = computed(() => ['all', 'team'].includes(activeSalaryScope.value))
const canDisplayTable = computed(() => activeSalaryScope.value === 'self' || Boolean(selectedUserId.value))
const staffSelectPlaceholder = computed(() => (activeSalaryScope.value === 'team' ? '请选择部门员工' : '请选择员工'))
const salaryScopeNote = computed(() => {
  if (activeSalaryScope.value === 'all') {
    return selectedStaff.value ? `当前查看：${staffLabel(selectedStaff.value)}` : '请选择员工后查看薪酬明细。'
  }
  if (activeSalaryScope.value === 'team') {
    return selectedStaff.value ? `当前查看部门成员：${staffLabel(selectedStaff.value)}` : '当前部门暂无可查看的薪酬人员。'
  }
  return '当前页面仅展示与你本人相关的薪酬数据。'
})
const emptyDescription = computed(() => {
  if (activeSalaryScope.value === 'team') {
    return '当前部门暂无可查看的薪酬记录'
  }
  if (activeSalaryScope.value === 'all') {
    return '请先选择员工后查看薪酬明细'
  }
  return '当前暂无你的薪酬记录'
})
const todayIncome = computed(() => {
  const today = new Date().toLocaleDateString('sv-SE')
  return salaryDetails.value
    .filter((item) => String(item.createTime || '').startsWith(today))
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
})
const deductionRows = computed(() =>
  salaryDetails.value
    .filter((item) => Number(item.amount || 0) < 0 || item.adjustmentType)
    .sort((left, right) => String(right.createTime || '').localeCompare(String(left.createTime || '')))
)
const dailyRows = computed(() => {
  const grouped = new Map()
  for (const detail of salaryDetails.value) {
    const date = String(detail.createTime || '').slice(0, 10) || '--'
    if (!grouped.has(date)) {
      grouped.set(date, {
        date,
        count: 0,
        orderAmount: 0,
        amount: 0,
        roleLabels: [],
        details: []
      })
    }
    const bucket = grouped.get(date)
    bucket.count += 1
    bucket.orderAmount += Number(detail.orderAmount || 0)
    bucket.amount += Number(detail.amount || 0)
    bucket.roleLabels = Array.from(new Set([...bucket.roleLabels, formatRoleCode(detail.roleCode)]))
    bucket.details.push(detail)
  }
  return Array.from(grouped.values()).sort((left, right) => String(right.date).localeCompare(String(left.date)))
})

function staffLabel(staff) {
  return `${staff.userName} / ${formatRoleCode(staff.roleCode)}`
}

function openDetail(row) {
  detailTitle.value = `${row.date} 收入明细`
  detailRows.value = row.details || []
  detailVisible.value = true
}

function detailTypeLabel(row) {
  return row?.adjustmentType === 'REFUND_REVERSAL' ? '退款冲正' : '佣金收入'
}

function applyDefaultSelection() {
  if (activeSalaryScope.value === 'self') {
    selectedUserId.value = currentUser.value?.userId || null
    return
  }
  const currentSelection = selectableStaffMembers.value.find((staff) => staff.userId === selectedUserId.value)
  selectedUserId.value = currentSelection?.userId || selectableStaffMembers.value[0]?.userId || null
}

async function handleScopeChange() {
  applyDefaultSelection()
  await loadSalaryView()
}

async function initSelection() {
  try {
    staffOptions.value = await fetchStaffOptions()
  } catch {
    staffOptions.value = []
  }
  applyDefaultSelection()
}

async function loadSalaryView() {
  if (!selectedUserId.value) {
    salaryDetails.value = []
    balance.value = null
    withdrawable.value = 0
    return
  }
  try {
    const [details, balancePayload, withdrawablePayload] = await Promise.all([
      fetchSalaryDetails(selectedUserId.value),
      fetchSalaryBalance(selectedUserId.value),
      fetchSalaryWithdrawable(selectedUserId.value)
    ])
    salaryDetails.value = details || []
    balance.value = balancePayload
    withdrawable.value = Number(withdrawablePayload || 0)
  } catch {
    salaryDetails.value = []
    balance.value = null
    withdrawable.value = 0
  }
}

onMounted(async () => {
  await initSelection()
  await loadSalaryView()
})
</script>
