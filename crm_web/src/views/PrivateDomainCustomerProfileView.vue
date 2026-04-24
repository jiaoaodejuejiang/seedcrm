<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>画像开关</span>
        <strong>{{ portraitEnabled ? '已开启' : '已关闭' }}</strong>
        <small>系统参数决定客户画像是否在企业微信侧展示。</small>
      </article>
      <article class="metric-card">
        <span>画像档案</span>
        <strong>{{ state.wecomCustomerPortraits.length }}</strong>
        <small>可维护客户需求、标签和最近触达摘要。</small>
      </article>
      <article class="metric-card">
        <span>可展示</span>
        <strong>{{ enabledPortraitCount }}</strong>
        <small>当前启用中的画像卡片数量。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>画像开关说明</h3>
          <p>客服画像需在系统设置中开启后，才会在企业微信侧展示。</p>
        </div>
        <el-button @click="router.push('/settings/parameters')">前往参数管理</el-button>
      </div>

      <el-alert
        :title="portraitEnabled ? '当前已开启客服画像展示' : '当前已关闭客服画像展示'"
        :type="portraitEnabled ? 'success' : 'warning'"
        show-icon
        :closable="false"
      />
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>客户画像档案</h3>
          <p>这里维护展示给客服看的画像摘要，帮助私域承接时快速理解客户需求。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>客户名称</span>
          <el-input v-model="portraitForm.customerName" placeholder="请输入客户名称" />
        </label>
        <label>
          <span>企微昵称</span>
          <el-input v-model="portraitForm.wecomNickname" placeholder="请输入企业微信昵称" />
        </label>
        <label class="full-span">
          <span>核心需求</span>
          <el-input v-model="portraitForm.primaryDemand" placeholder="如 皮肤管理 / 植发咨询 / 术后恢复" />
        </label>
        <label class="full-span">
          <span>画像标签</span>
          <el-input v-model="portraitForm.tagsText" placeholder="多个标签请用中文逗号分隔" />
        </label>
        <label>
          <span>最近触达时间</span>
          <el-date-picker
            v-model="portraitForm.lastContactAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择时间"
            style="width: 100%"
          />
        </label>
        <label>
          <span>状态</span>
          <el-select v-model="portraitForm.isEnabled">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="savePortrait">保存画像</el-button>
        <el-button @click="resetPortraitForm">重置表单</el-button>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="客户画像" min-width="200">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.customerName }}</strong>
              <span>{{ row.wecomNickname || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="核心需求" min-width="180" prop="primaryDemand" />
        <el-table-column label="标签" min-width="220">
          <template #default="{ row }">
            {{ (row.tags || []).join(' / ') || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="最近触达" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.lastContactAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickPortrait(row)">编辑</el-button>
              <el-button size="small" plain @click="togglePortrait(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
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
import { useRouter } from 'vue-router'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'
import {
  isSystemParameterEnabled,
  loadSystemConsoleState,
  nextSystemId,
  saveSystemConsoleState
} from '../utils/systemConsoleStore'

const router = useRouter()
const state = reactive(loadSystemConsoleState())
const pagination = useTablePagination(computed(() => state.wecomCustomerPortraits))

const portraitForm = reactive(createPortraitForm())

const portraitEnabled = computed(() => isSystemParameterEnabled(state, 'wecom.customerProfile.enabled'))
const enabledPortraitCount = computed(() => state.wecomCustomerPortraits.filter((item) => item.isEnabled === 1).length)

function createPortraitForm() {
  return {
    id: null,
    customerName: '',
    wecomNickname: '',
    primaryDemand: '',
    tagsText: '',
    lastContactAt: '',
    isEnabled: 1
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetPortraitForm() {
  Object.assign(portraitForm, createPortraitForm())
}

function pickPortrait(row) {
  Object.assign(portraitForm, {
    ...createPortraitForm(),
    ...row,
    tagsText: (row.tags || []).join('，')
  })
}

function savePortrait() {
  if (!portraitForm.customerName || !portraitForm.primaryDemand) {
    ElMessage.warning('请先填写客户名称和核心需求')
    return
  }
  const nextItems = [...state.wecomCustomerPortraits]
  const nextRow = {
    id: portraitForm.id || nextSystemId(nextItems),
    customerName: portraitForm.customerName,
    wecomNickname: portraitForm.wecomNickname,
    primaryDemand: portraitForm.primaryDemand,
    tags: portraitForm.tagsText
      .split(/[，,]/)
      .map((item) => item.trim())
      .filter(Boolean),
    lastContactAt: portraitForm.lastContactAt,
    isEnabled: portraitForm.isEnabled
  }
  if (portraitForm.id) {
    const index = nextItems.findIndex((item) => item.id === portraitForm.id)
    nextItems[index] = nextRow
  } else {
    nextItems.push(nextRow)
  }
  replaceState({
    ...state,
    wecomCustomerPortraits: nextItems
  })
  pagination.reset()
  ElMessage.success('客户画像已保存')
  resetPortraitForm()
}

function togglePortrait(row) {
  replaceState({
    ...state,
    wecomCustomerPortraits: state.wecomCustomerPortraits.map((item) =>
      item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
    )
  })
  ElMessage.success('画像状态已更新')
}
</script>
