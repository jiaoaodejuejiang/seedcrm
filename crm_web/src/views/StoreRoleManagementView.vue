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
          <h3>门店权限矩阵</h3>
        </div>
        <div class="action-group">
          <el-button :loading="policyLoading" plain @click="loadRuntimePolicies">刷新权限</el-button>
        </div>
      </div>

      <el-table v-loading="policyLoading" :data="storeCapabilityRows" stripe>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.roleName }}</strong>
              <span>{{ row.roleCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="定金/团购金额" width="140">
          <template #default="{ row }">
            <el-tag :type="row.businessAmountHidden ? 'info' : 'warning'" effect="light">
              {{ row.businessAmountHidden ? '隐藏' : '可见' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认单金额" width="140">
          <template #default="{ row }">
            <el-tag :type="row.serviceAmountHidden ? 'info' : 'warning'" effect="light">
              {{ row.serviceAmountHidden ? '隐藏' : '可见' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-for="capability in capabilityDefinitions" :key="capability.key" :label="capability.label" min-width="140">
          <template #default="{ row }">
            <el-tag :type="row.capabilities[capability.key] ? 'success' : 'info'" effect="light">
              {{ row.capabilities[capability.key] ? '允许' : '未授权' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>门店角色管理</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openCreateRole">新增角色</el-button>
        </div>
      </div>

      <el-drawer v-model="roleEditorVisible" class="config-editor-drawer" :size="drawerSize" :close-on-click-modal="false">
        <template #header>
          <div class="drawer-editor__header">
            <span class="drawer-editor__eyebrow">门店角色</span>
            <h3>{{ form.id ? `编辑角色：${form.roleName || '未命名'}` : '新增角色' }}</h3>
            <div class="drawer-editor__meta">
              <span>{{ form.roleCode || '未设置编码' }}</span>
              <span>数据范围：门店</span>
            </div>
          </div>
        </template>

        <div class="drawer-editor__body">
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
        </div>

        <template #footer>
          <div class="drawer-editor__footer">
            <el-button @click="roleEditorVisible = false">取消</el-button>
            <el-button @click="resetForm">重置表单</el-button>
            <el-button type="primary" @click="saveRole">{{ form.id ? '保存修改' : '保存角色' }}</el-button>
          </div>
        </template>
      </el-drawer>

      <el-table :data="pagination.rows" :row-class-name="roleRowClassName" stripe>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPermissionPolicies } from '../api/permission'
import { fetchSystemConfigs } from '../api/systemConfig'
import { useTablePagination } from '../composables/useTablePagination'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const DEFAULT_BUSINESS_AMOUNT_HIDDEN_ROLES = ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR']
const DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES = ['STORE_SERVICE', 'PHOTOGRAPHER', 'MAKEUP_ARTIST']
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
const capabilityDefinitions = [
  { key: 'orderView', label: '订单查看', moduleCode: 'ORDER', actionCode: 'VIEW' },
  { key: 'orderUpdate', label: '核销/确认', moduleCode: 'ORDER', actionCode: 'UPDATE' },
  { key: 'orderFinish', label: '结束订单', moduleCode: 'ORDER', actionCode: 'FINISH' },
  { key: 'storeRefund', label: '门店冲正', moduleCode: 'ORDER', actionCode: 'REFUND_STORE' },
  { key: 'planOrderView', label: '服务单查看', moduleCode: 'PLANORDER', actionCode: 'VIEW' },
  { key: 'planOrderUpdate', label: '服务单操作', moduleCode: 'PLANORDER', actionCode: 'UPDATE' }
]
const storeRoles = computed(() =>
  state.roles.filter((item) => item.roleCode === 'STORE_SERVICE' || item.dataScope === 'STORE')
)
const pagination = useTablePagination(storeRoles)
const coveredEmployeeCount = computed(() =>
  state.employees.filter((item) => storeRoles.value.some((role) => role.roleCode === item.roleCode)).length
)
const policyLoading = ref(false)
const permissionPolicies = ref([])
const amountVisibilityConfigs = ref([])
const storeCapabilityRows = computed(() =>
  storeRoles.value.map((role) => {
    const roleCode = normalizeCode(role.roleCode)
    return {
      ...role,
      roleCode,
      businessAmountHidden: businessAmountHiddenRoles.value.includes(roleCode),
      serviceAmountHidden: serviceAmountHiddenRoles.value.includes(roleCode),
      capabilities: Object.fromEntries(
        capabilityDefinitions.map((capability) => [capability.key, hasEnabledPolicy(roleCode, capability.moduleCode, capability.actionCode)])
      )
    }
  })
)
const businessAmountHiddenRoles = computed(() =>
  parseConfigRoles('amount.visibility.store_staff_hidden_roles', DEFAULT_BUSINESS_AMOUNT_HIDDEN_ROLES)
)
const serviceAmountHiddenRoles = computed(() =>
  parseConfigRoles('amount.visibility.service_confirm_hidden_roles', DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES)
)

const form = reactive(createForm())
const roleEditorVisible = ref(false)
const drawerSize = 'min(92vw, 640px)'

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

function openCreateRole() {
  resetForm()
  roleEditorVisible.value = true
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
  roleEditorVisible.value = false
  ElMessage.success('门店角色已保存')
}

function pickRole(row) {
  Object.assign(form, {
    ...row,
    moduleCodes: [...(row.moduleCodes || ['ORDER', 'PLANORDER'])]
  })
  roleEditorVisible.value = true
}

function roleRowClassName({ row }) {
  return roleEditorVisible.value && form.id === row.id ? 'is-editing-row' : ''
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

function hasEnabledPolicy(roleCode, moduleCode, actionCode) {
  return permissionPolicies.value.some((policy) =>
    normalizeCode(policy.roleCode) === roleCode
      && normalizeCode(policy.moduleCode) === normalizeCode(moduleCode)
      && normalizeCode(policy.actionCode) === normalizeCode(actionCode)
      && Number(policy.isEnabled ?? 1) === 1
  )
}

function parseConfigRoles(key, fallback) {
  const row = amountVisibilityConfigs.value.find((item) => item.configKey === key)
  const raw = row && row.enabled !== 0 ? row.configValue : ''
  const roles = String(raw || '')
    .split(/[,，\s]+/)
    .map(normalizeCode)
    .filter(Boolean)
  return roles.length ? roles : fallback
}

function normalizeCode(value) {
  return String(value || '').trim().toUpperCase()
}

async function loadRuntimePolicies() {
  policyLoading.value = true
  try {
    const [policies, amountConfigs] = await Promise.all([
      fetchPermissionPolicies(),
      fetchSystemConfigs('amount.visibility.')
    ])
    permissionPolicies.value = policies || []
    amountVisibilityConfigs.value = amountConfigs || []
  } catch {
    permissionPolicies.value = []
    amountVisibilityConfigs.value = []
  } finally {
    policyLoading.value = false
  }
}

onMounted(loadRuntimePolicies)
</script>

<style scoped>
.panel-heading {
  align-items: center;
}
</style>
