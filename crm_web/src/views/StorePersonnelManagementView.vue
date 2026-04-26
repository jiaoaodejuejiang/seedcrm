<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>门店人员</span>
        <strong>{{ storeEmployees.length }}</strong>
      </article>
      <article class="metric-card">
        <span>可登录</span>
        <strong>{{ storeEmployees.filter((item) => item.canLogin === 1).length }}</strong>
      </article>
      <article class="metric-card">
        <span>覆盖门店</span>
        <strong>{{ uniqueStoreCount }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>门店人员管理</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="resetForm">新增人员</el-button>
        </div>
      </div>

      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="filters.storeName" clearable placeholder="按门店筛选" style="width: 180px">
            <el-option v-for="storeName in storeNames" :key="storeName" :label="storeName" :value="storeName" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="按状态筛选" style="width: 160px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>账号</span>
          <el-input v-model="form.accountName" placeholder="请输入登录账号" />
        </label>
        <label>
          <span>姓名</span>
          <el-input v-model="form.userName" placeholder="请输入姓名" />
        </label>
        <label>
          <span>门店</span>
          <el-select v-model="form.storeName" placeholder="请选择门店">
            <el-option v-for="storeName in storeNames" :key="storeName" :label="storeName" :value="storeName" />
          </el-select>
        </label>
        <label>
          <span>岗位</span>
          <el-select v-model="form.positionCode" placeholder="请选择岗位">
            <el-option v-for="item in storePositions" :key="item.id" :label="item.positionName" :value="item.positionCode" />
          </el-select>
        </label>
        <label>
          <span>角色</span>
          <el-select v-model="form.roleCode" placeholder="请选择角色">
            <el-option v-for="item in storeRoles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <label>
          <span>名下数据量</span>
          <el-input-number v-model="form.ownedDataCount" :min="0" controls-position="right" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveEmployee">保存人员</el-button>
        <el-button @click="resetForm">重置</el-button>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="姓名" min-width="140" prop="userName" />
        <el-table-column label="账号" min-width="140" prop="accountName" />
        <el-table-column label="门店" min-width="140" prop="storeName" />
        <el-table-column label="岗位" width="140">
          <template #default="{ row }">
            {{ positionName(row.positionCode) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            {{ roleName(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="登录" width="100">
          <template #default="{ row }">
            <el-tag :type="row.canLogin === 1 ? 'success' : 'info'">{{ row.canLogin === 1 ? '可登录' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickEmployee(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleEmployee(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
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
import { listStoreNames, loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const filters = reactive({
  storeName: '',
  status: ''
})

const storeEmployees = computed(() => state.employees.filter((item) => item.departmentCode === 'STORE'))
const filteredEmployees = computed(() =>
  storeEmployees.value.filter((item) => {
    if (filters.storeName && item.storeName !== filters.storeName) {
      return false
    }
    if (filters.status && item.status !== filters.status) {
      return false
    }
    return true
  })
)
const pagination = useTablePagination(filteredEmployees)
const storePositions = computed(() => state.positions.filter((item) => item.departmentCode === 'STORE'))
const storeRoles = computed(() => state.roles.filter((item) => item.dataScope === 'STORE' || item.roleCode === 'STORE_SERVICE'))
const storeNames = computed(() => listStoreNames(state))
const uniqueStoreCount = computed(() => new Set(storeEmployees.value.map((item) => item.storeName).filter(Boolean)).size)

const form = reactive(createForm())

function createForm() {
  return {
    id: null,
    accountName: '',
    userName: '',
    storeName: '',
    positionCode: 'STORE_SERVICE',
    roleCode: 'STORE_SERVICE',
    ownedDataCount: 0
  }
}

function persistState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetForm() {
  Object.assign(form, createForm())
}

function saveEmployee() {
  if (!form.accountName || !form.userName || !form.storeName || !form.positionCode || !form.roleCode) {
    ElMessage.warning('请完整填写门店人员信息')
    return
  }
  const items = [...state.employees]
  const nextItem = {
    ...form,
    id: form.id || nextSystemId(items),
    departmentCode: 'STORE',
    status: form.id ? (items.find((item) => item.id === form.id)?.status ?? 'ACTIVE') : 'ACTIVE',
    canLogin: form.id ? (items.find((item) => item.id === form.id)?.canLogin ?? 1) : 1
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
  persistState({ ...state, employees: items })
  pagination.reset()
  resetForm()
  ElMessage.success('门店人员已保存')
}

function pickEmployee(row) {
  Object.assign(form, { ...row })
}

function toggleEmployee(row) {
  persistState({
    ...state,
    employees: state.employees.map((item) =>
      item.id === row.id
        ? {
            ...item,
            status: item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
            canLogin: item.status === 'ACTIVE' ? 0 : 1
          }
        : item
    )
  })
}

function positionName(positionCode) {
  return storePositions.value.find((item) => item.positionCode === positionCode)?.positionName || '--'
}

function roleName(roleCode) {
  return storeRoles.value.find((item) => item.roleCode === roleCode)?.roleName || roleCode || '--'
}
</script>
