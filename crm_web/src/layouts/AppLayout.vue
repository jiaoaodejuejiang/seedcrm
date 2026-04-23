<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <p class="brand-mark">Seed CRM</p>
        <h1>约束驱动主链控制台</h1>
        <p>前台与后端统一围绕“线索 -> 客户 -> 订单 -> 服务单”主链运行，登录、权限与调度全局生效。</p>
      </div>

      <nav class="nav-stack">
        <RouterLink
          v-for="item in visibleNavItems"
          :key="item.to"
          :to="item.to"
          class="nav-card"
          :class="{ 'is-active': isActive(item.to) }"
        >
          <span class="nav-card__label">{{ item.label }}</span>
          <small>{{ item.description }}</small>
        </RouterLink>
      </nav>

      <section class="permission-card">
        <div class="panel-heading compact">
          <div>
            <h3>当前登录</h3>
            <p>登录后系统会自动注入角色、数据范围与权限上下文。</p>
          </div>
          <el-button size="small" text @click="handleLogout">退出</el-button>
        </div>

        <div class="context-grid auth-grid">
          <label>
            <span>姓名</span>
            <strong class="auth-card-value">{{ currentUser?.displayName || '--' }}</strong>
          </label>
          <label>
            <span>角色</span>
            <strong class="auth-card-value">{{ currentRoleLabel }}</strong>
          </label>
          <label>
            <span>数据范围</span>
            <strong class="auth-card-value">{{ currentScopeLabel }}</strong>
          </label>
          <label>
            <span>门店 ID</span>
            <strong class="auth-card-value">{{ currentUser?.storeId || '--' }}</strong>
          </label>
          <label class="full-span">
            <span>账号</span>
            <strong class="auth-card-value">{{ currentUser?.username || '--' }}</strong>
          </label>
        </div>
      </section>
    </aside>

    <main class="main-panel">
      <section class="page-header">
        <div>
          <p class="page-header__eyebrow">约束优先</p>
          <h2>{{ route.meta.title }}</h2>
          <p>{{ route.meta.description }}</p>
        </div>

        <div class="page-header__summary">
          <span class="summary-pill">角色：{{ currentRoleLabel }}</span>
          <span class="summary-pill">范围：{{ currentScopeLabel }}</span>
          <span class="summary-pill">主链：线索 -> 客户 -> 订单 -> 服务单</span>
        </div>
      </section>

      <section class="page-body">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { currentUser, hasAccess, logout } from '../utils/auth'
import { formatRoleCode, formatScope } from '../utils/format'

const route = useRoute()
const router = useRouter()

const navItems = [
  { to: '/clues', label: '线索', description: '统一接入、分配、回收与转订单', moduleCode: 'CLUE' },
  { to: '/orders', label: '订单', description: '已支付与已使用订单链路', moduleCode: 'ORDER' },
  { to: '/plan-orders', label: '服务单', description: '服务履约与角色记录', moduleCode: 'PLANORDER' },
  { to: '/scheduler', label: '调度', description: '任务、队列、重试与日志', moduleCode: 'SCHEDULER' },
  { to: '/permission', label: '权限', description: 'RBAC、ABAC 与实时校验', moduleCode: 'PERMISSION' },
  { to: '/salary', label: '薪酬', description: '统计、结算、打款与提现', moduleCode: 'SALARY' },
  { to: '/distributors', label: '分销', description: '线索贡献与收益概览', moduleCode: 'DISTRIBUTOR' },
  { to: '/finance', label: '财务', description: '收入与提现记录总览', moduleCode: 'FINANCE' }
]

const visibleNavItems = computed(() => navItems.filter((item) => hasAccess(item.moduleCode)))
const currentRoleLabel = computed(() => formatRoleCode(currentUser.value?.roleCode))
const currentScopeLabel = computed(() => formatScope(currentUser.value?.dataScope))

async function handleLogout() {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能继续操作，是否确认退出？', '退出登录', {
      confirmButtonText: '确认退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await logout()
    await router.replace('/login')
  } catch {
    // 取消退出时不需要额外提示。
  }
}

function isActive(target) {
  return route.path === target || route.path.startsWith(`${target}/`)
}
</script>
