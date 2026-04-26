<template>
  <div class="plan-order-page" :class="{ 'plan-order-page--scan': scanMode, 'plan-order-page--view': readOnlyMode }">
    <section v-if="detail" v-loading="detailLoading" class="service-shell">
      <header class="service-hero">
        <div class="service-hero__main">
          <p class="service-hero__eyebrow">{{ heroEyebrow }}</p>
          <div class="service-hero__headline">
            <h2>{{ pageTitle }}</h2>
          </div>
          <div class="service-hero__customer">
            <strong>{{ customerDisplayName }}</strong>
            <span>{{ customerDisplayPhone }}</span>
            <span>{{ storeDisplayName }}</span>
          </div>
          <div class="service-hero__meta">
            <span>预约 {{ appointmentLabel }}</span>
            <span v-if="detail.order?.sourceChannel">来源 {{ formatChannel(detail.order?.sourceChannel) }}</span>
            <span v-if="detail.order?.type">类型 {{ formatOrderType(detail.order?.type) }}</span>
          </div>
        </div>

        <div class="service-hero__side">
          <div class="service-hero__status">
            <el-tag :type="serviceStage.tagType">
              {{ serviceStage.label }}
            </el-tag>
            <el-tag :type="statusTagType(detail.order?.verificationStatus || 'UNVERIFIED')">
              {{ formatVerificationStatus(detail.order?.verificationStatus || 'UNVERIFIED') }}
            </el-tag>
          </div>

          <div v-if="!scanMode" class="service-hero__actions">
            <el-button plain @click="goBackToOrders">返回订单列表</el-button>
            <el-button v-if="readOnlyMode" type="primary" plain @click="switchToEditMode">继续填写</el-button>
          </div>
        </div>
      </header>

      <section class="service-overview" :class="{ 'service-overview--scan': scanMode }">
        <article
          v-for="item in overviewCards"
          :key="item.label"
          class="service-overview__card"
          :class="`service-overview__card--${item.tone || 'default'}`"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <div class="service-workspace" :class="{ 'service-workspace--scan': scanMode, 'service-workspace--view': readOnlyMode }">
        <main class="service-main">
          <section class="service-card service-card--verify">
            <div class="service-card__header service-card__header--stack">
              <h3>{{ isVerified ? '核验状态' : '核验后填写服务单' }}</h3>
            </div>

            <div class="verify-panel" :class="{ 'verify-panel--readonly': readOnlyMode }">
              <template v-if="isVerified">
                <div class="verify-panel__state">
                  <el-tag type="success">已完成核验</el-tag>
                  <span>{{ formatDateTime(detail.order?.verificationTime) || '核验时间待记录' }}</span>
                  <span v-if="!scanMode && detail.order?.verificationCode">核验码 {{ detail.order?.verificationCode }}</span>
                </div>
              </template>

              <template v-else-if="!readOnlyMode">
                <div class="verify-panel__actions" :class="{ 'verify-panel__actions--scan': scanMode }">
                  <el-button type="primary" size="large" @click="openCameraScanner">摄像头扫码核验</el-button>
                  <div class="verify-panel__manual">
                    <el-input ref="verificationInputRef" v-model="verificationCode" placeholder="请输入核验码" class="verify-panel__input" />
                    <el-button @click="handleCodeVerify">输码核验</el-button>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="verify-panel__state verify-panel__state--pending">
                  <el-tag type="warning">未核验</el-tag>
                  <span>该服务单当前未完成核验。</span>
                </div>
              </template>
            </div>
          </section>

          <div v-if="!readOnlyMode && !isVerified" class="service-lock-banner">
            完成核验后可继续填写服务单。
          </div>

          <section class="service-card service-card--form-start" :class="{ 'service-card--locked': !readOnlyMode && !isVerified }">
            <div class="service-card__header">
              <h3>基础信息</h3>
            </div>

            <div class="service-form-grid">
              <div v-if="!scanMode" class="service-field">
                <label>当前角色</label>
                <div class="service-field__display">{{ formatRoleCode(serviceForm.currentRoleCode) }}</div>
              </div>
              <div class="service-field" :class="{ 'service-field--full': scanMode }">
                <label>职业</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ fieldText(serviceForm.profession) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.profession"
                  :disabled="!isVerified"
                  placeholder="请输入客户职业"
                />
              </div>
            </div>
          </section>

          <section class="service-card" :class="{ 'service-card--locked': !readOnlyMode && !isVerified }">
            <div class="service-card__header">
              <h3>服务确认</h3>
            </div>

            <div class="service-form-grid">
              <div class="service-field service-field--full">
                <label>到店需求</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display service-field__display--multiline">{{ fieldText(serviceForm.serviceRequirement) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.serviceRequirement"
                  type="textarea"
                  :rows="4"
                  :disabled="!isVerified"
                  placeholder="请输入到店需求"
                />
              </div>

              <div class="service-field service-field--full">
                <label>造型确认</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display service-field__display--multiline">{{ fieldText(serviceForm.styleConfirmation) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.styleConfirmation"
                  type="textarea"
                  :rows="3"
                  :disabled="!isVerified"
                  placeholder="请输入造型确认"
                />
              </div>

              <div class="service-field service-field--full">
                <label>服务项目</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ joinDisplay(serviceForm.serviceItems) }}</div>
                </template>
                <el-checkbox-group v-else v-model="serviceForm.serviceItems" :disabled="!isVerified">
                  <el-checkbox v-for="item in serviceItemOptions" :key="item" :value="item">{{ item }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="service-field">
                <label>预计时长</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ fieldText(serviceForm.serviceDuration) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.serviceDuration"
                  :disabled="!isVerified"
                  placeholder="如 3-4 小时"
                />
              </div>

              <div class="service-field">
                <label>签名确认</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ fieldText(serviceForm.signature) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.signature"
                  :disabled="!isVerified"
                  placeholder="请输入确认人"
                />
              </div>
            </div>
          </section>

          <section class="service-card" :class="{ 'service-card--locked': !readOnlyMode && !isVerified }">
            <div class="service-card__header">
              <h3>偏好与补充</h3>
            </div>

            <div class="service-form-grid">
              <div class="service-field service-field--full">
                <label>喜欢风格</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ joinDisplay(serviceForm.preferredStyles) }}</div>
                </template>
                <el-checkbox-group v-else v-model="serviceForm.preferredStyles" :disabled="!isVerified">
                  <el-checkbox v-for="item in styleOptions" :key="item" :value="item">{{ item }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="service-field service-field--full">
                <label>喜欢构图</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ joinDisplay(serviceForm.preferredScenes) }}</div>
                </template>
                <el-checkbox-group v-else v-model="serviceForm.preferredScenes" :disabled="!isVerified">
                  <el-checkbox v-for="item in sceneOptions" :key="item" :value="item">{{ item }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="service-field">
                <label>加选张数</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ String(serviceForm.addOnCount ?? 0) }}</div>
                </template>
                <el-input-number
                  v-else
                  v-model="serviceForm.addOnCount"
                  :disabled="!isVerified"
                  :min="0"
                  controls-position="right"
                />
              </div>

              <div class="service-field service-field--full">
                <label>加选内容</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display service-field__display--multiline">{{ fieldText(serviceForm.addOnContent) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.addOnContent"
                  type="textarea"
                  :rows="3"
                  :disabled="!isVerified"
                  placeholder="请输入加选内容"
                />
              </div>

              <div v-if="!scanMode" class="service-field service-field--full">
                <label>内部备注</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display service-field__display--multiline">{{ fieldText(serviceForm.internalRemark) }}</div>
                </template>
                <el-input
                  v-else
                  v-model="serviceForm.internalRemark"
                  type="textarea"
                  :rows="3"
                  :disabled="!isVerified"
                  placeholder="请输入内部备注"
                />
              </div>
            </div>
          </section>

          <section v-if="showRoleAssignment" class="service-card" :class="{ 'service-card--locked': !isVerified }">
            <div class="service-card__header">
              <h3>角色分配</h3>
            </div>

            <div class="role-grid">
              <article v-for="role in roleCards" :key="role.roleCode" class="role-card">
                <div class="role-card__header">
                  <strong>{{ formatRoleCode(role.roleCode) }}</strong>
                  <span>{{ role.current?.userName || '未分配' }}</span>
                </div>
                <div class="quick-button-row">
                  <el-button
                    v-for="staff in role.staffOptions"
                    :key="staff.userId"
                    size="small"
                    :type="role.current?.userId === staff.userId ? 'primary' : 'default'"
                    :disabled="!isVerified || !canManageRoles"
                    @click="handleAssignRole(role.roleCode, staff.userId)"
                  >
                    {{ staff.userName }}
                  </el-button>
                </div>
              </article>
            </div>
          </section>

          <div v-if="!readOnlyMode" class="service-footer-actions">
            <el-button type="primary" :disabled="!isVerified" :loading="savingServiceForm" @click="handleSaveServiceForm">
              保存服务单
            </el-button>
          </div>
        </main>

        <aside v-if="!scanMode" class="service-side">
          <section class="service-side-card">
            <div class="service-side-card__heading">
              <h3>{{ readOnlyMode ? '服务档案' : '订单摘要' }}</h3>
              <small>订单号 {{ detail.order?.orderNo || '--' }}</small>
            </div>
            <div class="service-side-list">
              <div v-for="item in orderSummaryItems" :key="item.label" class="service-side-list__item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section v-if="showTimelineCard" class="service-side-card">
            <h3>{{ readOnlyMode ? '服务轨迹' : '履约动作' }}</h3>
            <div v-if="showFlowActions" class="service-side-actions">
              <el-button type="primary" :disabled="!canArrive || !isVerified" @click="handlePlanAction('arrive')">到店</el-button>
              <el-button type="warning" :disabled="!canStart || !isVerified" @click="handlePlanAction('start')">开始服务</el-button>
              <el-button type="success" :disabled="!canFinish || !isVerified" @click="handlePlanAction('finish')">完成服务</el-button>
            </div>
            <div class="service-side-list service-side-list--timeline">
              <div v-for="item in timelineItems" :key="item.label" class="service-side-list__item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section v-if="currentRoles.length" class="service-side-card">
            <h3>当前分工</h3>
            <div class="service-role-tags">
              <div v-for="role in currentRoles" :key="`${role.roleCode}-${role.userId}`" class="service-role-tag">
                <span>{{ formatRoleCode(role.roleCode) }}</span>
                <strong>{{ role.userName || '--' }}</strong>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </section>

    <section v-else class="panel empty-panel">
      <el-empty :description="scanMode ? '服务单不存在、已失效或无权限访问' : '请选择一个服务单'" />
    </section>

    <el-dialog
      v-model="scannerDialogVisible"
      title="摄像头扫码核验"
      width="720px"
      destroy-on-close
      @closed="stopCameraScanner"
    >
      <div class="scanner-dialog">
        <div class="scanner-dialog__viewport">
          <video ref="scannerVideoRef" autoplay muted playsinline class="scanner-dialog__video"></video>
          <div class="scanner-dialog__frame"></div>
        </div>
        <div class="scanner-dialog__footer">
          <p>{{ scannerHint }}</p>
          <p v-if="scannerError" class="scanner-dialog__error">{{ scannerError }}</p>
          <div class="action-group">
            <el-button @click="scannerDialogVisible = false">关闭</el-button>
            <el-button v-if="scannerError" text type="primary" @click="restartCameraScanner">重新扫码</el-button>
            <el-button text type="primary" @click="useManualVerify">改为输码核验</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { arrivePlanOrder, assignPlanOrderRole, finishPlanOrder, startPlanOrder } from '../api/actions'
import { saveOrderServiceDetail, verifyOrderVoucher } from '../api/order'
import { fetchPlanOrderDetail, fetchStaffOptions } from '../api/workbench'
import { currentUser } from '../utils/auth'
import { loadSystemConsoleState } from '../utils/systemConsoleStore'
import {
  formatChannel,
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatOrderType,
  formatRoleCode,
  formatVerificationStatus,
  normalize,
  statusTagType
} from '../utils/format'

const route = useRoute()
const router = useRouter()
const detailLoading = ref(false)
const savingServiceForm = ref(false)
const detail = ref(null)
const staffOptions = ref([])
const verificationCode = ref('')
const verificationInputRef = ref(null)
const scannerDialogVisible = ref(false)
const scannerAutoOpened = ref(false)
const scannerVideoRef = ref(null)
const scannerHint = ref('请将核验二维码置于取景框内')
const scannerError = ref('')
const state = reactive(loadSystemConsoleState())
const serviceForm = reactive(createServiceForm())
const STORE_ROLE_CODES = ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR']
const ROLE_ASSIGNMENT_MANAGER_CODES = ['ADMIN', 'STORE_MANAGER']
const isStoreRoleCode = (roleCode) => STORE_ROLE_CODES.includes(normalize(roleCode || ''))
const serviceRoleOrder = (roleCode) => {
  const index = STORE_ROLE_CODES.indexOf(normalize(roleCode || ''))
  return index === -1 ? STORE_ROLE_CODES.length : index
}

const styleOptions = ['自然', '高级感', '轻奢', '活力', '知性']
const sceneOptions = ['特写', '三分', '五分', '七分', '全身']
const serviceItemOptions = ['服装造型', '精修底片', '拍摄服务', '加选服务']

const scanMode = computed(() => route.meta?.scanMode === true || String(route.query.scan || '') === '1')
const readOnlyMode = computed(() => String(route.query.mode || '') === 'view')
const isVerified = computed(() => normalize(detail.value?.order?.verificationStatus || 'UNVERIFIED') === 'VERIFIED')
const hasSavedServiceDetail = computed(() => Boolean(String(detail.value?.order?.serviceDetailJson || '').trim()))
const canManageRoles = computed(() => ROLE_ASSIGNMENT_MANAGER_CODES.includes(normalize(currentUser.value?.roleCode || '')))
const showRoleAssignment = computed(() => !scanMode.value && !readOnlyMode.value && canManageRoles.value)
const showFlowActions = computed(() => !scanMode.value && !readOnlyMode.value && isVerified.value)
const currentRoles = computed(() =>
  (detail.value?.currentRoles || [])
    .filter((item) => isStoreRoleCode(item?.roleCode))
    .sort((left, right) => serviceRoleOrder(left?.roleCode) - serviceRoleOrder(right?.roleCode))
)
const heroEyebrow = computed(() => {
  if (scanMode.value) {
    return detail.value?.order?.storeName || '服务单核验'
  }
  if (readOnlyMode.value) {
    return '服务单详情'
  }
  return activeTemplate.value?.templateName || '服务单'
})
const pageTitle = computed(() => {
  if (readOnlyMode.value) {
    return activeTemplate.value?.title || '服务单详情'
  }
  return activeTemplate.value?.title || '服务单'
})
const customerDisplayName = computed(() => detail.value?.customer?.name || detail.value?.order?.customerName || '未绑定客户')
const customerDisplayPhone = computed(() => detail.value?.customer?.phone || detail.value?.order?.customerPhone || '--')
const storeDisplayName = computed(() => detail.value?.order?.storeName || '未分配门店')
const appointmentLabel = computed(() => formatDateTime(detail.value?.order?.appointmentTime) || '未预约')
const serviceStage = computed(() => {
  if (detail.value?.summary?.finishTime) {
    return { label: '已完成', tagType: 'success' }
  }
  if (detail.value?.summary?.startTime) {
    return { label: '服务中', tagType: 'primary' }
  }
  if (detail.value?.summary?.arriveTime) {
    return { label: '已到店', tagType: 'warning' }
  }
  if (detail.value?.summary?.planOrderStatus) {
    return { label: '待到店', tagType: 'info' }
  }
  return { label: hasSavedServiceDetail.value ? '已填写' : '待填写', tagType: 'info' }
})
const canArrive = computed(() => detail.value?.summary?.planOrderStatus === 'arrived' && !detail.value?.summary?.arriveTime)
const canStart = computed(
  () => detail.value?.summary?.planOrderStatus === 'arrived' && !!detail.value?.summary?.arriveTime && !detail.value?.summary?.startTime
)
const canFinish = computed(
  () =>
    detail.value?.summary?.planOrderStatus === 'servicing' &&
    !!detail.value?.summary?.startTime &&
    !detail.value?.summary?.finishTime &&
    !!detail.value?.currentRoles?.length &&
    hasSavedServiceDetail.value
)
const roleCards = computed(() => {
  const currentRoleMap = new Map(currentRoles.value.map((item) => [item.roleCode, item]))
  return (staffOptions.value || []).map((role) => ({
    ...role,
    current: currentRoleMap.get(role.roleCode)
  }))
})
const activeTemplate = computed(() => {
  const storeName = detail.value?.order?.storeName
  const binding = (state.serviceFormBindings || []).find((item) => item.storeName === storeName && item.enabled === 1)
  return (
    (state.serviceFormTemplates || []).find((item) => item.id === binding?.templateId) ||
    state.serviceFormTemplates?.find((item) => item.enabled === 1) ||
    null
  )
})
const overviewCards = computed(() => {
  const cards = [
    { label: '订单金额', value: formatMoney(detail.value?.order?.amount), tone: 'accent' },
    { label: '预约时间', value: appointmentLabel.value, tone: 'default' },
    {
      label: '核验状态',
      value: formatVerificationStatus(detail.value?.order?.verificationStatus || 'UNVERIFIED'),
      tone: isVerified.value ? 'success' : 'warning'
    },
    { label: '履约进度', value: serviceStage.value.label, tone: serviceStage.value.tagType === 'success' ? 'success' : 'default' },
    { label: '最近更新时间', value: formatDateTime(detail.value?.order?.updateTime) || '--', tone: 'default' }
  ]
  return scanMode.value ? cards.slice(0, 4) : cards
})
const orderSummaryItems = computed(() => [
  { label: '客户姓名', value: customerDisplayName.value },
  { label: '手机号码', value: customerDisplayPhone.value },
  { label: '订单状态', value: formatOrderStatus(detail.value?.order?.status) },
  { label: '订单类型', value: formatOrderType(detail.value?.order?.type) },
  { label: '来源渠道', value: formatChannel(detail.value?.order?.sourceChannel) },
  { label: '服务单状态', value: hasSavedServiceDetail.value ? '已填写' : '待填写' }
])
const timelineItems = computed(() => [
  { label: '到店时间', value: formatDateTime(detail.value?.summary?.arriveTime) || '--' },
  { label: '开始时间', value: formatDateTime(detail.value?.summary?.startTime) || '--' },
  { label: '完成时间', value: formatDateTime(detail.value?.summary?.finishTime) || '--' }
])
const showTimelineCard = computed(
  () => !scanMode.value && (readOnlyMode.value || showFlowActions.value || timelineItems.value.some((item) => item.value !== '--'))
)

let scannerStream = null
let scannerFrameId = 0
let barcodeDetector = null

function createServiceForm(payload = {}) {
  return {
    currentRoleCode: normalize(payload.currentRoleCode || currentUser.value?.roleCode || 'STORE_SERVICE'),
    profession: String(payload.profession || ''),
    preferredStyles: normalizeArray(payload.preferredStyles),
    preferredScenes: normalizeArray(payload.preferredScenes),
    serviceRequirement: String(payload.serviceRequirement || ''),
    styleConfirmation: String(payload.styleConfirmation || ''),
    serviceItems: normalizeArray(payload.serviceItems),
    serviceDuration: String(payload.serviceDuration || ''),
    addOnCount: Number.isFinite(Number(payload.addOnCount)) ? Number(payload.addOnCount) : 0,
    addOnContent: String(payload.addOnContent || ''),
    signature: String(payload.signature || ''),
    internalRemark: String(payload.internalRemark || '')
  }
}

function applyServiceForm(payload) {
  Object.assign(serviceForm, createServiceForm(payload || {}))
}

function normalizeArray(value) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item || '').trim()).filter(Boolean)
  }
  if (typeof value === 'string' && value.trim()) {
    return value
      .split(/[、,，]/)
      .map((item) => item.trim())
      .filter(Boolean)
  }
  return []
}

function joinDisplay(values) {
  return normalizeArray(values).join('、') || '未填写'
}

function fieldText(value) {
  return String(value || '').trim() || '未填写'
}

async function loadStaffOptions() {
  try {
    staffOptions.value = (await fetchStaffOptions())
      .filter((role) => isStoreRoleCode(role?.roleCode))
      .sort((left, right) => serviceRoleOrder(left?.roleCode) - serviceRoleOrder(right?.roleCode))
  } catch {
    staffOptions.value = []
  }
}

async function ensureCurrentRoleBound(planOrderId) {
  if (!detail.value || !currentUser.value?.roleCode || !currentUser.value?.userId) {
    return
  }
  const normalizedRole = normalize(currentUser.value.roleCode)
  if (!isStoreRoleCode(normalizedRole)) {
    return
  }
  const existing = currentRoles.value.some((item) => normalize(item.roleCode) === normalizedRole)
  const roleExists = (staffOptions.value || []).some((item) => normalize(item.roleCode) === normalizedRole)
  if (existing || !roleExists || normalize(detail.value.summary?.planOrderStatus) === 'FINISHED') {
    return
  }
  await assignPlanOrderRole({
    planOrderId,
    roleCode: normalizedRole,
    userId: currentUser.value.userId
  })
  await loadDetail(planOrderId)
}

async function loadDetail(planOrderId) {
  if (!planOrderId) {
    detail.value = null
    applyServiceForm()
    return
  }
  detailLoading.value = true
  try {
    detail.value = await fetchPlanOrderDetail(planOrderId)
    const parsed = safeParseJson(detail.value?.order?.serviceDetailJson)
    applyServiceForm(parsed)
    serviceForm.currentRoleCode = normalize(serviceForm.currentRoleCode || currentUser.value?.roleCode || 'STORE_SERVICE')
    verificationCode.value = detail.value?.order?.verificationCode || ''
    await ensureCurrentRoleBound(planOrderId)
  } catch {
    detail.value = null
    applyServiceForm()
  } finally {
    detailLoading.value = false
  }
}

async function loadPageContext() {
  const planOrderId = Number(route.params.id)
  if (!planOrderId) {
    detail.value = null
    applyServiceForm()
    return
  }
  await loadStaffOptions()
  await loadDetail(planOrderId)
}

async function handlePlanAction(action) {
  const payload = {
    planOrderId: Number(route.params.id)
  }
  if (action === 'arrive') {
    await arrivePlanOrder(payload)
  } else if (action === 'start') {
    await startPlanOrder(payload)
  } else if (action === 'finish') {
    await finishPlanOrder(payload)
  }
  ElMessage.success('履约状态已更新')
  await loadPageContext()
}

async function handleAssignRole(roleCode, userId) {
  if (!canManageRoles.value) {
    return
  }
  await assignPlanOrderRole({
    planOrderId: Number(route.params.id),
    roleCode,
    userId
  })
  ElMessage.success('角色分配已更新')
  await loadPageContext()
}

function openCameraScanner() {
  scannerHint.value = '请将核验二维码置于取景框内'
  scannerError.value = ''
  scannerDialogVisible.value = true
}

function useManualVerify() {
  scannerDialogVisible.value = false
  void focusVerificationInput()
}

function restartCameraScanner() {
  scannerError.value = ''
  scannerHint.value = '正在重新打开摄像头'
  stopCameraScanner()
  void startCameraScanner()
}

async function startCameraScanner() {
  if (typeof window === 'undefined' || !scannerDialogVisible.value) {
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    scannerError.value = '当前浏览器不支持摄像头，请改用输码核验。'
    return
  }
  if (!('BarcodeDetector' in window)) {
    scannerError.value = '当前浏览器不支持二维码识别，请改用输码核验。'
    return
  }

  try {
    scannerError.value = ''
    scannerHint.value = '正在打开摄像头，请将核验二维码置于取景框内'
    await nextTick()
    const video = scannerVideoRef.value
    if (!video) {
      return
    }
    scannerStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' }
      },
      audio: false
    })
    video.srcObject = scannerStream
    await video.play()
    barcodeDetector = new window.BarcodeDetector({ formats: ['qr_code'] })
    scannerHint.value = '请将核验二维码完整置于取景框中'
    scanCameraFrame()
  } catch (error) {
    stopCameraScanner()
    scannerError.value = resolveCameraError(error)
  }
}

async function scanCameraFrame() {
  if (!scannerDialogVisible.value || !scannerVideoRef.value || !barcodeDetector) {
    return
  }
  try {
    const video = scannerVideoRef.value
    if (video.readyState >= 2) {
      const results = await barcodeDetector.detect(video)
      const matched = results.find((item) => String(item?.rawValue || '').trim())
      if (matched) {
        await handleCameraDetected(String(matched.rawValue || '').trim())
        return
      }
    }
  } catch {
    scannerError.value = '摄像头已打开，但二维码暂未识别成功，可继续尝试或改用输码核验。'
  }
  scannerFrameId = window.requestAnimationFrame(() => {
    void scanCameraFrame()
  })
}

async function handleCameraDetected(code) {
  stopCameraScanner()
  verificationCode.value = code
  try {
    await verifyCurrentOrder(code, 'SCAN_CAMERA')
    scannerDialogVisible.value = false
  } catch {
    scannerError.value = '核验失败，请核对二维码后重试，或改用输码核验。'
    scannerDialogVisible.value = true
  }
}

function stopCameraScanner() {
  if (scannerFrameId) {
    window.cancelAnimationFrame(scannerFrameId)
    scannerFrameId = 0
  }
  if (scannerStream) {
    scannerStream.getTracks().forEach((track) => track.stop())
    scannerStream = null
  }
  if (scannerVideoRef.value) {
    scannerVideoRef.value.pause?.()
    scannerVideoRef.value.srcObject = null
  }
  barcodeDetector = null
}

function resolveCameraError(error) {
  const message = String(error?.message || '')
  if (message.includes('Permission') || message.includes('denied')) {
    return '未获得摄像头权限，请允许访问后重试，或改用输码核验。'
  }
  return '摄像头暂时无法使用，请改用输码核验。'
}

async function handleCodeVerify() {
  if (!String(verificationCode.value || '').trim()) {
    ElMessage.warning('请输入核验码')
    await focusVerificationInput()
    return
  }
  await verifyCurrentOrder(String(verificationCode.value || '').trim(), 'CODE')
}

async function verifyCurrentOrder(code, method) {
  if (!detail.value?.order?.id) {
    return
  }
  const response = await verifyOrderVoucher({
    orderId: detail.value.order.id,
    verificationCode: code,
    verificationMethod: method
  })
  detail.value.order = {
    ...detail.value.order,
    ...response
  }
  verificationCode.value = response.verificationCode || code
  ElMessage.success('核验成功，可继续填写服务单')
  await scrollToFormStart()
}

async function handleSaveServiceForm() {
  if (!detail.value?.order?.id) {
    return
  }
  savingServiceForm.value = true
  try {
    const response = await saveOrderServiceDetail({
      orderId: detail.value.order.id,
      serviceRequirement: serviceForm.serviceRequirement,
      serviceDetailJson: JSON.stringify(createServiceForm(serviceForm))
    })
    detail.value.order = {
      ...detail.value.order,
      ...response
    }
    ElMessage.success('服务单已保存')
  } finally {
    savingServiceForm.value = false
  }
}

async function scrollToFormStart() {
  await nextTick()
  if (typeof window === 'undefined') {
    return
  }
  const target = document.querySelector('.service-card--form-start')
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  const firstEditable = target?.querySelector('input, textarea')
  firstEditable?.focus?.()
}

async function focusVerificationInput() {
  await nextTick()
  verificationInputRef.value?.focus?.()
}

function safeParseJson(value) {
  if (!value) {
    return null
  }
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function goBackToOrders() {
  router.push('/store-service/orders')
}

function switchToEditMode() {
  router.push(`/plan-orders/${route.params.id}`)
}

watch(
  () => `${route.params.id || ''}|${route.query.mode || ''}|${route.query.scan || ''}`,
  () => {
    scannerAutoOpened.value = false
    void loadPageContext()
  },
  { immediate: true }
)

watch(scannerDialogVisible, (visible) => {
  if (visible) {
    void startCameraScanner()
  } else {
    stopCameraScanner()
  }
})

watch(
  () => ({
    scanMode: scanMode.value,
    readOnlyMode: readOnlyMode.value,
    verified: isVerified.value,
    orderId: detail.value?.order?.id || null
  }),
  (state) => {
    if (!state.scanMode || state.readOnlyMode || state.verified || !state.orderId || scannerAutoOpened.value) {
      return
    }
    scannerAutoOpened.value = true
    openCameraScanner()
  }
)

onBeforeUnmount(() => {
  stopCameraScanner()
})
</script>

<style scoped>
.plan-order-page {
  min-width: 0;
  width: 100%;
}

.plan-order-page--scan {
  min-height: 100vh;
  padding: 24px 20px 56px;
  background: linear-gradient(180deg, #f4f7fb 0%, #eef3f8 100%);
}

.service-shell {
  display: grid;
  gap: 18px;
  width: 100%;
}

.plan-order-page--scan .service-shell {
  max-width: 940px;
  margin: 0 auto;
}

.service-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  padding: 24px 26px;
  border-radius: 26px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.98) 0%, rgba(246, 249, 252, 0.98) 100%);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.06);
}

.service-hero__main {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.service-hero__headline {
  display: grid;
  gap: 8px;
}

.service-hero__eyebrow {
  margin: 0;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.service-hero h2 {
  margin: 0;
  color: #0f172a;
  font-size: 36px;
  letter-spacing: -0.04em;
}

.service-hero__customer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
}

.service-hero__customer strong {
  color: #0f172a;
  font-size: 18px;
}

.service-hero__customer span {
  color: #64748b;
  font-size: 14px;
}

.service-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  color: #64748b;
  font-size: 14px;
}

.service-hero__meta-muted {
  color: #94a3b8;
}

.service-hero__side {
  display: grid;
  gap: 14px;
  justify-items: end;
}

.service-hero__status {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.service-hero__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.service-overview {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.service-overview--scan {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.service-overview__card {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.04);
}

.service-overview__card span {
  color: #64748b;
  font-size: 13px;
}

.service-overview__card strong {
  color: #0f172a;
  font-size: 18px;
  line-height: 1.4;
}

.service-overview__card--accent {
  background: linear-gradient(180deg, #173042 0%, #204357 100%);
  border-color: rgba(23, 48, 66, 0.8);
}

.service-overview__card--accent span,
.service-overview__card--accent strong {
  color: #ffffff;
}

.service-overview__card--success {
  background: linear-gradient(180deg, #f4fbf6 0%, #eef9f1 100%);
}

.service-overview__card--warning {
  background: linear-gradient(180deg, #fff9ec 0%, #fff5dd 100%);
}

.service-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
  align-items: start;
  width: 100%;
}

.service-workspace--scan {
  grid-template-columns: minmax(0, 1fr);
}

.service-workspace--view {
  grid-template-columns: minmax(0, 1.08fr) 320px;
}

.service-main,
.service-side {
  display: grid;
  gap: 18px;
  width: 100%;
}

.service-side {
  position: sticky;
  top: 0;
}

.service-card,
.service-side-card {
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.05);
}

.service-card {
  padding: 24px;
}

.service-side-card {
  padding: 20px;
}

.service-card__header,
.service-side-card h3 {
  margin: 0 0 18px;
}

.service-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.service-card__header--stack {
  align-items: flex-start;
  justify-content: flex-start;
}

.service-card__header h3,
.service-side-card h3 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  letter-spacing: -0.03em;
}

.service-side-card h3 {
  font-size: 20px;
}

.service-side-card__heading {
  display: grid;
  gap: 4px;
  margin-bottom: 18px;
}

.service-side-card__heading small {
  color: #94a3b8;
  font-size: 12px;
}

.service-fact-grid,
.service-form-grid {
  display: grid;
  gap: 14px;
}

.service-fact-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 18px;
}

.service-fact {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f8fbff 0%, #f4f7fb 100%);
  border: 1px solid rgba(59, 130, 246, 0.08);
}

.service-fact span,
.service-side-list__item span,
.service-field label {
  color: #64748b;
  font-size: 13px;
}

.service-fact strong,
.service-side-list__item strong {
  color: #0f172a;
  font-size: 16px;
  line-height: 1.5;
}

.verify-panel {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: 20px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.07);
}

.verify-panel__actions {
  display: grid;
  gap: 14px;
}

.verify-panel__actions > :deep(.el-button) {
  width: fit-content;
  min-width: 168px;
}

.verify-panel__actions--scan > :deep(.el-button) {
  width: 100%;
}

.verify-panel__manual {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.verify-panel__state {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  align-items: center;
  color: #64748b;
}

.verify-panel__state--pending {
  color: #92400e;
}

.verify-panel__input {
  width: 100%;
}

.service-lock-banner {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px dashed rgba(245, 158, 11, 0.48);
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.98) 0%, rgba(255, 247, 214, 0.92) 100%);
  color: #92400e;
  font-size: 14px;
  font-weight: 600;
}

.service-card--locked {
  border-color: rgba(245, 158, 11, 0.2);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(255, 251, 235, 0.92) 100%);
}

.service-form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.service-card--form-start {
  scroll-margin-top: 18px;
}

.service-field {
  display: grid;
  gap: 8px;
}

.service-field--full {
  grid-column: 1 / -1;
}

.service-field__display {
  min-height: 44px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: #0f172a;
  line-height: 1.7;
}

.service-field__display--multiline {
  white-space: pre-wrap;
}

.plan-order-page--view .service-card,
.plan-order-page--view .service-side-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 251, 255, 0.96) 100%);
}

.plan-order-page--view .service-field__display {
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.service-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.service-side-list {
  display: grid;
  gap: 12px;
}

.service-side-list__item {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.service-side-list--timeline .service-side-list__item {
  border-left: 3px solid rgba(37, 99, 235, 0.2);
}

.service-side-actions {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}

.service-side-actions :deep(.el-button) {
  width: 100%;
}

.service-role-tags {
  display: grid;
  gap: 10px;
}

.service-role-tag {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.service-role-tag span {
  color: #64748b;
  font-size: 13px;
}

.service-role-tag strong {
  color: #0f172a;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.role-card {
  padding: 18px;
  border-radius: 18px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.role-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.scanner-dialog {
  display: grid;
  gap: 18px;
}

.scanner-dialog__viewport {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  background: #08111f;
  min-height: 420px;
}

.scanner-dialog__video {
  display: block;
  width: 100%;
  height: 420px;
  object-fit: cover;
}

.scanner-dialog__frame {
  position: absolute;
  inset: 50%;
  width: min(72vw, 280px);
  height: min(72vw, 280px);
  transform: translate(-50%, -50%);
  border: 2px solid rgba(255, 255, 255, 0.92);
  border-radius: 28px;
  box-shadow: 0 0 0 999px rgba(8, 17, 31, 0.28);
}

.scanner-dialog__footer {
  display: grid;
  gap: 10px;
}

.scanner-dialog__footer p {
  margin: 0;
  color: #64748b;
}

.scanner-dialog__error {
  color: #dc2626 !important;
}

@media (max-width: 1360px) {
  .service-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .service-fact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .service-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .service-workspace {
    grid-template-columns: 1fr;
  }

  .service-side {
    order: -1;
  }
}

@media (max-width: 900px) {
  .service-hero {
    padding: 20px;
  }

  .service-hero,
  .service-card__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .service-hero__side,
  .service-hero__status,
  .service-hero__actions {
    justify-items: flex-start;
    justify-content: flex-start;
  }

  .service-form-grid,
  .role-grid,
  .verify-panel__manual,
  .service-fact-grid {
    grid-template-columns: 1fr;
  }

  .service-card,
  .service-side-card {
    padding: 20px;
  }

  .scanner-dialog__video,
  .scanner-dialog__viewport {
    min-height: 320px;
    height: 320px;
  }
}

@media (max-width: 640px) {
  .service-hero h2 {
    font-size: 30px;
  }

  .service-overview,
  .service-overview--scan {
    grid-template-columns: 1fr;
  }

  .service-hero__meta {
    display: grid;
    gap: 6px;
  }

  .service-footer-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .service-footer-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
