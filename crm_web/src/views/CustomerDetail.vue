<template>
  <div class="stack-page" v-loading="loading">
    <section v-if="profile" class="metrics-row">
      <article class="metric-card">
        <span>客户</span>
        <strong>{{ profile.customer?.name || '未命名客户' }}</strong>
        <small>{{ profile.customer?.phone || '--' }}</small>
      </article>
      <article class="metric-card">
        <span>来源</span>
        <strong>{{ formatChannel(profile.customer?.sourceChannel) }}</strong>
        <small>当前状态：{{ profile.customer?.status || '--' }}</small>
      </article>
      <article class="metric-card">
        <span>企微绑定</span>
        <strong>{{ profile.customer?.wecomBound ? '已绑定' : '未绑定' }}</strong>
        <small>电商绑定数：{{ profile.customer?.ecomBindingCount || 0 }}</small>
      </article>
    </section>

    <section v-if="profile" class="panel">
      <div class="detail-grid">
        <article class="detail-card">
          <h3>基础信息</h3>
          <p>姓名：{{ profile.customer?.name || '--' }}</p>
          <p>手机号：{{ profile.customer?.phone || '--' }}</p>
          <p>微信：{{ profile.customer?.wechat || '--' }}</p>
        </article>
        <article class="detail-card">
          <h3>标签</h3>
          <div class="chip-row">
            <el-tag v-for="tag in profile.tagDetails || []" :key="tag" effect="plain" type="success">{{ tag }}</el-tag>
            <span v-if="!(profile.tagDetails || []).length" class="text-secondary">暂无标签</span>
          </div>
        </article>
        <article class="detail-card">
          <h3>企微状态</h3>
          <p>外部联系人 ID：{{ profile.customer?.wecomExternalUserid || '--' }}</p>
          <p>员工企微 ID：{{ profile.customer?.wecomUserId || '--' }}</p>
          <el-button size="small" type="primary" :disabled="!profile.customer?.wecomBound" @click="messageDialogVisible = true">
            发送企微消息
          </el-button>
        </article>
      </div>
    </section>

    <section v-if="profile" class="panel">
      <div class="panel-heading">
        <div>
          <h3>订单历史</h3>
          <p>客户详情页只读展示下游结果，不绕过主链创建规则。</p>
        </div>
      </div>

      <el-table :data="profile.orderHistory || []" stripe>
        <el-table-column label="订单" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.orderNo }}</strong>
              <span>{{ formatDateTime(row.createTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag>{{ formatOrderType(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            {{ formatMoney(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ formatOrderStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务单" width="120">
          <template #default="{ row }">
            <span v-if="row.planOrderId">#{{ row.planOrderId }}</span>
            <span v-else class="text-secondary">未创建</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <el-button v-if="row.planOrderId" size="small" type="primary" @click="router.push(`/plan-orders/${row.planOrderId}`)">
              打开服务单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="profile" class="panel">
      <div class="detail-grid">
        <article class="detail-card">
          <h3>电商绑定</h3>
          <div v-if="(profile.ecomBindings || []).length" class="binding-list">
            <div v-for="item in profile.ecomBindings" :key="`${item.platform}-${item.ecomUserId}`" class="binding-item">
              <strong>{{ item.platform }}</strong>
              <span>{{ item.ecomUserId }}</span>
            </div>
          </div>
          <p v-else class="text-secondary">暂无电商账号绑定</p>
        </article>
        <article class="detail-card">
          <h3>最近企微日志</h3>
          <div v-if="(profile.recentWecomLogs || []).length" class="binding-list">
            <div v-for="item in profile.recentWecomLogs" :key="`${item.createTime}-${item.message}`" class="binding-item">
              <strong>{{ item.status }}</strong>
              <span>{{ formatDateTime(item.createTime) }}</span>
            </div>
          </div>
          <p v-else class="text-secondary">暂无最近日志</p>
        </article>
      </div>
    </section>

    <el-dialog v-model="messageDialogVisible" title="发送企微消息" width="480px">
      <el-input v-model="messageForm.message" type="textarea" :rows="4" placeholder="输入发送给该绑定客户的消息内容。" />
      <template #footer>
        <el-button @click="messageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSendMessage">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { sendWecomMessage } from '../api/actions'
import { fetchCustomerDetail } from '../api/workbench'
import { formatChannel, formatDateTime, formatMoney, formatOrderStatus, formatOrderType, statusTagType } from '../utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const profile = ref(null)
const messageDialogVisible = ref(false)
const messageForm = reactive({
  message: ''
})

async function loadProfile(customerId) {
  if (!customerId) {
    profile.value = null
    return
  }
  loading.value = true
  try {
    profile.value = await fetchCustomerDetail(customerId)
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

async function handleSendMessage() {
  try {
    await sendWecomMessage({
      customerId: Number(route.params.id),
      message: messageForm.message || undefined
    })
    ElMessage.success('企微消息已发送')
    messageDialogVisible.value = false
    messageForm.message = ''
    await loadProfile(Number(route.params.id))
  } catch {
    // HTTP 层统一处理错误提示。
  }
}

watch(
  () => route.params.id,
  (value) => {
    loadProfile(Number(value))
  },
  { immediate: true }
)

onMounted(() => {
  if (route.params.id) {
    loadProfile(Number(route.params.id))
  }
})
</script>
