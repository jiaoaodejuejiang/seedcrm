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

    <section v-if="currentMode === 'role'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>薪酬角色</h3>
          <p>为薪酬计算配置角色和对应人员，后续薪酬规则统一基于角色记录计算。</p>
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
          <el-input v-model="roleForm.remark" placeholder="请输入角色说明" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveSalaryRole">保存角色</el-button>
        <el-button @click="resetRoleForm">重置表单</el-button>
      </div>

      <el-table :data="rolePagination.rows" stripe>
        <el-table-column label="薪酬角色" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.roleName }}</strong>
              <span>{{ row.roleCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="关联人员" min-width="240">
          <template #default="{ row }">
            {{ employeeNames(row.employeeIds).join(' / ') || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220" prop="remark" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickSalaryRole(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleSalaryRole(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
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

    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <h3>薪酬档位</h3>
          <p>每个节点都可以按转化节点配置档位，金额按已完成订单金额确认，带率指标使用两个节点计算。</p>
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

      <div class="action-group">
        <el-button type="primary" @click="saveSalaryGrade">保存档位</el-button>
        <el-button @click="resetGradeForm">重置表单</el-button>
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
        <el-table-column label="人效" width="120" prop="targetPeople" />
        <el-table-column label="奖励" min-width="180" prop="rewardAmount" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickSalaryGrade(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleSalaryGrade(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="gradePagination.total"
          :current-page="gradePagination.currentPage"
          :page-size="gradePagination.pageSize"
          :page-sizes="gradePagination.pageSizes"
          @size-change="gradePagination.handleSizeChange"
          @current-change="gradePagination.handleCurrentChange"
        />
      </div>
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

const roleForm = reactive(createRoleForm())
const gradeForm = reactive(createGradeForm())

const metrics = computed(() => {
  if (currentMode.value === 'role') {
    return {
      primaryLabel: '薪酬角色',
      primaryValue: state.salaryRoles.length,
      primaryHint: '参与薪酬核算的角色配置数量',
      secondaryLabel: '关联人员',
      secondaryValue: state.salaryRoles.reduce((sum, item) => sum + Number(item.employeeIds?.length || 0), 0),
      secondaryHint: '已分配到薪酬角色中的员工数',
      tertiaryLabel: '启用角色',
      tertiaryValue: state.salaryRoles.filter((item) => item.isEnabled === 1).length,
      tertiaryHint: '启用角色会参与后续薪酬计算'
    }
  }
  return {
    primaryLabel: '薪酬档位',
    primaryValue: state.salaryGrades.length,
    primaryHint: '支持个人档和团队档统一配置',
    secondaryLabel: '个人档',
    secondaryValue: state.salaryGrades.filter((item) => item.category === 'INDIVIDUAL').length,
    secondaryHint: '用于个人转化与人效奖励',
    tertiaryLabel: '团队档',
    tertiaryValue: state.salaryGrades.filter((item) => item.category === 'TEAM').length,
    tertiaryHint: '用于团队与组长奖励配置'
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

function employeeNames(employeeIds = []) {
  return availableEmployees.value
    .filter((item) => employeeIds.includes(item.id))
    .map((item) => item.userName)
}

function pickSalaryRole(row) {
  Object.assign(roleForm, {
    ...row,
    employeeIds: [...(row.employeeIds || [])]
  })
}

function saveSalaryRole() {
  if (!roleForm.roleName || !roleForm.roleCode) {
    ElMessage.warning('请先填写角色名称和角色编码')
    return
  }
  const nextItems = [...state.salaryRoles]
  const nextRow = {
    ...roleForm,
    id: roleForm.id || nextSystemId(nextItems),
    isEnabled: roleForm.id ? (nextItems.find((item) => item.id === roleForm.id)?.isEnabled ?? 1) : 1
  }
  if (roleForm.id) {
    const index = nextItems.findIndex((item) => item.id === roleForm.id)
    nextItems[index] = nextRow
  } else {
    nextItems.push(nextRow)
  }
  replaceState({
    ...state,
    salaryRoles: nextItems
  })
  rolePagination.reset()
  ElMessage.success('薪酬角色已保存')
  resetRoleForm()
}

function toggleSalaryRole(row) {
  replaceState({
    ...state,
    salaryRoles: state.salaryRoles.map((item) =>
      item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
    )
  })
  ElMessage.success('角色状态已更新')
}

function pickSalaryGrade(row) {
  Object.assign(gradeForm, { ...row })
}

function saveSalaryGrade() {
  if (!gradeForm.gradeName || !gradeForm.metricLabel || !gradeForm.startNode || !gradeForm.endNode) {
    ElMessage.warning('请先完整填写档位信息')
    return
  }
  const nextItems = [...state.salaryGrades]
  const nextRow = {
    ...gradeForm,
    id: gradeForm.id || nextSystemId(nextItems),
    isEnabled: gradeForm.id ? (nextItems.find((item) => item.id === gradeForm.id)?.isEnabled ?? 1) : 1
  }
  if (gradeForm.id) {
    const index = nextItems.findIndex((item) => item.id === gradeForm.id)
    nextItems[index] = nextRow
  } else {
    nextItems.push(nextRow)
  }
  replaceState({
    ...state,
    salaryGrades: nextItems
  })
  gradePagination.reset()
  ElMessage.success('薪酬档位已保存')
  resetGradeForm()
}

function toggleSalaryGrade(row) {
  replaceState({
    ...state,
    salaryGrades: state.salaryGrades.map((item) =>
      item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
    )
  })
  ElMessage.success('档位状态已更新')
}
</script>
