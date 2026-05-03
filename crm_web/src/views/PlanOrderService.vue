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
              <span v-if="canViewVerificationAmounts">{{ verificationAmountLabel }}：{{ formatMoney(verificationAmount) }}</span>
              <span v-if="canViewServiceConfirmAmounts">确认单金额：{{ serviceConfirmAmountLabel }}</span>
              <span v-if="serviceTemplateDisplay">模板：{{ serviceTemplateDisplay }}</span>
              <span>表单状态：{{ serviceFormStatusLabel }}</span>
              <span>{{ verificationStatusMetaLabel }}：{{ verificationStatusLabel }}</span>
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
                <h3>{{ verificationStepTitle }}</h3>
              </div>
              <el-tag :type="isVerified ? 'success' : 'warning'">
                {{ verificationStepTagLabel }}
              </el-tag>
            </div>

            <div class="verify-panel" :class="{ 'verify-panel--scan': scanMode }">
              <template v-if="isVerified">
                <div class="verify-panel__result">
                  <div class="verify-panel__result-icon">已</div>
                  <div class="verify-panel__result-body">
                    <strong>{{ verificationResultTitle }}</strong>
                    <span>{{ verificationTimeLabel }}：{{ formatDateTime(detail.order?.verificationTime) || '--' }}</span>
                    <span v-if="showVerificationCodeMeta">{{ verificationCodeLabel }}：{{ detail.order.verificationCode }}</span>
                  </div>
                </div>
              </template>

              <template v-else-if="!readOnlyMode">
                <div class="verify-panel__actions">
                  <button
                    v-if="isDepositOrder"
                    type="button"
                    class="verify-hero verify-hero--primary"
                    :disabled="!directDepositEnabled || verifyingVoucher"
                    @click="handleDirectDepositVerify"
                  >
                    <strong>定金免码确认</strong>
                    <span>{{ directDepositEnabled ? '定金订单不用扫码或输码，确认到店后进入服务确认单流程' : '系统已停用定金免码确认' }}</span>
                  </button>
                  <button
                    v-else
                    type="button"
                    class="verify-hero verify-hero--primary"
                    :disabled="verifyingVoucher || !canVerifyVoucher"
                    @click="openCameraScanner"
                  >
                    <strong>{{ scanVerifyTitle }}</strong>
                    <span>{{ verifyingVoucher ? verifyingHint : voucherVerifyHint }}</span>
                  </button>
                  <div v-if="!isDepositOrder" class="verify-hero verify-hero--manual">
                    <strong>{{ manualVerifyTitle }}</strong>
                    <div class="verify-panel__manual">
                      <el-input ref="verificationInputRef" v-model="verificationCode" :disabled="!canVerifyVoucher || verifyingVoucher" placeholder="请输入核销码" />
                      <el-button :loading="verifyingVoucher" :disabled="!canVerifyVoucher" @click="handleCodeVerify">确认核销</el-button>
                    </div>
                  </div>
                </div>
                <div v-if="voucherVerifyError" class="verify-panel__error">
                  <strong>{{ voucherFailureTitle }}</strong>
                  <span>{{ voucherVerifyError }}</span>
                  <div class="action-group">
                    <el-button size="small" type="primary" plain @click="openCameraScanner">重新扫码</el-button>
                    <el-button size="small" plain @click="focusVerificationInput">改为输码</el-button>
                    <el-button size="small" text @click="copyVoucherFailureInfo">复制排障信息</el-button>
                    <el-button v-if="canOpenVoucherConfig" size="small" text @click="goVoucherConfig">{{ voucherConfigButtonLabel }}</el-button>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="verify-panel__pending">
                  <span>{{ verificationPendingText }}</span>
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

              <div v-if="canViewServiceConfirmAmounts" class="service-field">
                <label>服务确认单金额</label>
                <template v-if="readOnlyMode || !canEditServiceConfirmAmount">
                  <div class="service-field__display">{{ serviceConfirmAmountLabel }}</div>
                </template>
                <el-input-number
                  v-else
                  v-model="serviceForm.serviceConfirmAmount"
                  :disabled="!canEditServiceConfirmAmount"
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
              <div v-for="item in displayTimelineItems" :key="`${item.label}-${item.value}`" class="side-list__item">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </section>
        </aside>
      </div>

      <section class="print-service-form">
        <div class="print-service-form__head">
          <h1>{{ pageTitle }}</h1>
          <p>纸质确认单</p>
        </div>

        <div class="print-service-form__meta">
          <div><span>客户姓名</span><strong>{{ customerDisplayName }}</strong></div>
          <div><span>联系电话</span><strong>{{ customerDisplayPhone }}</strong></div>
          <div><span>服务门店</span><strong>{{ storeDisplayName }}</strong></div>
          <div><span>预约时间</span><strong>{{ appointmentLabel }}</strong></div>
          <div><span>订单号</span><strong>{{ detail.order?.orderNo || '--' }}</strong></div>
          <div><span>来源渠道</span><strong>{{ formatChannel(detail.order?.sourceChannel) }}</strong></div>
          <div v-if="canViewServiceConfirmAmounts"><span>确认单金额</span><strong>{{ serviceConfirmAmountLabel }}</strong></div>
        </div>

        <div class="print-service-form__section">
          <h2>服务内容</h2>
          <dl>
            <div>
              <dt>到店需求</dt>
              <dd>{{ fieldText(serviceForm.serviceRequirement) }}</dd>
            </div>
            <div>
              <dt>造型确认</dt>
              <dd>{{ fieldText(serviceForm.styleConfirmation) }}</dd>
            </div>
            <div>
              <dt>服务项目</dt>
              <dd>{{ joinDisplay(serviceForm.serviceItems) }}</dd>
            </div>
            <div>
              <dt>预计时长</dt>
              <dd>{{ fieldText(serviceForm.serviceDuration) }}</dd>
            </div>
            <div>
              <dt>偏好风格</dt>
              <dd>{{ joinDisplay(serviceForm.preferredStyles) }}</dd>
            </div>
            <div>
              <dt>喜欢构图</dt>
              <dd>{{ joinDisplay(serviceForm.preferredScenes) }}</dd>
            </div>
            <div>
              <dt>加选内容</dt>
              <dd>{{ fieldText(serviceForm.addOnContent) }}</dd>
            </div>
          </dl>
        </div>

        <div class="print-service-form__sign">
          <div>
            <span>客户手写签名</span>
            <strong></strong>
          </div>
          <div>
            <span>门店经办人</span>
            <strong>{{ fieldText(serviceForm.signature) }}</strong>
          </div>
          <div>
            <span>签署日期</span>
            <strong></strong>
          </div>
        </div>
      </section>

      <div v-if="!readOnlyMode" class="service-action-bar">
        <div class="service-action-bar__status">
          <span v-if="autosaveMessage">{{ autosaveMessage }}</span>
          <span v-else>{{ nextActionDisabledReason || serviceActionHint }}</span>
        </div>
        <div class="action-group action-group--wrap">
          <el-button :loading="savingServiceForm" :disabled="!canEditForm" @click="handleSaveServiceForm()">保存草稿</el-button>
          <el-button :loading="printingServiceForm" :disabled="!canPrintServiceForm" @click="handlePrintServiceForm">打印确认单</el-button>
          <el-button
            v-if="showConfirmPaperButton"
            type="warning"
            :loading="confirmingAction"
            :disabled="!canConfirmPaperForm"
            @click="handleConfirmPaperForm"
          >
            确认纸质单
          </el-button>
          <el-button v-else type="primary" :loading="confirmingAction" :disabled="!canEditForm || !canConfirmAction" @click="handleConfirmAndAdvance">
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
      <el-empty :description="scanMode ? '服务确认单不存在或链接已失效，请联系门店重发。' : '请选择一个服务确认单'" />
    </section>

    <el-dialog v-model="scannerDialogVisible" :title="scanVerifyTitle" width="720px" destroy-on-close @closed="stopCameraScanner">
      <div class="scanner-dialog">
        <div class="scanner-dialog__viewport">
          <video ref="scannerVideoRef" autoplay muted playsinline class="scanner-dialog__video"></video>
          <div class="scanner-dialog__frame"></div>
        </div>
        <div class="scanner-dialog__footer">
          <p>{{ scannerHint }}</p>
          <p v-if="scannerError" class="scanner-dialog__error">{{ scannerError }}</p>
          <p v-if="!isDepositOrder" class="scanner-dialog__note">{{ voucherVerifyHint }}</p>
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
  printPlanOrderServiceForm,
  sendPlanOrderServiceForm,
  startPlanOrder,
  arrivePlanOrder,
  finishPlanOrder
} from '../api/actions'
import { saveOrderServiceDetail, verifyOrderVoucher } from '../api/order'
import { previewServiceFormTemplate } from '../api/serviceFormTemplate'
import { fetchSystemConfigs } from '../api/systemConfig'
import { fetchPlanOrderDetail, fetchStaffOptions } from '../api/workbench'
import { canViewBusinessAmounts, canViewServiceAmounts, currentUser, hasRouteAccess } from '../utils/auth'
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
const printingServiceForm = ref(false)
const confirmingAction = ref(false)
const sendingToCustomer = ref(false)
const detail = ref(null)
const staffOptions = ref([])
const verificationCode = ref('')
const verificationInputRef = ref(null)
const verifyingVoucher = ref(false)
const voucherVerifyError = ref('')
const scannerDialogVisible = ref(false)
const scannerAutoOpened = ref(false)
const scannerVideoRef = ref(null)
const scannerHint = ref('请将核销二维码置于取景框内')
const scannerError = ref('')
const autosaveMessage = ref('')
const dirty = ref(false)
const serviceTemplatePreview = ref(null)
const directDepositEnabled = ref(true)
const serviceConfirmAmountEditRoles = ref(['ADMIN', 'FINANCE', 'PHOTO_SELECTOR'])
const state = reactive(loadSystemConsoleState())
const serviceForm = reactive(createServiceForm())

const STORE_ROLE_CODES = ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR']
const ROLE_ASSIGNMENT_MANAGER_CODES = ['ADMIN', 'STORE_MANAGER']
const DEFAULT_SERVICE_CONFIRM_AMOUNT_EDIT_ROLES = ['ADMIN', 'FINANCE', 'PHOTO_SELECTOR']
const VOUCHER_CONFIG_TARGETS = {
  DISTRIBUTION: {
    path: '/settings/integration/distribution-api?tab=config',
    routePath: '/settings/integration/distribution-api',
    roleCodes: ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR']
  },
  DOUYIN_LAIKE: {
    path: '/settings/integration/third-party?tab=voucher',
    routePath: '/settings/integration/third-party',
    roleCodes: ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR']
  }
}
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
const hasCurrentPrintedServiceForm = computed(
  () => normalize(serviceForm.printAudit?.status || '') === 'PRINTED' && Boolean(serviceForm.printAudit?.serviceDetailHash)
)
const compatibilityBackfillMode = computed(() => false)
const hasConfirmedServiceForm = computed(() => isServiceFormConfirmed(serviceForm.confirmation))
const canManageRoles = computed(() => ROLE_ASSIGNMENT_MANAGER_CODES.includes(normalize(currentUser.value?.roleCode || '')))
const canViewVerificationAmounts = computed(() => canViewBusinessAmounts())
const canViewServiceConfirmAmounts = computed(() => canViewServiceAmounts())
const canEditForm = computed(() => !readOnlyMode.value && isVerified.value && (!isFinishedOrder.value || compatibilityBackfillMode.value))
const canEditServiceConfirmAmount = computed(
  () => canEditForm.value && serviceConfirmAmountEditRoles.value.includes(normalize(currentUser.value?.roleCode || ''))
)
const canSwitchToEditMode = computed(() => readOnlyMode.value && !isFinishedOrder.value)
const canPrintServiceForm = computed(() => isVerified.value && hasSavedServiceDetail.value)
const showConfirmPaperButton = computed(
  () => isVerified.value && !detail.value?.summary?.startTime && !detail.value?.summary?.finishTime && !hasConfirmedServiceForm.value
)
const canConfirmPaperForm = computed(() => canEditForm.value && hasSavedServiceDetail.value && hasCurrentPrintedServiceForm.value && !dirty.value)
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
const pageTitle = computed(() => serviceTemplateTitle.value || (readOnlyMode.value ? '查看服务确认单' : '服务确认单'))
const voucherProviderLabel = computed(() => resolveVoucherProviderLabel(detail.value?.order))
const voucherProviderCode = computed(() => resolveVoucherProviderCode(detail.value?.order))
const voucherConfigTarget = computed(() => VOUCHER_CONFIG_TARGETS[voucherProviderCode.value] || null)
const canVerifyVoucher = computed(() => Boolean(voucherProviderCode.value))
const canOpenVoucherConfig = computed(() =>
  Boolean(voucherConfigTarget.value)
    && hasRouteAccess(voucherConfigTarget.value.routePath, 'SETTING', voucherConfigTarget.value.roleCodes)
)
const verificationAmountLabel = computed(() => (isDepositOrder.value ? '定金金额' : '核销金额'))
const verificationStatusMetaLabel = computed(() => (isDepositOrder.value ? '到店确认' : '核销'))
const verificationStatusLabel = computed(() =>
  isDepositOrder.value
    ? isVerified.value
      ? '到店已确认'
      : '待确认'
    : formatVerificationStatus(detail.value?.order?.verificationStatus || 'UNVERIFIED')
)
const verificationStepTitle = computed(() => {
  if (isDepositOrder.value) {
    return isVerified.value ? '到店已确认' : '先完成定金免码确认，再填写服务确认单'
  }
  return isVerified.value ? '核销完成' : '先完成团购券核销，再填写服务确认单'
})
const verificationStepTagLabel = computed(() => {
  if (isDepositOrder.value) {
    return isVerified.value ? '到店已确认' : '待确认'
  }
  return isVerified.value ? '已核销' : '待核销'
})
const verificationResultTitle = computed(() => {
  if (isDepositOrder.value) {
    return readOnlyMode.value || isFinishedOrder.value ? '到店确认已完成，服务确认单只读查看' : '到店确认已完成，可以继续填写服务确认单'
  }
  return readOnlyMode.value || isFinishedOrder.value ? '核销已完成，服务确认单只读查看' : '核销已完成，可以继续填写服务确认单'
})
const verificationTimeLabel = computed(() => (isDepositOrder.value ? '确认时间' : '核销时间'))
const verificationCodeLabel = computed(() => (isDepositOrder.value ? '确认编号' : '核销码'))
const showVerificationCodeMeta = computed(() => !isDepositOrder.value && Boolean(detail.value?.order?.verificationCode))
const verificationPendingText = computed(() => (isDepositOrder.value ? '当前定金订单尚未到店确认，暂不可编辑。' : '当前订单尚未完成团购券核销，服务确认单暂不可编辑。'))
const scanVerifyTitle = computed(() => (canVerifyVoucher.value ? `扫码核销${voucherProviderLabel.value}团购券` : '订单来源未识别，暂不能核销'))
const manualVerifyTitle = computed(() => (canVerifyVoucher.value ? `输码核销${voucherProviderLabel.value}团购券` : '请联系管理员补齐来源'))
const voucherVerifyHint = computed(() =>
  canVerifyVoucher.value ? `${voucherProviderLabel.value}外部接口返回成功后，系统才会进入服务确认单流程` : '订单来源未识别，请联系管理员补齐来源或核销策略'
)
const verifyingHint = computed(() => `正在向${voucherProviderLabel.value}系统核销，请勿重复点击`)
const voucherFailureTitle = computed(() => (canVerifyVoucher.value ? `${voucherProviderLabel.value}核销失败，订单仍为待核销` : '订单来源未识别，暂不能核销'))
const voucherConfigButtonLabel = computed(() => `检查${voucherProviderLabel.value}券核销配置`)
const verificationAmount = computed(() => {
  const deposit = toAmount(detail.value?.order?.deposit)
  if (deposit && deposit > 0) {
    return deposit
  }
  return toAmount(detail.value?.order?.amount) || 0
})
const serviceConfirmAmountLabel = computed(() =>
  Number(serviceForm.serviceConfirmAmount || 0) > 0
    ? formatMoney(serviceForm.serviceConfirmAmount)
    : !isVerified.value
      ? isDepositOrder.value ? '到店确认后填写' : '核销后填写'
      : '待填写'
)
const serviceFormStatusLabel = computed(() => {
  if (!isVerified.value) {
    return isDepositOrder.value ? '待到店确认' : '待核销'
  }
  if (readOnlyMode.value || serviceStage.value.key === 'finish') {
    return '已完成'
  }
  if (hasConfirmedServiceForm.value) {
    return '已确认'
  }
  if (!hasSavedServiceDetail.value) {
    return '待填写'
  }
  if (hasCurrentPrintedServiceForm.value) {
    return '已打印待签字'
  }
  return normalize(serviceForm.printAudit?.status || '') === 'STALE' ? '内容已变更，需重新打印' : '待打印确认'
})
const serviceStage = computed(() => {
  if (detail.value?.summary?.finishTime) {
    return { label: '已完成', key: 'finish' }
  }
  if (detail.value?.summary?.startTime) {
    return { label: '服务中', key: 'serving' }
  }
  if (!isVerified.value) {
    return { label: isDepositOrder.value ? '待到店确认' : '待核销', key: 'verify' }
  }
  if (!hasConfirmedServiceForm.value) {
    return { label: hasCurrentPrintedServiceForm.value ? '待确认签字' : hasSavedServiceDetail.value ? '待打印确认' : '待确认单', key: 'confirm' }
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
  return hasConfirmedServiceForm.value ? '开始服务' : '请先确认纸质单'
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
    return hasSavedServiceDetail.value && hasConfirmedServiceForm.value
  }
  return true
})
const nextActionDisabledReason = computed(() => {
  if (!detail.value || readOnlyMode.value) {
    return ''
  }
  if (!isVerified.value) {
    if (isDepositOrder.value) {
      return directDepositEnabled.value ? '请先点击定金免码确认，定金订单不用扫码或输码' : '定金免码确认已停用，请在系统流程配置中开启'
    }
    return '请先完成团购券核销'
  }
  if (!hasSavedServiceDetail.value) {
    return '请先保存服务确认单草稿'
  }
  if (dirty.value) {
    return '确认单内容已变更，请保存并重新打印当前版本'
  }
  if (showConfirmPaperButton.value && !hasConfirmedServiceForm.value) {
    return hasCurrentPrintedServiceForm.value ? '客户在纸质单手写签名后点击确认纸质单' : '请先打印当前版本确认单'
  }
  if (!detail.value.summary?.startTime && !hasConfirmedServiceForm.value) {
    return '纸质确认单未确认，暂不能开始服务'
  }
  return ''
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
    if (!hasSavedServiceDetail.value) {
      return '请先保存服务确认单草稿'
    }
    return hasCurrentPrintedServiceForm.value ? '客户手写签名后点击确认纸质单' : '请打印当前版本确认单'
  }
  return hasConfirmedServiceForm.value ? '确认单已确认，可单独点击开始服务' : '请打印确认单，客户线下手写签名后再确认'
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
    canViewVerificationAmounts.value ? { label: verificationAmountLabel.value, value: formatMoney(verificationAmount.value) } : null,
    canViewServiceConfirmAmounts.value ? { label: '确认单金额', value: serviceConfirmAmountLabel.value } : null,
    { label: '服务模板', value: serviceTemplateDisplay.value || '--' },
    { label: '来源渠道', value: formatChannel(detail.value?.order?.sourceChannel) },
    { label: '最近更新', value: formatDateTime(detail.value?.order?.updateTime || detail.value?.order?.createTime) || '--' }
  ].filter(Boolean)
)
const timelineItems = computed(() => [
  { label: verificationTimeLabel.value, value: formatDateTime(detail.value?.order?.verificationTime) || '--' },
  { label: '打印确认单', value: hasCurrentPrintedServiceForm.value ? `${formatDateTime(serviceForm.printAudit?.printedAt) || '已打印'} / 第 ${serviceForm.printAudit?.printCount || 1} 次` : '未打印' },
  { label: '纸质签字', value: hasConfirmedServiceForm.value ? '已确认' : hasCurrentPrintedServiceForm.value ? '待确认' : hasSavedServiceDetail.value ? '待打印' : '待确认' },
  { label: '开始服务', value: formatDateTime(detail.value?.summary?.startTime) || '--' },
  { label: '完成服务', value: formatDateTime(detail.value?.summary?.finishTime) || '--' }
])
const fulfillmentTimelineItems = computed(() => normalizeFulfillmentRecords(detail.value?.order?.fulfillmentRecords))
const appointmentTimelineItems = computed(() => normalizeAppointmentRecords(detail.value?.order?.appointmentRecords))
const flowTraceItems = computed(() => normalizeFlowTrace(detail.value?.flowTrace))
const flowTraceHint = computed(() =>
  fulfillmentTimelineItems.value.length || appointmentTimelineItems.value.length || flowTraceItems.value.length
    ? '来自已记录的业务动作。'
    : '暂无详细操作记录，按当前订单状态展示。'
)
const displayTimelineItems = computed(() => {
  const items = mergeTimelineItems([...fulfillmentTimelineItems.value, ...appointmentTimelineItems.value, ...flowTraceItems.value])
  return items.length ? items : timelineItems.value
})
const serviceProcessSteps = computed(() => {
  const stage = serviceStage.value.key
  const verified = isVerified.value
  const confirmed = hasConfirmedServiceForm.value
  const started = Boolean(detail.value?.summary?.startTime)
  const finished = Boolean(detail.value?.summary?.finishTime)
  return [
    { key: 'appointment', label: '预约', done: Boolean(detail.value?.order?.appointmentTime) || verified || started || finished },
    { key: 'verify', label: isDepositOrder.value ? '免码确认' : '到店核销', done: verified, current: stage === 'verify' },
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
    return isDepositOrder.value ? '下一步：免码确认到店，不用扫码或输码。' : '下一步：完成团购券码核销。'
  }
  if (!hasConfirmedServiceForm.value) {
    return hasSavedServiceDetail.value
      ? '下一步：打印确认单，客户线下手写签名后在系统确认。'
      : '下一步：填写确认单并打印，由客户在线下纸质单确认。'
  }
  return '下一步：确认单已确认，单独点击开始服务。'
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
    printAudit: normalizeServicePrintAudit(payload.printAudit),
    confirmation: normalizeServiceConfirmation(payload.confirmation || payload.serviceFormStatus),
    serviceTemplate: normalizeServiceTemplate(payload.serviceTemplate)
  }
}

function normalizeServicePrintAudit(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  return {
    ...value,
    status: normalize(value.status)
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

function syncServiceFormVersionState(serviceDetailJson) {
  const parsed = safeParseJson(serviceDetailJson)
  if (!parsed) {
    return
  }
  suppressDirtyWatch = true
  serviceForm.printAudit = normalizeServicePrintAudit(parsed.printAudit)
  serviceForm.confirmation = normalizeServiceConfirmation(parsed.confirmation || parsed.serviceFormStatus)
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

function normalizeFulfillmentRecords(records) {
  if (!Array.isArray(records) || !records.length) {
    return []
  }
  return records
    .map((record) => {
      const actionType = normalize(record?.actionType)
      const createTime = record?.createTime
      return {
        label: `${fulfillmentStageLabel(record?.stage)} - ${fulfillmentActionLabel(actionType)}`,
        value: buildFulfillmentRecordText(record),
        sortTime: createTime,
        dedupeKeys: [timelineDedupeKey(actionType, createTime), timelineDedupeKey(fulfillmentFlowTraceAction(actionType), createTime)]
      }
    })
    .filter((item) => item.label && item.value)
}

function fulfillmentStageLabel(stage) {
  const labels = {
    SCHEDULE: '排档',
    VERIFY: '核销',
    SERVICE_FORM: '确认单',
    SERVICE: '服务',
    FINANCE: '财务'
  }
  return labels[normalize(stage)] || '履约'
}

function fulfillmentActionLabel(actionType) {
  const labels = {
    APPOINTMENT_CREATE: '已约档',
    APPOINTMENT_CHANGE: '已改档',
    APPOINTMENT_CANCEL: '取消预约',
    DIRECT_DEPOSIT_VERIFY: '定金免码确认',
    EXTERNAL_VOUCHER_VERIFY: '团购券核销',
    VOUCHER_VERIFY: '团购券核销',
    VOUCHER_VERIFY_FAILED: '核销失败',
    SERVICE_FORM_PRINT: '打印确认单',
    SERVICE_FORM_CONFIRM: '确认纸质单',
    SERVICE_FINISH: '服务完成',
    ORDER_COMPLETE: '订单完成',
    REFUND_REGISTER: '退款冲正'
  }
  return labels[normalize(actionType)] || '履约记录'
}

function buildFulfillmentRecordText(record) {
  if (!record) {
    return '--'
  }
  const parts = []
  const actor = record.operatorUserName || (record.operatorUserId ? `ID ${record.operatorUserId}` : '')
  if (actor) {
    parts.push(`操作人：${actor}`)
  }
  const safeDetails = Array.isArray(record.detailItems) ? record.detailItems.map((item) => String(item || '').trim()).filter(Boolean) : []
  if (record.summary && fulfillmentActionLabel(record.actionType) === '履约记录') {
    safeDetails.unshift(String(record.summary).trim())
  }
  const fromStatus = formatOrderStatus(record.fromStatus)
  const toStatus = formatOrderStatus(record.toStatus)
  if (fromStatus && toStatus && fromStatus !== '--' && toStatus !== '--' && fromStatus !== toStatus) {
    parts.push(`状态：${fromStatus} -> ${toStatus}`)
  }
  parts.push(...safeDetails)
  const time = formatDateTime(record.createTime)
  if (time) {
    parts.push(time)
  }
  return parts.join(' / ') || '--'
}

function normalizeAppointmentRecords(records) {
  if (!Array.isArray(records) || !records.length) {
    return []
  }
  return records
    .map((record) => {
      const actionType = normalize(record?.actionType)
      return {
        label: appointmentRecordLabel(actionType),
        value: buildAppointmentRecordText(record),
        sortTime: record?.createTime,
        dedupeKeys: [timelineDedupeKey(actionType, record?.createTime)]
      }
    })
    .filter((item) => item.label && item.value)
}

function appointmentRecordLabel(actionType) {
  const labels = {
    APPOINTMENT_CREATE: '约档记录',
    APPOINTMENT_CHANGE: '改档记录',
    APPOINTMENT_CANCEL: '取消预约'
  }
  return labels[normalize(actionType)] || '排档记录'
}

function buildAppointmentRecordText(record) {
  const extra = safeParseJson(record?.extraJson) || {}
  const parts = []
  const actor = record?.operatorUserName || (record?.operatorUserId ? `ID ${record.operatorUserId}` : '')
  if (actor) {
    parts.push(`操作人：${actor}`)
  }
  if (extra.storeNameBefore || extra.appointmentTimeBefore) {
    parts.push(`原档：${[extra.storeNameBefore, extra.appointmentTimeBefore].filter(Boolean).join(' ')}`)
  }
  if (extra.storeNameAfter || extra.appointmentTimeAfter || extra.storeName) {
    parts.push(`新档：${[extra.storeNameAfter || extra.storeName, extra.appointmentTimeAfter].filter(Boolean).join(' ')}`)
  }
  const remark = extra.remark || record?.remark || ''
  if (remark) {
    parts.push(`备注：${remark}`)
  }
  const time = formatDateTime(record?.createTime)
  if (time) {
    parts.push(time)
  }
  return parts.join(' / ') || '--'
}

function normalizeFlowTrace(items) {
  if (!Array.isArray(items) || !items.length) {
    return []
  }
  return items
    .map((item) => {
      const actionCode = normalize(item?.actionCode)
      const eventTime = item?.eventTime
      return {
        label: formatFlowTraceAction(actionCode),
        value: buildFlowTraceText(item),
        sortTime: eventTime,
        dedupeKeys: [timelineDedupeKey(actionCode, eventTime), timelineDedupeKey(flowTraceFulfillmentAction(actionCode), eventTime)]
      }
    })
    .filter((item) => item.label && item.value)
}

function formatFlowTraceAction(actionCode) {
  const code = normalize(actionCode)
  const labels = {
    INSTANCE_START: '流程记录',
    ORDER_APPOINTMENT: '预约排档',
    ORDER_APPOINTMENT_CANCEL: '取消预约',
    ORDER_VERIFY: isDepositOrder.value ? '免码确认' : '到店核销',
    PLAN_CREATE: '创建服务确认单',
    PLAN_ARRIVE: '确认到店',
    SERVICE_FORM_PRINT: '打印确认单',
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

function fulfillmentFlowTraceAction(actionType) {
  const labels = {
    APPOINTMENT_CREATE: 'ORDER_APPOINTMENT',
    APPOINTMENT_CHANGE: 'ORDER_APPOINTMENT',
    APPOINTMENT_CANCEL: 'ORDER_APPOINTMENT_CANCEL',
    DIRECT_DEPOSIT_VERIFY: 'ORDER_VERIFY',
    EXTERNAL_VOUCHER_VERIFY: 'ORDER_VERIFY',
    VOUCHER_VERIFY: 'ORDER_VERIFY',
    SERVICE_FORM_PRINT: 'SERVICE_FORM_PRINT',
    SERVICE_FORM_CONFIRM: 'SERVICE_FORM_CONFIRM',
    SERVICE_FINISH: 'PLAN_FINISH',
    ORDER_COMPLETE: 'ORDER_COMPLETE'
  }
  return labels[normalize(actionType)] || ''
}

function flowTraceFulfillmentAction(actionCode) {
  const labels = {
    ORDER_APPOINTMENT: 'APPOINTMENT_CREATE',
    ORDER_APPOINTMENT_CANCEL: 'APPOINTMENT_CANCEL',
    ORDER_VERIFY: isDepositOrder.value ? 'DIRECT_DEPOSIT_VERIFY' : 'VOUCHER_VERIFY',
    SERVICE_FORM_PRINT: 'SERVICE_FORM_PRINT',
    SERVICE_FORM_CONFIRM: 'SERVICE_FORM_CONFIRM',
    PLAN_FINISH: 'SERVICE_FINISH',
    ORDER_COMPLETE: 'ORDER_COMPLETE'
  }
  return labels[normalize(actionCode)] || ''
}

function mergeTimelineItems(items) {
  const seen = new Set()
  return items
    .map((item, index) => ({ ...item, index }))
    .filter((item) => {
      const keys = (item.dedupeKeys || []).filter(Boolean)
      if (!keys.length) {
        return true
      }
      if (keys.some((key) => seen.has(key))) {
        return false
      }
      keys.forEach((key) => seen.add(key))
      return true
    })
    .sort((left, right) => {
      const diff = timelineTimestamp(right.sortTime) - timelineTimestamp(left.sortTime)
      return diff || left.index - right.index
    })
}

function timelineDedupeKey(actionType, time) {
  const action = normalize(actionType)
  if (!action) {
    return ''
  }
  return `${action}:${normalizeTimelineTime(time)}`
}

function normalizeTimelineTime(value) {
  const formatted = formatDateTime(value)
  return formatted || String(value || '').trim()
}

function timelineTimestamp(value) {
  const text = normalizeTimelineTime(value)
  if (!text) {
    return 0
  }
  const timestamp = Date.parse(text.replace(' ', 'T'))
  return Number.isFinite(timestamp) ? timestamp : 0
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

async function loadDirectDepositConfig() {
  try {
    const rows = await fetchSystemConfigs('deposit.direct.')
    const row = rows.find((item) => item.configKey === 'deposit.direct.enabled')
    directDepositEnabled.value = row ? String(row.configValue).toLowerCase() === 'true' && row.enabled !== 0 : true
  } catch {
    directDepositEnabled.value = true
  }
}

async function loadAmountVisibilityConfig() {
  try {
    const rows = await fetchSystemConfigs('amount.visibility.')
    const row = rows.find((item) => item.configKey === 'amount.visibility.service_confirm_edit_roles')
    serviceConfirmAmountEditRoles.value = row && row.enabled !== 0
      ? parseRoleList(row.configValue, DEFAULT_SERVICE_CONFIRM_AMOUNT_EDIT_ROLES)
      : [...DEFAULT_SERVICE_CONFIRM_AMOUNT_EDIT_ROLES]
  } catch {
    serviceConfirmAmountEditRoles.value = [...DEFAULT_SERVICE_CONFIRM_AMOUNT_EDIT_ROLES]
  }
}

function parseRoleList(value, fallback) {
  const roles = String(value || '')
    .split(/[,，\s]+/)
    .map((item) => normalize(item))
    .filter(Boolean)
  return roles.length ? roles : [...fallback]
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
  await Promise.all([loadStaffOptions(), loadDirectDepositConfig(), loadAmountVisibilityConfig()])
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
    if (nextActionDisabledReason.value) {
      ElMessage.warning(nextActionDisabledReason.value)
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
      ElMessage.error('服务确认单未保存成功，暂不能推进状态')
      return
    }
    if (detail.value?.summary?.finishTime) {
      ElMessage.info('当前服务确认单已完成')
      return
    }
    if (detail.value?.summary?.startTime) {
      await handlePlanAction('finish')
      return
    }
    if (!hasConfirmedServiceForm.value) {
      ElMessage.warning('请先确认纸质服务确认单')
      return
    }
    if (!detail.value?.summary?.arriveTime) {
      await handlePlanAction('arrive', { silent: true, skipReload: true })
    }
    await handlePlanAction('start')
  } finally {
    confirmingAction.value = false
  }
}

async function handleConfirmPaperForm() {
  if (!canConfirmPaperForm.value) {
    ElMessage.warning(nextActionDisabledReason.value || '请先保存服务确认单草稿')
    return
  }
  try {
    await ElMessageBox.confirm(
      canViewServiceConfirmAmounts.value
        ? '请确认服务确认单已经打印，客户已在线下纸质单手写签名。本操作只记录纸质确认，不会自动开始服务。'
        : '当前角色不能查看确认单金额，请确认客户签署的是当前打印版本；如需金额确认，请由店长或选片负责人重新打印含金额版本。本操作只记录纸质确认，不会自动开始服务。',
      '确认纸质单',
      {
        confirmButtonText: '确认纸质单',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  confirmingAction.value = true
  try {
    const saved = await handleSaveServiceForm({ silent: true })
    if (!saved) {
      ElMessage.error('服务确认单未保存成功，暂不能确认纸质单')
      return
    }
    await confirmPlanOrderServiceForm({
      planOrderId: Number(route.params.id)
    })
    serviceForm.confirmation = {
      status: 'PRINT_CONFIRMED',
      signatureMode: 'PAPER',
      signatureRequired: true
    }
    ElMessage.success('纸质确认单已确认，可单独开始服务')
    await loadPageContext()
  } finally {
    confirmingAction.value = false
  }
}

function confirmAdvanceMessage() {
  if (detail.value?.summary?.startTime) {
    return '确认完成本次门店服务吗？完成后订单会进入已完成状态，并用于后续服务分成统计；此操作不会发起退款或资金划拨。'
  }
  if (!hasConfirmedServiceForm.value) {
    return '纸质确认单尚未确认，请先打印并确认客户手写签名。'
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
    syncServiceFormVersionState(response?.serviceDetailJson)
    dirty.value = false
    const timeText = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    autosaveMessage.value = options.autosave ? `已自动保存 ${timeText}` : `已保存 ${timeText}`
    if (!options.silent) {
      ElMessage.success(options.autosave ? '已自动保存' : '服务确认单已保存')
    }
    return true
  } catch {
    autosaveMessage.value = options.autosave ? '自动保存失败，请手动保存' : autosaveMessage.value
    if (!options.silent) {
      ElMessage.error('服务确认单保存失败')
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
  if (printingServiceForm.value) {
    return
  }
  if (canEditForm.value && dirty.value) {
    const saved = await handleSaveServiceForm({ silent: true })
    if (!saved) {
      ElMessage.error('服务确认单未保存成功，暂不能打印')
      return
    }
  }
  if (!canViewServiceConfirmAmounts.value) {
    try {
      await ElMessageBox.confirm(
        '当前角色打印的纸质确认单不包含确认单金额，建议由店长或选片负责人打印含金额版本。是否继续打印？',
        '确认打印',
        {
          confirmButtonText: '继续打印',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }
  printingServiceForm.value = true
  try {
    await printPlanOrderServiceForm({
      planOrderId: Number(route.params.id)
    })
    await loadPageContext()
    ElMessage.success('确认单打印记录已生成')
    await nextTick()
    window.print()
  } finally {
    printingServiceForm.value = false
  }
}

function buildServiceFormMessage() {
  const url = buildSystemUrl(state, 'scan', `/service-scan/${route.params.id}?mode=view`)
  const lines = [
    `您好，${customerDisplayName.value} 的服务确认单已更新。`,
    `门店：${storeDisplayName.value}`,
    `预约时间：${appointmentLabel.value}`,
    `服务需求：${fieldText(serviceForm.serviceRequirement)}`,
    `查看链接：${url}`
  ]
  if (canViewServiceConfirmAmounts.value) {
    lines.splice(3, 0, `服务确认单金额：${serviceConfirmAmountLabel.value}`)
  }
  return lines.join('\n')
}

async function handleSaveAndSend() {
  if (!canSendToCustomer.value) {
    ElMessage.warning(sendToCustomerDisabledReason.value || '暂不能发送')
    return
  }
  try {
    await ElMessageBox.confirm(
      canViewServiceConfirmAmounts.value
        ? `将通过企业微信把当前服务确认单发送给 ${customerDisplayName.value}，是否继续？`
        : `将通过企业微信把当前服务确认单发送给 ${customerDisplayName.value}，本次发送内容不包含确认单金额，是否继续？`,
      '发送确认单',
      {
        confirmButtonText: '确认发送',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
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
      ElMessage.error('服务确认单未保存成功，暂未发送')
      return
    }
    await sendPlanOrderServiceForm({
      planOrderId: Number(route.params.id),
      message: buildServiceFormMessage()
    })
    ElMessage.success('服务确认单已保存并发送给客户')
  } catch {
    ElMessage.warning('服务确认单已保存，但发送失败，请稍后重试')
  } finally {
    sendingToCustomer.value = false
  }
}

function openCameraScanner() {
  if (!canVerifyVoucher.value) {
    ElMessage.warning('订单来源未识别，请联系管理员补齐来源或核销策略')
    return
  }
  scannerHint.value = `${voucherProviderLabel.value}团购券会在外部接口核销成功后推进服务流程，请将二维码置于取景框内`
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
  } catch (error) {
    scannerError.value = buildVoucherFailureMessage(error)
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

function resolveVoucherProviderLabel(order) {
  const providerCode = resolveVoucherProviderCode(order)
  if (providerCode === 'DISTRIBUTION') {
    return '分销'
  }
  if (providerCode === 'DOUYIN_LAIKE') {
    return '抖音'
  }
  return '未知来源'
}

function resolveVoucherProviderCode(order) {
  const sourceChannel = normalize(order?.sourceChannel || order?.source || '')
  const partnerCode = normalize(order?.externalPartnerCode || '')
  if (sourceChannel === 'DISTRIBUTOR' || sourceChannel === 'DISTRIBUTION' || partnerCode === 'DISTRIBUTION') {
    return 'DISTRIBUTION'
  }
  if (sourceChannel === 'DOUYIN' || partnerCode === 'DOUYIN_LAIKE') {
    return 'DOUYIN_LAIKE'
  }
  return ''
}

function buildVoucherFailureMessage(error) {
  const reason = normalizeApiError(error) || '外部核销接口未返回明确原因'
  if (isDepositOrder.value) {
    return `定金免码确认失败，订单仍为待确认。失败原因：${reason}`
  }
  if (!voucherProviderCode.value) {
    return `订单来源未识别，系统没有找到对应的券核销通道。失败原因：${reason}。请复制排障信息发给管理员补齐来源或核销策略。`
  }
  return `${voucherProviderLabel.value}团购券未核销成功，订单仍为待核销。失败原因：${reason}。可重新扫码、改为输码，或联系管理员检查${voucherProviderLabel.value}券核销配置。`
}

function normalizeApiError(error) {
  const data = error?.response?.data || {}
  const nested = data?.data || {}
  const reason = firstText(
    data.providerMessage,
    data.message,
    data.detail,
    data.errorMessage,
    data.msg,
    nested.providerMessage,
    nested.message,
    nested.detail,
    nested.errorMessage,
    nested.msg,
    error?.message
  )
  const traceId = firstText(data.traceId, data.trace_id, data.requestId, nested.traceId, nested.trace_id, nested.requestId)
  const cleanReason = reason.replace(/\s+/g, ' ').slice(0, 180)
  if (!cleanReason) {
    return traceId ? `第三方返回异常（追踪编号：${traceId}）` : ''
  }
  return traceId ? `${cleanReason}（追踪编号：${traceId}）` : cleanReason
}

function firstText(...values) {
  return values.map((value) => String(value || '').trim()).find(Boolean) || ''
}

async function copyVoucherFailureInfo() {
  const text = [
    `订单号：${detail.value?.order?.orderNo || '--'}`,
    `订单ID：${detail.value?.order?.id || route.params.id || '--'}`,
    `核销来源：${voucherProviderLabel.value}`,
    `核销状态：${formatVerificationStatus(detail.value?.order?.verificationStatus || 'UNVERIFIED')}`,
    `失败原因：${voucherVerifyError.value || scannerError.value || '--'}`
  ].join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('排障信息已复制，可发给管理员处理')
  } catch {
    ElMessage.warning('复制失败，请手动截图或转发失败原因')
  }
}

async function handleCodeVerify() {
  if (!canVerifyVoucher.value) {
    ElMessage.warning('订单来源未识别，请联系管理员补齐来源或核销策略')
    return
  }
  if (!String(verificationCode.value || '').trim()) {
    ElMessage.warning('请输入核销码')
    await focusVerificationInput()
    return
  }
  try {
    await verifyCurrentOrder(String(verificationCode.value || '').trim(), 'CODE')
  } catch {
    await focusVerificationInput()
  }
}

async function handleDirectDepositVerify() {
  if (!directDepositEnabled.value) {
    ElMessage.warning('定金免码确认已停用，请在系统流程配置中开启')
    return
  }
  try {
    await ElMessageBox.confirm(
      '定金订单不用扫码或输码。确认后将记录为定金到店确认，并进入服务确认单流程。本操作不会调用真实核销接口，也不会生成券码核销记录。',
      '确认定金免码到店？',
      {
        confirmButtonText: '确认到店',
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
  voucherVerifyError.value = ''
  scannerError.value = ''
  verifyingVoucher.value = true
  try {
    if (method !== 'DIRECT_DEPOSIT') {
      scannerHint.value = verifyingHint.value
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
    ElMessage.success(method === 'DIRECT_DEPOSIT' ? '定金到店已确认，可以继续填写服务确认单' : `${voucherProviderLabel.value}核销成功，可以继续填写服务确认单`)
    await scrollToFormStart()
    return response
  } catch (error) {
    voucherVerifyError.value = buildVoucherFailureMessage(error)
    throw error
  } finally {
    verifyingVoucher.value = false
  }
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

function goVoucherConfig() {
  if (!voucherConfigTarget.value) {
    ElMessage.warning('订单来源未识别，请联系管理员补齐来源或核销策略')
    return
  }
  if (!canOpenVoucherConfig.value) {
    ElMessage.warning('当前角色不能进入接口配置，请复制排障信息发给管理员处理')
    return
  }
  router.push(voucherConfigTarget.value.path)
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

.verify-panel__error {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(220, 38, 38, 0.22);
  background: #fff1f2;
  color: #991b1b;
}

.verify-panel__error strong {
  color: #7f1d1d;
}

.verify-panel__error span {
  line-height: 1.6;
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

.verify-hero:disabled {
  cursor: not-allowed;
  opacity: 0.62;
  box-shadow: none;
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

.print-service-form {
  display: none;
}

.print-service-form__head {
  text-align: center;
  border-bottom: 2px solid #0f172a;
  padding-bottom: 14px;
  margin-bottom: 18px;
}

.print-service-form__head h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
}

.print-service-form__head p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 14px;
}

.print-service-form__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid #0f172a;
  border-bottom: 0;
}

.print-service-form__meta div {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  min-height: 38px;
  border-bottom: 1px solid #0f172a;
}

.print-service-form__meta span,
.print-service-form__meta strong {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  color: #0f172a;
  font-size: 13px;
}

.print-service-form__meta span {
  border-right: 1px solid #0f172a;
  font-weight: 700;
  background: #f8fafc;
}

.print-service-form__section {
  margin-top: 18px;
}

.print-service-form__section h2 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 18px;
}

.print-service-form__section dl {
  display: grid;
  gap: 0;
  margin: 0;
  border: 1px solid #0f172a;
  border-bottom: 0;
}

.print-service-form__section dl div {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  min-height: 42px;
  border-bottom: 1px solid #0f172a;
}

.print-service-form__section dt,
.print-service-form__section dd {
  margin: 0;
  padding: 10px 12px;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.print-service-form__section dt {
  border-right: 1px solid #0f172a;
  font-weight: 700;
  background: #f8fafc;
}

.print-service-form__sign {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 36px;
}

.print-service-form__sign div {
  display: grid;
  gap: 18px;
}

.print-service-form__sign span {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.print-service-form__sign strong {
  min-height: 42px;
  border-bottom: 1px solid #0f172a;
  color: #0f172a;
  font-size: 14px;
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

.scanner-dialog__note {
  color: #64748b;
  font-size: 13px;
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

@media print {
  @page {
    size: A4;
    margin: 12mm;
  }

  :global(body) {
    background: #ffffff !important;
  }

  .plan-order-page,
  .service-shell {
    display: block;
    width: auto;
    min-width: 0;
    background: #ffffff !important;
  }

  .service-header,
  .service-workspace,
  .service-action-bar,
  .scanner-dialog {
    display: none !important;
  }

  .print-service-form {
    display: block;
    color: #0f172a;
    background: #ffffff;
  }
}
</style>
