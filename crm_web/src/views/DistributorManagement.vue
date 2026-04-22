<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>分销商数</span>
        <strong>{{ distributors.length }}</strong>
        <small>当前系统已纳入统计的分销伙伴</small>
      </article>
      <article class="metric-card">
        <span>带来线索</span>
        <strong>{{ totalClues }}</strong>
        <small>按分销来源沉淀的线索量</small>
      </article>
      <article class="metric-card">
        <span>总收益</span>
        <strong>{{ formatMoney(totalIncome) }}</strong>
        <small>累计分销收益，不做复杂报表</small>
      </article>
    </section>

    <section class="panel">
      <el-table v-loading="loading" :data="distributors" stripe>
        <el-table-column label="分销商" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.name }}</strong>
              <span>{{ row.contactInfo || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="带来线索" width="110" prop="clueCount" />
        <el-table-column label="成单客户" width="110" prop="dealCustomerCount" />
        <el-table-column label="订单数" width="100" prop="orderCount" />
        <el-table-column label="累计收益" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.totalIncome) }}
          </template>
        </el-table-column>
        <el-table-column label="待结算" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.unsettledIncome) }}
          </template>
        </el-table-column>
        <el-table-column label="可提现" width="140">
          <template #default="{ row }">
            {{ formatMoney(row.withdrawableAmount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchDistributors } from '../api/workbench'
import { formatMoney, statusTagType } from '../utils/format'

const loading = ref(false)
const distributors = ref([])

const totalClues = computed(() => distributors.value.reduce((sum, item) => sum + Number(item.clueCount || 0), 0))
const totalIncome = computed(() => distributors.value.reduce((sum, item) => sum + Number(item.totalIncome || 0), 0))

async function loadDistributors() {
  loading.value = true
  try {
    distributors.value = await fetchDistributors()
  } finally {
    loading.value = false
  }
}

onMounted(loadDistributors)
</script>
