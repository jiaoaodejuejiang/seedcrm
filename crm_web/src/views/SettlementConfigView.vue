<template>
  <div class="stack-page">
    <section class="summary-strip">
      <article class="summary-pill">
        <span>结算规则</span>
        <strong>{{ state.salarySettlementRules.length }}</strong>
      </article>
      <article class="summary-pill">
        <span>角色规则</span>
        <strong>{{ state.salarySettlementRules.filter((item) => item.scopeType === 'ROLE').length }}</strong>
      </article>
      <article class="summary-pill">
        <span>金额规则</span>
        <strong>{{ state.salarySettlementRules.filter((item) => item.scopeType === 'AMOUNT').length }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>结算配置</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>规则名称</span>
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </label>
        <label>
          <span>匹配方式</span>
          <el-select v-model="ruleForm.scopeType">
            <el-option label="按角色" value="ROLE" />
            <el-option label="按金额" value="AMOUNT" />
          </el-select>
        </label>
        <label v-if="ruleForm.scopeType === 'ROLE'" class="full-span">
          <span>薪酬角色</span>
          <el-select v-model="ruleForm.roleCodes" multiple placeholder="请选择薪酬角色">
            <el-option v-for="item in state.salaryRoles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
        <template v-else>
          <label>
            <span>最低金额</span>
            <el-input-number v-model="ruleForm.amountMin" :min="0" :precision="2" controls-position="right" />
          </label>
          <label>
            <span>最高金额</span>
            <el-input v-model="ruleForm.amountMax" placeholder="留空表示不上限" />
          </label>
        </template>
        <label>
          <span>结算方式</span>
          <el-select v-model="ruleForm.settlementMode">
            <el-option label="提现审核" value="WITHDRAW_AUDIT" />
            <el-option label="提现不审核" value="WITHDRAW_DIRECT" />
            <el-option label="只记账" value="LEDGER_ONLY" />
          </el-select>
        </label>
        <label class="full-span">
          <span>说明</span>
          <el-input v-model="ruleForm.remark" placeholder="请输入规则说明" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveRule">保存规则</el-button>
        <el-button @click="resetRuleForm">重置</el-button>
      </div>

      <el-table :data="pagination.rows" stripe>
        <el-table-column label="规则" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.ruleName }}</strong>
              <span>{{ row.scopeType === 'ROLE' ? '按角色' : '按金额' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="匹配条件" min-width="220">
          <template #default="{ row }">
            <span v-if="row.scopeType === 'ROLE'">
              {{ (row.roleCodes || []).map(formatRoleCode).join(' / ') || '--' }}
            </span>
            <span v-else>
              {{ formatMoney(row.amountMin || 0) }} ~ {{ row.amountMax === '' ? '不限' : formatMoney(row.amountMax || 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结算方式" width="140">
          <template #default="{ row }">
            {{ formatSettlementMode(row.settlementMode) }}
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
              <el-button size="small" @click="pickRule(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleRule(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
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
import { formatMoney, formatRoleCode, formatSettlementMode } from '../utils/format'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const pagination = useTablePagination(computed(() => state.salarySettlementRules || []))
const ruleForm = reactive(createRuleForm())

function createRuleForm() {
  return {
    id: null,
    ruleName: '',
    scopeType: 'ROLE',
    roleCodes: [],
    amountMin: 0,
    amountMax: '',
    settlementMode: 'WITHDRAW_AUDIT',
    remark: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetRuleForm() {
  Object.assign(ruleForm, createRuleForm())
}

function pickRule(row) {
  Object.assign(ruleForm, {
    ...row,
    roleCodes: [...(row.roleCodes || [])]
  })
}

function toggleRule(row) {
  replaceState({
    ...state,
    salarySettlementRules: state.salarySettlementRules.map((item) =>
      item.id === row.id ? { ...item, enabled: item.enabled === 1 ? 0 : 1 } : item
    )
  })
  ElMessage.success('规则状态已更新')
}

function saveRule() {
  if (!ruleForm.ruleName) {
    ElMessage.warning('请先填写规则名称')
    return
  }
  if (ruleForm.scopeType === 'ROLE' && !(ruleForm.roleCodes || []).length) {
    ElMessage.warning('请选择至少一个薪酬角色')
    return
  }
  const items = [...state.salarySettlementRules]
  const nextRow = {
    ...ruleForm,
    id: ruleForm.id || nextSystemId(items),
    enabled: ruleForm.id ? (items.find((item) => item.id === ruleForm.id)?.enabled ?? 1) : 1
  }
  if (ruleForm.id) {
    items.splice(
      items.findIndex((item) => item.id === ruleForm.id),
      1,
      nextRow
    )
  } else {
    items.push(nextRow)
  }
  replaceState({
    ...state,
    salarySettlementRules: items
  })
  pagination.reset()
  resetRuleForm()
  ElMessage.success('结算规则已保存')
}
</script>
