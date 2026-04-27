<template>
  <div class="stack-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>今日收入</span>
        <strong>{{ formatMoney(overview?.todayIncome) }}</strong>
      </article>
      <article class="summary-pill">
        <span>员工收入</span>
        <strong>{{ formatMoney(overview?.employeeIncome) }}</strong>
      </article>
      <article class="summary-pill">
        <span>分销收入</span>
        <strong>{{ formatMoney(overview?.distributorIncome) }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>财务看板</h3>
        </div>
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
            <el-table-column label="订单金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.orderIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="结算收入" min-width="140">
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
            <el-table-column label="订单收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.orderIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="员工收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.employeeIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="分销收入" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.distributorIncome) }}
              </template>
            </el-table-column>
            <el-table-column label="提现金额" min-width="140">
              <template #default="{ row }">
                {{ formatMoney(row.withdrawAmount) }}
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
import { fetchFinanceOverview } from '../api/workbench'
import { formatMoney, formatRoleCode } from '../utils/format'

const activeTab = ref('team')
const loading = ref(true)
const overview = ref(null)

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

onMounted(loadOverview)
</script>

<style scoped>
.finance-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
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
  .tab-summary {
    grid-template-columns: 1fr;
  }
}
</style>
