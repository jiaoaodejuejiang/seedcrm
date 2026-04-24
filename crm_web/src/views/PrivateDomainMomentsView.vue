<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>群发任务</span>
        <strong>{{ state.wecomMomentsCampaigns.length }}</strong>
        <small>统一管理企业微信朋友圈的定时群发计划。</small>
      </article>
      <article class="metric-card">
        <span>已启用</span>
        <strong>{{ enabledCount }}</strong>
        <small>启用状态的任务会进入后续排程队列。</small>
      </article>
      <article class="metric-card">
        <span>待发送</span>
        <strong>{{ pendingCount }}</strong>
        <small>草稿或待执行的任务可继续调整内容与时间。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>朋友圈定时群发</h3>
          <p>针对企业微信好友朋友圈进行统一群发，支持按时间和目标人群管理任务。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>任务名称</span>
          <el-input v-model="campaignForm.campaignName" placeholder="请输入任务名称" />
        </label>
        <label>
          <span>目标范围</span>
          <el-input v-model="campaignForm.targetScope" placeholder="如 全部好友 / 已到店客户 / 标签分组" />
        </label>
        <label class="full-span">
          <span>群发内容</span>
          <el-input v-model="campaignForm.contentSummary" type="textarea" :rows="4" placeholder="请输入朋友圈群发内容摘要" />
        </label>
        <label>
          <span>计划时间</span>
          <el-date-picker
            v-model="campaignForm.scheduleTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择计划时间"
            style="width: 100%"
          />
        </label>
        <label>
          <span>状态</span>
          <el-select v-model="campaignForm.status">
            <el-option label="启用" value="ENABLED" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveCampaign">保存任务</el-button>
        <el-button @click="resetCampaignForm">重置表单</el-button>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="任务名称" min-width="180" prop="campaignName" />
        <el-table-column label="目标范围" min-width="160" prop="targetScope" />
        <el-table-column label="内容摘要" min-width="220" prop="contentSummary" />
        <el-table-column label="计划时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.scheduleTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="140" prop="createdBy" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickCampaign(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCampaign(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
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
const pagination = useTablePagination(computed(() => state.wecomMomentsCampaigns))

const campaignForm = reactive(createCampaignForm())

const enabledCount = computed(() => state.wecomMomentsCampaigns.filter((item) => item.status === 'ENABLED').length)
const pendingCount = computed(() => state.wecomMomentsCampaigns.filter((item) => item.status !== 'ENABLED').length)

function createCampaignForm() {
  return {
    id: null,
    campaignName: '',
    contentSummary: '',
    scheduleTime: '',
    targetScope: '',
    status: 'ENABLED'
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetCampaignForm() {
  Object.assign(campaignForm, createCampaignForm())
}

function pickCampaign(row) {
  Object.assign(campaignForm, { ...row })
}

function saveCampaign() {
  if (!campaignForm.campaignName || !campaignForm.contentSummary || !campaignForm.scheduleTime) {
    ElMessage.warning('请先填写任务名称、内容和计划时间')
    return
  }
  const nextItems = [...state.wecomMomentsCampaigns]
  const nextRow = {
    ...campaignForm,
    id: campaignForm.id || nextSystemId(nextItems),
    createdBy: campaignForm.createdBy || '私域客服A'
  }
  if (campaignForm.id) {
    const index = nextItems.findIndex((item) => item.id === campaignForm.id)
    nextItems[index] = nextRow
  } else {
    nextItems.push(nextRow)
  }
  replaceState({
    ...state,
    wecomMomentsCampaigns: nextItems
  })
  pagination.reset()
  ElMessage.success('群发任务已保存')
  resetCampaignForm()
}

function toggleCampaign(row) {
  replaceState({
    ...state,
    wecomMomentsCampaigns: state.wecomMomentsCampaigns.map((item) =>
      item.id === row.id
        ? { ...item, status: item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' }
        : item
    )
  })
  ElMessage.success('任务状态已更新')
}
</script>
