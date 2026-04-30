<template>
  <div class="login-shell admin-login-shell">
    <section class="login-showcase">
      <div class="login-showcase__brand">
        <p class="brand-mark">CRM</p>
        <span>SeedCRM</span>
      </div>

      <div class="login-showcase__content">
        <p class="login-showcase__eyebrow">客户增长与门店履约工作台</p>
        <h1>把客资、订单、服务和结算放进一条清晰链路。</h1>
        <p class="login-showcase__summary">
          登录后按角色进入对应工作台，门店、岗位、权限和数据范围会自动生效。
        </p>
      </div>

      <div class="login-showcase__flow">
        <article>
          <span>01</span>
          <strong>客资跟进</strong>
          <small>自动接入、分配、预约</small>
        </article>
        <article>
          <span>02</span>
          <strong>门店服务</strong>
        <small>核销、确认单、履约</small>
        </article>
        <article>
          <span>03</span>
          <strong>财务结算</strong>
          <small>薪酬、分销、审计</small>
        </article>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-panel__card login-panel__card--main">
        <div class="login-panel__header">
          <div>
            <span class="login-panel__tag">{{ loginMode === 'store' ? '门店入口' : '总部入口' }}</span>
            <h2>{{ loginMode === 'store' ? '门店登录' : '总部登录' }}</h2>
          </div>
          <p>{{ loginHeaderDescription }}</p>
        </div>

        <div class="login-mode-switch">
          <button type="button" :class="{ 'is-active': loginMode === 'hq' }" @click="loginMode = 'hq'">
            总部登录
          </button>
          <button type="button" :class="{ 'is-active': loginMode === 'store' }" @click="loginMode = 'store'">
            门店登录
          </button>
        </div>

        <el-form class="login-form" :model="form" label-position="top" @submit.prevent="handleLogin">
          <el-form-item v-if="loginMode === 'store'" label="选择门店">
            <el-select
              v-model="selectedStoreName"
              placeholder="请先选择门店"
              clearable
              filterable
              :loading="storeLoading"
              style="width: 100%"
            >
              <el-option v-for="store in storeOptions" :key="store.value" :label="store.label" :value="store.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="登录账号">
            <el-input
              v-model="form.username"
              :disabled="accountInputDisabled"
              :placeholder="loginMode === 'store' && !selectedStoreName ? '请先选择门店' : '请输入账号'"
              size="large"
            />
          </el-form-item>

          <el-form-item label="登录密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :disabled="accountInputDisabled"
              :placeholder="loginMode === 'store' && !selectedStoreName ? '请先选择门店' : '请输入密码'"
              size="large"
            />
          </el-form-item>

          <el-button type="primary" class="login-submit" :loading="loading" :disabled="!canLogin" @click="handleLogin">
            {{ submitButtonText }}
          </el-button>
        </el-form>

        <div class="login-support">
          <div class="login-safe-note">
            <span>权限生效</span>
            <span>数据隔离</span>
            <span>演示密码 123456</span>
          </div>

          <div class="login-quick-access">
            <div class="login-quick-access__head">
              <h3>{{ loginMode === 'store' ? '门店快捷账号' : '总部快捷账号' }}</h3>
              <p v-if="loginMode === 'store' && !selectedStoreName">请先选择门店</p>
              <p v-else>点击卡片快速填充</p>
            </div>

            <el-empty
              v-if="loginMode === 'store' && selectedStoreName && !visibleAccounts.length"
              description="当前门店暂无可登录账号"
            />

            <div v-else-if="loginMode === 'store' && !selectedStoreName" class="login-empty-tip">请选择门店后登录</div>

            <div v-else class="login-demo-grid">
              <button
                v-for="account in visibleAccounts"
                :key="account.username"
                type="button"
                class="demo-account"
                :class="{ 'is-active': form.username === account.username }"
                @click="pickAccount(account)"
              >
                <span class="demo-account__avatar">{{ account.title?.slice(0, 1) || '账' }}</span>
                <span class="demo-account__body">
                  <strong>{{ account.title }}</strong>
                  <small>{{ account.username }}<template v-if="account.storeName"> · {{ account.storeName }}</template></small>
                </span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { fetchStoreLoginOptions } from '../api/auth'
import { demoAccounts, getFirstAccessibleRoute, login } from '../utils/auth'

const DEMO_DEFAULT_PASSWORD = '123456'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const storeLoading = ref(false)
const loginMode = ref('hq')
const selectedStoreName = ref('')
const remoteStoreOptions = ref([])

const form = reactive({
  username: 'admin',
  password: DEMO_DEFAULT_PASSWORD
})

const fallbackStoreOptions = computed(() =>
  [...new Set(demoAccounts.filter((account) => account.loginMode === 'store').map((account) => account.storeName).filter(Boolean))].map((storeName) => ({
    label: storeName,
    value: storeName,
    storeId: null,
    accounts: demoAccounts
      .filter((account) => account.loginMode === 'store' && account.storeName === storeName)
      .map((account) => ({
        username: account.username,
        title: account.title,
        storeName: account.storeName,
        password: account.password || DEMO_DEFAULT_PASSWORD
      }))
  }))
)

const storeOptions = computed(() => (remoteStoreOptions.value.length ? remoteStoreOptions.value : fallbackStoreOptions.value))
const selectedStoreOption = computed(() => storeOptions.value.find((item) => item.value === selectedStoreName.value) || null)

const visibleAccounts = computed(() => {
  if (loginMode.value === 'store') {
    return selectedStoreOption.value?.accounts || []
  }
  return demoAccounts.filter((account) => account.loginMode !== 'store')
})

const accountInputDisabled = computed(() => loginMode.value === 'store' && !selectedStoreName.value)
const canLogin = computed(() => {
  if (loading.value) {
    return false
  }
  if (loginMode.value === 'store' && !selectedStoreName.value) {
    return false
  }
  return Boolean(String(form.username || '').trim() && String(form.password || '').trim())
})

const loginHeaderDescription = computed(() =>
  loginMode.value === 'store' ? '请先选择门店，再使用对应账号进入门店工作台。' : '登录后进入总部业务与系统管理后台。'
)

const submitButtonText = computed(() => {
  if (loginMode.value === 'store' && !selectedStoreName.value) {
    return '请先选择门店'
  }
  return loginMode.value === 'store' ? '进入门店工作台' : '登录系统'
})

function resetForm(defaultAccount = null) {
  form.username = defaultAccount?.username || ''
  form.password = defaultAccount?.password || (defaultAccount ? DEMO_DEFAULT_PASSWORD : '')
}

function pickAccount(account) {
  form.username = account.username
  form.password = account.password || DEMO_DEFAULT_PASSWORD
}

async function loadStoreOptions() {
  storeLoading.value = true
  try {
    const options = await fetchStoreLoginOptions()
    remoteStoreOptions.value = options.map((item) => ({
      label: item.storeName,
      value: item.storeName,
      storeId: item.storeId,
      accounts: (item.accounts || []).map((account) => ({
        username: account.username,
        title: account.title || account.roleName || account.username,
        storeName: account.storeName || item.storeName,
        password: DEMO_DEFAULT_PASSWORD
      }))
    }))
  } catch {
    remoteStoreOptions.value = []
  } finally {
    storeLoading.value = false
  }
}

async function handleLogin() {
  if (!canLogin.value) {
    if (loginMode.value === 'store' && !selectedStoreName.value) {
      ElMessage.warning('请先选择门店')
    }
    return
  }

  loading.value = true
  try {
    await login({
      username: form.username,
      password: form.password,
      loginMode: loginMode.value,
      storeId: loginMode.value === 'store' ? selectedStoreOption.value?.storeId : undefined,
      storeName: loginMode.value === 'store' ? selectedStoreName.value : undefined
    })
    ElMessage.success('登录成功')
    await router.replace(route.query.redirect || getFirstAccessibleRoute())
  } finally {
    loading.value = false
  }
}

watch(
  loginMode,
  (mode) => {
    if (mode === 'store') {
      selectedStoreName.value = ''
      resetForm()
      return
    }
    selectedStoreName.value = ''
    const fallback = demoAccounts.find((account) => account.loginMode !== 'store')
    resetForm(fallback)
  },
  { immediate: true }
)

watch(selectedStoreName, () => {
  if (loginMode.value !== 'store') {
    return
  }
  resetForm()
  const fallback = visibleAccounts.value[0]
  if (fallback) {
    pickAccount(fallback)
  }
})

onMounted(() => {
  void loadStoreOptions()
})
</script>
