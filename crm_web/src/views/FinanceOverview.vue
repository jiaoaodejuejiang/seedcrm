<template>
  <div class="stack-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>今日记账金额</span>
        <strong>{{ formatMoney(overview?.todayIncome) }}</strong>
      </article>
      <article class="summary-pill">
        <span>员工账面收入</span>
        <strong>{{ formatMoney(overview?.employeeIncome) }}</strong>
      </article>
      <article class="summary-pill">
        <span>分销账面收入</span>
        <strong>{{ formatMoney(overview?.distributorIncome) }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>财务看板</h3>
          <p>本系统只记录业务账务和处理状态，真实收款、退款、结清由抖音、分销平台、支付平台或线下完成。</p>
        </div>
      </div>

      <el-alert
        :title="ledgerBoundary.title"
        :description="ledgerBoundary.overviewDescription"
        type="info"
        show-icon
        :closable="false"
        class="finance-boundary-alert"
      />

      <div class="finance-boundary-grid">
        <article>
          <span>财务模式</span>
          <strong>{{ ledgerBoundary.onlyModeEnabled ? '只记账' : '按配置' }}</strong>
        </article>
        <article>
          <span>退款冲正</span>
          <strong>{{ ledgerBoundary.refundSalaryReversalRequired ? '必须冲正' : '按配置' }}</strong>
        </article>
        <article>
          <span>分销提现</span>
          <strong>{{ ledgerBoundary.distributorWithdrawRegisterOnly ? '仅登记' : '按配置' }}</strong>
        </article>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs finance-tabs">
        <el-tab-pane label="团队" name="team">
          <div class="tab-summary">
            <article class="tab-summary__item">
              <span>团队数量</span>
              <strong>{{ overview?.teamStats?.length || 0 }}</strong>
            </article>
            <article class="tab-summary__item">
              <span>最高结算团队</span>
              <strong>{{ displayTeamLabel(overview?.teamStats?.[0]?.teamLabel) }}</strong>
            </article>
          </div>

          <el-table v-loading="loading" :data="overview?.teamStats || []" stripe>
            <el-table-column label="团队" min-width="180">
              <template #default="{ row }">
                {{ displayTeamLabel(row.teamLabel) }}
              </template>
            </el-table-column>
            <el-table-column label="人数" width="100" prop="memberCount" />
            <el-table-column label="服务次数" width="120" prop="serviceCount" />
            <el-table-column label="订单记账金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.orderIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="账面结算收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.incomeAmount) }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="月度" name="month">
          <div class="tab-summary">
            <article class="tab-summary__item">
              <span>统计月份</span>
              <strong>{{ overview?.monthlyStats?.length || 0 }}</strong>
            </article>
            <article class="tab-summary__item">
              <span>最新月份</span>
              <strong>{{ overview?.monthlyStats?.[0]?.monthLabel || '--' }}</strong>
            </article>
          </div>

          <el-table v-loading="loading" :data="overview?.monthlyStats || []" stripe>
            <el-table-column label="月份" width="120" prop="monthLabel" />
            <el-table-column label="订单记账收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.orderIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="员工账面收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.employeeIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="分销账面收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.distributorIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="线下处理金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.withdrawAmount) }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="线下结清记录" name="withdraw">
          <el-table v-loading="loading" :data="overview?.withdrawRecords || []" stripe>
            <el-table-column label="对象" min-width="180">
              <template #default="{ row }">
                <div class="table-primary">
                  <strong>{{ row.ownerName || '--' }}</strong>
                  <span>{{ formatOwnerType(row.ownerType) }} #{{ row.ownerId || '--' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="记账金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.amount) }}
              </template>
            </el-table-column>
            <el-table-column label="线下处理状态" min-width="140">
              <template #default="{ row }">
                <el-tag :type="withdrawStatusTag(row.status)">{{ formatWithdrawStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="记录时间" min-width="180">
              <template #default="{ row }">
                {{ row.createTime || '--' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchFinanceLedgerBoundary } from '../api/finance'
import { fetchFinanceOverview } from '../api/workbench'
import { formatMoney, formatRoleCode } from '../utils/format'

const activeTab = ref('team')
const loading = ref(true)
const overview = ref(null)
const ledgerBoundary = ref(defaultFinanceLedgerBoundary())

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await fetchFinanceOverview()
  } catch {
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function loadLedgerBoundary() {
  try {
    ledgerBoundary.value = {
      ...defaultFinanceLedgerBoundary(),
      ...(await fetchFinanceLedgerBoundary({ silentError: true }))
    }
  } catch {
    ledgerBoundary.value = defaultFinanceLedgerBoundary()
  }
}

function defaultFinanceLedgerBoundary() {
  return {
    onlyModeEnabled: true,
    refundSalaryReversalRequired: true,
    distributorWithdrawRegisterOnly: true,
    title: '只记账、不走资金',
    overviewDescription: '订单完成、薪酬、分销和退款冲正只形成系统台账；线下结清状态仅表示人工或第三方处理进度。'
  }
}

function displayTeamLabel(value) {
  if (!value) {
    return '--'
  }
  const text = String(value).trim()
  if (/^[A-Z_]+$/.test(text)) {
    if (text === 'DISTRIBUTOR_TEAM') {
      return '分销团队'
    }
    return formatRoleCode(text)
  }
  return text
    .replace(/^Finance\b/gi, '财务')
    .replace(/\bQA\b/gi, '质检')
    .replace(/\bRole\b/gi, '角色')
    .replace(/\s+/g, ' ')
    .trim()
}

function formatOwnerType(value) {
  const normalized = String(value || '').trim().toUpperCase()
  if (normalized === 'DISTRIBUTOR') {
    return '分销'
  }
  if (normalized === 'EMPLOYEE' || normalized === 'STAFF') {
    return '员工'
  }
  return normalized || '对象'
}

function formatWithdrawStatus(value) {
  const normalized = String(value || '').trim().toUpperCase()
  const labels = {
    PENDING: '待线下处理',
    PROCESSING: '线下处理中',
    PAID: '已标记处理',
    COMPLETED: '已标记处理',
    REJECTED: '已驳回',
    CANCELLED: '已取消'
  }
  return labels[normalized] || value || '--'
}

function withdrawStatusTag(value) {
  const normalized = String(value || '').trim().toUpperCase()
  if (['PAID', 'COMPLETED'].includes(normalized)) {
    return 'success'
  }
  if (['REJECTED', 'CANCELLED'].includes(normalized)) {
    return 'danger'
  }
  return 'warning'
}

onMounted(async () => {
  await Promise.all([loadLedgerBoundary(), loadOverview()])
})
</script>

<style scoped>
.finance-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.finance-boundary-alert {
  margin-bottom: 18px;
}

.finance-boundary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.finance-boundary-grid article {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5edf4;
  background: #f8fbff;
  border-radius: 8px;
}

.finance-boundary-grid span {
  color: #64748b;
  font-size: 12px;
}

.finance-boundary-grid strong {
  color: #0f172a;
  font-size: 15px;
}

.tab-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.tab-summary__item {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fbff;
  border: 1px solid #e5edf4;
}

.tab-summary__item span {
  color: #64748b;
  font-size: 13px;
}

.tab-summary__item strong {
  color: #0f172a;
  font-size: 18px;
}

@media (max-width: 900px) {
  .tab-summary,
  .finance-boundary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
