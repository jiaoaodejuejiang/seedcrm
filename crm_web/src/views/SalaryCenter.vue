<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>当前结算方式</span>
        <strong>{{ currentModeLabel }}</strong>
      </article>
      <article class="metric-card">
        <span>可提现金额</span>
        <strong>{{ formatMoney(withdrawable) }}</strong>
      </article>
      <article class="metric-card">
        <span>单据总数</span>
        <strong>{{ settlements.length + withdraws.length }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>结算规则卡</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>规则名称</span>
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </label>
        <label>
          <span>匹配方式</span>
          <el-select v-model="ruleForm.scopeType">
            <el-option label="按角色" value="ROLE" />
            <el-option label="按金额" value="AMOUNT" />
          </el-select>
        </label>
        <label v-if="ruleForm.scopeType === 'ROLE'" class="full-span">
          <span>薪酬角色</span>
          <el-select v-model="ruleForm.roleCodes" multiple placeholder="请选择薪酬角色">
            <el-option v-for="item in state.salaryRoles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <template v-else>
          <label>
            <span>最低金额</span>
            <el-input-number v-model="ruleForm.amountMin" :min="0" :precision="2" controls-position="right" />
          </label>
          <label>
            <span>最高金额</span>
            <el-input v-model="ruleForm.amountMax" placeholder="留空表示不上限" />
          </label>
        </template>
        <label>
          <span>结算方式</span>
          <el-select v-model="ruleForm.settlementMode">
            <el-option label="提现审核" value="WITHDRAW_AUDIT" />
            <el-option label="提现不审核" value="WITHDRAW_DIRECT" />
            <el-option label="只记账" value="LEDGER_ONLY" />
          </el-select>
        </label>
        <label class="full-span">
          <span>说明</span>
          <el-input v-model="ruleForm.remark" placeholder="请输入规则说明" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveRule">保存规则</el-button>
        <el-button @click="resetRuleForm">重置</el-button>
      </div>

      <el-table :data="rulePagination.rows" stripe>
        <el-table-column label="规则" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.ruleName }}</strong>
              <span>{{ row.scopeType === 'ROLE' ? '按角色' : '按金额' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="匹配条件" min-width="220">
          <template #default="{ row }">
            <span v-if="row.scopeType === 'ROLE'">
              {{ (row.roleCodes || []).map(formatRoleCode).join(' / ') || '--' }}
            </span>
            <span v-else>
              {{ formatMoney(row.amountMin || 0) }} ~ {{ row.amountMax === '' ? '不限' : formatMoney(row.amountMax || 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="方式" width="120">
          <template #default="{ row }">
            {{ formatSettlementMode(row.settlementMode) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickRule(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleRule(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>员工结算台</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>员工</span>
          <el-select v-model="selectedUserId" placeholder="请选择员工" @change="loadSalaryData">
            <el-option v-for="staff in staffMembers" :key="staff.userId" :label="staffLabel(staff)" :value="staff.userId" />
          </el-select>
        </label>
        <label>
          <span>规则预览角色</span>
          <el-select v-model="previewRoleCode">
            <el-option v-for="item in state.salaryRoles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <label>
          <span>参与订单</span>
          <el-input :model-value="String(stat?.participateOrderCount || 0)" readonly />
        </label>
        <label>
          <span>服务次数</span>
          <el-input :model-value="String(stat?.serviceCount || 0)" readonly />
        </label>
        <label>
          <span>未结金额</span>
          <el-input :model-value="formatMoney(balance?.unsettledAmount)" readonly />
        </label>
        <label>
          <span>已结金额</span>
          <el-input :model-value="formatMoney(balance?.settledAmount)" readonly />
        </label>
      </div>

      <div class="detail-grid">
        <article class="detail-card">
          <h3>当前命中规则</h3>
          <p>规则：{{ matchedRule?.ruleName || '未命中规则' }}</p>
          <p>方式：{{ currentModeLabel }}</p>
          <p>说明：{{ matchedRule?.remark || '--' }}</p>
        </article>
        <article class="detail-card">
          <h3>下一动作</h3>
          <p>{{ nextActionText }}</p>
        </article>
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
        <label v-if="matchedMode !== 'LEDGER_ONLY'">
          <span>提现单 ID</span>
          <el-input-number v-model="withdrawApproveForm.withdrawId" :min="1" controls-position="right" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="handleCreateSettlement">{{ matchedMode === 'LEDGER_ONLY' ? '创建记账单' : '创建结算单' }}</el-button>
        <el-button @click="handleConfirmSettlement">{{ matchedMode === 'LEDGER_ONLY' ? '确认记账' : '确认结算' }}</el-button>
        <el-button @click="handlePaySettlement">{{ matchedMode === 'LEDGER_ONLY' ? '标记已结' : '确认打款' }}</el-button>
        <el-button v-if="matchedMode === 'WITHDRAW_AUDIT'" type="warning" @click="handleCreateWithdraw">发起提现</el-button>
        <el-button v-if="matchedMode === 'WITHDRAW_AUDIT'" type="success" @click="handleApproveWithdraw">审核 / 打款</el-button>
        <el-button v-if="matchedMode === 'WITHDRAW_DIRECT'" type="warning" @click="handleAutoWithdraw">自动提现</el-button>
      </div>
    </section>

    <section class="panel">
      <el-tabs v-model="activeHistoryTab" class="platform-tabs">
        <el-tab-pane label="结算单" name="settlements">
          <el-table :data="settlementPagination.rows" stripe>
            <el-table-column label="结算单" width="120" prop="id" />
            <el-table-column label="金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.totalAmount) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="周期" min-width="240">
              <template #default="{ row }">
                {{ formatDateTime(row.startTime) }} 至 {{ formatDateTime(row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="matchedMode === 'LEDGER_ONLY' ? '记账记录' : '提现单'" name="withdraws">
          <el-table :data="withdrawPagination.rows" stripe>
            <el-table-column label="单号" width="120" prop="id" />
            <el-table-column label="金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.amount) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
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
  fetchSalarySettlements,
  fetchSalaryStat,
  fetchSalaryWithdrawable,
  fetchSalaryWithdraws,
  paySalarySettlement
} from '../api/salary'
import { fetchStaffOptions } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'
import {
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatRoleCode,
  formatSettlementMode,
  statusTagType,
  toDateTimeString
} from '../utils/format'

const state = reactive(loadSystemConsoleState())
const staffOptions = ref([])
const selectedUserId = ref(1001)
const previewRoleCode = ref(state.salaryRoles?.[0]?.roleCode || '')
const stat = ref(null)
const balance = ref(null)
const withdrawable = ref(0)
const settlements = ref([])
const withdraws = ref([])
const activeHistoryTab = ref('settlements')

const rulePagination = useTablePagination(computed(() => state.salarySettlementRules || []))
const settlementPagination = useTablePagination(settlements)
const withdrawPagination = useTablePagination(withdraws)

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
  status: 'PAID'
})
const ruleForm = reactive(createRuleForm())

const staffMembers = computed(() =>
  staffOptions.value.flatMap((role) =>
    (role.staffOptions || []).map((staff) => ({
      ...staff,
      roleCode: role.roleCode || staff.roleCode,
      roleName: role.roleName
    }))
  )
)

const matchedRule = computed(() => {
  const enabledRules = (state.salarySettlementRules || []).filter((item) => item.enabled === 1)
  const amountMatch = enabledRules.find((item) => {
    if (item.scopeType !== 'AMOUNT') {
      return false
    }
    const min = Number(item.amountMin || 0)
    const max = item.amountMax === '' ? Number.POSITIVE_INFINITY : Number(item.amountMax || 0)
    return withdrawable.value >= min && withdrawable.value <= max
  })
  if (amountMatch) {
    return amountMatch
  }
  return enabledRules.find((item) => item.scopeType === 'ROLE' && (item.roleCodes || []).includes(previewRoleCode.value)) || null
})
const matchedMode = computed(() => matchedRule.value?.settlementMode || 'LEDGER_ONLY')
const currentModeLabel = computed(() => formatSettlementMode(matchedMode.value))
const nextActionText = computed(() => {
  if (matchedMode.value === 'WITHDRAW_AUDIT') {
    return '先创建结算单，再发起提现并走审核 / 打款。'
  }
  if (matchedMode.value === 'WITHDRAW_DIRECT') {
    return '先创建结算单，再自动发起提现并完成打款。'
  }
  return '当前仅记账，不走提现。'
})

function createRuleForm() {
  return {
    id: null,
    ruleName: '',
    scopeType: 'ROLE',
    roleCodes: [],
    amountMin: 0,
    amountMax: '',
    settlementMode: 'WITHDRAW_AUDIT',
    remark: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetRuleForm() {
  Object.assign(ruleForm, createRuleForm())
}

function pickRule(row) {
  Object.assign(ruleForm, {
    ...row,
    roleCodes: [...(row.roleCodes || [])]
  })
}

function toggleRule(row) {
  replaceState({
    ...state,
    salarySettlementRules: state.salarySettlementRules.map((item) =>
      item.id === row.id ? { ...item, enabled: item.enabled === 1 ? 0 : 1 } : item
    )
  })
}

function saveRule() {
  if (!ruleForm.ruleName) {
    ElMessage.warning('请先填写规则名称')
    return
  }
  if (ruleForm.scopeType === 'ROLE' && !(ruleForm.roleCodes || []).length) {
    ElMessage.warning('请选择至少一个薪酬角色')
    return
  }
  const items = [...state.salarySettlementRules]
  const nextRow = {
    ...ruleForm,
    id: ruleForm.id || nextSystemId(items),
    enabled: ruleForm.id ? (items.find((item) => item.id === ruleForm.id)?.enabled ?? 1) : 1
  }
  if (ruleForm.id) {
    items.splice(
      items.findIndex((item) => item.id === ruleForm.id),
      1,
      nextRow
    )
  } else {
    items.push(nextRow)
  }
  replaceState({ ...state, salarySettlementRules: items })
  rulePagination.reset()
  resetRuleForm()
  ElMessage.success('结算规则已保存')
}

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
    const [statResponse, balanceResponse, withdrawableResponse, settlementRows, withdrawRows] = await Promise.all([
      fetchSalaryStat(selectedUserId.value),
      fetchSalaryBalance(selectedUserId.value),
      fetchSalaryWithdrawable(selectedUserId.value),
      fetchSalarySettlements(selectedUserId.value),
      fetchSalaryWithdraws(selectedUserId.value)
    ])
    stat.value = statResponse
    balance.value = balanceResponse
    withdrawable.value = Number(withdrawableResponse || 0)
    settlements.value = settlementRows || []
    withdraws.value = withdrawRows || []
    settlementPagination.reset()
    withdrawPagination.reset()
  } catch {
    stat.value = null
    balance.value = null
    withdrawable.value = 0
    settlements.value = []
    withdraws.value = []
  }
}

async function handleCreateSettlement() {
  await createSalarySettlement({
    userId: selectedUserId.value,
    startTime: toDateTimeString(settlementForm.range?.[0]),
    endTime: toDateTimeString(settlementForm.range?.[1])
  })
  ElMessage.success(matchedMode.value === 'LEDGER_ONLY' ? '记账单已创建' : '结算单已创建')
  await loadSalaryData()
}

async function handleConfirmSettlement() {
  await confirmSalarySettlement({
    settlementId: settlementStatusForm.settlementId
  })
  ElMessage.success(matchedMode.value === 'LEDGER_ONLY' ? '记账已确认' : '结算单已确认')
  await loadSalaryData()
}

async function handlePaySettlement() {
  await paySalarySettlement({
    settlementId: settlementStatusForm.settlementId
  })
  ElMessage.success(matchedMode.value === 'LEDGER_ONLY' ? '已标记记账完成' : '结算已打款')
  await loadSalaryData()
}

async function handleCreateWithdraw() {
  const created = await createSalaryWithdraw({
    userId: selectedUserId.value,
    amount: withdrawForm.amount
  })
  withdrawApproveForm.withdrawId = created?.id || withdrawApproveForm.withdrawId
  ElMessage.success('提现申请已创建')
  await loadSalaryData()
}

async function handleApproveWithdraw() {
  await approveSalaryWithdraw({
    withdrawId: withdrawApproveForm.withdrawId,
    status: withdrawApproveForm.status
  })
  ElMessage.success('提现状态已更新')
  await loadSalaryData()
}

async function handleAutoWithdraw() {
  const created = await createSalaryWithdraw({
    userId: selectedUserId.value,
    amount: withdrawForm.amount
  })
  await approveSalaryWithdraw({
    withdrawId: created?.id,
    status: 'PAID'
  })
  ElMessage.success('自动提现已完成')
  await loadSalaryData()
}

onMounted(async () => {
  await loadStaff()
  await loadSalaryData()
})
</script>
