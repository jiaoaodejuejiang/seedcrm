<template>
  <div class="stack-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>当前结算方式</span>
        <strong>{{ currentModeLabel }}</strong>
      </article>
      <article class="summary-pill">
        <span>待结金额</span>
        <strong>{{ formatMoney(balance?.unsettledAmount) }}</strong>
      </article>
      <article class="summary-pill">
        <span>可提现金额</span>
        <strong>{{ formatMoney(withdrawable) }}</strong>
      </article>
      <article class="summary-pill">
        <span>当前对象</span>
        <strong>{{ activeStaffLabel }}</strong>
      </article>
    </section>

    <section class="panel compact-panel">
      <div class="toolbar toolbar--stack">
        <div class="toolbar__filters">
          <el-select v-if="canChooseUser" v-model="selectedUserId" placeholder="请选择员工" style="width: 260px" @change="handleUserChange">
            <el-option v-for="staff in staffMembers" :key="staff.userId" :label="staffLabel(staff)" :value="staff.userId" />
          </el-select>
        </div>

        <div class="rule-strip">
          <article class="rule-strip__card">
            <span>命中规则</span>
            <strong>{{ matchedRule?.ruleName || '未命中规则' }}</strong>
            <small>{{ matchedRule?.remark || '请先在结算配置中维护规则。' }}</small>
          </article>
          <article class="rule-strip__card">
            <span>参与订单</span>
            <strong>{{ stat?.participateOrderCount || 0 }}</strong>
            <small>服务次数 {{ stat?.serviceCount || 0 }} / 已结金额 {{ formatMoney(balance?.settledAmount) }}</small>
          </article>
        </div>
      </div>
    </section>

    <section class="panel">
      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="结算台" name="settlement">
          <div class="form-grid">
            <label class="full-span">
              <span>结算时间范围</span>
              <el-date-picker
                v-model="settlementForm.range"
                type="datetimerange"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                style="width: 100%"
                :disabled="!hasSelectedUser"
              />
            </label>
          </div>

          <div class="action-group action-group--section">
            <el-button type="primary" :disabled="!hasSelectedUser" @click="handleCreateSettlement">
              {{ matchedMode === 'LEDGER_ONLY' ? '创建记账单' : '创建结算单' }}
            </el-button>
          </div>

          <el-table :data="settlementPagination.rows" stripe>
            <el-table-column label="结算单号" width="120" prop="id" />
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
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button
                    v-if="normalize(row.status) === 'INIT' || normalize(row.status) === 'PENDING'"
                    size="small"
                    @click="handleConfirmSettlement(row)"
                  >
                    {{ matchedMode === 'LEDGER_ONLY' ? '确认记账' : '确认结算' }}
                  </el-button>
                  <el-button
                    v-if="normalize(row.status) === 'CONFIRMED'"
                    size="small"
                    type="primary"
                    @click="handlePaySettlement(row)"
                  >
                    {{ matchedMode === 'LEDGER_ONLY' ? '标记已结' : '确认打款' }}
                  </el-button>
                  <el-button v-if="normalize(row.status) !== 'CONFIRMED'" size="small" plain @click="fillSettlementRange(row)">带入周期</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="withdrawTabLabel" name="withdraw">
          <template v-if="matchedMode === 'LEDGER_ONLY'">
            <el-empty description="当前角色只记账，不走提现流程" />
          </template>

          <template v-else>
            <div class="form-grid">
              <label>
                <span>{{ matchedMode === 'WITHDRAW_AUDIT' ? '发起提现金额' : '自动提现金额' }}</span>
                <el-input-number
                  v-model="withdrawForm.amount"
                  :min="0"
                  :precision="2"
                  controls-position="right"
                  :disabled="!hasSelectedUser"
                />
              </label>
            </div>

            <div class="action-group action-group--section">
              <el-button
                v-if="matchedMode === 'WITHDRAW_AUDIT'"
                type="warning"
                :disabled="!hasSelectedUser || Number(withdrawForm.amount || 0) <= 0"
                @click="handleCreateWithdraw"
              >
                发起提现
              </el-button>
              <el-button
                v-if="matchedMode === 'WITHDRAW_DIRECT'"
                type="warning"
                :disabled="!hasSelectedUser || Number(withdrawForm.amount || 0) <= 0"
                @click="handleAutoWithdraw"
              >
                自动提现
              </el-button>
            </div>
          </template>

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
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button
                    v-if="matchedMode === 'WITHDRAW_AUDIT' && normalize(row.status) === 'PENDING'"
                    size="small"
                    type="primary"
                    @click="handleApproveWithdraw(row)"
                  >
                    审核打款
                  </el-button>
                  <el-button size="small" plain @click="fillWithdrawAmount(row)">带入金额</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="单据记录" name="records">
          <el-tabs v-model="historyTab" class="platform-tabs inner-tabs">
            <el-tab-pane label="结算单" name="settlements">
              <el-table :data="settlementPagination.rows" stripe>
                <el-table-column label="结算单号" width="120" prop="id" />
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
                <el-table-column label="创建时间" min-width="170">
                  <template #default="{ row }">
                    {{ formatDateTime(row.createTime) }}
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane :label="withdrawTabLabel" name="withdraws">
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
import { currentUser } from '../utils/auth'
import { loadSystemConsoleState } from '../utils/systemConsoleStore'
import {
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatRoleCode,
  formatSettlementMode,
  normalize,
  statusTagType,
  toDateTimeString
} from '../utils/format'

const state = reactive(loadSystemConsoleState())
const staffOptions = ref([])
const selectedUserId = ref(null)
const stat = ref(null)
const balance = ref(null)
const withdrawable = ref(0)
const settlements = ref([])
const withdraws = ref([])
const activeTab = ref('settlement')
const historyTab = ref('settlements')

const settlementPagination = useTablePagination(settlements)
const withdrawPagination = useTablePagination(withdraws)

const settlementForm = reactive({ range: [] })
const withdrawForm = reactive({ amount: 0 })

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
const hasSelectedUser = computed(() => Boolean(selectedUserId.value))
const activeStaffLabel = computed(() => (selectedStaff.value ? staffLabel(selectedStaff.value) : '未选择'))
const selectedRoleCode = computed(() => selectedStaff.value?.roleCode || currentUser.value?.roleCode || '')
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
  return enabledRules.find((item) => item.scopeType === 'ROLE' && (item.roleCodes || []).includes(selectedRoleCode.value)) || null
})
const matchedMode = computed(() => matchedRule.value?.settlementMode || 'LEDGER_ONLY')
const currentModeLabel = computed(() => formatSettlementMode(matchedMode.value))
const withdrawTabLabel = computed(() => (matchedMode.value === 'LEDGER_ONLY' ? '记账记录' : '提现处理'))

function staffLabel(staff) {
  return `${staff.userName} / ${formatRoleCode(staff.roleCode)}`
}

function fillSettlementRange(row) {
  const startTime = parsePickerDate(row?.startTime)
  const endTime = parsePickerDate(row?.endTime)
  settlementForm.range = startTime && endTime ? [startTime, endTime] : []
  ElMessage.success('已带入结算周期')
}

function fillWithdrawAmount(row) {
  withdrawForm.amount = Number(row?.amount || 0)
  ElMessage.success('已带入提现金额')
}

function parsePickerDate(value) {
  if (!value) {
    return null
  }
  const parsed = new Date(String(value).replace(' ', 'T'))
  return Number.isNaN(parsed.getTime()) ? null : parsed
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

async function handleUserChange() {
  settlementForm.range = []
  withdrawForm.amount = 0
  await loadSalaryData()
}

async function loadSalaryData() {
  if (!selectedUserId.value) {
    stat.value = null
    balance.value = null
    withdrawable.value = 0
    settlements.value = []
    withdraws.value = []
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

async function handleConfirmSettlement(row) {
  await confirmSalarySettlement({
    settlementId: row.id
  })
  ElMessage.success(matchedMode.value === 'LEDGER_ONLY' ? '记账已确认' : '结算单已确认')
  await loadSalaryData()
}

async function handlePaySettlement(row) {
  await paySalarySettlement({
    settlementId: row.id
  })
  ElMessage.success(matchedMode.value === 'LEDGER_ONLY' ? '已标记记账完成' : '结算已打款')
  await loadSalaryData()
}

async function handleCreateWithdraw() {
  await createSalaryWithdraw({
    userId: selectedUserId.value,
    amount: withdrawForm.amount
  })
  ElMessage.success('提现申请已创建')
  await loadSalaryData()
}

async function handleApproveWithdraw(row) {
  await approveSalaryWithdraw({
    withdrawId: row.id,
    status: 'PAID'
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
  await initSelection()
  await loadSalaryData()
})
</script>

<style scoped>
.toolbar--stack {
  display: grid;
  gap: 14px;
}

.rule-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.rule-strip__card {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fbff;
  border: 1px solid #e5edf4;
}

.rule-strip__card span {
  color: #64748b;
  font-size: 12px;
}

.rule-strip__card strong {
  color: #0f172a;
  font-size: 18px;
}

.rule-strip__card small {
  color: #64748b;
  font-size: 12px;
}

.inner-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .rule-strip {
    grid-template-columns: 1fr;
  }
}
</style>
