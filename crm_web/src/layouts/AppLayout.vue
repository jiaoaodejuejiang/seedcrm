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

          <template v-if="group.sections?.length">
            <div v-for="section in group.sections" :key="section.key" class="nav-subsection">
              <div class="nav-subsection__title">{{ section.label }}</div>
              <div class="nav-section__items">
                <RouterLink
                  v-for="item in section.items"
                  :key="item.key"
                  :to="item.to"
                  class="nav-card"
                  :class="{ 'is-active': isActive(item) }"
                >
                  <span class="nav-card__label">{{ item.label }}</span>
                  <small>{{ item.description }}</small>
                </RouterLink>
              </div>
            </div>
          </template>

          <div v-else class="nav-section__items">
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
        description: '已预约订单可确认服务项目，已完成订单可查看确认单',
        moduleCode: 'ORDER',
        roleCodes: ['STORE_SERVICE', 'ADMIN'],
        activePrefixes: ['/store-service/orders', '/orders', '/plan-orders', '/customers']
      }
    ]
  },
  {
    key: 'system-management',
    label: '系统管理',
    description: '管理员管理组织，部门负责人管理本部门成员',
    items: [
      {
        key: 'system-departments',
        to: '/system/departments',
        label: '部门管理',
        description: '设置组织架构和部门数据范围',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      },
      {
        key: 'system-employees',
        to: '/system/employees',
        label: '员工管理',
        description: '添加、停用与调岗员工',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN', 'CLUE_MANAGER']
      },
      {
        key: 'system-positions',
        to: '/system/positions',
        label: '岗位管理',
        description: '维护岗位并在删除时转移员工',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      },
      {
        key: 'system-roles',
        to: '/system/roles',
        label: '角色管理',
        description: '配置角色、人员和授权策略',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      }
    ]
  },
  {
    key: 'system-settings',
    label: '系统设置',
    description: '管理员维护菜单、调度、字典与参数',
    sections: [
      {
        key: 'system-setting-base',
        label: '基础配置',
        items: [
          {
            key: 'settings-menu',
            to: '/settings/menu',
            label: '菜单管理',
            description: '编排页面入口并配置角色权限',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-dictionaries',
            to: '/settings/dictionaries',
            label: '字典管理',
            description: '维护编码和值的展示映射',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-parameters',
            to: '/settings/parameters',
            label: '参数管理',
            description: '统一维护系统参数键值',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          }
        ]
      },
      {
        key: 'system-setting-integration',
        label: '调度中心',
        items: [
          {
            key: 'settings-third-party',
            to: '/settings/integration/third-party',
            label: '三方接口',
            description: '配置客资拉取等三方接口地址',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-callback',
            to: '/settings/integration/callback',
            label: '回调接口',
            description: '配置异步回调地址与验签方式',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-jobs',
            to: '/settings/integration/jobs',
            label: '任务调度',
            description: '配置定时任务、同步方式和失败重试',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-public-api',
            to: '/settings/integration/public-api',
            label: '对外接口',
            description: '配置对外查询结果、认证和缓存',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          }
        ]
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
    key: 'private-domain',
    label: '私域客服',
    description: '企业微信相关功能集中配置',
    items: [
      {
        key: 'private-domain-wecom',
        to: '/private-domain/wecom',
        label: '企业微信',
        description: '配置企微联系人、触达规则和私域消息',
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
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
    .map((group) => {
      if (group.sections?.length) {
        const sections = group.sections
          .map((section) => ({
            ...section,
            items: section.items.filter((item) => hasAccess(item.moduleCode, item.roleCodes))
          }))
          .filter((section) => section.items.length)
        return {
          ...group,
          sections,
          items: []
        }
      }
      return {
        ...group,
        items: group.items.filter((item) => hasAccess(item.moduleCode, item.roleCodes)),
        sections: []
      }
    })
    .filter((group) => group.items.length || group.sections.length)
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
