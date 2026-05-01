<template>
  <div class="plan-order-page" :class="{ 'plan-order-page--scan': scanMode, 'plan-order-page--view': readOnlyMode }">
    <section v-if="detail" v-loading="detailLoading" class="service-shell">
      <header class="service-header">
        <div class="service-header__main">
          <div class="service-header__headline">
            <h2>{{ pageTitle }}</h2>
            <div class="service-header__meta">
              <span>{{ customerDisplayName }}</span>
              <span>{{ customerDisplayPhone }}</span>
              <span>{{ storeDisplayName }}</span>
              <span>预约：{{ appointmentLabel }}</span>
              <span v-if="canViewAmounts">核销金额：{{ formatMoney(verificationAmount) }}</span>
              <span v-if="canViewAmounts">确认单金额：{{ serviceConfirmAmountLabel }}</span>
              <span v-if="serviceTemplateDisplay">模板：{{ serviceTemplateDisplay }}</span>
              <span>表单状态：{{ serviceFormStatusLabel }}</span>
              <span>核销：{{ formatVerificationStatus(detail.order?.verificationStatus || 'UNVERIFIED') }}</span>
              <span>履约：{{ serviceStage.label }}</span>
              <span v-if="detail.order?.orderNo" class="service-header__weak">订单号 {{ detail.order.orderNo }}</span>
            </div>
          </div>
        </div>
        <div v-if="!scanMode" class="service-header__actions">
          <el-button plain @click="goBackToOrders">返回订单列表</el-button>
          <el-button v-if="canSwitchToEditMode" type="primary" plain @click="switchToEditMode">继续填写</el-button>
        </div>
      </header>

      <div class="service-workspace" :class="{ 'service-workspace--single': scanMode }">
        <main class="service-main">
          <section class="service-card">
            <div class="service-card__header">
              <div>
                <h3>{{ isVerified ? '核销完成' : '先完成核销，再填写服务确认单' }}</h3>
              </div>
              <el-tag :type="isVerified ? 'success' : 'warning'">
                {{ isVerified ? '已核销' : '待核销' }}
              </el-tag>
            </div>

            <div class="verify-panel" :class="{ 'verify-panel--scan': scanMode }">
              <template v-if="isVerified">
                <div class="verify-panel__result">
                  <div class="verify-panel__result-icon">已</div>
                  <div class="verify-panel__result-body">
                    <strong>{{ readOnlyMode || isFinishedOrder ? '核销已完成，服务单只读查看' : '核销已完成，可以继续填写服务单' }}</strong>
                    <span>核销时间：{{ formatDateTime(detail.order?.verificationTime) || '--' }}</span>
                    <span v-if="detail.order?.verificationCode">核销码：{{ detail.order.verificationCode }}</span>
                  </div>
                </div>
              </template>

              <template v-else-if="!readOnlyMode">
                <div class="verify-panel__actions">
                  <button
                    v-if="isDepositOrder"
                    type="button"
                    class="verify-hero verify-hero--primary"
                    @click="handleDirectDepositVerify"
                  >
                    <strong>直接核销</strong>
                    <span>定金订单不用扫码或输码，直接进入后续流程</span>
                  </button>
                  <button v-else type="button" class="verify-hero verify-hero--primary" @click="openCameraScanner">
                    <strong>扫码核销</strong>
                    <span>优先使用摄像头快速核销</span>
                  </button>
                  <div v-if="!isDepositOrder" class="verify-hero verify-hero--manual">
                    <strong>输码核销</strong>
                    <div class="verify-panel__manual">
                      <el-input ref="verificationInputRef" v-model="verificationCode" placeholder="请输入核销码" />
                      <el-button @click="handleCodeVerify">确认核销</el-button>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="verify-panel__pending">
                  <span>当前服务单尚未核销，暂不可编辑。</span>
                </div>
              </template>
            </div>
          </section>

          <section class="service-card" :class="{ 'service-card--locked': !canEditForm }">
            <div class="service-card__header">
              <div>
                <h3>基础信息</h3>
              </div>
            </div>

            <div class="service-form-grid">
              <div class="service-field">
                <label>当前角色</label>
                <div class="service-field__display">{{ formatRoleCode(serviceForm.currentRoleCode) }}</div>
              </div>
              <div class="service-field">
                <label>职业</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ fieldText(serviceForm.profession) }}</div>
                </template>
                <el-input v-else v-model="serviceForm.profession" :disabled="!canEditForm" placeholder="请输入客户职业" />
              </div>
            </div>
          </section>

          <section class="service-card" :class="{ 'service-card--locked': !canEditForm }">
            <div class="service-card__header">
              <div>
                <h3>服务确认</h3>
              </div>
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
                  :disabled="!canEditForm"
                  placeholder="请输入客户本次到店需求"
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
                  :disabled="!canEditForm"
                  placeholder="请输入造型、风格或执行要点"
                />
              </div>

              <div class="service-field service-field--full">
                <label>服务项目</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ joinDisplay(serviceForm.serviceItems) }}</div>
                </template>
                <el-checkbox-group v-else v-model="serviceForm.serviceItems" :disabled="!canEditForm">
                  <el-checkbox v-for="item in serviceItemOptions" :key="item" :value="item">{{ item }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div v-if="canViewAmounts" class="service-field">
                <label>服务确认单金额</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ serviceConfirmAmountLabel }}</div>
                </template>
                <el-input-number
                  v-else
                  v-model="serviceForm.serviceConfirmAmount"
                  :disabled="!canEditForm"
                  :min="0"
                  :precision="2"
                  controls-position="right"
                />
              </div>

              <div class="service-field">
                <label>预计时长</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ fieldText(serviceForm.serviceDuration) }}</div>
                </template>
                <el-input v-else v-model="serviceForm.serviceDuration" :disabled="!canEditForm" placeholder="例如：3-4 小时" />
              </div>

              <div class="service-field">
                <label>确认人</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ fieldText(serviceForm.signature) }}</div>
                </template>
                <el-input v-else v-model="serviceForm.signature" :disabled="!canEditForm" placeholder="请输入确认人姓名" />
              </div>
            </div>
          </section>

          <section class="service-card" :class="{ 'service-card--locked': !canEditForm }">
            <div class="service-card__header">
              <div>
                <h3>偏好与补充</h3>
              </div>
            </div>

            <div class="service-form-grid">
              <div class="service-field service-field--full">
                <label>喜欢风格</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ joinDisplay(serviceForm.preferredStyles) }}</div>
                </template>
                <el-checkbox-group v-else v-model="serviceForm.preferredStyles" :disabled="!canEditForm">
                  <el-checkbox v-for="item in styleOptions" :key="item" :value="item">{{ item }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="service-field service-field--full">
                <label>喜欢构图</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ joinDisplay(serviceForm.preferredScenes) }}</div>
                </template>
                <el-checkbox-group v-else v-model="serviceForm.preferredScenes" :disabled="!canEditForm">
                  <el-checkbox v-for="item in sceneOptions" :key="item" :value="item">{{ item }}</el-checkbox>
                </el-checkbox-group>
              </div>

              <div class="service-field">
                <label>加选张数</label>
                <template v-if="readOnlyMode">
                  <div class="service-field__display">{{ String(serviceForm.addOnCount ?? 0) }}</div>
                </template>
                <el-input-number v-else v-model="serviceForm.addOnCount" :disabled="!canEditForm" :min="0" controls-position="right" />
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
                  :disabled="!canEditForm"
                  placeholder="请输入加选内容"
                />
              </div>

              <div class="service-field service-field--full">
                <label>纸质签名位置</label>
                <div class="table-note">
                  确认单打印后由客户在线下纸质单手写确认。
                </div>
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
                  :disabled="!canEditForm"
                  placeholder="请输入内部备注"
                />
              </div>
            </div>
          </section>
        </main>

        <aside v-if="!scanMode" class="service-side">
          <section class="service-card">
            <div class="service-card__header">
              <div>
                <h3>订单摘要</h3>
              </div>
            </div>
            <div class="side-list">
              <div v-for="item in orderSummaryItems" :key="item.label" class="side-list__item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>

          <section v-if="showRoleAssignment" class="service-card">
            <div class="service-card__header">
              <div>
                <h3>角色分配</h3>
              </div>
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
                    :disabled="!canEditForm"
                    @click="handleAssignRole(role.roleCode, staff.userId)"
                  >
                    {{ staff.userName }}
                  </el-button>
                </div>
              </article>
            </div>
          </section>

          <section class="service-card">
            <div class="service-card__header">
              <div>
                <h3>当前流程</h3>
                <p>{{ serviceNextStepHint }}</p>
              </div>
            </div>
            <div class="process-steps">
              <div
                v-for="item in serviceProcessSteps"
                :key="item.key"
                class="process-step"
                :class="{ 'is-done': item.done, 'is-current': item.current }"
              >
                <span>{{ item.label }}</span>
              </div>
            </div>
          </section>

          <section class="service-card">
            <div class="service-card__header">
              <div>
                <h3>业务轨迹</h3>
                <p>{{ flowTraceHint }}</p>
              </div>
            </div>
            <div class="side-list">
              <div v-for="item in displayTimelineItems" :key="item.label" class="side-list__item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>
        </aside>
      </div>

      <div v-if="!readOnlyMode" class="service-action-bar">
        <div class="service-action-bar__status">
          <span v-if="autosaveMessage">{{ autosaveMessage }}</span>
          <span v-else>{{ serviceActionHint }}</span>
        </div>
        <div class="action-group action-group--wrap">
          <el-button :loading="savingServiceForm" :disabled="!canEditForm" @click="handleSaveServiceForm()">保存草稿</el-button>
          <el-button :disabled="!hasSavedServiceDetail" @click="handlePrintServiceForm">打印确认单</el-button>
          <el-button type="primary" :loading="confirmingAction" :disabled="!canEditForm || !canConfirmAction" @click="handleConfirmAndAdvance">
            {{ confirmActionLabel }}
          </el-button>
          <el-button
            type="success"
            :loading="sendingToCustomer"
            :disabled="!canSendToCustomer"
            :title="sendToCustomerDisabledReason"
            @click="handleSaveAndSend"
          >
            保存并发送给客户
          </el-button>
        </div>
      </div>
    </section>

    <section v-else class="panel empty-panel">
      <el-empty :description="scanMode ? '服务单不存在或链接已失效，请联系门店重发。' : '请选择一个服务单'" />
    </section>

    <el-dialog v-model="scannerDialogVisible" title="扫码核销" width="720px" destroy-on-close @closed="stopCameraScanner">
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
            <el-button text type="primary" @click="useManualVerify">改为输码核销</el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  assignPlanOrderRole,
  confirmPlanOrderServiceForm,
  sendPlanOrderServiceForm,
  startPlanOrder,
  arrivePlanOrder,
  finishPlanOrder
} from '../api/actions'
import { saveOrderServiceDetail, verifyOrderVoucher } from '../api/order'
import { previewServiceFormTemplate } from '../api/serviceFormTemplate'
import { fetchPlanOrderDetail, fetchStaffOptions } from '../api/workbench'
import { currentUser } from '../utils/auth'
import { buildSystemUrl, loadSystemConsoleState } from '../utils/systemConsoleStore'
import {
  formatChannel,
  formatDateTime,
  formatMoney,
  formatOrderStatus,
  formatRoleCode,
  formatVerificationStatus,
  normalize
} from '../utils/format'

const route = useRoute()
const router = useRouter()

const detailLoading = ref(false)
const savingServiceForm = ref(false)
const confirmingAction = ref(false)
const sendingToCustomer = ref(false)
const detail = ref(null)
const staffOptions = ref([])
const verificationCode = ref('')
const verificationInputRef = ref(null)
const scannerDialogVisible = ref(false)
const scannerAutoOpened = ref(false)
const scannerVideoRef = ref(null)
const scannerHint = ref('请将核销二维码置于取景框内')
const scannerError = ref('')
const autosaveMessage = ref('')
const dirty = ref(false)
const serviceTemplatePreview = ref(null)
const state = reactive(loadSystemConsoleState())
const serviceForm = reactive(createServiceForm())

const STORE_ROLE_CODES = ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR']
const ROLE_ASSIGNMENT_MANAGER_CODES = ['ADMIN', 'STORE_MANAGER']
const styleOptions = ['自然', '高级感', '轻奢', '活力', '知性']
const sceneOptions = ['特写', '三分', '五分', '七分', '全身']
const serviceItemOptions = ['服装造型', '精修底片', '拍摄服务', '加选服务']

const scanMode = computed(() => route.meta?.scanMode === true || String(route.query.scan || '') === '1')
const readOnlyMode = computed(() => String(route.query.mode || '') === 'view')
const isVerified = computed(() => normalize(detail.value?.order?.verificationStatus || 'UNVERIFIED') === 'VERIFIED')
const isDepositOrder = computed(() => normalize(detail.value?.order?.type || '') === 'DEPOSIT')
const isFinishedOrder = computed(
  () =>
    Boolean(detail.value?.summary?.finishTime) ||
    ['COMPLETED', 'FINISHED', 'USED'].includes(normalize(detail.value?.order?.status || ''))
)
const hasSavedServiceDetail = computed(() => Boolean(String(detail.value?.order?.serviceDetailJson || '').trim()))
const compatibilityBackfillMode = computed(() => false)
const hasConfirmedServiceForm = computed(() => isServiceFormConfirmed(serviceForm.confirmation))
const canManageRoles = computed(() => ROLE_ASSIGNMENT_MANAGER_CODES.includes(normalize(currentUser.value?.roleCode || '')))
const canViewAmounts = computed(() => ['ADMIN', 'FINANCE'].includes(normalize(currentUser.value?.roleCode || '')))
const canEditForm = computed(() => !readOnlyMode.value && isVerified.value && (!isFinishedOrder.value || compatibilityBackfillMode.value))
const canSwitchToEditMode = computed(() => readOnlyMode.value && !isFinishedOrder.value)
const canSendToCustomer = computed(
  () => Boolean(detail.value?.order?.customerId) && detail.value?.customer?.wecomBound === true && canEditForm.value && hasConfirmedServiceForm.value
)
const sendToCustomerDisabledReason = computed(() => {
  if (canSendToCustomer.value) {
    return ''
  }
  if (!hasConfirmedServiceForm.value) {
    return '确认单尚未打印确认，暂不能发送'
  }
  return '客户尚未绑定企业微信，暂不能发送'
})
const showRoleAssignment = computed(() => !scanMode.value && !readOnlyMode.value && canManageRoles.value)
const currentRoles = computed(() =>
  (detail.value?.currentRoles || [])
    .filter((item) => STORE_ROLE_CODES.includes(normalize(item?.roleCode)))
    .sort((left, right) => STORE_ROLE_CODES.indexOf(normalize(left?.roleCode)) - STORE_ROLE_CODES.indexOf(normalize(right?.roleCode)))
)
const customerDisplayName = computed(() => detail.value?.customer?.name || detail.value?.order?.customerName || '未绑定客户')
const customerDisplayPhone = computed(() => detail.value?.customer?.phone || detail.value?.order?.customerPhone || '--')
const storeDisplayName = computed(() => detail.value?.order?.storeName || currentUser.value?.storeName || '未分配门店')
const appointmentLabel = computed(() => formatDateTime(detail.value?.order?.appointmentTime) || '待确认')
const savedServiceTemplate = computed(() => serviceForm.serviceTemplate || null)
const serviceTemplateTitle = computed(() => savedServiceTemplate.value?.title || serviceTemplatePreview.value?.template?.title || '')
const serviceTemplateDisplay = computed(
  () => savedServiceTemplate.value?.title || serviceTemplatePreview.value?.template?.title || serviceTemplatePreview.value?.template?.templateName || ''
)
const pageTitle = computed(() => serviceTemplateTitle.value || (readOnlyMode.value ? '查看服务单' : '服务确认单'))
const verificationAmount = computed(() => {
  const deposit = toAmount(detail.value?.order?.deposit)
  if (deposit && deposit > 0) {
    return deposit
  }
  return toAmount(detail.value?.order?.amount) || 0
})
const serviceConfirmAmountLabel = computed(() =>
  Number(serviceForm.serviceConfirmAmount || 0) > 0 ? formatMoney(serviceForm.serviceConfirmAmount) : '待填写'
)
const serviceFormStatusLabel = computed(() => {
  if (!isVerified.value) {
    return '待核销'
  }
  if (readOnlyMode.value || serviceStage.value.key === 'finish') {
    return '已完成'
  }
  if (hasConfirmedServiceForm.value) {
    return '已确认'
  }
  return hasSavedServiceDetail.value ? '待打印确认' : '待填写'
})
const serviceStage = computed(() => {
  if (detail.value?.summary?.finishTime) {
    return { label: '已完成', key: 'finish' }
  }
  if (detail.value?.summary?.startTime) {
    return { label: '服务中', key: 'serving' }
  }
  if (!isVerified.value) {
    return { label: '待核销', key: 'verify' }
  }
  if (!hasConfirmedServiceForm.value) {
    return { label: hasSavedServiceDetail.value ? '待打印确认' : '待确认单', key: 'confirm' }
  }
  return { label: '待开始服务', key: 'start' }
})
const confirmActionLabel = computed(() => {
  if (compatibilityBackfillMode.value) {
    return '仅查看'
  }
  if (!isVerified.value) {
    return '请先完成核销'
  }
  if (detail.value?.summary?.finishTime) {
    return '已完成'
  }
  if (detail.value?.summary?.startTime) {
    return '完成服务'
  }
  return hasConfirmedServiceForm.value ? '开始服务' : '确认纸质单并开始服务'
})
const canConfirmAction = computed(() => {
  if (!detail.value || !isVerified.value) {
    return false
  }
  if (compatibilityBackfillMode.value) {
    return false
  }
  if (detail.value.summary?.finishTime) {
    return false
  }
  if (!detail.value.summary?.startTime) {
    return hasSavedServiceDetail.value
  }
  return true
})
const serviceActionHint = computed(() => {
  if (compatibilityBackfillMode.value) {
    return '已完成订单仅支持查看服务确认单'
  }
  if (!isVerified.value) {
    return '请先完成核销后继续'
  }
  if (detail.value?.summary?.finishTime) {
    return '订单已完成，可返回列表查看或登记退款'
  }
  if (detail.value?.summary?.startTime) {
    return '服务进行中，完成后点击完成服务'
  }
  if (!hasConfirmedServiceForm.value) {
    return hasSavedServiceDetail.value ? '请确认纸质服务确认单后开始服务' : '请先保存服务确认单草稿'
  }
  return hasConfirmedServiceForm.value ? '确认单已确认，可开始服务' : '请打印确认单，客户线下手写签名后再确认'
})
const roleCards = computed(() => {
  const currentRoleMap = new Map(currentRoles.value.map((item) => [normalize(item.roleCode), item]))
  return (staffOptions.value || []).map((role) => ({
    ...role,
    current: currentRoleMap.get(normalize(role.roleCode))
  }))
})
const orderSummaryItems = computed(() =>
  [
    { label: '订单号', value: detail.value?.order?.orderNo || '--' },
    { label: '订单状态', value: formatOrderStatus(detail.value?.order?.status) },
    canViewAmounts.value ? { label: '核销金额', value: formatMoney(verificationAmount.value) } : null,
    canViewAmounts.value ? { label: '确认单金额', value: serviceConfirmAmountLabel.value } : null,
    { label: '服务模板', value: serviceTemplateDisplay.value || '--' },
    { label: '来源渠道', value: formatChannel(detail.value?.order?.sourceChannel) },
    { label: '最近更新', value: formatDateTime(detail.value?.order?.updateTime || detail.value?.order?.createTime) || '--' }
  ].filter(Boolean)
)
const timelineItems = computed(() => [
  { label: '核销时间', value: formatDateTime(detail.value?.order?.verificationTime) || '--' },
  { label: '确认单', value: hasConfirmedServiceForm.value ? '已确认' : hasSavedServiceDetail.value ? '待打印确认' : '待确认' },
  { label: '开始服务', value: formatDateTime(detail.value?.summary?.startTime) || '--' },
  { label: '完成服务', value: formatDateTime(detail.value?.summary?.finishTime) || '--' }
])
const flowTraceItems = computed(() => normalizeFlowTrace(detail.value?.flowTrace))
const flowTraceHint = computed(() => (flowTraceItems.value.length ? '来自已记录的业务动作。' : '暂无详细操作记录，按当前订单状态展示。'))
const displayTimelineItems = computed(() => (flowTraceItems.value.length ? flowTraceItems.value : timelineItems.value))
const serviceProcessSteps = computed(() => {
  const stage = serviceStage.value.key
  const verified = isVerified.value
  const confirmed = hasConfirmedServiceForm.value
  const started = Boolean(detail.value?.summary?.startTime)
  const finished = Boolean(detail.value?.summary?.finishTime)
  return [
    { key: 'appointment', label: '预约', done: Boolean(detail.value?.order?.appointmentTime) || verified || started || finished },
    { key: 'verify', label: isDepositOrder.value ? '直接核销' : '到店核销', done: verified, current: stage === 'verify' },
    { key: 'form', label: '确认单', done: confirmed, current: stage === 'confirm' },
    { key: 'serving', label: '服务中', done: started || finished, current: stage === 'serving' },
    { key: 'finish', label: '已完成', done: finished, current: stage === 'finish' }
  ]
})
const serviceNextStepHint = computed(() => {
  if (serviceStage.value.key === 'finish') {
    return '本次门店服务已完成。'
  }
  if (serviceStage.value.key === 'serving') {
    return '下一步：结束订单，系统只做账务记录。'
  }
  if (!isVerified.value) {
    return isDepositOrder.value ? '下一步：直接确认到店，不用扫码或输码。' : '下一步：完成团购券码核销。'
  }
  if (!hasConfirmedServiceForm.value) {
    return hasSavedServiceDetail.value
      ? '下一步：打印确认单，客户线下手写签名后在系统确认。'
      : '下一步：填写确认单并打印，由客户在线下纸质单确认。'
  }
  return '下一步：确认单已确认并开始服务。'
})

let autosaveTimer = null
let scannerStream = null
let scannerFrameId = 0
let barcodeDetector = null
let suppressDirtyWatch = false

function createServiceForm(payload = {}) {
  return {
    currentRoleCode: normalize(payload.currentRoleCode || currentUser.value?.roleCode || 'STORE_SERVICE'),
    profession: String(payload.profession || ''),
    preferredStyles: normalizeArray(payload.preferredStyles),
    preferredScenes: normalizeArray(payload.preferredScenes),
    serviceRequirement: String(payload.serviceRequirement || ''),
    styleConfirmation: String(payload.styleConfirmation || ''),
    serviceItems: normalizeArray(payload.serviceItems),
    serviceConfirmAmount: toAmount(payload.serviceConfirmAmount),
    serviceDuration: String(payload.serviceDuration || ''),
    addOnCount: Number.isFinite(Number(payload.addOnCount)) ? Number(payload.addOnCount) : 0,
    addOnContent: String(payload.addOnContent || ''),
    signature: String(payload.signature || ''),
    internalRemark: String(payload.internalRemark || ''),
    confirmation: normalizeServiceConfirmation(payload.confirmation || payload.serviceFormStatus),
    serviceTemplate: normalizeServiceTemplate(payload.serviceTemplate)
  }
}

function normalizeServiceConfirmation(value) {
  if (!value) {
    return null
  }
  if (typeof value === 'string') {
    return normalize(value) === 'PRINT_CONFIRMED' ? { status: 'PRINT_CONFIRMED' } : null
  }
  if (typeof value === 'object') {
    return {
      ...value,
      status: normalize(value.status)
    }
  }
  return null
}

function isServiceFormConfirmed(value) {
  return normalize(value?.status || value) === 'PRINT_CONFIRMED'
}

function applyServiceForm(payload) {
  suppressDirtyWatch = true
  Object.assign(serviceForm, createServiceForm(payload || {}))
  dirty.value = false
  queueMicrotask(() => {
    suppressDirtyWatch = false
  })
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

function normalizeFlowTrace(items) {
  if (!Array.isArray(items) || !items.length) {
    return []
  }
  return items
    .map((item) => ({
      label: formatFlowTraceAction(item?.actionCode),
      value: buildFlowTraceText(item)
    }))
    .filter((item) => item.label && item.value)
}

function formatFlowTraceAction(actionCode) {
  const code = normalize(actionCode)
  const labels = {
    INSTANCE_START: '流程记录',
    ORDER_APPOINTMENT: '预约排档',
    ORDER_APPOINTMENT_CANCEL: '取消预约',
    ORDER_VERIFY: isDepositOrder.value ? '直接核销' : '到店核销',
    PLAN_CREATE: '创建服务单',
    PLAN_ARRIVE: '确认到店',
    SERVICE_FORM_CONFIRM: '确认纸质单',
    PLAN_START: '开始服务',
    PLAN_FINISH: '完成服务',
    ORDER_COMPLETE: '订单完成'
  }
  return labels[code] || '业务动作'
}

function buildFlowTraceText(item) {
  const parts = []
  const summary = String(item?.summary || '').replace(/；实际操作人：.+$/, '').trim()
  if (summary) {
    parts.push(summary)
  }
  const time = formatDateTime(item?.eventTime)
  if (time) {
    parts.push(time)
  }
  return parts.join(' / ') || '--'
}

function toAmount(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : null
}

async function loadStaffOptions() {
  try {
    staffOptions.value = (await fetchStaffOptions())
      .filter((role) => STORE_ROLE_CODES.includes(normalize(role?.roleCode)))
      .sort((left, right) => STORE_ROLE_CODES.indexOf(normalize(left?.roleCode)) - STORE_ROLE_CODES.indexOf(normalize(right?.roleCode)))
  } catch {
    staffOptions.value = []
  }
}

async function ensureCurrentRoleBound(planOrderId) {
  if (!detail.value || !currentUser.value?.roleCode || !currentUser.value?.userId) {
    return
  }
  const normalizedRole = normalize(currentUser.value.roleCode)
  if (!STORE_ROLE_CODES.includes(normalizedRole)) {
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
    await loadServiceTemplatePreview()
    const parsed = safeParseJson(detail.value?.order?.serviceDetailJson)
    applyServiceForm(parsed)
    serviceForm.currentRoleCode = normalize(serviceForm.currentRoleCode || currentUser.value?.roleCode || 'STORE_SERVICE')
    verificationCode.value = detail.value?.order?.verificationCode || ''
    await ensureCurrentRoleBound(planOrderId)
  } catch {
    detail.value = null
    serviceTemplatePreview.value = null
    applyServiceForm()
  } finally {
    detailLoading.value = false
  }
}

async function loadServiceTemplatePreview() {
  try {
    serviceTemplatePreview.value = await previewServiceFormTemplate({
      storeName: detail.value?.order?.storeName || currentUser.value?.storeName || undefined
    })
  } catch {
    serviceTemplatePreview.value = null
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

async function handlePlanAction(action, options = {}) {
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
  if (!options.silent) {
    ElMessage.success('履约状态已更新')
  }
  if (!options.skipReload) {
    await loadPageContext()
  }
}

async function handleConfirmAndAdvance() {
  if (!canEditForm.value || !canConfirmAction.value) {
    if (isVerified.value && !detail.value?.summary?.startTime && !hasSavedServiceDetail.value) {
      ElMessage.warning('请先保存服务确认单草稿')
    }
    return
  }
  try {
    await ElMessageBox.confirm(confirmAdvanceMessage(), '确认状态流转', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  confirmingAction.value = true
  try {
    const saved = await handleSaveServiceForm({ silent: true })
    if (!saved) {
      ElMessage.error('服务单未保存成功，暂不能推进状态')
      return
    }
    if (detail.value?.summary?.finishTime) {
      ElMessage.info('当前服务单已完成')
      return
    }
    if (detail.value?.summary?.startTime) {
      await handlePlanAction('finish')
      return
    }
    if (!hasConfirmedServiceForm.value) {
      await confirmPlanOrderServiceForm({
        planOrderId: Number(route.params.id)
      })
      serviceForm.confirmation = { status: 'PRINT_CONFIRMED' }
    }
    if (!detail.value?.summary?.arriveTime) {
      await handlePlanAction('arrive', { silent: true, skipReload: true })
    }
    await handlePlanAction('start')
  } finally {
    confirmingAction.value = false
  }
}

function confirmAdvanceMessage() {
  if (detail.value?.summary?.startTime) {
    return '确认完成本次门店服务吗？完成后订单会进入已完成状态，并用于后续服务分成统计；此操作不会发起退款或资金划拨。'
  }
  if (!hasConfirmedServiceForm.value) {
    return '请确认服务确认单已经打印，且客户已在线下纸质单手写签名。确认后系统会记录纸质确认并开始服务。'
  }
  return `点击后将推进到下一状态：${confirmActionLabel.value}。是否继续？`
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

function markDirty() {
  if (readOnlyMode.value || !detail.value?.order?.id) {
    return
  }
  dirty.value = true
  queueAutosave()
}

function queueAutosave() {
  if (!canEditForm.value) {
    return
  }
  if (autosaveTimer) {
    window.clearTimeout(autosaveTimer)
  }
  autosaveMessage.value = '内容已变更，稍后自动保存'
  autosaveTimer = window.setTimeout(() => {
    void handleSaveServiceForm({ silent: true, autosave: true })
  }, 1500)
}

function clearAutosaveTimer() {
  if (autosaveTimer) {
    window.clearTimeout(autosaveTimer)
    autosaveTimer = null
  }
}

async function handleSaveServiceForm(options = {}) {
  if (!detail.value?.order?.id || (!canEditForm.value && !options.force)) {
    return false
  }
  if (savingServiceForm.value) {
    return false
  }
  clearAutosaveTimer()
  savingServiceForm.value = true
  if (options.autosave) {
    autosaveMessage.value = '正在自动保存...'
  }
  try {
    const templateSnapshot = buildServiceTemplateSnapshot()
    const serviceDetail = {
      ...createServiceForm(serviceForm),
      serviceTemplate: templateSnapshot || serviceForm.serviceTemplate || null
    }
    const response = await saveOrderServiceDetail({
      orderId: detail.value.order.id,
      serviceRequirement: serviceForm.serviceRequirement,
      serviceDetailJson: JSON.stringify(serviceDetail),
      serviceTemplateId: templateSnapshot?.templateId,
      serviceTemplateBindingId: templateSnapshot?.bindingId,
      serviceTemplateCode: templateSnapshot?.templateCode,
      serviceTemplateName: templateSnapshot?.templateName,
      serviceTemplateTitle: templateSnapshot?.title,
      serviceTemplateLayoutMode: templateSnapshot?.layoutMode,
      serviceTemplateConfigJson: stringifyTemplateConfig(templateSnapshot?.config),
      serviceTemplateSnapshotJson: templateSnapshot ? JSON.stringify(templateSnapshot) : undefined
    })
    detail.value.order = {
      ...detail.value.order,
      ...response
    }
    dirty.value = false
    const timeText = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    autosaveMessage.value = options.autosave ? `已自动保存 ${timeText}` : `已保存 ${timeText}`
    if (!options.silent) {
      ElMessage.success(options.autosave ? '已自动保存' : '服务单已保存')
    }
    return true
  } catch {
    autosaveMessage.value = options.autosave ? '自动保存失败，请手动保存' : autosaveMessage.value
    if (!options.silent) {
      ElMessage.error('服务单保存失败')
    }
    return false
  } finally {
    savingServiceForm.value = false
  }
}

async function handlePrintServiceForm() {
  if (!hasSavedServiceDetail.value) {
    ElMessage.warning('请先保存服务确认单草稿')
    return
  }
  if (canEditForm.value && dirty.value) {
    const saved = await handleSaveServiceForm({ silent: true })
    if (!saved) {
      ElMessage.error('服务单未保存成功，暂不能打印')
      return
    }
  }
  await nextTick()
  window.print()
}

function buildServiceFormMessage() {
  const url = buildSystemUrl(state, 'scan', `/service-scan/${route.params.id}?mode=view`)
  return [
    `您好，${customerDisplayName.value} 的服务确认单已更新。`,
    `门店：${storeDisplayName.value}`,
    `预约时间：${appointmentLabel.value}`,
    `服务确认单金额：${serviceConfirmAmountLabel.value}`,
    `服务需求：${fieldText(serviceForm.serviceRequirement)}`,
    `查看链接：${url}`
  ].join('\n')
}

async function handleSaveAndSend() {
  if (!canSendToCustomer.value) {
    ElMessage.warning(sendToCustomerDisabledReason.value || '暂不能发送')
    return
  }
  try {
    await ElMessageBox.confirm(`将通过企业微信把当前服务确认单发送给 ${customerDisplayName.value}，是否继续？`, '发送确认单', {
      confirmButtonText: '确认发送',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }
  if (!canEditForm.value) {
    return
  }
  sendingToCustomer.value = true
  try {
    const saved = await handleSaveServiceForm({ silent: true })
    if (!saved) {
      ElMessage.error('服务单未保存成功，暂未发送')
      return
    }
    await sendPlanOrderServiceForm({
      planOrderId: Number(route.params.id),
      message: buildServiceFormMessage()
    })
    ElMessage.success('服务单已保存并发送给客户')
  } catch {
    ElMessage.warning('服务单已保存，但发送失败，请稍后重试')
  } finally {
    sendingToCustomer.value = false
  }
}

function openCameraScanner() {
  scannerHint.value = '请将核销二维码置于取景框内'
  scannerError.value = ''
  scannerDialogVisible.value = true
}

function useManualVerify() {
  scannerDialogVisible.value = false
  void focusVerificationInput()
}

function restartCameraScanner() {
  scannerError.value = ''
  scannerHint.value = '正在重新打开摄像头...'
  stopCameraScanner()
  void startCameraScanner()
}

async function startCameraScanner() {
  if (typeof window === 'undefined' || !scannerDialogVisible.value) {
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    scannerError.value = '当前浏览器不支持摄像头，请改用输码核销。'
    return
  }
  if (!('BarcodeDetector' in window)) {
    scannerError.value = '当前浏览器不支持二维码识别，请改用输码核销。'
    return
  }

  try {
    scannerError.value = ''
    scannerHint.value = '正在打开摄像头，请将核销二维码完整置于取景框内。'
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
    scannerHint.value = '请对准核销二维码'
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
    scannerError.value = '摄像头已打开，但暂未识别成功，可继续尝试或改用输码核销。'
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
    scannerError.value = '核销失败，请核对二维码后重试，或改用输码核销。'
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
    return '未获得摄像头权限，请允许访问后重试，或改用输码核销。'
  }
  return '摄像头暂时无法使用，请改用输码核销。'
}

async function handleCodeVerify() {
  if (!String(verificationCode.value || '').trim()) {
    ElMessage.warning('请输入核销码')
    await focusVerificationInput()
    return
  }
  await verifyCurrentOrder(String(verificationCode.value || '').trim(), 'CODE')
}

async function handleDirectDepositVerify() {
  try {
    await ElMessageBox.confirm(
      '定金订单不用扫码或输码。确认后将记录为定金到店确认，并进入服务确认单流程。本操作不会调用真实核销接口，也不会生成券码核销记录。',
      '确认直接核销？',
      {
        confirmButtonText: '确认直接核销',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
  } catch {
    return
  }
  await verifyCurrentOrder(`DIRECT-DEPOSIT-${detail.value?.order?.id || route.params.id}`, 'DIRECT_DEPOSIT')
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
  ElMessage.success(method === 'DIRECT_DEPOSIT' ? '已确认到店，可以继续填写服务单' : '核销成功，可以继续填写服务单')
  await scrollToFormStart()
}

async function scrollToFormStart() {
  await nextTick()
  if (typeof window === 'undefined') {
    return
  }
  const target = document.querySelector('.service-card--locked')?.nextElementSibling || document.querySelector('.service-form-grid')
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  const firstEditable = document.querySelector('.service-main input, .service-main textarea')
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

function normalizeServiceTemplate(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  return { ...value }
}

function buildServiceTemplateSnapshot() {
  const template = serviceTemplatePreview.value?.template
  if (!template) {
    return serviceForm.serviceTemplate || null
  }
  const config = safeParseJson(template.configJson)
  return {
    templateId: template.id ?? null,
    bindingId: serviceTemplatePreview.value?.binding?.id ?? null,
    templateCode: template.templateCode || '',
    templateName: template.templateName || '',
    title: template.title || '',
    layoutMode: template.layoutMode || '',
    storeName: serviceTemplatePreview.value?.storeName || storeDisplayName.value,
    config: config || template.configJson || null
  }
}

function stringifyTemplateConfig(config) {
  if (!config) {
    return undefined
  }
  return typeof config === 'string' ? config : JSON.stringify(config)
}

function goBackToOrders() {
  router.push('/store-service/orders')
}

function switchToEditMode() {
  router.push(`/plan-orders/${route.params.id}`)
}

function handleBeforeUnload(event) {
  if (!dirty.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

function handlePageHide() {
  if (dirty.value) {
    void handleSaveServiceForm({ silent: true, autosave: true, force: true })
  }
}

watch(
  () => `${route.params.id || ''}|${route.query.mode || ''}|${route.query.scan || ''}`,
  () => {
    scannerAutoOpened.value = false
    clearAutosaveTimer()
    autosaveMessage.value = ''
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
  (current) => {
    if (!current.scanMode || current.readOnlyMode || current.verified || !current.orderId || scannerAutoOpened.value) {
      return
    }
    scannerAutoOpened.value = true
    openCameraScanner()
  }
)

watch(
  () => JSON.stringify(createServiceForm(serviceForm)),
  (next, prev) => {
    if (!detail.value || next === prev || suppressDirtyWatch) {
      return
    }
    markDirty()
  }
)

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  window.addEventListener('pagehide', handlePageHide)
})

onBeforeUnmount(() => {
  stopCameraScanner()
  clearAutosaveTimer()
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('pagehide', handlePageHide)
})
</script>

<style scoped>
.plan-order-page {
  min-width: 0;
  width: 100%;
}

.plan-order-page--scan {
  min-height: 100vh;
  padding: 20px 16px 96px;
  background: #f4f7fb;
}

.service-shell {
  display: grid;
  gap: 16px;
  width: 100%;
}

.plan-order-page--scan .service-shell {
  max-width: 980px;
  margin: 0 auto;
}

.service-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 14px 18px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e5edf4;
}

.service-header__main {
  display: grid;
  gap: 8px;
}

.service-header__headline h2 {
  margin: 0;
  font-size: 24px;
  color: #0f172a;
}

.service-header__meta {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 10px;
  color: #475569;
  font-size: 12px;
}

.service-header__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid #e5edf4;
}

.service-header__weak {
  color: #94a3b8;
  background: transparent !important;
  border-color: transparent !important;
  padding-left: 0 !important;
  padding-right: 0 !important;
}

.service-header__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.service-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.service-workspace--single {
  grid-template-columns: minmax(0, 1fr);
}

.service-main {
  display: grid;
  gap: 16px;
}

.service-side {
  display: grid;
  gap: 16px;
}

.service-card {
  display: grid;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e5edf4;
}

.service-card--locked {
  opacity: 0.76;
}

.service-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.service-card__header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.service-card__header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.verify-panel {
  display: grid;
  gap: 14px;
}

.verify-panel__actions {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 12px;
}

.verify-panel__manual {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.verify-panel__result,
.verify-panel__pending {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 18px;
  background: #effbf4;
  border: 1px solid rgba(22, 163, 74, 0.16);
  color: #64748b;
}

.verify-panel__pending {
  background: #fff7ed;
  border-color: rgba(249, 115, 22, 0.16);
  color: #9a3412;
}

.verify-panel__result strong {
  color: #0f172a;
}

.verify-panel__result-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #16a34a;
  color: #ffffff;
  font-size: 18px;
  font-weight: 700;
}

.verify-panel__result-body {
  display: grid;
  gap: 4px;
}

.verify-hero {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  text-align: left;
  min-height: 132px;
}

.verify-hero strong {
  color: #0f172a;
  font-size: 18px;
}

.verify-hero span {
  color: #64748b;
  font-size: 13px;
}

.verify-hero--primary {
  border: 1px solid #173042;
  cursor: pointer;
  background: linear-gradient(135deg, #173042 0%, #28495f 100%);
  box-shadow: 0 10px 24px rgba(23, 48, 66, 0.16);
}

.verify-hero--primary strong,
.verify-hero--primary span {
  color: #ffffff;
}

.service-form-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.service-field {
  display: grid;
  gap: 10px;
}

.service-field--full {
  grid-column: 1 / -1;
}

.service-field label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.service-field__display {
  min-height: 44px;
  padding: 11px 13px;
  border-radius: 12px;
  border: 1px solid #e5edf4;
  background: #f8fbff;
  color: #0f172a;
  display: flex;
  align-items: center;
}

.service-field__display--multiline {
  align-items: flex-start;
  white-space: pre-wrap;
}

.side-list {
  display: grid;
  gap: 12px;
}

.process-steps {
  display: grid;
  gap: 8px;
}

.process-step {
  position: relative;
  min-height: 36px;
  display: flex;
  align-items: center;
  padding: 8px 12px 8px 32px;
  border-radius: 12px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  border: 1px solid #e5edf4;
}

.process-step::before {
  content: '';
  position: absolute;
  left: 12px;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #cbd5e1;
}

.process-step.is-done {
  color: #166534;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.process-step.is-done::before {
  background: #16a34a;
}

.process-step.is-current {
  color: #075985;
  background: #f0f9ff;
  border-color: #bae6fd;
  font-weight: 700;
}

.process-step.is-current::before {
  background: #0284c7;
}

.side-list__item {
  display: grid;
  gap: 4px;
}

.side-list__item span {
  color: #64748b;
  font-size: 12px;
}

.side-list__item strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.6;
}

.role-grid {
  display: grid;
  gap: 12px;
}

.role-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbff;
}

.role-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.role-card__header span {
  color: #64748b;
  font-size: 13px;
}

.quick-button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.service-action-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 14px 18px;
  border-radius: 20px;
  background: rgba(15, 23, 42, 0.94);
  color: #e2e8f0;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.16);
}

.service-action-bar__status {
  font-size: 13px;
}

.action-group--wrap {
  flex-wrap: wrap;
}

.scanner-dialog {
  display: grid;
  gap: 14px;
}

.scanner-dialog__viewport {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  background: #0f172a;
  min-height: 360px;
}

.scanner-dialog__video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.scanner-dialog__frame {
  position: absolute;
  inset: 18% 18%;
  border: 2px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  box-shadow: 0 0 0 999px rgba(15, 23, 42, 0.22);
}

.scanner-dialog__footer {
  display: grid;
  gap: 10px;
  color: #475569;
}

.scanner-dialog__error {
  color: #dc2626;
}

@media (max-width: 1080px) {
  .service-workspace {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .service-header,
  .service-action-bar {
    grid-template-columns: minmax(0, 1fr);
    display: grid;
  }

  .service-header__meta span {
    min-height: auto;
  }

  .service-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .verify-panel__actions,
  .verify-panel__manual {
    grid-template-columns: minmax(0, 1fr);
  }

}
</style>
