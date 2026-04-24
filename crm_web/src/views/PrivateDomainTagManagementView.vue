<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>便签数量</span>
        <strong>{{ state.wecomTagConfigs.length }}</strong>
        <small>管理客户标签并与企业微信标签保持同步。</small>
      </article>
      <article class="metric-card">
        <span>累计客户</span>
        <strong>{{ totalCustomerCount }}</strong>
        <small>基于当前便签统计出的客户总量。</small>
      </article>
      <article class="metric-card">
        <span>今日新增</span>
        <strong>{{ totalNewToday }}</strong>
        <small>按标签维度统计今天新增客户情况。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>便签配置</h3>
          <p>建立标签、给客户打标签，并同步到企业微信标签体系。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>便签名称</span>
          <el-input v-model="tagForm.tagName" placeholder="请输入便签名称" />
        </label>
        <label>
          <span>编码</span>
          <el-input v-model="tagForm.tagCode" placeholder="请输入唯一编码" />
        </label>
        <label>
          <span>客户数</span>
          <el-input-number v-model="tagForm.customerCount" :min="0" controls-position="right" />
        </label>
        <label>
          <span>今日新增</span>
          <el-input-number v-model="tagForm.newToday" :min="0" controls-position="right" />
        </label>
        <label>
          <span>同步状态</span>
          <el-select v-model="tagForm.syncStatus">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="处理中" value="PENDING" />
            <el-option label="失败" value="FAIL" />
          </el-select>
        </label>
        <label>
          <span>创建时间</span>
          <el-date-picker
            v-model="tagForm.createdAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择时间"
            style="width: 100%"
          />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveTag">保存便签</el-button>
        <el-button @click="resetTagForm">重置表单</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>标签列表</h3>
          <p>可直接查看标签同步状态，以及累计客户和今日新增的统计结果。</p>
        </div>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="便签" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.tagName }}</strong>
              <span>{{ row.tagCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="累计客户" width="120" prop="customerCount" />
        <el-table-column label="今日新增" width="120" prop="newToday" />
        <el-table-column label="同步状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.syncStatus)">{{ formatOrderStatus(row.syncStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickTag(row)">编辑</el-button>
              <el-button size="small" plain @click="promoteTag(row)">同步加一</el-button>
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
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, formatOrderStatus, statusTagType } from '../utils/format'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const pagination = useTablePagination(computed(() => state.wecomTagConfigs))

const tagForm = reactive(createTagForm())

const totalCustomerCount = computed(() => state.wecomTagConfigs.reduce((sum, item) => sum + Number(item.customerCount || 0), 0))
const totalNewToday = computed(() => state.wecomTagConfigs.reduce((sum, item) => sum + Number(item.newToday || 0), 0))

function createTagForm() {
  return {
    id: null,
    tagName: '',
    tagCode: '',
    customerCount: 0,
    newToday: 0,
    syncStatus: 'SUCCESS',
    createdAt: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetTagForm() {
  Object.assign(tagForm, createTagForm())
}

function pickTag(row) {
  Object.assign(tagForm, { ...row })
}

function saveTag() {
  if (!tagForm.tagName || !tagForm.tagCode) {
    ElMessage.warning('请先填写便签名称和编码')
    return
  }
  const nextItems = [...state.wecomTagConfigs]
  const nextRow = {
    ...tagForm,
    id: tagForm.id || nextSystemId(nextItems)
  }
  if (tagForm.id) {
    const index = nextItems.findIndex((item) => item.id === tagForm.id)
    nextItems[index] = nextRow
  } else {
    nextItems.push(nextRow)
  }
  replaceState({
    ...state,
    wecomTagConfigs: nextItems
  })
  pagination.reset()
  ElMessage.success('便签已保存')
  resetTagForm()
}

function promoteTag(row) {
  replaceState({
    ...state,
    wecomTagConfigs: state.wecomTagConfigs.map((item) =>
      item.id === row.id
        ? {
            ...item,
            customerCount: Number(item.customerCount || 0) + 1,
            newToday: Number(item.newToday || 0) + 1,
            syncStatus: 'SUCCESS'
          }
        : item
    )
  })
  ElMessage.success('标签统计已更新')
}
</script>
