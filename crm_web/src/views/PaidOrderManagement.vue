<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>已付款客资</span>
        <strong>{{ filteredOrders.length }}</strong>
        <small>这里承接所有已付款客资，由客服统一为门店安排档期。</small>
      </article>
      <article class="metric-card">
        <span>已预约</span>
        <strong>{{ appointmentCount }}</strong>
        <small>已生成预约时间的客户会同步显示到门店日历中。</small>
      </article>
      <article class="metric-card">
        <span>待排档</span>
        <strong>{{ waitingCount }}</strong>
        <small>仍未预约的已付款客资需要尽快安排门店档期。</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar-tabs">
          <el-radio-group v-model="productSourceFilter">
            <el-radio-button value="ALL">全部来源</el-radio-button>
            <el-radio-button value="GROUP_BUY">团购</el-radio-button>
            <el-radio-button value="FORM">表单</el-radio-button>
          </el-radio-group>
        </div>

        <div class="toolbar__filters">
          <el-select v-model="storeFilter" clearable placeholder="按门店筛选" style="width: 180px">
            <el-option v-for="store in storeOptions" :key="store" :label="store" :value="store" />
          </el-select>
          <el-button @click="loadOrders">刷新列表</el-button>
        </div>
      </div>

      <div class="calendar-layout">
        <div class="calendar-side">
          <div class="panel-heading compact">
            <div>
              <h3>门店排档日历</h3>
              <p>按当前门店查看已预约客户，满档日期会显示“满”，当天不可继续预约。</p>
            </div>
          </div>

          <el-calendar v-model="calendarDate" class="appointment-calendar">
            <template #date-cell="{ data }">
              <div class="calendar-cell schedule-calendar-cell">
                <span class="calendar-cell__day">{{ Number(data.day.split('-').pop()) }}</span>
                <template v-for="item in appointmentRowsByDay(data.day).slice(0, 2)" :key="`${data.day}-${item.id}`">
                  <span class="calendar-cell__event">{{ item.customerName || item.orderNo }}</span>
                </template>
                <span v-if="isDateFull(data.day)" class="calendar-cell__full">满</span>
              </div>
            </template>
          </el-calendar>
        </div>

        <div class="calendar-side">
          <div class="panel-heading compact">
            <div>
              <h3>客资排档列表</h3>
              <p>客服在这里为已付款客资安排门店档期；仅待预约订单允许提交新的排档。</p>
            </div>
          </div>

          <el-table v-loading="loading" :data="pagination.rows" stripe>
            <el-table-column label="订单" min-width="180">
              <template #default="{ row }">
                <div class="table-primary">
                  <strong>{{ row.orderNo }}</strong>
                  <span>#{{ row.id }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="顾客" min-width="160">
              <template #default="{ row }">
                <div class="table-primary">
                  <strong>{{ row.customerName || '待绑定客户' }}</strong>
                  <span>{{ row.customerPhone || '--' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="来源形式" width="110">
              <template #default="{ row }">
                <el-tag>{{ formatProductSourceType(row.productSourceType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="预约门店" min-width="130" prop="storeName" />
            <el-table-column label="金额" width="120">
              <template #default="{ row }">
                {{ formatMoney(row.amount) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="预约时间" min-width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.appointmentTime) }}
              </template>
            </el-table-column>
            <el-table-column label="门店容量" min-width="130">
              <template #default="{ row }">
                {{ storeCapacity(row.storeName) || 0 }} 档/天
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="openAppointmentDialog(row)">
                  {{ canSchedule(row) ? '预约门店档期' : '查看排档' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-pagination">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="pagination.total"
              :current-page="pagination.currentPage"
              :page-size="pagination.pageSize"
              :page-sizes="pagination.pageSizes"
              @size-change="pagination.handleSizeChange"
              @current-change="pagination.handleCurrentChange"
            />
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="appointmentDialogVisible" :title="selectedOrderCanSchedule ? '预约门店档期' : '查看排档'" width="560px">
      <el-form :model="appointmentForm" label-width="92px">
        <el-form-item label="订单">
          <el-input :model-value="selectedOrderLabel" disabled />
        </el-form-item>
        <el-form-item label="预约门店">
          <el-select
            v-model="appointmentForm.storeName"
            :disabled="!selectedOrderCanSchedule"
            style="width: 100%"
            @change="handleStoreChange"
          >
            <el-option v-for="item in storeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker
            v-model="appointmentForm.appointmentTime"
            type="datetime"
            placeholder="请选择预约时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="!selectedOrderCanSchedule"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="档位提示">
          <div class="table-note">
            当前门店 {{ appointmentForm.storeName || '--' }} 每日可排 {{ storeCapacity(appointmentForm.storeName) }} 档；
            {{ formatDate(appointmentForm.appointmentTime) || '所选日期' }} 已约
            {{ bookedCountByStoreAndDay(appointmentForm.storeName, formatDate(appointmentForm.appointmentTime), selectedOrder?.id) }} 位客户。
          </div>
        </el-form-item>
        <el-form-item label="排档备注">
          <el-input
            v-model="appointmentForm.remark"
            type="textarea"
            :rows="4"
            :readonly="!selectedOrderCanSchedule"
            placeholder="填写排档说明、到店提醒或门店注意事项"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appointmentDialogVisible = false">取消</el-button>
        <el-button v-if="selectedOrderCanSchedule" type="primary" :loading="saving" @click="handleSaveAppointment">保存排档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { appointOrder } from '../api/order'
import { fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, formatMoney, formatOrderStatus, formatProductSourceType, normalize, statusTagType } from '../utils/format'
import { calculateStoreCapacity, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const route = useRoute()
const router = useRouter()
const consoleState = reactive(loadSystemConsoleState())
const loading = ref(true)
const saving = ref(false)
const orders = ref([])
const productSourceFilter = ref('ALL')
const storeFilter = ref('')
const calendarDate = ref(new Date())
const appointmentDialogVisible = ref(false)
const selectedOrder = ref(null)
const pendingRouteOrderId = ref(Number(route.query.orderId || 0))

const appointmentForm = reactive({
  appointmentTime: '',
  remark: '',
  storeName: ''
})

const mergedOrders = computed(() =>
  orders.value.map((item) => {
    const clueProfile = (consoleState.clueConsoleProfiles || []).find((profile) => profile.clueId === item.clueId)
    return {
      ...item,
      storeName: clueProfile?.intendedStoreName || item.storeName
    }
  })
)

const filteredOrders = computed(() =>
  mergedOrders.value.filter((item) => {
    if (productSourceFilter.value !== 'ALL' && item.productSourceType !== productSourceFilter.value) {
      return false
    }
    if (storeFilter.value && item.storeName !== storeFilter.value) {
      return false
    }
    return true
  })
)

const pagination = useTablePagination(filteredOrders)
const storeOptions = computed(() => [...new Set(mergedOrders.value.map((item) => item.storeName).filter(Boolean))])
const appointmentCount = computed(() => filteredOrders.value.filter((item) => item.appointmentTime).length)
const waitingCount = computed(() => filteredOrders.value.filter((item) => !item.appointmentTime).length)
const selectedOrderCanSchedule = computed(() => canSchedule(selectedOrder.value))

const selectedOrderLabel = computed(() => {
  if (!selectedOrder.value) {
    return ''
  }
  return `${selectedOrder.value.orderNo} / ${selectedOrder.value.customerName || '待绑定客户'}`
})

function formatDate(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toISOString().slice(0, 10)
}

function appointmentRowsByDay(day) {
  return filteredOrders.value.filter((item) => item.storeName === storeFilter.value && formatDate(item.appointmentTime) === day)
}

function bookedCountByStoreAndDay(storeName, day, excludingOrderId = null) {
  if (!storeName || !day) {
    return 0
  }
  return mergedOrders.value.filter((item) => {
    if (item.storeName !== storeName || formatDate(item.appointmentTime) !== day) {
      return false
    }
    if (excludingOrderId && item.id === excludingOrderId) {
      return false
    }
    return true
  }).length
}

function storeCapacity(storeName) {
  const config = (consoleState.storeScheduleConfigs || []).find((item) => item.storeName === storeName)
  return calculateStoreCapacity(config)
}

function isDateFull(day) {
  if (!storeFilter.value || !day) {
    return false
  }
  const capacity = storeCapacity(storeFilter.value)
  if (capacity <= 0) {
    return false
  }
  return bookedCountByStoreAndDay(storeFilter.value, day) >= capacity
}

function canSchedule(row) {
  return normalize(row?.status) === 'PAID_DEPOSIT'
}

function openAppointmentDialog(row) {
  selectedOrder.value = row
  appointmentForm.appointmentTime = row.appointmentTime || ''
  appointmentForm.remark = row.remark || ''
  appointmentForm.storeName = row.storeName || storeFilter.value || storeOptions.value[0] || ''
  if (appointmentForm.storeName) {
    storeFilter.value = appointmentForm.storeName
  }
  appointmentDialogVisible.value = true
}

function handleStoreChange(value) {
  storeFilter.value = value || ''
}

async function loadOrders() {
  loading.value = true
  try {
    orders.value = await fetchOrders({
      status: 'paid'
    })
    if (!storeFilter.value && storeOptions.value.length) {
      const routeStore = String(route.query.storeName || '').trim()
      storeFilter.value = routeStore && storeOptions.value.includes(routeStore) ? routeStore : storeOptions.value[0]
    }
    pagination.reset()

    if (pendingRouteOrderId.value) {
      const matched = mergedOrders.value.find((item) => item.id === pendingRouteOrderId.value)
      if (matched) {
        openAppointmentDialog(matched)
        pendingRouteOrderId.value = 0
      }
    }
  } catch {
    orders.value = []
  } finally {
    loading.value = false
  }
}

async function handleSaveAppointment() {
  if (!selectedOrderCanSchedule.value) {
    appointmentDialogVisible.value = false
    return
  }
  if (!selectedOrder.value?.id || !appointmentForm.appointmentTime || !appointmentForm.storeName) {
    ElMessage.warning('请先选择预约门店和预约时间')
    return
  }

  const day = formatDate(appointmentForm.appointmentTime)
  const capacity = storeCapacity(appointmentForm.storeName)
  const booked = bookedCountByStoreAndDay(appointmentForm.storeName, day, selectedOrder.value.id)
  if (capacity > 0 && booked >= capacity) {
    ElMessage.warning('当前日期门店档位已满，请改约其他日期')
    return
  }

  saving.value = true
  try {
    await appointOrder({
      orderId: selectedOrder.value.id,
      appointmentTime: appointmentForm.appointmentTime,
      remark: appointmentForm.remark || undefined
    })

    if (selectedOrder.value.clueId) {
      const nextProfiles = [...(consoleState.clueConsoleProfiles || [])]
      const index = nextProfiles.findIndex((item) => item.clueId === selectedOrder.value.clueId)
      const nextProfile = {
        id: index >= 0 ? nextProfiles[index].id : Date.now(),
        clueId: selectedOrder.value.clueId,
        intendedStoreName: appointmentForm.storeName
      }
      if (index >= 0) {
        nextProfiles[index] = {
          ...nextProfiles[index],
          intendedStoreName: appointmentForm.storeName
        }
      } else {
        nextProfiles.push(nextProfile)
      }
      consoleState.clueConsoleProfiles = nextProfiles
      saveSystemConsoleState(consoleState)
    }

    ElMessage.success('门店档期已保存')
    appointmentDialogVisible.value = false
    if (route.query.orderId) {
      router.replace({ path: '/clues/scheduling', query: appointmentForm.storeName ? { storeName: appointmentForm.storeName } : {} })
    }
    await loadOrders()
  } finally {
    saving.value = false
  }
}

loadOrders()
</script>
