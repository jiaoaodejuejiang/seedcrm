<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>当前角色</span>
        <strong>{{ formatRoleCode(currentUser?.roleCode) }}</strong>
        <small>权限校验默认基于当前登录账号，不再使用手工上下文。</small>
      </article>
      <article class="metric-card">
        <span>数据范围</span>
        <strong>{{ formatScope(currentUser?.dataScope) }}</strong>
        <small>接口会自动注入 RBAC 与 ABAC 所需信息。</small>
      </article>
      <article class="metric-card">
        <span>策略数</span>
        <strong>{{ policies.length }}</strong>
        <small>当前后端生效中的权限策略总数。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>保存策略</h3>
          <p>维护“模块 + 动作 + 角色 + 范围”规则，作为后端统一权限源。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>模块</span>
          <el-select v-model="policyForm.moduleCode">
            <el-option v-for="option in moduleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label>
          <span>动作</span>
          <el-select v-model="policyForm.actionCode">
            <el-option v-for="option in actionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label>
          <span>角色</span>
          <el-select v-model="policyForm.roleCode">
            <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label>
          <span>范围</span>
          <el-select v-model="policyForm.dataScope">
            <el-option v-for="option in scopeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label class="full-span">
          <span>条件规则</span>
          <el-input v-model="policyForm.conditionRule" placeholder="例如：order(status=finished)" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="handleSavePolicy">保存策略</el-button>
        <el-button @click="loadPolicies">刷新列表</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>权限校验</h3>
          <p>使用当前登录用户的角色和范围，验证具体资源是否允许访问。</p>
        </div>
        <el-button text @click="syncCheckForm">同步当前登录信息</el-button>
      </div>

      <div class="form-grid">
        <label>
          <span>模块</span>
          <el-select v-model="checkForm.moduleCode">
            <el-option v-for="option in moduleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label>
          <span>动作</span>
          <el-select v-model="checkForm.actionCode">
            <el-option v-for="option in actionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <label>
          <span>角色</span>
          <el-input v-model="checkForm.roleCode" />
        </label>
        <label>
          <span>范围</span>
          <el-input v-model="checkForm.dataScope" />
        </label>
        <label>
          <span>当前用户 ID</span>
          <el-input-number v-model="checkForm.currentUserId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>资源负责人 ID</span>
          <el-input-number v-model="checkForm.resourceOwnerId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>当前门店 ID</span>
          <el-input-number v-model="checkForm.currentStoreId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>资源门店 ID</span>
          <el-input-number v-model="checkForm.resourceStoreId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>绑定客户用户 ID</span>
          <el-input-number v-model="checkForm.boundCustomerUserId" :min="1" controls-position="right" />
        </label>
        <label class="full-span">
          <span>团队成员 ID</span>
          <el-input v-model="checkForm.teamMemberIdsText" placeholder="例如：1001,1002" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="handleCheckPermission">开始校验</el-button>
      </div>

      <div v-if="checkResult" class="result-card">
        <strong>{{ checkResult.allowed ? '允许访问' : '拒绝访问' }}</strong>
        <p>命中策略：{{ checkResult.matchedPolicy || '--' }}</p>
        <p>命中范围：{{ checkResult.dataScope || '--' }}</p>
        <p>原因说明：{{ checkResult.reason || '--' }}</p>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>策略列表</h3>
          <p>直接读取后端策略表，便于核对是否与当前约束一致。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="policies" stripe>
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            {{ formatModuleCode(row.moduleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="动作" width="140">
          <template #default="{ row }">
            {{ formatActionCode(row.actionCode) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="180">
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
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkPermission, fetchPermissionPolicies, savePermissionPolicy } from '../api/permission'
import { currentUser } from '../utils/auth'
import { roleOptions, scopeOptions, parseTeamMemberIds } from '../utils/permission'
import { formatActionCode, formatModuleCode, formatRoleCode, formatScope } from '../utils/format'

const loading = ref(false)
const policies = ref([])
const checkResult = ref(null)

const moduleOptions = [
  { label: '线索', value: 'CLUE' },
  { label: '订单', value: 'ORDER' },
  { label: '服务单', value: 'PLANORDER' },
  { label: '调度', value: 'SCHEDULER' },
  { label: '权限', value: 'PERMISSION' },
  { label: '薪酬', value: 'SALARY' },
  { label: '分销', value: 'DISTRIBUTOR' },
  { label: '财务', value: 'FINANCE' }
]

const actionOptions = [
  { label: '查看', value: 'VIEW' },
  { label: '创建', value: 'CREATE' },
  { label: '更新', value: 'UPDATE' },
  { label: '分配', value: 'ASSIGN' },
  { label: '回收', value: 'RECYCLE' },
  { label: '完结', value: 'FINISH' },
  { label: '分配角色', value: 'ASSIGN_ROLE' },
  { label: '触发', value: 'TRIGGER' },
  { label: '校验', value: 'CHECK' }
]

const policyForm = reactive({
  moduleCode: 'ORDER',
  actionCode: 'VIEW',
  roleCode: 'STORE_SERVICE',
  dataScope: 'STORE',
  conditionRule: ''
})

const checkForm = reactive({
  moduleCode: 'PLANORDER',
  actionCode: 'VIEW',
  roleCode: '',
  dataScope: '',
  currentUserId: null,
  resourceOwnerId: null,
  currentStoreId: null,
  resourceStoreId: null,
  boundCustomerUserId: null,
  teamMemberIdsText: ''
})

function syncCheckForm() {
  checkForm.roleCode = currentUser.value?.roleCode || ''
  checkForm.dataScope = currentUser.value?.dataScope || ''
  checkForm.currentUserId = currentUser.value?.userId || null
  checkForm.currentStoreId = currentUser.value?.storeId || null
  checkForm.resourceStoreId = currentUser.value?.storeId || null
  checkForm.boundCustomerUserId = currentUser.value?.boundCustomerUserId || null
  checkForm.teamMemberIdsText = (currentUser.value?.teamMemberIds || []).join(',')
}

async function loadPolicies() {
  loading.value = true
  try {
    policies.value = await fetchPermissionPolicies()
  } catch {
    policies.value = []
  } finally {
    loading.value = false
  }
}

async function handleSavePolicy() {
  try {
    await savePermissionPolicy({
      moduleCode: policyForm.moduleCode,
      actionCode: policyForm.actionCode,
      roleCode: policyForm.roleCode,
      dataScope: policyForm.dataScope,
      conditionRule: policyForm.conditionRule || undefined,
      isEnabled: 1
    })
    ElMessage.success('权限策略已保存')
    await loadPolicies()
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

async function handleCheckPermission() {
  try {
    checkResult.value = await checkPermission({
      moduleCode: checkForm.moduleCode,
      actionCode: checkForm.actionCode,
      roleCode: checkForm.roleCode,
      dataScope: checkForm.dataScope || undefined,
      currentUserId: checkForm.currentUserId || undefined,
      resourceOwnerId: checkForm.resourceOwnerId || undefined,
      currentStoreId: checkForm.currentStoreId || undefined,
      resourceStoreId: checkForm.resourceStoreId || undefined,
      boundCustomerUserId: checkForm.boundCustomerUserId || undefined,
      teamMemberIds: parseTeamMemberIds(checkForm.teamMemberIdsText)
    })
  } catch {
    checkResult.value = null
  }
}

onMounted(async () => {
  syncCheckForm()
  await loadPolicies()
})
</script>
