<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>今日收入</span>
        <strong>{{ formatMoney(overview?.todayIncome) }}</strong>
        <small>按今日完成订单累计</small>
      </article>
      <article class="metric-card">
        <span>员工收益</span>
        <strong>{{ formatMoney(overview?.employeeIncome) }}</strong>
        <small>服务角色累计收益</small>
      </article>
      <article class="metric-card">
        <span>分销收益</span>
        <strong>{{ formatMoney(overview?.distributorIncome) }}</strong>
        <small>分销带客累计收益</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>提现记录</h3>
          <p>门店日常只看最近记录，避免复杂报表干扰决策。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="overview?.withdrawRecords || []" stripe>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.ownerType === 'EMPLOYEE' ? '员工提现' : '分销提现' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主体" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.ownerName }}</strong>
              <span>ID: {{ row.ownerId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchFinanceOverview } from '../api/workbench'
import { formatDateTime, formatMoney, statusTagType } from '../utils/format'

const loading = ref(false)
const overview = ref(null)

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await fetchFinanceOverview()
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>
