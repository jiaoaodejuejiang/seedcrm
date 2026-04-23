<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>策略状态</span>
        <strong>{{ strategy.enabled === 1 ? '已启用' : '已停用' }}</strong>
        <small>启用后，新进入系统的客资会自动轮询分配给当值客服。</small>
      </article>
      <article class="metric-card">
        <span>当值客服</span>
        <strong>{{ onDutyCount }}</strong>
        <small>仅当值且未请假的客服会进入自动分配池。</small>
      </article>
      <article class="metric-card">
        <span>上次轮询</span>
        <strong>{{ lastAssignedLabel }}</strong>
        <small>自动分配会以上次分配的客服为游标继续轮询。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>自动分配策略</h3>
          <p>V1 固定为“自动轮询当值客服”，不接入外部模型，也不做复杂条件编排。</p>
        </div>
      </div>

      <div class="detail-grid">
        <article class="detail-card">
          <h3>开关状态</h3>
          <el-switch
            v-model="strategyEnabled"
            inline-prompt
            active-text="启用"
            inactive-text="停用"
          />
          <p class="table-note">关闭后，新客资入库时不会执行自动轮询分配。</p>
        </article>
        <article class="detail-card">
          <h3>分配模式</h3>
          <p>当前模式：自动轮询当值客服</p>
          <p>模式编码：{{ strategy.assignmentMode || 'ROUND_ROBIN' }}</p>
        </article>
        <article class="detail-card">
          <h3>策略更新时间</h3>
          <p>{{ formatDateTime(strategy.updatedAt) }}</p>
          <p>更新人：{{ strategy.updatedBy || '--' }}</p>
        </article>
      </div>

      <div class="action-group">
        <el-button type="primary" :loading="saving" @click="handleSave">保存策略</el-button>
        <el-button @click="loadData">刷新数据</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>当前参与轮询的客服</h3>
          <p>值班名单来源于“值班客服”配置页，这里只展示当前自动分配会用到的人员。</p>
        </div>
      </div>

      <el-table :data="dutyStaff" stripe>
        <el-table-column label="客服" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.userName }}</strong>
              <span>{{ row.accountName || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="班次" min-width="180" prop="shiftLabel" />
        <el-table-column label="当值" width="100">
          <template #default="{ row }">
            <el-tag :type="row.onDuty === 1 ? 'success' : 'info'">{{ row.onDuty === 1 ? '当值' : '休息' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="请假" width="100">
          <template #default="{ row }">
            <el-tag :type="row.onLeave === 1 ? 'warning' : 'success'">{{ row.onLeave === 1 ? '请假中' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="200" prop="remark" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAssignmentStrategy, fetchDutyCustomerServices, saveAssignmentStrategy } from '../api/clueManagement'
import { formatDateTime } from '../utils/format'

const strategy = ref({
  enabled: 1,
  assignmentMode: 'ROUND_ROBIN',
  lastAssignedUserId: null,
  updatedAt: null,
  updatedBy: null
})
const dutyStaff = ref([])
const saving = ref(false)
const strategyEnabled = ref(true)

const onDutyCount = computed(() => dutyStaff.value.filter((item) => item.onDuty === 1 && item.onLeave !== 1).length)
const lastAssignedLabel = computed(() => {
  const current = dutyStaff.value.find((item) => item.userId === strategy.value.lastAssignedUserId)
  return current?.userName || '--'
})

async function loadData() {
  const [strategyResponse, dutyStaffResponse] = await Promise.all([fetchAssignmentStrategy(), fetchDutyCustomerServices()])
  strategy.value = strategyResponse || strategy.value
  dutyStaff.value = dutyStaffResponse || []
  strategyEnabled.value = strategy.value.enabled === 1
}

async function handleSave() {
  saving.value = true
  try {
    strategy.value = await saveAssignmentStrategy({
      enabled: strategyEnabled.value ? 1 : 0,
      assignmentMode: 'ROUND_ROBIN'
    })
    strategyEnabled.value = strategy.value.enabled === 1
    ElMessage.success('自动分配策略已保存')
  } finally {
    saving.value = false
  }
}

loadData()
</script>
