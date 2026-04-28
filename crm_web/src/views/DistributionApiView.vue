<template>
  <div class="stack-page">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>分销接口</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="saveConfig">保存配置</el-button>
          <el-button :loading="testing" @click="testConfig">测试接口</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="platform-tabs">
        <el-tab-pane label="接入配置" name="config">
          <div class="form-grid">
            <div class="full-span form-group-title">应用身份</div>
            <label>
              <span>启用状态</span>
              <el-select v-model="config.enabled">
                <el-option :value="1" label="启用" />
                <el-option :value="0" label="停用" />
              </el-select>
            </label>
            <label>
              <span>运行模式</span>
              <el-select v-model="config.executionMode">
                <el-option label="模拟" value="MOCK" />
                <el-option label="真实" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>AppId</span>
              <el-input v-model="config.appId" placeholder="分销小程序或渠道应用 AppId" />
            </label>
            <label>
              <span>AppSecret</span>
              <el-input v-model="config.appSecret" type="password" show-password placeholder="分销接口密钥" />
            </label>

            <div class="full-span form-group-title">接口路径</div>
            <label>
              <span>API 域名前缀</span>
              <span class="readonly-prefix">{{ apiBaseUrl }}</span>
            </label>
            <label>
              <span>认证方式</span>
              <el-select v-model="config.authMode">
                <el-option label="签名 + Token" value="SIGN_TOKEN" />
                <el-option label="AppId + Secret" value="APP_SECRET" />
              </el-select>
            </label>
            <label class="full-span">
              <span>客资获取路径</span>
              <el-input v-model="config.leadPullPath" placeholder="/open/distribution/leads" />
            </label>
            <label class="full-span">
              <span>订单状态回调路径</span>
              <el-input v-model="config.orderCallbackPath" placeholder="/open/distribution/order-callback" />
            </label>
            <label>
              <span>限流策略</span>
              <el-input v-model="config.rateLimit" placeholder="如 60 次/分钟" />
            </label>
            <label>
              <span>缓存策略</span>
              <el-input v-model="config.cachePolicy" placeholder="如 30 秒缓存" />
            </label>
          </div>
        </el-tab-pane>

        <el-tab-pane label="输出字段" name="fields">
          <el-table :data="config.fields" stripe>
            <el-table-column label="字段名" min-width="160" prop="fieldName" />
            <el-table-column label="来源" min-width="180" prop="source" />
            <el-table-column label="说明" min-width="220" prop="description" />
            <el-table-column label="是否必填" width="110">
              <template #default="{ row }">
                <el-tag :type="row.required ? 'success' : 'info'">{{ row.required ? '必填' : '可选' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <div class="endpoint-preview">
        <article>
          <span>客资获取地址</span>
          <strong>{{ leadPullUrl }}</strong>
        </article>
        <article>
          <span>订单回调地址</span>
          <strong>{{ orderCallbackUrl }}</strong>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { debugSchedulerInterface, saveIntegrationProvider } from '../api/scheduler'
import { buildSystemUrl, loadSystemConsoleState, saveSystemConsoleState } from '../utils/systemConsoleStore'

const activeTab = ref('config')
const testing = ref(false)
const state = reactive(loadSystemConsoleState())
const config = reactive({
  enabled: state.distributionApi?.enabled ?? 1,
  executionMode: state.distributionApi?.executionMode || 'MOCK',
  appId: state.distributionApi?.appId || '',
  appSecret: state.distributionApi?.appSecret || '',
  authMode: state.distributionApi?.authMode || 'SIGN_TOKEN',
  leadPullPath: state.distributionApi?.leadPullPath || '/open/distribution/leads',
  orderCallbackPath: state.distributionApi?.orderCallbackPath || '/open/distribution/order-callback',
  rateLimit: state.distributionApi?.rateLimit || '60 次/分钟',
  cachePolicy: state.distributionApi?.cachePolicy || '30 秒缓存',
  fields: state.distributionApi?.fields || [
    { fieldName: 'phone', source: '客资手机号', description: '用于 Clue 去重和 Customer 创建', required: true },
    { fieldName: 'source', source: '固定 distribution', description: '进入 Clue.source，保留 raw_data', required: true },
    { fieldName: 'product_type', source: '团购/定金', description: '用于判断后续 Order.type', required: true },
    { fieldName: 'paid_amount', source: '已付款金额', description: '同步付款客户，进入顾客排档', required: true },
    { fieldName: 'raw_data', source: '三方原始报文', description: '外部数据必须完整保留', required: true }
  ]
})

const apiBaseUrl = computed(() => String(state.domainSettings?.apiBaseUrl || '').trim() || '--')
const leadPullUrl = computed(() => buildSystemUrl(state, 'api', config.leadPullPath))
const orderCallbackUrl = computed(() => buildSystemUrl(state, 'api', config.orderCallbackPath))

async function saveConfig() {
  const nextState = loadSystemConsoleState()
  nextState.distributionApi = JSON.parse(JSON.stringify(config))
  saveSystemConsoleState(nextState)
  await saveIntegrationProvider({
    providerCode: 'DISTRIBUTION',
    providerName: '分销接口',
    moduleCode: 'CLUE',
    executionMode: config.executionMode,
    authType: config.authMode,
    appId: config.appId,
    clientSecret: config.appSecret,
    endpointPath: config.leadPullPath,
    callbackUrl: orderCallbackUrl.value,
    enabled: config.enabled,
    remark: `限流：${config.rateLimit || '--'}；缓存：${config.cachePolicy || '--'}`
  })
  ElMessage.success('分销接口配置已保存')
}

async function testConfig() {
  testing.value = true
  try {
    await debugSchedulerInterface({
      mode: config.executionMode,
      providerCode: 'DISTRIBUTION',
      interfaceCode: 'DISTRIBUTION_LEAD_PULL',
      requestMethod: 'POST',
      path: config.leadPullPath,
      payload: JSON.stringify({
        app_id: config.appId || 'mock-distribution-app',
        page: 1,
        page_size: 30,
        fields: config.fields.map((item) => item.fieldName)
      })
    })
    ElMessage.success('分销接口测试完成')
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.readonly-prefix {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 0 12px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #475569;
  word-break: break-all;
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

@media (max-width: 900px) {
  .endpoint-preview {
    grid-template-columns: 1fr;
  }
}
</style>
