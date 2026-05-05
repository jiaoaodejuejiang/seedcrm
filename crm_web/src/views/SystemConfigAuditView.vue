<template>
  <div class="stack-page config-audit-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>受控能力</span>
        <strong>{{ runtimeOverview.capabilityCount ?? capabilities.length }}</strong>
      </article>
      <article class="summary-pill">
        <span>待发布草稿</span>
        <strong>{{ runtimeOverview.draftCount ?? draftCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>高风险待发布</span>
        <strong>{{ runtimeOverview.highRiskDraftCount ?? highRiskDraftCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>发布成功</span>
        <strong>{{ runtimeOverview.publishSuccessCount ?? publishSuccessCount }}</strong>
      </article>
      <article class="summary-pill">
        <span>发布失败</span>
        <strong>{{ runtimeOverview.publishFailedCount ?? publishFailedCount }}</strong>
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
          <el-button :loading="refreshing" @click="refreshAll">刷新</el-button>
        </div>
      </div>

      <el-alert
        class="config-alert"
        type="warning"
        show-icon
        :closable="false"
        title="硬边界：本页只发布受控配置 Key，不直接创建或修改 Customer、Order、PlanOrder、Ledger；核心对象仍通过既有业务主链路流转。"
      />

      <div class="publish-flow">
        <el-steps :active="currentStepIndex" finish-status="success" process-status="process" simple>
          <el-step title="预览" />
          <el-step title="保存草稿" />
          <el-step title="校验" />
          <el-step title="发布预检查" />
          <el-step title="发布" />
          <el-step title="运行态/回滚" />
        </el-steps>
        <div class="page-status">
          <strong>{{ pageStatusTitle }}</strong>
          <span>{{ pageStatusText }}</span>
        </div>
      </div>

      <div class="capability-strip" v-loading="loadingCapabilities || loadingRuntime">
        <div class="capability-strip__main">
          <span class="capability-strip__label">能力清单</span>
          <el-tag
            v-for="item in visibleCapabilities"
            :key="item.capabilityCode"
            :type="riskTagType(item.riskLevel)"
            effect="plain"
          >
            {{ capabilityLabel(item) }} · {{ moduleLabel(item.ownerModule) }}
          </el-tag>
          <span v-if="capabilities.length > visibleCapabilities.length" class="muted">
            +{{ capabilities.length - visibleCapabilities.length }}
          </span>
        </div>
        <div class="capability-strip__meta">
          <span>待处理运行态事件 {{ runtimePendingCount }}</span>
          <span>失败/终止 {{ runtimeFailedCount }}</span>
          <span>最近刷新 {{ runtimeOverview.latestRuntimeHandledAt ? formatDate(runtimeOverview.latestRuntimeHandledAt) : '--' }}</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="草稿预览" name="preview">
          <el-alert
            class="config-alert"
            type="info"
            show-icon
            :closable="false"
            title="预览只检查影响范围，不会改变线上业务；保存为草稿后仍需在待发布中手动校验、发布预检查并发布生效。"
          />

          <div class="capability-catalog" v-loading="loadingCapabilities">
            <button
              v-for="item in capabilityCards"
              :key="item.capabilityCode"
              class="capability-card"
              :class="{ 'is-active': activeCapabilityCode === item.capabilityCode }"
              type="button"
              @click="applyCapability(item)"
            >
              <span class="capability-card__top">
                <strong>{{ capabilityLabel(item) }}</strong>
                <el-tag :type="riskTagType(item.riskLevel)" effect="light" size="small">
                  {{ riskLabel(item.riskLevel) }}
                </el-tag>
              </span>
              <span>{{ capabilityDescription(item) }}</span>
              <small>{{ moduleLabel(item.ownerModule) }} · {{ valueTypeLabel(item.valueType) }}</small>
            </button>
          </div>

          <div class="service-form-presets">
            <div class="service-form-presets__head">
              <strong>服务确认单治理</strong>
              <span>打印确认、纸质签名、设计器适配走配置草稿发布，不直接改订单或服务单状态。</span>
            </div>
            <div class="service-form-preset-grid">
              <button
                v-for="item in serviceFormPresets"
                :key="item.configKey"
                type="button"
                class="service-form-preset"
                :class="{ 'is-active': previewForm.configKey === item.configKey }"
                @click="applyServiceFormPreset(item)"
              >
                <strong>{{ item.label }}</strong>
                <span>{{ item.description }}</span>
                <small>{{ item.configKey }}</small>
              </button>
            </div>
          </div>

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
            <el-table-column label="校验" width="110">
              <template #default="{ row }">
                <el-tag :type="validationTagType(validationMap[row.draftNo]?.valid)">
                  {{ validationLabel(validationMap[row.draftNo]?.valid) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发布预检查" width="120">
              <template #default="{ row }">
                <el-tag :type="dryRunTagType(draftDryRunState(row))">
                  {{ dryRunLabel(draftDryRunState(row)) }}
                </el-tag>
              </template>
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
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDraft(row)">查看</el-button>
                <el-button link :loading="validatingDraftNo === row.draftNo" @click="handleValidateDraft(row)">
                  校验
                </el-button>
                <el-button link :loading="dryRunningDraftNo === row.draftNo" @click="handleDryRunDraft(row)">
                  预检查
                </el-button>
                <el-button link type="success" :loading="publishingDraftNo === row.draftNo" @click="handlePublishDraft(row)">
                  发布
                </el-button>
                <el-button link type="danger" :loading="discardingDraftNo === row.draftNo" @click="handleDiscardDraft(row)">
                  作废
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发布批次" name="publishRecords">
          <el-table
            v-loading="loadingPublishRecords"
            :data="publishRecords"
            stripe
            height="520"
            empty-text="暂无配置发布批次"
          >
            <el-table-column prop="publishNo" label="发布号" min-width="150" />
            <el-table-column prop="draftNo" label="草稿号" min-width="150" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="publishStatusType(row.status)" effect="light">{{ publishStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="风险" width="100">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="影响模块" min-width="160">
              <template #default="{ row }">
                <div class="tag-line">
                  <el-tag v-for="item in row.impactModules || []" :key="item" effect="plain">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="failureReason" label="失败原因" min-width="180" show-overflow-tooltip />
            <el-table-column label="发布人" width="150">
              <template #default="{ row }">{{ row.publishedByRoleCode || '--' }} / {{ row.publishedByUserId || '--' }}</template>
            </el-table-column>
            <el-table-column label="发布时间" width="180">
              <template #default="{ row }">{{ formatDate(row.publishedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPublishRecord(row)">详情</el-button>
                <el-button
                  link
                  type="success"
                  :disabled="row.status !== 'SUCCESS'"
                  :loading="refreshingPublishNo === row.publishNo || processingRuntimePublishNo === row.publishNo"
                  @click="handleRefreshRuntime(row)"
                >
                  刷新并处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="变更日志" name="logs">
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

          <el-table v-loading="loadingLogs" :data="logs" stripe height="520" empty-text="暂无配置变更日志">
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

    <el-drawer v-model="draftDrawerVisible" title="草稿详情" size="620px">
      <template v-if="selectedDraft">
        <div class="drawer-actions">
          <el-button :loading="validatingDraftNo === selectedDraft.draftNo" @click="handleValidateDraft(selectedDraft)">
            校验
          </el-button>
          <el-button :loading="dryRunningDraftNo === selectedDraft.draftNo" @click="handleDryRunDraft(selectedDraft)">
            发布预检查
          </el-button>
          <el-button type="success" :loading="publishingDraftNo === selectedDraft.draftNo" @click="handlePublishDraft(selectedDraft)">
            发布生效
          </el-button>
          <el-button type="danger" plain :loading="discardingDraftNo === selectedDraft.draftNo" @click="handleDiscardDraft(selectedDraft)">
            作废草稿
          </el-button>
        </div>

        <result-panel
          v-if="validationMap[selectedDraft.draftNo]"
          title="校验结果"
          :items="validationMap[selectedDraft.draftNo].items || []"
        />
        <div v-if="dryRunMap[selectedDraft.draftNo]" class="runtime-box">
          <h4>发布预检查结果</h4>
          <p>{{ dryRunMap[selectedDraft.draftNo].summary }}</p>
          <el-tag
            v-for="item in dryRunMap[selectedDraft.draftNo].runtimeEvents || []"
            :key="item"
            effect="plain"
          >
            {{ item }}
          </el-tag>
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

    <el-drawer v-model="publishDrawerVisible" title="发布批次详情" size="640px">
      <template v-if="selectedPublishRecord">
        <div class="drawer-actions">
          <el-button
            :disabled="selectedPublishRecord.status !== 'SUCCESS'"
            :loading="processingRuntimePublishNo === selectedPublishRecord.publishNo"
            @click="handleProcessRuntimeEvents(selectedPublishRecord)"
          >
            处理待刷新事件
          </el-button>
          <el-button
            type="success"
            :disabled="selectedPublishRecord.status !== 'SUCCESS'"
            :loading="refreshingPublishNo === selectedPublishRecord.publishNo || processingRuntimePublishNo === selectedPublishRecord.publishNo"
            @click="handleRefreshRuntime(selectedPublishRecord)"
          >
            新增刷新并处理
          </el-button>
        </div>
        <dl class="diff-list drawer-diff">
          <div>
            <dt>发布号</dt>
            <dd>{{ selectedPublishRecord.publishNo }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>
              <el-tag :type="publishStatusType(selectedPublishRecord.status)" effect="light">
                {{ publishStatusLabel(selectedPublishRecord.status) }}
              </el-tag>
            </dd>
          </div>
          <div>
            <dt>变更前快照</dt>
            <dd class="value-block">{{ selectedPublishRecord.beforeSnapshotMaskedJson || '--' }}</dd>
          </div>
          <div>
            <dt>变更后快照</dt>
            <dd class="value-block">{{ selectedPublishRecord.afterSnapshotMaskedJson || '--' }}</dd>
          </div>
          <div v-if="selectedPublishRecord.failureReason">
            <dt>失败原因</dt>
            <dd>{{ selectedPublishRecord.failureReason }}</dd>
          </div>
        </dl>
        <div class="runtime-box">
          <h4>运行态事件</h4>
          <el-empty v-if="!selectedPublishRecord.events?.length" description="暂无运行态事件" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="event in selectedPublishRecord.events"
              :key="event.id"
              :timestamp="formatDate(event.createTime)"
            >
              <strong>{{ eventTypeLabel(event.eventType) }}</strong>
              <span>
                {{ moduleLabel(event.moduleCode) }} ·
                <el-tag :type="eventStatusType(event.status)" effect="light" size="small">
                  {{ eventStatusLabel(event.status) }}
                </el-tag>
              </span>
              <div class="runtime-event-meta">
                <small>尝试 {{ event.retryCount ?? 0 }}/{{ event.maxRetryCount ?? 3 }}</small>
                <small v-if="event.lastAttemptAt">最近尝试 {{ formatDate(event.lastAttemptAt) }}</small>
                <small v-if="event.handledAt">完成 {{ formatDate(event.handledAt) }}</small>
                <small v-if="event.nextRetryAt">下次重试 {{ formatDate(event.nextRetryAt) }}</small>
              </div>
              <p v-if="event.errorMessage" class="runtime-event-error">{{ event.errorMessage }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="logDrawerVisible" title="配置变更详情" size="560px">
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
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTag } from 'element-plus'
import {
  createRollbackSystemConfigDraft,
  createSystemConfigDraft,
  discardSystemConfigDraft,
  dryRunSystemConfigDraft,
  fetchSystemConfigCapabilities,
  fetchSystemConfigChangeLogs,
  fetchSystemConfigDrafts,
  fetchSystemConfigPublishRecord,
  fetchSystemConfigPublishRecords,
  fetchSystemConfigRuntimeOverview,
  previewSystemConfig,
  processSystemConfigRuntimeEvents,
  publishSystemConfigDraft,
  refreshSystemConfigRuntime,
  rollbackPreviewSystemConfig,
  validateSystemConfigDraft
} from '../api/systemConfig'

const ResultPanel = defineComponent({
  props: {
    title: { type: String, required: true },
    items: { type: Array, default: () => [] }
  },
  setup(props) {
    const typeOf = (status) => {
      if (status === 'BLOCK') return 'danger'
      if (status === 'WARN') return 'warning'
      return 'success'
    }
    const labelOf = (status) => {
      if (status === 'BLOCK') return '阻断'
      if (status === 'WARN') return '提醒'
      if (status === 'PASS') return '通过'
      return status || '通过'
    }
    return () =>
      h('div', { class: 'runtime-box' }, [
        h('h4', props.title),
        props.items.length
          ? h(
              'div',
              { class: 'validation-list' },
              props.items.map((item) =>
                h('div', { class: 'validation-row', key: item.configKey }, [
                  h(ElTag, { type: typeOf(item.status), effect: 'light' }, () => labelOf(item.status)),
                  h('span', { class: 'validation-row__key' }, item.configKey),
                  h('span', { class: 'validation-row__message' }, [
                    item.message || '--',
                    item.suggestion ? h('small', { class: 'validation-row__suggestion' }, item.suggestion) : null
                  ])
                ])
              )
            )
          : h('span', { class: 'muted' }, '暂无校验明细')
      ])
  }
})

const activeTab = ref('preview')
const refreshing = ref(false)
const loadingLogs = ref(false)
const loadingDrafts = ref(false)
const loadingCapabilities = ref(false)
const loadingRuntime = ref(false)
const loadingPublishRecords = ref(false)
const previewing = ref(false)
const savingDraft = ref(false)
const rollbackPreviewing = ref(false)
const rollbackDrafting = ref(false)
const publishingDraftNo = ref('')
const discardingDraftNo = ref('')
const validatingDraftNo = ref('')
const dryRunningDraftNo = ref('')
const refreshingPublishNo = ref('')
const processingRuntimePublishNo = ref('')

const logs = ref([])
const drafts = ref([])
const capabilities = ref([])
const publishRecords = ref([])
const runtimeOverview = ref({})
const previewResult = ref(null)
const selectedLog = ref(null)
const selectedDraft = ref(null)
const selectedPublishRecord = ref(null)
const rollbackPreviewResult = ref(null)
const logDrawerVisible = ref(false)
const draftDrawerVisible = ref(false)
const publishDrawerVisible = ref(false)
const validationMap = reactive({})
const dryRunMap = reactive({})
const activeCapabilityCode = ref('')

const capabilityMeta = {
  SYSTEM_DOMAIN: {
    label: '域名与回调地址',
    description: '统一生成系统域名、API 域名、回调和联调地址',
    exampleKey: 'system.domain.apiBaseUrl',
    exampleValue: 'https://api.seedcrm.com'
  },
  WORKFLOW_SWITCH: {
    label: '流程开关',
    description: '控制订单、排档、服务单等流程能力灰度启用',
    exampleKey: 'workflow.service_order.enabled',
    exampleValue: 'false'
  },
  DEPOSIT_DIRECT: {
    label: '定金免码核销',
    description: '控制定金订单是否允许免扫码进入服务流程',
    exampleKey: 'deposit.direct.enabled',
    exampleValue: 'true'
  },
  AMOUNT_VISIBILITY: {
    label: '金额可见范围',
    description: '控制门店、财务和服务角色看到哪些金额字段',
    exampleKey: 'amount.visibility.store_staff_hidden',
    exampleValue: 'true'
  },
  CLUE_DEDUP: {
    label: '客资去重规则',
    description: '控制客资入站去重窗口和合并策略',
    exampleKey: 'clue.dedup.window_days',
    exampleValue: '90'
  },
  SERVICE_FORM_PRINT_REQUIRED: {
    label: '确认前打印',
    description: '控制纸质单确认前是否必须打印当前服务确认单版本',
    exampleKey: 'service_form.print.required_before_confirm',
    exampleValue: 'true'
  },
  SERVICE_FORM_CONFIRM_REQUIRED: {
    label: '服务前确认',
    description: '控制开始服务前是否必须确认纸质服务确认单',
    exampleKey: 'service_form.confirm.required_before_start',
    exampleValue: 'true'
  },
  SERVICE_FORM_STALE_POLICY: {
    label: '重打策略',
    description: '控制确认单内容变更后是否阻断确认和开始服务',
    exampleKey: 'service_form.print.stale_policy',
    exampleValue: 'BLOCK_CONFIRM'
  },
  SERVICE_FORM_DESIGNER: {
    label: '服务单设计器',
    description: '控制成熟设计器适配、白名单和导入拦截规则',
    exampleKey: 'form_designer.allowed_engines',
    exampleValue: 'INTERNAL_SCHEMA,FORMILY,VFORM3,LOWCODE_ENGINE,JSON_SCHEMA'
  },
  SERVICE_FORM_DESIGNER_PAPER_SIGNATURE: {
    label: '纸质签名留位',
    description: '控制打印版服务确认单是否强制保留手写签名位置',
    exampleKey: 'form_designer.paper_signature_required',
    exampleValue: 'true'
  },
  SERVICE_FORM_DESIGNER_SCHEMA_SIZE: {
    label: 'Schema 大小',
    description: '控制单个服务单设计器 Schema 的最大导入长度',
    exampleKey: 'form_designer.max_schema_bytes',
    exampleValue: '200000'
  },
  DISTRIBUTION_MAPPING: {
    label: '分销订单映射',
    description: '配置外部分销订单类型、SKU 与内部订单类型映射',
    exampleKey: 'distribution.order.type.mapping',
    exampleValue: '{\n  "default": "coupon",\n  "aliases": {},\n  "rules": []\n}'
  },
  SCHEDULER_INTEGRATION: {
    label: '调度集成参数',
    description: '控制调度任务、重试和队列类配置',
    exampleKey: 'scheduler.distribution.retry.max',
    exampleValue: '3'
  },
  DOUYIN_INTEGRATION: {
    label: '抖音接口配置',
    description: '维护抖音线索拉取、回调和接口联调参数',
    exampleKey: 'douyin.clientId',
    exampleValue: ''
  },
  WECOM_INTEGRATION: {
    label: '企业微信配置',
    description: '维护企微活码、客户关系回调和私域能力参数',
    exampleKey: 'wecom.corpId',
    exampleValue: ''
  },
  PAYMENT_BLOCKED: {
    label: '支付资金配置',
    description: '资金类配置只允许走财务专用流程，本页禁止直写',
    exampleKey: 'payment.gateway.key',
    exampleValue: ''
  }
}

const moduleNameMap = {
  SYSTEM_SETTING: '系统设置',
  SYSTEM_FLOW: '系统流程',
  STORE_SERVICE: '门店服务',
  FINANCE: '财务管理',
  CLUE: '客资中心',
  PLANORDER: '门店排档',
  SCHEDULER: '调度中心',
  WECOM: '私域客服',
  SYSTEM_CONFIG: '系统配置'
}

const pinnedCapabilityCodes = [
  'SERVICE_FORM_PRINT_REQUIRED',
  'SERVICE_FORM_CONFIRM_REQUIRED',
  'SERVICE_FORM_STALE_POLICY',
  'SERVICE_FORM_DESIGNER',
  'SERVICE_FORM_DESIGNER_PAPER_SIGNATURE',
  'SERVICE_FORM_DESIGNER_SCHEMA_SIZE'
]

const serviceFormPresets = [
  {
    label: '确认前必须打印',
    description: '纸质单确认前要求当前版本已打印',
    configKey: 'service_form.print.required_before_confirm',
    configValue: 'true',
    valueType: 'BOOLEAN'
  },
  {
    label: '开始服务前确认',
    description: '开始服务前要求纸质单已确认',
    configKey: 'service_form.confirm.required_before_start',
    configValue: 'true',
    valueType: 'BOOLEAN'
  },
  {
    label: '内容变更阻断',
    description: '确认单内容变化后要求重新打印',
    configKey: 'service_form.print.stale_policy',
    configValue: 'BLOCK_CONFIRM',
    valueType: 'STRING'
  },
  {
    label: '成熟设计器白名单',
    description: '只允许已适配设计器引擎导入',
    configKey: 'form_designer.allowed_engines',
    configValue: 'INTERNAL_SCHEMA,FORMILY,VFORM3,LOWCODE_ENGINE,JSON_SCHEMA',
    valueType: 'STRING'
  },
  {
    label: '电子签名拦截',
    description: '导入时拦截电子签名和脚本组件',
    configKey: 'form_designer.blocked_components',
    configValue: 'signature,esign,electronicSignature,canvasSignature,html,iframe,script,webview',
    valueType: 'STRING'
  },
  {
    label: 'Schema 大小上限',
    description: '限制单模板导入体积，避免内嵌资源过大',
    configKey: 'form_designer.max_schema_bytes',
    configValue: '200000',
    valueType: 'NUMBER'
  },
  {
    label: '纸质签名留位',
    description: '打印版强制保留手写签名位置',
    configKey: 'form_designer.paper_signature_required',
    configValue: 'true',
    valueType: 'BOOLEAN'
  }
]

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
const publishSuccessCount = computed(() => publishRecords.value.filter((item) => item.status === 'SUCCESS').length)
const publishFailedCount = computed(() => publishRecords.value.filter((item) => item.status === 'FAILED').length)
const runtimePendingCount = computed(() => runtimeOverview.value.runtimeEventPendingCount ?? 0)
const runtimeFailedCount = computed(() =>
  (runtimeOverview.value.runtimeEventFailedCount ?? 0) + (runtimeOverview.value.runtimeEventTerminatedCount ?? 0)
)
const sortedCapabilities = computed(() => [...capabilities.value].sort(compareCapability))
const visibleCapabilities = computed(() => sortedCapabilities.value.slice(0, 9))
const capabilityCards = computed(() => sortedCapabilities.value.slice(0, 12))
const currentStepIndex = computed(() => {
  const map = {
    preview: previewResult.value ? 1 : 0,
    drafts: 3,
    publishRecords: 5,
    logs: 5
  }
  return map[activeTab.value] ?? 0
})
const pageStatusTitle = computed(() => {
  if (runtimeFailedCount.value > 0) return '有运行态刷新失败'
  if (highRiskDraftCount.value > 0) return '有高风险草稿待处理'
  if (runtimePendingCount.value > 0) return '有运行态事件待处理'
  return '配置治理链路正常'
})
const pageStatusText = computed(() => {
  if (runtimeFailedCount.value > 0) {
    return '运行态刷新失败不会改动客户、订单、排档或财务数据；可在发布批次详情中手动重试刷新。'
  }
  if (highRiskDraftCount.value > 0) {
    return '高风险配置必须完成校验和发布预检查，确认发布后才会生效。'
  }
  if (runtimePendingCount.value > 0) {
    return '发布已写入运行态事件，后续由模块感知或手工刷新处理。'
  }
  return '受控配置会按预览、草稿、校验、发布预检查、发布、审计回滚的顺序流转。'
})
const latestPublishTime = computed(() => {
  if (runtimeOverview.value.lastPublishedAt) {
    return formatDate(runtimeOverview.value.lastPublishedAt)
  }
  return publishRecords.value[0] ? formatDate(publishRecords.value[0].publishedAt) : '--'
})

onMounted(refreshAll)

async function refreshAll() {
  refreshing.value = true
  try {
    await Promise.all([
      loadDrafts(),
      loadLogs(),
      loadCapabilities(),
      loadRuntimeOverview(),
      loadPublishRecords()
    ])
  } finally {
    refreshing.value = false
  }
}

async function handleTabChange(name) {
  if (name === 'drafts') {
    await loadDrafts()
  } else if (name === 'logs') {
    await loadLogs()
  } else if (name === 'publishRecords') {
    await loadPublishRecords()
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

async function loadCapabilities() {
  loadingCapabilities.value = true
  try {
    capabilities.value = await fetchSystemConfigCapabilities()
  } finally {
    loadingCapabilities.value = false
  }
}

async function loadRuntimeOverview() {
  loadingRuntime.value = true
  try {
    runtimeOverview.value = await fetchSystemConfigRuntimeOverview()
  } finally {
    loadingRuntime.value = false
  }
}

async function loadPublishRecords() {
  loadingPublishRecords.value = true
  try {
    publishRecords.value = await fetchSystemConfigPublishRecords({ limit: 100 })
  } finally {
    loadingPublishRecords.value = false
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
    await Promise.all([loadDrafts(), loadRuntimeOverview()])
  } finally {
    savingDraft.value = false
  }
}

async function handleValidateDraft(row) {
  if (!row?.draftNo) return null
  validatingDraftNo.value = row.draftNo
  try {
    const result = await validateSystemConfigDraft(row.draftNo)
    validationMap[row.draftNo] = result
    if (result.valid) {
      ElMessage.success('草稿校验通过')
    } else {
      ElMessage.warning('草稿存在阻断项，不能发布')
    }
    return result
  } finally {
    validatingDraftNo.value = ''
  }
}

async function handleDryRunDraft(row) {
  if (!row?.draftNo) return null
  dryRunningDraftNo.value = row.draftNo
  try {
    const result = await dryRunSystemConfigDraft(row.draftNo)
    dryRunMap[row.draftNo] = result
    if (result.runnable) {
      ElMessage.success('发布预检查通过')
    } else {
      ElMessage.warning('发布预检查未通过，请先处理校验项')
    }
    await loadDrafts()
    return result
  } finally {
    dryRunningDraftNo.value = ''
  }
}

async function handlePublishDraft(row) {
  if (!row?.draftNo) return
  const validation = validationMap[row.draftNo] || (await handleValidateDraft(row))
  if (!validation?.valid) {
    ElMessage.error('草稿校验未通过，已阻止发布')
    return
  }
  const dryRun = dryRunMap[row.draftNo] || (await handleDryRunDraft(row))
  if (!dryRun?.runnable) {
    ElMessage.error('发布预检查未通过，已阻止发布')
    return
  }
  if (row.riskLevel === 'HIGH') {
    await ElMessageBox.prompt(
      `发布后将影响 ${moduleText(row.impactModules)}。请输入“确认发布”继续。`,
      '确认发布高风险配置',
      {
        type: 'warning',
        inputPattern: /^确认发布$/,
        inputErrorMessage: '请输入：确认发布',
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
    await Promise.all([loadDrafts(), loadLogs(), loadPublishRecords(), loadRuntimeOverview()])
    activeTab.value = 'publishRecords'
  } finally {
    publishingDraftNo.value = ''
  }
}

async function handleDiscardDraft(row) {
  if (!row?.draftNo) return
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
    await Promise.all([loadDrafts(), loadRuntimeOverview()])
  } finally {
    discardingDraftNo.value = ''
  }
}

async function handleRefreshRuntime(row) {
  if (!row?.publishNo) return
  await ElMessageBox.confirm(
    `确认为发布批次 ${row.publishNo} 新增一次刷新事件并立即处理？该操作只刷新配置感知，不会修改客户、订单、排档或账务数据。`,
    '刷新运行态配置',
    {
      type: 'warning',
      confirmButtonText: '刷新并处理',
      cancelButtonText: '取消'
    }
  )
  refreshingPublishNo.value = row.publishNo
  processingRuntimePublishNo.value = row.publishNo
  try {
    await refreshSystemConfigRuntime(row.publishNo)
    const detail = await processSystemConfigRuntimeEvents(row.publishNo)
    selectedPublishRecord.value = detail
    publishDrawerVisible.value = true
    ElMessage.success('运行态刷新已处理')
    await Promise.all([loadPublishRecords(), loadRuntimeOverview()])
  } finally {
    refreshingPublishNo.value = ''
    processingRuntimePublishNo.value = ''
  }
}

async function handleProcessRuntimeEvents(row) {
  if (!row?.publishNo) return
  await ElMessageBox.confirm(
    `确认处理发布批次 ${row.publishNo} 的待刷新或失败事件？本操作只重新加载已发布配置，不会改动业务数据。`,
    '处理运行态刷新事件',
    {
      type: 'warning',
      confirmButtonText: '处理刷新事件',
      cancelButtonText: '取消'
    }
  )
  processingRuntimePublishNo.value = row.publishNo
  try {
    const detail = await processSystemConfigRuntimeEvents(row.publishNo)
    selectedPublishRecord.value = detail
    ElMessage.success('运行态刷新事件已处理')
    await Promise.all([loadPublishRecords(), loadRuntimeOverview()])
  } finally {
    processingRuntimePublishNo.value = ''
  }
}

async function handleRollbackPreview() {
  if (!selectedLog.value?.id) return
  rollbackPreviewing.value = true
  try {
    rollbackPreviewResult.value = await rollbackPreviewSystemConfig(selectedLog.value.id)
    ElMessage.success('回滚预览已生成，线上业务未变化')
  } finally {
    rollbackPreviewing.value = false
  }
}

async function handleCreateRollbackDraft() {
  if (!selectedLog.value?.id) return
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
    await Promise.all([loadDrafts(), loadRuntimeOverview()])
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

function applyCapability(item) {
  const meta = capabilityMeta[item.capabilityCode] || {}
  activeCapabilityCode.value = item.capabilityCode
  previewForm.configKey = meta.exampleKey || defaultConfigKey(item)
  previewForm.valueType = item.valueType || 'STRING'
  previewForm.scopeType = item.scopeTypes?.[0] || 'GLOBAL'
  previewForm.scopeId = 'GLOBAL'
  previewForm.configValue = meta.exampleValue ?? defaultConfigValue(item.valueType)
  previewForm.enabled = 1
  previewForm.summary = `调整${capabilityLabel(item)}`
  previewResult.value = null
}

function applyServiceFormPreset(item) {
  activeCapabilityCode.value = relatedServiceFormCapability(item.configKey)
  previewForm.configKey = item.configKey
  previewForm.valueType = item.valueType
  previewForm.scopeType = 'GLOBAL'
  previewForm.scopeId = 'GLOBAL'
  previewForm.configValue = item.configValue
  previewForm.enabled = 1
  previewForm.summary = `调整${item.label}`
  previewResult.value = null
}

function relatedServiceFormCapability(configKey) {
  const key = String(configKey || '')
  if (key === 'service_form.print.required_before_confirm') return 'SERVICE_FORM_PRINT_REQUIRED'
  if (key === 'service_form.confirm.required_before_start') return 'SERVICE_FORM_CONFIRM_REQUIRED'
  if (key === 'service_form.print.stale_policy') return 'SERVICE_FORM_STALE_POLICY'
  if (key === 'form_designer.paper_signature_required') return 'SERVICE_FORM_DESIGNER_PAPER_SIGNATURE'
  if (key === 'form_designer.max_schema_bytes') return 'SERVICE_FORM_DESIGNER_SCHEMA_SIZE'
  return 'SERVICE_FORM_DESIGNER'
}

function compareCapability(a, b) {
  const left = pinnedCapabilityIndex(a?.capabilityCode)
  const right = pinnedCapabilityIndex(b?.capabilityCode)
  if (left !== right) return left - right
  return String(a?.capabilityCode || '').localeCompare(String(b?.capabilityCode || ''))
}

function pinnedCapabilityIndex(code) {
  const index = pinnedCapabilityCodes.indexOf(code)
  return index === -1 ? 1000 : index
}

function defaultConfigKey(item) {
  const pattern = String(item?.configKeyPattern || '').trim()
  if (!pattern) return ''
  return pattern.endsWith('%') ? `${pattern.slice(0, -1)}example` : pattern
}

function defaultConfigValue(valueType) {
  const type = String(valueType || '').toUpperCase()
  if (type === 'BOOLEAN') return 'false'
  if (type === 'NUMBER') return '1'
  if (type === 'URL') return 'https://api.seedcrm.com'
  if (type === 'JSON') return '{}'
  return ''
}

function openLog(row) {
  selectedLog.value = row
  rollbackPreviewResult.value = null
  logDrawerVisible.value = true
}

async function openPublishRecord(row) {
  selectedPublishRecord.value = row
  publishDrawerVisible.value = true
  if (row?.publishNo) {
    selectedPublishRecord.value = await fetchSystemConfigPublishRecord(row.publishNo)
  }
}

function openDraft(row) {
  selectedDraft.value = row
  draftDrawerVisible.value = true
}

function firstDraftItem(row) {
  return row?.items?.[0] || {}
}

function moduleText(items = []) {
  return items.length ? items.map((item) => moduleLabel(item)).join('、') : '相关模块'
}

function moduleLabel(value) {
  return moduleNameMap[value] || value || '--'
}

function capabilityLabel(item) {
  return capabilityMeta[item?.capabilityCode]?.label || item?.capabilityCode || '--'
}

function capabilityDescription(item) {
  return capabilityMeta[item?.capabilityCode]?.description || '受控配置能力，发布前必须完成校验和发布预检查'
}

function valueTypeLabel(value) {
  const map = {
    STRING: '文本',
    BOOLEAN: '开关',
    NUMBER: '数字',
    URL: '地址',
    JSON: 'JSON'
  }
  return map[value] || value || '--'
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
    BLOCKED: '阻断',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低'
  }
  return map[value] || value || '--'
}

function riskTagType(value) {
  const map = {
    BLOCKED: 'danger',
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return map[value] || 'info'
}

function validationLabel(value) {
  if (value === true) return '通过'
  if (value === false) return '阻断'
  return '未校验'
}

function validationTagType(value) {
  if (value === true) return 'success'
  if (value === false) return 'danger'
  return 'info'
}

function dryRunLabel(value) {
  if (value === true) return '已通过'
  if (value === false) return '未通过'
  return '未预检'
}

function dryRunTagType(value) {
  if (value === true) return 'success'
  if (value === false) return 'danger'
  return 'info'
}

function draftDryRunState(row) {
  if (dryRunMap[row.draftNo]?.runnable === true) return true
  if (dryRunMap[row.draftNo]?.runnable === false) return false
  if (row.lastDryRunStatus === 'PASS') return true
  if (row.lastDryRunStatus === 'BLOCK') return false
  return null
}

function publishStatusLabel(value) {
  const map = {
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return map[value] || value || '--'
}

function publishStatusType(value) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  return 'info'
}

function eventTypeLabel(value) {
  const map = {
    CONFIG_PUBLISHED: '配置发布事件',
    RUNTIME_REFRESH: '运行态刷新事件',
    CACHE_EVICT: '缓存刷新事件'
  }
  return map[value] || value || '--'
}

function eventStatusLabel(value) {
  const map = {
    PENDING: '待处理',
    RETRYING: '处理中',
    SUCCESS: '成功',
    FAILED: '失败',
    TERMINATED: '已终止',
    RECORDED: '已记录'
  }
  return map[value] || value || '--'
}

function eventStatusType(value) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'TERMINATED') return 'danger'
  if (value === 'RETRYING') return 'warning'
  if (value === 'PENDING') return 'warning'
  return 'info'
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
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.config-alert {
  margin-bottom: 16px;
  border-radius: 8px;
}

.publish-flow {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}

.publish-flow :deep(.el-steps--simple) {
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #eef6ff 100%);
}

.page-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 13px;
}

.page-status strong {
  color: #0f172a;
}

.capability-strip {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  min-height: 52px;
  margin-bottom: 16px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.capability-strip__main,
.capability-strip__meta,
.tag-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.capability-strip__label {
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.capability-strip__meta,
.muted {
  color: #64748b;
  font-size: 13px;
}

.capability-catalog {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.capability-card {
  min-width: 0;
  min-height: 118px;
  padding: 12px;
  border: 1px solid #dbe4f0;
  border-radius: 12px;
  background: #ffffff;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.capability-card:hover,
.capability-card.is-active {
  border-color: #2563eb;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.12);
  transform: translateY(-1px);
}

.capability-card__top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.capability-card strong {
  color: #0f172a;
  font-size: 14px;
}

.capability-card span,
.capability-card small {
  display: block;
}

.capability-card small {
  margin-top: 8px;
  color: #64748b;
}

.service-form-presets {
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.service-form-presets__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  margin-bottom: 12px;
}

.service-form-presets__head strong {
  color: #0f172a;
}

.service-form-presets__head span {
  color: #64748b;
  font-size: 13px;
}

.service-form-preset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.service-form-preset {
  min-height: 104px;
  padding: 12px;
  border: 1px solid #dbe4f0;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.service-form-preset:hover,
.service-form-preset.is-active {
  border-color: #0ea5e9;
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.12);
}

.service-form-preset strong,
.service-form-preset span,
.service-form-preset small {
  display: block;
}

.service-form-preset strong {
  color: #0f172a;
  font-size: 14px;
}

.service-form-preset span {
  margin-top: 6px;
  font-size: 13px;
}

.service-form-preset small {
  margin-top: 8px;
  color: #64748b;
  overflow-wrap: anywhere;
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

.preview-result__header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
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

.runtime-box,
.rollback-preview {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.runtime-box h4,
.rollback-preview h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #0f172a;
}

.runtime-box p {
  margin: 0 0 10px;
  color: #475569;
}

.runtime-event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  color: #64748b;
}

.runtime-event-error {
  margin: 8px 0 0;
  padding: 8px;
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
  overflow-wrap: anywhere;
}

.validation-list {
  display: grid;
  gap: 8px;
}

.validation-row {
  display: grid;
  grid-template-columns: 72px minmax(120px, 1fr) minmax(180px, 1.4fr);
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.validation-row__key,
.validation-row__message {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #334155;
}

.validation-row__suggestion {
  display: block;
  margin-top: 4px;
  color: #64748b;
}

@media (max-width: 1280px) {
  .capability-catalog,
  .service-form-preset-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .preview-layout,
  .log-toolbar,
  .validation-row {
    grid-template-columns: 1fr;
  }

  .capability-catalog,
  .service-form-preset-grid {
    grid-template-columns: 1fr;
  }

  .config-form {
    grid-template-columns: 1fr;
  }

  .capability-strip {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
