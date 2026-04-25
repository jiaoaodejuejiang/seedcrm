<template>
  <div class="login-shell admin-login-shell">
    <section class="login-showcase">
      <p class="brand-mark">CRM</p>
      <h1>CRM 控制台</h1>
      <p class="login-showcase__summary">按角色登录后自动进入对应工作台与权限范围。</p>

      <div class="login-showcase__roles">
        <span v-for="account in demoAccounts" :key="account.username" class="login-role-pill">
          {{ account.title }}
        </span>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-panel__card">
        <div class="login-panel__header">
          <h2>账号登录</h2>
          <p>请输入账号和密码进入系统</p>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="账号">
            <el-input v-model="form.username" placeholder="请输入账号" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              size="large"
            />
          </el-form-item>
          <el-button type="primary" class="login-submit" :loading="loading" @click="handleLogin">
            登录系统
          </el-button>
        </el-form>
      </div>

      <div class="login-panel__card login-panel__card--soft">
        <div class="login-panel__header">
          <h3>快捷账号</h3>
          <p>点击即可填充，统一密码 `123456`</p>
        </div>

        <div class="login-demo-grid">
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            type="button"
            class="demo-account"
            @click="pickDemo(account)"
          >
            <strong>{{ account.title }}</strong>
            <span>{{ account.username }}</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { demoAccounts, getFirstAccessibleRoute, login } from '../utils/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456'
})

function pickDemo(account) {
  form.username = account.username
  form.password = account.password
}

async function handleLogin() {
  loading.value = true
  try {
    await login({
      username: form.username,
      password: form.password
    })
    ElMessage.success('登录成功')
    await router.replace(route.query.redirect || getFirstAccessibleRoute())
  } finally {
    loading.value = false
  }
}
</script>
