<template>
  <div class="app-shell admin-shell">
    <header class="shell-topbar">
      <div class="shell-topbar__brand">
        <div class="brand-mark">CRM</div>
        <div class="brand-copy">
          <h1>CRM控制台</h1>
          <p>{{ currentUser?.displayName || '未登录' }} · {{ currentRoleLabel }}</p>
        </div>
      </div>

      <div class="shell-topbar__main">
        <el-breadcrumb class="top-breadcrumb" separator="/">
          <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item">
            {{ item }}
          </el-breadcrumb-item>
        </el-breadcrumb>

        <nav class="top-nav">
          <RouterLink
            v-for="group in visibleGroups"
            :key="group.key"
            :to="getGroupEntry(group)"
            class="top-nav__item"
            :class="{ 'is-active': activeGroup?.key === group.key }"
          >
            <el-icon class="top-nav__icon">
              <component :is="group.icon" />
            </el-icon>
            <span>{{ group.label }}</span>
          </RouterLink>
        </nav>
      </div>

      <div class="shell-topbar__actions">
        <div class="account-chip">
          <strong class="account-chip__name">{{ currentUser?.displayName || '--' }}</strong>
          <span>{{ currentRoleLabel }} · {{ currentUser?.username || '--' }}</span>
        </div>
        <el-button class="shell-topbar__logout" @click="handleLogout">退出</el-button>
      </div>
    </header>

    <div class="shell-body">
      <aside v-if="activeGroup" class="side-nav">
        <div class="side-nav__hero">
          <div class="side-nav__hero-icon">
            <el-icon>
              <component :is="activeGroup.icon" />
            </el-icon>
          </div>
          <div class="side-nav__hero-copy">
            <strong>{{ activeGroup.label }}</strong>
          </div>
        </div>

        <nav class="side-nav__sections">
          <template v-if="activeGroup.sections?.length">
            <section v-for="section in activeGroup.sections" :key="section.key" class="side-nav__section">
              <div class="side-nav__section-title">
                <el-icon>
                  <component :is="section.icon" />
                </el-icon>
                <span>{{ section.label }}</span>
              </div>

              <RouterLink
                v-for="item in section.items"
                :key="item.key"
                :to="item.to"
                class="side-nav__link"
                :class="{ 'is-active': isActive(item) }"
              >
                <span class="side-nav__link-icon">
                  <el-icon>
                    <component :is="item.icon" />
                  </el-icon>
                </span>
                <span class="side-nav__link-body">
                  <span class="side-nav__link-title">{{ item.label }}</span>
                </span>
                <el-icon class="side-nav__link-arrow">
                  <ArrowRight />
                </el-icon>
              </RouterLink>
            </section>
          </template>

          <section v-else class="side-nav__section">
            <RouterLink
              v-for="item in activeGroup.items"
              :key="item.key"
              :to="item.to"
              class="side-nav__link"
              :class="{ 'is-active': isActive(item) }"
            >
              <span class="side-nav__link-icon">
                <el-icon>
                  <component :is="item.icon" />
                </el-icon>
              </span>
              <span class="side-nav__link-body">
                <span class="side-nav__link-title">{{ item.label }}</span>
              </span>
              <el-icon class="side-nav__link-arrow">
                <ArrowRight />
              </el-icon>
            </RouterLink>
          </section>
        </nav>

      </aside>

      <main class="main-panel">
        <section class="page-header">
          <div class="page-header__main">
            <p class="page-header__eyebrow">{{ route.meta.sectionTitle || activeGroup?.label || '系统模块' }}</p>
            <h2>{{ route.meta.title }}</h2>
          </div>
        </section>

        <section class="page-body">
          <router-view />
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Bell,
  Calendar,
  ChatDotRound,
  CollectionTag,
  Connection,
  DataAnalysis,
  Files,
  Grid,
  House,
  Key,
  Link,
  Menu as MenuIcon,
  Money,
  OfficeBuilding,
  Operation,
  Picture,
  Promotion,
  SetUp,
  Shop,
  Suitcase,
  Timer,
  User,
  Van,
  WalletFilled
} from '@element-plus/icons-vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { currentUser, hasAccess, logout } from '../utils/auth'
import { formatRoleCode, formatScope } from '../utils/format'

const route = useRoute()
const router = useRouter()

const icon = (component) => markRaw(component)

const navGroups = [
  {
    key: 'clue-center',
    label: '客资中心',
    icon: icon(House),
    description: '统一查看客资、付款线索和排档进度。',
    sections: [
      {
        key: 'clue-center-main',
        label: '业务工作台',
        icon: icon(Operation),
        items: [
          {
            key: 'clues',
            to: '/clues',
            label: '客资列表',
            icon: icon(Files),
            moduleCode: 'CLUE'
          },
          {
            key: 'paid-orders',
            to: '/clues/scheduling',
            label: '顾客排档',
            icon: icon(Calendar),
            moduleCode: 'ORDER',
            roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE']
          }
        ]
      },
      {
        key: 'clue-center-management',
        label: '客资管理',
        icon: icon(SetUp),
        items: [
          {
            key: 'clue-auto-assign',
            to: '/clue-management/auto-assign',
            label: '自动分配',
            icon: icon(Connection),
            moduleCode: 'CLUE',
            roleCodes: ['CLUE_MANAGER', 'ADMIN']
          },
          {
            key: 'duty-customer-service',
            to: '/clue-management/duty-cs',
            label: '值班客服',
            icon: icon(User),
            moduleCode: 'CLUE',
            roleCodes: ['CLUE_MANAGER', 'ADMIN']
          },
          {
            key: 'store-schedules',
            to: '/clue-management/store-schedules',
            label: '门店档期',
            icon: icon(Calendar),
            moduleCode: 'CLUE',
            roleCodes: ['CLUE_MANAGER', 'ADMIN']
          }
        ]
      }
    ]
  },
  {
    key: 'store-service',
    label: '门店服务',
    icon: icon(Shop),
    description: '围绕订单、确认单和服务履约完成门店服务。',
    items: [
      {
        key: 'store-service-orders',
        to: '/store-service/orders',
        label: '订单列表',
        icon: icon(Van),
        moduleCode: 'ORDER',
        roleCodes: ['STORE_SERVICE', 'ADMIN'],
        activePrefixes: ['/store-service/orders', '/orders', '/plan-orders', '/customers']
      }
    ]
  },
  {
    key: 'private-domain',
    label: '私域客服',
    icon: icon(ChatDotRound),
    description: '统一管理企业微信触达、活码、画像和标签能力。',
    items: [
      {
        key: 'private-domain-wecom',
        to: '/private-domain/wecom',
        label: '企业微信',
        icon: icon(ChatDotRound),
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-live-code',
        to: '/private-domain/live-code',
        label: '活码配置',
        icon: icon(Promotion),
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-profile',
        to: '/private-domain/customer-profile',
        label: '客户画像',
        icon: icon(Picture),
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-moments',
        to: '/private-domain/moments',
        label: '朋友圈定时群发',
        icon: icon(Bell),
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-tags',
        to: '/private-domain/tags',
        label: '标签管理',
        icon: icon(CollectionTag),
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      }
    ]
  },
  {
    key: 'finance',
    label: '财务管理',
    icon: icon(WalletFilled),
    description: '查看财务看板、薪酬中心和薪酬配置。',
    sections: [
      {
        key: 'finance-dashboard',
        label: '财务看板',
        icon: icon(DataAnalysis),
        items: [
          {
            key: 'finance',
            to: '/finance',
            label: '财务看板',
            icon: icon(DataAnalysis),
            moduleCode: 'FINANCE'
          }
        ]
      },
      {
        key: 'finance-salary',
        label: '薪酬中心',
        icon: icon(Money),
        items: [
          {
            key: 'salary-center',
            to: '/finance/salary-center',
            label: '薪酬中心',
            icon: icon(Money),
            moduleCode: 'SALARY'
          }
        ]
      },
      {
        key: 'finance-salary-config',
        label: '薪酬配置',
        icon: icon(Grid),
        items: [
          {
            key: 'salary-config-roles',
            to: '/finance/salary-config/roles',
            label: '薪酬角色',
            icon: icon(User),
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN']
          },
          {
            key: 'salary-config-grades',
            to: '/finance/salary-config/grades',
            label: '薪酬档位',
            icon: icon(Suitcase),
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN']
          }
        ]
      }
    ]
  },
  {
    key: 'system-management',
    label: '系统管理',
    icon: icon(Grid),
    description: '维护组织架构、员工、岗位与角色关系。',
    items: [
      {
        key: 'system-departments',
        to: '/system/departments',
        label: '部门管理',
        icon: icon(OfficeBuilding),
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      },
      {
        key: 'system-employees',
        to: '/system/employees',
        label: '员工管理',
        icon: icon(User),
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN', 'CLUE_MANAGER']
      },
      {
        key: 'system-positions',
        to: '/system/positions',
        label: '岗位管理',
        icon: icon(Suitcase),
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      },
      {
        key: 'system-roles',
        to: '/system/roles',
        label: '角色管理',
        icon: icon(Key),
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      }
    ]
  },
  {
    key: 'system-settings',
    label: '系统设置',
    icon: icon(SetUp),
    description: '统一管理菜单、参数、字典和调度中心。',
    sections: [
      {
        key: 'system-setting-base',
        label: '基础配置',
        icon: icon(MenuIcon),
        items: [
          {
            key: 'settings-menu',
            to: '/settings/menu',
            label: '菜单管理',
            icon: icon(MenuIcon),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-dictionaries',
            to: '/settings/dictionaries',
            label: '字典管理',
            icon: icon(CollectionTag),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-parameters',
            to: '/settings/parameters',
            label: '参数管理',
            icon: icon(SetUp),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          }
        ]
      },
      {
        key: 'system-setting-integration',
        label: '调度中心',
        icon: icon(Timer),
        items: [
          {
            key: 'settings-third-party',
            to: '/settings/integration/third-party',
            label: '三方接口',
            icon: icon(Link),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-callback',
            to: '/settings/integration/callback',
            label: '回调接口',
            icon: icon(Bell),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-jobs',
            to: '/settings/integration/jobs',
            label: '任务调度',
            icon: icon(Timer),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-public-api',
            to: '/settings/integration/public-api',
            label: '对外接口',
            icon: icon(Connection),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          }
        ]
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

const activeGroup = computed(() => visibleGroups.value.find((group) => groupHasActive(group)) || visibleGroups.value[0] || null)
const currentRoleLabel = computed(() => formatRoleCode(currentUser.value?.roleCode))
const currentScopeLabel = computed(() => formatScope(currentUser.value?.dataScope))
const breadcrumbItems = computed(() => {
  const parts = String(route.meta.sectionTitle || activeGroup.value?.label || '系统模块')
    .split(/\s*\/\s*/)
    .map((item) => item.trim())
    .filter(Boolean)
  const title = String(route.meta.title || '').trim()

  if (title && parts.at(-1) !== title) {
    parts.push(title)
  }

  return parts.length ? parts : ['系统模块']
})

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

function getGroupEntry(group) {
  if (group.sections?.length) {
    return group.sections[0]?.items[0]?.to || '/'
  }
  return group.items?.[0]?.to || '/'
}

function isActive(item) {
  if (route.path === item.to) {
    return true
  }
  if (!item.activePrefixes?.length) {
    return false
  }
  return item.activePrefixes.some((prefix) => route.path === prefix || route.path.startsWith(`${prefix}/`))
}

function groupHasActive(group) {
  if (group.sections?.length) {
    return group.sections.some((section) => section.items.some((item) => isActive(item)))
  }
  return (group.items || []).some((item) => isActive(item))
}
</script>
