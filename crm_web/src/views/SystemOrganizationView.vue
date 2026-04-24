<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>{{ metrics.primaryLabel }}</span>
        <strong>{{ metrics.primaryValue }}</strong>
        <small>{{ metrics.primaryHint }}</small>
      </article>
      <article class="metric-card">
        <span>{{ metrics.secondaryLabel }}</span>
        <strong>{{ metrics.secondaryValue }}</strong>
        <small>{{ metrics.secondaryHint }}</small>
      </article>
      <article class="metric-card">
        <span>{{ metrics.tertiaryLabel }}</span>
        <strong>{{ metrics.tertiaryValue }}</strong>
        <small>{{ metrics.tertiaryHint }}</small>
      </article>
    </section>

    <section v-if="currentMode === 'department'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>部门管理</h3>
          <p>设置组织架构，并约束各部门只处理自己部门内有效数据。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>部门编码</span>
          <el-input v-model="departmentForm.departmentCode" placeholder="如 CLUE" />
        </label>
        <label>
          <span>部门名称</span>
          <el-input v-model="departmentForm.departmentName" placeholder="请输入部门名称" />
        </label>
        <label>
          <span>上级部门</span>
          <el-select v-model="departmentForm.parentCode" clearable placeholder="请选择上级部门">
            <el-option v-for="item in state.departments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
          </el-select>
        </label>
        <label>
          <span>管理角色</span>
          <el-select v-model="departmentForm.managerRoleCode" placeholder="请选择管理角色">
            <el-option v-for="item in state.roles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <label class="full-span">
          <span>数据范围规则</span>
          <el-input v-model="departmentForm.dataScopeRule" placeholder="描述部门内有效数据边界" />
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="departmentForm.remark" type="textarea" :rows="3" placeholder="请输入部门备注" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveDepartment">保存部门</el-button>
        <el-button @click="resetDepartmentForm">重置表单</el-button>
      </div>

      <el-table :data="departmentPagination.rows" stripe>
        <el-table-column label="部门" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.departmentName }}</strong>
              <span>{{ row.departmentCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上级部门" min-width="160">
          <template #default="{ row }">
            {{ getDepartmentName(state.departments, row.parentCode) }}
          </template>
        </el-table-column>
        <el-table-column label="管理角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.managerRoleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="数据范围" min-width="220" prop="dataScopeRule" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickDepartment(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleDepartment(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="departmentPagination.total"
          :current-page="departmentPagination.currentPage"
          :page-size="departmentPagination.pageSize"
          :page-sizes="departmentPagination.pageSizes"
          @size-change="departmentPagination.handleSizeChange"
          @current-change="departmentPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'employee'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>员工管理</h3>
          <p>支持新增、停用与调岗；停用前会自动转移名下数据，停用员工不可登录。</p>
        </div>
      </div>

      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="employeeFilter.departmentCode" clearable placeholder="按部门筛选" style="width: 220px">
            <el-option v-for="item in availableDepartments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
          </el-select>
          <el-select v-model="employeeFilter.status" clearable placeholder="按状态筛选" style="width: 180px">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>账号</span>
          <el-input v-model="employeeForm.accountName" placeholder="请输入登录账号" />
        </label>
        <label>
          <span>姓名</span>
          <el-input v-model="employeeForm.userName" placeholder="请输入员工姓名" />
        </label>
        <label>
          <span>部门</span>
          <el-select v-model="employeeForm.departmentCode" placeholder="请选择部门">
            <el-option v-for="item in availableDepartments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
          </el-select>
        </label>
        <label>
          <span>岗位</span>
          <el-select v-model="employeeForm.positionCode" placeholder="请选择岗位">
            <el-option v-for="item in filteredPositionsForEmployeeForm" :key="item.id" :label="item.positionName" :value="item.positionCode" />
          </el-select>
        </label>
        <label>
          <span>角色</span>
          <el-select v-model="employeeForm.roleCode" placeholder="请选择角色">
            <el-option v-for="item in availableRoles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <label>
          <span>名下数据量</span>
          <el-input-number v-model="employeeForm.ownedDataCount" :min="0" controls-position="right" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveEmployee">保存员工</el-button>
        <el-button @click="resetEmployeeForm">重置表单</el-button>
      </div>

      <el-table :data="employeePagination.rows" stripe>
        <el-table-column label="员工" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.userName }}</strong>
              <span>{{ row.accountName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="140">
          <template #default="{ row }">
            {{ getDepartmentName(state.departments, row.departmentCode) }}
          </template>
        </el-table-column>
        <el-table-column label="岗位" width="160">
          <template #default="{ row }">
            {{ getPositionName(state.positions, row.positionCode) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="名下数据" width="110" prop="ownedDataCount" />
        <el-table-column label="登录状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.canLogin === 1 ? 'success' : 'info'">{{ row.canLogin === 1 ? '可登录' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickEmployee(row)">编辑</el-button>
              <el-button size="small" plain @click="moveEmployee(row)">调岗</el-button>
              <el-button size="small" type="warning" @click="toggleEmployeeStatus(row)">
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="employeePagination.total"
          :current-page="employeePagination.currentPage"
          :page-size="employeePagination.pageSize"
          :page-sizes="employeePagination.pageSizes"
          @size-change="employeePagination.handleSizeChange"
          @current-change="employeePagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'position'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>岗位管理</h3>
          <p>新增、修改、删除岗位；删除前会自动把名下员工迁移到同部门其它岗位。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>岗位编码</span>
          <el-input v-model="positionForm.positionCode" placeholder="如 CLUE_SUPERVISOR" />
        </label>
        <label>
          <span>岗位名称</span>
          <el-input v-model="positionForm.positionName" placeholder="请输入岗位名称" />
        </label>
        <label>
          <span>所属部门</span>
          <el-select v-model="positionForm.departmentCode" placeholder="请选择部门">
            <el-option v-for="item in state.departments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
          </el-select>
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="positionForm.remark" placeholder="请输入岗位说明" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="savePosition">保存岗位</el-button>
        <el-button @click="resetPositionForm">重置表单</el-button>
      </div>

      <el-table :data="positionPagination.rows" stripe>
        <el-table-column label="岗位" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.positionName }}</strong>
              <span>{{ row.positionCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="140">
          <template #default="{ row }">
            {{ getDepartmentName(state.departments, row.departmentCode) }}
          </template>
        </el-table-column>
        <el-table-column label="在岗人数" width="100">
          <template #default="{ row }">
            {{ employeesInPosition(row.positionCode).length }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220" prop="remark" />
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickPosition(row)">编辑</el-button>
              <el-button size="small" plain @click="togglePosition(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
              <el-button size="small" type="danger" @click="removePosition(row)">删除并转岗</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="positionPagination.total"
          :current-page="positionPagination.currentPage"
          :page-size="positionPagination.pageSize"
          :page-sizes="positionPagination.pageSizes"
          @size-change="positionPagination.handleSizeChange"
          @current-change="positionPagination.handleCurrentChange"
        />
      </div>
    </section>

    <template v-else>
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>角色管理</h3>
            <p>维护角色、模块权限和数据范围；原“权限中心”能力已融合到这里。</p>
          </div>
        </div>

        <div class="form-grid">
          <label>
            <span>角色编码</span>
            <el-input v-model="roleForm.roleCode" placeholder="如 STORE_SERVICE" />
          </label>
          <label>
            <span>角色名称</span>
            <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
          </label>
          <label>
            <span>数据范围</span>
            <el-select v-model="roleForm.dataScope" placeholder="请选择数据范围">
              <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label class="full-span">
            <span>模块权限</span>
            <el-select v-model="roleForm.moduleCodes" multiple placeholder="请选择模块权限">
              <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label class="full-span">
            <span>备注</span>
            <el-input v-model="roleForm.remark" placeholder="请输入角色说明" />
          </label>
        </div>

        <div class="action-group">
          <el-button type="primary" @click="saveRole">保存角色</el-button>
          <el-button @click="resetRoleForm">重置表单</el-button>
        </div>

        <el-table :data="rolePagination.rows" stripe>
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <div class="table-primary">
                <strong>{{ row.roleName }}</strong>
                <span>{{ row.roleCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="数据范围" width="120">
            <template #default="{ row }">
              {{ formatScope(row.dataScope) }}
            </template>
          </el-table-column>
          <el-table-column label="模块权限" min-width="240">
            <template #default="{ row }">
              {{ (row.moduleCodes || []).map(formatModuleCode).join(' / ') || '--' }}
            </template>
          </el-table-column>
          <el-table-column label="配置人员数" width="120">
            <template #default="{ row }">
              {{ employeesInRole(row.roleCode).length }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" @click="pickRole(row)">编辑</el-button>
                <el-button size="small" plain @click="toggleRole(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
                <el-button size="small" type="danger" @click="removeRole(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="rolePagination.total"
            :current-page="rolePagination.currentPage"
            :page-size="rolePagination.pageSize"
            :page-sizes="rolePagination.pageSizes"
            @size-change="rolePagination.handleSizeChange"
            @current-change="rolePagination.handleCurrentChange"
          />
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>角色授权策略</h3>
            <p>这里保留原“权限中心”的策略保存能力，角色和菜单的授权都从这块编排。</p>
          </div>
        </div>

        <div class="form-grid">
          <label>
            <span>模块</span>
            <el-select v-model="policyForm.moduleCode">
              <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label>
            <span>动作</span>
            <el-select v-model="policyForm.actionCode">
              <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label>
            <span>角色</span>
            <el-select v-model="policyForm.roleCode">
              <el-option v-for="item in state.roles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
            </el-select>
          </label>
          <label>
            <span>范围</span>
            <el-select v-model="policyForm.dataScope">
              <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label class="full-span">
            <span>条件规则</span>
            <el-input v-model="policyForm.conditionRule" placeholder="如 order(status=appointment)" />
          </label>
        </div>

        <div class="action-group">
          <el-button type="primary" @click="savePolicy">保存授权策略</el-button>
          <el-button @click="loadPolicies">刷新策略</el-button>
        </div>

        <el-table :data="policyPagination.rows" stripe>
          <el-table-column label="模块" width="140">
            <template #default="{ row }">
              {{ formatModuleCode(row.moduleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="动作" width="120">
            <template #default="{ row }">
              {{ formatActionCode(row.actionCode) }}
            </template>
          </el-table-column>
          <el-table-column label="角色" width="160">
            <template #default="{ row }">
              {{ formatRoleCode(row.roleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="范围" width="100">
            <template #default="{ row }">
              {{ formatScope(row.dataScope) }}
            </template>
          </el-table-column>
          <el-table-column label="条件规则" min-width="220" prop="conditionRule" />
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="policyPagination.total"
            :current-page="policyPagination.currentPage"
            :page-size="policyPagination.pageSize"
            :page-sizes="policyPagination.pageSizes"
            @size-change="policyPagination.handleSizeChange"
            @current-change="policyPagination.handleCurrentChange"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { fetchPermissionPolicies, savePermissionPolicy } from '../api/permission'
import { useTablePagination } from '../composables/useTablePagination'
import { currentUser } from '../utils/auth'
import {
  formatActionCode,
  formatModuleCode,
  formatRoleCode,
  formatScope
} from '../utils/format'
import { scopeOptions } from '../utils/permission'
import {
  filterEmployeesByRole,
  getDepartmentName,
  getManagedDepartmentCodes,
  getPositionName,
  loadSystemConsoleState,
  nextSystemId,
  saveSystemConsoleState
} from '../utils/systemConsoleStore'

const route = useRoute()
const state = reactive(loadSystemConsoleState())
const policies = ref([])

const moduleOptions = [
  { label: '客资', value: 'CLUE' },
  { label: '订单', value: 'ORDER' },
  { label: '服务单', value: 'PLANORDER' },
  { label: '薪酬', value: 'SALARY' },
  { label: '财务', value: 'FINANCE' },
  { label: '系统管理', value: 'SYSTEM' },
  { label: '系统设置', value: 'SETTING' },
  { label: '私域客服', value: 'WECOM' }
]

const actionOptions = [
  { label: '查看', value: 'VIEW' },
  { label: '创建', value: 'CREATE' },
  { label: '更新', value: 'UPDATE' },
  { label: '分配', value: 'ASSIGN' },
  { label: '回收', value: 'RECYCLE' },
  { label: '完结', value: 'FINISH' },
  { label: '触发', value: 'TRIGGER' }
]

const employeeFilter = reactive({
  departmentCode: '',
  status: ''
})

const departmentForm = reactive(createDepartmentForm())
const employeeForm = reactive(createEmployeeForm())
const positionForm = reactive(createPositionForm())
const roleForm = reactive(createRoleForm())
const policyForm = reactive({
  moduleCode: 'SYSTEM',
  actionCode: 'VIEW',
  roleCode: 'ADMIN',
  dataScope: 'ALL',
  conditionRule: ''
})

const currentMode = computed(() => route.meta.orgMode || 'department')
const isAdmin = computed(() => String(currentUser.value?.roleCode || '').trim().toUpperCase() === 'ADMIN')
const managedDepartmentCodes = computed(() => getManagedDepartmentCodes(currentUser.value?.roleCode))
const availableDepartments = computed(() => {
  if (managedDepartmentCodes.value === null) {
    return state.departments
  }
  return state.departments.filter((item) => managedDepartmentCodes.value.includes(item.departmentCode))
})
const availableRoles = computed(() => {
  if (isAdmin.value) {
    return state.roles
  }
  return state.roles.filter((item) => item.roleCode === 'ONLINE_CUSTOMER_SERVICE' || item.roleCode === 'CLUE_MANAGER')
})
const filteredEmployees = computed(() =>
  filterEmployeesByRole(state, currentUser.value?.roleCode).filter((item) => {
    if (employeeFilter.departmentCode && item.departmentCode !== employeeFilter.departmentCode) {
      return false
    }
    if (employeeFilter.status && item.status !== employeeFilter.status) {
      return false
    }
    return true
  })
)
const departmentPagination = useTablePagination(computed(() => state.departments))
const employeePagination = useTablePagination(() => filteredEmployees.value)
const positionPagination = useTablePagination(computed(() => state.positions))
const rolePagination = useTablePagination(computed(() => state.roles))
const policyPagination = useTablePagination(policies)
const filteredPositionsForEmployeeForm = computed(() => {
  const departmentCode = employeeForm.departmentCode
  return state.positions.filter((item) => item.departmentCode === departmentCode && item.isEnabled === 1)
})

const metrics = computed(() => {
  if (currentMode.value === 'department') {
    return {
      primaryLabel: '部门数量',
      primaryValue: state.departments.length,
      primaryHint: '已建立的组织部门数量',
      secondaryLabel: '启用部门',
      secondaryValue: state.departments.filter((item) => item.isEnabled === 1).length,
      secondaryHint: '当前仍在正常运作的部门',
      tertiaryLabel: '部门负责人角色',
      tertiaryValue: new Set(state.departments.map((item) => item.managerRoleCode)).size,
      tertiaryHint: '部门管理角色已经与组织架构绑定'
    }
  }
  if (currentMode.value === 'employee') {
    const employees = filteredEmployees.value
    return {
      primaryLabel: '可管理员工',
      primaryValue: employees.length,
      primaryHint: isAdmin.value ? '管理员可查看全部员工' : '部门负责人仅查看本部门员工',
      secondaryLabel: '在职人数',
      secondaryValue: employees.filter((item) => item.status === 'ACTIVE').length,
      secondaryHint: '在职员工可以继续登录和处理数据',
      tertiaryLabel: '待转移数据',
      tertiaryValue: employees.reduce((sum, item) => sum + Number(item.ownedDataCount || 0), 0),
      tertiaryHint: '停用员工前需要先处理名下数据'
    }
  }
  if (currentMode.value === 'position') {
    return {
      primaryLabel: '岗位数量',
      primaryValue: state.positions.length,
      primaryHint: '当前系统内可分配岗位数',
      secondaryLabel: '启用岗位',
      secondaryValue: state.positions.filter((item) => item.isEnabled === 1).length,
      secondaryHint: '可继续分配给在职员工的岗位',
      tertiaryLabel: '岗位承载员工',
      tertiaryValue: state.employees.filter((item) => item.status === 'ACTIVE').length,
      tertiaryHint: '岗位删除时需迁移这些员工'
    }
  }
  return {
    primaryLabel: '角色数量',
    primaryValue: state.roles.length,
    primaryHint: '系统内当前可配置角色数',
    secondaryLabel: '启用角色',
    secondaryValue: state.roles.filter((item) => item.isEnabled === 1).length,
    secondaryHint: '启用角色可继续分配给员工',
    tertiaryLabel: '授权策略',
    tertiaryValue: policies.value.length,
    tertiaryHint: '原权限中心策略已在此页管理'
  }
})

function createDepartmentForm() {
  return {
    id: null,
    departmentCode: '',
    departmentName: '',
    parentCode: '',
    managerRoleCode: 'CLUE_MANAGER',
    dataScopeRule: '',
    remark: ''
  }
}

function createEmployeeForm() {
  return {
    id: null,
    accountName: '',
    userName: '',
    departmentCode: 'CLUE',
    positionCode: '',
    roleCode: 'ONLINE_CUSTOMER_SERVICE',
    ownedDataCount: 0
  }
}

function createPositionForm() {
  return {
    id: null,
    positionCode: '',
    positionName: '',
    departmentCode: 'CLUE',
    remark: ''
  }
}

function createRoleForm() {
  return {
    id: null,
    roleCode: '',
    roleName: '',
    dataScope: 'TEAM',
    moduleCodes: [],
    remark: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetDepartmentForm() {
  Object.assign(departmentForm, createDepartmentForm())
}

function resetEmployeeForm() {
  Object.assign(employeeForm, createEmployeeForm())
}

function resetPositionForm() {
  Object.assign(positionForm, createPositionForm())
}

function resetRoleForm() {
  Object.assign(roleForm, createRoleForm())
}

function pickDepartment(row) {
  Object.assign(departmentForm, { ...row })
}

function pickEmployee(row) {
  Object.assign(employeeForm, { ...row })
}

function pickPosition(row) {
  Object.assign(positionForm, { ...row })
}

function pickRole(row) {
  Object.assign(roleForm, {
    ...row,
    moduleCodes: [...(row.moduleCodes || [])]
  })
}

function saveDepartment() {
  if (!departmentForm.departmentCode || !departmentForm.departmentName) {
    ElMessage.warning('请先填写部门编码和部门名称')
    return
  }
  const nextDepartments = [...state.departments]
  if (departmentForm.id) {
    const index = nextDepartments.findIndex((item) => item.id === departmentForm.id)
    nextDepartments[index] = { ...nextDepartments[index], ...departmentForm }
  } else {
    nextDepartments.push({
      ...departmentForm,
      id: nextSystemId(nextDepartments),
      isEnabled: 1
    })
  }
  replaceState({ ...state, departments: nextDepartments })
  ElMessage.success('部门信息已保存')
  resetDepartmentForm()
}

function toggleDepartment(row) {
  const nextDepartments = state.departments.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, departments: nextDepartments })
  ElMessage.success('部门状态已更新')
}

function saveEmployee() {
  if (!employeeForm.accountName || !employeeForm.userName || !employeeForm.departmentCode || !employeeForm.roleCode) {
    ElMessage.warning('请先完整填写员工信息')
    return
  }
  const nextEmployees = [...state.employees]
  if (employeeForm.id) {
    const index = nextEmployees.findIndex((item) => item.id === employeeForm.id)
    nextEmployees[index] = {
      ...nextEmployees[index],
      ...employeeForm
    }
  } else {
    nextEmployees.push({
      ...employeeForm,
      id: nextSystemId(nextEmployees),
      status: 'ACTIVE',
      canLogin: 1
    })
  }
  replaceState({ ...state, employees: nextEmployees })
  ElMessage.success('员工信息已保存')
  resetEmployeeForm()
}

function moveEmployee(row) {
  const candidate = state.positions.find((item) => item.departmentCode === row.departmentCode && item.positionCode !== row.positionCode)
  if (!candidate) {
    ElMessage.warning('当前部门没有其它可调岗岗位')
    return
  }
  const nextEmployees = state.employees.map((item) =>
    item.id === row.id ? { ...item, positionCode: candidate.positionCode } : item
  )
  replaceState({ ...state, employees: nextEmployees })
  ElMessage.success(`已将 ${row.userName} 调整到 ${candidate.positionName}`)
}

function toggleEmployeeStatus(row) {
  const nextEmployees = [...state.employees]
  const currentIndex = nextEmployees.findIndex((item) => item.id === row.id)
  const currentEmployee = { ...nextEmployees[currentIndex] }
  if (currentEmployee.status === 'ACTIVE') {
    if (Number(currentEmployee.ownedDataCount || 0) > 0) {
      const receiver = nextEmployees.find(
        (item) => item.id !== currentEmployee.id && item.departmentCode === currentEmployee.departmentCode && item.status === 'ACTIVE'
      )
      if (!receiver) {
        ElMessage.warning('当前部门没有可接收数据的在职员工，无法停用')
        return
      }
      receiver.ownedDataCount = Number(receiver.ownedDataCount || 0) + Number(currentEmployee.ownedDataCount || 0)
      currentEmployee.ownedDataCount = 0
      ElMessage.success(`已将名下数据转交给 ${receiver.userName}，并停用该员工`)
    } else {
      ElMessage.success('员工已停用，后续不能登录系统')
    }
    currentEmployee.status = 'DISABLED'
    currentEmployee.canLogin = 0
  } else {
    currentEmployee.status = 'ACTIVE'
    currentEmployee.canLogin = 1
    ElMessage.success('员工已重新启用')
  }
  nextEmployees[currentIndex] = currentEmployee
  replaceState({ ...state, employees: nextEmployees })
}

function savePosition() {
  if (!positionForm.positionCode || !positionForm.positionName || !positionForm.departmentCode) {
    ElMessage.warning('请先完整填写岗位信息')
    return
  }
  const nextPositions = [...state.positions]
  if (positionForm.id) {
    const index = nextPositions.findIndex((item) => item.id === positionForm.id)
    nextPositions[index] = { ...nextPositions[index], ...positionForm }
  } else {
    nextPositions.push({
      ...positionForm,
      id: nextSystemId(nextPositions),
      isEnabled: 1
    })
  }
  replaceState({ ...state, positions: nextPositions })
  ElMessage.success('岗位信息已保存')
  resetPositionForm()
}

function employeesInPosition(positionCode) {
  return state.employees.filter((item) => item.positionCode === positionCode && item.status === 'ACTIVE')
}

function togglePosition(row) {
  const nextPositions = state.positions.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, positions: nextPositions })
  ElMessage.success('岗位状态已更新')
}

function removePosition(row) {
  const attachedEmployees = employeesInPosition(row.positionCode)
  const fallbackPosition = state.positions.find(
    (item) => item.positionCode !== row.positionCode && item.departmentCode === row.departmentCode && item.isEnabled === 1
  )
  if (attachedEmployees.length && !fallbackPosition) {
    ElMessage.warning('删除岗位前，需要先准备同部门其它启用岗位承接员工')
    return
  }

  const nextEmployees = state.employees.map((item) => {
    if (item.positionCode !== row.positionCode) {
      return item
    }
    return {
      ...item,
      positionCode: fallbackPosition ? fallbackPosition.positionCode : ''
    }
  })
  const nextPositions = state.positions.filter((item) => item.id !== row.id)
  replaceState({ ...state, positions: nextPositions, employees: nextEmployees })
  ElMessage.success(attachedEmployees.length ? `岗位已删除，员工已迁移到 ${fallbackPosition.positionName}` : '岗位已删除')
}

function saveRole() {
  if (!roleForm.roleCode || !roleForm.roleName) {
    ElMessage.warning('请先填写角色编码和角色名称')
    return
  }
  const nextRoles = [...state.roles]
  if (roleForm.id) {
    const index = nextRoles.findIndex((item) => item.id === roleForm.id)
    nextRoles[index] = { ...nextRoles[index], ...roleForm }
  } else {
    nextRoles.push({
      ...roleForm,
      id: nextSystemId(nextRoles),
      isEnabled: 1
    })
  }
  replaceState({ ...state, roles: nextRoles })
  ElMessage.success('角色信息已保存')
  resetRoleForm()
}

function employeesInRole(roleCode) {
  return state.employees.filter((item) => item.roleCode === roleCode && item.status === 'ACTIVE')
}

function toggleRole(row) {
  const nextRoles = state.roles.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, roles: nextRoles })
  ElMessage.success('角色状态已更新')
}

function removeRole(row) {
  if (employeesInRole(row.roleCode).length) {
    ElMessage.warning('当前角色仍绑定在职员工，需先调整人员角色后再删除')
    return
  }
  const nextRoles = state.roles.filter((item) => item.id !== row.id)
  replaceState({ ...state, roles: nextRoles })
  ElMessage.success('角色已删除')
}

async function loadPolicies() {
  try {
    policies.value = await fetchPermissionPolicies()
  } catch {
    policies.value = []
  }
}

async function savePolicy() {
  try {
    await savePermissionPolicy({
      moduleCode: policyForm.moduleCode,
      actionCode: policyForm.actionCode,
      roleCode: policyForm.roleCode,
      dataScope: policyForm.dataScope,
      conditionRule: policyForm.conditionRule || undefined,
      isEnabled: 1
    })
    ElMessage.success('授权策略已保存')
    await loadPolicies()
  } catch {
    // HTTP 层统一处理提示
  }
}

watch(
  () => currentMode.value,
  async (mode) => {
    if (mode === 'role') {
      await loadPolicies()
    }
  },
  { immediate: true }
)

watch(
  () => employeeForm.departmentCode,
  (departmentCode) => {
    const availablePositions = state.positions.filter(
      (item) => item.departmentCode === departmentCode && item.isEnabled === 1
    )
    if (!availablePositions.some((item) => item.positionCode === employeeForm.positionCode)) {
      employeeForm.positionCode = availablePositions[0]?.positionCode || ''
    }
  },
  { immediate: true }
)
</script>
