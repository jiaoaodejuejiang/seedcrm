<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>门店数量</span>
        <strong>{{ storeConfigs.length }}</strong>
        <small>每个门店可单独设置上下班时间与每档服务时长。</small>
      </article>
      <article class="metric-card">
        <span>日档位数</span>
        <strong>{{ draftCapacity }}</strong>
        <small>{{ selectedStoreName || '当前门店' }}按设置可承接的单日客户数。</small>
      </article>
      <article class="metric-card">
        <span>本月满档天数</span>
        <strong>{{ fullDayCount }}</strong>
        <small>日历中显示“满”的日期不可继续预约客户。</small>
      </article>
    </section>

    <section class="panel" v-loading="loading">
      <div class="panel-heading">
        <div>
          <h3>门店档期配置</h3>
          <p>先选择门店，再设置上下班时间与每档服务时长，系统会自动计算每日档位数。</p>
        </div>
      </div>

      <div class="detail-grid schedule-grid">
        <article class="detail-card">
          <h3>门店列表</h3>
          <div class="schedule-store-list">
            <button
              v-for="store in storeConfigs"
              :key="store.storeName"
              type="button"
              class="schedule-store-button"
              :class="{ 'is-active': selectedStoreName === store.storeName }"
              @click="selectStore(store.storeName)"
            >
              <strong>{{ store.storeName }}</strong>
              <span>{{ calculateStoreCapacity(store) }} 档/天</span>
            </button>
          </div>
        </article>

        <article class="detail-card">
          <h3>上下班与档位设置</h3>
          <div class="form-grid">
            <label>
              <span>门店名称</span>
              <el-input :model-value="selectedStoreName" disabled />
            </label>
            <label>
              <span>每档时长（小时）</span>
              <el-input-number v-model="scheduleForm.slotHours" :min="0.5" :step="0.5" :precision="1" style="width: 100%" />
            </label>
            <label>
              <span>上午上班</span>
              <el-time-select
                v-model="scheduleForm.morningStart"
                start="08:00"
                step="00:30"
                end="12:30"
                placeholder="开始时间"
                style="width: 100%"
              />
            </label>
            <label>
              <span>上午下班</span>
              <el-time-select
                v-model="scheduleForm.morningEnd"
                start="09:00"
                step="00:30"
                end="13:30"
                placeholder="结束时间"
                style="width: 100%"
              />
            </label>
            <label>
              <span>下午上班</span>
              <el-time-select
                v-model="scheduleForm.afternoonStart"
                start="12:00"
                step="00:30"
                end="18:30"
                placeholder="开始时间"
                style="width: 100%"
              />
            </label>
            <label>
              <span>下午下班</span>
              <el-time-select
                v-model="scheduleForm.afternoonEnd"
                start="13:00"
                step="00:30"
                end="22:00"
                placeholder="结束时间"
                style="width: 100%"
              />
            </label>
            <label class="full-span">
              <span>门店备注</span>
              <el-input v-model="scheduleForm.remark" placeholder="如 团购客资集中承接 / 医生面诊排档" />
            </label>
          </div>

          <div class="action-group">
            <el-button type="primary" :loading="saving" @click="saveSchedule">保存档期</el-button>
            <el-button @click="resetScheduleForm">重置当前门店</el-button>
          </div>

          <div class="table-note">
            当前设置下，{{ selectedStoreName || '当前门店' }}每天可排 {{ draftCapacity }} 个档位。
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>门店日历</h3>
          <p>选择门店后查看每天已预约人数和剩余档位，满档日期会直接显示“满”。</p>
        </div>
      </div>

      <div class="calendar-layout">
        <div class="calendar-side">
          <el-calendar v-model="calendarDate" class="appointment-calendar" data-qa="store-schedule-calendar">
            <template #date-cell="{ data }">
              <button type="button" class="calendar-cell schedule-calendar-cell" @click="selectedDate = data.day">
                <span class="calendar-cell__day">{{ Number(data.day.split('-').pop()) }}</span>
                <span v-if="bookedCount(data.day)" class="calendar-cell__event">
                  已约 {{ bookedCount(data.day) }}/{{ selectedCapacity || 0 }}
                </span>
                <span v-if="isDateFull(data.day)" class="calendar-cell__full">满</span>
              </button>
            </template>
          </el-calendar>
        </div>

        <div class="calendar-side">
          <div data-qa="store-schedule-day-card" class="detail-card calendar-day-card">
            <h3>{{ selectedStoreName }} - {{ selectedDate }}</h3>
            <p>已预约 {{ bookedCount(selectedDate) }} 位客户，剩余 {{ remainingCount(selectedDate) }} 个档位。</p>

            <div class="chip-row">
              <el-tag effect="plain">上午 {{ scheduleForm.morningStart }} - {{ scheduleForm.morningEnd }}</el-tag>
              <el-tag effect="plain">下午 {{ scheduleForm.afternoonStart }} - {{ scheduleForm.afternoonEnd }}</el-tag>
              <el-tag type="success" effect="plain">{{ scheduleForm.slotHours }} 小时/档</el-tag>
            </div>

            <div v-if="selectedDateAppointments.length" class="binding-list">
              <div v-for="item in selectedDateAppointments" :key="item.id" class="binding-item">
                <strong>{{ item.customerName || item.orderNo }}</strong>
                <span>{{ formatDateTime(item.appointmentTime) }}</span>
              </div>
            </div>
            <p v-else class="text-secondary">当天暂无已预约客户。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchStoreScheduleConfigs, saveStoreScheduleConfigs } from '../api/systemConfig'
import { fetchAppointments } from '../api/workbench'
import {
  calculateStoreCapacity,
  getStoreScheduleConfig,
  loadSystemConsoleState,
  saveSystemConsoleState
} from '../utils/systemConsoleStore'
import { formatDateTime } from '../utils/format'

const state = reactive(loadSystemConsoleState())
const loading = ref(false)
const saving = ref(false)
const orders = ref([])
const selectedStoreName = ref(state.storeScheduleConfigs[0]?.storeName || '')
const calendarDate = ref(new Date())
const selectedDate = ref(formatDate(new Date()))
const scheduleForm = reactive(createScheduleForm())

const storeConfigs = computed(() => state.storeScheduleConfigs || [])
const selectedConfig = computed(() => getStoreScheduleConfig(state, selectedStoreName.value))
const selectedCapacity = computed(() => calculateStoreCapacity(selectedConfig.value))
const draftCapacity = computed(() => calculateStoreCapacity(scheduleForm))
const selectedDateAppointments = computed(() =>
  orders.value
    .filter((item) => item.storeName === selectedStoreName.value && formatDate(item.appointmentTime) === selectedDate.value)
    .sort((left, right) => String(left.appointmentTime || '').localeCompare(String(right.appointmentTime || '')))
)
const fullDayCount = computed(() => {
  const currentMonth = formatMonth(calendarDate.value)
  return Array.from(new Set(
    orders.value
      .filter((item) => item.storeName === selectedStoreName.value && formatMonth(item.appointmentTime) === currentMonth)
      .map((item) => formatDate(item.appointmentTime))
      .filter((day) => isDateFull(day))
  )).length
})

watch(
  selectedConfig,
  (value) => {
    Object.assign(scheduleForm, createScheduleForm(value))
  },
  { immediate: true }
)

function createScheduleForm(config = null) {
  return {
    morningStart: config?.morningStart || '09:00',
    morningEnd: config?.morningEnd || '12:00',
    afternoonStart: config?.afternoonStart || '13:30',
    afternoonEnd: config?.afternoonEnd || '18:00',
    slotHours: Number(config?.slotHours || 1),
    remark: config?.remark || ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function selectStore(storeName) {
  selectedStoreName.value = storeName
}

function resetScheduleForm() {
  Object.assign(scheduleForm, createScheduleForm(selectedConfig.value))
}

async function saveSchedule() {
  if (!selectedStoreName.value) {
    ElMessage.warning('请先选择门店')
    return
  }
  if (draftCapacity.value <= 0) {
    ElMessage.warning('请检查上下班时间和每档时长，当前可排档位数需大于 0')
    return
  }

  const nextConfigs = [...state.storeScheduleConfigs]
  const index = nextConfigs.findIndex((item) => item.storeName === selectedStoreName.value)
  if (index < 0) {
    return
  }

  nextConfigs[index] = {
    ...nextConfigs[index],
    ...scheduleForm,
    slotHours: Number(scheduleForm.slotHours || 1)
  }

  saving.value = true
  try {
    await saveStoreScheduleConfigs(nextConfigs)
    replaceState({
      ...state,
      storeScheduleConfigs: nextConfigs
    })
    ElMessage.success('门店档期已保存并同步到系统配置')
  } finally {
    saving.value = false
  }
}

async function loadStoreScheduleConfigs() {
  loading.value = true
  try {
    const configs = await fetchStoreScheduleConfigs()
    if (Array.isArray(configs) && configs.length) {
      replaceState({
        ...state,
        storeScheduleConfigs: configs
      })
      if (!selectedStoreName.value || !configs.some((item) => item.storeName === selectedStoreName.value)) {
        selectedStoreName.value = configs[0]?.storeName || ''
      }
    }
  } catch {
  } finally {
    loading.value = false
  }
}

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

function formatMonth(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toISOString().slice(0, 7)
}

function bookedCount(day) {
  return orders.value.filter((item) => item.storeName === selectedStoreName.value && formatDate(item.appointmentTime) === day).length
}

function remainingCount(day) {
  return Math.max(selectedCapacity.value - bookedCount(day), 0)
}

function isDateFull(day) {
  if (!day || selectedCapacity.value <= 0) {
    return false
  }
  return bookedCount(day) >= selectedCapacity.value
}

async function loadOrders() {
  try {
    orders.value = await fetchAppointments()
  } catch {
    orders.value = []
  }
}

loadStoreScheduleConfigs()
loadOrders()
</script>
