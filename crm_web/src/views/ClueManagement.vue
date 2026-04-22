<template>
  <div class="stack-page">
    <section class="panel">
      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="filters.sourceChannel" clearable placeholder="来源渠道" style="width: 160px">
            <el-option label="抖音" value="DOUYIN" />
            <el-option label="分销" value="DISTRIBUTOR" />
            <el-option label="其他" value="OTHER" />
          </el-select>
          <el-select v-model="filters.status" clearable placeholder="线索状态" style="width: 160px">
            <el-option label="未跟进" value="NEW" />
            <el-option label="已认领" value="ASSIGNED" />
            <el-option label="已转订单" value="CONVERTED" />
          </el-select>
          <el-button @click="loadClues">筛选</el-button>
        </div>
        <el-button type="primary" @click="createDialogVisible = true">新增线索</el-button>
      </div>

      <el-table v-loading="loading" :data="clues" stripe>
        <el-table-column label="客户信息" min-width="210">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.name || '未填写姓名' }}</strong>
              <span>{{ row.phone || row.wechat || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag>{{ formatChannel(row.sourceChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatClueStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前跟进" width="140">
          <template #default="{ row }">
            <span>{{ row.currentOwnerName || '待认领' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近订单" min-width="180">
          <template #default="{ row }">
            <span v-if="row.latestOrderId">订单 #{{ row.latestOrderId }} / {{ formatOrderStage(row.latestOrderStatus) }}</span>
            <span v-else class="text-secondary">暂无订单</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button
                v-for="staff in consultantOptions"
                :key="staff.userId"
                size="small"
                plain
                @click="handleAssign(row, staff.userId)"
              >
                分给{{ staff.userName }}
              </el-button>
              <el-button size="small" type="primary" @click="openOrderDialog(row)">转订单</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createDialogVisible" title="新增线索" width="520px">
      <el-form :model="clueForm" label-width="92px">
        <el-form-item label="客户姓名">
          <el-input v-model="clueForm.name" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="clueForm.phone" placeholder="手机号或微信至少填一项" />
        </el-form-item>
        <el-form-item label="微信号">
          <el-input v-model="clueForm.wechat" />
        </el-form-item>
        <el-form-item label="来源渠道">
          <el-select v-model="clueForm.sourceChannel" style="width: 100%">
            <el-option label="抖音" value="DOUYIN" />
            <el-option label="分销" value="DISTRIBUTOR" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道ID">
          <el-input v-model="clueForm.sourceId" placeholder="分销来源可填写分销商ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateClue">保存线索</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orderDialogVisible" title="从线索创建订单" width="520px">
      <el-form :model="orderForm" label-width="92px">
        <el-form-item label="线索">
          <el-input :model-value="selectedClueLabel" disabled />
        </el-form-item>
        <el-form-item label="订单类型">
          <el-select v-model="orderForm.type" style="width: 100%">
            <el-option label="定金单" :value="1" />
            <el-option label="团购单" :value="2" />
            <el-option label="补款单" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单金额">
          <el-input-number v-model="orderForm.amount" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="定金">
          <el-input-number v-model="orderForm.deposit" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="orderForm.remark" type="textarea" :rows="3" placeholder="如套餐说明、客服备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateOrder">创建订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assignClue, createClue, createOrder } from '../api/actions'
import { fetchClues, fetchStaffOptions } from '../api/workbench'
import {
  formatChannel,
  formatClueStatus,
  formatOrderStage,
  statusTagType
} from '../utils/format'

const loading = ref(false)
const clues = ref([])
const createDialogVisible = ref(false)
const orderDialogVisible = ref(false)
const selectedClue = ref(null)
const staffOptions = ref([])

const filters = reactive({
  sourceChannel: '',
  status: ''
})

const clueForm = reactive({
  name: '',
  phone: '',
  wechat: '',
  sourceChannel: 'DOUYIN',
  sourceId: ''
})

const orderForm = reactive({
  type: 1,
  amount: 5000,
  deposit: 1000,
  remark: ''
})

const consultantOptions = computed(() => {
  return staffOptions.value.find((item) => item.roleCode === 'CONSULTANT')?.staffOptions || []
})

const selectedClueLabel = computed(() => {
  if (!selectedClue.value) {
    return ''
  }
  return `${selectedClue.value.name || '未命名线索'} / ${selectedClue.value.phone || '--'}`
})

async function loadClues() {
  loading.value = true
  try {
    clues.value = await fetchClues({
      sourceChannel: filters.sourceChannel || undefined,
      status: filters.status || undefined
    })
  } finally {
    loading.value = false
  }
}

async function loadStaffOptions() {
  staffOptions.value = await fetchStaffOptions()
}

async function handleCreateClue() {
  await createClue({
    name: clueForm.name || undefined,
    phone: clueForm.phone || undefined,
    wechat: clueForm.wechat || undefined,
    sourceChannel: clueForm.sourceChannel,
    sourceId: clueForm.sourceId ? Number(clueForm.sourceId) : undefined
  })
  ElMessage.success('线索已创建')
  createDialogVisible.value = false
  Object.assign(clueForm, {
    name: '',
    phone: '',
    wechat: '',
    sourceChannel: 'DOUYIN',
    sourceId: ''
  })
  await loadClues()
}

async function handleAssign(row, userId) {
  await assignClue({
    clueId: row.id,
    userId
  })
  ElMessage.success('线索已认领')
  await loadClues()
}

function openOrderDialog(row) {
  selectedClue.value = row
  orderForm.type = 1
  orderForm.amount = 5000
  orderForm.deposit = 1000
  orderForm.remark = ''
  orderDialogVisible.value = true
}

async function handleCreateOrder() {
  if (!selectedClue.value) {
    return
  }

  const order = await createOrder({
    clueId: selectedClue.value.id,
    type: orderForm.type,
    amount: orderForm.amount,
    deposit: orderForm.deposit,
    remark: orderForm.remark || undefined
  })
  ElMessage.success(`订单创建成功，订单号：${order.orderNo}`)
  orderDialogVisible.value = false
  await loadClues()
}

onMounted(async () => {
  await Promise.all([loadClues(), loadStaffOptions()])
})
</script>
