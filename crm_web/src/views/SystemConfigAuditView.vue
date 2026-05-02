<template>
  <div class="stack-page config-audit-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>审计记录</span>
        <strong>{{ logs.length }}</strong>
      </article>
      <article class="summary-pill">
        <span>高风险变更</span>
        <strong>{{ highRiskCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>当前筛选</span>
        <strong>{{ activeFilterLabel }}</strong>
      </article>
      <article class="summary-pill">
        <span>预览状态</span>
        <strong>{{ previewStatus }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>配置审计</h3>
        </div>
        <div class="action-group">
          <el-button :loading="loadingLogs" @click="loadLogs">刷新记录</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="变更预览" name="preview">
          <el-alert
            class="config-alert"
            type="info"
            show-icon
            :closable="false"
            title="配置保存前先做预览，确认风险等级、影响模块和变更前后值；未保存的预览不会影响线上业务。"
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
                  placeholder="填写要发布的配置值"
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
                <el-button :disabled="!previewResult" :loading="saving" @click="handleSave">保存配置</el-button>
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

          <el-table v-loading="loadingLogs" :data="logs" stripe height="520" empty-text="暂无配置变更记录">
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
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openLog(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-drawer v-model="logDrawerVisible" title="配置变更详情" size="520px">
      <dl v-if="selectedLog" class="diff-list drawer-diff">
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
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchSystemConfigChangeLogs, previewSystemConfig, saveSystemConfig } from '../api/systemConfig'

const activeTab = ref('preview')
const loadingLogs = ref(false)
const previewing = ref(false)
const saving = ref(false)
const logs = ref([])
const previewResult = ref(null)
const selectedLog = ref(null)
const logDrawerVisible = ref(false)

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

const highRiskCount = computed(() => logs.value.filter((item) => item.riskLevel === 'HIGH').length)
const activeFilterLabel = computed(() => filters.configKey || filters.prefix || '全部')
const previewStatus = computed(() => {
  if (!previewResult.value) {
    return '待预览'
  }
  return previewResult.value.changed ? '有变更' : '无变化'
})

onMounted(loadLogs)

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

async function handleSave() {
  if (!previewResult.value) {
    ElMessage.warning('请先生成配置预览')
    return
  }
  await ElMessageBox.confirm(
    `将保存配置 ${previewResult.value.configKey}，风险等级：${riskLabel(previewResult.value.riskLevel)}。`,
    '确认保存配置',
    {
      type: previewResult.value.riskLevel === 'HIGH' ? 'warning' : 'info',
      confirmButtonText: '确认保存',
      cancelButtonText: '取消'
    }
  )
  saving.value = true
  try {
    await saveSystemConfig(buildPreviewPayload())
    ElMessage.success('配置已保存，审计记录已生成')
    previewResult.value = null
    filters.configKey = String(previewForm.configKey || '').trim()
    activeTab.value = 'logs'
    await loadLogs()
  } finally {
    saving.value = false
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
    summary: String(previewForm.summary || '').trim() || '通过配置审计页保存'
  }
}

function openLog(row) {
  selectedLog.value = row
  logDrawerVisible.value = true
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
  border-radius: 12px;
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

.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 10px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
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
