<template>
  <div class="stack-page">
    <section class="panel">
      <div class="toolbar">
        <div>
          <h3>薪酬看板</h3>
          <p class="text-secondary">薪酬统一基于 order_role_record 计算，不允许写死业务规则。</p>
        </div>
        <div class="toolbar__filters">
          <el-select v-model="selectedUserId" placeholder="选择员工" style="width: 240px" @change="loadSalaryData">
            <el-option v-for="staff in staffMembers" :key="staff.userId" :label="staffLabel(staff)" :value="staff.userId" />
          </el-select>
          <el-button @click="loadSalaryData">刷新</el-button>
        </div>
      </div>

      <div class="metrics-row">
        <article class="metric-card">
          <span>参与订单</span>
          <strong>{{ stat?.participateOrderCount || 0 }}</strong>
          <small>该员工通过角色记录参与过的订单数量。</small>
        </article>
        <article class="metric-card">
          <span>服务次数</span>
          <strong>{{ stat?.serviceCount || 0 }}</strong>
          <small>该员工累计完成的服务次数。</small>
        </article>
        <article class="metric-card">
          <span>可提现</span>
          <strong>{{ formatMoney(withdrawable) }}</strong>
          <small>当前可进入提现流程的金额。</small>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="detail-grid">
        <article class="detail-card">
          <h3>余额</h3>
          <p>未结算：{{ formatMoney(balance?.unsettledAmount) }}</p>
          <p>已结算：{{ formatMoney(balance?.settledAmount) }}</p>
          <p>已提现：{{ formatMoney(balance?.withdrawnAmount) }}</p>
          <p>可提现：{{ formatMoney(balance?.withdrawableAmount) }}</p>
        </article>
        <article class="detail-card">
          <h3>角色分布</h3>
          <div class="binding-list">
            <div v-for="item in roleDistributionRows" :key="item.roleCode" class="binding-item">
              <strong>{{ formatRoleCode(item.roleCode) }}</strong>
              <span>{{ item.count }} 条</span>
            </div>
            <p v-if="!roleDistributionRows.length" class="text-secondary">暂无角色分布数据</p>
          </div>
        </article>
        <article class="detail-card">
          <h3>重算薪酬</h3>
          <el-input-number v-model="recalculateForm.planOrderId" :min="1" controls-position="right" style="width: 100%" />
          <div class="table-note">可针对某个服务单触发重算，验证角色记录与薪酬链路是否一致。</div>
          <el-button type="primary" @click="handleRecalculate">执行重算</el-button>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>结算与提现</h3>
          <p>在同一页面内完成结算创建、确认、打款与提现审批。</p>
        </div>
      </div>

      <div class="form-grid">
        <label class="full-span">
          <span>结算时间范围</span>
          <el-date-picker
            v-model="settlementForm.range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </label>
        <label>
          <span>结算单 ID</span>
          <el-input-number v-model="settlementStatusForm.settlementId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>提现金额</span>
          <el-input-number v-model="withdrawForm.amount" :min="0" :precision="2" controls-position="right" />
        </label>
        <label>
          <span>提现单 ID</span>
          <el-input-number v-model="withdrawApproveForm.withdrawId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>提现状态</span>
          <el-select v-model="withdrawApproveForm.status">
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已打款" value="PAID" />
          </el-select>
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="handleCreateSettlement">创建结算</el-button>
        <el-button @click="handleConfirmSettlement">确认结算</el-button>
        <el-button @click="handlePaySettlement">结算打款</el-button>
        <el-button type="warning" @click="handleCreateWithdraw">发起提现</el-button>
        <el-button type="success" @click="handleApproveWithdraw">更新提现状态</el-button>
      </div>

      <div v-if="lastResult" class="result-card">
        <strong>最近一次结果</strong>
        <pre class="code-block">{{ JSON.stringify(lastResult, null, 2) }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveSalaryWithdraw,
  confirmSalarySettlement,
  createSalarySettlement,
  createSalaryWithdraw,
  fetchSalaryBalance,
  fetchSalaryStat,
  fetchSalaryWithdrawable,
  paySalarySettlement,
  recalculateSalary
} from '../api/salary'
import { fetchStaffOptions } from '../api/workbench'
import { formatMoney, formatRoleCode, toDateTimeString } from '../utils/format'

const staffOptions = ref([])
const selectedUserId = ref(1001)
const stat = ref(null)
const balance = ref(null)
const withdrawable = ref(0)
const lastResult = ref(null)

const recalculateForm = reactive({
  planOrderId: null
})

const settlementForm = reactive({
  range: []
})

const settlementStatusForm = reactive({
  settlementId: null
})

const withdrawForm = reactive({
  amount: 0
})

const withdrawApproveForm = reactive({
  withdrawId: null,
  status: 'APPROVED'
})

const staffMembers = computed(() => {
  return staffOptions.value.flatMap((role) =>
    (role.staffOptions || []).map((staff) => ({
      ...staff,
      roleCode: role.roleCode || staff.roleCode,
      roleName: role.roleName
    }))
  )
})

const roleDistributionRows = computed(() => {
  const distribution = stat.value?.roleDistribution || {}
  return Object.entries(distribution).map(([roleCode, count]) => ({
    roleCode,
    count
  }))
})

function staffLabel(staff) {
  return `${staff.userName} / ${formatRoleCode(staff.roleCode) || staff.roleName || '--'}`
}

async function loadStaff() {
  try {
    staffOptions.value = await fetchStaffOptions()
  } catch {
    staffOptions.value = []
  }
}

async function loadSalaryData() {
  if (!selectedUserId.value) {
    return
  }
  try {
    const [statResponse, balanceResponse, withdrawableResponse] = await Promise.all([
      fetchSalaryStat(selectedUserId.value),
      fetchSalaryBalance(selectedUserId.value),
      fetchSalaryWithdrawable(selectedUserId.value)
    ])
    stat.value = statResponse
    balance.value = balanceResponse
    withdrawable.value = withdrawableResponse
  } catch {
    stat.value = null
    balance.value = null
    withdrawable.value = 0
  }
}

async function handleRecalculate() {
  try {
    lastResult.value = await recalculateSalary({
      planOrderId: recalculateForm.planOrderId
    })
    ElMessage.success('薪酬重算完成')
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleCreateSettlement() {
  try {
    lastResult.value = await createSalarySettlement({
      userId: selectedUserId.value,
      startTime: toDateTimeString(settlementForm.range?.[0]),
      endTime: toDateTimeString(settlementForm.range?.[1])
    })
    settlementStatusForm.settlementId = lastResult.value?.id || settlementStatusForm.settlementId
    ElMessage.success('结算单已创建')
    await loadSalaryData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleConfirmSettlement() {
  try {
    lastResult.value = await confirmSalarySettlement({
      settlementId: settlementStatusForm.settlementId
    })
    ElMessage.success('结算单已确认')
    await loadSalaryData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handlePaySettlement() {
  try {
    lastResult.value = await paySalarySettlement({
      settlementId: settlementStatusForm.settlementId
    })
    ElMessage.success('结算单已打款')
    await loadSalaryData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleCreateWithdraw() {
  try {
    lastResult.value = await createSalaryWithdraw({
      userId: selectedUserId.value,
      amount: withdrawForm.amount
    })
    withdrawApproveForm.withdrawId = lastResult.value?.id || withdrawApproveForm.withdrawId
    ElMessage.success('提现申请已创建')
    await loadSalaryData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleApproveWithdraw() {
  try {
    lastResult.value = await approveSalaryWithdraw({
      withdrawId: withdrawApproveForm.withdrawId,
      status: withdrawApproveForm.status
    })
    ElMessage.success('提现状态已更新')
    await loadSalaryData()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

onMounted(async () => {
  await loadStaff()
  await loadSalaryData()
})
</script>
