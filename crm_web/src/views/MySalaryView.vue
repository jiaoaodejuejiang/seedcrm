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
          <el-select v-if="canChooseUser" v-model="selectedUserId" placeholder="请选择员工" style="width: 260px" @change="loadSalaryView">
            <el-option v-for="staff in staffMembers" :key="staff.userId" :label="staffLabel(staff)" :value="staff.userId" />
          </el-select>
        </div>
      </div>

      <p v-if="canChooseUser" class="table-note">
        {{ selectedStaff ? `当前查看：${staffLabel(selectedStaff)}` : '请先选择员工，再查看薪酬明细。' }}
      </p>

      <el-table v-if="!canChooseUser || selectedUserId" :data="dailyRows" stripe>
        <el-table-column label="日期" min-width="160" prop="date" />
        <el-table-column label="收入笔数" width="120" prop="count" />
        <el-table-column label="订单金额" width="140">
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
      <el-empty v-else description="请先选择员工后查看薪酬明细" />
    </section>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="880px">
      <el-table :data="detailRows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="订单金额" width="140">
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
import { currentUser } from '../utils/auth'
import { formatDateTime, formatMoney, formatRoleCode } from '../utils/format'

const staffOptions = ref([])
const salaryDetails = ref([])
const balance = ref(null)
const withdrawable = ref(0)
const selectedUserId = ref(null)
const detailVisible = ref(false)
const detailTitle = ref('收入明细')
const detailRows = ref([])

const canChooseUser = computed(() => ['ADMIN', 'FINANCE'].includes(String(currentUser.value?.roleCode || '').toUpperCase()))
const staffMembers = computed(() =>
  staffOptions.value.flatMap((role) =>
    (role.staffOptions || []).map((staff) => ({
      ...staff,
      roleCode: role.roleCode || staff.roleCode,
      roleName: role.roleName
    }))
  )
)
const selectedStaff = computed(() => staffMembers.value.find((item) => item.userId === selectedUserId.value) || null)
const todayIncome = computed(() => {
  const today = new Date().toLocaleDateString('sv-SE')
  return salaryDetails.value
    .filter((item) => String(item.createTime || '').startsWith(today))
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
})
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

async function initSelection() {
  try {
    staffOptions.value = await fetchStaffOptions()
  } catch {
    staffOptions.value = []
  }
  if (canChooseUser.value) {
    const currentUserId = currentUser.value?.userId || null
    selectedUserId.value = staffMembers.value.some((item) => item.userId === currentUserId) ? currentUserId : null
  } else {
    selectedUserId.value = currentUser.value?.userId || null
  }
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
