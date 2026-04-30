<template>
  <div class="stack-page clue-management-page">
    <section class="panel">
      <div class="toolbar">
        <div class="toolbar-tabs">
          <el-radio-group v-model="productSourceFilter" @change="applyFilters">
            <el-radio-button value="ALL">{{ sourceFilterLabel('ALL') }}</el-radio-button>
            <el-radio-button value="GROUP_BUY">{{ sourceFilterLabel('GROUP_BUY') }}</el-radio-button>
            <el-radio-button value="FORM">{{ sourceFilterLabel('FORM') }}</el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar__filters">
          <el-button plain @click="columnDrawerVisible = true">可选列</el-button>
        </div>
      </div>

      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="filters.queueStatus" @change="applyFilters">
            <el-radio-button value="ALL">{{ queueFilterLabel('ALL') }}</el-radio-button>
            <el-radio-button value="WAIT_ASSIGN">{{ queueFilterLabel('WAIT_ASSIGN') }}</el-radio-button>
            <el-radio-button value="WAIT_FOLLOW_UP">{{ queueFilterLabel('WAIT_FOLLOW_UP') }}</el-radio-button>
          </el-radio-group>
        </div>

        <div class="toolbar__filters">
          <el-input v-model="filters.phone" clearable placeholder="手机号搜索" style="width: 180px" />
          <el-date-picker
            v-model="filters.createdRange"
            type="daterange"
            unlink-panels
            range-separator="至"
            start-placeholder="创建开始"
            end-placeholder="创建结束"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
          <el-button @click="applyFilters">筛选</el-button>
          <el-button plain @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div
        v-show="hasFloatingTableScrollbar"
        ref="floatingTableScrollbarRef"
        class="floating-table-scrollbar"
        @scroll="handleFloatingTableScroll"
      >
        <div class="floating-table-scrollbar__inner" :style="{ width: `${floatingTableScrollWidth}px` }"></div>
      </div>

      <el-table
        ref="clueTableRef"
        class="clue-list-table"
        v-loading="loading"
        :data="pagination.rows"
        row-key="id"
        stripe
        table-layout="fixed"
        max-height="560"
        scrollbar-always-on
      >
        <el-table-column label="姓名" width="160" fixed="left">
          <template #default="{ row }">
            <div class="editable-cell">
              <span class="editable-cell__text">{{ displayName(row) }}</span>
              <el-popover placement="bottom-start" :width="260" trigger="click" @show="prepareCellDraft(row.id, 'displayName', row.editName)">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑姓名">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="editable-popover">
                  <h4>编辑姓名</h4>
                  <el-input
                    :model-value="getCellDraft(row.id, 'displayName', row.editName)"
                    :placeholder="`线索#${row.id}`"
                    @update:model-value="setCellDraft(row.id, 'displayName', $event)"
                  />
                  <div class="action-group flex-end">
                    <el-button type="primary" size="small" @click="saveCellEdit(row, 'displayName', row.editName, 'displayName')">
                      保存
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="电话" width="150" fixed="left">
          <template #default="{ row }">
            <div class="editable-cell">
              <span class="editable-cell__text">{{ row.editPhone || '--' }}</span>
              <el-popover placement="bottom-start" :width="280" trigger="click" @show="prepareCellDraft(row.id, 'phone', row.editPhone)">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑电话">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="editable-popover">
                  <h4>编辑电话</h4>
                  <el-input
                    :model-value="getCellDraft(row.id, 'phone', row.editPhone)"
                    placeholder="请输入联系电话"
                    @update:model-value="setCellDraft(row.id, 'phone', $event)"
                  />
                  <div class="action-group flex-end">
                    <el-button type="primary" size="small" @click="saveCellEdit(row, 'phone', row.editPhone)">
                      保存
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          v-for="column in visibleTableColumns"
          :key="column.key"
          :label="column.label"
          :width="column.width"
        >
          <template #default="{ row }">
            <template v-if="column.key === 'orderType'">
              <el-tag effect="plain">{{ formatClueOrderType(row.orderType) }}</el-tag>
            </template>

            <template v-else-if="column.key === 'paymentStatus'">
              <el-tag :type="row.leadStage === 'DEPOSIT_PAID' ? 'success' : 'info'" effect="light">
                {{ row.paymentStatusLabel }}
              </el-tag>
            </template>

            <div v-else-if="column.key === 'callStatus'" class="editable-cell">
              <span class="editable-cell__text">{{ formatCallStatus(row.callStatus) }}</span>
              <el-popover placement="bottom-start" :width="240" trigger="click" @show="prepareCellDraft(row.id, 'callStatus', row.callStatus)">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑通话状态">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="editable-popover">
                  <h4>编辑通话状态</h4>
                  <el-select
                    :model-value="getCellDraft(row.id, 'callStatus', row.callStatus)"
                    style="width: 100%"
                    @update:model-value="setCellDraft(row.id, 'callStatus', $event)"
                  >
                    <el-option v-for="item in callStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <div class="action-group flex-end">
                    <el-button type="primary" size="small" @click="saveCellEdit(row, 'callStatus', row.callStatus)">
                      保存
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>

            <div v-else-if="column.key === 'leadStage'" class="editable-cell">
              <span class="editable-cell__text">{{ formatLeadStage(row.leadStage) }}</span>
              <el-popover placement="bottom-start" :width="260" trigger="click" @show="prepareCellDraft(row.id, 'leadStage', row.leadStage)">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑线索阶段">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="editable-popover">
                  <h4>编辑线索阶段</h4>
                  <el-select
                    :model-value="getCellDraft(row.id, 'leadStage', row.leadStage)"
                    style="width: 100%"
                    @update:model-value="setCellDraft(row.id, 'leadStage', $event)"
                  >
                    <el-option v-for="item in leadStageOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <div class="action-group flex-end">
                    <el-button type="primary" size="small" @click="saveCellEdit(row, 'leadStage', row.leadStage)">
                      保存
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>

            <div v-else-if="column.key === 'followRecords'" class="editable-cell editable-cell--stack">
              <div class="table-primary">
                <strong>{{ latestFollowRecord(row) }}</strong>
                <span>{{ row.followRecords.length ? `共 ${row.followRecords.length} 条` : '暂无跟进记录' }}</span>
              </div>

              <el-popover placement="bottom-start" :width="380" trigger="click" popper-class="follow-record-popover">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑跟进记录">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="follow-record-panel">
                  <div class="panel-heading compact">
                    <div>
                      <h3>跟进记录</h3>
                    </div>
                  </div>

                  <div v-if="row.followRecords.length" class="follow-record-list">
                    <article v-for="record in row.followRecords" :key="record.id" class="follow-record-item">
                      <div class="follow-record-item__header">
                        <strong>{{ formatDateTime(record.createdAt) }}</strong>
                        <el-popconfirm title="确认删除这条跟进记录吗？" @confirm="removeFollowRecord(row, record.id)">
                          <template #reference>
                            <el-button link type="danger">删除</el-button>
                          </template>
                        </el-popconfirm>
                      </div>
                      <p>{{ record.content }}</p>
                    </article>
                  </div>
                  <p v-else class="text-secondary">暂无跟进记录</p>

                  <div class="follow-record-editor">
                    <el-input
                      v-model="followRecordDrafts[row.id]"
                      type="textarea"
                      :rows="3"
                      placeholder="请输入本次跟进内容"
                    />
                    <div class="action-group">
                      <el-button type="primary" size="small" @click="addFollowRecord(row)">提交记录</el-button>
                    </div>
                  </div>
                </div>
              </el-popover>
            </div>

            <div v-else-if="column.key === 'leadTags'" class="editable-cell">
              <span class="editable-cell__text">{{ previewLeadTags(row.leadTags) }}</span>
              <el-popover placement="bottom-start" :width="320" trigger="click" @show="prepareCellDraft(row.id, 'leadTags', row.leadTags)">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑标签">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="editable-popover">
                  <h4>编辑标签</h4>
                  <el-select
                    :model-value="getCellDraft(row.id, 'leadTags', row.leadTags)"
                    class="stack-field-cell__control"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    collapse-tags
                    collapse-tags-tooltip
                    size="small"
                    placeholder="标签"
                    @update:model-value="setCellDraft(row.id, 'leadTags', $event)"
                  >
                    <el-option v-for="item in tagOptions" :key="item" :label="item" :value="item" />
                  </el-select>
                  <div class="action-group flex-end">
                    <el-button type="primary" size="small" @click="saveCellEdit(row, 'leadTags', row.leadTags)">
                      保存
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>

            <div v-else-if="column.key === 'intendedStoreName'" class="editable-cell">
              <span class="editable-cell__text">{{ row.intendedStoreName || '--' }}</span>
              <el-popover placement="bottom-start" :width="260" trigger="click" @show="prepareCellDraft(row.id, 'intendedStoreName', row.intendedStoreName)">
                <template #reference>
                  <el-button link class="editable-cell__trigger" title="编辑意向门店">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </template>

                <div class="editable-popover">
                  <h4>编辑意向门店</h4>
                  <el-select
                    :model-value="getCellDraft(row.id, 'intendedStoreName', row.intendedStoreName)"
                    style="width: 100%"
                    placeholder="请选择门店"
                    @update:model-value="setCellDraft(row.id, 'intendedStoreName', $event)"
                  >
                    <el-option v-for="item in storeOptions" :key="item" :label="item" :value="item" />
                  </el-select>
                  <div class="action-group flex-end">
                    <el-button type="primary" size="small" @click="saveCellEdit(row, 'intendedStoreName', row.intendedStoreName)">
                      保存
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>

            <template v-else-if="column.key === 'assignedAt'">
              {{ formatDateTime(row.assignedAt) }}
            </template>

            <template v-else-if="column.key === 'createdAt'">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <div class="action-group action-group--compact">
              <el-button size="small" @click="openDetailDrawer(row)">详情</el-button>
              <el-button
                v-if="row.paidOrderId"
                data-qa="clue-schedule-action"
                type="primary"
                size="small"
                @click="goToScheduling(row)"
              >
                约档
              </el-button>
              <el-dropdown v-if="canShowMoreActions(row)">
                <el-button size="small" plain>更多操作</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canAssignClue" @click="openAssignDialog(row)">分配客服</el-dropdown-item>
                    <el-dropdown-item
                      v-if="canRecycleClue"
                      :disabled="!row.currentOwnerId"
                      @click="handleRecycle(row)"
                    >
                      回收线索
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
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
    </section>

    <el-drawer v-model="columnDrawerVisible" title="可选列" size="360px">
      <div class="stack-page">
        <section class="panel compact-panel">
          <div class="panel-heading compact">
            <div>
              <h3>列表字段</h3>
              <p class="panel-heading__meta">选择显示字段，并用上移/下移调整顺序。</p>
            </div>
          </div>
          <div class="column-config-list">
            <article v-for="(column, index) in configurableColumns" :key="column.key" class="column-config-item">
              <el-checkbox
                :model-value="column.visible"
                :disabled="column.required"
                @change="setColumnVisible(column.key, $event)"
              >
                {{ column.label }}
              </el-checkbox>
              <div class="action-group action-group--compact">
                <el-button size="small" plain :disabled="index === 0" @click="moveColumn(index, -1)">上移</el-button>
                <el-button size="small" plain :disabled="index === configurableColumns.length - 1" @click="moveColumn(index, 1)">下移</el-button>
              </div>
            </article>
          </div>
        </section>
        <div class="action-group flex-end">
          <el-button plain @click="resetColumnPreferences">恢复默认</el-button>
          <el-button type="primary" @click="columnDrawerVisible = false">完成</el-button>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="detailDrawerVisible" title="线索详情" size="560px">
      <template v-if="detailRow">
        <div class="stack-page">
          <section class="panel">
            <div class="detail-grid">
              <article class="detail-card">
                <h3>基础信息</h3>
                <p>线索编号：{{ detailRow.id }}</p>
                <p>姓名：{{ displayName(detailRow) }}</p>
                <p>电话：{{ detailRow.editPhone || '--' }}</p>
                <p>来源形式：{{ formatProductSourceType(detailRow.productSourceType) }}</p>
                <p>订单类型：{{ formatClueOrderType(detailRow.orderType) }}</p>
                <p>付款状态：{{ detailRow.paymentStatusLabel }}</p>
              </article>
              <article class="detail-card">
                <h3>跟进状态</h3>
                <p>通话状态：{{ formatCallStatus(detailRow.callStatus) }}</p>
                <p>线索阶段：{{ formatLeadStage(detailRow.leadStage) }}</p>
                <p>意向门店：{{ detailRow.intendedStoreName || '--' }}</p>
                <p>分配客服：{{ detailRow.currentOwnerName || '未分配' }}</p>
              </article>
            </div>
          </section>

          <section class="panel">
                <div class="panel-heading compact">
                  <div>
                    <h3>线索标签</h3>
                  </div>
                </div>
            <div class="chip-row">
              <el-tag v-for="tag in detailRow.leadTags" :key="tag" effect="plain" type="success">{{ tag }}</el-tag>
              <span v-if="!detailRow.leadTags.length" class="text-secondary">暂无标签</span>
            </div>
          </section>

          <section class="panel">
                <div class="panel-heading compact">
                  <div>
                    <h3>客资记录</h3>
                  </div>
                </div>
            <div v-if="detailRow.leadRecordItems.length" class="follow-record-list">
              <article v-for="record in detailRow.leadRecordItems" :key="record.id" class="follow-record-item">
                <div class="follow-record-item__header">
                  <strong>{{ record.title }}</strong>
                  <span>{{ formatDateTime(record.createdAt) }}</span>
                </div>
                <p>{{ record.content }}</p>
              </article>
            </div>
            <p v-else class="text-secondary">暂无客资记录</p>
          </section>
        </div>
      </template>
    </el-drawer>

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
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { assignClue, recycleClue } from '../api/actions'
import { fetchDutyCustomerServices } from '../api/clueManagement'
import { fetchClues, fetchOrders } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { currentUser } from '../utils/auth'
import {
  formatCallStatus,
  formatDateTime,
  formatLeadStage,
  formatOrderStage,
  formatProductSourceType,
  normalize
} from '../utils/format'
import { listStoreNames, loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const router = useRouter()
const consoleState = reactive(loadSystemConsoleState())
const loading = ref(true)
const clues = ref([])
const paidOrders = ref([])
const dutyStaff = ref([])
const assignDialogVisible = ref(false)
const detailDrawerVisible = ref(false)
const columnDrawerVisible = ref(false)
const assignTarget = ref(null)
const detailRowId = ref(null)
const productSourceFilter = ref('ALL')
const clueTableRef = ref(null)
const floatingTableScrollbarRef = ref(null)
const floatingTableScrollWidth = ref(0)
const hasFloatingTableScrollbar = ref(false)
const followRecordDrafts = reactive({})
const cellDrafts = reactive({})
let refreshTimer = null
let tableScrollWrap = null
let scrollSyncing = false
let tableResizeObserver = null

const COLUMN_PREFERENCE_KEY = 'seedcrm:clue-list-columns'
const defaultColumnConfig = [
  { key: 'orderType', label: '订单类型', width: 120, visible: true, required: false },
  { key: 'paymentStatus', label: '付款状态', width: 126, visible: true, required: false },
  { key: 'callStatus', label: '通话状态', width: 136, visible: true, required: false },
  { key: 'leadStage', label: '线索阶段', width: 144, visible: true, required: true },
  { key: 'followRecords', label: '跟进记录', width: 220, visible: true, required: false },
  { key: 'leadTags', label: '线索标签', width: 180, visible: true, required: false },
  { key: 'intendedStoreName', label: '意向门店', width: 160, visible: true, required: false },
  { key: 'assignedAt', label: '分配时间', width: 180, visible: true, required: false },
  { key: 'createdAt', label: '创建时间', width: 180, visible: true, required: false }
]
const configurableColumns = ref(loadColumnPreferences())
const visibleTableColumns = computed(() => configurableColumns.value.filter((item) => item.visible !== false))

const filters = reactive({
  phone: '',
  createdRange: [],
  queueStatus: 'ALL'
})

const clueSourceRows = computed(() => {
  const existingClueIds = new Set(clues.value.map((item) => item.id))
  const fallbackRows = paidOrders.value
    .filter((item) => item?.clueId && !existingClueIds.has(item.clueId))
    .map((item) => buildSyntheticClueFromOrder(item))
  return [...fallbackRows, ...clues.value].sort((left, right) => compareClueRecency(right, left))
})
const mergedClues = computed(() => clueSourceRows.value.map((item) => buildClueRow(item)))
const filteredClues = computed(() =>
  mergedClues.value.filter((item) => matchesProductSource(item) && matchesPhone(item) && matchesCreatedRange(item) && matchesQueueStatus(item))
)
const pagination = useTablePagination(filteredClues)
const storeOptions = computed(() => {
  const values = [
    ...listStoreNames(consoleState),
    ...clueSourceRows.value.map((item) => item.storeName).filter(Boolean),
    ...paidOrders.value.map((item) => item.storeName).filter(Boolean)
  ]
  return [...new Set(values)]
})
const latestPaidOrderByClueId = computed(() => {
  const orderMap = new Map()
  for (const item of paidOrders.value) {
    if (!item?.clueId) {
      continue
    }
    const current = orderMap.get(item.clueId)
    if (!current || compareOrderRecency(item, current) > 0) {
      orderMap.set(item.clueId, item)
    }
    }
  return orderMap
})
const assignableStaff = computed(() => dutyStaff.value.filter((item) => item.onLeave !== 1))
const canAssignClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(currentUser.value?.roleCode || ''))
const canRecycleClue = computed(() => ['ADMIN', 'CLUE_MANAGER'].includes(currentUser.value?.roleCode || ''))
const detailRow = computed(() => mergedClues.value.find((item) => item.id === detailRowId.value) || null)
const sourceFilterCounts = computed(() => ({
  ALL: mergedClues.value.length,
  GROUP_BUY: mergedClues.value.filter((item) => normalize(item.productSourceType) === 'GROUP_BUY').length,
  FORM: mergedClues.value.filter((item) => normalize(item.productSourceType) === 'FORM').length
}))
const queueFilterCounts = computed(() => {
  const sourceMatchedRows = mergedClues.value.filter((item) => matchesProductSource(item) && matchesPhone(item) && matchesCreatedRange(item))
  return {
    ALL: sourceMatchedRows.length,
    WAIT_ASSIGN: sourceMatchedRows.filter((item) => resolveQueueStatus(item) === 'WAIT_ASSIGN').length,
    WAIT_FOLLOW_UP: sourceMatchedRows.filter((item) => resolveQueueStatus(item) === 'WAIT_FOLLOW_UP').length
  }
})

const callStatusOptions = [
  { label: '未通话', value: 'NOT_CALLED' },
  { label: '已接通', value: 'CONNECTED' },
  { label: '未接通', value: 'MISSED' },
  { label: '待回拨', value: 'CALLBACK' },
  { label: '无效号码', value: 'INVALID' }
]

const leadStageOptions = [
  { label: '新线索', value: 'NEW' },
  { label: '有意向', value: 'INTENT' },
  { label: '到店', value: 'ARRIVED' },
  { label: '成交', value: 'DEAL' },
  { label: '待再次沟通', value: 'CALLBACK_PENDING' },
  { label: '已加微信', value: 'WECHAT_ADDED' },
  { label: '预付定金', value: 'DEPOSIT_PAID' },
  { label: '无效', value: 'INVALID' }
]

const tagOptions = ['高意向', '团购', '表单', '已付款', '已加微信', '待再次沟通', '待到店', '复诊']

function loadColumnPreferences() {
  try {
    const saved = JSON.parse(window.localStorage.getItem(COLUMN_PREFERENCE_KEY) || '[]')
    if (Array.isArray(saved) && saved.length) {
      const known = new Map(defaultColumnConfig.map((item) => [item.key, item]))
      const merged = saved
        .filter((item) => known.has(item?.key))
        .map((item) => ({
          ...known.get(item.key),
          visible: known.get(item.key).required ? true : item.visible !== false
        }))
      for (const item of defaultColumnConfig) {
        if (!merged.some((column) => column.key === item.key)) {
          merged.push({ ...item })
        }
      }
      return merged
    }
  } catch {
    // Ignore invalid local preferences and fall back to defaults.
  }
  return defaultColumnConfig.map((item) => ({ ...item }))
}

function persistColumnPreferences() {
  window.localStorage.setItem(COLUMN_PREFERENCE_KEY, JSON.stringify(configurableColumns.value))
  void updateFloatingTableScrollbar()
}

function isColumnVisible(key) {
  const column = configurableColumns.value.find((item) => item.key === key)
  return column ? column.visible !== false : true
}

function setColumnVisible(key, visible) {
  configurableColumns.value = configurableColumns.value.map((item) =>
    item.key === key ? { ...item, visible: item.required ? true : Boolean(visible) } : item
  )
  persistColumnPreferences()
}

function moveColumn(index, direction) {
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= configurableColumns.value.length) {
    return
  }
  const nextColumns = [...configurableColumns.value]
  const [item] = nextColumns.splice(index, 1)
  nextColumns.splice(nextIndex, 0, item)
  configurableColumns.value = nextColumns
  persistColumnPreferences()
}

function resetColumnPreferences() {
  configurableColumns.value = defaultColumnConfig.map((item) => ({ ...item }))
  persistColumnPreferences()
}

function sourceFilterLabel(value) {
  const labels = {
    ALL: '全部产品',
    GROUP_BUY: '团购',
    FORM: '表单'
  }
  return `${labels[value] || value} ${sourceFilterCounts.value[value] ?? 0}`
}

function queueFilterLabel(value) {
  const labels = {
    ALL: '全部',
    WAIT_ASSIGN: '待分配',
    WAIT_FOLLOW_UP: '待跟进'
  }
  return `${labels[value] || value} ${queueFilterCounts.value[value] ?? 0}`
}

function parseDateValue(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

function compareClueRecency(left, right) {
  return parseDateValue(left?.createdAt) - parseDateValue(right?.createdAt)
}

function normalizeDraftValue(value) {
  if (Array.isArray(value)) {
    return [...value]
  }
  return value ?? ''
}

function buildCellDraftKey(rowId, field) {
  return `${rowId}:${field}`
}

function prepareCellDraft(rowId, field, value) {
  cellDrafts[buildCellDraftKey(rowId, field)] = normalizeDraftValue(value)
}

function getCellDraft(rowId, field, fallback = '') {
  const key = buildCellDraftKey(rowId, field)
  if (!Object.prototype.hasOwnProperty.call(cellDrafts, key)) {
    prepareCellDraft(rowId, field, fallback)
  }
  return cellDrafts[key]
}

function setCellDraft(rowId, field, value) {
  cellDrafts[buildCellDraftKey(rowId, field)] = normalizeDraftValue(value)
}

function compareOrderRecency(left, right) {
  const leftTime = parseDateValue(left?.appointmentTime || left?.createTime)
  const rightTime = parseDateValue(right?.appointmentTime || right?.createTime)
  if (leftTime !== rightTime) {
    return leftTime - rightTime
  }
  return Number(left?.id || 0) - Number(right?.id || 0)
}

function resolvePaidOrder(row) {
  return latestPaidOrderByClueId.value.get(row.id) || null
}

function resolveLatestOrderStatus(row) {
  const paidOrder = resolvePaidOrder(row)
  return paidOrder?.status || paidOrder?.statusCategory || row.latestOrderStatus
}

function resolveLatestOrderId(row) {
  const paidOrder = resolvePaidOrder(row)
  return paidOrder?.id || row.latestOrderId || null
}

function resolveLatestOrderType(row) {
  const paidOrder = resolvePaidOrder(row)
  return paidOrder?.type || row.latestOrderType || null
}

function buildSyntheticClueFromOrder(order) {
  const profile = findClueProfile(order.clueId)
  return {
    id: order.clueId,
    name: profile?.displayName || order.customerName || '',
    phone: profile?.phone || order.customerPhone || '',
    wechat: '',
    sourceChannel: order.sourceChannel,
    productSourceType: order.productSourceType,
    sourceId: null,
    status: 'CONVERTED',
    currentOwnerId: currentUser.value?.userId || null,
    currentOwnerName: currentUser.value?.displayName || '',
    isPublic: 0,
    storeName: profile?.intendedStoreName || order.storeName || '',
    customerId: order.customerId,
    latestOrderId: order.id,
    latestOrderStatus: order.statusCategory || order.status,
    latestOrderType: order.type,
    orderCount: 1,
    createdAt: order.createTime
  }
}

function replaceConsoleState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(consoleState, loadSystemConsoleState())
}

function currentTimestampString() {
  const value = new Date()
  const offset = value.getTimezoneOffset() * 60000
  return new Date(value.getTime() - offset).toISOString().slice(0, 19).replace('T', ' ')
}

function sortFollowRecords(records = []) {
  return [...records].sort((left, right) => parseDateValue(right?.createdAt) - parseDateValue(left?.createdAt))
}

function defaultCallStatus(row) {
  if (isPaidCustomer(resolveLatestOrderStatus(row))) {
    return 'CONNECTED'
  }
  if (['ASSIGNED', 'FOLLOWING', 'CONVERTED'].includes(normalize(row.status))) {
    return 'CALLBACK'
  }
  return 'NOT_CALLED'
}

function defaultLeadStage(row) {
  const latestOrderStatus = normalize(resolveLatestOrderStatus(row))
  if (['USED', 'COMPLETED', 'FINISHED'].includes(latestOrderStatus)) {
    return 'DEAL'
  }
  if (['ARRIVED', 'SERVING'].includes(latestOrderStatus)) {
    return 'ARRIVED'
  }
  if (['PAID', 'PAID_DEPOSIT', 'APPOINTMENT'].includes(latestOrderStatus)) {
    return 'DEPOSIT_PAID'
  }
  if (String(row.wechat || '').trim()) {
    return 'WECHAT_ADDED'
  }
  if (['ASSIGNED', 'FOLLOWING', 'CONVERTED'].includes(normalize(row.status))) {
    return 'CALLBACK_PENDING'
  }
  return 'NEW'
}

function defaultLeadTags(row) {
  const values = []
  const latestOrderStatus = resolveLatestOrderStatus(row)
  if (row.productSourceType) {
    values.push(formatProductSourceType(row.productSourceType))
  }
  if (isPaidCustomer(latestOrderStatus)) {
    values.push('已付款')
  }
  if (String(row.wechat || '').trim()) {
    values.push('已加微信')
  }
  return [...new Set(values.filter(Boolean))]
}

function formatClueOrderType(value) {
  const normalized = normalize(value)
  if (normalized === 'DEPOSIT') {
    return '定金'
  }
  if (normalized === 'COUPON') {
    return '团购'
  }
  return '未关联'
}

function paymentStatusLabel(row) {
  if (normalize(row.leadStage) === 'DEPOSIT_PAID') {
    return '已付定金'
  }
  if (isPaidCustomer(row.latestOrderStatus)) {
    return '已付款'
  }
  return '未付款'
}

function buildLeadRecordItems(row, paidOrder) {
  const records = []
  if (paidOrder?.id || row.latestOrderId) {
    records.push({
      id: `order-${paidOrder?.id || row.latestOrderId}`,
      title: '订单同步',
      content: `${formatClueOrderType(paidOrder?.type || row.latestOrderType)} / ${formatOrderStage(paidOrder?.statusCategory || paidOrder?.status || row.latestOrderStatus)}`,
      createdAt: paidOrder?.createTime || row.createdAt
    })
  }
  if (paidOrder?.appointmentTime) {
    records.push({
      id: `appointment-${paidOrder.id}`,
      title: '预约排档',
      content: `预约时间：${formatDateTime(paidOrder.appointmentTime)}`,
      createdAt: paidOrder.appointmentTime
    })
  }
  for (const item of row.followRecords || []) {
    records.push({
      id: `follow-${item.id}`,
      title: '跟进记录',
      content: item.content,
      createdAt: item.createdAt
    })
  }
  return records.sort((left, right) => parseDateValue(right.createdAt) - parseDateValue(left.createdAt))
}

function findClueProfile(clueId) {
  return (consoleState.clueConsoleProfiles || []).find((item) => item.clueId === clueId) || null
}

function ensureClueProfile(row) {
  const existing = findClueProfile(row.id)
  if (existing) {
    return existing
  }
  return {
    id: nextSystemId(consoleState.clueConsoleProfiles || []),
    clueId: row.id,
    displayName: row.name || '',
    phone: row.phone || '',
    callStatus: defaultCallStatus(row),
    leadStage: defaultLeadStage(row),
    leadTags: defaultLeadTags(row),
    followRecords: [],
    intendedStoreName: row.storeName || storeOptions.value[0] || '静安门店',
    assignedAt: row.currentOwnerId ? row.createdAt || currentTimestampString() : '',
    updatedAt: currentTimestampString()
  }
}

function buildClueRow(row) {
  const paidOrder = resolvePaidOrder(row)
  const profile = ensureClueProfile(row)
  const latestOrderId = resolveLatestOrderId(row)
  const latestOrderStatus = resolveLatestOrderStatus(row)
  const latestOrderType = resolveLatestOrderType(row)
  const normalizedOrderStatus = normalize(latestOrderStatus)
  const paidOrderAppointmentTime = paidOrder?.appointmentTime || ''
  const canViewScheduling = isPaidCustomer(latestOrderStatus)
  const orderStageLabel = latestOrderId ? formatOrderStage(latestOrderStatus) : '暂无订单'
  let schedulingActionLabel = ''
  if (normalizedOrderStatus === 'APPOINTMENT') {
    schedulingActionLabel = '改档'
  } else if (['PAID', 'PAID_DEPOSIT'].includes(normalizedOrderStatus)) {
    schedulingActionLabel = '预约'
  } else {
    schedulingActionLabel = '查看'
  }
  return {
    ...row,
    latestOrderId,
    latestOrderStatus,
    latestOrderType,
    orderType: latestOrderType,
    editName: profile.displayName || row.name || '',
    editPhone: profile.phone || row.phone || '',
    callStatus: profile.callStatus,
    leadStage: profile.leadStage,
    leadTags: [...(profile.leadTags || [])],
    followRecords: sortFollowRecords(profile.followRecords || []),
    intendedStoreName: profile.intendedStoreName || row.storeName || '',
    assignedAt: row.currentOwnerId ? profile.assignedAt || row.createdAt : '',
    latestOrderStageLabel: orderStageLabel,
    paymentStatusLabel: paymentStatusLabel({ ...profile, latestOrderStatus }),
    leadRecordItems: buildLeadRecordItems({ ...profile, ...row, latestOrderId, latestOrderStatus, latestOrderType }, paidOrder),
    isPaidCustomer: canViewScheduling,
    paidOrderId: canViewScheduling ? latestOrderId : null,
    paidOrderAppointmentTime,
    schedulingActionLabel
  }
}

function displayName(row) {
  return row.editName || row.name || `线索#${row.id}`
}

function latestFollowRecord(row) {
  return row.followRecords?.[0]?.content || '暂无跟进记录'
}

function previewLeadTags(tags = []) {
  const values = (tags || []).filter(Boolean)
  if (!values.length) {
    return '未打标签'
  }
  return values.slice(0, 2).join(' / ')
}

function hasOwner(row) {
  return Boolean(row.currentOwnerId || row.currentOwnerName || row.assignedAt)
}

function resolveQueueStatus(row) {
  if (!hasOwner(row)) {
    return 'WAIT_ASSIGN'
  }
  if (['DEAL', 'INVALID'].includes(normalize(row.leadStage))) {
    return 'DONE'
  }
  return 'WAIT_FOLLOW_UP'
}

function matchesProductSource(row) {
  if (productSourceFilter.value === 'ALL') {
    return true
  }
  return normalize(row.productSourceType) === normalize(productSourceFilter.value)
}

function matchesPhone(row) {
  const keyword = String(filters.phone || '').trim()
  if (!keyword) {
    return true
  }
  return String(row.editPhone || row.phone || '').includes(keyword)
}

function matchesCreatedRange(row) {
  const [start, end] = filters.createdRange || []
  if (!start || !end) {
    return true
  }
  const createdAt = parseDateValue(row.createdAt)
  const startTime = parseDateValue(`${start} 00:00:00`)
  const endTime = parseDateValue(`${end} 23:59:59`)
  return createdAt >= startTime && createdAt <= endTime
}

function matchesQueueStatus(row) {
  if (filters.queueStatus === 'ALL') {
    return true
  }
  return resolveQueueStatus(row) === filters.queueStatus
}

function isPaidCustomer(status) {
  return ['PAID', 'PAID_DEPOSIT', 'APPOINTMENT', 'ARRIVED', 'SERVING', 'USED', 'COMPLETED', 'FINISHED'].includes(
    normalize(status)
  )
}

function canShowMoreActions(row) {
  return canAssignClue.value || (canRecycleClue.value && row.currentOwnerId)
}

function saveCellEdit(row, field, currentValue, patchKey = field) {
  handleInlineUpdate(row, {
    [patchKey]: getCellDraft(row.id, field, currentValue)
  })
}

function applyFilters() {
  pagination.reset()
}

function resetFilters() {
  filters.phone = ''
  filters.createdRange = []
  filters.queueStatus = 'ALL'
  pagination.reset()
}

function handleInlineUpdate(row, patch, options = {}) {
  const nextPatch = { ...patch }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'displayName')) {
    nextPatch.displayName = String(nextPatch.displayName || '').trim()
  }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'phone')) {
    nextPatch.phone = String(nextPatch.phone || '').trim()
  }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'leadTags')) {
    nextPatch.leadTags = [...new Set((nextPatch.leadTags || []).map((item) => String(item || '').trim()).filter(Boolean))]
  }
  if (Object.prototype.hasOwnProperty.call(nextPatch, 'followRecords')) {
    nextPatch.followRecords = sortFollowRecords(
      (nextPatch.followRecords || [])
        .map((item) => ({
          id: item?.id,
          content: String(item?.content || '').trim(),
          createdAt: item?.createdAt || currentTimestampString()
        }))
        .filter((item) => item.content)
    )
  }

  const profile = ensureClueProfile(row)
  const changed = Object.entries(nextPatch).some(([key, value]) => JSON.stringify(profile[key]) !== JSON.stringify(value))
  if (!changed) {
    return
  }

  const nextProfiles = [...(consoleState.clueConsoleProfiles || [])]
  const index = nextProfiles.findIndex((item) => item.clueId === row.id)
  const nextProfile = {
    ...profile,
    ...nextPatch,
    updatedAt: currentTimestampString()
  }
  if (index >= 0) {
    nextProfiles[index] = nextProfile
  } else {
    nextProfiles.push(nextProfile)
  }
  replaceConsoleState({
    ...consoleState,
    clueConsoleProfiles: nextProfiles
  })

  if (!options.silent) {
    ElMessage.success('线索信息已更新')
  }
}

function addFollowRecord(row) {
  const content = String(followRecordDrafts[row.id] || '').trim()
  if (!content) {
    ElMessage.warning('请先填写跟进内容')
    return
  }
  const profile = ensureClueProfile(row)
  const nextRecords = [
    {
      id: nextSystemId(profile.followRecords || []),
      content,
      createdAt: currentTimestampString()
    },
    ...(profile.followRecords || [])
  ]
  handleInlineUpdate(row, { followRecords: nextRecords }, { silent: true })
  followRecordDrafts[row.id] = ''
  ElMessage.success('跟进记录已添加')
}

function removeFollowRecord(row, recordId) {
  const profile = ensureClueProfile(row)
  const nextRecords = (profile.followRecords || []).filter((item) => item.id !== recordId)
  handleInlineUpdate(row, { followRecords: nextRecords }, { silent: true })
  ElMessage.success('跟进记录已删除')
}

async function loadClues() {
  loading.value = true
  try {
    const [clueResult, orderResult] = await Promise.allSettled([
      fetchClues(),
      fetchOrders({
        status: 'paid'
      })
    ])
    clues.value = clueResult.status === 'fulfilled' ? clueResult.value : []
    paidOrders.value = orderResult.status === 'fulfilled' ? orderResult.value : []
    pagination.reset()
  } catch {
    clues.value = []
    paidOrders.value = []
  } finally {
    loading.value = false
  }
}

async function loadDutyStaff() {
  if (!canAssignClue.value) {
    dutyStaff.value = []
    return
  }
  try {
    dutyStaff.value = await fetchDutyCustomerServices()
  } catch {
    dutyStaff.value = []
  }
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
  handleInlineUpdate(
    row,
    {
      assignedAt: currentTimestampString(),
      callStatus: row.callStatus === 'NOT_CALLED' ? 'CALLBACK' : row.callStatus
    },
    { silent: true }
  )
  assignDialogVisible.value = false
  ElMessage.success('线索已分配')
  await loadClues()
}

async function handleRecycle(row) {
  await recycleClue(row.id)
  handleInlineUpdate(
    row,
    {
      assignedAt: '',
      callStatus: 'NOT_CALLED',
      leadStage: 'NEW'
    },
    { silent: true }
  )
  ElMessage.success('线索已回收到公海')
  await loadClues()
}

function openDetailDrawer(row) {
  detailRowId.value = row.id
  detailDrawerVisible.value = true
}

function goToScheduling(row) {
  if (!row.paidOrderId) {
    ElMessage.warning('当前线索还没有可排档的订单')
    return
  }
  const query = {
    orderId: row.paidOrderId,
    clueId: row.id
  }
  if (row.intendedStoreName) {
    query.storeName = row.intendedStoreName
  }
  if (row.paidOrderAppointmentTime) {
    query.day = String(row.paidOrderAppointmentTime).slice(0, 10)
  }
  router.push({
    path: '/clues/scheduling',
    query
  })
}

function getTableScrollWrap() {
  return clueTableRef.value?.$el?.querySelector('.el-scrollbar__wrap') || null
}

function syncFloatingScrollFromTable() {
  if (scrollSyncing || !floatingTableScrollbarRef.value || !tableScrollWrap) {
    return
  }
  scrollSyncing = true
  floatingTableScrollbarRef.value.scrollLeft = tableScrollWrap.scrollLeft
  window.requestAnimationFrame(() => {
    scrollSyncing = false
  })
}

function handleFloatingTableScroll() {
  if (scrollSyncing) {
    return
  }
  const wrap = getTableScrollWrap()
  if (!wrap || !floatingTableScrollbarRef.value) {
    return
  }
  scrollSyncing = true
  wrap.scrollLeft = floatingTableScrollbarRef.value.scrollLeft
  window.requestAnimationFrame(() => {
    scrollSyncing = false
  })
}

function bindTableScrollWrap() {
  const wrap = getTableScrollWrap()
  if (wrap === tableScrollWrap) {
    return
  }
  if (tableScrollWrap) {
    tableScrollWrap.removeEventListener('scroll', syncFloatingScrollFromTable)
  }
  tableScrollWrap = wrap
  if (tableScrollWrap) {
    tableScrollWrap.addEventListener('scroll', syncFloatingScrollFromTable, { passive: true })
  }
}

async function updateFloatingTableScrollbar() {
  await nextTick()
  bindTableScrollWrap()
  if (!tableScrollWrap) {
    hasFloatingTableScrollbar.value = false
    floatingTableScrollWidth.value = 0
    return
  }
  floatingTableScrollWidth.value = tableScrollWrap.scrollWidth
  hasFloatingTableScrollbar.value = tableScrollWrap.scrollWidth > tableScrollWrap.clientWidth + 1
  syncFloatingScrollFromTable()
}

onMounted(async () => {
  await Promise.all([loadClues(), loadDutyStaff()])
  await updateFloatingTableScrollbar()
  tableResizeObserver = new ResizeObserver(updateFloatingTableScrollbar)
  if (clueTableRef.value?.$el) {
    tableResizeObserver.observe(clueTableRef.value.$el)
  }
  window.addEventListener('resize', updateFloatingTableScrollbar)
  refreshTimer = window.setInterval(loadClues, 15000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
  window.removeEventListener('resize', updateFloatingTableScrollbar)
  if (tableScrollWrap) {
    tableScrollWrap.removeEventListener('scroll', syncFloatingScrollFromTable)
  }
  tableResizeObserver?.disconnect()
})

watch(
  () => [pagination.rows.length, pagination.currentPage, pagination.pageSize],
  updateFloatingTableScrollbar
)
</script>
