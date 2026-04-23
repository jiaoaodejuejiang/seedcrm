<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>客资总数</span>
        <strong>{{ clues.length }}</strong>
        <small>客资统一从同一入口进入系统，并保留原始数据便于追溯。</small>
      </article>
      <article class="metric-card">
        <span>待分配</span>
        <strong>{{ pendingCount }}</strong>
        <small>这些客资仍在公海或尚未进入后续跟进环节。</small>
      </article>
      <article class="metric-card">
        <span>已转化</span>
        <strong>{{ convertedCount }}</strong>
        <small>已转化客资会继续进入客户、订单和服务单主链。</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="filters.sourceChannel" clearable placeholder="来源渠道" style="width: 160px">
            <el-option label="抖音" value="DOUYIN" />
            <el-option label="分销" value="DISTRIBUTION" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="客资状态" style="width: 160px">
            <el-option label="新客资" value="NEW" />
            <el-option label="已分配" value="ASSIGNED" />
            <el-option label="跟进中" value="FOLLOWING" />
            <el-option label="已转化" value="CONVERTED" />
          </el-select>
          <el-button @click="loadClues">筛选</el-button>
        </div>

        <div class="action-group">
          <span class="text-secondary">系统已接入自动拉取客资，本页每 15 秒自动刷新一次。</span>
          <el-button v-if="canCreateClue" type="primary" @click="createDialogVisible = true">新增客资</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="clues" stripe>
        <el-table-column label="客户信息" min-width="220">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.name || '未命名客资' }}</strong>
              <span>{{ row.phone || row.wechat || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag>{{ formatChannel(row.sourceChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatClueStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前负责人" min-width="160">
          <template #default="{ row }">
            <span>{{ row.currentOwnerName || '公海池' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近订单" min-width="180">
          <template #default="{ row }">
            <span v-if="row.latestOrderId">#{{ row.latestOrderId }} / {{ formatOrderStage(row.latestOrderStatus) }}</span>
            <span v-else class="text-secondary">尚未转订单</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="340" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-dropdown v-if="canAssignClue" split-button type="primary" @click="openAssignDialog(row)">
                分配
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="staff in assignableStaff"
                      :key="staff.userId"
                      @click="handleAssign(row, staff.userId)"
                    >
                      分配给 {{ staff.userName }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button v-if="canRecycleClue" size="small" plain :disabled="!row.currentOwnerId" @click="handleRecycle(row)">
                回收
              </el-button>
              <el-button v-if="canCreateOrderFromClue" size="small" type="success" @click="openOrderDialog(row)">转订单</el-button>
              <span v-if="!canAssignClue && !canRecycleClue && !canCreateOrderFromClue" class="text-secondary">当前角色仅可查看</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增客资" width="560px">
      <el-form :model="clueForm" label-width="92px">
        <el-form-item label="姓名">
          <el-input v-model="clueForm.name" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="clueForm.phone" placeholder="手机号和微信至少填写一个" />
        </el-form-item>
        <el-form-item label="微信">
          <el-input v-model="clueForm.wechat" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="clueForm.sourceChannel" style="width: 100%">
            <el-option label="抖音" value="DOUYIN" />
            <el-option label="分销" value="DISTRIBUTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源 ID">
          <el-input v-model="clueForm.sourceId" placeholder="外部来源 ID 或分销商 ID" />
        </el-form-item>
        <el-form-item label="原始数据">
          <el-input
            v-model="clueForm.rawData"
            type="textarea"
            :rows="4"
            placeholder="建议保留上游返回的 JSON 文本，便于后续追溯。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateClue">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" title="选择分配客服" width="520px">
      <div class="quick-button-row">
        <el-button
          v-for="staff in assignableStaff"
          :key="staff.userId"
          type="primary"
          plain
          @click="handleAssign(assignTarget, staff.userId)"
        >
          {{ staff.userName }}
        </el-button>
      </div>
      <template #footer>
        <el-button @click="assignDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="从客资创建订单" width="560px">
      <el-form :model="orderForm" label-width="92px">
        <el-form-item label="客资">
          <el-input :model-value="selectedClueLabel" disabled />
        </el-form-item>
        <el-form-item label="订单类型">
          <el-select v-model="orderForm.type" style="width: 100%">
            <el-option label="定金" :value="1" />
            <el-option label="卡券" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单金额">
          <el-input-number v-model="orderForm.amount" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="定金金额">
          <el-input-number
            v-model="orderForm.deposit"
            :min="0"
            :precision="2"
            :disabled="orderForm.type === 2"
            style="width: 100%"
          />
          <div class="table-note">卡券订单会自动按全款处理；定金订单可继续进入预约和服务主链。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="orderForm.remark" type="textarea" :rows="3" placeholder="填写转化说明或订单备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateOrder">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assignClue, createClue, createOrder, recycleClue } from '../api/actions'
import { fetchDutyCustomerServices } from '../api/clueManagement'
import { fetchClues } from '../api/workbench'
import { currentUser, hasAccess } from '../utils/auth'
import { formatChannel, formatClueStatus, formatDateTime, formatOrderStage, normalize, statusTagType } from '../utils/format'

const loading = ref(true)
const clues = ref([])
const dutyStaff = ref([])
const createDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const orderDialogVisible = ref(false)
const assignTarget = ref(null)
const selectedClue = ref(null)
let refreshTimer = null

const filters = reactive({
  sourceChannel: '',
  status: ''
})

const clueForm = reactive({
  name: '',
  phone: '',
  wechat: '',
  sourceChannel: 'DOUYIN',
  sourceId: '',
  rawData: ''
})

const orderForm = reactive({
  type: 1,
  amount: 1999,
  deposit: 199,
  remark: ''
})

const roleCode = computed(() => currentUser.value?.roleCode || '')
const assignableStaff = computed(() => dutyStaff.value.filter((item) => item.onLeave !== 1))
const canCreateClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(roleCode.value))
const canAssignClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(roleCode.value))
const canRecycleClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(roleCode.value))
const canCreateOrderFromClue = computed(() => hasAccess('ORDER'))
const pendingCount = computed(() => clues.value.filter((item) => normalize(item.status) === 'NEW').length)
const convertedCount = computed(() => clues.value.filter((item) => normalize(item.status) === 'CONVERTED').length)

const selectedClueLabel = computed(() => {
  if (!selectedClue.value) {
    return ''
  }
  return `${selectedClue.value.name || '未命名客资'} / ${selectedClue.value.phone || selectedClue.value.wechat || '--'}`
})

async function loadClues() {
  loading.value = true
  try {
    clues.value = await fetchClues({
      sourceChannel: filters.sourceChannel || undefined,
      status: filters.status || undefined
    })
  } catch {
    clues.value = []
  } finally {
    loading.value = false
  }
}

async function loadDutyStaff() {
  try {
    dutyStaff.value = await fetchDutyCustomerServices()
  } catch {
    dutyStaff.value = []
  }
}

async function handleCreateClue() {
  await createClue({
    name: clueForm.name || undefined,
    phone: clueForm.phone || undefined,
    wechat: clueForm.wechat || undefined,
    sourceChannel: clueForm.sourceChannel,
    sourceId: clueForm.sourceId ? Number(clueForm.sourceId) : undefined,
    rawData: clueForm.rawData || undefined
  })
  ElMessage.success('客资已创建')
  createDialogVisible.value = false
  Object.assign(clueForm, {
    name: '',
    phone: '',
    wechat: '',
    sourceChannel: 'DOUYIN',
    sourceId: '',
    rawData: ''
  })
  await loadClues()
}

function openAssignDialog(row) {
  assignTarget.value = row
  assignDialogVisible.value = true
}

async function handleAssign(row, userId) {
  if (!row?.id) {
    return
  }
  await assignClue({
    clueId: row.id,
    userId
  })
  assignDialogVisible.value = false
  ElMessage.success('客资已分配')
  await loadClues()
}

async function handleRecycle(row) {
  await recycleClue(row.id)
  ElMessage.success('客资已回收到公海')
  await loadClues()
}

function openOrderDialog(row) {
  selectedClue.value = row
  orderForm.type = 1
  orderForm.amount = 1999
  orderForm.deposit = 199
  orderForm.remark = ''
  orderDialogVisible.value = true
}

async function handleCreateOrder() {
  if (!selectedClue.value) {
    return
  }

  const payload = {
    clueId: selectedClue.value.id,
    type: orderForm.type,
    amount: orderForm.amount,
    remark: orderForm.remark || undefined
  }
  if (orderForm.type === 1) {
    payload.deposit = orderForm.deposit
  }

  const order = await createOrder(payload)
  ElMessage.success(`订单已创建：${order.orderNo}`)
  orderDialogVisible.value = false
  await loadClues()
}

onMounted(async () => {
  await Promise.all([loadClues(), loadDutyStaff()])
  refreshTimer = window.setInterval(loadClues, 15000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})
</script>
