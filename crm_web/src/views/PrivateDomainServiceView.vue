<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>授权状态</span>
        <strong>{{ config.authStatus || (config.executionMode === 'LIVE' ? '待配置' : 'MOCK') }}</strong>
        <small>{{ config.enabled === 1 ? '已启用' : '未启用' }}</small>
      </article>
      <article class="metric-card">
        <span>最近回调</span>
        <strong>{{ formatDateTime(config.lastCallbackAt) || '--' }}</strong>
        <small>{{ config.lastCallbackStatus || '未收到' }}</small>
      </article>
      <article class="metric-card">
        <span>触达规则</span>
        <strong>{{ activeRuleCount }}</strong>
        <small>{{ config.lastTokenStatus || '未检测' }}</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>企业微信配置</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" :loading="savingConfig" @click="handleSaveConfig">保存配置</el-button>
          <el-button :loading="testingConfig" @click="handleTestConfig">测试连接</el-button>
        </div>
      </div>

      <div class="status-strip">
        <div class="status-pill">
          <span>Token 检测</span>
          <strong>{{ config.lastTokenStatus || '未检测' }}</strong>
        </div>
        <div class="status-pill">
          <span>回调状态</span>
          <strong>{{ config.lastCallbackStatus || '未收到' }}</strong>
        </div>
        <div class="status-pill">
          <span>授权码时间</span>
          <strong>{{ formatDateTime(config.lastAuthCodeAt) || '--' }}</strong>
        </div>
      </div>

      <div class="form-grid">
        <div class="full-span form-group-title">基础鉴权</div>
        <label>
          <span>应用编码</span>
          <el-input v-model="config.appCode" placeholder="默认 PRIVATE_DOMAIN" />
        </label>
        <label>
          <span>应用 AppId</span>
          <el-input v-model="config.appId" placeholder="可选，企业微信平台应用 ID" />
        </label>
        <label>
          <span>SuiteId</span>
          <el-input v-model="config.suiteId" placeholder="可选，服务商模式使用" />
        </label>
        <label>
          <span>执行模式</span>
          <el-select v-model="config.executionMode">
            <el-option label="MOCK" value="MOCK" />
            <el-option label="LIVE" value="LIVE" />
          </el-select>
        </label>
        <label>
          <span>企业 ID</span>
          <el-input v-model="config.corpId" placeholder="请输入 CorpID" />
        </label>
        <label>
          <span>应用 AgentId</span>
          <el-input v-model="config.agentId" placeholder="请输入 AgentId" />
        </label>
        <label>
          <span>应用 Secret</span>
          <el-input
            v-model="config.appSecret"
            type="password"
            show-password
            :placeholder="config.appSecretMasked || '留空则保持原值'"
          />
        </label>
        <label>
          <span>授权码</span>
          <el-input
            v-model="config.authCode"
            :placeholder="config.authCodeMasked || '由回调自动回填，留空则保持原值'"
          />
        </label>
        <label>
          <span>Access Token</span>
          <el-input
            v-model="config.accessToken"
            type="password"
            show-password
            :placeholder="config.accessTokenMasked || '测试连接或回调后自动写入'"
          />
        </label>
        <label>
          <span>Refresh Token</span>
          <el-input
            v-model="config.refreshToken"
            type="password"
            show-password
            :placeholder="config.refreshTokenMasked || '如有回调返回则自动写入'"
          />
        </label>
        <div class="full-span form-group-title">回调验签</div>
        <label>
          <span>回调地址</span>
          <el-input v-model="config.callbackUrl" placeholder="请输入回调 URL" />
        </label>
        <label>
          <span>回调 Token</span>
          <el-input
            v-model="config.callbackToken"
            type="password"
            show-password
            :placeholder="config.callbackTokenMasked || '留空则保持原值'"
          />
        </label>
        <label>
          <span>EncodingAESKey</span>
          <el-input
            v-model="config.encodingAesKey"
            type="password"
            show-password
            :placeholder="config.encodingAesKeyMasked || '留空则保持原值'"
          />
        </label>
        <label>
          <span>回调状态</span>
          <el-input :model-value="config.lastCallbackStatus || '--'" readonly />
        </label>
        <label>
          <span>回调时间</span>
          <el-input :model-value="formatDateTime(config.lastCallbackAt) || '--'" readonly />
        </label>
        <label class="full-span">
          <span>最近回调结果</span>
          <el-input :model-value="config.lastCallbackMessage || '--'" readonly />
        </label>
        <div class="full-span form-group-title">活码参数</div>
        <label>
          <span>联系我类型</span>
          <el-select v-model="config.liveCodeType">
            <el-option label="单人" :value="1" />
            <el-option label="多人" :value="2" />
          </el-select>
        </label>
        <label>
          <span>联系我场景</span>
          <el-select v-model="config.liveCodeScene">
            <el-option label="小程序联系" :value="1" />
            <el-option label="二维码联系" :value="2" />
          </el-select>
        </label>
        <label>
          <span>活码样式</span>
          <el-input-number v-model="config.liveCodeStyle" :min="1" controls-position="right" />
        </label>
        <label>
          <span>跳过验证</span>
          <el-select v-model="config.skipVerify">
            <el-option label="是" :value="1" />
            <el-option label="否" :value="0" />
          </el-select>
        </label>
        <label class="full-span">
          <span>回调透传标记(state)</span>
          <el-input v-model="config.stateTemplate" placeholder="如 seedcrm:{scene}:{strategy}:{codeName}" />
        </label>
        <label class="full-span">
          <span>来源备注</span>
          <el-input v-model="config.markSource" placeholder="如 客资中心活码轮询" />
        </label>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>最近回调</h3>
        </div>
      </div>

      <el-table :data="callbackLogPagination.rows" stripe>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.receivedAt) || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="应用" min-width="140" prop="appCode" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.processStatus === 'SUCCESS' ? 'success' : row.processStatus === 'FAILED' ? 'danger' : 'info'">
              {{ row.processStatus || '--' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="验签" width="120" prop="signatureStatus" />
        <el-table-column label="事件" min-width="160" prop="eventType" />
        <el-table-column label="结果" min-width="220" prop="processMessage" />
        <el-table-column label="TraceId" min-width="220" prop="traceId" />
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="callbackLogPagination.total"
          :current-page="callbackLogPagination.currentPage"
          :page-size="callbackLogPagination.pageSize"
          :page-sizes="callbackLogPagination.pageSizes"
          @size-change="callbackLogPagination.handleSizeChange"
          @current-change="callbackLogPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>手动触达</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" :loading="sending" @click="handleSend">发送消息</el-button>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>客户 ID</span>
          <el-input-number v-model="sendForm.customerId" :min="1" controls-position="right" />
        </label>
        <label class="full-span">
          <span>发送内容</span>
          <el-input v-model="sendForm.message" type="textarea" :rows="4" placeholder="请输入企业微信触达内容" />
        </label>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>触达规则</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="handleSaveRule">保存规则</el-button>
          <el-button @click="resetRuleForm">重置</el-button>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>规则名称</span>
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </label>
        <label>
          <span>触发场景</span>
          <el-input v-model="ruleForm.triggerType" placeholder="如 APPOINTMENT / COMPLETED" />
        </label>
        <label class="full-span">
          <span>消息模板</span>
          <el-input v-model="ruleForm.messageTemplate" type="textarea" :rows="3" placeholder="请输入触达模板" />
        </label>
      </div>

      <el-table :data="rulePagination.rows" stripe>
        <el-table-column label="规则名称" min-width="180" prop="ruleName" />
        <el-table-column label="触发场景" width="180" prop="triggerType" />
        <el-table-column label="消息模板" min-width="280" prop="messageTemplate" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickRule(row)">编辑</el-button>
              <el-button size="small" plain @click="handleToggleRule(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="rulePagination.total"
          :current-page="rulePagination.currentPage"
          :page-size="rulePagination.pageSize"
          :page-sizes="rulePagination.pageSizes"
          @size-change="rulePagination.handleSizeChange"
          @current-change="rulePagination.handleCurrentChange"
        />
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>最近触达记录</h3>
        </div>
      </div>

      <el-table :data="logPagination.rows" stripe>
        <el-table-column label="发送时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="客户 ID" width="120" prop="customerId" />
        <el-table-column label="企业微信外部 ID" min-width="180" prop="externalUserid" />
        <el-table-column label="内容" min-width="280" prop="message" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="logPagination.total"
          :current-page="logPagination.currentPage"
          :page-size="logPagination.pageSize"
          :page-sizes="logPagination.pageSizes"
          @size-change="logPagination.handleSizeChange"
          @current-change="logPagination.handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchWecomCallbackLogs,
  fetchWecomConfig,
  fetchWecomLogs,
  fetchWecomRules,
  saveWecomConfig,
  saveWecomRule,
  sendWecomMessage,
  testWecomConfig,
  toggleWecomRule
} from '../api/wecom'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime } from '../utils/format'

const sending = ref(false)
const savingConfig = ref(false)
const testingConfig = ref(false)
const rules = ref([])
const logs = ref([])
const callbackLogs = ref([])
const rulePagination = useTablePagination(rules)
const logPagination = useTablePagination(logs)
const callbackLogPagination = useTablePagination(callbackLogs)

const config = reactive(createConfig())
const sendForm = reactive({
  customerId: 301,
  message: '您好，这里是私域客服，欢迎添加企业微信继续咨询。'
})
const ruleForm = reactive(createRuleForm())

const activeRuleCount = computed(() => rules.value.filter((item) => item.isEnabled === 1).length)

onMounted(async () => {
  await Promise.all([loadConfig(), loadRules(), loadLogs()])
})

function createConfig() {
  return {
    id: null,
    appCode: 'PRIVATE_DOMAIN',
    appId: '',
    suiteId: '',
    corpId: '',
    agentId: '',
    appSecret: '',
    appSecretMasked: '',
    authCode: '',
    authCodeMasked: '',
    accessToken: '',
    accessTokenMasked: '',
    refreshToken: '',
    refreshTokenMasked: '',
    executionMode: 'MOCK',
    callbackUrl: '',
    callbackToken: '',
    callbackTokenMasked: '',
    encodingAesKey: '',
    encodingAesKeyMasked: '',
    liveCodeType: 2,
    liveCodeScene: 2,
    liveCodeStyle: 1,
    skipVerify: 1,
    stateTemplate: 'seedcrm:{scene}:{strategy}:{codeName}',
    markSource: '',
    enabled: 1,
    lastTokenStatus: '',
    lastTokenMessage: '',
    authStatus: '',
    lastCallbackStatus: '',
    lastCallbackMessage: '',
    lastAuthCodeAt: '',
    lastCallbackAt: '',
    lastCallbackPayload: ''
  }
}

function createRuleForm() {
  return {
    id: null,
    ruleName: '',
    triggerType: '',
    messageTemplate: '',
    isEnabled: 1
  }
}

function applyConfig(payload) {
  Object.assign(config, createConfig(), payload || {})
  config.appSecret = ''
  config.callbackToken = ''
  config.encodingAesKey = ''
}

function resetRuleForm() {
  Object.assign(ruleForm, createRuleForm())
}

async function loadConfig() {
  const payload = await fetchWecomConfig()
  applyConfig(payload)
  await loadCallbackLogs()
}

async function loadRules() {
  rules.value = await fetchWecomRules()
  rulePagination.reset()
}

async function loadLogs() {
  logs.value = await fetchWecomLogs()
  logPagination.reset()
}

async function loadCallbackLogs() {
  callbackLogs.value = await fetchWecomCallbackLogs(config.appCode || undefined)
  callbackLogPagination.reset()
}

async function handleSaveConfig() {
  savingConfig.value = true
  try {
    const payload = await saveWecomConfig({ ...config })
    applyConfig(payload)
    await loadCallbackLogs()
    ElMessage.success('企业微信配置已保存')
  } finally {
    savingConfig.value = false
  }
}

async function handleTestConfig() {
  testingConfig.value = true
  try {
    const payload = await testWecomConfig({ ...config })
    applyConfig(payload)
    await loadCallbackLogs()
    ElMessage.success(payload.lastTokenMessage || '企业微信连接检测成功')
  } finally {
    testingConfig.value = false
  }
}

async function handleSend() {
  if (!sendForm.customerId || !sendForm.message) {
    ElMessage.warning('请先填写客户 ID 和发送内容')
    return
  }
  sending.value = true
  try {
    await sendWecomMessage({
      customerId: sendForm.customerId,
      message: sendForm.message
    })
    await loadLogs()
    ElMessage.success('企业微信消息已发送')
  } finally {
    sending.value = false
  }
}

async function handleSaveRule() {
  if (!ruleForm.ruleName || !ruleForm.triggerType || !ruleForm.messageTemplate) {
    ElMessage.warning('请先完整填写规则信息')
    return
  }
  await saveWecomRule({
    id: ruleForm.id,
    ruleName: ruleForm.ruleName,
    tag: ruleForm.triggerType,
    triggerType: ruleForm.triggerType,
    messageTemplate: ruleForm.messageTemplate,
    isEnabled: ruleForm.isEnabled
  })
  await loadRules()
  resetRuleForm()
  ElMessage.success('触达规则已保存')
}

function pickRule(row) {
  Object.assign(ruleForm, {
    id: row.id,
    ruleName: row.ruleName,
    triggerType: row.triggerType,
    messageTemplate: row.messageTemplate,
    isEnabled: row.isEnabled ?? 1
  })
}

async function handleToggleRule(row) {
  await toggleWecomRule(row.id)
  await loadRules()
  ElMessage.success('规则状态已更新')
}
</script>
