<template>
  <div class="stack-page paid-order-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>付款订单</span>
        <strong>{{ schedulingOrders.length }}</strong>
        <small>这里只展示已付款且仍需预约排档的订单。</small>
      </article>
      <article class="metric-card">
        <span>已预约</span>
        <strong>{{ appointmentCount }}</strong>
        <small>已预约客户可以继续更改档期，也能在门店日历里查看。</small>
      </article>
      <article class="metric-card">
        <span>未预约</span>
        <strong>{{ waitingCount }}</strong>
        <small>默认优先展示未预约客户，方便客服尽快排入门店空档。</small>
      </article>
    </section>

    <section class="panel">
      <div class="toolbar">
        <div class="toolbar-tabs">
          <el-radio-group v-model="productSourceFilter">
            <el-radio-button value="ALL">全部订单</el-radio-button>
            <el-radio-button value="GROUP_BUY">团购</el-radio-button>
            <el-radio-button value="FORM">定金</el-radio-button>
          </el-radio-group>

          <el-radio-group v-model="viewMode">
            <el-radio-button value="ORDER">订单列表</el-radio-button>
            <el-radio-button value="STORE">门店列表</el-radio-button>
          </el-radio-group>
        </div>

        <div class="toolbar__filters">
          <el-button @click="loadOrders">刷新列表</el-button>
        </div>
      </div>

      <template v-if="viewMode === 'ORDER'">
        <div class="toolbar toolbar--compact">
          <div class="toolbar-tabs">
            <el-radio-group v-model="orderStatusFilter">
              <el-radio-button value="UNAPPOINTED">待约档</el-radio-button>
              <el-radio-button value="APPOINTED">已约档</el-radio-button>
              <el-radio-button value="VERIFIED">已核销</el-radio-button>
              <el-radio-button value="ALL">全部</el-radio-button>
            </el-radio-group>
          </div>

          <div class="toolbar__filters">
            <el-input v-model="orderFilters.customerName" clearable placeholder="姓名查询" style="width: 180px" />
            <el-input v-model="orderFilters.customerPhone" clearable placeholder="手机号查询" style="width: 180px" />
          </div>
        </div>

        <el-table class="paid-order-table" v-loading="loading" :data="orderPagination.rows" row-key="id" stripe table-layout="fixed">
          <el-table-column label="订单" width="170">
            <template #default="{ row }">
              <div class="table-primary">
                <strong>{{ row.orderNo }}</strong>
                <span>#{{ row.id }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="客户信息" width="180">
            <template #default="{ row }">
              <div class="table-primary">
                <strong>{{ row.customerName || '--' }}</strong>
                <span>{{ row.customerPhone || '--' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="门店" width="132" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.storeName || '--' }}
            </template>
          </el-table-column>

          <el-table-column label="付款时间" width="160">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) }}
            </template>
          </el-table-column>

          <el-table-column label="是否已预约" width="120">
            <template #default="{ row }">
              <el-tag :type="appointmentStateTagType(row)">
                {{ appointmentStateLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="到店/档数" width="118">
            <template #default="{ row }">
              <span>{{ appointmentOccupancyText(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="预约档期" width="190" show-overflow-tooltip>
            <template #default="{ row }">
              {{ appointmentDisplayText(row) }}
            </template>
          </el-table-column>

          <el-table-column label="最近排档动态" min-width="230">
            <template #default="{ row }">
              <div v-if="latestAppointmentRecord(row)" class="schedule-record-preview">
                <div class="schedule-record-preview__head">
                  <el-tag size="small" :type="recordTagType(latestAppointmentRecord(row)?.actionType)">
                    {{ recordActionLabel(latestAppointmentRecord(row)?.actionType) }}
                  </el-tag>
                  <span>{{ recentRecordText(row) }}</span>
                </div>
                <div v-if="appointmentRecordBadges(row).length" class="schedule-record-preview__badges">
                  <el-tag v-for="badge in appointmentRecordBadges(row)" :key="badge" size="small" effect="plain">
                    {{ badge }}
                  </el-tag>
                </div>
                <el-button link type="primary" @click="openAppointmentRecordDialog(row)">查看记录</el-button>
              </div>
              <span v-else class="text-secondary">暂无排档记录</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="172" fixed="right">
            <template #default="{ row }">
              <div class="action-group action-group--compact">
                <el-button v-if="canEditAppointment(row)" size="small" type="primary" @click="openAppointmentDialog(row)">
                  {{ isAppointedOrder(row) ? '改档' : '约档' }}
                </el-button>
                <el-popconfirm v-if="canCancelAppointment(row)" title="确认取消当前预约吗？" @confirm="handleCancelAppointment(row)">
                  <template #reference>
                    <el-button size="small" plain type="danger">取消预约</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="orderPagination.total"
            :current-page="orderPagination.currentPage"
            :page-size="orderPagination.pageSize"
            :page-sizes="orderPagination.pageSizes"
            @size-change="orderPagination.handleSizeChange"
            @current-change="orderPagination.handleCurrentChange"
          />
        </div>
      </template>

      <template v-else>
        <div class="store-browser">
          <aside class="store-browser__aside">
            <div class="panel-heading compact">
              <div>
                <h3>门店列表</h3>
              </div>
            </div>

            <div class="toolbar toolbar--compact">
              <div class="toolbar__filters">
                <el-input v-model="storeKeyword" clearable placeholder="名称 / 位置搜索" style="width: 100%" />
              </div>
            </div>

            <div v-if="filteredStoreCards.length" class="store-browser__list">
              <button
                v-for="store in filteredStoreCards"
                :key="store.storeName"
                type="button"
                class="store-card"
                :class="{ 'is-active': activeStoreName === store.storeName }"
                @click="selectedStoreName = store.storeName"
              >
                <strong>{{ store.storeName }}</strong>
                <span>{{ store.location }}</span>
                <small>今日已约 {{ store.todayBooked }} / {{ store.capacity || 0 }} 档</small>
              </button>
            </div>
            <p v-else class="text-secondary">暂无符合条件的门店。</p>
          </aside>

          <div class="calendar-side">
            <div class="panel-heading compact">
              <div>
                <h3>{{ activeStoreName || '请选择门店' }}</h3>
                <p class="panel-heading__meta">{{ activeStoreLocation }}</p>
              </div>
            </div>

            <el-calendar v-model="calendarDate" class="appointment-calendar">
              <template #date-cell="{ data }">
                <button
                  type="button"
                  class="calendar-cell schedule-calendar-cell"
                  :class="{ 'is-selected': selectedCalendarDay === data.day, 'is-full': isDateFull(data.day) }"
                  @click="handleCalendarDayClick(data.day)"
                >
                  <span class="calendar-cell__day">{{ Number(data.day.split('-').pop()) }}</span>
                  <template v-for="item in appointmentRowsByDay(data.day).slice(0, 2)" :key="`${data.day}-${item.id}`">
                    <span class="calendar-cell__event">{{ item.customerName || item.orderNo }}</span>
                  </template>
                  <span v-if="isDateFull(data.day)" class="calendar-cell__full">满</span>
                </button>
              </template>
            </el-calendar>

            <div class="detail-card calendar-day-card">
              <div class="store-day-actions">
                <div>
                  <h3>{{ selectedCalendarDay }}</h3>
                  <p>已占用 {{ selectedDayScheduledRows.length }} 档，剩余 {{ selectedDayRemainingCount }} 个空档。</p>
                </div>
                <el-button
                  type="primary"
                  :disabled="!activeStoreName || !availableStoreSlots.length"
                  @click="openStoreBookingDialog"
                >
                  添加
                </el-button>
              </div>

              <el-table
                v-if="selectedDayScheduledRows.length"
                class="appointment-day-table"
                :data="selectedDayScheduledRows"
                stripe
                size="small"
                table-layout="fixed"
              >
                <el-table-column label="客户" width="178">
                  <template #default="{ row }">
                    <div class="table-primary">
                      <strong>{{ row.customerName }}</strong>
                      <span>{{ row.customerPhone }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="付款时间" width="160">
                  <template #default="{ row }">
                    {{ formatDateTime(row.createTime) }}
                  </template>
                </el-table-column>
                <el-table-column label="档顺序号" width="92" prop="slotIndex" />
                <el-table-column label="档期时间段" min-width="150" prop="slotLabel" />
              </el-table>
              <p v-else class="text-secondary">当天暂无预约客户。</p>
            </div>
          </div>
        </div>
      </template>
    </section>

    <el-dialog v-model="appointmentDialogVisible" :title="appointmentDialogTitle" width="640px">
      <el-alert
        v-if="appointmentStatusTip"
        class="appointment-status-tip"
        :title="appointmentStatusTip"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form :model="appointmentForm" label-width="92px">
        <el-form-item label="订单">
          <el-input :model-value="selectedOrderLabel" disabled />
        </el-form-item>
        <el-form-item label="预约门店">
          <el-select
            v-model="appointmentForm.storeName"
            :disabled="!selectedOrderCanEditAppointment"
            style="width: 100%"
            @change="handleAppointmentStoreChange"
          >
            <el-option v-for="item in storeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="到店人数">
          <div class="appointment-headcount-row">
            <el-input-number
              v-model="appointmentForm.headcount"
              :min="1"
              :max="20"
              :disabled="!selectedOrderCanEditAppointment"
              controls-position="right"
              @change="handleAppointmentHeadcountChange"
            />
            <span>需占用 {{ requiredAppointmentSlotCount }} 个档</span>
          </div>
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker
            v-model="appointmentForm.appointmentTime"
            type="datetime"
            placeholder="请选择预约时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="!selectedOrderCanEditAppointment"
            style="width: 100%"
            @change="handleAppointmentTimeChange"
          />
        </el-form-item>
        <el-form-item v-if="selectedOrderCanEditAppointment" label="门店空档">
          <div data-qa="paid-order-slot-picker" class="slot-picker">
            <button
              v-for="slot in appointmentSlotOptions"
              :key="slot.value"
              type="button"
              class="slot-button"
              :class="{ 'is-selected': selectedAppointmentSlotValues.includes(slot.value) }"
              :disabled="slot.isOccupied"
              @click="selectAppointmentSlot(slot)"
            >
              <strong>{{ slot.label }}</strong>
              <span>{{ slot.isOccupied ? `已约：${slot.occupiedLabel}` : `第 ${slot.index} 档` }}</span>
            </button>
            <span v-if="!appointmentSlotOptions.length" class="text-secondary">当前门店当天没有可用空档。</span>
          </div>
          <div class="appointment-slot-summary">
            已选择 {{ selectedAppointmentSlotCount }} / {{ requiredAppointmentSlotCount }} 档
          </div>
        </el-form-item>
        <el-form-item label="排档提示">
          <div class="table-note">
            当前门店 {{ appointmentForm.storeName || '--' }} 每日可排 {{ storeCapacity(appointmentForm.storeName) }} 档；
            {{ formatDate(appointmentForm.appointmentTime) || '所选日期' }} 已约
            {{ bookedCountByStoreAndDay(appointmentForm.storeName, formatDate(appointmentForm.appointmentTime), selectedOrder?.id) }}
            档。
          </div>
        </el-form-item>
        <el-form-item label="排档备注">
          <el-input
            v-model="appointmentForm.remark"
            type="textarea"
            :rows="4"
            :readonly="!selectedOrderCanEditAppointment"
            placeholder="填写排档说明、到店提醒或门店注意事项"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appointmentDialogVisible = false">关闭</el-button>
        <el-button v-if="selectedOrderCanEditAppointment" type="primary" :loading="saving" @click="handleSaveAppointment">
          {{ appointmentSubmitText }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="storeBookingDialogVisible" title="添加客户到门店空档" width="760px">
      <div class="stack-page">
        <div class="toolbar toolbar--compact">
          <div class="toolbar__filters">
            <el-input v-model="storeBookingForm.keyword" clearable placeholder="搜索姓名或手机号" style="width: 240px" />
          </div>
        </div>

        <div class="store-booking-grid">
          <section class="panel">
            <div class="panel-heading compact">
              <div>
                <h3>选择客户订单</h3>
              </div>
            </div>

            <div v-if="storeBookingCandidateOrders.length" class="store-booking-list">
              <button
                v-for="order in storeBookingCandidateOrders"
                :key="order.id"
                type="button"
                class="store-booking-card"
                :class="{ 'is-active': storeBookingForm.orderId === order.id }"
                @click="storeBookingForm.orderId = order.id"
              >
                <strong>{{ order.customerName || order.orderNo }}</strong>
                <span>{{ order.customerPhone || '--' }}</span>
                <small>付款时间：{{ formatDateTime(order.createTime) }}</small>
              </button>
            </div>
            <p v-else class="text-secondary">没有可加入当前门店档期的未预约订单。</p>
          </section>

          <section class="panel">
            <div class="panel-heading compact">
              <div>
                <h3>选择空白档期</h3>
                <p class="panel-heading__meta">{{ activeStoreName }} / {{ selectedCalendarDay }}</p>
              </div>
            </div>

            <div v-if="availableStoreSlots.length" class="slot-picker">
              <button
                v-for="slot in availableStoreSlots"
                :key="slot.value"
                type="button"
                class="slot-button"
                :class="{ 'is-selected': storeBookingForm.slotValue === slot.value }"
                @click="storeBookingForm.slotValue = slot.value"
              >
                <strong>{{ slot.label }}</strong>
                <span>第 {{ slot.index }} 档</span>
              </button>
            </div>
            <p v-else class="text-secondary">当前日期已满，无法继续添加预约。</p>
          </section>
        </div>
      </div>

      <template #footer>
        <el-button @click="storeBookingDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleStoreBookingConfirm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="appointmentRecordDialogVisible" :title="appointmentRecordDialogTitle" width="680px">
      <el-timeline v-if="selectedAppointmentRecords.length" class="schedule-record-timeline">
        <el-timeline-item
          v-for="(record, index) in selectedAppointmentRecords"
          :key="`${record.actionType}-${record.createTime}-${index}`"
          :timestamp="record.createTime || '--'"
          placement="top"
        >
          <div class="schedule-record-line">
            <strong>{{ timelineActionLabel(record.actionType) }}</strong>
            <span v-if="recordActorText(record)">{{ recordActorText(record) }}</span>
            <span>{{ recordSummary(record) }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无排档记录" />
      <template #footer>
        <el-button @click="appointmentRecordDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { appointOrder, cancelOrderAppointment } from '../api/order'
import { fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, normalize } from '../utils/format'
import { calculateStoreCapacity, loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const FALLBACK_STORE_LOCATIONS = {
  静安门店: '上海市静安区南京西路商圈',
  浦东门店: '上海市浦东新区陆家嘴片区',
  徐汇门店: '上海市徐汇区漕溪北路片区'
}

const route = useRoute()
const router = useRouter()
const consoleState = reactive(loadSystemConsoleState())
const loading = ref(true)
const saving = ref(false)
const orders = ref([])
const productSourceFilter = ref('ALL')
const viewMode = ref('ORDER')
const orderStatusFilter = ref('UNAPPOINTED')
const initialCalendarDay = String(route.query.day || '').trim() || formatDate(new Date())
const calendarDate = ref(parseCalendarDate(initialCalendarDay))
const selectedCalendarDay = ref(initialCalendarDay)
const selectedStoreName = ref(String(route.query.storeName || '').trim())
const storeKeyword = ref('')
const appointmentDialogVisible = ref(false)
const storeBookingDialogVisible = ref(false)
const appointmentRecordDialogVisible = ref(false)
const selectedOrder = ref(null)
const selectedRecordOrder = ref(null)
const pendingRouteOrderId = ref(Number(route.query.orderId || 0))

const orderFilters = reactive({
  customerName: '',
  customerPhone: ''
})

const appointmentForm = reactive({
  appointmentTime: '',
  appointmentSlots: [],
  headcount: 1,
  remark: '',
  storeName: ''
})

const storeBookingForm = reactive({
  keyword: '',
  orderId: null,
  slotValue: ''
})

const mergedOrders = computed(() =>
  orders.value.map((item) => {
    const clueProfile = (consoleState.clueConsoleProfiles || []).find((profile) => profile.clueId === item.clueId)
    const storeName = isAppointedOrder(item) ? item.storeName : clueProfile?.intendedStoreName || item.storeName
    return {
      ...item,
      storeName
    }
  })
)

const productFilteredOrders = computed(() =>
  mergedOrders.value.filter((item) => {
    if (productSourceFilter.value === 'ALL') {
      return true
    }
    return item.productSourceType === productSourceFilter.value
  })
)

const schedulingOrders = computed(() => productFilteredOrders.value.filter((item) => isSchedulingOrder(item)))

const filteredOrderRows = computed(() =>
  schedulingOrders.value.filter((item) => {
    if (orderStatusFilter.value === 'UNAPPOINTED' && (!isUnappointedOrder(item) || isVerifiedOrder(item))) {
      return false
    }
    if (orderStatusFilter.value === 'APPOINTED' && (!isAppointedOrder(item) || isVerifiedOrder(item))) {
      return false
    }
    if (orderStatusFilter.value === 'VERIFIED' && !isVerifiedOrder(item)) {
      return false
    }
    if (orderFilters.customerName && !String(item.customerName || '').includes(orderFilters.customerName.trim())) {
      return false
    }
    if (orderFilters.customerPhone && !String(item.customerPhone || '').includes(orderFilters.customerPhone.trim())) {
      return false
    }
    return true
  })
)

const orderPagination = useTablePagination(filteredOrderRows)
const storeCards = computed(() => {
  const storeNames = [
    ...(consoleState.storeScheduleConfigs || []).map((item) => item.storeName),
    ...mergedOrders.value.map((item) => item.storeName)
  ].filter(Boolean)

  return [...new Set(storeNames)].map((storeName) => ({
    storeName,
    location: resolveStoreLocation(storeName),
    capacity: storeCapacity(storeName),
    todayBooked: bookedCountByStoreAndDay(storeName, formatDate(new Date()))
  }))
})
const storeOptions = computed(() => storeCards.value.map((item) => item.storeName))
const filteredStoreCards = computed(() =>
  storeCards.value.filter((item) => {
    const keyword = String(storeKeyword.value || '').trim()
    if (!keyword) {
      return true
    }
    return `${item.storeName} ${item.location}`.includes(keyword)
  })
)
const activeStoreCard = computed(
  () => filteredStoreCards.value.find((item) => item.storeName === selectedStoreName.value) || filteredStoreCards.value[0] || null
)
const activeStoreName = computed(() => activeStoreCard.value?.storeName || '')
const activeStoreLocation = computed(() => activeStoreCard.value?.location || '请选择左侧门店后查看日历')
const selectedDaySlotRows = computed(() => buildAppointmentSlots(activeStoreName.value, selectedCalendarDay.value))
const selectedDayScheduledRows = computed(() =>
  selectedDaySlotRows.value
    .filter((item) => item.order)
    .map((item) => ({
      id: item.order.id,
      customerName: item.order.customerName || item.order.orderNo,
      customerPhone: item.order.customerPhone || '--',
      createTime: item.order.createTime,
      slotIndex: item.index,
      slotLabel: item.label
    }))
)
const selectedDayCapacity = computed(() => storeCapacity(activeStoreName.value))
const selectedDayRemainingCount = computed(() => Math.max(selectedDayCapacity.value - selectedDayScheduledRows.value.length, 0))
const appointmentCount = computed(() => schedulingOrders.value.filter((item) => isAppointedOrder(item) && !isVerifiedOrder(item)).length)
const waitingCount = computed(() => schedulingOrders.value.filter((item) => isUnappointedOrder(item) && !isVerifiedOrder(item)).length)
const selectedOrderCanEditAppointment = computed(() => canEditAppointment(selectedOrder.value))
const requiredAppointmentSlotCount = computed(() => normalizeHeadcount(appointmentForm.headcount))
const selectedAppointmentSlotValues = computed(() => normalizeSlotValues(appointmentForm.appointmentSlots))
const selectedAppointmentSlotCount = computed(() => selectedAppointmentSlotValues.value.length)
const appointmentSlotOptions = computed(() => {
  if (!selectedOrderCanEditAppointment.value) {
    return []
  }
  const scheduleDay = formatDate(appointmentForm.appointmentTime) || selectedCalendarDay.value
  return buildAppointmentSlots(appointmentForm.storeName, scheduleDay, selectedOrder.value?.id)
})
const availableStoreSlots = computed(() => selectedDaySlotRows.value.filter((item) => !item.isOccupied))
const storeBookingCandidateOrders = computed(() =>
  schedulingOrders.value.filter((item) => {
    if (!isUnappointedOrder(item)) {
      return false
    }
    const keyword = String(storeBookingForm.keyword || '').trim()
    if (!keyword) {
      return true
    }
    return `${item.customerName || ''} ${item.customerPhone || ''} ${item.orderNo || ''}`.includes(keyword)
  })
)
const appointmentDialogTitle = computed(() => {
  if (isAppointedOrder(selectedOrder.value)) {
    return '调整排档'
  }
  if (isUnappointedOrder(selectedOrder.value)) {
    return '预约排档'
  }
  return '查看排档'
})
const appointmentStatusTip = computed(() => {
  if (!selectedOrder.value) {
    return ''
  }
  if (isVerifiedOrder(selectedOrder.value)) {
    return '该客户已核销，默认不再参与待约档处理。'
  }
  if (isAppointedOrder(selectedOrder.value)) {
    return '已约档，可在这里调整门店、到店人数和档期。'
  }
  return '待约档，选择门店和足够档位后即可保存。'
})
const appointmentSubmitText = computed(() => (isAppointedOrder(selectedOrder.value) ? '确认改档' : '确认约档'))
const selectedOrderLabel = computed(() => {
  if (!selectedOrder.value) {
    return ''
  }
  return `${selectedOrder.value.orderNo} / ${selectedOrder.value.customerName || '未绑定客户'}`
})
const selectedAppointmentRecords = computed(() => appointmentRecords(selectedRecordOrder.value))
const appointmentRecordDialogTitle = computed(() => {
  if (!selectedRecordOrder.value) {
    return '排档记录'
  }
  return `排档记录 / ${selectedRecordOrder.value.customerName || selectedRecordOrder.value.orderNo || '--'}`
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

function parseCalendarDate(day) {
  const normalized = String(day || '').trim()
  if (!normalized) {
    return new Date()
  }
  const [year, month, date] = normalized.split('-').map((item) => Number(item))
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(date)) {
    return new Date(normalized)
  }
  return new Date(year, month - 1, date, 12, 0, 0)
}

function syncCalendarDay(day) {
  const normalized = String(day || '').trim()
  if (!normalized) {
    return
  }
  selectedCalendarDay.value = normalized
  calendarDate.value = parseCalendarDate(normalized)
}

function mergeOrdersById(...groups) {
  const orderMap = new Map()
  for (const group of groups) {
    for (const item of group || []) {
      if (!item?.id || orderMap.has(item.id)) {
        continue
      }
      orderMap.set(item.id, item)
    }
  }
  return [...orderMap.values()]
}

function resolveStoreLocation(storeName) {
  const config = (consoleState.storeScheduleConfigs || []).find((item) => item.storeName === storeName)
  return config?.location || FALLBACK_STORE_LOCATIONS[storeName] || '门店位置待维护'
}

function appointmentRowsByDay(day, storeName = activeStoreName.value) {
  if (!storeName) {
    return []
  }
  return buildAppointmentSlots(storeName, day)
    .filter((item) => item.order)
    .map((item) => ({
      ...item.order,
      slotIndex: item.index,
      slotLabel: item.label
    }))
}

function bookedCountByStoreAndDay(storeName, day, excludingOrderId = null) {
  if (!storeName || !day) {
    return 0
  }
  return mergedOrders.value.reduce((total, item) => {
    if (item.storeName !== storeName || (excludingOrderId && item.id === excludingOrderId)) {
      return total
    }
    return total + orderAppointmentSlotValues(item).filter((slotValue) => formatDate(slotValue) === day).length
  }, 0)
}

function storeCapacity(storeName) {
  const config = (consoleState.storeScheduleConfigs || []).find((item) => item.storeName === storeName)
  return calculateStoreCapacity(config)
}

function isDateFull(day, storeName = activeStoreName.value) {
  if (!storeName || !day) {
    return false
  }
  const capacity = storeCapacity(storeName)
  if (capacity <= 0) {
    return false
  }
  return bookedCountByStoreAndDay(storeName, day) >= capacity
}

function resolveOrderStatus(row) {
  return normalize(row?.status || row?.statusCategory)
}

function isSchedulingOrder(row) {
  return isUnappointedOrder(row) || isAppointedOrder(row) || isVerifiedOrder(row)
}

function isUnappointedOrder(row) {
  return ['PAID', 'PAID_DEPOSIT'].includes(resolveOrderStatus(row)) && !isVerifiedOrder(row)
}

function isAppointedOrder(row) {
  return resolveOrderStatus(row) === 'APPOINTMENT'
}

function isVerifiedOrder(row) {
  return normalize(row?.verificationStatus) === 'VERIFIED'
}

function canEditAppointment(row) {
  return !isVerifiedOrder(row) && (isUnappointedOrder(row) || isAppointedOrder(row))
}

function canCancelAppointment(row) {
  return !isVerifiedOrder(row) && isAppointedOrder(row)
}

function appointmentStateLabel(row) {
  if (isVerifiedOrder(row)) {
    return '已核销'
  }
  return isAppointedOrder(row) ? '已约档' : '待约档'
}

function appointmentStateTagType(row) {
  if (isVerifiedOrder(row)) {
    return 'info'
  }
  return isAppointedOrder(row) ? 'success' : 'warning'
}

function appointmentDisplayText(row) {
  const slots = orderAppointmentSlotValues(row)
  if (!slots.length) {
    return '待约档'
  }
  if (slots.length === 1) {
    return formatDateTime(slots[0])
  }
  return `${formatDateTime(slots[0])} 等 ${slots.length} 档`
}

function appointmentOccupancyText(row) {
  if (!isAppointedOrder(row) && !isVerifiedOrder(row)) {
    return '--'
  }
  return `${appointmentHeadcount(row)} 人 / ${appointmentSlotCount(row)} 档`
}

function appointmentRecords(row) {
  return Array.isArray(row?.appointmentRecords) ? row.appointmentRecords : []
}

function currentAppointmentExtra(row) {
  const record = appointmentRecords(row).find((item) => ['APPOINTMENT_CREATE', 'APPOINTMENT_CHANGE'].includes(normalize(item?.actionType)))
  return parseRecordExtra(record?.extraJson)
}

function orderAppointmentSlotValues(row) {
  const extra = currentAppointmentExtra(row)
  const slots = normalizeSlotValues(extra.appointmentSlotsAfter)
  if (slots.length) {
    return slots
  }
  return row?.appointmentTime ? [normalizeSlotValue(row.appointmentTime)] : []
}

function appointmentHeadcount(row) {
  const extra = currentAppointmentExtra(row)
  const value = Number(extra.headcountAfter || extra.slotCountAfter || orderAppointmentSlotValues(row).length || 1)
  return Number.isFinite(value) && value > 0 ? value : 1
}

function appointmentSlotCount(row) {
  return Math.max(orderAppointmentSlotValues(row).length, appointmentHeadcount(row), 1)
}

function latestAppointmentRecord(row) {
  return appointmentRecords(row)[0] || null
}

function appointmentChangeCount(row) {
  return appointmentRecords(row).filter((item) => normalize(item?.actionType) === 'APPOINTMENT_CHANGE').length
}

function hasCanceledAppointment(row) {
  return appointmentRecords(row).some((item) => normalize(item?.actionType) === 'APPOINTMENT_CANCEL')
}

function appointmentRecordBadges(row) {
  const parts = []
  const changeCount = appointmentChangeCount(row)
  if (changeCount > 0) {
    parts.push(`改档${changeCount}次`)
  }
  if (hasCanceledAppointment(row)) {
    parts.push('曾取消')
  }
  return parts
}

function openAppointmentRecordDialog(row) {
  selectedRecordOrder.value = row
  appointmentRecordDialogVisible.value = true
}

function recordActionLabel(actionType) {
  const normalized = normalize(actionType)
  const labels = {
    APPOINTMENT_CREATE: '已约档',
    APPOINTMENT_CHANGE: '已改档',
    APPOINTMENT_CANCEL: '已取消'
  }
  return labels[normalized] || '记录'
}

function timelineActionLabel(actionType) {
  const normalized = normalize(actionType)
  const labels = {
    APPOINTMENT_CREATE: '约档',
    APPOINTMENT_CHANGE: '改档',
    APPOINTMENT_CANCEL: '取消预约'
  }
  return labels[normalized] || '排档记录'
}

function recordTagType(actionType) {
  const normalized = normalize(actionType)
  if (normalized === 'APPOINTMENT_CANCEL') {
    return 'danger'
  }
  if (normalized === 'APPOINTMENT_CHANGE') {
    return 'warning'
  }
  return 'success'
}

function recentRecordText(row) {
  const record = latestAppointmentRecord(row)
  const extra = parseRecordExtra(record?.extraJson)
  const afterSlots = normalizeSlotValues(extra.appointmentSlotsAfter)
  const beforeSlots = normalizeSlotValues(extra.appointmentSlotsBefore)
  if (normalize(record?.actionType) === 'APPOINTMENT_CANCEL') {
    return formatSlotList(beforeSlots) || extra.appointmentTimeBefore || record?.createTime || '--'
  }
  if (normalize(record?.actionType) === 'APPOINTMENT_CHANGE') {
    const slotText = formatSlotList(afterSlots) || extra.appointmentTimeAfter
    return slotText ? `至 ${slotText}` : record?.createTime || '--'
  }
  return formatSlotList(afterSlots) || extra.appointmentTimeAfter || record?.createTime || '--'
}

function recordSummary(record) {
  const extra = parseRecordExtra(record?.extraJson)
  const before = extra.appointmentTimeBefore || ''
  const after = extra.appointmentTimeAfter || ''
  const beforeSlots = normalizeSlotValues(extra.appointmentSlotsBefore)
  const afterSlots = normalizeSlotValues(extra.appointmentSlotsAfter)
  const beforeStore = extra.storeNameBefore || ''
  const afterStore = extra.storeNameAfter || extra.storeName || ''
  const remark = extra.remark || record?.remark || ''
  if (normalize(record?.actionType) === 'APPOINTMENT_CANCEL') {
    const parts = []
    if (beforeStore) {
      parts.push(`原门店：${beforeStore}`)
    }
    parts.push(beforeSlots.length ? `原档期：${formatSlotList(beforeSlots)}` : (before ? `原档期：${before}` : '已取消预约'))
    if (Number(extra.headcountBefore) > 0) {
      parts.push(`原人数：${extra.headcountBefore} 人`)
    }
    if (remark) {
      parts.push(remark)
    }
    return parts.join(' / ')
  }
  const parts = []
  if (beforeStore && afterStore && beforeStore !== afterStore) {
    parts.push(`门店：${beforeStore} -> ${afterStore}`)
  } else if (afterStore) {
    parts.push(`门店：${afterStore}`)
  }
  if (beforeSlots.length || afterSlots.length) {
    const beforeText = formatSlotList(beforeSlots)
    const afterText = formatSlotList(afterSlots)
    parts.push(beforeText && afterText && beforeText !== afterText ? `档期：${beforeText} -> ${afterText}` : `档期：${afterText || beforeText}`)
  } else if (before && after && before !== after) {
    parts.push(`档期：${before} -> ${after}`)
  } else if (after) {
    parts.push(`档期：${after}`)
  }
  if (Number(extra.headcountAfter) > 0) {
    const beforeHeadcount = Number(extra.headcountBefore || 0)
    const afterHeadcount = Number(extra.headcountAfter)
    parts.push(beforeHeadcount > 0 && beforeHeadcount !== afterHeadcount ? `人数：${beforeHeadcount} -> ${afterHeadcount}` : `人数：${afterHeadcount} 人`)
  }
  if (remark && !['预约排档', '更改预约档期'].includes(remark)) {
    parts.push(remark)
  }
  return parts.join(' / ') || record?.createTime || '--'
}

function recordActorText(record) {
  const actor = record?.operatorUserName || (record?.operatorUserId ? `ID ${record.operatorUserId}` : '')
  return actor ? `操作人：${actor}` : ''
}

function parseRecordExtra(value) {
  if (!value) {
    return {}
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function normalizeHeadcount(value) {
  const numericValue = Number(value || 1)
  if (!Number.isFinite(numericValue) || numericValue < 1) {
    return 1
  }
  return Math.min(Math.floor(numericValue), 20)
}

function normalizeSlotValues(value) {
  const rawValues = Array.isArray(value) ? value : (value ? [value] : [])
  return [...new Set(rawValues.map((item) => normalizeSlotValue(item)).filter(Boolean))]
}

function normalizeSlotValue(value) {
  if (!value) {
    return ''
  }
  const raw = String(value).trim().replace('T', ' ')
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(raw)) {
    return `${raw}:00`
  }
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(raw)) {
    return raw.slice(0, 19)
  }
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) {
    return raw
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

function formatSlotList(values) {
  const slots = normalizeSlotValues(values)
  if (!slots.length) {
    return ''
  }
  if (slots.length === 1) {
    return formatDateTime(slots[0])
  }
  return `${formatDateTime(slots[0])} 等 ${slots.length} 档`
}

function parseClockMinutes(value) {
  if (!value || !String(value).includes(':')) {
    return 0
  }
  const [hour, minute] = String(value).split(':').map((item) => Number(item))
  return (Number.isFinite(hour) ? hour : 0) * 60 + (Number.isFinite(minute) ? minute : 0)
}

function formatClock(minutes) {
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

function isAppointmentWithinSlot(appointmentTime, day, startMinutes, endMinutes) {
  if (!appointmentTime || formatDate(appointmentTime) !== day) {
    return false
  }
  const normalized = String(appointmentTime).trim()
  const timePart = normalized.includes(' ') ? normalized.split(' ')[1] : normalized.slice(11, 19)
  const minutes = parseClockMinutes(timePart)
  return minutes >= startMinutes && minutes < endMinutes
}

function buildAppointmentSlots(storeName, day, excludingOrderId = null) {
  if (!storeName || !day) {
    return []
  }
  const config = (consoleState.storeScheduleConfigs || []).find((item) => item.storeName === storeName)
  const slotMinutes = Number(config?.slotHours || 0) * 60
  if (!config || slotMinutes <= 0) {
    return []
  }

  const segments = [
    [config.morningStart, config.morningEnd],
    [config.afternoonStart, config.afternoonEnd]
  ]

  let slotIndex = 1
  return segments.flatMap(([start, end]) => {
    const slots = []
    const startMinutes = parseClockMinutes(start)
    const endMinutes = parseClockMinutes(end)
    for (let current = startMinutes; current + slotMinutes <= endMinutes; current += slotMinutes) {
      const slotStart = formatClock(current)
      const slotEnd = formatClock(current + slotMinutes)
      const slotValue = `${day} ${slotStart}:00`
      const occupiedOrder = mergedOrders.value.find((item) => {
        if (item.id === excludingOrderId || item.storeName !== storeName) {
          return false
        }
        return orderAppointmentSlotValues(item).some((appointmentSlot) =>
          isAppointmentWithinSlot(appointmentSlot, day, current, current + slotMinutes)
        )
      })
      slots.push({
        index: slotIndex,
        label: `${slotStart} - ${slotEnd}`,
        value: slotValue,
        isOccupied: Boolean(occupiedOrder),
        occupiedLabel: occupiedOrder ? occupiedOrder.customerName || occupiedOrder.orderNo : '',
        order: occupiedOrder || null
      })
      slotIndex += 1
    }
    return slots
  })
}

function firstAvailableSlotValues(storeName, day, excludingOrderId = null, count = 1) {
  const slots = buildAppointmentSlots(storeName, day, excludingOrderId)
  const requiredCount = normalizeHeadcount(count)
  for (let index = 0; index <= slots.length - requiredCount; index += 1) {
    const candidateSlots = slots.slice(index, index + requiredCount)
    if (candidateSlots.length === requiredCount && candidateSlots.every((item) => !item.isOccupied)) {
      return candidateSlots.map((item) => item.value)
    }
  }
  return []
}

function consecutiveSlotValuesFrom(startValue) {
  const normalizedStartValue = normalizeSlotValue(startValue)
  const requiredCount = requiredAppointmentSlotCount.value
  const slots = appointmentSlotOptions.value
  const startIndex = slots.findIndex((item) => item.value === normalizedStartValue)
  if (startIndex < 0) {
    return firstAvailableSlotValues(appointmentForm.storeName, selectedCalendarDay.value, selectedOrder.value?.id, requiredCount)
  }
  const candidateSlots = slots.slice(startIndex, startIndex + requiredCount)
  if (candidateSlots.length === requiredCount && candidateSlots.every((item) => !item.isOccupied)) {
    return candidateSlots.map((item) => item.value)
  }
  return []
}

function setAppointmentSlots(values) {
  const slots = normalizeSlotValues(values)
  appointmentForm.appointmentSlots = slots
  appointmentForm.appointmentTime = slots[0] || ''
}

function handleCalendarDayClick(day) {
  syncCalendarDay(day)
  if (!appointmentDialogVisible.value || !selectedOrderCanEditAppointment.value) {
    return
  }
  const nextValues = firstAvailableSlotValues(
    appointmentForm.storeName,
    day,
    selectedOrder.value?.id,
    requiredAppointmentSlotCount.value
  )
  if (nextValues.length) {
    setAppointmentSlots(nextValues)
  }
}

function selectAppointmentSlot(slot) {
  if (slot?.isOccupied) {
    return
  }
  const nextValues = consecutiveSlotValuesFrom(slot.value)
  if (!nextValues.length) {
    ElMessage.warning('当前日期没有足够连续空档，请减少人数或更换日期')
    return
  }
  setAppointmentSlots(nextValues)
}

function handleAppointmentHeadcountChange() {
  appointmentForm.headcount = normalizeHeadcount(appointmentForm.headcount)
  const nextValues = consecutiveSlotValuesFrom(appointmentForm.appointmentTime)
  if (nextValues.length) {
    setAppointmentSlots(nextValues)
    return
  }
  setAppointmentSlots([])
}

function handleAppointmentTimeChange(value) {
  const normalizedValue = normalizeSlotValue(value)
  if (!normalizedValue) {
    setAppointmentSlots([])
    return
  }
  const nextDay = formatDate(normalizedValue)
  if (nextDay) {
    syncCalendarDay(nextDay)
  }
  const nextValues = consecutiveSlotValuesFrom(normalizedValue)
  if (nextValues.length) {
    setAppointmentSlots(nextValues)
    return
  }
  setAppointmentSlots([normalizedValue])
}

function openAppointmentDialog(row) {
  selectedOrder.value = row
  appointmentForm.remark = row.remark || ''
  appointmentForm.storeName = row.storeName || activeStoreName.value || storeCards.value[0]?.storeName || ''
  appointmentForm.headcount = appointmentHeadcount(row)
  if (appointmentForm.storeName) {
    selectedStoreName.value = appointmentForm.storeName
  }
  const currentSlots = orderAppointmentSlotValues(row)
  const preferredDay = formatDate(currentSlots[0] || row.appointmentTime) || selectedCalendarDay.value || formatDate(new Date())
  syncCalendarDay(preferredDay)
  if (canEditAppointment(row)) {
    if (currentSlots.length) {
      setAppointmentSlots(currentSlots)
    } else {
      setAppointmentSlots(firstAvailableSlotValues(appointmentForm.storeName, preferredDay, row.id, appointmentForm.headcount))
    }
  } else {
    setAppointmentSlots(currentSlots)
  }
  appointmentDialogVisible.value = true
}

function handleAppointmentStoreChange(value) {
  selectedStoreName.value = value || ''
  if (!selectedOrderCanEditAppointment.value) {
    return
  }
  const nextValues = firstAvailableSlotValues(value, selectedCalendarDay.value, selectedOrder.value?.id, requiredAppointmentSlotCount.value)
  if (nextValues.length) {
    setAppointmentSlots(nextValues)
  }
}

function persistPreferredStore(order, storeName) {
  if (!order?.clueId || !storeName) {
    return
  }
  const nextProfiles = [...(consoleState.clueConsoleProfiles || [])]
  const index = nextProfiles.findIndex((item) => item.clueId === order.clueId)
  if (index >= 0) {
    nextProfiles[index] = {
      ...nextProfiles[index],
      intendedStoreName: storeName
    }
  } else {
    nextProfiles.push({
      id: nextSystemId(nextProfiles),
      clueId: order.clueId,
      intendedStoreName: storeName
    })
  }
  consoleState.clueConsoleProfiles = nextProfiles
  saveSystemConsoleState(consoleState)
}

async function loadOrders() {
  loading.value = true
  try {
    let nextOrders = await fetchOrders({
      status: 'paid'
    })
    if (pendingRouteOrderId.value && !nextOrders.some((item) => item.id === pendingRouteOrderId.value)) {
      const completedOrders = await fetchOrders({
        status: 'used'
      }).catch(() => [])
      nextOrders = mergeOrdersById(nextOrders, completedOrders)
    }
    orders.value = nextOrders
    const routeDay = String(route.query.day || '').trim()
      if (routeDay) {
        syncCalendarDay(routeDay)
      }
    if (!selectedStoreName.value && storeCards.value.length) {
      const routeStore = String(route.query.storeName || '').trim()
      selectedStoreName.value = routeStore && storeCards.value.some((item) => item.storeName === routeStore)
        ? routeStore
        : storeCards.value[0].storeName
    }
    orderPagination.reset()

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
  if (!selectedOrderCanEditAppointment.value) {
    appointmentDialogVisible.value = false
    return
  }
  const selectedSlots = selectedAppointmentSlotValues.value
  if (!selectedOrder.value?.id || !appointmentForm.appointmentTime || !appointmentForm.storeName) {
    ElMessage.warning('请先选择预约门店和预约时间')
    return
  }
  if (selectedSlots.length < requiredAppointmentSlotCount.value) {
    ElMessage.warning(`请先选择 ${requiredAppointmentSlotCount.value} 个连续空档`)
    return
  }

  const day = formatDate(appointmentForm.appointmentTime)
  const capacity = storeCapacity(appointmentForm.storeName)
  const booked = bookedCountByStoreAndDay(appointmentForm.storeName, day, selectedOrder.value.id)
  if (capacity > 0 && booked + selectedSlots.length > capacity) {
    ElMessage.warning('当前日期门店档位已满，请改约其他日期')
    return
  }

  saving.value = true
  try {
    await appointOrder({
      orderId: selectedOrder.value.id,
      appointmentTime: selectedSlots[0],
      appointmentSlots: selectedSlots,
      headcount: requiredAppointmentSlotCount.value,
      previousStoreName: selectedOrder.value.storeName || undefined,
      storeName: appointmentForm.storeName,
      sourceSurface: 'CUSTOMER_SCHEDULE',
      remark: appointmentForm.remark || undefined
    })
    persistPreferredStore(selectedOrder.value, appointmentForm.storeName)
    ElMessage.success(isAppointedOrder(selectedOrder.value) ? '已改档，记录已保存' : '已约档，门店可在服务列表查看')
    appointmentDialogVisible.value = false
    pendingRouteOrderId.value = 0
    selectedOrder.value = null
    if (route.query.orderId) {
      await router.replace({
        path: '/clues/scheduling',
        query: {
          storeName: appointmentForm.storeName,
          day
        }
      })
    }
    syncCalendarDay(day)
    await loadOrders()
  } finally {
    saving.value = false
  }
}

async function handleCancelAppointment(row) {
  saving.value = true
  try {
    await cancelOrderAppointment({
      orderId: row.id,
      sourceSurface: 'CUSTOMER_SCHEDULE',
      remark: '取消预约'
    })
    ElMessage.success('已取消预约，客户已回到未预约状态')
    await loadOrders()
  } finally {
    saving.value = false
  }
}

function openStoreBookingDialog() {
  if (!activeStoreName.value) {
    ElMessage.warning('请先选择门店')
    return
  }
  if (!availableStoreSlots.value.length) {
    ElMessage.warning('当前日期已经约满，请更换日期后再添加')
    return
  }
  storeBookingForm.keyword = ''
  storeBookingForm.orderId = storeBookingCandidateOrders.value[0]?.id || null
  storeBookingForm.slotValue = availableStoreSlots.value[0]?.value || ''
  storeBookingDialogVisible.value = true
}

async function handleStoreBookingConfirm() {
  const order = schedulingOrders.value.find((item) => item.id === storeBookingForm.orderId)
  if (!order) {
    ElMessage.warning('请先选择客户订单')
    return
  }
  if (!storeBookingForm.slotValue) {
    ElMessage.warning('请先选择空白档期')
    return
  }

  saving.value = true
  try {
    await appointOrder({
      orderId: order.id,
      appointmentTime: storeBookingForm.slotValue,
      appointmentSlots: [storeBookingForm.slotValue],
      headcount: 1,
      previousStoreName: order.storeName || undefined,
      storeName: activeStoreName.value,
      sourceSurface: 'STORE_SCHEDULE',
      remark: order.remark || undefined
    })
    persistPreferredStore(order, activeStoreName.value)
    ElMessage.success('门店档期添加成功')
    storeBookingDialogVisible.value = false
    await router.replace({
      path: '/clues/scheduling',
      query: {
        storeName: activeStoreName.value,
        day: selectedCalendarDay.value
      }
    })
    syncCalendarDay(selectedCalendarDay.value)
    await loadOrders()
  } finally {
    saving.value = false
  }
}

loadOrders()
</script>

<style scoped>
.appointment-status-tip {
  margin-bottom: 14px;
}

.appointment-headcount-row {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
}

.appointment-slot-summary {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 8px;
  width: 100%;
}

.schedule-record-preview {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.schedule-record-line strong {
  color: #0f172a;
  font-size: 13px;
}

.schedule-record-preview__head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.schedule-record-preview__head span,
.schedule-record-line span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.schedule-record-preview__badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.schedule-record-line {
  display: grid;
  gap: 6px;
}

.schedule-record-timeline {
  padding: 6px 4px 0;
}
</style>
