<template>
  <div class="login-shell">
    <section class="login-card">
      <div class="login-hero">
        <p class="brand-mark">Seed CRM</p>
        <h1>中文 CRM 控制台登录</h1>
        <p>登录后系统会自动注入角色、数据范围和菜单权限，整个前后台保持统一约束。</p>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" class="login-submit" :loading="loading" @click="handleLogin">登录系统</el-button>
      </el-form>
    </section>

    <section class="login-card login-card--soft">
      <div class="panel-heading compact">
        <div>
          <h3>演示账号</h3>
          <p>点击即可自动填充，统一密码均为 `123456`。</p>
        </div>
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
          <small>{{ account.description }}</small>
        </button>
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
