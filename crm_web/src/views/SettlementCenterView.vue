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
            <strong>{{ matchedRule?.policyName || '未命中规则' }}</strong>
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
        <el-tab-pane v-if="pageMode === 'settlement'" label="薪酬结算单" name="settlement">
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

        <el-tab-pane v-if="pageMode === 'withdraw'" :label="withdrawTabLabel" name="withdraw">
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
            <el-table-column label="结算方式" min-width="140">
              <template #default="{ row }">
                {{ formatSettlementMode(row.settlementMode || matchedMode) }}
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
            <el-table-column label="审核说明" min-width="180">
              <template #default="{ row }">
                {{ row.auditRemark || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
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
                  <el-button
                    v-if="matchedMode === 'WITHDRAW_AUDIT' && normalize(row.status) === 'PENDING'"
                    size="small"
                    type="danger"
                    plain
                    @click="handleRejectWithdraw(row)"
                  >
                    驳回
                  </el-button>
                  <el-button size="small" plain @click="fillWithdrawAmount(row)">带入金额</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="pageMode === 'refunds'" label="已核销退款冲正" name="refunds">
          <div class="settlement-refund-head">
            <el-alert
              title="这里处理已核销团购券/定金退款对薪酬的冲正；门店线下确认单金额退款仍在门店订单列表登记。"
              type="warning"
              show-icon
              :closable="false"
            />
            <el-button :loading="refundOrdersLoading" @click="loadRefundableOrders">刷新订单</el-button>
          </div>

          <el-table v-loading="refundOrdersLoading" :data="refundOrderPagination.rows" stripe>
            <el-table-column label="手机号" min-width="140" prop="customerPhone" />
            <el-table-column label="姓名" min-width="120">
              <template #default="{ row }">
                {{ row.customerName || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="门店" min-width="130" prop="storeName" />
            <el-table-column label="核销金额" width="120">
              <template #default="{ row }">
                {{ formatMoney(resolveVerificationAmount(row)) }}
              </template>
            </el-table-column>
            <el-table-column label="确认单金额" width="130">
              <template #default="{ row }">
                {{ formatServiceConfirmAmount(row) }}
              </template>
            </el-table-column>
            <el-table-column label="订单状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="核销状态" width="110">
              <template #default="{ row }">
                {{ formatVerificationStatus(row.verificationStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="核销时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.verificationTime) || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="danger" plain @click="openFinanceRefund(row)">登记团购/定金退款冲正</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-pagination">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="refundOrderPagination.total"
              :current-page="refundOrderPagination.currentPage"
              :page-size="refundOrderPagination.pageSize"
              :page-sizes="refundOrderPagination.pageSizes"
              @size-change="refundOrderPagination.handleSizeChange"
              @current-change="refundOrderPagination.handleCurrentChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="financeRefundDialogVisible" title="团购/定金退款冲正" width="620px">
      <div class="refund-dialog">
        <el-alert
          title="本入口登记已核销金额退款，并同步冲正客服与分销绩效；真实抖音/支付退款由三方接口配置后对接。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div class="refund-summary">
          <span>客户：{{ financeRefundForm.order?.customerName || financeRefundForm.order?.customerPhone || '--' }}</span>
          <span>门店：{{ financeRefundForm.order?.storeName || '--' }}</span>
          <span>可退核销金额：{{ formatMoney(resolveVerificationAmount(financeRefundForm.order)) }}</span>
          <span>影响范围：客服绩效、分销绩效</span>
        </div>
        <el-form label-width="120px">
          <el-form-item label="退款对象">
            <span>
              {{ financeRefundForm.order?.customerName || financeRefundForm.order?.customerPhone || '--' }}
              / {{ financeRefundForm.order?.storeName || '--' }}
            </span>
          </el-form-item>
          <el-form-item label="退款金额">
            <el-input-number v-model="financeRefundForm.refundAmount" :min="0" :precision="2" controls-position="right" />
          </el-form-item>
          <el-form-item label="原因类型">
            <el-select v-model="financeRefundForm.reasonType" placeholder="请选择退款原因">
              <el-option label="订单退款关联扣减" value="ORDER_REFUND_REVERSE" />
              <el-option label="团购券退款" value="COUPON_REFUND" />
              <el-option label="定金退款" value="DEPOSIT_REFUND" />
              <el-option label="结算金额异常" value="SETTLEMENT_ERROR" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="退款原因">
            <el-input v-model="financeRefundForm.reason" type="textarea" :rows="3" placeholder="请填写退款原因" />
          </el-form-item>
          <el-form-item label="影响范围">
            <div class="refund-dialog__checks">
              <el-checkbox v-model="financeRefundForm.reverseCustomerService" disabled>冲正客服绩效薪资</el-checkbox>
              <el-checkbox v-model="financeRefundForm.reverseDistributor" disabled>冲正分销绩效薪资</el-checkbox>
              <small>门店线下确认单金额退款请回到【门店服务-订单列表】处理。</small>
            </div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="financeRefundDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="financeRefundSubmitting" @click="submitFinanceRefund">确认登记冲正</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  approveSalaryWithdraw,
  confirmSalarySettlement,
  createSalarySettlement,
  createSalaryWithdraw,
  fetchSettlementPolicies,
  fetchSalaryBalance,
  fetchSalarySettlements,
  fetchSalaryStat,
  fetchSalaryWithdrawable,
  fetchSalaryWithdraws,
  paySalarySettlement
} from '../api/salary'
import { refundOrder } from '../api/order'
import { fetchOrders, fetchStaffOptions } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { currentUser } from '../utils/auth'
import { loadSystemConsoleState } from '../utils/systemConsoleStore'
import {
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatRoleCode,
  formatSettlementMode,
  formatVerificationStatus,
  normalize,
  statusTagType,
  toDateTimeString
} from '../utils/format'

const route = useRoute()
const state = reactive(loadSystemConsoleState())
const staffOptions = ref([])
const selectedUserId = ref(null)
const stat = ref(null)
const balance = ref(null)
const withdrawable = ref(0)
const settlements = ref([])
const withdraws = ref([])
const policyRows = ref([])
const refundableOrders = ref([])
const activeTab = ref('settlement')
const refundOrdersLoading = ref(false)
const financeRefundDialogVisible = ref(false)
const financeRefundSubmitting = ref(false)

const settlementPagination = useTablePagination(settlements)
const withdrawPagination = useTablePagination(withdraws)
const refundOrderPagination = useTablePagination(refundableOrders)

const settlementForm = reactive({ range: [] })
const withdrawForm = reactive({ amount: 0 })
const financeRefundForm = reactive({
  order: null,
  refundAmount: null,
  reasonType: '',
  reason: '',
  reverseCustomerService: true,
  reverseDistributor: true
})

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
const activeSettlementRules = computed(() => {
  const rows = policyRows.value.length ? policyRows.value : (state.salarySettlementRules || []).map(normalizeLegacySettlementRule)
  return rows.filter((item) => {
    const enabled = item.enabled === undefined ? 1 : item.enabled
    const status = normalize(item.status || 'PUBLISHED')
    return enabled === 1 && status === 'PUBLISHED' && normalize(item.subjectType || 'INTERNAL_STAFF') === 'INTERNAL_STAFF'
  })
})
const matchedRule = computed(() => {
  return activeSettlementRules.value.find((item) => item.scopeType === 'ROLE' && (item.roleCodes || []).includes(selectedRoleCode.value)) || null
})
const matchedMode = computed(() => matchedRule.value?.settlementMode || 'LEDGER_ONLY')
const currentModeLabel = computed(() => formatSettlementMode(matchedMode.value))
const withdrawTabLabel = computed(() => (matchedMode.value === 'LEDGER_ONLY' ? '记账记录' : '提现处理'))
const pageMode = computed(() => route.meta?.settlementCenterMode || 'settlement')

function staffLabel(staff) {
  return `${staff.userName} / ${formatRoleCode(staff.roleCode)}`
}

function normalizeLegacySettlementRule(row) {
  return {
    id: row.id,
    policyName: row.ruleName,
    subjectType: row.scopeType === 'AMOUNT' ? 'DISTRIBUTOR' : 'INTERNAL_STAFF',
    scopeType: row.scopeType,
    roleCodes: row.roleCodes || [],
    amountMin: row.amountMin === '' ? null : row.amountMin,
    amountMax: row.amountMax === '' ? null : row.amountMax,
    settlementCycle: row.settlementMode === 'LEDGER_ONLY' ? 'MONTHLY' : 'INSTANT',
    settlementMode: row.settlementMode,
    enabled: row.enabled,
    status: row.enabled === 1 ? 'PUBLISHED' : 'DISABLED',
    remark: row.remark
  }
}

async function loadPolicyRows() {
  try {
    policyRows.value = await fetchSettlementPolicies()
  } catch {
    policyRows.value = []
  }
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
    const currentStaff = staffMembers.value.find((item) => item.userId === currentUserId) || null
    const defaultStaff = currentStaff && hasSettlementRuleForRole(currentStaff.roleCode)
      ? currentStaff
      : findDefaultSalaryStaff()
    selectedUserId.value = defaultStaff?.userId || currentStaff?.userId || null
  } else {
    selectedUserId.value = currentUser.value?.userId || null
  }
}

function findDefaultSalaryStaff() {
  return staffMembers.value.find((item) => hasSettlementRuleForRole(item.roleCode)) || null
}

function hasSettlementRuleForRole(roleCode) {
  const normalizedRoleCode = normalize(roleCode)
  if (!normalizedRoleCode) {
    return false
  }
  return activeSettlementRules.value.some((item) => item.scopeType === 'ROLE' && (item.roleCodes || []).includes(normalizedRoleCode))
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

async function loadRefundableOrders() {
  refundOrdersLoading.value = true
  try {
    const rows = await fetchOrders()
    refundableOrders.value = (rows || []).filter(isFinanceRefundableOrder)
    refundOrderPagination.reset()
  } catch {
    refundableOrders.value = []
  } finally {
    refundOrdersLoading.value = false
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
    subjectType: resolveWithdrawSubjectType(),
    roleCode: selectedRoleCode.value,
    amount: withdrawForm.amount
  })
  ElMessage.success('提现申请已创建')
  await loadSalaryData()
}

async function handleApproveWithdraw(row) {
  try {
    await ElMessageBox.confirm('确认通过该提现并登记为已打款吗？', '审核打款', {
      type: 'warning',
      confirmButtonText: '确认打款',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await approveSalaryWithdraw({
    withdrawId: row.id,
    status: 'PAID',
    auditRemark: '财务审核通过'
  })
  ElMessage.success('提现状态已更新')
  await loadSalaryData()
}

async function handleRejectWithdraw(row) {
  let auditRemark = ''
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因，驳回后该金额会释放为可提现余额。', '驳回提现', {
      inputType: 'textarea',
      inputPlaceholder: '例如：收款信息不完整，请补充后重新发起。',
      inputPattern: /\S+/,
      inputErrorMessage: '请填写驳回原因',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      type: 'warning'
    })
    auditRemark = result.value
  } catch {
    return
  }
  await approveSalaryWithdraw({
    withdrawId: row.id,
    status: 'REJECTED',
    auditRemark
  })
  ElMessage.success('提现已驳回')
  await loadSalaryData()
}

async function handleAutoWithdraw() {
  await createSalaryWithdraw({
    userId: selectedUserId.value,
    subjectType: resolveWithdrawSubjectType(),
    roleCode: selectedRoleCode.value,
    amount: withdrawForm.amount
  })
  ElMessage.success('自动提现已完成')
  await loadSalaryData()
}

function resolveWithdrawSubjectType() {
  return matchedRule.value?.subjectType || (matchedRule.value?.scopeType === 'AMOUNT' ? 'DISTRIBUTOR' : 'INTERNAL_STAFF')
}

function isFinanceRefundableOrder(row) {
  return normalize(row?.verificationStatus || 'UNVERIFIED') === 'VERIFIED'
    && ['PAID_DEPOSIT', 'APPOINTMENT', 'ARRIVED', 'SERVING', 'COMPLETED', 'FINISHED', 'USED'].includes(normalize(row?.status || ''))
}

function openFinanceRefund(row) {
  Object.assign(financeRefundForm, {
    order: row,
    refundAmount: resolveVerificationAmount(row),
    reasonType: '',
    reason: '',
    reverseCustomerService: true,
    reverseDistributor: true
  })
  financeRefundDialogVisible.value = true
}

async function submitFinanceRefund() {
  if (!financeRefundForm.order?.id) {
    return
  }
  if (Number(financeRefundForm.refundAmount || 0) <= 0) {
    ElMessage.warning('请填写团购券/定金退款金额')
    return
  }
  if (!financeRefundForm.reasonType) {
    ElMessage.warning('请选择退款原因类型')
    return
  }
  if (!String(financeRefundForm.reason || '').trim()) {
    ElMessage.warning('请填写退款原因')
    return
  }
  financeRefundSubmitting.value = true
  try {
    await refundOrder({
      orderId: financeRefundForm.order.id,
      refundScene: 'FINANCE_VERIFIED_PAYMENT',
      refundAmount: financeRefundForm.refundAmount,
      idempotencyKey: buildFinanceRefundIdempotencyKey(),
      outOrderNo: financeRefundForm.order.orderNo,
      itemOrderId: financeRefundForm.order.verificationCode,
      refundReasonType: financeRefundForm.reasonType,
      refundReason: financeRefundForm.reason,
      reverseCustomerService: financeRefundForm.reverseCustomerService,
      reverseDistributor: financeRefundForm.reverseDistributor,
      reverseSalary: false,
      reverseStorePerformance: false,
      remark: buildFinanceRefundRemark()
    })
    financeRefundDialogVisible.value = false
    ElMessage.success('已登记团购/定金退款冲正')
    await Promise.all([loadSalaryData(), loadRefundableOrders()])
  } finally {
    financeRefundSubmitting.value = false
  }
}

function buildFinanceRefundRemark() {
  return [
    `财务已核销金额退款：${financeRefundReasonTypeLabel(financeRefundForm.reasonType)} / ${financeRefundForm.reason}`,
    '冲正客服绩效薪资',
    '冲正分销绩效薪资'
  ].join('；')
}

function buildFinanceRefundIdempotencyKey() {
  return [
    'FINANCE_REFUND',
    financeRefundForm.order?.id || '',
    Number(financeRefundForm.refundAmount || 0).toFixed(2),
    financeRefundForm.reasonType || '',
    String(financeRefundForm.reason || '').trim()
  ].join(':')
}

function financeRefundReasonTypeLabel(value) {
  return (
    {
      ORDER_REFUND_REVERSE: '订单退款关联扣减',
      COUPON_REFUND: '团购券退款',
      DEPOSIT_REFUND: '定金退款',
      SETTLEMENT_ERROR: '结算金额异常',
      OTHER: '其他'
    }[value] || '未选择'
  )
}

function parseServiceDetail(row) {
  if (!row?.serviceDetailJson) {
    return null
  }
  try {
    return JSON.parse(row.serviceDetailJson)
  } catch {
    return null
  }
}

function toAmount(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

function resolveVerificationAmount(row) {
  const deposit = toAmount(row?.deposit)
  if (deposit && deposit > 0) {
    return deposit
  }
  return toAmount(row?.amount) || 0
}

function resolveServiceConfirmAmount(row) {
  const parsed = parseServiceDetail(row)
  const amount = toAmount(parsed?.serviceConfirmAmount)
  return amount && amount > 0 ? amount : null
}

function formatServiceConfirmAmount(row) {
  const amount = resolveServiceConfirmAmount(row)
  return amount ? formatMoney(amount) : '待填写'
}

onMounted(async () => {
  activeTab.value = pageMode.value
  await loadPolicyRows()
  await initSelection()
  await loadSalaryData()
  if (pageMode.value === 'refunds') {
    await loadRefundableOrders()
  }
})

watch(
  () => pageMode.value,
  async (mode) => {
    activeTab.value = mode
    if (mode === 'refunds') {
      await loadRefundableOrders()
    }
  }
)
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

.settlement-refund-head,
.refund-dialog {
  display: grid;
  gap: 14px;
  margin-bottom: 16px;
}

.refund-dialog {
  margin-bottom: 0;
}

.refund-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f8fbff;
  border: 1px solid #e5edf4;
  color: #334155;
  font-size: 13px;
}

.refund-dialog__checks {
  display: grid;
  gap: 6px;
}

.refund-dialog__checks small {
  color: #94a3b8;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .rule-strip {
    grid-template-columns: 1fr;
  }

  .refund-summary {
    grid-template-columns: 1fr;
  }
}
</style>
