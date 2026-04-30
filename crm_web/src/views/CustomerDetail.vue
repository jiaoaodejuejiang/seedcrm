<template>
  <div class="stack-page customer-detail-page" v-loading="loading">
    <section v-if="loadError" class="panel state-panel">
      <el-empty :description="loadError">
        <el-button type="primary" @click="router.back()">返回上一页</el-button>
      </el-empty>
    </section>

    <template v-else-if="profile">
      <section class="panel customer-hero">
        <div class="customer-main">
          <el-button v-if="fromMembers" class="back-link" link type="primary" @click="returnToMembers">返回会员信息</el-button>
          <span class="eyebrow">客户详情</span>
          <h2>{{ customer.name || '未命名客户' }}</h2>
          <div class="customer-meta">
            <span>{{ customer.phone || '--' }}</span>
            <span>{{ customer.wechat || '未填写微信' }}</span>
            <span>{{ sourceLabel(customer.sourceChannel) }}</span>
          </div>
        </div>
        <div class="customer-status">
          <el-tag :type="customer.wecomBound ? 'success' : 'info'" effect="light">
            {{ customer.wecomBound ? '已绑定企微' : '未绑定企微' }}
          </el-tag>
          <el-tag effect="plain">{{ customer.status || '未标记状态' }}</el-tag>
        </div>
      </section>

      <section class="detail-layout">
        <article class="panel compact-card">
          <h3>基础信息</h3>
          <dl class="info-list">
            <div>
              <dt>姓名</dt>
              <dd>{{ customer.name || '--' }}</dd>
            </div>
            <div>
              <dt>手机号</dt>
              <dd>{{ customer.phone || '--' }}</dd>
            </div>
            <div>
              <dt>微信</dt>
              <dd>{{ customer.wechat || '--' }}</dd>
            </div>
            <div>
              <dt>来源</dt>
              <dd>{{ sourceLabel(customer.sourceChannel) }}</dd>
            </div>
          </dl>
        </article>

        <article class="panel compact-card">
          <h3>标签</h3>
          <div class="chip-row">
            <el-tag v-for="tag in profile.tagDetails || []" :key="tag" effect="plain" type="success">{{ tag }}</el-tag>
            <span v-if="!(profile.tagDetails || []).length" class="text-secondary">暂无标签</span>
          </div>
        </article>

        <article class="panel compact-card">
          <h3>企微关系</h3>
          <dl class="info-list">
            <div>
              <dt>外部联系人</dt>
              <dd>{{ customer.wecomExternalUserid || '--' }}</dd>
            </div>
            <div>
              <dt>员工企微账号</dt>
              <dd>{{ customer.wecomUserId || '--' }}</dd>
            </div>
          </dl>
          <el-button size="small" type="primary" :disabled="!customer.wecomBound" @click="messageDialogVisible = true">
            发送企微消息
          </el-button>
        </article>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>订单历史</h3>
          </div>
          <el-tag effect="light">{{ pagination.total }} 单</el-tag>
        </div>

        <el-table
          :data="pagination.rows"
          stripe
          class="crm-table"
          empty-text="暂无订单记录"
          :row-class-name="orderRowClassName"
        >
          <el-table-column label="订单" min-width="180">
            <template #default="{ row }">
              <div class="table-primary">
                <strong>{{ row.orderNo || row.id }}</strong>
                <span>{{ formatDateTime(row.createTime) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag effect="plain">{{ orderTypeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }">
              {{ money(row.amount) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" effect="light">{{ orderStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="服务单" width="130">
            <template #default="{ row }">
              <el-button v-if="row.planOrderId" link type="primary" @click="router.push(`/plan-orders/${row.planOrderId}`)">
                打开服务单
              </el-button>
              <span v-else class="text-secondary">未创建</span>
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

      <section class="detail-layout">
        <article class="panel compact-card">
          <h3>电商绑定</h3>
          <div v-if="(profile.ecomBindings || []).length" class="binding-list">
            <div v-for="item in profile.ecomBindings" :key="`${item.platform}-${item.ecomUserId}`" class="binding-item">
              <strong>{{ item.platform || '--' }}</strong>
              <span>{{ item.ecomUserId || '--' }}</span>
            </div>
          </div>
          <p v-else class="text-secondary">暂无电商账号绑定</p>
        </article>

        <article class="panel compact-card">
          <h3>最近企微日志</h3>
          <div v-if="(profile.recentWecomLogs || []).length" class="binding-list">
            <div v-for="item in profile.recentWecomLogs" :key="`${item.createTime}-${item.message}`" class="binding-item">
              <strong>{{ item.message || item.status || '--' }}</strong>
              <span>{{ formatDateTime(item.createTime) }}</span>
            </div>
          </div>
          <p v-else class="text-secondary">暂无最近日志</p>
        </article>
      </section>
    </template>

    <section v-else-if="!loading" class="panel state-panel">
      <el-empty description="暂无客户详情" />
    </section>

    <el-dialog v-model="messageDialogVisible" title="发送企微消息" width="480px">
      <el-input v-model="messageForm.message" type="textarea" :rows="4" placeholder="输入要发送给该客户的消息内容" />
      <template #footer>
        <el-button @click="messageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSendMessage">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { sendWecomMessage } from '../api/actions'
import { fetchCustomerDetail } from '../api/workbench'
import { useTablePagination } from '../composables/useTablePagination'
import { formatDateTime, normalize, statusTagType } from '../utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const profile = ref(null)
const loadError = ref('')
const messageDialogVisible = ref(false)
const pagination = useTablePagination(computed(() => profile.value?.orderHistory || []))
const messageForm = reactive({
  message: ''
})

const customer = computed(() => profile.value?.customer || {})
const fromMembers = computed(() => route.query.from === 'private-domain-members')
const highlightedOrderId = computed(() => Number(route.query.orderId || 0))

async function loadProfile(customerId) {
  if (!customerId) {
    profile.value = null
    loadError.value = '客户编号无效'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    profile.value = await fetchCustomerDetail(customerId)
    pagination.reset()
  } catch (error) {
    profile.value = null
    loadError.value = error?.response?.status === 403
      ? '暂无权限查看该客户详情'
      : error?.response?.data?.message || '客户详情加载失败'
  } finally {
    loading.value = false
  }
}

async function handleSendMessage() {
  if (!messageForm.message?.trim()) {
    ElMessage.warning('请先输入消息内容')
    return
  }
  await sendWecomMessage({
    customerId: Number(route.params.id),
    message: messageForm.message.trim()
  })
  ElMessage.success('企微消息已发送')
  messageDialogVisible.value = false
  messageForm.message = ''
  await loadProfile(Number(route.params.id))
}

function money(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

function sourceLabel(value) {
  const labels = {
    DOUYIN: '抖音',
    DISTRIBUTION: '分销',
    DISTRIBUTOR: '分销',
    FORM: '表单',
    MANUAL: '手动'
  }
  return labels[normalize(value)] || value || '--'
}

function orderTypeLabel(value) {
  const labels = {
    1: '定金',
    2: '团购券',
    DEPOSIT: '定金',
    COUPON: '团购券',
    DISTRIBUTION_PRODUCT: '分销商品'
  }
  return labels[normalize(value)] || labels[String(value)] || value || '--'
}

function orderStatusLabel(value) {
  const labels = {
    PAID: '已付款',
    USED: '已核销',
    CANCELLED: '已取消',
    REFUND_PENDING: '退款中',
    REFUNDED: '已退款',
    FINISHED: '已完成',
    SERVICING: '服务中'
  }
  return labels[normalize(value)] || value || '--'
}

function statusTag(value) {
  return statusTagType(value)
}

function orderRowClassName({ row }) {
  return highlightedOrderId.value && Number(row?.id) === highlightedOrderId.value ? 'is-highlight-order' : ''
}

function returnToMembers() {
  router.push({
    path: '/private-domain/members',
    query: {
      sourceTab: route.query.sourceTab || undefined,
      page: route.query.page || undefined,
      phone: route.query.phone || undefined,
      name: route.query.name || undefined,
      externalMemberId: route.query.externalMemberId || undefined
    }
  })
}

watch(
  () => route.params.id,
  (value) => {
    loadProfile(Number(value))
  },
  { immediate: true }
)
</script>

<style scoped>
.customer-detail-page {
  gap: 16px;
}

.customer-hero {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 18px 20px;
}

.customer-main h2 {
  color: #173b33;
  font-size: 24px;
  margin: 4px 0 8px;
}

.back-link {
  margin: 0 0 6px -4px;
}

.eyebrow {
  color: #7c6f57;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.customer-meta,
.customer-status {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.customer-meta span {
  background: #f7f4ec;
  border-radius: 999px;
  color: #4b5563;
  font-size: 13px;
  padding: 5px 10px;
}

.detail-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.compact-card {
  min-height: 150px;
}

.compact-card h3 {
  color: #173b33;
  font-size: 16px;
  margin: 0 0 14px;
}

.info-list {
  display: grid;
  gap: 10px;
  margin: 0 0 14px;
}

.info-list div {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.info-list dt {
  color: #8a8172;
  flex: 0 0 auto;
  font-size: 13px;
}

.info-list dd {
  color: #1f2937;
  font-weight: 700;
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.binding-list {
  display: grid;
  gap: 10px;
}

.binding-item {
  background: #f8faf8;
  border: 1px solid rgba(23, 59, 51, 0.08);
  border-radius: 12px;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
}

.binding-item strong {
  color: #173b33;
}

.binding-item span {
  color: #6b7280;
  font-size: 13px;
}

.state-panel {
  align-items: center;
  display: flex;
  justify-content: center;
  min-height: 360px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.crm-table :deep(.is-highlight-order td) {
  background: #fff7dd !important;
}

@media (max-width: 1100px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .customer-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
