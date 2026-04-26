<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>门店角色</span>
        <strong>{{ storeRoles.length }}</strong>
      </article>
      <article class="metric-card">
        <span>启用角色</span>
        <strong>{{ storeRoles.filter((item) => item.isEnabled === 1).length }}</strong>
      </article>
      <article class="metric-card">
        <span>覆盖人员</span>
        <strong>{{ coveredEmployeeCount }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>门店角色管理</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="resetForm">新增角色</el-button>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>角色编码</span>
          <el-input v-model="form.roleCode" placeholder="如 STORE_MANAGER" />
        </label>
        <label>
          <span>角色名称</span>
          <el-input v-model="form.roleName" placeholder="请输入门店角色名称" />
        </label>
        <label>
          <span>默认首页</span>
          <el-select v-model="form.defaultEntry">
            <el-option v-for="item in entryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label>
          <span>数据范围</span>
          <el-input model-value="门店" readonly />
        </label>
        <label class="full-span">
          <span>模块权限</span>
          <el-select v-model="form.moduleCodes" multiple placeholder="请选择门店角色可用模块">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="form.remark" placeholder="请输入角色说明" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveRole">保存角色</el-button>
        <el-button @click="resetForm">重置</el-button>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.roleName }}</strong>
              <span>{{ row.roleCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="默认首页" min-width="180">
          <template #default="{ row }">
            {{ formatEntryLabel(row.defaultEntry) || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="模块权限" min-width="220">
          <template #default="{ row }">
            {{ formatModuleNames(row.moduleCodes) || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="关联人员" width="110">
          <template #default="{ row }">
            {{ employeeCount(row.roleCode) }}
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
              <el-button size="small" @click="pickRole(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleRole(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useTablePagination } from '../composables/useTablePagination'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const entryOptions = [
  { label: '订单列表', value: '/store-service/orders' },
  { label: '服务单设计', value: '/store-service/service-design' },
  { label: '人员管理', value: '/store-service/personnel' }
]
const moduleOptions = [
  { label: '订单管理', value: 'ORDER' },
  { label: '服务单', value: 'PLANORDER' },
  { label: '门店管理', value: 'SYSTEM' }
]
const storeRoles = computed(() =>
  state.roles.filter((item) => item.roleCode === 'STORE_SERVICE' || item.dataScope === 'STORE')
)
const pagination = useTablePagination(storeRoles)
const coveredEmployeeCount = computed(() =>
  state.employees.filter((item) => storeRoles.value.some((role) => role.roleCode === item.roleCode)).length
)

const form = reactive(createForm())

function createForm() {
  return {
    id: null,
    roleCode: '',
    roleName: '',
    defaultEntry: '/store-service/orders',
    moduleCodes: ['ORDER', 'PLANORDER'],
    remark: ''
  }
}

function persistState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetForm() {
  Object.assign(form, createForm())
}

function saveRole() {
  if (!form.roleCode || !form.roleName) {
    ElMessage.warning('请完整填写角色名称和角色编码')
    return
  }
  const items = [...state.roles]
  const nextItem = {
    ...form,
    id: form.id || nextSystemId(items),
    dataScope: 'STORE',
    isEnabled: form.id ? (items.find((item) => item.id === form.id)?.isEnabled ?? 1) : 1
  }
  if (form.id) {
    items.splice(
      items.findIndex((item) => item.id === form.id),
      1,
      nextItem
    )
  } else {
    items.push(nextItem)
  }
  persistState({ ...state, roles: items })
  pagination.reset()
  resetForm()
  ElMessage.success('门店角色已保存')
}

function pickRole(row) {
  Object.assign(form, {
    ...row,
    moduleCodes: [...(row.moduleCodes || ['ORDER', 'PLANORDER'])]
  })
}

function toggleRole(row) {
  persistState({
    ...state,
    roles: state.roles.map((item) =>
      item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
    )
  })
}

function employeeCount(roleCode) {
  return state.employees.filter((item) => item.roleCode === roleCode).length
}

function formatEntryLabel(value) {
  return entryOptions.find((item) => item.value === value)?.label || value
}

function formatModuleNames(moduleCodes = []) {
  return moduleCodes
    .map((code) => moduleOptions.find((item) => item.value === code)?.label || code)
    .join(' / ')
}
</script>
