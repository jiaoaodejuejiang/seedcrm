<template>
  <div class="stack-page">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>分销接口</h3>
        </div>
        <div class="action-group">
          <el-button @click="loadActiveQueue">刷新当前页</el-button>
          <el-button v-if="canUpdateConfig && activeTab === 'config'" type="primary" @click="saveConfig">保存配置</el-button>
          <el-button v-if="canUpdateConfig && activeTab === 'config'" :loading="testing" @click="testConfig">dry-run 测试入站映射（不入库）</el-button>
          <el-button v-if="canProcessQueues && activeTab === 'reconcile'" :loading="processingStatusCheck" type="warning" plain @click="handleStatusCheck">执行真实状态回查</el-button>
          <el-button v-if="canProcessQueues && activeTab === 'reconcile'" :loading="processingReconcile" type="warning" plain @click="handleReconcilePull">执行真实对账拉取</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="接入配置" name="config">
          <el-alert
            v-if="!canUpdateConfig"
            class="queue-alert"
            type="info"
            show-icon
            :closable="false"
            title="当前角色仅可查看配置和安全日志，不能修改配置、执行入站测试或触发全局批处理。"
          />
          <div class="go-live-overview">
            <div class="go-live-overview__head">
              <div>
                <strong>生产联调信息</strong>
                <span>地址来自【系统设置 / 基础配置 / 域名配置】；Swagger 看接口定义，联调工作台做 dry-run 验证。</span>
              </div>
              <div class="action-group">
                <el-button plain @click="copyText(eventIngestUrl, '分销入站地址')">复制入站地址</el-button>
                <el-button plain @click="openExternal(userGuideUrl)">使用说明</el-button>
                <el-button plain @click="openExternal(integrationGuideUrl)">联调说明</el-button>
                <el-button plain @click="openExternal(deploymentGuideUrl)">上线手册</el-button>
                <el-button plain @click="openExternal(swaggerUiUrl)">查看接口定义</el-button>
                <el-button plain @click="router.push('/settings/integration/debug')">进入联调工作台</el-button>
              </div>
            </div>
            <div class="go-live-chain">
              <span>方案 B 主链路</span>
              <strong>外部分销已支付订单 → Customer → Order(已支付) → PlanOrder → Order used → Outbox 回推</strong>
              <small>已支付分销订单不进入 Clue；SeedCRM 不承接外部分佣、提现和退款资金流。</small>
            </div>
            <div class="go-live-grid">
              <article>
                <span>API 域名</span>
                <strong>{{ apiBaseUrl }}</strong>
                <el-button class="copy-mini" link type="primary" @click="copyText(apiBaseUrl, 'API 域名')">复制</el-button>
              </article>
              <article>
                <span>已支付订单入站</span>
                <strong>{{ eventIngestUrl }}</strong>
                <el-button class="copy-mini" link type="primary" @click="copyText(eventIngestUrl, '已支付订单入站地址')">复制</el-button>
              </article>
              <article>
                <span>履约回推目标</span>
                <strong>{{ config.fulfillmentCallbackUrl || '待配置外部分销系统回调地址' }}</strong>
                <el-button class="copy-mini" link type="primary" @click="copyText(config.fulfillmentCallbackUrl, '履约回推目标')">复制</el-button>
              </article>
              <article>
                <span>状态回查地址</span>
                <strong>{{ statusQueryUrl }}</strong>
                <el-button class="copy-mini" link type="primary" @click="copyText(statusQueryUrl, '状态回查地址')">复制</el-button>
              </article>
              <article>
                <span>对账拉取地址</span>
                <strong>{{ reconcilePullUrl }}</strong>
                <el-button class="copy-mini" link type="primary" @click="copyText(reconcilePullUrl, '对账拉取地址')">复制</el-button>
              </article>
              <article>
                <span>OpenAPI JSON</span>
                <strong>{{ openApiDocsUrl }}</strong>
                <el-button class="copy-mini" link type="primary" @click="copyText(openApiDocsUrl, 'OpenAPI JSON 地址')">复制</el-button>
              </article>
              <article>
                <span>上线检查</span>
                <div class="go-live-tags">
                  <el-tag v-for="item in goLiveChecks" :key="item.label" :type="item.ok ? 'success' : 'warning'" effect="light">
                    {{ item.label }}
                  </el-tag>
                </div>
              </article>
            </div>
          </div>
          <div class="form-grid">
            <div class="full-span form-group-title">应用身份</div>
            <label>
              <span>启用状态</span>
              <el-select v-model="config.enabled" :disabled="!canUpdateConfig">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>运行模式</span>
              <el-select v-model="config.executionMode" :disabled="!canUpdateConfig">
                <el-option label="模拟" value="MOCK" />
                <el-option label="真实" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="config.appId" :disabled="!canUpdateConfig" placeholder="外部分销系统 AppId" />
            </label>
            <label>
              <span>AppSecret</span>
              <el-input v-model="config.appSecret" :disabled="!canUpdateConfig" type="password" show-password placeholder="验签与回推签名密钥" />
            </label>

            <div class="full-span form-group-title">接口地址</div>
            <label class="full-span">
              <span>已支付订单入站地址</span>
              <span class="readonly-prefix">{{ eventIngestUrl }}</span>
            </label>
            <label class="full-span">
              <span>履约状态回推目标</span>
              <el-input v-model="config.fulfillmentCallbackUrl" :disabled="!canUpdateConfig" placeholder="https://partner.example.com/open/crm/fulfillment" />
            </label>
            <label>
              <span>认证方式</span>
              <el-select v-model="config.authMode" :disabled="!canUpdateConfig">
                <el-option label="签名 + 防重复编号" value="SIGN_TOKEN" />
                <el-option label="AppId + Secret" value="APP_SECRET" />
              </el-select>
            </label>
            <label>
              <span>入站路径</span>
              <el-input v-model="config.eventIngestPath" :disabled="!canUpdateConfig" placeholder="/open/distribution/events" />
            </label>
            <label>
              <span>状态回查路径</span>
              <el-input v-model="config.statusQueryPath" :disabled="!canUpdateConfig" placeholder="/open/distribution/orders/status" />
            </label>
            <label>
              <span>对账拉取路径</span>
              <el-input v-model="config.reconciliationPullPath" :disabled="!canUpdateConfig" placeholder="/open/distribution/orders/reconcile" />
            </label>
            <label class="full-span">
              <span>状态映射</span>
              <el-input
                v-model="config.statusMapping"
                :disabled="!canUpdateConfig"
                placeholder="paid=distribution.order.paid,cancelled=distribution.order.cancelled,refund_pending=distribution.order.refund_pending,refunded=distribution.order.refunded"
              />
            </label>
            <label class="full-span order-type-mapping-field">
              <span>订单类型 / SKU 映射</span>
              <el-input
                v-model="config.orderTypeMapping"
                :disabled="!canUpdateConfig"
                type="textarea"
                :rows="11"
                placeholder="配置外部 type、商品 ID、SKU 到内部团购 / 定金的映射"
              />
              <small>
                只映射到内部订单类型：coupon=团购，deposit=定金。strictProductMapping 打开后，带商品或 SKU 的订单必须命中 rules，否则正式入站会进入异常队列。
              </small>
            </label>
            <label>
              <span>限流策略</span>
              <el-input-number v-model="config.rateLimitPerMinute" :disabled="!canUpdateConfig" :min="1" :max="10000" controls-position="right" />
            </label>
            <label>
              <span>缓存策略</span>
              <el-input-number v-model="config.cacheTtlSeconds" :disabled="!canUpdateConfig" :min="0" :max="86400" controls-position="right" />
            </label>
          </div>

          <div class="endpoint-preview">
            <article>
              <span>本系统接收入站</span>
              <strong>{{ eventIngestUrl }}</strong>
            </article>
            <article>
              <span>履约状态异步回推</span>
              <strong>{{ config.fulfillmentCallbackUrl || '待配置外部分销系统回调地址' }}</strong>
            </article>
            <article>
              <span>状态回查</span>
              <strong>{{ config.statusQueryPath || 'MOCK 模式使用本地测试数据，LIVE 模式需配置路径' }}</strong>
            </article>
            <article>
              <span>对账拉取</span>
              <strong>{{ config.reconciliationPullPath || 'MOCK 模式使用本地测试数据，LIVE 模式需配置路径' }}</strong>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="防重复健康" name="health">
          <el-alert
            class="queue-alert"
            :type="idempotencyHealth.healthy ? 'success' : 'warning'"
            show-icon
            :closable="false"
            :title="idempotencyHealthTitle"
          />

          <div class="health-summary">
            <article>
              <span>整体状态</span>
              <strong>{{ formatHealthStatus(idempotencyHealth.status) }}</strong>
            </article>
            <article>
              <span>重复分组</span>
              <strong>{{ idempotencyHealth.duplicateGroupCount || 0 }}</strong>
            </article>
            <article>
              <span>涉及接收记录</span>
              <strong>{{ idempotencyHealth.affectedLogCount || 0 }}</strong>
            </article>
            <article>
              <span>检查范围</span>
              <strong>{{ idempotencyHealth.providerCode || '全部渠道' }}</strong>
            </article>
          </div>

          <div class="queue-toolbar">
            <el-button :loading="healthLoading" type="primary" @click="loadIdempotencyHealth">重新检查</el-button>
            <el-tag v-for="index in idempotencyHealth.indexes || []" :key="index.indexName" :type="index.exists ? 'success' : 'warning'" effect="light">
              {{ index.indexName }}：{{ formatIndexStatus(index.status) }}
            </el-tag>
          </div>

          <div v-if="idempotencyHealth.recommendedActions?.length" class="health-actions">
            <strong>处理建议</strong>
            <span v-for="item in idempotencyHealth.recommendedActions" :key="item">{{ item }}</span>
          </div>

          <el-table
            v-loading="healthLoading"
            :data="idempotencyHealth.duplicateGroups || []"
            stripe
            empty-text="当前未发现重复接收记录"
          >
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="payload-panel">
                  <div class="detail-grid compact-detail">
                    <article>
                      <span>首条记录</span>
                      <strong>{{ row.firstLogId || '--' }}</strong>
                    </article>
                    <article>
                      <span>最新记录</span>
                      <strong>{{ row.latestLogId || '--' }}</strong>
                    </article>
                    <article>
                      <span>最新接收时间</span>
                      <strong>{{ formatDateTime(row.latestReceivedAt) }}</strong>
                    </article>
                    <article>
                      <span>追踪编号样例</span>
                      <strong>{{ (row.sampleTraceIds || []).join(' / ') || '--' }}</strong>
                    </article>
                  </div>
                  <h4>重复接收日志样例</h4>
                  <el-table :data="row.logSamples || []" size="small" border empty-text="暂无样例明细">
                    <el-table-column label="接收记录" width="90" prop="id" />
                    <el-table-column label="追踪编号" min-width="190" prop="traceId" show-overflow-tooltip />
                    <el-table-column label="处理状态" width="120">
                      <template #default="{ row: sample }">{{ formatSchedulerRunStatus(sample.processStatus) }}</template>
                    </el-table-column>
                    <el-table-column label="防重复状态" width="130">
                      <template #default="{ row: sample }">{{ formatIdempotencyStatus(sample.idempotencyStatus) }}</template>
                    </el-table-column>
                    <el-table-column label="内容指纹" min-width="180" prop="bodyHash" show-overflow-tooltip />
                    <el-table-column label="关联订单" width="110" prop="relatedOrderId" />
                    <el-table-column label="接收时间" min-width="170" prop="receivedAt" />
                  </el-table>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="重复类型" width="140">
              <template #default="{ row }">{{ formatDuplicateType(row.duplicateType) }}</template>
            </el-table-column>
            <el-table-column label="渠道" width="130" prop="providerCode" />
            <el-table-column label="重复依据" min-width="260" prop="duplicateKey" show-overflow-tooltip />
            <el-table-column label="重复数量" width="100" prop="duplicateCount" />
            <el-table-column label="建议保留" width="110" prop="retainLogId" />
            <el-table-column label="需核对记录" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.reviewLogIds || []).join(' / ') || '--' }}</template>
            </el-table-column>
            <el-table-column label="治理口径" min-width="300" prop="cleanupStrategy" show-overflow-tooltip />
            <el-table-column label="处理建议" min-width="280" prop="recommendedAction" show-overflow-tooltip />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.sampleTraceIds?.length"
                  size="small"
                  plain
                  @click="copyText(row.sampleTraceIds[0], '追踪编号')"
                >
                  复制样例
                </el-button>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="履约回推队列" name="outbox">
          <div class="queue-toolbar">
            <el-select v-model="outboxStatus" clearable placeholder="按状态筛选" style="width: 180px" @change="loadOutboxEvents">
              <el-option label="待推送" value="PENDING" />
              <el-option label="推送中" value="PROCESSING" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="死信" value="DEAD_LETTER" />
            </el-select>
            <el-input
              v-model="outboxKeyword"
              clearable
              placeholder="外部订单号 / 追踪编号 / 回推目标"
              style="width: 300px"
            />
            <el-button v-if="canProcessQueues" :loading="processingOutbox" type="primary" @click="handleProcessOutbox">立即处理队列</el-button>
            <el-button @click="loadOutboxEvents">刷新</el-button>
          </div>

          <el-table v-loading="outboxLoading" :data="displayOutboxEvents" stripe :empty-text="outboxEmptyText">
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="业务事件" min-width="150">
              <template #default="{ row }">
                <el-tag effect="light">{{ formatOutboxEventType(row.eventType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="外部订单号" min-width="160" prop="externalOrderId" />
            <el-table-column label="追踪编号" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ outboxTraceId(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="queueStatusTag(row.status)">{{ formatQueueStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="重试次数" width="100" prop="retryCount" />
            <el-table-column label="下次重试" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.nextRetryTime) }}</template>
            </el-table-column>
            <el-table-column label="回推目标" min-width="240" prop="destinationUrl" show-overflow-tooltip />
            <el-table-column label="失败原因" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ formatOutboxFailure(row) }}</template>
            </el-table-column>
            <el-table-column label="建议动作" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ outboxRecommendation(row) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button size="small" plain @click="copyOutboxTraceId(row)">复制追踪编号</el-button>
                  <el-button
                    v-if="canTriggerQueues && String(row.status).toUpperCase() !== 'SUCCESS'"
                    size="small"
                    type="primary"
                    plain
                    :loading="retryingOutboxId === row.id"
                    @click="handleRetryOutbox(row)"
                  >
                    重新入队
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="异常队列" name="exceptions">
          <div class="queue-toolbar">
            <el-select v-model="exceptionStatus" clearable placeholder="按状态筛选" style="width: 180px" @change="loadDistributionExceptions">
              <el-option label="待处理" value="OPEN" />
              <el-option label="已重新入队" value="RETRY_QUEUED" />
              <el-option label="已处理" value="HANDLED" />
            </el-select>
            <el-select v-model="exceptionErrorCode" clearable placeholder="按异常类型筛选" style="width: 200px">
              <el-option label="外部订单冲突" value="EXTERNAL_ORDER_CONFLICT" />
              <el-option label="外部状态冲突" value="EXTERNAL_STATUS_CONFLICT" />
              <el-option label="入站处理失败" value="INGEST_FAILED" />
              <el-option label="接口配置异常" value="PROVIDER_INVALID" />
              <el-option label="重复报文冲突" value="DUPLICATE_PAYLOAD_CONFLICT" />
              <el-option label="签名失败" value="SIGNATURE_INVALID" />
              <el-option label="防重复编号缺失" value="IDEMPOTENCY_MISSING" />
            </el-select>
            <el-input
              v-model="exceptionKeyword"
              clearable
              placeholder="外部订单号 / 本地订单号 / 追踪编号"
              style="width: 300px"
            />
            <el-date-picker
              v-model="exceptionDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 260px"
            />
            <el-button v-if="canProcessQueues" :loading="processingExceptions" type="primary" @click="handleProcessExceptions">处理已重新入队异常</el-button>
            <el-button @click="loadDistributionExceptions">刷新</el-button>
          </div>

          <div v-if="exceptionSourceContext" class="source-context">
            <div>
              <span>来源</span>
              <strong>{{ exceptionSourceContext.label }}</strong>
            </div>
            <div class="action-group">
              <el-button size="small" plain @click="clearExceptionFilters">清除筛选</el-button>
              <el-button size="small" type="primary" plain @click="returnToExceptionSource">返回来源</el-button>
            </div>
          </div>

          <el-table v-loading="exceptionLoading" :data="displayDistributionExceptions" stripe class="exception-table" :empty-text="exceptionEmptyText">
            <el-table-column v-if="canUpdateConfig" type="expand" width="44">
              <template #default="{ row }">
                <div class="exception-detail">
                  <div v-if="parseConflictFields(row).length" class="conflict-panel">
                    <h4>冲突字段明细</h4>
                    <el-table :data="parseConflictFields(row)" size="small" border>
                      <el-table-column label="字段" width="120" prop="label" />
                      <el-table-column label="本地值" min-width="180" prop="existingValue" show-overflow-tooltip />
                      <el-table-column label="外部值" min-width="180" prop="incomingValue" show-overflow-tooltip />
                      <el-table-column label="说明" min-width="220" show-overflow-tooltip>
                        <template #default="{ row: conflict }">{{ formatConflictDetail(conflict) }}</template>
                      </el-table-column>
                    </el-table>
                  </div>
                  <div class="detail-grid">
                    <article>
                      <span>异常说明</span>
                      <strong>{{ formatExceptionMessage(row) }}</strong>
                    </article>
                    <article>
                      <span>处理建议</span>
                      <strong>{{ exceptionRecommendation(row) }}</strong>
                    </article>
                    <article>
                      <span>防重复编号</span>
                      <strong>{{ row.idempotencyKey || '--' }}</strong>
                    </article>
                    <article>
                      <span>追踪编号</span>
                      <strong>{{ row.callbackLogTraceId || '--' }}</strong>
                    </article>
                    <article>
                      <span>事件ID</span>
                      <strong>{{ row.eventId || '--' }}</strong>
                    </article>
                    <article>
                      <span>外部会员</span>
                      <strong>{{ row.externalMemberId || '--' }}</strong>
                    </article>
                    <article>
                      <span>会员手机号</span>
                      <strong>{{ row.phone || '--' }}</strong>
                    </article>
                    <article>
                      <span>更新时间</span>
                      <strong>{{ formatDateTime(row.updatedAt) }}</strong>
                    </article>
                    <article>
                      <span>处理备注</span>
                      <strong>{{ row.handleRemark || '--' }}</strong>
                    </article>
                  </div>
                  <div class="process-hint">
                    <strong>建议处理路径</strong>
                    <span>{{ exceptionProcessPath(row) }}</span>
                  </div>
                  <div v-if="row.rawPayload" class="payload-panel">
                    <h4>技术排查信息，仅排障使用</h4>
                    <pre>{{ formatJsonPayload(row.rawPayload) }}</pre>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="异常类型" min-width="150">
              <template #default="{ row }">
                <el-tag :type="exceptionErrorTag(row.errorCode)">{{ formatExceptionErrorCode(row.errorCode) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="外部订单号" min-width="150" prop="externalOrderId" />
            <el-table-column label="本地订单号" min-width="160">
              <template #default="{ row }">{{ row.relatedOrderNo || row.relatedOrderId || '--' }}</template>
            </el-table-column>
            <el-table-column label="冲突字段" min-width="230">
              <template #default="{ row }">
                <div v-if="parseConflictFields(row).length" class="conflict-tags">
                  <el-tag v-for="item in parseConflictFields(row)" :key="item.field" type="danger" effect="plain">
                    {{ item.label }}
                  </el-tag>
                </div>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
            <el-table-column label="处理状态" width="120">
              <template #default="{ row }">
                <el-tag :type="exceptionStatusTag(row.handlingStatus)">{{ formatExceptionStatus(row.handlingStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理建议" min-width="280">
              <template #default="{ row }">{{ exceptionRecommendation(row) }}</template>
            </el-table-column>
            <el-table-column label="追踪编号" min-width="190" prop="callbackLogTraceId" show-overflow-tooltip />
            <el-table-column label="操作" width="290" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button v-if="row.callbackLogTraceId" size="small" plain @click="copyTraceId(row)">
                    复制追踪编号
                  </el-button>
                  <el-button
                    v-if="canProcessQueues && String(row.handlingStatus).toUpperCase() !== 'HANDLED'"
                    size="small"
                    plain
                    :loading="retryingExceptionId === row.id"
                    @click="handleRetryException(row)"
                  >
                    重新入队
                  </el-button>
                  <el-button
                    v-if="canUpdateConfig && String(row.handlingStatus).toUpperCase() !== 'HANDLED'"
                    size="small"
                    type="success"
                    plain
                    :loading="handlingExceptionId === row.id"
                    @click="handleMarkHandled(row)"
                  >
                    标记处理
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="回查对账结果" name="reconcile">
          <div class="queue-toolbar">
            <el-button :loading="dryRunningStatusCheck" plain @click="handleStatusCheckDryRun">状态回查预检（不入库）</el-button>
            <el-button :loading="dryRunningReconcile" plain @click="handleReconcileDryRun">对账拉取预检（不入库）</el-button>
            <el-button v-if="canProcessQueues" :loading="processingStatusCheck" type="warning" plain @click="handleStatusCheck">执行真实状态回查</el-button>
            <el-button v-if="canProcessQueues" :loading="processingReconcile" type="warning" plain @click="handleReconcilePull">执行真实对账拉取</el-button>
            <el-select v-model="reconciliationJobFilter" clearable placeholder="按任务类型筛选" style="width: 180px">
              <el-option label="状态回查" value="DISTRIBUTION_STATUS_CHECK" />
              <el-option label="对账拉取" value="DISTRIBUTION_RECONCILE_PULL" />
            </el-select>
            <el-select v-model="reconciliationResultFilter" clearable placeholder="按结果筛选" style="width: 170px">
              <el-option label="仅失败" value="FAILED" />
              <el-option label="已按新状态处理" value="REPLAYED" />
              <el-option label="无需处理" value="NO_CHANGE" />
            </el-select>
            <el-input
              v-model="reconciliationKeyword"
              clearable
              placeholder="批次 / 外部订单号 / 说明"
              style="width: 280px"
            />
            <el-button :loading="reconciliationHistoryLoading" @click="loadReconciliationHistory">刷新批次记录</el-button>
          </div>

          <div v-if="currentReconciliationContext" class="current-batch">
            <div>
              <span>当前查看</span>
              <strong>{{ currentReconciliationContext }}</strong>
            </div>
            <el-button v-if="reconciliationSummary.failed > 0" type="danger" plain @click="showFailedReconciliationItems">
              查看失败记录
            </el-button>
          </div>

          <div class="reconcile-summary">
            <article class="summary-card">
              <span>{{ reconciliationResults.length ? '本次处理' : '当前批次处理' }}</span>
              <strong>{{ reconciliationSummary.total }}</strong>
            </article>
            <article class="summary-card">
              <span>已按新状态处理</span>
              <strong>{{ reconciliationSummary.replayed }}</strong>
            </article>
            <article class="summary-card">
              <span>无需处理</span>
              <strong>{{ reconciliationSummary.noChange }}</strong>
            </article>
            <article class="summary-card" :class="{ 'is-danger': reconciliationSummary.failed > 0 }" @click="showFailedReconciliationItems">
              <span>失败</span>
              <strong>{{ reconciliationSummary.failed }}</strong>
              <small v-if="reconciliationSummary.failed > 0">点击查看失败</small>
            </article>
          </div>

          <div class="reconcile-history">
            <div class="reconcile-history__heading">
              <strong>历史批次</strong>
              <span>这里显示最近的回查和对账批次；如需改变订单状态，系统会按既定流程处理，不会直接覆盖订单。</span>
            </div>
            <el-table
              v-loading="reconciliationHistoryLoading"
              :data="displayReconciliationHistory"
              height="360"
              stripe
              empty-text="暂无历史批次，请先执行状态回查或对账拉取"
              :row-class-name="reconciliationHistoryRowClassName"
              @row-click="selectReconciliationBatch"
            >
              <el-table-column label="完成时间" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.finishedAt || row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="任务类型" width="120" prop="jobName" />
              <el-table-column label="触发方式" width="110">
                <template #default="{ row }">{{ formatTriggerType(row.triggerType) }}</template>
              </el-table-column>
              <el-table-column label="执行状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="schedulerRunTag(row.status)">{{ formatSchedulerRunStatus(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="总数 / 已处理 / 失败" min-width="160">
                <template #default="{ row }">
                  {{ row.processedCount ?? 0 }} / {{ row.replayedCount ?? 0 }} / {{ row.failedCount ?? 0 }}
                </template>
              </el-table-column>
              <el-table-column label="处理建议" min-width="280" prop="recommendedAction" show-overflow-tooltip />
              <el-table-column label="批次编号" width="110" prop="batchId" />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="selectReconciliationBatch(row)">查看明细</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="failure-samples">
            <div class="reconcile-history__heading">
              <strong>失败样例</strong>
              <span>{{ reconciliationFailureSamples.length ? '优先进入异常队列处理失败订单；修正外部数据或接口配置后重新入队，再刷新批次结果。' : '当前批次无失败记录，无需进入异常队列。' }}</span>
            </div>
            <el-table :data="reconciliationFailureSamples" size="small" stripe empty-text="当前批次无失败记录">
              <el-table-column label="外部订单号" min-width="160" prop="externalOrderId" />
              <el-table-column label="失败原因" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">{{ formatReconciliationMessage(row) }}</template>
              </el-table-column>
              <el-table-column label="处理建议" min-width="280">
                <template #default="{ row }">{{ reconciliationFailureAdvice(row) }}</template>
              </el-table-column>
              <el-table-column label="防重复编号" min-width="190" prop="idempotencyKey" show-overflow-tooltip />
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <div class="action-group">
                    <el-button link type="primary" @click="copyText(row.idempotencyKey || row.externalOrderId, '防重复编号')">复制</el-button>
                    <el-button link type="primary" @click="openExceptionFromReconcile(row)">去异常队列处理</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="reconcile-history__heading detail-heading">
            <strong>{{ reconciliationResults.length ? '本次执行明细' : '批次明细' }}</strong>
            <span v-if="activeReconciliationBatch && !reconciliationResults.length">
              当前批次：{{ activeReconciliationBatch.batchId }}，{{ activeReconciliationBatch.jobName }}
            </span>
          </div>

          <el-table :data="displayReconciliationItems" stripe empty-text="当前批次暂无明细，请刷新批次记录或重新选择批次">
            <el-table-column label="外部订单号" min-width="160" prop="externalOrderId" />
            <el-table-column label="本地订单ID" min-width="120">
              <template #default="{ row }">{{ row.orderId || '--' }}</template>
            </el-table-column>
            <el-table-column label="结果" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'FAILED' ? 'danger' : 'success'">{{ row.status === 'FAILED' ? '失败' : '成功' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理动作" width="150">
              <template #default="{ row }">
                <el-tag :type="reconciliationActionTag(row.action)">{{ formatReconciliationAction(row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="外部状态变化" min-width="210" show-overflow-tooltip>
              <template #default="{ row }">{{ formatDistributionEventType(row.eventType) }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="320" show-overflow-tooltip>
              <template #default="{ row }">{{ formatReconciliationMessage(row) }}</template>
            </el-table-column>
            <el-table-column label="检查时间" min-width="170">
              <template #default="{ row }">{{ formatDateTime(row.checkedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="String(row.status || row.action || '').toUpperCase().includes('FAILED')"
                  link
                  type="primary"
                  @click="openExceptionFromReconcile(row)"
                >
                  去异常队列处理
                </el-button>
                <span v-else class="muted">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="验收样本" name="acceptance">
          <el-alert
            class="queue-alert"
            type="info"
            show-icon
            :closable="false"
            :title="`方案 B 验收样本已就绪 ${acceptanceReadyCount}/${acceptanceSamples.length || 5} 项；本页只读展示，不会修改历史数据。`"
          />
          <div class="queue-toolbar">
            <el-button :loading="acceptanceLoading" type="primary" @click="loadAcceptanceSamples">刷新样本</el-button>
          </div>
          <div v-loading="acceptanceLoading" class="acceptance-grid">
            <article
              v-for="sample in acceptanceSamples"
              :key="sample.sampleType"
              class="acceptance-card"
              :class="{ 'is-ready': sample.ready, 'is-missing': !sample.ready }"
            >
              <div class="acceptance-card__head">
                <strong>{{ sample.title }}</strong>
                <div class="tag-row">
                  <el-tag :type="acceptanceTypeTag(sample)" effect="light">
                    {{ formatAcceptanceSampleType(sample) }}
                  </el-tag>
                  <el-tag :type="sample.ready ? 'success' : 'warning'" effect="light">
                    {{ sample.ready ? '已有样本' : '待生成' }}
                  </el-tag>
                </div>
              </div>
              <p>{{ sample.description }}</p>
              <div class="acceptance-meta">
                <span>状态</span>
                <strong>{{ formatAcceptanceStatus(sample) }}</strong>
                <span>验收位置</span>
                <strong>{{ formatAcceptanceTarget(sample) }}</strong>
                <span>外部订单号</span>
                <strong>{{ sample.externalOrderId || '--' }}</strong>
                <span>记录编号</span>
                <strong>{{ sample.recordId || '--' }}</strong>
                <span>追踪编号</span>
                <strong>{{ sample.traceId || '--' }}</strong>
                <span>发生时间</span>
                <strong>{{ formatDateTime(sample.occurredAt) }}</strong>
              </div>
              <div class="process-hint">
                <strong>验收动作</strong>
                <span>{{ sample.recommendedAction }}</span>
              </div>
              <div class="action-group">
                <el-button v-if="sample.ready" size="small" type="primary" plain @click="openAcceptanceSample(sample)">查看样本</el-button>
                <el-button v-if="sample.traceId" size="small" plain @click="copyText(sample.traceId, '追踪编号')">复制追踪编号</el-button>
              </div>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="字段规范（技术）" name="fields">
          <el-table :data="config.fields" stripe>
            <el-table-column label="字段名" min-width="180" prop="fieldName" />
            <el-table-column label="来源" min-width="220" prop="source" />
            <el-table-column label="说明" min-width="280" prop="description" />
            <el-table-column label="是否必填" width="110">
              <template #default="{ row }">
                <el-tag :type="row.required ? 'success' : 'info'">{{ row.required ? '必填' : '可选' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  debugSchedulerInterface,
  fetchDistributionExceptions,
  fetchIntegrationProviders,
  fetchSchedulerIdempotencyHealth,
  fetchSchedulerGoLiveReadiness,
  fetchSchedulerLogs,
  fetchSchedulerMonitorSummary,
  fetchSchedulerOutboxEvents,
  markDistributionExceptionHandled,
  processDistributionExceptionRetries,
  processDistributionReconciliation,
  processDistributionStatusCheck,
  dryRunDistributionReconciliation,
  dryRunDistributionStatusCheck,
  processSchedulerOutbox,
  retryDistributionException,
  retrySchedulerOutboxEvent,
  saveIntegrationProvider
} from '../api/scheduler'
import { formatDateTime } from '../utils/format'
import { currentUser } from '../utils/auth'
import { syncDomainSettingsFromBackend } from '../utils/domainSettings'
import { buildSystemUrl, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'
import { fetchSystemConfigs, saveSystemConfig } from '../api/systemConfig'

const route = useRoute()
const router = useRouter()
const ORDER_TYPE_MAPPING_CONFIG_KEY = 'distribution.order.type.mapping'
const DEFAULT_ORDER_TYPE_MAPPING = JSON.stringify(
  {
    default: 'coupon',
    strictProductMapping: false,
    aliases: {
      coupon: 'coupon',
      groupbuy: 'coupon',
      voucher: 'coupon',
      团购: 'coupon',
      团购券: 'coupon',
      deposit: 'deposit',
      prepay: 'deposit',
      prepaid: 'deposit',
      定金: 'deposit',
      预付定金: 'deposit'
    },
    rules: [
      {
        ruleId: 'sample-sku-deposit',
        enabled: false,
        providerCode: 'DISTRIBUTION',
        externalSkuId: 'sku_001',
        internalOrderType: 'deposit',
        verificationPolicy: 'DIRECT',
        priority: 10
      }
    ]
  },
  null,
  2
)
const activeTab = ref(resolveInitialTab())
const testing = ref(false)
const outboxLoading = ref(false)
const exceptionLoading = ref(false)
const reconciliationHistoryLoading = ref(false)
const processingOutbox = ref(false)
const processingExceptions = ref(false)
const processingStatusCheck = ref(false)
const processingReconcile = ref(false)
const dryRunningStatusCheck = ref(false)
const dryRunningReconcile = ref(false)
const retryingOutboxId = ref(null)
const retryingExceptionId = ref(null)
const handlingExceptionId = ref(null)
const outboxStatus = ref('')
const outboxKeyword = ref('')
const exceptionStatus = ref('OPEN')
const exceptionErrorCode = ref('')
const exceptionKeyword = ref('')
const exceptionDateRange = ref([])
const outboxEvents = ref([])
const distributionExceptions = ref([])
const reconciliationResults = ref([])
const reconciliationHistory = ref([])
const selectedReconciliationBatchId = ref(null)
const reconciliationJobFilter = ref('')
const reconciliationResultFilter = ref('')
const reconciliationKeyword = ref('')
const healthLoading = ref(false)
const acceptanceLoading = ref(false)
const exceptionSourceContext = ref(null)
const idempotencyHealth = ref({})
const goLiveReadiness = ref({})
const monitorSummary = ref({})
const state = reactive(loadSystemConsoleState())
const config = reactive({
  enabled: state.distributionApi?.enabled ?? 1,
  executionMode: state.distributionApi?.executionMode || 'MOCK',
  appId: state.distributionApi?.appId || '',
  appSecret: state.distributionApi?.appSecret || '',
  authMode: state.distributionApi?.authMode || 'SIGN_TOKEN',
  eventIngestPath: state.distributionApi?.eventIngestPath || '/open/distribution/events',
  fulfillmentCallbackUrl:
    state.distributionApi?.fulfillmentCallbackUrl ||
    state.distributionApi?.callbackUrl ||
    state.distributionApi?.fulfillmentPushUrl ||
    '',
  statusQueryPath: state.distributionApi?.statusQueryPath || '/open/distribution/orders/status',
  reconciliationPullPath: state.distributionApi?.reconciliationPullPath || '/open/distribution/orders/reconcile',
  statusMapping:
    state.distributionApi?.statusMapping ||
    'paid=distribution.order.paid,cancelled=distribution.order.cancelled,refund_pending=distribution.order.refund_pending,refunded=distribution.order.refunded',
  orderTypeMapping: state.distributionApi?.orderTypeMapping || DEFAULT_ORDER_TYPE_MAPPING,
  rateLimitPerMinute: parseIntegerConfig(state.distributionApi?.rateLimitPerMinute, state.distributionApi?.rateLimit, 60),
  cacheTtlSeconds: parseIntegerConfig(state.distributionApi?.cacheTtlSeconds, state.distributionApi?.cachePolicy, 30),
  secretConfigured: Boolean(state.distributionApi?.secretConfigured),
  fields: state.distributionApi?.fields || [
    { fieldName: 'eventType', source: '固定 distribution.order.paid', description: '只有已支付订单允许创建或匹配 Customer + Order(paid)', required: true },
    { fieldName: 'eventId', source: '外部分销事件 ID', description: '用于日志追踪与重复事件识别', required: true },
    { fieldName: 'member.phone', source: '购买会员手机号', description: '用于匹配 Customer，手机号不能作为订单防重复键', required: true },
    { fieldName: 'member.externalMemberId', source: '外部会员 ID', description: '与 partnerCode 共同作为会员身份标识', required: true },
    { fieldName: 'order.externalOrderId', source: '外部订单 ID', description: '与 partnerCode 共同作为订单防重复键', required: true },
    { fieldName: 'order.amount', source: '已支付金额，单位分', description: '入库后转换为 Order 金额，订单状态为已支付', required: true },
    { fieldName: 'rawData', source: '三方原始报文', description: '外部数据必须完整保留，便于追踪、补偿和对账', required: true }
  ]
})

const apiBaseUrl = computed(() => String(state.domainSettings?.apiBaseUrl || '').trim() || '--')
const eventIngestUrl = computed(() => buildSystemUrl(state, 'api', config.eventIngestPath))
const swaggerUiUrl = computed(() => buildSystemUrl(state, 'api', '/swagger-ui.html'))
const openApiDocsUrl = computed(() => buildSystemUrl(state, 'api', '/v3/api-docs/distribution-open-api'))
const statusQueryUrl = computed(() => buildSystemUrl(state, 'api', config.statusQueryPath))
const reconcilePullUrl = computed(() => buildSystemUrl(state, 'api', config.reconciliationPullPath))
const userGuideUrl = '/docs/distribution-user-guide.html'
const integrationGuideUrl = '/docs/distribution-api-integration-guide.html'
const deploymentGuideUrl = '/docs/deployment-runbook.html'
const statusAndReconcileTarget = computed(() => {
  const status = config.statusQueryPath ? statusQueryUrl.value : '状态回查未配置'
  const reconcile = config.reconciliationPullPath ? reconcilePullUrl.value : '对账拉取未配置'
  return `${status} / ${reconcile}`
})
const fallbackGoLiveChecks = computed(() => [
  { label: apiBaseUrl.value !== '--' ? 'API 域名已配置' : 'API 域名待配置', ok: apiBaseUrl.value !== '--' },
  { label: config.executionMode === 'LIVE' ? '真实模式' : '模拟模式', ok: config.executionMode === 'LIVE' },
  { label: config.secretConfigured || String(config.appSecret || '').trim() ? '密钥已配置' : '密钥待配置', ok: config.secretConfigured || String(config.appSecret || '').trim() },
  { label: String(config.fulfillmentCallbackUrl || '').trim() ? '回推目标已配置' : '回推目标待配置', ok: String(config.fulfillmentCallbackUrl || '').trim() },
  { label: config.statusQueryPath && config.reconciliationPullPath ? '回查对账路径已配置' : '回查对账路径待配置', ok: Boolean(config.statusQueryPath && config.reconciliationPullPath) },
  { label: idempotencyHealth.value?.healthy ? '防重复健康' : '防重复待检查', ok: Boolean(idempotencyHealth.value?.healthy) }
])
const goLiveChecks = computed(() => {
  if (goLiveReadiness.value?.checks?.length) {
    return goLiveReadiness.value.checks.map((item) => ({
      label: item.title,
      ok: item.status === 'PASS',
      status: item.status,
      severity: item.severity,
      action: item.recommendedAction
    }))
  }
  return fallbackGoLiveChecks.value
})
const currentRoleCode = computed(() => String(currentUser.value?.roleCode || '').trim().toUpperCase())
const canUpdateConfig = computed(() => ['ADMIN', 'INTEGRATION_ADMIN'].includes(currentRoleCode.value))
const canTriggerQueues = computed(() => ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR'].includes(currentRoleCode.value))
const canProcessQueues = computed(() => ['ADMIN', 'INTEGRATION_ADMIN'].includes(currentRoleCode.value))
const idempotencyHealthTitle = computed(() => {
  if (!idempotencyHealth.value?.status) {
    return '正在检查接口接收日志的防重复约束与历史重复数据。'
  }
  if (idempotencyHealth.value.healthy) {
    return '防重复约束已生效，当前未发现重复接口接收记录。'
  }
  return '发现防重复约束未完全生效或存在历史重复数据，请按处理建议完成数据治理。'
})
const acceptanceSamples = computed(() => monitorSummary.value?.acceptanceSamples || [])
const acceptanceReadyCount = computed(() => acceptanceSamples.value.filter((sample) => sample.ready).length)
const reconciliationSummary = computed(() => {
  const rows = activeReconciliationItems.value || []
  return rows.reduce(
    (summary, row) => {
      summary.total += 1
      const action = String(row.action || '').toUpperCase()
      const status = String(row.status || '').toUpperCase()
      if (action === 'REPLAYED') {
        summary.replayed += 1
      }
      if (action === 'NO_CHANGE') {
        summary.noChange += 1
      }
      if (status === 'FAILED' || action === 'FAILED') {
        summary.failed += 1
      }
      return summary
    },
    { total: 0, replayed: 0, noChange: 0, failed: 0 }
  )
})
const displayReconciliationHistory = computed(() => {
  const jobCode = String(reconciliationJobFilter.value || '').trim().toUpperCase()
  const resultFilter = String(reconciliationResultFilter.value || '').trim().toUpperCase()
  const keyword = normalizeKeyword(reconciliationKeyword.value)
  return (reconciliationHistory.value || []).filter((row) => {
    if (jobCode && row.jobCode !== jobCode) {
      return false
    }
    if (resultFilter && !reconciliationBatchMatchesResult(row, resultFilter)) {
      return false
    }
    if (!keyword) {
      return true
    }
    return [
      row.batchId,
      row.jobName,
      row.status,
      row.resultSummary,
      row.recommendedAction,
      ...(row.items || []).flatMap((item) => [item.externalOrderId, item.orderId, item.partnerCode, item.message, item.idempotencyKey])
    ]
      .map(normalizeKeyword)
      .some((value) => value.includes(keyword))
  })
})
const activeReconciliationBatch = computed(() => {
  if (selectedReconciliationBatchId.value) {
    return reconciliationHistory.value.find((item) => item.batchId === selectedReconciliationBatchId.value) || null
  }
  return reconciliationHistory.value[0] || null
})
const activeReconciliationItems = computed(() => {
  if (reconciliationResults.value.length) {
    return reconciliationResults.value
  }
  return activeReconciliationBatch.value?.items || []
})
const displayReconciliationItems = computed(() => {
  const resultFilter = String(reconciliationResultFilter.value || '').trim().toUpperCase()
  if (!resultFilter) {
    return activeReconciliationItems.value
  }
  return (activeReconciliationItems.value || []).filter((row) => reconciliationItemMatchesResult(row, resultFilter))
})
const reconciliationFailureSamples = computed(() =>
  (activeReconciliationItems.value || [])
    .filter((row) => String(row.status || row.action || '').toUpperCase().includes('FAILED'))
    .slice(0, 5)
)
const currentReconciliationContext = computed(() => {
  if (reconciliationResults.value.length) {
    const first = reconciliationResults.value[0] || {}
    return `本次执行结果，${formatReconciliationJobType(first.jobType)}，共 ${reconciliationResults.value.length} 条`
  }
  const batch = activeReconciliationBatch.value
  if (!batch) {
    return ''
  }
  return `${batch.jobName} / ${batch.batchId} / ${formatDateTime(batch.finishedAt || batch.createdAt)}`
})
const displayOutboxEvents = computed(() => {
  const keyword = normalizeKeyword(outboxKeyword.value)
  if (!keyword) {
    return outboxEvents.value || []
  }
  return (outboxEvents.value || []).filter((row) =>
    [
      row.externalOrderId,
      row.eventKey,
      row.destinationUrl,
      row.lastError,
      outboxTraceId(row)
    ]
      .map(normalizeKeyword)
      .some((value) => value.includes(keyword))
  )
})
const outboxEmptyText = computed(() =>
  outboxStatus.value || outboxKeyword.value ? '没有符合筛选条件的履约回推记录' : '暂无待回推履约'
)
const displayDistributionExceptions = computed(() => {
  const errorCode = String(exceptionErrorCode.value || '').trim().toUpperCase()
  const keyword = normalizeKeyword(exceptionKeyword.value)
  return (distributionExceptions.value || []).filter((row) => {
    if (errorCode && String(row.errorCode || '').toUpperCase() !== errorCode) {
      return false
    }
    if (!withinDateRange(row.createdAt, exceptionDateRange.value)) {
      return false
    }
    if (!keyword) {
      return true
    }
    return [
      row.externalOrderId,
      row.relatedOrderNo,
      row.relatedOrderId,
      row.callbackLogTraceId,
      row.eventId,
      row.idempotencyKey
    ]
      .map(normalizeKeyword)
      .some((value) => value.includes(keyword))
  })
})
const exceptionEmptyText = computed(() => {
  const hasCustomFilter =
    exceptionErrorCode.value ||
    exceptionKeyword.value ||
    (Array.isArray(exceptionDateRange.value) && exceptionDateRange.value.length > 0) ||
    (exceptionStatus.value && exceptionStatus.value !== 'OPEN')
  return hasCustomFilter ? '没有符合筛选条件的异常记录' : '暂无待处理异常'
})

function resolveInitialTab() {
  const tab = String(route.query.tab || '').trim()
  return ['config', 'fields', 'health', 'outbox', 'exceptions', 'reconcile', 'acceptance'].includes(tab) ? tab : 'config'
}

onMounted(async () => {
  await loadDomainSettings()
  await loadDistributionOrderTypeMappingConfig()
  applyRouteFilters()
  loadBackendProviderConfig()
  loadGoLiveReadiness()
  loadActiveQueue()
})

watch(activeTab, (tab) => {
  if (tab === 'outbox' && outboxEvents.value.length === 0) {
    loadOutboxEvents()
  }
  if (tab === 'exceptions' && distributionExceptions.value.length === 0) {
    loadDistributionExceptions()
  }
  if (tab === 'health' && !idempotencyHealth.value?.status) {
    loadIdempotencyHealth()
  }
  if (tab === 'reconcile' && reconciliationHistory.value.length === 0) {
    loadReconciliationHistory()
  }
  if (tab === 'acceptance' && !monitorSummary.value?.generatedAt) {
    loadAcceptanceSamples()
  }
})

watch(
  () => route.query.tab,
  () => {
    activeTab.value = resolveInitialTab()
    applyRouteFilters()
    loadActiveQueue()
  }
)

watch(
  () => [route.query.status, route.query.keyword],
  () => {
    applyRouteFilters()
  }
)

watch(displayReconciliationHistory, (rows) => {
  if (activeTab.value !== 'reconcile' || !rows.length) {
    return
  }
  if (!rows.some((row) => row.batchId === selectedReconciliationBatchId.value)) {
    selectedReconciliationBatchId.value = rows[0].batchId
    reconciliationResults.value = []
  }
})

function loadActiveQueue() {
  if (activeTab.value === 'outbox') {
    return loadOutboxEvents()
  }
  if (activeTab.value === 'exceptions') {
    return loadDistributionExceptions()
  }
  if (activeTab.value === 'health') {
    return loadIdempotencyHealth()
  }
  if (activeTab.value === 'reconcile') {
    return loadReconciliationHistory()
  }
  if (activeTab.value === 'acceptance') {
    return loadAcceptanceSamples()
  }
  return Promise.resolve()
}

function applyRouteFilters() {
  const status = String(route.query.status || '').trim().toUpperCase()
  const keyword = String(route.query.keyword || '').trim()
  if (activeTab.value === 'outbox') {
    outboxStatus.value = ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'DEAD_LETTER'].includes(status) ? status : outboxStatus.value
    outboxKeyword.value = keyword || outboxKeyword.value
  }
  if (activeTab.value === 'exceptions') {
    exceptionStatus.value = ['OPEN', 'RETRY_QUEUED', 'HANDLED'].includes(status) ? status : exceptionStatus.value
    exceptionKeyword.value = keyword || exceptionKeyword.value
    applyRouteSourceContext()
  }
}

function applyRouteSourceContext() {
  const source = String(route.query.source || '').trim()
  if (!source) {
    return
  }
  exceptionSourceContext.value = {
    label: String(route.query.sourceLabel || '外部入口').trim(),
    returnPath: String(route.query.returnPath || '').trim(),
    returnTab: String(route.query.returnTab || '').trim(),
    batchId: String(route.query.batchId || '').trim()
  }
}

function parseIntegerConfig(primary, fallback, defaultValue) {
  const candidates = [primary, fallback]
  for (const value of candidates) {
    if (value === null || typeof value === 'undefined' || value === '') {
      continue
    }
    const matched = String(value).match(/\d+/)
    if (matched) {
      return Number(matched[0])
    }
  }
  return defaultValue
}

async function loadDomainSettings() {
  try {
    const domainSettings = await syncDomainSettingsFromBackend()
    state.domainSettings = {
      ...(state.domainSettings || {}),
      ...domainSettings
    }
  } catch (error) {
    // Keep local fallback values when backend config is unavailable.
  }
}

async function loadDistributionOrderTypeMappingConfig() {
  try {
    const rows = await fetchSystemConfigs('distribution.order.type.')
    const row = rows.find((item) => item.configKey === ORDER_TYPE_MAPPING_CONFIG_KEY)
    if (row?.configValue) {
      config.orderTypeMapping = normalizeOrderTypeMappingJson(row.configValue)
    }
  } catch (error) {
    // Keep the local default when the current role cannot read system settings.
  }
}

async function loadBackendProviderConfig() {
  try {
    const providers = await fetchIntegrationProviders()
    const provider = providers.find((item) => String(item.providerCode || '').toUpperCase() === 'DISTRIBUTION')
    if (!provider) {
      return
    }
    Object.assign(config, {
      enabled: provider.enabled ?? config.enabled,
      executionMode: provider.executionMode || config.executionMode,
      appId: provider.appId || config.appId,
      authMode: provider.authType || config.authMode,
      eventIngestPath: provider.endpointPath || config.eventIngestPath,
      fulfillmentCallbackUrl: provider.callbackUrl || config.fulfillmentCallbackUrl,
      statusQueryPath: provider.statusQueryPath || config.statusQueryPath,
      reconciliationPullPath: provider.reconciliationPullPath || config.reconciliationPullPath,
      statusMapping: provider.statusMapping || config.statusMapping,
      rateLimitPerMinute: parseIntegerConfig(provider.rateLimitPerMinute, config.rateLimitPerMinute, 60),
      cacheTtlSeconds: parseIntegerConfig(provider.cacheTtlSeconds, config.cacheTtlSeconds, 30),
      secretConfigured: Boolean(provider.clientSecretConfigured || provider.clientSecretMasked)
    })
  } catch (error) {
    // The page can still work with local draft settings when the user has no provider permission.
  }
}

async function saveConfig() {
  if (!canUpdateConfig.value) {
    ElMessage.warning('当前角色不能修改分销接口配置')
    return
  }
  let normalizedOrderTypeMapping = DEFAULT_ORDER_TYPE_MAPPING
  try {
    normalizedOrderTypeMapping = normalizeOrderTypeMappingJson(config.orderTypeMapping)
    config.orderTypeMapping = normalizedOrderTypeMapping
  } catch (error) {
    ElMessage.warning(error?.message || '订单类型 / SKU 映射不是有效 JSON')
    return
  }
  if (String(config.executionMode || '').toUpperCase() === 'LIVE') {
    const issues = validateLiveConfig()
    if (issues.length) {
      await ElMessageBox.alert(`真实模式保存前请先补齐：\n${issues.map((item) => `- ${item}`).join('\n')}`, '上线配置未完成', {
        confirmButtonText: '我知道了',
        type: 'warning'
      })
      return
    }
  }
  await saveSystemConfig({
    configKey: ORDER_TYPE_MAPPING_CONFIG_KEY,
    configValue: normalizedOrderTypeMapping,
    valueType: 'JSON',
    scopeType: 'GLOBAL',
    scopeId: 'GLOBAL',
    enabled: 1,
    description: '分销外部订单类型、商品和 SKU 到内部团购 / 定金的映射配置',
    summary: '更新分销订单类型 / SKU 映射'
  })
  const nextState = loadSystemConsoleState()
  nextState.distributionApi = {
    ...JSON.parse(JSON.stringify(config)),
    secretConfigured: config.secretConfigured || Boolean(config.appSecret)
  }
  saveSystemConsoleState(nextState)
  Object.assign(state, nextState)
  await saveIntegrationProvider({
    providerCode: 'DISTRIBUTION',
    providerName: '外部分销系统',
    moduleCode: 'SCHEDULER',
    executionMode: config.executionMode,
    authType: config.authMode,
    appId: config.appId,
    clientSecret: config.appSecret,
    endpointPath: config.eventIngestPath,
    statusQueryPath: config.statusQueryPath,
    reconciliationPullPath: config.reconciliationPullPath,
    statusMapping: config.statusMapping,
    rateLimitPerMinute: config.rateLimitPerMinute,
    cacheTtlSeconds: config.cacheTtlSeconds,
    callbackUrl: config.fulfillmentCallbackUrl,
    enabled: config.enabled,
    remark: `方案B：外部分销已支付订单入站；SeedCRM 只负责预约排档、门店履约和履约状态回推；限流：${config.rateLimitPerMinute || '--'}次/分钟；缓存：${config.cacheTtlSeconds ?? '--'}秒`
  })
  await loadBackendProviderConfig()
  await loadGoLiveReadiness()
  ElMessage.success('分销接口配置已保存')
}

function validateLiveConfig() {
  const issues = []
  if (!String(state.domainSettings?.apiBaseUrl || '').trim()) {
    issues.push('API 域名')
  }
  if (!String(config.eventIngestPath || '').trim()) {
    issues.push('已支付订单入站路径')
  }
  if (!String(config.fulfillmentCallbackUrl || '').trim()) {
    issues.push('外部分销系统履约回推目标')
  }
  if (!String(config.statusQueryPath || '').trim()) {
    issues.push('状态回查路径')
  }
  if (!String(config.reconciliationPullPath || '').trim()) {
    issues.push('对账拉取路径')
  }
  if (!config.secretConfigured && !String(config.appSecret || '').trim()) {
    issues.push('签名密钥 AppSecret')
  }
  if (String(config.authMode || '').toUpperCase() === 'APP_SECRET' && !String(config.appId || '').trim()) {
    issues.push('AppId')
  }
  return issues
}

function normalizeOrderTypeMappingJson(value) {
  const source = String(value || '').trim() || DEFAULT_ORDER_TYPE_MAPPING
  let parsed
  try {
    parsed = JSON.parse(source)
  } catch (error) {
    throw new Error('订单类型 / SKU 映射必须是有效 JSON')
  }
  const assertOrderType = (orderType, fieldName) => {
    const normalized = String(orderType || '').trim().toLowerCase()
    if (!['coupon', 'deposit'].includes(normalized)) {
      throw new Error(`${fieldName} 只能填写 coupon 或 deposit`)
    }
    return normalized
  }
  parsed.default = assertOrderType(parsed.default || 'coupon', 'default')
  if (!parsed.aliases || typeof parsed.aliases !== 'object' || Array.isArray(parsed.aliases)) {
    parsed.aliases = {}
  }
  Object.entries(parsed.aliases).forEach(([key, orderType]) => {
    if (!String(key || '').trim()) {
      throw new Error('aliases 里不能存在空的外部类型')
    }
    parsed.aliases[key] = assertOrderType(orderType, `aliases.${key}`)
  })
  if (!Array.isArray(parsed.rules)) {
    parsed.rules = []
  }
  parsed.rules = parsed.rules.map((rule, index) => {
    if (!rule || typeof rule !== 'object' || Array.isArray(rule)) {
      throw new Error(`rules[${index}] 必须是对象`)
    }
    const enabled = rule.enabled !== false
    if (enabled) {
      rule.internalOrderType = assertOrderType(rule.internalOrderType || rule.orderType || rule.targetOrderType, `rules[${index}].internalOrderType`)
      if (
        !rule.externalProductId &&
        !rule.externalSkuId &&
        !rule.externalOrderType &&
        !rule.externalType &&
        !rule.externalStoreCode &&
        !rule.storeCode
      ) {
        throw new Error(`rules[${index}] 至少填写一个商品、SKU、外部门店或外部类型条件`)
      }
    }
    return {
      ...rule,
      enabled,
      priority: Number.isFinite(Number(rule.priority)) ? Number(rule.priority) : 0
    }
  })
  parsed.strictProductMapping = Boolean(parsed.strictProductMapping)
  return JSON.stringify(parsed, null, 2)
}

function openExternal(url) {
  if (!url || url === '--') {
    ElMessage.warning('地址未配置')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function testConfig() {
  testing.value = true
  try {
    await debugSchedulerInterface({
      mode: config.executionMode,
      providerCode: 'DISTRIBUTION',
      interfaceCode: 'DISTRIBUTION_ORDER_PAID',
      requestMethod: 'POST',
      path: config.eventIngestPath,
      payload: JSON.stringify({
        eventType: 'distribution.order.paid',
        eventId: 'evt_debug_001',
        partnerCode: 'DISTRIBUTION',
        member: {
          externalMemberId: 'm_debug_001',
          name: '测试会员',
          phone: '13800000000',
          role: 'member'
        },
        order: {
          externalOrderId: 'o_debug_001',
          externalTradeNo: 'pay_debug_001',
          type: 'coupon',
          amount: 19900,
          paidAt: new Date().toISOString(),
          status: 'paid'
        },
        rawData: {}
      })
    })
    ElMessage.success('分销入站 dry-run 测试完成，未写入业务表')
  } finally {
    testing.value = false
  }
}

async function loadOutboxEvents() {
  outboxLoading.value = true
  try {
    outboxEvents.value = await fetchSchedulerOutboxEvents(outboxStatus.value || undefined)
  } finally {
    outboxLoading.value = false
  }
}

async function handleProcessOutbox() {
  try {
    await ElMessageBox.confirm(
      '将立即处理待推送/失败的履约回推队列；LIVE 模式下会向外部分销系统发送履约状态，不会修改本地订单。是否继续？',
      '确认处理履约回推队列',
      {
        confirmButtonText: '继续处理',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  processingOutbox.value = true
  try {
    await processSchedulerOutbox(20)
    ElMessage.success('已触发履约回推队列处理')
    await loadOutboxEvents()
  } finally {
    processingOutbox.value = false
  }
}

async function handleRetryOutbox(row) {
  try {
    await ElMessageBox.confirm(
      `将把外部订单 ${row.externalOrderId || '--'} 的履约回推记录重新入队，调度器会再次尝试通知外部分销系统。是否继续？`,
      '确认重新入队',
      {
        confirmButtonText: '重新入队',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  retryingOutboxId.value = row.id
  try {
    await retrySchedulerOutboxEvent(row.id, 'manual retry from distribution api page')
    ElMessage.success('回推事件已重新入队')
    await loadOutboxEvents()
  } finally {
    retryingOutboxId.value = null
  }
}

async function loadDistributionExceptions() {
  exceptionLoading.value = true
  try {
    distributionExceptions.value = await fetchDistributionExceptions(exceptionStatus.value || undefined)
  } finally {
    exceptionLoading.value = false
  }
}

async function loadIdempotencyHealth() {
  healthLoading.value = true
  try {
    idempotencyHealth.value = await fetchSchedulerIdempotencyHealth('DISTRIBUTION')
    await loadGoLiveReadiness()
  } finally {
    healthLoading.value = false
  }
}

async function loadGoLiveReadiness() {
  try {
    goLiveReadiness.value = await fetchSchedulerGoLiveReadiness('DISTRIBUTION')
  } catch (error) {
    goLiveReadiness.value = {}
  }
}

async function loadAcceptanceSamples() {
  acceptanceLoading.value = true
  try {
    monitorSummary.value = await fetchSchedulerMonitorSummary('DISTRIBUTION')
  } finally {
    acceptanceLoading.value = false
  }
}

async function loadReconciliationHistory() {
  reconciliationHistoryLoading.value = true
  try {
    const [statusLogs, reconcileLogs] = await Promise.all([
      fetchSchedulerLogs('DISTRIBUTION_STATUS_CHECK'),
      fetchSchedulerLogs('DISTRIBUTION_RECONCILE_PULL')
    ])
    reconciliationHistory.value = [...(statusLogs || []), ...(reconcileLogs || [])]
      .map(buildReconciliationBatch)
      .sort((left, right) => new Date(right.createdAt || 0).getTime() - new Date(left.createdAt || 0).getTime())
      .slice(0, 80)
    if (!selectedReconciliationBatchId.value && reconciliationHistory.value.length) {
      selectedReconciliationBatchId.value = reconciliationHistory.value[0].batchId
    }
  } finally {
    reconciliationHistoryLoading.value = false
  }
}

async function handleRetryException(row) {
  try {
    await ElMessageBox.confirm(
      `系统将重新按分销订单同步流程处理外部订单 ${row.externalOrderId || '--'}，不会直接覆盖本地订单。请确认外部订单号和本地订单号已核对。`,
      '确认重新入队',
      {
        confirmButtonText: '重新入队',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  retryingExceptionId.value = row.id
  try {
    await retryDistributionException(row.id, 'manual retry from distribution api page')
    ElMessage.success('异常记录已重新入队，调度器会自动重放')
    await loadDistributionExceptions()
  } finally {
    retryingExceptionId.value = null
  }
}

async function handleProcessExceptions() {
  try {
    await ElMessageBox.confirm(
      '系统将处理已重新入队的异常记录，并重新按分销订单同步流程执行。不会直接覆盖本地订单，请确认异常记录已经核对。',
      '确认处理异常重试队列',
      {
        confirmButtonText: '继续处理',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  processingExceptions.value = true
  try {
    await processDistributionExceptionRetries(10)
    ElMessage.success('已触发异常重试队列处理')
    await loadDistributionExceptions()
  } finally {
    processingExceptions.value = false
  }
}

async function handleMarkHandled(row) {
  let remark = ''
  try {
    const result = await ElMessageBox.prompt('请填写处理原因，系统会记录操作者与备注。', '标记异常已处理', {
      confirmButtonText: '确认标记',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：已与外部分销系统核对，外部订单金额已修正，无需再次重试。',
      inputValidator: (value) => Boolean(String(value || '').trim()) || '处理原因不能为空'
    })
    remark = String(result.value || '').trim()
  } catch (error) {
    return
  }
  handlingExceptionId.value = row.id
  try {
    await markDistributionExceptionHandled(row.id, remark)
    ElMessage.success('异常记录已标记处理')
    await loadDistributionExceptions()
  } finally {
    handlingExceptionId.value = null
  }
}

async function handleStatusCheck() {
  try {
    await ElMessageBox.confirm(
      '将按当前分销接口配置执行状态回查；如发现退款/取消等变化，会生成入站事件并走受控重放。是否继续？',
      '确认状态回查',
      {
        confirmButtonText: '执行回查',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  processingStatusCheck.value = true
  try {
    const rows = await processDistributionStatusCheck(20)
    reconciliationResults.value = rows
    selectedReconciliationBatchId.value = null
    activeTab.value = 'reconcile'
    ElMessage.success(`状态回查完成，处理 ${rows.length} 条记录`)
    await loadReconciliationHistory()
  } finally {
    processingStatusCheck.value = false
  }
}

async function handleStatusCheckDryRun() {
  dryRunningStatusCheck.value = true
  try {
    const rows = await dryRunDistributionStatusCheck(20)
    reconciliationResults.value = rows
    selectedReconciliationBatchId.value = null
    activeTab.value = 'reconcile'
    ElMessage.success(`状态回查预检完成，预览 ${rows.length} 条记录，未写核心业务表`)
  } finally {
    dryRunningStatusCheck.value = false
  }
}

async function handleReconcilePull() {
  try {
    await ElMessageBox.confirm(
      '将按当前分销接口配置拉取对账数据；数据变化会转换为入站事件处理，不会直接写核心表。是否继续？',
      '确认对账拉取',
      {
        confirmButtonText: '执行拉取',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  processingReconcile.value = true
  try {
    const rows = await processDistributionReconciliation(20)
    reconciliationResults.value = rows
    selectedReconciliationBatchId.value = null
    activeTab.value = 'reconcile'
    ElMessage.success(`对账拉取完成，处理 ${rows.length} 条记录`)
    await loadReconciliationHistory()
  } finally {
    processingReconcile.value = false
  }
}

async function handleReconcileDryRun() {
  dryRunningReconcile.value = true
  try {
    const rows = await dryRunDistributionReconciliation(20)
    reconciliationResults.value = rows
    selectedReconciliationBatchId.value = null
    activeTab.value = 'reconcile'
    ElMessage.success(`对账拉取预检完成，预览 ${rows.length} 条记录，未写核心业务表`)
  } finally {
    dryRunningReconcile.value = false
  }
}

function buildReconciliationBatch(log) {
  const payload = parseJsonObject(log?.payload) || {}
  const actionCounts = payload.actionCounts || {}
  const jobCode = String(log?.jobCode || payload.jobCode || '').toUpperCase()
  const processedCount = Number(log?.importedCount ?? payload.processedCount ?? payload.importedCount ?? 0)
  const items = Array.isArray(payload.items)
    ? payload.items.map((item) => ({
        ...item,
        checkedAt: log.finishedAt || log.createdAt,
        jobType: jobCode === 'DISTRIBUTION_STATUS_CHECK' ? 'STATUS_CHECK' : 'RECONCILE_PULL'
      }))
    : []
  const failedCount = Number(actionCounts.failed || (String(log?.status || '').toUpperCase() === 'FAILED' ? 1 : 0))
  return {
    batchId: log?.id ? `LOG-${log.id}` : `LOG-${Date.now()}`,
    logId: log?.id,
    jobCode,
    jobName: jobCode === 'DISTRIBUTION_STATUS_CHECK' ? '状态回查' : '对账拉取',
    triggerType: log?.triggerType,
    status: log?.status,
    processedCount,
    replayedCount: Number(actionCounts.replayed || 0),
    noChangeCount: Number(actionCounts.noChange || 0),
    failedCount,
    resultSummary: reconciliationBatchSummary(log, processedCount, failedCount),
    recommendedAction: reconciliationBatchRecommendation(log, failedCount),
    items,
    createdAt: log?.createdAt,
    startedAt: log?.startedAt,
    finishedAt: log?.finishedAt
  }
}

function reconciliationBatchMatchesResult(row, resultFilter) {
  const items = row?.items || []
  if (items.length) {
    return items.some((item) => reconciliationItemMatchesResult(item, resultFilter))
  }
  if (resultFilter === 'FAILED') {
    return Number(row?.failedCount || 0) > 0
  }
  if (resultFilter === 'REPLAYED') {
    return Number(row?.replayedCount || 0) > 0
  }
  if (resultFilter === 'NO_CHANGE') {
    return Number(row?.noChangeCount || 0) > 0
  }
  return true
}

function reconciliationItemMatchesResult(row, resultFilter) {
  const action = String(row?.action || '').toUpperCase()
  const status = String(row?.status || row?.processStatus || '').toUpperCase()
  if (resultFilter === 'FAILED') {
    return action === 'FAILED' || status === 'FAILED'
  }
  if (resultFilter === 'REPLAYED') {
    return action === 'REPLAYED'
  }
  if (resultFilter === 'NO_CHANGE') {
    return action === 'NO_CHANGE'
  }
  return true
}

function reconciliationBatchSummary(log, processedCount, failedCount) {
  if (log?.errorMessage) {
    return log.errorMessage
  }
  if (failedCount > 0) {
    return `本批次处理 ${processedCount} 条，其中 ${failedCount} 条失败`
  }
  if (String(log?.status || '').toUpperCase() === 'SUCCESS') {
    return `本批次处理 ${processedCount} 条，未发现失败`
  }
  return '等待调度执行完成'
}

function reconciliationBatchRecommendation(log, failedCount) {
  const status = String(log?.status || '').toUpperCase()
  if (status === 'FAILED') {
    return '请查看任务失败原因，修正接口配置或外部数据后重新执行'
  }
  if (failedCount > 0) {
    return '请查看批次明细中的失败订单，进入异常队列处理'
  }
  if (status === 'QUEUED' || status === 'RUNNING') {
    return '批次仍在等待或执行中，请稍后刷新历史'
  }
  return '无需处理'
}

function selectReconciliationBatch(row) {
  selectedReconciliationBatchId.value = row?.batchId || null
  reconciliationResults.value = []
}

function reconciliationHistoryRowClassName({ row }) {
  return row?.batchId && row.batchId === selectedReconciliationBatchId.value ? 'selected-reconcile-row' : ''
}

function showFailedReconciliationItems() {
  if (reconciliationSummary.value.failed <= 0) {
    return
  }
  reconciliationResultFilter.value = 'FAILED'
}

function openExceptionFromReconcile(row) {
  exceptionStatus.value = 'OPEN'
  exceptionKeyword.value = row?.externalOrderId || row?.idempotencyKey || ''
  exceptionSourceContext.value = {
    label: `来自回查对账：${currentReconciliationContext.value || '当前批次'}`,
    returnTab: 'reconcile',
    batchId: activeReconciliationBatch.value?.batchId || selectedReconciliationBatchId.value || ''
  }
  activeTab.value = 'exceptions'
  loadDistributionExceptions()
}

function clearExceptionFilters() {
  exceptionStatus.value = 'OPEN'
  exceptionErrorCode.value = ''
  exceptionKeyword.value = ''
  exceptionDateRange.value = []
  exceptionSourceContext.value = null
  router.replace({
    path: route.path,
    query: { tab: 'exceptions' }
  })
  loadDistributionExceptions()
}

function returnToExceptionSource() {
  const source = exceptionSourceContext.value
  if (source?.returnPath) {
    router.push({
      path: source.returnPath,
      query: source.returnTab ? { tab: source.returnTab } : {}
    })
    return
  }
  activeTab.value = source?.returnTab || 'reconcile'
  if (source?.batchId) {
    selectedReconciliationBatchId.value = source.batchId
  }
  if (activeTab.value === 'reconcile') {
    loadReconciliationHistory()
  }
}

function openAcceptanceSample(sample) {
  const tab = sample?.targetTab || 'acceptance'
  if (tab === 'outbox') {
    outboxStatus.value = sample.targetStatus || ''
    outboxKeyword.value = sample.externalOrderId || sample.traceId || ''
    activeTab.value = 'outbox'
    loadOutboxEvents()
    return
  }
  if (tab === 'exceptions') {
    exceptionStatus.value = sample.targetStatus || 'OPEN'
    exceptionKeyword.value = sample.externalOrderId || sample.traceId || ''
    activeTab.value = 'exceptions'
    loadDistributionExceptions()
    return
  }
  if (tab === 'reconcile') {
    reconciliationResultFilter.value = 'FAILED'
    reconciliationKeyword.value = sample.traceId || ''
    activeTab.value = 'reconcile'
    loadReconciliationHistory()
  }
}

function reconciliationFailureAdvice(row) {
  if (row?.externalOrderId) {
    return '进入异常队列核对该外部订单，修正外部数据或接口配置后重新入队。'
  }
  return '请查看调度日志失败原因，修正配置或外部数据后重新执行。'
}

function formatReconciliationMessage(row) {
  const message = String(row?.message || '').trim()
  const normalized = message.toLowerCase()
  if (!message) {
    return '--'
  }
  if (normalized.includes('external order status updated')) {
    return '外部订单状态已按流程处理'
  }
  if (normalized.includes('secret is required')) {
    return '真实模式缺少签名密钥，请先完善分销接口配置'
  }
  if (normalized.includes('baseurl is required')) {
    return '真实模式缺少外部分销系统地址，请先完善分销接口配置'
  }
  if (normalized.includes('live reconciliation call failed')) {
    return '调用外部分销系统失败，请检查接口地址、签名和网络状态'
  }
  if (normalized.includes('serialization failed')) {
    return '请求报文生成失败，请检查接口配置'
  }
  return message
}

function formatTriggerType(value) {
  return (
    {
      AUTO: '自动',
      MANUAL: '手动',
      RETRY: '自动重试',
      MANUAL_RETRY: '手动重试'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function formatSchedulerRunStatus(value) {
  return (
    {
      QUEUED: '等待中',
      RUNNING: '执行中',
      SUCCESS: '成功',
      FAILED: '失败'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function schedulerRunTag(value) {
  return (
    {
      QUEUED: 'warning',
      RUNNING: 'primary',
      SUCCESS: 'success',
      FAILED: 'danger'
    }[String(value || '').toUpperCase()] || 'info'
  )
}

function formatQueueStatus(value) {
  return (
    {
      PENDING: '待推送',
      PROCESSING: '推送中',
      SUCCESS: '成功',
      FAILED: '失败',
      DEAD_LETTER: '死信（需管理员处理）'
    }[String(value || '').toUpperCase()] || (value ? '未知状态' : '--')
  )
}

function formatAcceptanceStatus(sample) {
  if (!sample?.status) {
    return '--'
  }
  if (sample.targetTab === 'outbox') {
    return formatQueueStatus(sample.status)
  }
  if (sample.targetTab === 'exceptions') {
    return formatExceptionStatus(sample.status)
  }
  if (sample.targetTab === 'reconcile') {
    return formatSchedulerRunStatus(sample.status)
  }
  return sample.status
}

function formatAcceptanceSampleType(sample) {
  const type = String(sample?.sampleType || '').toUpperCase()
  return (
    {
      OUTBOX_SUCCESS: '成功样本',
      OUTBOX_FAILED: '失败样本',
      OUTBOX_DEAD_LETTER: '死信样本',
      EXCEPTION_OPEN: '异常样本',
      RECONCILE_FAILED: '对账失败样本'
    }[type] || '验收样本'
  )
}

function acceptanceTypeTag(sample) {
  const type = String(sample?.sampleType || '').toUpperCase()
  if (type === 'OUTBOX_SUCCESS') {
    return 'success'
  }
  if (type === 'OUTBOX_FAILED' || type === 'OUTBOX_DEAD_LETTER' || type === 'RECONCILE_FAILED') {
    return 'danger'
  }
  if (type === 'EXCEPTION_OPEN') {
    return 'warning'
  }
  return 'info'
}

function formatAcceptanceTarget(sample) {
  const tab = String(sample?.targetTab || '').toLowerCase()
  const status = sample?.targetStatus ? ` / ${formatAcceptanceStatus(sample)}` : ''
  return (
    {
      outbox: '履约回推队列',
      exceptions: '异常队列',
      reconcile: '回查对账结果',
      health: '防重复健康'
    }[tab] || '当前页面'
  ) + status
}

function normalizeKeyword(value) {
  return String(value ?? '').trim().toLowerCase()
}

function withinDateRange(value, range) {
  if (!Array.isArray(range) || range.length !== 2 || !range[0] || !range[1]) {
    return true
  }
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) {
    return false
  }
  const start = new Date(range[0])
  start.setHours(0, 0, 0, 0)
  const end = new Date(range[1])
  end.setHours(23, 59, 59, 999)
  return time >= start.getTime() && time <= end.getTime()
}

function formatHealthStatus(value) {
  return (
    {
      HEALTHY: '健康',
      DUPLICATE_DATA: '存在重复数据',
      INDEX_NOT_READY: '索引未生效',
      MISSING_TABLE: '日志表缺失'
    }[String(value || '').toUpperCase()] || (value ? '待检查' : '--')
  )
}

function formatIndexStatus(value) {
  return (
    {
      ACTIVE: '已生效',
      BLOCKED_BY_DUPLICATES: '被重复数据阻塞',
      READY_TO_CREATE: '可创建',
      MISSING_TABLE: '表不存在'
    }[String(value || '').toUpperCase()] || (value ? '待检查' : '--')
  )
}

function formatDuplicateType(value) {
  return (
    {
      IDEMPOTENCY_KEY: '防重复编号',
      EVENT_ID: '事件ID'
    }[String(value || '').toUpperCase()] || value || '--'
  )
}

function formatIdempotencyStatus(value) {
  return (
    {
      NEW: '首次接收',
      DUPLICATE: '重复接收',
      CONFLICT: '重复冲突',
      MISSING: '编号缺失'
    }[String(value || '').toUpperCase()] || (value ? '待确认' : '--')
  )
}

function formatOutboxEventType(value) {
  return (
    {
      'CRM.ORDER.USED': '门店已履约回推',
      'CRM.ORDER.REFUNDED': '退款状态回推'
    }[String(value || '').toUpperCase()] || (value ? '未知业务事件' : '--')
  )
}

function formatDistributionEventType(value) {
  return (
    {
      'DISTRIBUTION.ORDER.PAID': '已支付成交入站',
      'DISTRIBUTION.ORDER.CANCELLED': '外部取消入站',
      'DISTRIBUTION.ORDER.REFUND_PENDING': '外部退款待处理',
      'DISTRIBUTION.ORDER.REFUNDED': '外部已退款入站',
      'CRM.ORDER.USED': '门店已履约回推'
    }[String(value || '').toUpperCase()] || (value ? '未知事件' : '--')
  )
}

function formatOutboxFailure(row) {
  if (!row) {
    return '--'
  }
  if (row.lastError) {
    return row.lastError
  }
  const response = parseJsonObject(row.lastResponse)
  if (response?.message) {
    return response.message
  }
  const status = String(row.status || '').toUpperCase()
  if (status === 'SUCCESS') {
    return '已成功回推'
  }
  if (status === 'PENDING') {
    return '等待调度处理'
  }
  if (status === 'PROCESSING') {
    return '正在推送'
  }
  return '--'
}

function outboxRecommendation(row) {
  const status = String(row?.status || '').toUpperCase()
  if (status === 'PENDING') {
    return '等待自动调度；如需立即同步外部分销系统，可手动处理队列。'
  }
  if (status === 'PROCESSING') {
    return '正在处理，请稍后刷新查看结果。'
  }
  if (status === 'SUCCESS') {
    return '已完成回推，无需操作。'
  }
  if (status === 'FAILED') {
    return '请核对回推目标和签名配置，复制追踪编号排查后可重新入队。'
  }
  if (status === 'DEAD_LETTER') {
    return '已进入死信，请管理员排查配置或外部接口后再重新入队。'
  }
  return '请根据回推状态和失败原因判断下一步。'
}

function queueStatusTag(value) {
  return (
    {
      PENDING: 'warning',
      PROCESSING: 'primary',
      SUCCESS: 'success',
      FAILED: 'danger',
      DEAD_LETTER: 'danger'
    }[String(value || '').toUpperCase()] || 'info'
  )
}

function formatExceptionStatus(value) {
  return (
    {
      OPEN: '待处理',
      RETRY_QUEUED: '已重新入队',
      HANDLED: '已处理'
    }[String(value || '').toUpperCase()] || (value ? '未知状态' : '--')
  )
}

function exceptionStatusTag(value) {
  return (
    {
      OPEN: 'warning',
      RETRY_QUEUED: 'primary',
      HANDLED: 'success'
    }[String(value || '').toUpperCase()] || 'info'
  )
}

function formatExceptionErrorCode(value) {
  return (
    {
      EXTERNAL_ORDER_CONFLICT: '外部订单冲突',
      EXTERNAL_STATUS_CONFLICT: '外部状态冲突',
      INGEST_FAILED: '入站处理失败',
      PROVIDER_INVALID: '接口配置异常',
      DUPLICATE_PAYLOAD_CONFLICT: '重复报文冲突',
      PARTNER_MISMATCH: '渠道不一致',
      NONCE_REPLAYED: '重复请求',
      SIGNATURE_INVALID: '签名失败',
      SIGNATURE_MISSING: '签名缺失',
      PAYLOAD_INVALID: '报文异常',
      IDEMPOTENCY_MISSING: '防重复编号缺失'
    }[String(value || '').toUpperCase()] || (value ? '未知异常' : '--')
  )
}

function exceptionErrorTag(value) {
  return (
    {
      EXTERNAL_ORDER_CONFLICT: 'danger',
      EXTERNAL_STATUS_CONFLICT: 'warning',
      INGEST_FAILED: 'danger',
      PROVIDER_INVALID: 'danger',
      DUPLICATE_PAYLOAD_CONFLICT: 'danger',
      PARTNER_MISMATCH: 'danger',
      NONCE_REPLAYED: 'warning',
      SIGNATURE_INVALID: 'danger',
      SIGNATURE_MISSING: 'warning',
      PAYLOAD_INVALID: 'danger',
      IDEMPOTENCY_MISSING: 'warning'
    }[String(value || '').toUpperCase()] || 'info'
  )
}

function parseConflictFields(row) {
  const code = String(row?.errorCode || '').toUpperCase()
  if (!['EXTERNAL_ORDER_CONFLICT', 'EXTERNAL_STATUS_CONFLICT', 'DUPLICATE_PAYLOAD_CONFLICT'].includes(code)) {
    return []
  }
  const structured = parseConflictDetailJson(row?.conflictDetailJson)
  if (structured.length) {
    return structured
  }
  const message = String(row?.errorMessage || '')
  if (code === 'EXTERNAL_STATUS_CONFLICT') {
    const localStatus = message.match(/local order status\s+([^,\s]+)/i)?.[1]
    const incomingEvent = message.match(/external\s+([^\s]+)\s+received/i)?.[1]
    return [
      {
        field: 'status',
        label: '订单状态',
        existingValue: localStatus || '--',
        incomingValue: incomingEvent || '--',
        detail: message || '外部状态与本地履约状态不一致'
      }
    ]
  }
  const [, conflictPart = message] = message.split('duplicate external order conflict:')
  return conflictPart
    .split(';')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const field = item.split(/\s+/)[0]
      const existingValue = extractConflictValue(item, 'existing')
      const incomingValue = extractConflictValue(item, 'incoming')
      return {
        field,
        label: conflictFieldLabel(field),
        existingValue: existingValue || '--',
        incomingValue: incomingValue || '--',
        detail: item
      }
    })
}

function parseConflictDetailJson(value) {
  if (!value) {
    return []
  }
  try {
    const rows = JSON.parse(value)
    if (!Array.isArray(rows)) {
      return []
    }
    return rows
      .filter((item) => item && typeof item === 'object')
      .map((item) => ({
        field: item.field || '',
        label: item.fieldLabel || conflictFieldLabel(item.field),
        existingValue: item.existingValue ?? '--',
        incomingValue: item.incomingValue ?? '--',
        detail: item.detail || `${item.field || 'field'} existing=${item.existingValue ?? '--'} incoming=${item.incomingValue ?? '--'}`
      }))
  } catch (error) {
    return []
  }
}

function extractConflictValue(text, key) {
  const pattern = new RegExp(`${key}=([^\\s]+)`)
  const matched = String(text || '').match(pattern)
  return matched?.[1] || ''
}

function conflictFieldLabel(field) {
  return (
    {
      externalTradeNo: '支付流水',
      externalMemberId: '外部会员',
      type: '订单类型',
      amount: '订单金额',
      status: '订单状态',
      bodyHash: '报文指纹'
    }[field] || field || '冲突字段'
  )
}

function formatConflictDetail(item) {
  if (!item) {
    return '--'
  }
  if (item.label && (item.existingValue || item.incomingValue)) {
    return `${item.label}不一致：本地 ${item.existingValue || '--'}，外部 ${item.incomingValue || '--'}`
  }
  return item.detail || '--'
}

function formatExceptionMessage(row) {
  const code = String(row?.errorCode || '').toUpperCase()
  const fields = parseConflictFields(row)
  if (code === 'EXTERNAL_ORDER_CONFLICT') {
    if (fields.length) {
      return `外部订单与本地订单存在字段冲突：${fields.map((item) => item.label).join('、')}`
    }
    return '外部订单与本地订单存在字段冲突'
  }
  if (code === 'EXTERNAL_STATUS_CONFLICT') {
    const status = fields[0]
    if (status) {
      return `外部订单状态与本地履约状态不一致：本地 ${status.existingValue || '--'}，外部 ${status.incomingValue || '--'}`
    }
    return '外部订单状态与本地履约状态不一致'
  }
  if (code === 'DUPLICATE_PAYLOAD_CONFLICT') {
    return '同一防重复编号或事件ID对应的回调报文不一致，请核对外部分销系统是否重复推送了不同内容。'
  }
  return row?.errorMessage || '--'
}

function outboxTraceId(row) {
  const response = parseJsonObject(row?.lastResponse)
  return response?.traceId || (row?.id ? `outbox-${row.id}` : row?.eventKey || '--')
}

function formatJsonPayload(value) {
  if (!value) {
    return '--'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (error) {
    return value
  }
}

function parseJsonObject(value) {
  if (!value) {
    return null
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch (error) {
    return null
  }
}

function exceptionRecommendation(row) {
  const code = String(row?.errorCode || '').toUpperCase()
  if (code === 'EXTERNAL_ORDER_CONFLICT') {
    const fields = parseConflictFields(row)
      .map((item) => item.label)
      .join('、')
    return `请核对外部订单${fields ? `的${fields}` : ''}，修正外部数据或配置后重新推送/重新入队；本系统不会覆盖本地订单。`
  }
  if (code === 'EXTERNAL_STATUS_CONFLICT') {
    const status = parseConflictFields(row)?.[0]
    const suffix = status ? `当前本地状态为 ${status.existingValue}，外部状态为 ${status.incomingValue}。` : ''
    return `${suffix}请核对外部退款/取消状态与本地履约状态，确认后重新入队或标记处理；不要绕过门店履约流程。`
  }
  if (code === 'DUPLICATE_PAYLOAD_CONFLICT') {
    return '请核对报文指纹与外部订单内容；同一防重复编号/事件ID不允许承载不同订单内容，需由外部系统重新生成防重复编号或修正数据后再推送。'
  }
  if (code === 'PROVIDER_INVALID' || code === 'SIGNATURE_INVALID' || code === 'SIGNATURE_MISSING') {
    return '请先检查分销接口配置、签名密钥、回调地址和请求头，再重新入队处理。'
  }
  if (code === 'PAYLOAD_INVALID' || code === 'IDEMPOTENCY_MISSING') {
    return '请检查外部回调报文格式、必填字段和防重复编号，修正后重新推送。'
  }
  return '请查看异常说明和处理建议，修正配置或外部数据后重新入队；确认无需处理时再标记处理。'
}

function exceptionProcessPath(row) {
  const status = String(row?.handlingStatus || '').toUpperCase()
  if (status === 'HANDLED') {
    return '该异常已标记处理，请查看处理备注和追踪编号。'
  }
  if (status === 'RETRY_QUEUED') {
    return '等待异常重试队列处理；处理后请刷新状态，失败会回到待处理。'
  }
  if (parseConflictFields(row).length) {
    return '先核对冲突字段；外部数据已修正后点“重新入队”，无需再处理时由管理员“标记处理”。'
  }
  return '先查看异常说明和处理建议；修正配置或外部报文后重新入队，确认无需处理时标记处理。'
}

async function copyTraceId(row) {
  const traceId = row?.callbackLogTraceId
  if (!traceId) {
    return
  }
  await copyText(traceId, '追踪编号')
}

async function copyOutboxTraceId(row) {
  const traceId = outboxTraceId(row)
  if (!traceId || traceId === '--') {
    return
  }
  await copyText(traceId, '追踪编号')
}

async function copyText(text, label) {
  if (!text) {
    ElMessage.warning(`${label}为空，无法复制`)
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch (error) {
    ElMessage.warning(`${label}：${text}`)
  }
}

function formatReconciliationJobType(value) {
  return (
    {
      STATUS_CHECK: '状态回查',
      RECONCILE_PULL: '对账拉取'
    }[String(value || '').toUpperCase()] || (value ? '未知任务' : '--')
  )
}

function formatReconciliationAction(value) {
  return (
    {
      REPLAYED: '已按新状态处理',
      WOULD_REPLAY: '预检会处理',
      DRY_RUN: '仅预检',
      NO_CHANGE: '无需处理',
      FAILED: '处理失败'
    }[String(value || '').toUpperCase()] || (value ? '未知动作' : '--')
  )
}

function reconciliationActionTag(value) {
  return (
    {
      REPLAYED: 'primary',
      WOULD_REPLAY: 'warning',
      DRY_RUN: 'info',
      NO_CHANGE: 'info',
      FAILED: 'danger'
    }[String(value || '').toUpperCase()] || 'info'
  )
}
</script>

<style scoped>
.readonly-prefix {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  padding: 0 12px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #475569;
  word-break: break-all;
}

.order-type-mapping-field small {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.go-live-overview {
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid #cfe3da;
  border-radius: 18px;
  background:
    radial-gradient(circle at 96% 8%, rgba(23, 107, 84, 0.10), transparent 28%),
    #f8fcfa;
}

.go-live-overview__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.go-live-overview__head div:first-child {
  display: grid;
  gap: 5px;
}

.go-live-overview__head strong {
  color: #173b33;
  font-size: 16px;
}

.go-live-overview__head span {
  color: #64748b;
  font-size: 13px;
}

.go-live-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.go-live-chain {
  display: grid;
  gap: 5px;
  padding: 13px 14px;
  border: 1px solid #cfe3da;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.72);
}

.go-live-chain span {
  color: #64748b;
  font-size: 12px;
}

.go-live-chain strong {
  color: #173b33;
  font-size: 14px;
  line-height: 1.5;
}

.go-live-chain small {
  color: #7a5a18;
  font-size: 12px;
  line-height: 1.5;
}

.go-live-grid article {
  display: grid;
  gap: 6px;
  min-height: 76px;
  padding: 13px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.82);
}

.go-live-grid span {
  color: #64748b;
  font-size: 12px;
}

.go-live-grid strong {
  color: #0f172a;
  font-size: 13px;
  line-height: 1.45;
  word-break: break-all;
}

.copy-mini {
  justify-self: start;
  padding: 0;
  min-height: auto;
  font-size: 12px;
}

.go-live-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.endpoint-preview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.endpoint-preview article {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e5edf4;
}

.endpoint-preview span {
  color: #64748b;
  font-size: 13px;
}

.endpoint-preview strong {
  color: #0f172a;
  word-break: break-all;
}

.queue-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.queue-alert {
  margin-bottom: 14px;
  border-radius: 14px;
}

.health-summary,
.reconcile-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.current-batch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid #d8eadf;
  border-radius: 16px;
  background: #f6fbf7;
}

.current-batch div {
  display: grid;
  gap: 5px;
}

.current-batch span {
  color: #64748b;
  font-size: 12px;
}

.current-batch strong {
  color: #173b33;
  font-size: 14px;
}

.source-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding: 11px 14px;
  border: 1px solid #d8eadf;
  border-radius: 14px;
  background: #f6fbf7;
}

.source-context div:first-child {
  display: grid;
  gap: 5px;
}

.source-context span {
  color: #64748b;
  font-size: 12px;
}

.source-context strong {
  color: #173b33;
  font-size: 14px;
}

.health-summary article,
.reconcile-summary article {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #f8fafc;
}

.health-summary span,
.reconcile-summary span {
  color: #64748b;
  font-size: 12px;
}

.health-summary strong,
.reconcile-summary strong {
  color: #0f172a;
  font-size: 20px;
}

.summary-card {
  cursor: default;
}

.summary-card.is-danger {
  cursor: pointer;
  border-color: #fecaca;
  background: #fff7f7;
}

.summary-card.is-danger strong,
.summary-card.is-danger small {
  color: #b91c1c;
}

.summary-card small {
  font-size: 12px;
}

.health-actions {
  display: grid;
  gap: 6px;
  margin-bottom: 14px;
  padding: 13px 14px;
  border: 1px solid #f8d99b;
  border-radius: 14px;
  background: #fff9eb;
  color: #7c4a03;
}

.health-actions strong {
  font-size: 13px;
}

.health-actions span {
  font-size: 13px;
  line-height: 1.5;
}

.acceptance-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.acceptance-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #fff;
}

.acceptance-card.is-ready {
  border-color: #bbdec8;
  background: #f8fcf9;
}

.acceptance-card.is-missing {
  border-color: #f5d59a;
  background: #fffbf0;
}

.acceptance-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.acceptance-card__head strong {
  color: #173b33;
  font-size: 16px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.acceptance-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.acceptance-meta {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 8px 12px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.acceptance-meta span {
  color: #64748b;
  font-size: 12px;
}

.acceptance-meta strong {
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.reconcile-history {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
}

.failure-samples {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #f8d99b;
  border-radius: 16px;
  background: #fffaf0;
}

.reconcile-history__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.reconcile-history__heading strong {
  color: #173b33;
  font-size: 15px;
}

.reconcile-history__heading span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.detail-heading {
  margin-top: 4px;
}

.exception-table :deep(.el-table__expanded-cell) {
  background: #f8fafc;
}

.exception-detail {
  padding: 6px 10px 12px;
}

.conflict-panel {
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #fecaca;
  border-radius: 16px;
  background: #fff7f7;
}

.conflict-panel h4 {
  margin: 0 0 10px;
  color: #991b1b;
  font-size: 14px;
}

.payload-panel {
  margin-top: 14px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #dbe7f3;
  border-radius: 16px;
  background: #f8fbff;
}

.payload-panel h4 {
  margin: 0 0 10px;
  color: #1e3a5f;
  font-size: 14px;
}

.payload-panel pre {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: #334155;
  font-size: 12px;
  line-height: 1.55;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.compact-detail {
  margin-bottom: 14px;
}

.detail-grid article {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}

.detail-grid span {
  color: #64748b;
  font-size: 12px;
}

.detail-grid strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  word-break: break-all;
}

.process-hint {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #f8d99b;
  border-radius: 14px;
  background: #fff9eb;
  color: #7c4a03;
}

.process-hint strong {
  font-size: 13px;
}

.process-hint span {
  font-size: 13px;
  line-height: 1.55;
}

.conflict-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

:deep(.selected-reconcile-row) {
  --el-table-tr-bg-color: #f0f9f4;
}

.muted {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .go-live-overview__head {
    flex-direction: column;
  }

  .go-live-grid {
    grid-template-columns: 1fr;
  }

  .endpoint-preview {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .health-summary,
  .reconcile-summary {
    grid-template-columns: 1fr;
  }

  .acceptance-grid {
    grid-template-columns: 1fr;
  }

  .reconcile-history__heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .current-batch {
    align-items: flex-start;
    flex-direction: column;
  }

  .source-context {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
