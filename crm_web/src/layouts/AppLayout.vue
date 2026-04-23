<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <p class="brand-mark">Seed CRM</p>
        <h1>约束驱动的中文 CRM 控制台</h1>
        <p>围绕“客资 -> 客户 -> 订单 -> 服务单”主链工作，登录、角色与权限在整个系统中统一生效。</p>
      </div>

      <nav class="nav-stack">
        <section v-for="group in visibleGroups" :key="group.key" class="nav-section">
          <div class="nav-section__header">
            <strong>{{ group.label }}</strong>
            <small>{{ group.description }}</small>
          </div>

          <div class="nav-section__items">
            <RouterLink
              v-for="item in group.items"
              :key="item.key"
              :to="item.to"
              class="nav-card"
              :class="{ 'is-active': isActive(item) }"
            >
              <span class="nav-card__label">{{ item.label }}</span>
              <small>{{ item.description }}</small>
            </RouterLink>
          </div>
        </section>
      </nav>

      <section class="permission-card">
        <div class="panel-heading compact">
          <div>
            <h3>当前登录</h3>
            <p>系统会自动带入角色、门店范围与模块权限，无需手动切换上下文。</p>
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
          <p class="page-header__eyebrow">{{ route.meta.sectionTitle || '系统模块' }}</p>
          <h2>{{ route.meta.title }}</h2>
          <p>{{ route.meta.description }}</p>
        </div>

        <div class="page-header__summary">
          <span class="summary-pill">角色：{{ currentRoleLabel }}</span>
          <span class="summary-pill">范围：{{ currentScopeLabel }}</span>
          <span class="summary-pill">主链：客资 → 客户 → 订单 → 服务单</span>
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

const navGroups = [
  {
    key: 'clue-center',
    label: '客资中心',
    description: '统一接入与转化客资',
    items: [
      {
        key: 'clues',
        to: '/clues',
        label: '客资列表',
        description: '查看自动拉取客资、手动分配、公海回收和转订单主链',
        moduleCode: 'CLUE'
      }
    ]
  },
  {
    key: 'clue-management',
    label: '客资管理',
    description: '仅客资主管可见',
    items: [
      {
        key: 'clue-auto-assign',
        to: '/clue-management/auto-assign',
        label: '自动分配',
        description: '维护自动轮询当值客服的分配策略',
        moduleCode: 'CLUE',
        roleCodes: ['CLUE_MANAGER', 'ADMIN']
      },
      {
        key: 'duty-customer-service',
        to: '/clue-management/duty-cs',
        label: '值班客服',
        description: '设置客服班次、当值状态和请假情况',
        moduleCode: 'CLUE',
        roleCodes: ['CLUE_MANAGER', 'ADMIN']
      }
    ]
  },
  {
    key: 'store-service',
    label: '门店服务',
    description: '门店服务人员工入口',
    items: [
      {
        key: 'store-service-orders',
        to: '/store-service/orders',
        label: '订单列表',
        description: '查看全部、已预约、已完成订单，并填写确认单',
        moduleCode: 'ORDER',
        roleCodes: ['STORE_SERVICE', 'ADMIN'],
        activePrefixes: ['/store-service/orders', '/orders', '/plan-orders', '/customers']
      }
    ]
  },
  {
    key: 'scheduler',
    label: '调度中心',
    description: '同步、队列和日志',
    items: [
      {
        key: 'scheduler',
        to: '/scheduler',
        label: '任务调度',
        description: '管理外部调用、增量同步、失败重试和执行日志',
        moduleCode: 'SCHEDULER'
      }
    ]
  },
  {
    key: 'permission',
    label: '权限中心',
    description: 'RBAC / ABAC',
    items: [
      {
        key: 'permission',
        to: '/permission',
        label: '权限策略',
        description: '维护模块权限、角色范围和实时校验规则',
        moduleCode: 'PERMISSION'
      }
    ]
  },
  {
    key: 'salary',
    label: '薪酬中心',
    description: '结算与提现',
    items: [
      {
        key: 'salary',
        to: '/salary',
        label: '薪酬看板',
        description: '按服务角色记录统计薪酬并处理结算与提现',
        moduleCode: 'SALARY'
      }
    ]
  },
  {
    key: 'distributors',
    label: '分销概览',
    description: '渠道贡献与收益',
    items: [
      {
        key: 'distributors',
        to: '/distributors',
        label: '分销数据',
        description: '查看分销带来的客资、订单和收益表现',
        moduleCode: 'DISTRIBUTOR'
      }
    ]
  },
  {
    key: 'finance',
    label: '财务总览',
    description: '收入与提现记录',
    items: [
      {
        key: 'finance',
        to: '/finance',
        label: '财务看板',
        description: '查看员工与分销两侧的收入、结算和提现记录',
        moduleCode: 'FINANCE'
      }
    ]
  }
]

const visibleGroups = computed(() =>
  navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => hasAccess(item.moduleCode, item.roleCodes))
    }))
    .filter((group) => group.items.length)
)

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
    // ignore cancel
  }
}

function isActive(item) {
  const activePrefixes = item.activePrefixes || [item.to]
  return activePrefixes.some((prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`))
}
</script>
