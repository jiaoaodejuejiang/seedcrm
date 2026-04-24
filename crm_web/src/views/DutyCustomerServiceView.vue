<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>客服人数</span>
        <strong>{{ staffRows.length }}</strong>
        <small>值班客服维护自动分配可使用的客服名单。</small>
      </article>
      <article class="metric-card">
        <span>当值中</span>
        <strong>{{ onDutyCount }}</strong>
        <small>只有当值且未请假的客服会进入自动分配池。</small>
      </article>
      <article class="metric-card">
        <span>请假中</span>
        <strong>{{ onLeaveCount }}</strong>
        <small>请假客服会自动被排除出轮询队列。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>值班客服配置</h3>
          <p>可以维护班次、排序、请假和备注；保存后自动分配策略会立刻读取最新名单。</p>
        </div>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="handleAddRow">新增客服</el-button>
        <el-button :loading="saving" @click="handleSave">保存配置</el-button>
        <el-button @click="loadData">重新加载</el-button>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="用户 ID" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.userId" :min="1" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="账号" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.accountName" placeholder="如 online_cs_a" />
          </template>
        </el-table-column>
        <el-table-column label="姓名" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.userName" placeholder="如 客服A" />
          </template>
        </el-table-column>
        <el-table-column label="班次" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.shiftLabel" placeholder="如 早班 09:00-18:00" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="1" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="当值" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.onDuty" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="请假" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.onLeave" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="班次备注、请假说明等" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ $index }">
            <el-button text type="danger" @click="staffRows.splice($index, 1)">删除</el-button>
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
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDutyCustomerServices, saveDutyCustomerServices } from '../api/clueManagement'
import { useTablePagination } from '../composables/useTablePagination'

const staffRows = ref([])
const pagination = useTablePagination(staffRows)
const saving = ref(false)

const onDutyCount = computed(() => staffRows.value.filter((item) => item.onDuty === 1 && item.onLeave !== 1).length)
const onLeaveCount = computed(() => staffRows.value.filter((item) => item.onLeave === 1).length)

async function loadData() {
  staffRows.value = await fetchDutyCustomerServices()
  pagination.reset()
}

function handleAddRow() {
  staffRows.value.push({
    id: null,
    userId: undefined,
    accountName: '',
    userName: '',
    shiftLabel: '',
    onDuty: 1,
    onLeave: 0,
    sortOrder: staffRows.value.length + 1,
    remark: ''
  })
}

async function handleSave() {
  saving.value = true
  try {
    staffRows.value = await saveDutyCustomerServices({
      staff: staffRows.value.map((item, index) => ({
        ...item,
        sortOrder: item.sortOrder || index + 1
      }))
    })
    pagination.reset()
    ElMessage.success('值班客服配置已保存')
  } finally {
    saving.value = false
  }
}

loadData()
</script>
