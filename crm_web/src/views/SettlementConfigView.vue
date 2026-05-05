<template>
  <div class="stack-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>已发布</span>
        <strong>{{ publishedCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>草稿</span>
        <strong>{{ draftCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>已停用</span>
        <strong>{{ disabledCount }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>结算与线下处理规则</h3>
          <p>配置只决定系统台账、审核和线下处理登记方式，不发起真实收款、退款、提现或转账。</p>
        </div>
        <div class="action-group">
          <el-button @click="loadPolicies">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">新增规则</el-button>
        </div>
      </div>

      <div class="finance-boundary-grid">
        <article v-for="item in financeBoundaryCards" :key="item.title" class="finance-boundary-card">
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </article>
      </div>

      <el-tabs v-model="activeRuleTab" class="platform-tabs" @tab-change="pagination.reset">
        <el-tab-pane label="结算规则" name="settlement" />
        <el-tab-pane label="线下结清规则" name="withdraw" />
      </el-tabs>

      <el-table v-loading="loading" :data="pagination.rows" stripe>
        <el-table-column label="规则名称" min-width="190">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.policyName }}</strong>
              <span>{{ row.remark || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="120">
          <template #default="{ row }">
            {{ formatSubjectType(row.subjectType) }}
          </template>
        </el-table-column>
        <el-table-column label="匹配条件" min-width="240">
          <template #default="{ row }">
            <template v-if="row.scopeType === 'ROLE'">
              {{ (row.roleCodes || []).map(formatRoleCode).join(' / ') || '--' }}
            </template>
            <template v-else>
              {{ formatMoney(row.amountMin || 0) }} ~ {{ row.amountMax === null || row.amountMax === undefined ? '不限' : formatMoney(row.amountMax) }}
            </template>
          </template>
        </el-table-column>
        <el-table-column label="周期" width="110">
          <template #default="{ row }">
            {{ formatCycle(row.settlementCycle) }}
          </template>
        </el-table-column>
        <el-table-column label="方式" width="130">
          <template #default="{ row }">
            {{ formatSettlementMode(row.settlementMode) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="policyStatusTag(row.status)">{{ formatPolicyStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90" prop="priority" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" @click="publishRule(row)">发布</el-button>
              <el-button v-if="row.status !== 'DISABLED'" size="small" plain @click="disableRule(row)">停用</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="pagination.total"
          :current-page="pagination.currentPage"
          :page-size="pagination.pageSize"
          :page-sizes="pagination.pageSizes"
          @size-change="pagination.handleSizeChange"
          @current-change="pagination.handleCurrentChange"
        />
      </div>
    </section>

    <section class="panel compact-panel">
      <div class="panel-heading">
        <div>
          <h3>规则模拟</h3>
        </div>
      </div>
      <div class="simulate-grid">
        <label>
          <span>结算对象</span>
          <el-select v-model="simulateForm.subjectType">
            <el-option label="内部员工" value="INTERNAL_STAFF" />
            <el-option label="外部分销" value="DISTRIBUTOR" />
          </el-select>
        </label>
        <label v-if="simulateForm.subjectType === 'INTERNAL_STAFF'">
          <span>员工角色</span>
          <el-select v-model="simulateForm.roleCode" filterable placeholder="请选择角色">
            <el-option v-for="item in roleOptions" :key="item.roleCode" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <label>
          <span>金额</span>
          <el-input-number v-model="simulateForm.amount" :min="0" :precision="2" controls-position="right" />
        </label>
        <div class="simulate-actions">
          <el-button type="primary" :loading="simulating" @click="simulateRule">测试匹配</el-button>
        </div>
      </div>

      <div v-if="simulateResult" class="decision-card">
        <div>
          <span>命中规则</span>
          <strong>{{ simulateResult.matchedPolicy?.policyName || '未命中' }}</strong>
        </div>
        <div>
          <span>结算周期</span>
          <strong>{{ formatCycle(simulateResult.settlementCycle) }}</strong>
        </div>
        <div>
          <span>处理方式</span>
          <strong>{{ formatSettlementMode(simulateResult.settlementMode) }}</strong>
        </div>
        <p>{{ simulateResult.nextAction || simulateResult.message }}</p>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="ruleForm.id ? '编辑结算规则' : '新增结算规则'" width="720px">
      <div class="form-grid">
        <label>
          <span>规则名称</span>
          <el-input v-model="ruleForm.policyName" placeholder="例如：分销小额线下结清登记" />
        </label>
        <label>
          <span>结算对象</span>
          <el-select v-model="ruleForm.subjectType" @change="handleSubjectChange">
            <el-option label="内部员工" value="INTERNAL_STAFF" />
            <el-option label="外部分销" value="DISTRIBUTOR" />
          </el-select>
        </label>
        <label>
          <span>匹配方式</span>
          <el-select v-model="ruleForm.scopeType">
            <el-option label="按角色" value="ROLE" />
            <el-option label="按金额" value="AMOUNT" />
          </el-select>
        </label>
        <label>
          <span>优先级</span>
          <el-input-number v-model="ruleForm.priority" :min="1" :step="10" controls-position="right" />
        </label>
        <label v-if="ruleForm.scopeType === 'ROLE'" class="full-span">
          <span>匹配角色</span>
          <el-select v-model="ruleForm.roleCodes" multiple filterable collapse-tags collapse-tags-tooltip placeholder="请选择角色">
            <el-option v-for="item in roleOptions" :key="item.roleCode" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <template v-else>
          <label>
            <span>最低金额</span>
            <el-input-number v-model="ruleForm.amountMin" :min="0" :precision="2" controls-position="right" />
          </label>
          <label>
            <span>最高金额</span>
            <el-input-number v-model="ruleForm.amountMax" :min="0" :precision="2" controls-position="right" placeholder="留空表示不限" />
          </label>
        </template>
        <label>
          <span>结算周期</span>
          <el-select v-model="ruleForm.settlementCycle">
            <el-option label="按月" value="MONTHLY" />
            <el-option label="即时" value="INSTANT" />
          </el-select>
        </label>
        <label>
          <span>结算方式</span>
          <el-select v-model="ruleForm.settlementMode">
            <el-option label="只记账" value="LEDGER_ONLY" />
            <el-option label="外部处理直接登记" value="WITHDRAW_DIRECT" />
            <el-option label="线下结清需审核" value="WITHDRAW_AUDIT" />
          </el-select>
        </label>
        <label>
          <span>审核阈值</span>
          <el-input-number v-model="ruleForm.auditThresholdAmount" :min="0" :precision="2" controls-position="right" />
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="ruleForm.remark" placeholder="用于说明该规则的适用边界" />
        </label>
      </div>
      <el-alert
        v-if="ruleForm.settlementMode === 'WITHDRAW_DIRECT'"
        title="当前规则只会直接登记为外部已处理；系统不会发起打款、提现或资金划拨。发布前请确认金额上限和适用对象。"
        type="warning"
        show-icon
        :closable="false"
        class="rule-risk-alert"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTablePagination } from '../composables/useTablePagination'
import {
  disableSettlementPolicy,
  fetchSettlementPolicies,
  publishSettlementPolicy,
  saveSettlementPolicyDraft,
  simulateSettlementPolicy
} from '../api/salary'
import { formatMoney, formatRoleCode, formatSettlementMode } from '../utils/format'
import { loadSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const policies = ref([])
const loading = ref(false)
const saving = ref(false)
const simulating = ref(false)
const dialogVisible = ref(false)
const simulateResult = ref(null)
const activeRuleTab = ref('settlement')
const filteredPolicies = computed(() =>
  policies.value.filter((item) => {
    const subjectType = item.subjectType || 'INTERNAL_STAFF'
    if (activeRuleTab.value === 'settlement') {
      return subjectType === 'INTERNAL_STAFF' || item.settlementMode === 'LEDGER_ONLY'
    }
    return subjectType === 'DISTRIBUTOR' || item.settlementMode !== 'LEDGER_ONLY'
  })
)
const pagination = useTablePagination(filteredPolicies)
const ruleForm = reactive(createRuleForm())
const simulateForm = reactive({
  subjectType: 'DISTRIBUTOR',
  roleCode: 'ONLINE_CUSTOMER_SERVICE',
  amount: 500
})

const roleOptions = computed(() => {
  const roleMap = new Map()
  for (const item of [...(state.roles || []), ...(state.salaryRoles || [])]) {
    const roleCode = item.roleCode
    if (!roleCode) {
      continue
    }
    roleMap.set(roleCode, {
      roleCode,
      roleName: item.roleName || formatRoleCode(roleCode)
    })
  }
  return Array.from(roleMap.values())
})
const publishedCount = computed(() => policies.value.filter((item) => item.status === 'PUBLISHED').length)
const draftCount = computed(() => policies.value.filter((item) => item.status === 'DRAFT').length)
const disabledCount = computed(() => policies.value.filter((item) => ['DISABLED', 'ARCHIVED'].includes(item.status)).length)
const financeBoundaryCards = [
  {
    title: '台账边界',
    description: '本系统只生成薪酬、分销和线下处理台账，不接管第三方资金。'
  },
  {
    title: '退款口径',
    description: '真实退款在抖音、分销或线下渠道完成，本系统登记退款冲正和薪资冲正。'
  },
  {
    title: '分销结清',
    description: '分销提现按规则进入外部处理登记或财务审核登记，不在系统内打款。'
  }
]

function createRuleForm() {
  return {
    id: null,
    policyName: '',
    subjectType: 'DISTRIBUTOR',
    scopeType: 'AMOUNT',
    roleCodes: [],
    amountMin: 0,
    amountMax: null,
    settlementCycle: 'INSTANT',
    settlementMode: 'WITHDRAW_AUDIT',
    auditThresholdAmount: 3000,
    priority: 100,
    remark: ''
  }
}

function normalizeLegacyRule(row) {
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
    auditThresholdAmount: row.settlementMode === 'WITHDRAW_AUDIT' ? row.amountMin : null,
    priority: row.id * 10,
    enabled: row.enabled,
    status: row.enabled === 1 ? 'PUBLISHED' : 'DISABLED',
    remark: row.remark
  }
}

async function loadPolicies() {
  loading.value = true
  try {
    policies.value = await fetchSettlementPolicies()
  } catch {
    policies.value = (state.salarySettlementRules || []).map(normalizeLegacyRule)
  } finally {
    loading.value = false
    pagination.reset()
  }
}

function openCreateDialog() {
  Object.assign(ruleForm, createRuleForm())
  dialogVisible.value = true
}

function openEditDialog(row) {
  Object.assign(ruleForm, {
    ...createRuleForm(),
    ...row,
    roleCodes: [...(row.roleCodes || [])],
    amountMax: row.amountMax ?? null,
    auditThresholdAmount: row.auditThresholdAmount ?? 0
  })
  dialogVisible.value = true
}

function handleSubjectChange() {
  if (ruleForm.subjectType === 'INTERNAL_STAFF') {
    ruleForm.scopeType = 'ROLE'
    ruleForm.settlementCycle = 'MONTHLY'
    ruleForm.settlementMode = 'LEDGER_ONLY'
    return
  }
  ruleForm.scopeType = 'AMOUNT'
  ruleForm.settlementCycle = 'INSTANT'
}

async function saveDraft() {
  if (!ruleForm.policyName.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  if (ruleForm.scopeType === 'ROLE' && !ruleForm.roleCodes.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  try {
    await ElMessageBox.confirm('保存草稿不会立即生效；发布后才会影响后续新结算和线下处理登记，不发起真实资金动作。确认保存吗？', '保存规则草稿', {
      type: 'warning',
      confirmButtonText: '确认保存',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  saving.value = true
  try {
    await saveSettlementPolicyDraft({
      ...ruleForm,
      amountMax: ruleForm.scopeType === 'AMOUNT' ? ruleForm.amountMax : null
    })
    dialogVisible.value = false
    ElMessage.success('规则草稿已保存')
    await loadPolicies()
  } finally {
    saving.value = false
  }
}

async function publishRule(row) {
  try {
    await ElMessageBox.confirm('发布只影响后续规则匹配，不发起资金划转，不重算历史结算/线下结清/冲正记录。确认发布吗？', '发布结算规则', {
      type: 'warning',
      confirmButtonText: '确认发布',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await publishSettlementPolicy({ policyId: row.id })
  ElMessage.success('规则已发布')
  await loadPolicies()
}

async function disableRule(row) {
  try {
    await ElMessageBox.confirm('停用后该规则不再参与结算/线下处理匹配，确认停用吗？', '停用结算规则', {
      type: 'warning',
      confirmButtonText: '确认停用',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await disableSettlementPolicy({ policyId: row.id })
  ElMessage.success('规则已停用')
  await loadPolicies()
}

async function simulateRule() {
  simulating.value = true
  try {
    simulateResult.value = await simulateSettlementPolicy(simulateForm)
  } finally {
    simulating.value = false
  }
}

function formatSubjectType(value) {
  return {
    INTERNAL_STAFF: '内部员工',
    DISTRIBUTOR: '外部分销'
  }[value] || value || '--'
}

function formatCycle(value) {
  return {
    MONTHLY: '按月',
    INSTANT: '即时'
  }[value] || value || '--'
}

function formatPolicyStatus(value) {
  return {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    DISABLED: '已停用',
    ARCHIVED: '已归档'
  }[value] || value || '--'
}

function policyStatusTag(value) {
  return {
    DRAFT: 'warning',
    PUBLISHED: 'success',
    DISABLED: 'info',
    ARCHIVED: 'info'
  }[value] || 'info'
}

onMounted(loadPolicies)
</script>

<style scoped>
.simulate-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  align-items: end;
}

.simulate-grid label {
  display: grid;
  gap: 8px;
}

.simulate-grid label span {
  color: #64748b;
  font-size: 13px;
}

.simulate-actions {
  display: flex;
  align-items: center;
}

.finance-boundary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.finance-boundary-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.finance-boundary-card strong {
  color: #0f172a;
}

.finance-boundary-card span {
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.decision-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: #f8fbff;
}

.decision-card div {
  display: grid;
  gap: 4px;
}

.decision-card span {
  color: #64748b;
  font-size: 12px;
}

.decision-card strong {
  color: #0f172a;
  font-size: 16px;
}

.decision-card p {
  grid-column: 1 / -1;
  margin: 0;
  color: #475569;
  font-size: 13px;
}

.rule-risk-alert {
  margin-top: 14px;
}

@media (max-width: 1100px) {
  .simulate-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .finance-boundary-grid {
    grid-template-columns: 1fr;
  }

  .decision-card {
    grid-template-columns: 1fr;
  }
}
</style>
