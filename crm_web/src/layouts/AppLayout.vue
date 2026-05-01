<template>
  <div class="app-shell admin-shell" :class="{ 'app-shell--standalone': standalonePage }">
    <header v-if="!standalonePage" class="shell-topbar">
      <div class="shell-topbar__brand">
        <div class="brand-mark">CRM</div>
        <div class="brand-copy">
          <h1>CRM 控制台</h1>
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

    <div class="shell-body" :class="{ 'shell-body--standalone': standalonePage }">
      <aside v-if="activeGroup && !standalonePage" class="side-nav">
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

      <main class="main-panel" :class="{ 'main-panel--standalone': standalonePage }">
        <section v-if="!standalonePage && !route.meta?.hidePageHeader" class="page-header">
          <div class="page-header__main">
            <p class="page-header__eyebrow">{{ resolvedSectionTitle }}</p>
            <h2>{{ resolvedPageTitle }}</h2>
          </div>
        </section>

        <section class="page-body" :class="{ 'page-body--standalone': standalonePage }">
          <router-view />
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, onUnmounted, ref } from 'vue'
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
import { currentUser, getEffectiveMenuConfigs, hasRouteAccess, logout } from '../utils/auth'
import { syncDomainSettingsFromBackend } from '../utils/domainSettings'
import { formatRoleCode } from '../utils/format'

const route = useRoute()
const router = useRouter()
const systemConsoleVersion = ref(0)

const icon = (component) => markRaw(component)
const storeRoleCodes = ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR', 'ADMIN']
const integrationViewRoleCodes = ['ADMIN', 'INTEGRATION_ADMIN', 'INTEGRATION_OPERATOR']
const integrationConfigRoleCodes = ['ADMIN', 'INTEGRATION_ADMIN']

const navGroups = [
  {
    key: 'clue-center',
    label: '客资中心',
    icon: icon(House),
    sections: [
      {
        key: 'clue-center-main',
        label: '业务工作台',
        icon: icon(Operation),
        items: [
          { key: 'clues', to: '/clues', label: '客资列表', icon: icon(Files), moduleCode: 'CLUE' },
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
          }
        ]
      }
    ]
  },
  {
    key: 'store-service',
    label: '门店服务',
    icon: icon(Shop),
    items: [
      {
        key: 'store-service-orders',
        to: '/store-service/orders',
        label: '订单列表',
        icon: icon(Van),
        moduleCode: 'ORDER',
        roleCodes: storeRoleCodes,
        activePrefixes: ['/store-service/orders', '/orders', '/plan-orders', '/customers']
      },
      {
        key: 'store-service-design',
        to: '/store-service/service-design',
        label: '服务单设计',
        icon: icon(Picture),
        moduleCode: 'PLANORDER',
        roleCodes: ['STORE_MANAGER', 'ADMIN']
      },
      {
        key: 'store-schedules',
        to: '/store-service/schedules',
        label: '门店档期',
        icon: icon(Calendar),
        moduleCode: 'PLANORDER',
        roleCodes: ['STORE_MANAGER', 'ADMIN']
      },
      {
        key: 'store-service-personnel',
        to: '/store-service/personnel',
        label: '人员管理',
        icon: icon(User),
        moduleCode: 'SYSTEM',
        roleCodes: ['STORE_MANAGER', 'ADMIN']
      },
      {
        key: 'store-service-roles',
        to: '/store-service/roles',
        label: '门店角色',
        icon: icon(Key),
        moduleCode: 'SYSTEM',
        roleCodes: ['STORE_MANAGER', 'ADMIN']
      }
    ]
  },
  {
    key: 'private-domain',
    label: '私域客服',
    icon: icon(ChatDotRound),
    items: [
      {
        key: 'private-domain-live-code',
        to: '/private-domain/live-code',
        label: '活码配置',
        icon: icon(Promotion),
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-members',
        to: '/private-domain/members',
        label: '会员信息',
        icon: icon(User),
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
    sections: [
      {
        key: 'finance-dashboard',
        label: '财务看板',
        icon: icon(DataAnalysis),
        items: [
          { key: 'finance', to: '/finance', label: '财务看板', icon: icon(DataAnalysis), moduleCode: 'FINANCE' },
          { key: 'salary-my', to: '/finance/salary/my', label: '我的薪酬', icon: icon(Money), moduleCode: 'SALARY' }
        ]
      },
      {
        key: 'finance-settlement',
        label: '薪酬结算',
        icon: icon(WalletFilled),
        items: [
          {
            key: 'salary-settlements',
            to: '/finance/salary/settlements',
            label: '结算单管理',
            icon: icon(WalletFilled),
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN', 'FINANCE']
          },
          {
            key: 'salary-withdrawals',
            to: '/finance/salary/withdrawals',
            label: '提现处理',
            icon: icon(Money),
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN', 'FINANCE']
          },
          {
            key: 'salary-refund-adjustments',
            to: '/finance/salary/refund-adjustments',
            label: '退款冲正',
            icon: icon(Operation),
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN', 'FINANCE']
          },
          {
            key: 'salary-settlement-config',
            to: '/finance/salary/settlement-config',
            label: '结算配置',
            icon: icon(SetUp),
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN', 'FINANCE']
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
          },
          {
            key: 'salary-config-distributor',
            to: '/finance/salary-config/distributor',
            label: '分销配置',
            icon: icon(Link),
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
    sections: [
      {
        key: 'system-setting-base',
        label: '基础配置',
        icon: icon(MenuIcon),
        items: [
          { key: 'settings-domain', to: '/settings/base/domain', label: '域名配置', icon: icon(Link), moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
          { key: 'settings-go-live', to: '/settings/base/go-live', label: '上线工具', icon: icon(Operation), moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
          {
            key: 'settings-wecom',
            to: '/settings/base/wecom',
            label: '企业微信',
            icon: icon(ChatDotRound),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          { key: 'settings-menu', to: '/settings/menu', label: '菜单管理', icon: icon(MenuIcon), moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
          {
            key: 'settings-dictionaries',
            to: '/settings/dictionaries',
            label: '字典管理',
            icon: icon(CollectionTag),
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          { key: 'settings-parameters', to: '/settings/parameters', label: '参数管理', icon: icon(SetUp), moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
          { key: 'settings-payment', to: '/settings/payment', label: '支付设置', icon: icon(WalletFilled), moduleCode: 'SETTING', roleCodes: ['ADMIN'] }
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
            label: '抖音接口',
            icon: icon(Link),
            moduleCode: 'SETTING',
            roleCodes: integrationConfigRoleCodes
          },
          {
            key: 'settings-callback',
            to: '/settings/integration/callback',
            label: '回调接口',
            icon: icon(Bell),
            moduleCode: 'SETTING',
            roleCodes: integrationViewRoleCodes
          },
          {
            key: 'settings-jobs',
            to: '/settings/integration/jobs',
            label: '任务调度',
            icon: icon(Timer),
            moduleCode: 'SETTING',
            roleCodes: integrationViewRoleCodes
          },
            {
              key: 'settings-interface-debug',
              to: '/settings/integration/debug',
              label: '联调工作台',
              icon: icon(Operation),
              moduleCode: 'SETTING',
              roleCodes: integrationViewRoleCodes
          },
          { key: 'settings-public-api', to: '/settings/integration/public-api', label: '对外接口', icon: icon(Connection), moduleCode: 'SETTING', roleCodes: ['ADMIN'] },
          {
            key: 'settings-distribution-api',
            to: '/settings/integration/distribution-api',
            label: '分销接口',
            icon: icon(Promotion),
            moduleCode: 'SETTING',
            roleCodes: integrationViewRoleCodes
          }
        ]
      },
      {
        key: 'system-setting-flow',
        label: '流程配置',
        icon: icon(SetUp),
        items: [
          { key: 'settings-system-flow', to: '/settings/system-flow', label: '系统流程', icon: icon(SetUp), moduleCode: 'SETTING', roleCodes: ['ADMIN'] }
        ]
      }
    ]
  }
]

const routeTitleMap = {
  login: '登录',
  clues: '客资列表',
  'clues-scheduling': '顾客排档',
  'clue-auto-assign': '自动分配',
  'duty-customer-service': '值班客服',
  'store-schedules': '门店档期',
  'store-service-orders': '订单列表',
  'store-service-design': '服务单设计',
  'store-service-personnel': '人员管理',
  'store-service-roles': '门店角色',
  'plan-orders': '服务单',
  'plan-orders-scan': '扫码服务单',
  customers: '客户详情',
  'private-domain-live-code': '活码配置',
  'private-domain-members': '会员信息',
  'private-domain-customer-profile': '客户画像',
  'private-domain-moments': '朋友圈定时群发',
  'private-domain-tags': '标签管理',
  finance: '财务看板',
  'salary-my': '我的薪酬',
  'salary-settlements': '结算单管理',
  'salary-withdrawals': '提现处理',
  'salary-refund-adjustments': '退款冲正',
  'salary-settlement-config': '结算配置',
  'salary-config-roles': '薪酬角色',
  'salary-config-grades': '薪酬档位',
  'salary-config-distributor': '分销配置',
  'system-departments': '部门管理',
  'system-employees': '员工管理',
  'system-positions': '岗位管理',
  'system-roles': '角色管理',
  'settings-third-party': '抖音接口',
  'settings-callback': '回调接口',
  'settings-jobs': '任务调度',
  'settings-interface-debug': '联调工作台',
  'settings-domain': '域名配置',
  'settings-go-live': '上线工具',
  'settings-wecom': '企业微信',
  'settings-menu': '菜单管理',
  'settings-public-api': '对外接口',
  'settings-distribution-api': '分销接口',
  'settings-dictionaries': '字典管理',
  'settings-parameters': '参数管理',
  'settings-payment': '支付设置',
  'settings-system-flow': '系统流程'
}

const routeSectionMap = {
  clues: '客资中心',
  'clues-scheduling': '客资中心',
  'clue-auto-assign': '客资中心 / 客资管理',
  'duty-customer-service': '客资中心 / 客资管理',
  'store-schedules': '客资中心 / 客资管理',
  'store-service-orders': '门店服务',
  'store-service-design': '门店服务',
  'store-service-personnel': '门店服务',
  'store-service-roles': '门店服务',
  'plan-orders': '门店服务',
  'plan-orders-scan': '门店服务',
  customers: '门店服务',
  'private-domain-live-code': '私域客服',
  'private-domain-members': '私域客服',
  'private-domain-customer-profile': '私域客服',
  'private-domain-moments': '私域客服',
  'private-domain-tags': '私域客服',
  finance: '财务管理',
  'salary-my': '财务管理 / 薪酬中心',
  'salary-settlements': '财务管理 / 薪酬结算',
  'salary-withdrawals': '财务管理 / 薪酬结算',
  'salary-refund-adjustments': '财务管理 / 薪酬结算',
  'salary-settlement-config': '财务管理 / 薪酬结算',
  'salary-config-roles': '财务管理 / 薪酬配置',
  'salary-config-grades': '财务管理 / 薪酬配置',
  'salary-config-distributor': '财务管理 / 薪酬配置',
  'system-departments': '系统管理',
  'system-employees': '系统管理',
  'system-positions': '系统管理',
  'system-roles': '系统管理',
  'settings-third-party': '系统设置 / 调度中心',
  'settings-callback': '系统设置 / 调度中心',
  'settings-jobs': '系统设置 / 调度中心',
  'settings-interface-debug': '系统设置 / 调度中心',
  'settings-domain': '系统设置 / 基础配置',
  'settings-go-live': '系统设置 / 基础配置',
  'settings-wecom': '系统设置 / 基础配置',
  'settings-menu': '系统设置 / 基础配置',
  'settings-public-api': '系统设置 / 调度中心',
  'settings-distribution-api': '系统设置 / 调度中心',
  'settings-dictionaries': '系统设置 / 基础配置',
  'settings-parameters': '系统设置 / 基础配置',
  'settings-payment': '系统设置 / 基础配置',
  'settings-system-flow': '系统设置 / 流程配置'
}

const visibleGroups = computed(() =>
  navGroups
    .map((group) => {
      if (group.sections?.length) {
        const sections = group.sections
          .map((section) => ({
            ...section,
            items: section.items
              .map(applyMenuConfig)
              .filter((item) => item.isEnabled !== false && hasRouteAccess(item.to, item.moduleCode, item.roleCodes))
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
        items: group.items
          .map(applyMenuConfig)
          .filter((item) => item.isEnabled !== false && hasRouteAccess(item.to, item.moduleCode, item.roleCodes)),
        sections: []
      }
    })
    .filter((group) => group.items.length || group.sections.length)
)

const menuConfigByRoute = computed(() => {
  systemConsoleVersion.value
  const items = getEffectiveMenuConfigs()
  return new Map(items.map((item) => [String(item.routePath || '').trim(), item]).filter(([routePath]) => routePath))
})
const activeGroup = computed(() => visibleGroups.value.find((group) => groupHasActive(group)) || visibleGroups.value[0] || null)
const currentRoleLabel = computed(() => formatRoleCode(currentUser.value?.roleCode))
const standalonePage = computed(() => Boolean(route.meta?.standalone) || String(route.query.scan || '') === '1')
const configuredRouteLabel = computed(() => menuConfigByRoute.value.get(route.path)?.menuName || '')
const resolvedPageTitle = computed(() => configuredRouteLabel.value || routeTitleMap[route.name] || route.meta.title || activeGroup.value?.label || '系统模块')
const resolvedSectionTitle = computed(
  () => routeSectionMap[route.name] || route.meta.sectionTitle || activeGroup.value?.label || '系统模块'
)
const breadcrumbItems = computed(() => {
  const parts = String(resolvedSectionTitle.value)
    .split(/\s*\/\s*/)
    .map((item) => item.trim())
    .filter(Boolean)
  const title = String(resolvedPageTitle.value || '').trim()

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

onMounted(() => {
  window.addEventListener('seedcrm:system-console-updated', refreshSystemConsoleVersion)
  syncDomainSettingsFromBackend()
    .then(refreshSystemConsoleVersion)
    .catch(() => {
      // Keep the local fallback so the console remains usable when the config API is unavailable.
    })
})

onUnmounted(() => {
  window.removeEventListener('seedcrm:system-console-updated', refreshSystemConsoleVersion)
})

function refreshSystemConsoleVersion() {
  systemConsoleVersion.value += 1
}

function applyMenuConfig(item) {
  const config = menuConfigByRoute.value.get(item.to)
  if (!config) {
    return item
  }
  return {
    ...item,
    label: config.menuName || item.label,
    moduleCode: config.moduleCode || item.moduleCode,
    roleCodes: config.roleCodes?.length ? config.roleCodes : item.roleCodes,
    isEnabled: config.isEnabled !== 0
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
