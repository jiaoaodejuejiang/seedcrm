<template>
  <div class="login-shell admin-login-shell">
    <section class="login-showcase">
      <div class="login-showcase__badge">Element Admin 风格</div>
      <p class="brand-mark">CRM</p>
      <h1>CRM 控制台登录</h1>
      <p class="login-showcase__summary">
        采用统一后台壳风格，登录后自动注入角色、数据范围和菜单权限，前后台行为保持一致。
      </p>

      <div class="login-showcase__highlights">
        <article class="login-highlight">
          <strong>角色权限自动生效</strong>
          <span>管理员、客资主管、门店服务、私域客服登录后看到的菜单会自动收敛。</span>
        </article>
        <article class="login-highlight">
          <strong>业务主链统一联动</strong>
          <span>客资、订单、排档和门店履约都在同一套后台交互中完成。</span>
        </article>
        <article class="login-highlight">
          <strong>后续可继续扩展主题</strong>
          <span>当前先落地成熟商务风，后面再接入浅色、深色或品牌主题切换。</span>
        </article>
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
          <h3>演示账号</h3>
          <p>点击即可自动填充，统一密码均为 `123456`</p>
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
