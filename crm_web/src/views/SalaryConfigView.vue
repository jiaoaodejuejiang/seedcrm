<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>{{ metrics.primaryLabel }}</span>
        <strong>{{ metrics.primaryValue }}</strong>
      </article>
      <article class="metric-card">
        <span>{{ metrics.secondaryLabel }}</span>
        <strong>{{ metrics.secondaryValue }}</strong>
      </article>
      <article class="metric-card">
        <span>{{ metrics.tertiaryLabel }}</span>
        <strong>{{ metrics.tertiaryValue }}</strong>
      </article>
    </section>

    <section v-if="currentMode === 'role'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>薪酬角色</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>角色名称</span>
          <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
        </label>
        <label>
          <span>角色编码</span>
          <el-input v-model="roleForm.roleCode" placeholder="如 NORMAL_CS" />
        </label>
        <label class="full-span">
          <span>角色人员</span>
          <el-select v-model="roleForm.employeeIds" multiple collapse-tags collapse-tags-tooltip placeholder="请选择员工">
            <el-option
              v-for="employee in availableEmployees"
              :key="employee.id"
              :label="`${employee.userName} / ${employee.accountName}`"
              :value="employee.id"
            />
          </el-select>
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="roleForm.remark" placeholder="请输入备注" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveSalaryRole">保存角色</el-button>
        <el-button @click="resetRoleForm">重置</el-button>
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
        <el-table-column label="关联人员" min-width="220">
          <template #default="{ row }">
            {{ employeeNames(row.employeeIds).join(' / ') || '--' }}
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
              <el-button size="small" @click="pickSalaryRole(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleSalaryRole(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else-if="currentMode === 'grade'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>薪酬档位</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>档位名称</span>
          <el-input v-model="gradeForm.gradeName" placeholder="请输入档位名称" />
        </label>
        <label>
          <span>档位类型</span>
          <el-select v-model="gradeForm.category">
            <el-option label="个人档" value="INDIVIDUAL" />
            <el-option label="团队档" value="TEAM" />
          </el-select>
        </label>
        <label>
          <span>统计指标</span>
          <el-input v-model="gradeForm.metricLabel" placeholder="如 到店率 / 有效到店率" />
        </label>
        <label>
          <span>开始节点</span>
          <el-input v-model="gradeForm.startNode" placeholder="如 已付款" />
        </label>
        <label>
          <span>结束节点</span>
          <el-input v-model="gradeForm.endNode" placeholder="如 已到店 / 已完成" />
        </label>
        <label>
          <span>目标转化率</span>
          <el-input v-model="gradeForm.targetRate" placeholder="如 78%" />
        </label>
        <label>
          <span>人效目标</span>
          <el-input v-model="gradeForm.targetPeople" placeholder="如 160人" />
        </label>
        <label class="full-span">
          <span>奖励金额</span>
          <el-input v-model="gradeForm.rewardAmount" placeholder="如 160元/人 或 组长团队优秀奖 1200元" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveSalaryGrade">保存档位</el-button>
        <el-button @click="resetGradeForm">重置</el-button>
      </div>

      <el-table :data="gradePagination.rows" stripe>
        <el-table-column label="档位" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.gradeName }}</strong>
              <span>{{ row.category === 'TEAM' ? '团队档' : '个人档' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="指标" width="140" prop="metricLabel" />
        <el-table-column label="节点" min-width="180">
          <template #default="{ row }">
            {{ row.startNode }} → {{ row.endNode }}
          </template>
        </el-table-column>
        <el-table-column label="目标" width="120" prop="targetRate" />
        <el-table-column label="奖励" min-width="160" prop="rewardAmount" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickSalaryGrade(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleSalaryGrade(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <h3>分销配置</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>配置名称</span>
          <el-input v-model="distributorForm.configName" placeholder="请输入分销配置名称" />
        </label>
        <label>
          <span>产品类型</span>
          <el-select v-model="distributorForm.productType">
            <el-option label="定金" value="deposit" />
            <el-option label="团购券" value="coupon" />
          </el-select>
        </label>
        <label>
          <span>结算节点</span>
          <el-input v-model="distributorForm.orderStage" placeholder="如 到店核销 / 订单完成" />
        </label>
        <label>
          <span>提成比例</span>
          <el-input-number v-model="distributorForm.commissionRate" :precision="2" :step="0.01" :min="0" :max="1" />
        </label>
        <label>
          <span>结算口径</span>
          <el-select v-model="distributorForm.settlementBase">
            <el-option label="完成金额" value="completed_amount" />
            <el-option label="支付金额" value="paid_amount" />
          </el-select>
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="distributorForm.remark" placeholder="请输入配置说明" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveDistributorConfig">保存配置</el-button>
        <el-button @click="resetDistributorForm">重置</el-button>
      </div>

      <el-table :data="distributorPagination.rows" stripe>
        <el-table-column label="配置" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.configName }}</strong>
              <span>{{ row.productType === 'coupon' ? '团购券' : '定金' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结算节点" min-width="160" prop="orderStage" />
        <el-table-column label="提成比例" width="120">
          <template #default="{ row }">
            {{ Number(row.commissionRate || 0) * 100 }}%
          </template>
        </el-table-column>
        <el-table-column label="结算口径" width="140">
          <template #default="{ row }">
            {{ formatSettlementBase(row.settlementBase) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickDistributor(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleDistributor(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
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
import { useRoute } from 'vue-router'
import { useTablePagination } from '../composables/useTablePagination'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const route = useRoute()
const state = reactive(loadSystemConsoleState())

const currentMode = computed(() => route.meta.salaryConfigMode || 'role')
const availableEmployees = computed(() => state.employees.filter((item) => item.status === 'ACTIVE' && item.canLogin === 1))
const rolePagination = useTablePagination(computed(() => state.salaryRoles))
const gradePagination = useTablePagination(computed(() => state.salaryGrades))
const distributorPagination = useTablePagination(computed(() => state.distributorConfigs))

const roleForm = reactive(createRoleForm())
const gradeForm = reactive(createGradeForm())
const distributorForm = reactive(createDistributorForm())

const metrics = computed(() => {
  if (currentMode.value === 'role') {
    return {
      primaryLabel: '薪酬角色',
      primaryValue: state.salaryRoles.length,
      secondaryLabel: '关联人员',
      secondaryValue: state.salaryRoles.reduce((sum, item) => sum + Number(item.employeeIds?.length || 0), 0),
      tertiaryLabel: '启用角色',
      tertiaryValue: state.salaryRoles.filter((item) => item.isEnabled === 1).length
    }
  }
  if (currentMode.value === 'grade') {
    return {
      primaryLabel: '薪酬档位',
      primaryValue: state.salaryGrades.length,
      secondaryLabel: '个人档',
      secondaryValue: state.salaryGrades.filter((item) => item.category === 'INDIVIDUAL').length,
      tertiaryLabel: '团队档',
      tertiaryValue: state.salaryGrades.filter((item) => item.category === 'TEAM').length
    }
  }
  return {
    primaryLabel: '分销配置',
    primaryValue: state.distributorConfigs.length,
    secondaryLabel: '团购券配置',
    secondaryValue: state.distributorConfigs.filter((item) => item.productType === 'coupon').length,
    tertiaryLabel: '定金配置',
    tertiaryValue: state.distributorConfigs.filter((item) => item.productType === 'deposit').length
  }
})

function createRoleForm() {
  return {
    id: null,
    roleName: '',
    roleCode: '',
    employeeIds: [],
    remark: ''
  }
}

function createGradeForm() {
  return {
    id: null,
    category: 'INDIVIDUAL',
    gradeName: '',
    metricLabel: '',
    startNode: '',
    endNode: '',
    targetRate: '',
    targetPeople: '',
    rewardAmount: ''
  }
}

function createDistributorForm() {
  return {
    id: null,
    configName: '',
    productType: 'coupon',
    orderStage: '',
    commissionRate: 0.1,
    settlementBase: 'completed_amount',
    remark: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetRoleForm() {
  Object.assign(roleForm, createRoleForm())
}

function resetGradeForm() {
  Object.assign(gradeForm, createGradeForm())
}

function resetDistributorForm() {
  Object.assign(distributorForm, createDistributorForm())
}

function employeeNames(employeeIds = []) {
  return availableEmployees.value.filter((item) => employeeIds.includes(item.id)).map((item) => item.userName)
}

function pickSalaryRole(row) {
  Object.assign(roleForm, {
    ...row,
    employeeIds: [...(row.employeeIds || [])]
  })
}

function saveSalaryRole() {
  if (!roleForm.roleName || !roleForm.roleCode) {
    ElMessage.warning('请完整填写角色名称和角色编码')
    return
  }
  const nextItems = [...state.salaryRoles]
  const nextRow = {
    ...roleForm,
    id: roleForm.id || nextSystemId(nextItems),
    isEnabled: roleForm.id ? (nextItems.find((item) => item.id === roleForm.id)?.isEnabled ?? 1) : 1
  }
  if (roleForm.id) {
    nextItems.splice(
      nextItems.findIndex((item) => item.id === roleForm.id),
      1,
      nextRow
    )
  } else {
    nextItems.push(nextRow)
  }
  replaceState({ ...state, salaryRoles: nextItems })
  rolePagination.reset()
  resetRoleForm()
  ElMessage.success('薪酬角色已保存')
}

function toggleSalaryRole(row) {
  replaceState({
    ...state,
    salaryRoles: state.salaryRoles.map((item) =>
      item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
    )
  })
}

function pickSalaryGrade(row) {
  Object.assign(gradeForm, { ...row })
}

function saveSalaryGrade() {
  if (!gradeForm.gradeName || !gradeForm.metricLabel || !gradeForm.startNode || !gradeForm.endNode) {
    ElMessage.warning('请完整填写档位信息')
    return
  }
  const nextItems = [...state.salaryGrades]
  const nextRow = {
    ...gradeForm,
    id: gradeForm.id || nextSystemId(nextItems),
    isEnabled: gradeForm.id ? (nextItems.find((item) => item.id === gradeForm.id)?.isEnabled ?? 1) : 1
  }
  if (gradeForm.id) {
    nextItems.splice(
      nextItems.findIndex((item) => item.id === gradeForm.id),
      1,
      nextRow
    )
  } else {
    nextItems.push(nextRow)
  }
  replaceState({ ...state, salaryGrades: nextItems })
  gradePagination.reset()
  resetGradeForm()
  ElMessage.success('薪酬档位已保存')
}

function toggleSalaryGrade(row) {
  replaceState({
    ...state,
    salaryGrades: state.salaryGrades.map((item) =>
      item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
    )
  })
}

function pickDistributor(row) {
  Object.assign(distributorForm, { ...row })
}

function saveDistributorConfig() {
  if (!distributorForm.configName || !distributorForm.orderStage) {
    ElMessage.warning('请完整填写分销配置')
    return
  }
  const nextItems = [...state.distributorConfigs]
  const nextRow = {
    ...distributorForm,
    id: distributorForm.id || nextSystemId(nextItems),
    enabled: distributorForm.id ? (nextItems.find((item) => item.id === distributorForm.id)?.enabled ?? 1) : 1
  }
  if (distributorForm.id) {
    nextItems.splice(
      nextItems.findIndex((item) => item.id === distributorForm.id),
      1,
      nextRow
    )
  } else {
    nextItems.push(nextRow)
  }
  replaceState({ ...state, distributorConfigs: nextItems })
  distributorPagination.reset()
  resetDistributorForm()
  ElMessage.success('分销配置已保存')
}

function toggleDistributor(row) {
  replaceState({
    ...state,
    distributorConfigs: state.distributorConfigs.map((item) =>
      item.id === row.id ? { ...item, enabled: item.enabled === 1 ? 0 : 1 } : item
    )
  })
}

function formatSettlementBase(value) {
  return value === 'paid_amount' ? '支付金额' : '完成金额'
}
</script>
