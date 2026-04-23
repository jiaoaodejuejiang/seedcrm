<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>今日收入</span>
        <strong>{{ formatMoney(overview?.todayIncome) }}</strong>
        <small>来自后端财务总览的当前日收入快照。</small>
      </article>
      <article class="metric-card">
        <span>员工收入</span>
        <strong>{{ formatMoney(overview?.employeeIncome) }}</strong>
        <small>归因到员工角色记录的收入汇总。</small>
      </article>
      <article class="metric-card">
        <span>分销收入</span>
        <strong>{{ formatMoney(overview?.distributorIncome) }}</strong>
        <small>归因到分销转化链路的收入汇总。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>提现记录</h3>
          <p>统一查看员工与分销两侧的提现记录及当前审核状态。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="overview?.withdrawRecords || []" stripe>
        <el-table-column label="归属类型" width="140">
          <template #default="{ row }">
            <el-tag>{{ row.ownerType === 'EMPLOYEE' ? '员工' : '分销商' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归属对象" min-width="180">
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
        <el-table-column label="创建时间" min-width="180">
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

onMounted(loadOverview)
</script>
