<template>
  <div class="stack-page config-audit-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>待发布草稿</span>
        <strong>{{ draftCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>高风险待发布</span>
        <strong>{{ highRiskDraftCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>发布记录</span>
        <strong>{{ logs.length }}</strong>
      </article>
      <article class="summary-pill">
        <span>最近发布时间</span>
        <strong>{{ latestPublishTime }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>配置发布中心</h3>
        </div>
        <div class="action-group">
          <el-button :loading="loadingDrafts || loadingLogs" @click="refreshAll">刷新</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="草稿预览" name="preview">
          <el-alert
            class="config-alert"
            type="info"
            show-icon
            :closable="false"
            title="预览只检查影响范围，不会改变线上业务。保存为草稿后仍需在待发布中手动发布生效。"
          />

          <div class="preview-layout">
            <div class="config-form">
              <label>
                <span>配置 Key</span>
                <el-input v-model="previewForm.configKey" placeholder="例如 clue.dedup.window_days" />
              </label>
              <label>
                <span>值类型</span>
                <el-select v-model="previewForm.valueType">
                  <el-option label="字符串" value="STRING" />
                  <el-option label="布尔值" value="BOOLEAN" />
                  <el-option label="数字" value="NUMBER" />
                  <el-option label="URL" value="URL" />
                  <el-option label="JSON" value="JSON" />
                </el-select>
              </label>
              <label>
                <span>作用域类型</span>
                <el-input v-model="previewForm.scopeType" />
              </label>
              <label>
                <span>作用域 ID</span>
                <el-input v-model="previewForm.scopeId" />
              </label>
              <label class="full-span">
                <span>配置值</span>
                <el-input
                  v-model="previewForm.configValue"
                  :autosize="{ minRows: 4, maxRows: 10 }"
                  type="textarea"
                  placeholder="填写要进入草稿的配置值"
                />
              </label>
              <label class="switch-row">
                <span>启用</span>
                <el-switch v-model="previewEnabled" />
              </label>
              <label class="full-span">
                <span>变更摘要</span>
                <el-input v-model="previewForm.summary" placeholder="说明本次配置调整原因" />
              </label>
              <div class="preview-actions full-span">
                <el-button type="primary" :loading="previewing" @click="handlePreview">生成预览</el-button>
                <el-button :disabled="!previewResult" :loading="savingDraft" @click="handleSaveDraft">
                  保存为草稿
                </el-button>
              </div>
            </div>

            <div class="preview-result">
              <template v-if="previewResult">
                <div class="preview-result__header">
                  <el-tag :type="riskTagType(previewResult.riskLevel)" effect="light">
                    {{ riskLabel(previewResult.riskLevel) }}
                  </el-tag>
                  <strong>{{ changeTypeLabel(previewResult.changeType) }}</strong>
                </div>
                <dl class="diff-list">
                  <div>
                    <dt>影响模块</dt>
                    <dd>
                      <el-tag v-for="item in previewResult.impactModules || []" :key="item" effect="plain">
                        {{ item }}
                      </el-tag>
                    </dd>
                  </div>
                  <div>
                    <dt>变更前</dt>
                    <dd class="value-block">{{ previewResult.beforeValue || '--' }}</dd>
                  </div>
                  <div>
                    <dt>变更后</dt>
                    <dd class="value-block">{{ previewResult.afterValue || '--' }}</dd>
                  </div>
                </dl>
                <div v-if="previewResult.warnings?.length" class="warning-list">
                  <el-alert
                    v-for="item in previewResult.warnings"
                    :key="item"
                    type="warning"
                    show-icon
                    :closable="false"
                    :title="item"
                  />
                </div>
              </template>
              <el-empty v-else description="填写配置后生成预览" />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="待发布" name="drafts">
          <el-table
            v-loading="loadingDrafts"
            :data="drafts"
            stripe
            height="520"
            empty-text="暂无待发布草稿，线上业务正在使用当前生效配置"
          >
            <el-table-column prop="draftNo" label="草稿号" min-width="150" />
            <el-table-column label="配置 Key" min-width="230">
              <template #default="{ row }">{{ firstDraftItem(row).configKey || '--' }}</template>
            </el-table-column>
            <el-table-column label="风险" width="100">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="110">
              <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column label="影响模块" min-width="150">
              <template #default="{ row }">
                <div class="tag-line">
                  <el-tag v-for="item in row.impactModules || []" :key="item" effect="plain">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            <el-table-column label="创建人" width="150">
              <template #default="{ row }">{{ row.createdByRoleCode || '--' }} / {{ row.createdByUserId || '--' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDraft(row)">查看</el-button>
                <el-button link type="success" :loading="publishingDraftNo === row.draftNo" @click="handlePublishDraft(row)">
                  发布生效
                </el-button>
                <el-button link type="danger" :loading="discardingDraftNo === row.draftNo" @click="handleDiscardDraft(row)">
                  作废
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发布记录" name="logs">
          <div class="log-toolbar">
            <el-input v-model="filters.prefix" clearable placeholder="按前缀筛选，例如 clue." />
            <el-input v-model="filters.configKey" clearable placeholder="按完整 Key 筛选" />
            <el-select v-model="filters.limit">
              <el-option label="最近 50 条" :value="50" />
              <el-option label="最近 100 条" :value="100" />
              <el-option label="最近 200 条" :value="200" />
            </el-select>
            <el-button :loading="loadingLogs" @click="loadLogs">查询</el-button>
          </div>

          <el-table v-loading="loadingLogs" :data="logs" stripe height="520" empty-text="暂无配置发布记录">
            <el-table-column prop="configKey" label="配置 Key" min-width="230" />
            <el-table-column label="风险" width="100">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变更类型" width="110">
              <template #default="{ row }">{{ changeTypeLabel(row.changeType) }}</template>
            </el-table-column>
            <el-table-column label="影响模块" min-width="150">
              <template #default="{ row }">
                <div class="tag-line">
                  <el-tag v-for="item in row.impactModules || []" :key="item" effect="plain">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作人" width="150">
              <template #default="{ row }">{{ row.actorRoleCode || '--' }} / {{ row.actorUserId || '--' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openLog(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="draftDrawerVisible" title="草稿详情" size="560px">
      <template v-if="selectedDraft">
        <div class="drawer-actions">
          <el-button type="success" :loading="publishingDraftNo === selectedDraft.draftNo" @click="handlePublishDraft(selectedDraft)">
            发布生效
          </el-button>
          <el-button type="danger" plain :loading="discardingDraftNo === selectedDraft.draftNo" @click="handleDiscardDraft(selectedDraft)">
            作废草稿
          </el-button>
        </div>
        <dl class="diff-list drawer-diff">
          <div>
            <dt>草稿号</dt>
            <dd>{{ selectedDraft.draftNo }}</dd>
          </div>
          <div>
            <dt>来源</dt>
            <dd>{{ sourceTypeLabel(selectedDraft.sourceType) }}</dd>
          </div>
          <div>
            <dt>风险</dt>
            <dd>
              <el-tag :type="riskTagType(selectedDraft.riskLevel)" effect="light">
                {{ riskLabel(selectedDraft.riskLevel) }}
              </el-tag>
            </dd>
          </div>
          <div v-for="item in selectedDraft.items || []" :key="item.id || item.configKey">
            <dt>{{ item.configKey }}</dt>
            <dd class="value-block">
              <strong>变更前</strong>
              <pre>{{ item.beforeValue || '--' }}</pre>
              <strong>变更后</strong>
              <pre>{{ item.afterValue || '--' }}</pre>
            </dd>
          </div>
        </dl>
      </template>
    </el-drawer>

    <el-drawer v-model="logDrawerVisible" title="配置发布详情" size="560px">
      <template v-if="selectedLog">
        <div class="drawer-actions">
          <el-button :loading="rollbackPreviewing" @click="handleRollbackPreview">生成回滚预览</el-button>
          <el-button type="primary" :loading="rollbackDrafting" @click="handleCreateRollbackDraft">
            生成回滚草稿
          </el-button>
        </div>
        <dl class="diff-list drawer-diff">
          <div>
            <dt>配置 Key</dt>
            <dd>{{ selectedLog.configKey }}</dd>
          </div>
          <div>
            <dt>作用域</dt>
            <dd>{{ selectedLog.scopeType }} / {{ selectedLog.scopeId }}</dd>
          </div>
          <div>
            <dt>变更前</dt>
            <dd class="value-block">{{ selectedLog.beforeValue || '--' }}</dd>
          </div>
          <div>
            <dt>变更后</dt>
            <dd class="value-block">{{ selectedLog.afterValue || '--' }}</dd>
          </div>
          <div>
            <dt>摘要</dt>
            <dd>{{ selectedLog.summary || '--' }}</dd>
          </div>
        </dl>

        <div v-if="rollbackPreviewResult" class="rollback-preview">
          <h4>回滚预览</h4>
          <dl class="diff-list">
            <div>
              <dt>回滚后值</dt>
              <dd class="value-block">{{ rollbackPreviewResult.afterValue || '--' }}</dd>
            </div>
            <div>
              <dt>影响模块</dt>
              <dd>
                <el-tag v-for="item in rollbackPreviewResult.impactModules || []" :key="item" effect="plain">
                  {{ item }}
                </el-tag>
              </dd>
            </div>
          </dl>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRollbackSystemConfigDraft,
  createSystemConfigDraft,
  discardSystemConfigDraft,
  fetchSystemConfigChangeLogs,
  fetchSystemConfigDrafts,
  previewSystemConfig,
  publishSystemConfigDraft,
  rollbackPreviewSystemConfig
} from '../api/systemConfig'

const activeTab = ref('preview')
const loadingLogs = ref(false)
const loadingDrafts = ref(false)
const previewing = ref(false)
const savingDraft = ref(false)
const rollbackPreviewing = ref(false)
const rollbackDrafting = ref(false)
const publishingDraftNo = ref('')
const discardingDraftNo = ref('')

const logs = ref([])
const drafts = ref([])
const previewResult = ref(null)
const selectedLog = ref(null)
const selectedDraft = ref(null)
const rollbackPreviewResult = ref(null)
const logDrawerVisible = ref(false)
const draftDrawerVisible = ref(false)

const filters = reactive({
  prefix: '',
  configKey: '',
  limit: 50
})

const previewForm = reactive({
  configKey: 'clue.dedup.window_days',
  configValue: '90',
  valueType: 'NUMBER',
  scopeType: 'GLOBAL',
  scopeId: 'GLOBAL',
  enabled: 1,
  summary: ''
})

const previewEnabled = computed({
  get: () => Number(previewForm.enabled) !== 0,
  set: (value) => {
    previewForm.enabled = value ? 1 : 0
  }
})

const draftCount = computed(() => drafts.value.length)
const highRiskDraftCount = computed(() => drafts.value.filter((item) => item.riskLevel === 'HIGH').length)
const latestPublishTime = computed(() => (logs.value[0] ? formatDate(logs.value[0].createTime) : '--'))

onMounted(refreshAll)

async function refreshAll() {
  await Promise.all([loadDrafts(), loadLogs()])
}

async function handleTabChange(name) {
  if (name === 'drafts') {
    await loadDrafts()
  } else if (name === 'logs') {
    await loadLogs()
  }
}

async function loadDrafts() {
  loadingDrafts.value = true
  try {
    drafts.value = await fetchSystemConfigDrafts({ status: 'DRAFT', limit: 100 })
  } finally {
    loadingDrafts.value = false
  }
}

async function loadLogs() {
  loadingLogs.value = true
  try {
    logs.value = await fetchSystemConfigChangeLogs({
      prefix: String(filters.prefix || '').trim(),
      configKey: String(filters.configKey || '').trim(),
      limit: filters.limit
    })
  } finally {
    loadingLogs.value = false
  }
}

async function handlePreview() {
  previewing.value = true
  try {
    previewResult.value = await previewSystemConfig(buildPreviewPayload())
    ElMessage.success('配置预览已生成')
  } finally {
    previewing.value = false
  }
}

async function handleSaveDraft() {
  if (!previewResult.value) {
    ElMessage.warning('请先生成配置预览')
    return
  }
  if (previewResult.value.changed === false) {
    ElMessage.warning('当前值与线上配置一致，无需保存草稿')
    return
  }
  await ElMessageBox.confirm(
    `将保存草稿 ${previewResult.value.configKey}，尚不会影响线上业务。`,
    '保存为草稿',
    {
      type: previewResult.value.riskLevel === 'HIGH' ? 'warning' : 'info',
      confirmButtonText: '保存为草稿',
      cancelButtonText: '取消'
    }
  )
  savingDraft.value = true
  try {
    await createSystemConfigDraft(buildPreviewPayload())
    ElMessage.success('草稿已保存，尚未影响线上业务')
    previewResult.value = null
    activeTab.value = 'drafts'
    await loadDrafts()
  } finally {
    savingDraft.value = false
  }
}

async function handlePublishDraft(row) {
  if (!row?.draftNo) {
    return
  }
  if (row.riskLevel === 'HIGH') {
    await ElMessageBox.prompt(
      `发布后将影响 ${moduleText(row.impactModules)}。请输入 PUBLISH 确认。`,
      '确认发布高风险配置',
      {
        type: 'warning',
        inputPattern: /^PUBLISH$/,
        inputErrorMessage: '请输入 PUBLISH',
        confirmButtonText: '发布生效',
        cancelButtonText: '取消'
      }
    )
  } else {
    await ElMessageBox.confirm(
      `确认发布草稿 ${row.draftNo}？发布成功后新业务将按当前配置执行。`,
      '确认发布配置',
      {
        type: 'warning',
        confirmButtonText: '发布生效',
        cancelButtonText: '取消'
      }
    )
  }
  publishingDraftNo.value = row.draftNo
  try {
    await publishSystemConfigDraft(row.draftNo)
    ElMessage.success('配置已发布，新业务将按当前配置执行')
    draftDrawerVisible.value = false
    await Promise.all([loadDrafts(), loadLogs()])
  } finally {
    publishingDraftNo.value = ''
  }
}

async function handleDiscardDraft(row) {
  if (!row?.draftNo) {
    return
  }
  await ElMessageBox.confirm(
    `作废草稿 ${row.draftNo} 后不可发布，线上配置不受影响。`,
    '作废草稿',
    {
      type: 'warning',
      confirmButtonText: '作废草稿',
      cancelButtonText: '取消'
    }
  )
  discardingDraftNo.value = row.draftNo
  try {
    await discardSystemConfigDraft(row.draftNo)
    ElMessage.success('草稿已作废，线上配置不受影响')
    draftDrawerVisible.value = false
    await loadDrafts()
  } finally {
    discardingDraftNo.value = ''
  }
}

async function handleRollbackPreview() {
  if (!selectedLog.value?.id) {
    return
  }
  rollbackPreviewing.value = true
  try {
    rollbackPreviewResult.value = await rollbackPreviewSystemConfig(selectedLog.value.id)
    ElMessage.success('回滚预览已生成，线上业务未变化')
  } finally {
    rollbackPreviewing.value = false
  }
}

async function handleCreateRollbackDraft() {
  if (!selectedLog.value?.id) {
    return
  }
  await ElMessageBox.confirm(
    '回滚只会生成草稿，不会立即改变线上配置。请在待发布中确认发布。',
    '生成回滚草稿',
    {
      type: 'warning',
      confirmButtonText: '生成草稿',
      cancelButtonText: '取消'
    }
  )
  rollbackDrafting.value = true
  try {
    await createRollbackSystemConfigDraft(selectedLog.value.id)
    ElMessage.success('回滚草稿已生成，请在待发布中确认发布')
    logDrawerVisible.value = false
    activeTab.value = 'drafts'
    await loadDrafts()
  } finally {
    rollbackDrafting.value = false
  }
}

function buildPreviewPayload() {
  return {
    configKey: String(previewForm.configKey || '').trim(),
    configValue: previewForm.configValue,
    valueType: previewForm.valueType,
    scopeType: String(previewForm.scopeType || 'GLOBAL').trim(),
    scopeId: String(previewForm.scopeId || 'GLOBAL').trim(),
    enabled: previewForm.enabled,
    summary: String(previewForm.summary || '').trim() || '通过配置发布中心创建草稿'
  }
}

function openLog(row) {
  selectedLog.value = row
  rollbackPreviewResult.value = null
  logDrawerVisible.value = true
}

function openDraft(row) {
  selectedDraft.value = row
  draftDrawerVisible.value = true
}

function firstDraftItem(row) {
  return row?.items?.[0] || {}
}

function moduleText(items = []) {
  return items.length ? items.join('、') : '相关模块'
}

function sourceTypeLabel(value) {
  const map = {
    MANUAL: '手工草稿',
    ROLLBACK: '回滚草稿'
  }
  return map[value] || value || '--'
}

function riskLabel(value) {
  const map = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低'
  }
  return map[value] || value || '--'
}

function riskTagType(value) {
  const map = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return map[value] || 'info'
}

function changeTypeLabel(value) {
  const map = {
    CREATE: '新增',
    UPDATE: '更新',
    NO_CHANGE: '无变化'
  }
  return map[value] || value || '--'
}

function formatDate(value) {
  if (!value) {
    return '--'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.config-alert {
  margin-bottom: 16px;
  border-radius: 8px;
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.92fr);
  gap: 18px;
  align-items: start;
}

.config-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.config-form label,
.log-toolbar {
  min-width: 0;
}

.config-form label span {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 13px;
}

.full-span {
  grid-column: 1 / -1;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preview-actions,
.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.drawer-actions {
  margin-bottom: 16px;
}

.preview-result {
  min-height: 340px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.preview-result__header,
.tag-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.preview-result__header {
  margin-bottom: 14px;
}

.diff-list {
  margin: 0;
  display: grid;
  gap: 12px;
}

.diff-list div {
  min-width: 0;
}

.diff-list dt {
  margin-bottom: 6px;
  color: #64748b;
  font-size: 13px;
}

.diff-list dd {
  margin: 0;
  color: #0f172a;
}

.value-block {
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 10px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.value-block pre {
  margin: 6px 0 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: inherit;
}

.warning-list {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.log-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.2fr) 150px auto;
  gap: 10px;
  margin-bottom: 14px;
}

.drawer-diff {
  gap: 16px;
}

.rollback-preview {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.rollback-preview h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #0f172a;
}

@media (max-width: 980px) {
  .preview-layout,
  .log-toolbar {
    grid-template-columns: 1fr;
  }

  .config-form {
    grid-template-columns: 1fr;
  }
}
</style>
