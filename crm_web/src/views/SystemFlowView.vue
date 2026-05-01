<template>
  <div class="stack-page system-flow-page">
    <section class="flow-hero panel">
      <div>
        <span class="flow-hero__eyebrow">唯一主链路</span>
        <h3>Clue -> Customer -> Order -> PlanOrder</h3>
        <div class="flow-boundary-tags">
          <el-tag effect="plain">只读配置</el-tag>
          <el-tag effect="plain" type="success">触发器元数据</el-tag>
          <el-tag effect="plain" type="warning">不改业务单据</el-tag>
        </div>
      </div>
      <div class="flow-hero__stats">
        <article>
          <span>当前版本</span>
          <strong>v{{ detail.version?.versionNo || '-' }}</strong>
        </article>
        <article>
          <span>配置版本</span>
          <strong>{{ formatFlowStatus(detail.version?.status) }}</strong>
        </article>
        <article>
          <span>触发器元数据</span>
          <strong>{{ detail.triggers?.length || 0 }}</strong>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>能力开关</h3>
        </div>
        <div class="action-group">
          <el-button :loading="configLoading" @click="loadCapabilityConfigs">刷新开关</el-button>
        </div>
      </div>
      <el-table v-loading="configLoading" :data="capabilityConfigs" stripe>
        <el-table-column label="配置项" min-width="260">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.configKey }}</strong>
              <span>{{ row.description || '--' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="值" width="180">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueType === 'BOOLEAN'"
              :model-value="row.configValue === 'true'"
              :loading="savingConfigKey === row.configKey"
              @change="toggleCapabilityConfig(row, $event)"
            />
            <el-tag v-else effect="plain">{{ row.configValue || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>系统流程</h3>
        </div>
        <div class="action-group">
          <el-button @click="loadAll">刷新</el-button>
          <el-button type="success" :disabled="!detail.version" @click="openDraftDialog">基于当前版本建草稿</el-button>
          <el-button type="primary" :disabled="!canPublishCurrentVersion" @click="publishCurrentVersion">发布草稿版本</el-button>
        </div>
      </div>

      <el-table :data="flows" stripe>
        <el-table-column label="流程" min-width="220">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.flowName }}</strong>
              <span>{{ row.flowCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="业务对象" width="120" prop="businessObject" />
        <el-table-column label="版本" width="120">
          <template #default="{ row }">v{{ row.currentVersionNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="节点/触发器" width="140">
          <template #default="{ row }">{{ row.nodeCount || 0 }} / {{ row.triggerCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="配置展示状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '展示中' : '已隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="loadDetail(row.flowCode)">查看</el-button>
              <el-button size="small" plain :disabled="row.enabled !== 1" @click="disableFlow(row)">停用配置展示</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>{{ detail.definition?.flowName || '流程详情' }}</h3>
        </div>
        <div class="flow-version-tools" v-if="detail.version">
          <el-select v-model="selectedVersionId" class="flow-version-select" placeholder="选择版本" @change="handleVersionChange">
            <el-option
              v-for="version in versions"
              :key="version.id"
              :label="`v${version.versionNo} · ${formatFlowStatus(version.status)}`"
              :value="version.id"
            />
          </el-select>
          <div class="flow-version-pill">
            <span>v{{ detail.version.versionNo }}</span>
            <strong>{{ formatFlowStatus(detail.version.status) }}</strong>
          </div>
          <el-tag :type="validationReport?.valid ? 'success' : 'warning'" effect="light">
            {{ validationReport?.valid ? '体检通过' : '需检查' }}
          </el-tag>
        </div>
      </div>

      <el-empty v-if="!detail.definition" description="暂无流程详情，请先选择一个流程" />
      <template v-else>
        <div class="flow-lane">
          <article v-for="node in detail.nodes" :key="node.nodeCode" class="flow-node-card">
            <div class="flow-node-card__index">{{ node.sortOrder }}</div>
            <el-tag size="small" effect="plain">{{ formatDomain(node.domainCode) }}</el-tag>
            <strong>{{ node.nodeName }}</strong>
            <span>{{ node.nodeCode }}</span>
            <small>{{ formatNodeType(node.nodeType) }} · {{ node.businessState || '展示节点' }} · {{ node.roleCode || '系统' }}</small>
          </article>
        </div>

        <el-tabs v-model="detailTab" class="flow-tabs">
          <el-tab-pane label="流转规则" name="transitions">
            <el-table :data="detail.transitions" stripe>
              <el-table-column label="动作" min-width="160">
                <template #default="{ row }">
                  <div class="table-primary">
                    <strong>{{ row.actionName }}</strong>
                    <span>{{ row.actionCode }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="从" width="160" prop="fromNodeCode" />
              <el-table-column label="到" width="160" prop="toNodeCode" />
              <el-table-column label="前置条件" min-width="220" prop="guardRule" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="触发器" name="triggers">
          <el-table :data="detail.triggers" stripe>
              <el-table-column label="触发器" min-width="200">
                <template #default="{ row }">
                  <div class="table-primary">
                    <strong>{{ row.triggerName }}</strong>
                    <span>{{ row.triggerType }} / {{ row.executionMode }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="节点" width="160" prop="nodeCode" />
              <el-table-column label="目标" min-width="220" prop="targetCode" />
              <el-table-column label="边界" min-width="220">
                <template #default>仅保存元数据，不真实调用接口/服务</template>
              </el-table-column>
              <el-table-column label="配置展示状态" width="130">
                <template #default="{ row }">
                  <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '展示中' : '已隐藏' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="流程体检" name="validation">
            <div class="flow-validation-summary">
              <el-tag :type="validationReport?.valid ? 'success' : 'warning'">
                {{ validationReport?.valid ? '通过' : '需处理' }}
              </el-tag>
              <span>v{{ validationReport?.versionNo || '-' }} · {{ formatFlowStatus(validationReport?.versionStatus) }}</span>
            </div>
            <el-table :data="validationReport?.items || []" stripe>
              <el-table-column label="结果" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '阻断' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="范围" width="130" prop="domain" />
              <el-table-column label="检查项" min-width="180" prop="checkCode" />
              <el-table-column label="结论" min-width="260" prop="message" />
              <el-table-column label="处理建议" min-width="260" prop="suggestion" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="触发器联动" name="linkage">
            <div class="flow-validation-summary">
              <el-tag :type="triggerLinkageReport?.healthy ? 'success' : 'danger'">
                {{ triggerLinkageReport?.healthy ? '联动正常' : '存在阻断' }}
              </el-tag>
              <span>只读检查，不执行真实任务。</span>
            </div>
            <el-table :data="triggerLinkageReport?.items || []" stripe>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="formatLinkageStatus(row.status).type">
                    {{ formatLinkageStatus(row.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="触发器" min-width="210">
                <template #default="{ row }">
                  <div class="table-primary">
                    <strong>{{ row.triggerName || '--' }}</strong>
                    <span>{{ row.nodeCode || '--' }} / {{ row.targetCode || '--' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="关联对象" min-width="220">
                <template #default="{ row }">
                  <div class="table-primary">
                    <strong>{{ formatLinkageModule(row.linkedModule) }}</strong>
                    <span>{{ row.linkedResource || '--' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="结论" min-width="260" prop="message" />
              <el-table-column label="建议" min-width="260" prop="suggestion" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="旁路记录" name="runtime">
            <div class="flow-validation-summary">
              <el-tag type="success">轻量状态机</el-tag>
              <span>仅记录实例、人工任务和事件日志，不自动改订单、财务或三方接口。</span>
            </div>
            <div class="flow-hero__stats flow-hero__stats--inline">
              <article>
                <span>旁路实例</span>
                <strong>{{ runtimeOverview?.runningCount || 0 }}</strong>
              </article>
              <article>
                <span>待办任务</span>
                <strong>{{ runtimeOverview?.openTaskCount || 0 }}</strong>
              </article>
              <article>
                <span>最近事件</span>
                <strong>{{ runtimeOverview?.recentEvents?.length || 0 }}</strong>
              </article>
            </div>
            <el-tabs v-model="runtimeTab" class="flow-tabs">
              <el-tab-pane label="实例" name="instances">
                <el-table :data="runtimeOverview?.recentInstances || []" stripe>
                  <el-table-column label="业务" min-width="180">
                    <template #default="{ row }">
                      <div class="table-primary">
                        <strong>{{ row.title || `${row.businessObject}#${row.businessId}` }}</strong>
                        <span>{{ row.businessObject }} / {{ row.businessId }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="当前节点" min-width="180">
                    <template #default="{ row }">{{ row.currentNodeName || row.currentNodeCode }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="120" prop="status" />
                  <el-table-column label="更新时间" width="180">
                    <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="人工任务" name="tasks">
                <el-table :data="runtimeOverview?.openTasks || []" stripe>
                  <el-table-column label="任务" min-width="220">
                    <template #default="{ row }">
                      <div class="table-primary">
                        <strong>{{ row.taskName }}</strong>
                        <span>{{ row.nodeName || row.nodeCode }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="角色" width="160" prop="roleCode" />
                  <el-table-column label="打开时间" width="180">
                    <template #default="{ row }">{{ formatDateTime(row.openedAt) }}</template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="事件日志" name="events">
                <el-table :data="runtimeOverview?.recentEvents || []" stripe>
                  <el-table-column label="动作" width="170" prop="actionCode" />
                  <el-table-column label="流转" width="210">
                    <template #default="{ row }">{{ row.fromNodeCode || '开始' }} -> {{ row.toNodeCode || '--' }}</template>
                  </el-table-column>
                  <el-table-column label="说明" min-width="220" prop="summary" />
                  <el-table-column label="时间" width="180">
                    <template #default="{ row }">{{ formatDateTime(row.eventTime) }}</template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>

          <el-tab-pane label="审计记录" name="audit">
            <el-table :data="auditLogs" stripe>
              <el-table-column label="时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="动作" width="140" prop="actionType" />
              <el-table-column label="版本" width="90">
                <template #default="{ row }">{{ row.versionNo ? `v${row.versionNo}` : '--' }}</template>
              </el-table-column>
              <el-table-column label="操作者" width="160">
                <template #default="{ row }">{{ row.actorRoleCode || '--' }} / {{ row.actorUserId || '--' }}</template>
              </el-table-column>
              <el-table-column label="说明" min-width="220" prop="summary" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>只读试算</h3>
        </div>
      </div>
      <div class="form-grid">
        <label>
          <span>当前节点</span>
          <el-select v-model="simulateForm.currentNodeCode" placeholder="请选择节点">
            <el-option v-for="node in detail.nodes" :key="node.nodeCode" :label="`${node.nodeName} / ${node.nodeCode}`" :value="node.nodeCode" />
          </el-select>
        </label>
        <label>
          <span>动作</span>
          <el-select v-model="simulateForm.actionCode" clearable placeholder="可选择动作">
            <el-option
              v-for="item in availableActions"
              :key="item.actionCode"
              :label="`${item.actionName} / ${item.actionCode}`"
              :value="item.actionCode"
            />
          </el-select>
        </label>
        <label>
          <span>角色</span>
          <el-select v-model="simulateForm.roleCode">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
      </div>
      <div class="action-group action-group--section">
        <el-button type="primary" :disabled="!detail.definition" @click="runSimulation">只读试算</el-button>
        <el-button type="success" plain :disabled="!detail.definition" :loading="runtimeStarting" @click="startRuntimeProbe">创建沙盒记录</el-button>
        <el-button @click="resetSimulation">重置</el-button>
      </div>

      <div v-if="simulation" class="simulation-result" :class="{ 'simulation-result--ok': simulation.allowed }">
        <strong>{{ simulation.allowed ? '只读试算通过' : '未命中流转' }}</strong>
        <span>{{ simulation.message }}</span>
        <small>下一节点：{{ simulation.nextNodeCode || '--' }}</small>
        <small>命中触发器元数据：{{ simulation.matchedTriggers?.map((item) => item.triggerName).join('、') || '无' }}</small>
        <small>本次试算没有执行调度任务、三方接口或内部服务。</small>
      </div>

      <div v-if="runtimeProbe" class="simulation-result simulation-result--ok">
        <strong>沙盒记录已创建</strong>
        <span>{{ runtimeProbe.title || `${runtimeProbe.businessObject}#${runtimeProbe.businessId}` }}</span>
        <small>当前节点：{{ runtimeProbe.currentNodeName || runtimeProbe.currentNodeCode }}</small>
        <small>它只写入流程旁路记录表，不会改真实订单、排档、薪酬或接口配置。</small>
      </div>
    </section>

    <el-dialog v-model="draftDialogVisible" title="流程草稿编辑" width="86%" top="5vh" class="flow-draft-dialog">
      <el-alert
        title="草稿只更新流程配置，不执行真实业务动作"
        type="info"
        :closable="false"
        show-icon
      />

      <div class="draft-basic-grid">
        <label>
          <span>流程名称</span>
          <el-input v-model="draftForm.flowName" />
        </label>
        <label>
          <span>变更说明</span>
          <el-input v-model="draftForm.changeSummary" placeholder="说明本次草稿要解决什么问题" />
        </label>
        <label class="draft-basic-grid__wide">
          <span>流程说明</span>
          <el-input v-model="draftForm.description" type="textarea" :rows="2" />
        </label>
      </div>

      <el-tabs v-model="draftTab" class="draft-tabs">
        <el-tab-pane label="节点配置" name="nodes">
          <el-table :data="draftForm.nodes" stripe max-height="360">
            <el-table-column label="顺序" width="90" prop="sortOrder" />
            <el-table-column label="节点编码" min-width="160" prop="nodeCode" />
            <el-table-column label="所属域" width="150">
              <template #default="{ row }">
                <el-select v-model="row.domainCode" size="small">
                  <el-option label="Clue" value="CLUE" />
                  <el-option label="Customer" value="CUSTOMER" />
                  <el-option label="Order" value="ORDER" />
                  <el-option label="PlanOrder" value="PLANORDER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="节点名称" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.nodeName" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="节点类型" width="130">
              <template #default="{ row }">
                <el-select v-model="row.nodeType" size="small">
                  <el-option label="开始" value="START" />
                  <el-option label="动作" value="TASK" />
                  <el-option label="状态" value="STATE" />
                  <el-option label="结束" value="END" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="责任角色" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.roleCode" size="small" placeholder="如 STORE_SERVICE" />
              </template>
            </el-table-column>
            <el-table-column label="业务状态" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.businessState" size="small" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="流转规则" name="transitions">
          <el-table :data="draftForm.transitions" stripe max-height="360">
            <el-table-column label="动作编码" min-width="180" prop="actionCode" />
            <el-table-column label="动作名称" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.actionName" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="从" width="160" prop="fromNodeCode" />
            <el-table-column label="到" width="160" prop="toNodeCode" />
            <el-table-column label="前置条件" min-width="260">
              <template #default="{ row }">
                <el-input v-model="row.guardRule" size="small" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="触发器" name="triggers">
          <el-table :data="draftForm.triggers" stripe max-height="360">
            <el-table-column label="展示" width="90">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
              </template>
            </el-table-column>
            <el-table-column label="节点" width="150" prop="nodeCode" />
            <el-table-column label="触发器名称" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.triggerName" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="170">
              <template #default="{ row }">
                <el-select v-model="row.triggerType" size="small">
                  <el-option label="调度任务" value="SCHEDULER_JOB" />
                  <el-option label="三方接口" value="THIRD_PARTY_API" />
                  <el-option label="内部服务" value="INTERNAL_SERVICE" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="执行方式" width="140">
              <template #default="{ row }">
                <el-select v-model="row.executionMode" size="small">
                  <el-option label="仅元数据" value="METADATA_ONLY" />
                  <el-option label="人工" value="MANUAL" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="目标编码" min-width="220">
              <template #default="{ row }">
                <el-input v-model="row.targetCode" size="small" placeholder="仅允许元数据编码，如 DOUYIN_VOUCHER_VERIFY" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="差异预览" name="diff">
          <div class="draft-diff-toolbar">
            <el-tag v-if="draftDiffPreview" :type="draftDiffPreview.valid ? 'success' : 'warning'">
              {{ draftDiffPreview.valid ? '后端校验通过' : '后端校验未通过' }}
            </el-tag>
            <el-button size="small" :loading="draftDiffLoading" @click="refreshDraftDiffPreview">刷新预览</el-button>
          </div>
          <el-alert
            v-if="draftDiffPreview && !draftDiffPreview.valid"
            :title="draftDiffPreview.validationMessage || '草稿未通过后端校验'"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else-if="draftDiffError"
            :title="draftDiffError"
            type="error"
            :closable="false"
            show-icon
          />
          <div v-loading="draftDiffLoading" class="draft-diff-list">
            <article v-for="item in draftDiffs" :key="item.key" class="draft-diff-item">
              <strong>{{ item.title }}</strong>
              <span>{{ item.detail }}</span>
              <small>{{ item.impact }}</small>
            </article>
            <el-empty v-if="!draftDiffs.length" description="当前草稿与基准版本暂无差异" />
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <div class="draft-dialog-footer">
          <span>保存生成草稿，发布后成为当前配置版本。</span>
          <div class="action-group">
            <el-button @click="draftDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveDraft">保存草稿</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  disableSystemFlow,
  fetchSystemFlowAuditLogs,
  fetchSystemFlowDetail,
  fetchSystemFlowTriggerLinkageReport,
  fetchSystemFlowRuntimeOverview,
  fetchSystemFlowValidationReport,
  fetchSystemFlows,
  fetchSystemFlowVersions,
  previewSystemFlowDiff,
  publishSystemFlow,
  saveSystemFlowDraft,
  simulateSystemFlow,
  startSystemFlowRuntime
} from '../api/systemFlow'
import { fetchSystemConfigs, saveSystemConfig } from '../api/systemConfig'
import { formatDateTime } from '../utils/format'

const flows = ref([])
const versions = ref([])
const selectedVersionId = ref(null)
const detail = reactive({
  definition: null,
  version: null,
  nodes: [],
  transitions: [],
  triggers: []
})
const auditLogs = ref([])
const validationReport = ref(null)
const triggerLinkageReport = ref(null)
const runtimeOverview = ref(null)
const capabilityConfigs = ref([])
const configLoading = ref(false)
const savingConfigKey = ref('')
const detailTab = ref('transitions')
const runtimeTab = ref('instances')
const draftTab = ref('nodes')
const draftDialogVisible = ref(false)
const simulation = ref(null)
const runtimeProbe = ref(null)
const runtimeStarting = ref(false)
const simulateForm = reactive({
  currentNodeCode: '',
  actionCode: '',
  roleCode: 'ADMIN'
})
const draftForm = reactive({
  flowName: '',
  description: '',
  changeSummary: '',
  nodes: [],
  transitions: [],
  triggers: []
})
const draftDiffPreview = ref(null)
const draftDiffLoading = ref(false)
const draftDiffError = ref('')
const allowedTriggerTargets = new Set([
  'DOUYIN_CLUE_INCREMENTAL',
  'DOUYIN_VOUCHER_VERIFY',
  'ORDER_SETTLEMENT_METADATA',
  'SALARY_SETTLEMENT_METADATA'
])
const roleOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '客资主管', value: 'CLUE_MANAGER' },
  { label: '在线客服', value: 'ONLINE_CUSTOMER_SERVICE' },
  { label: '门店服务', value: 'STORE_SERVICE' },
  { label: '财务', value: 'FINANCE' }
]

const availableActions = computed(() =>
  detail.transitions.filter((item) => !simulateForm.currentNodeCode || item.fromNodeCode === simulateForm.currentNodeCode)
)
const canPublishCurrentVersion = computed(() => String(detail.version?.status || '').toUpperCase() === 'DRAFT')
const draftDiffs = computed(() => formatDiffPreviewItems(draftDiffPreview.value?.items || []))
let draftDiffTimer = null
let draftDiffRequestSeq = 0

function applyDetail(payload) {
  detail.definition = payload?.definition || null
  detail.version = payload?.version || null
  detail.nodes = payload?.nodes || []
  detail.transitions = payload?.transitions || []
  detail.triggers = payload?.triggers || []
  selectedVersionId.value = detail.version?.id || null
  simulateForm.currentNodeCode = detail.nodes[0]?.nodeCode || ''
  simulateForm.actionCode = ''
  simulation.value = null
}

async function loadAll() {
  flows.value = await fetchSystemFlows()
  await loadCapabilityConfigs()
  const firstFlowCode = flows.value[0]?.flowCode || 'ORDER_MAIN_FLOW'
  await loadDetail(firstFlowCode)
}

async function loadCapabilityConfigs() {
  configLoading.value = true
  try {
    const prefixes = ['workflow.', 'deposit.direct', 'amount.visibility', 'form_designer.']
    const results = await Promise.all(prefixes.map((prefix) => fetchSystemConfigs(prefix)))
    capabilityConfigs.value = results.flat()
  } finally {
    configLoading.value = false
  }
}

async function toggleCapabilityConfig(row, value) {
  savingConfigKey.value = row.configKey
  try {
    const saved = await saveSystemConfig({
      ...row,
      configValue: String(Boolean(value)),
      summary: '流程配置页更新能力开关'
    })
    capabilityConfigs.value = capabilityConfigs.value.map((item) => (item.id === saved.id ? saved : item))
    ElMessage.success('能力开关已更新')
  } finally {
    savingConfigKey.value = ''
  }
}

async function loadDetail(flowCode, versionId) {
  const payload = await fetchSystemFlowDetail(flowCode, versionId)
  applyDetail(payload)
  await refreshFlowMeta(payload?.definition?.flowCode || flowCode, payload?.version?.id || versionId)
}

async function handleVersionChange(versionId) {
  if (!detail.definition?.flowCode || !versionId) {
    return
  }
  await loadDetail(detail.definition.flowCode, versionId)
}

async function refreshFlowMeta(flowCode, versionId) {
  const [versionPayload, auditPayload, validationPayload, linkagePayload, runtimePayload] = await Promise.all([
    fetchSystemFlowVersions(flowCode),
    fetchSystemFlowAuditLogs(flowCode),
    fetchSystemFlowValidationReport(flowCode, versionId),
    fetchSystemFlowTriggerLinkageReport(flowCode, versionId),
    fetchSystemFlowRuntimeOverview(flowCode)
  ])
  versions.value = versionPayload
  auditLogs.value = auditPayload
  validationReport.value = validationPayload
  triggerLinkageReport.value = linkagePayload
  runtimeOverview.value = runtimePayload
}

async function publishCurrentVersion() {
  if (!detail.definition || !detail.version) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认发布 v${detail.version.versionNo}？发布后它只会成为当前流程配置展示版本，不会自动执行真实订单、薪酬、调度或三方调用。`,
      '发布流程版本',
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '返回检查',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  applyDetail(await publishSystemFlow({
    flowCode: detail.definition.flowCode,
    versionId: detail.version.id,
    summary: draftForm.changeSummary || detail.version.changeSummary || '页面发布当前流程版本'
  }))
  await refreshFlowMeta(detail.definition.flowCode, detail.version?.id)
  ElMessage.success('流程配置版本已发布，仅用于配置展示和只读试算，不会自动执行真实业务动作')
}

async function disableFlow(row) {
  try {
    await ElMessageBox.confirm(
      `确认停用「${row.flowName}」的配置展示？这不会停掉真实业务接口，也不会影响已存在订单或服务单。`,
      '停用配置展示',
      {
        confirmButtonText: '确认停用',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  await disableSystemFlow({
    flowCode: row.flowCode,
    reason: '页面停用流程配置展示'
  })
  ElMessage.success('流程配置展示已停用，真实业务接口不受影响')
  await loadAll()
}

function openDraftDialog() {
  if (!detail.definition || !detail.version) {
    return
  }
  draftForm.flowName = detail.definition.flowName || ''
  draftForm.description = detail.definition.description || ''
  draftForm.changeSummary = `基于 v${detail.version.versionNo} 调整流程配置`
  draftForm.nodes = cloneFlowItems(detail.nodes)
  draftForm.transitions = cloneFlowItems(detail.transitions)
  draftForm.triggers = cloneFlowItems(detail.triggers)
  draftDiffPreview.value = null
  draftDiffError.value = ''
  draftTab.value = 'nodes'
  draftDialogVisible.value = true
  scheduleDraftDiffPreview()
}

async function saveDraft() {
  const error = validateDraft()
  if (error) {
    ElMessage.warning(error)
    return
  }
  const payload = buildDraftPayload()
  const preview = await refreshDraftDiffPreview()
  if (!preview) {
    draftTab.value = 'diff'
    ElMessage.warning('请先完成后端差异预览')
    return
  }
  if (!preview.valid) {
    draftTab.value = 'diff'
    ElMessage.warning(preview.validationMessage || '草稿未通过后端校验')
    return
  }
  applyDetail(await saveSystemFlowDraft(payload))
  await refreshFlowMeta(detail.definition.flowCode, detail.version?.id)
  draftDialogVisible.value = false
  ElMessage.success('草稿已保存，仅保存配置版本，不改订单、服务单、薪酬或三方接口')
}

async function runSimulation() {
  simulation.value = await simulateSystemFlow({
    flowCode: detail.definition?.flowCode,
    currentNodeCode: simulateForm.currentNodeCode,
    actionCode: simulateForm.actionCode,
    roleCode: simulateForm.roleCode
  })
}

async function startRuntimeProbe() {
  if (!detail.definition) {
    return
  }
  runtimeStarting.value = true
  try {
    const businessId = Date.now()
    runtimeProbe.value = await startSystemFlowRuntime({
      flowCode: detail.definition.flowCode,
      businessObject: 'CONFIG_PROBE',
      businessId,
      startNodeCode: simulateForm.currentNodeCode || detail.nodes[0]?.nodeCode,
      title: `流程沙盒记录 ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`,
      remark: '页面创建沙盒记录，仅用于验证流程旁路记录'
    })
    await refreshFlowMeta(detail.definition.flowCode, detail.version?.id)
    detailTab.value = 'runtime'
    ElMessage.success('沙盒记录已创建，不会改动真实业务单据')
  } finally {
    runtimeStarting.value = false
  }
}

function resetSimulation() {
  simulateForm.currentNodeCode = detail.nodes[0]?.nodeCode || ''
  simulateForm.actionCode = ''
  simulateForm.roleCode = 'ADMIN'
  simulation.value = null
  runtimeProbe.value = null
}

function formatFlowStatus(status) {
  const value = String(status || '').toUpperCase()
  const map = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  }
  return map[value] || status || '--'
}

function formatNodeType(type) {
  const value = String(type || '').toUpperCase()
  const map = {
    START: '开始',
    TASK: '动作',
    STATE: '状态',
    END: '结束'
  }
  return map[value] || type || '--'
}

function formatDomain(domainCode) {
  const value = String(domainCode || '').toUpperCase()
  const map = {
    CLUE: 'Clue',
    CUSTOMER: 'Customer',
    ORDER: 'Order',
    PLANORDER: 'PlanOrder',
    SCHEDULER: 'Scheduler'
  }
  return map[value] || value || '扩展'
}

function formatLinkageStatus(status) {
  const value = String(status || '').toUpperCase()
  const map = {
    LINKED: { label: '已联动', type: 'success' },
    WARNING: { label: '需确认', type: 'warning' },
    BLOCKER: { label: '阻断', type: 'danger' },
    INFO: { label: '提示', type: 'info' }
  }
  return map[value] || { label: status || '--', type: 'info' }
}

function formatLinkageModule(moduleCode) {
  const value = String(moduleCode || '').toUpperCase()
  const map = {
    SCHEDULER: '调度中心',
    THIRD_PARTY_API: '抖音接口',
    ORDER: '订单配置',
    SALARY: '薪酬配置',
    METADATA: '扩展元数据',
    FLOW: '流程配置',
    UNKNOWN: '未知配置'
  }
  return map[value] || moduleCode || '--'
}

function cloneFlowItems(items = []) {
  return JSON.parse(JSON.stringify(items || [])).map((item) => {
    const next = { ...item }
    delete next.id
    return next
  })
}

function buildDraftPayload() {
  return {
    flowCode: detail.definition?.flowCode,
    flowName: draftForm.flowName,
    moduleCode: detail.definition?.moduleCode,
    businessObject: detail.definition?.businessObject,
    description: draftForm.description,
    changeSummary: draftForm.changeSummary,
    nodes: draftForm.nodes,
    transitions: draftForm.transitions,
    triggers: draftForm.triggers
  }
}

function scheduleDraftDiffPreview() {
  if (!draftDialogVisible.value || !detail.definition) {
    return
  }
  if (draftDiffTimer) {
    clearTimeout(draftDiffTimer)
  }
  draftDiffTimer = setTimeout(() => {
    refreshDraftDiffPreview()
  }, 350)
}

async function refreshDraftDiffPreview() {
  if (!draftDialogVisible.value || !detail.definition) {
    return null
  }
  if (draftDiffTimer) {
    clearTimeout(draftDiffTimer)
    draftDiffTimer = null
  }
  const requestSeq = ++draftDiffRequestSeq
  draftDiffLoading.value = true
  draftDiffError.value = ''
  try {
    const preview = await previewSystemFlowDiff(buildDraftPayload())
    if (requestSeq === draftDiffRequestSeq) {
      draftDiffPreview.value = preview
    }
    return preview
  } catch (error) {
    if (requestSeq === draftDiffRequestSeq) {
      draftDiffPreview.value = null
      draftDiffError.value = error?.message || '差异预览失败'
    }
    return null
  } finally {
    if (requestSeq === draftDiffRequestSeq) {
      draftDiffLoading.value = false
    }
  }
}

function validateDraft() {
  if (!String(draftForm.flowName || '').trim()) {
    return '请填写流程名称'
  }
  if (!draftForm.nodes.length) {
    return '流程至少需要一个节点'
  }
  if (!draftForm.transitions.length) {
    return '流程至少需要一条流转规则'
  }
  const nodeCodes = new Set()
  const domains = new Set()
  for (const node of draftForm.nodes) {
    const code = String(node.nodeCode || '').trim()
    if (!code || nodeCodes.has(code)) {
      return '节点编码不能为空且不能重复'
    }
    nodeCodes.add(code)
    if (node.domainCode) {
      domains.add(String(node.domainCode).toUpperCase())
    }
    if (!['CLUE', 'CUSTOMER', 'ORDER', 'PLANORDER'].includes(String(node.domainCode || '').toUpperCase())) {
      return '订单主流程节点只能属于 Clue、Customer、Order、PlanOrder；调度能力请放在触发器元数据中'
    }
    if (!String(node.nodeName || '').trim()) {
      return `节点 ${code} 需要填写节点名称`
    }
  }
  for (const domain of ['CLUE', 'CUSTOMER', 'ORDER', 'PLANORDER']) {
    if (!domains.has(domain)) {
      return `订单主流程必须包含 ${domain} 节点，不能绕过 Clue -> Customer -> Order -> PlanOrder 主链路`
    }
  }
  const orderStates = draftForm.nodes
    .filter((node) => String(node.domainCode || '').toUpperCase() === 'ORDER')
    .map((node) => String(node.businessState || '').toLowerCase())
  const planOrderStates = draftForm.nodes
    .filter((node) => String(node.domainCode || '').toUpperCase() === 'PLANORDER')
    .map((node) => String(node.businessState || '').toLowerCase())
  if (!['paid', 'used'].every((state) => orderStates.includes(state))) {
    return 'Order 节点必须包含 paid、used 两个核心状态'
  }
  if (!['arrived', 'servicing', 'finished'].every((state) => planOrderStates.includes(state))) {
    return 'PlanOrder 节点必须包含 arrived、servicing、finished 三个核心状态'
  }
  if (!draftForm.nodes.some((node) => String(node.nodeType || '').toUpperCase() === 'START')) {
    return '流程必须包含开始节点'
  }
  if (!draftForm.nodes.some((node) => String(node.nodeType || '').toUpperCase() === 'END')) {
    return '流程必须包含结束节点'
  }
  for (const transition of draftForm.transitions) {
    if (!nodeCodes.has(transition.fromNodeCode) || !nodeCodes.has(transition.toNodeCode)) {
      return `流转 ${transition.actionCode || ''} 引用了不存在的节点`
    }
    if (!String(transition.actionCode || '').trim() || !String(transition.actionName || '').trim()) {
      return '流转规则需要填写动作编码和动作名称'
    }
  }
  for (const trigger of draftForm.triggers) {
    if (!nodeCodes.has(String(trigger.nodeCode || '').trim())) {
      return `触发器 ${trigger.triggerName || ''} 引用了不存在的节点`
    }
    const targetCode = String(trigger.targetCode || '').trim()
    const configJson = String(trigger.configJson || '').toLowerCase()
    const executionMode = String(trigger.executionMode || '').toUpperCase()
    if (
      targetCode &&
      (targetCode !== targetCode.toUpperCase() ||
        !/^[A-Z0-9_]+$/.test(targetCode) ||
        (!allowedTriggerTargets.has(targetCode) && !targetCode.endsWith('_METADATA')) ||
        (executionMode && !['MANUAL', 'METADATA_ONLY'].includes(executionMode)) ||
        targetCode.includes(':') ||
        targetCode.includes('.') ||
        targetCode.includes('/') ||
        configJson.includes('http://') ||
        configJson.includes('https://') ||
        configJson.includes('script') ||
        configJson.includes('select ') ||
        configJson.includes('update ') ||
        configJson.includes('delete ') ||
        configJson.includes('insert '))
    ) {
      return '触发器目标只能填写元数据编码，不能填写真实接口、Bean 方法、SQL 或脚本'
    }
  }
  return ''
}

function formatDiffPreviewItems(items = []) {
  return items.map((item, index) => ({
    key: `${item.domain || 'DIFF'}-${item.changeType || 'CHANGE'}-${item.objectCode || index}-${index}`,
    title: `${formatDiffDomain(item.domain)}${formatDiffChangeType(item.changeType)}：${item.title || item.objectCode || '--'}`,
    detail: `${item.beforeValue || '--'} -> ${item.afterValue || '--'}`,
    impact: item.impact || '仅配置预览'
  }))
}

function formatDiffDomain(domain) {
  const value = String(domain || '').toUpperCase()
  const map = {
    DEFINITION: '流程定义',
    NODE: '节点',
    TRANSITION: '流转规则',
    TRIGGER: '触发器'
  }
  return map[value] || value || '配置'
}

function formatDiffChangeType(type) {
  const value = String(type || '').toUpperCase()
  const map = {
    ADD: '新增',
    UPDATE: '调整',
    REMOVE: '移除'
  }
  return map[value] || '变更'
}

watch(
  draftForm,
  () => {
    scheduleDraftDiffPreview()
  },
  { deep: true }
)

watch(draftDialogVisible, (visible) => {
  if (visible) {
    scheduleDraftDiffPreview()
    return
  }
  if (draftDiffTimer) {
    clearTimeout(draftDiffTimer)
    draftDiffTimer = null
  }
  draftDiffPreview.value = null
  draftDiffError.value = ''
})

loadAll()
</script>

<style scoped>
.system-flow-page {
  --flow-accent: #1d6f5f;
}

.flow-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  background:
    radial-gradient(circle at top right, rgba(29, 111, 95, 0.12), transparent 32%),
    #ffffff;
}

.flow-hero h3 {
  margin: 6px 0;
  color: #102a24;
  font-size: 24px;
}

.flow-hero__eyebrow {
  color: var(--flow-accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.flow-boundary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.flow-hero__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(110px, 1fr));
  gap: 10px;
  min-width: 390px;
}

.flow-hero__stats--inline {
  min-width: 0;
  margin-bottom: 12px;
}

.flow-hero__stats article {
  padding: 12px;
  border: 1px solid #dfe8e3;
  border-radius: 16px;
  background: #f8fbf9;
}

.flow-hero__stats span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.flow-hero__stats strong {
  display: block;
  margin-top: 4px;
  color: #102a24;
  font-size: 20px;
}

.flow-version-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #eff8f4;
  color: var(--flow-accent);
}

.flow-version-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.flow-version-select {
  width: 190px;
}

.flow-lane {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.flow-node-card {
  position: relative;
  display: grid;
  gap: 4px;
  min-height: 112px;
  padding: 16px;
  border: 1px solid #dfe8e3;
  border-radius: 18px;
  background: linear-gradient(145deg, #ffffff, #f5faf7);
  box-shadow: 0 10px 24px rgba(42, 84, 66, 0.08);
}

.flow-node-card__index {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--flow-accent);
  color: #fff;
  font-size: 12px;
}

.flow-node-card strong {
  color: #1e3029;
}

.flow-node-card span,
.flow-node-card small {
  color: #6e7f76;
}

.flow-tabs {
  margin-top: 4px;
}

.flow-validation-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: #64748b;
}

.simulation-result {
  display: grid;
  gap: 4px;
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #fff7ed;
  color: #9a3412;
}

.simulation-result--ok {
  background: #ecfdf5;
  color: #047857;
}

.draft-basic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin: 16px 0;
}

.draft-basic-grid label {
  display: grid;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.draft-basic-grid__wide {
  grid-column: 1 / -1;
}

.draft-tabs {
  margin-top: 8px;
}

.draft-diff-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.draft-diff-list {
  display: grid;
  gap: 10px;
  min-height: 160px;
}

.draft-diff-item {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fbff;
}

.draft-diff-item strong {
  color: #1e3a8a;
}

.draft-diff-item span {
  color: #64748b;
  font-size: 13px;
}

.draft-diff-item small {
  color: #7c8a83;
}

.draft-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #64748b;
}

@media (max-width: 900px) {
  .flow-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .flow-hero__stats {
    min-width: 0;
    width: 100%;
  }

  .draft-basic-grid {
    grid-template-columns: 1fr;
  }

  .draft-dialog-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
