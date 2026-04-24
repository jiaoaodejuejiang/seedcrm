<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <p class="brand-mark">CRM</p>
        <h1>CRM控制台</h1>
      </div>

      <nav class="nav-stack">
        <section v-for="group in visibleGroups" :key="group.key" class="nav-section">
          <div class="nav-section__header">
            <div class="nav-title-row">
              <strong>{{ group.label }}</strong>
            </div>
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
                  <div class="nav-card__row">
                    <span class="nav-card__label">{{ item.label }}</span>
                  </div>
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
              <div class="nav-card__row">
                <span class="nav-card__label">{{ item.label }}</span>
              </div>
            </RouterLink>
          </div>
        </section>
      </nav>

      <section class="permission-card">
        <div class="panel-heading compact">
          <div>
            <h3>当前登录</h3>
            <p>登录角色与权限已自动生效。</p>
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
        </div>

        <div class="page-header__summary">
          <span class="summary-pill">角色：{{ currentRoleLabel }}</span>
          <span class="summary-pill">范围：{{ currentScopeLabel }}</span>
          <span class="summary-pill">主链：客资 → 客户 → 订单 → 排期履约</span>
        </div>
      </section>

      <section class="page-body">
        <router-view />
        <p class="page-footnote">说明：{{ route.meta.note || route.meta.description || '当前页面按角色权限展示可操作数据。' }}</p>
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
    sections: [
      {
        key: 'clue-center-main',
        label: '业务工作台',
        items: [
          {
            key: 'clues',
            to: '/clues',
            label: '客资列表',
            moduleCode: 'CLUE'
          },
          {
            key: 'paid-orders',
            to: '/clues/scheduling',
            label: '顾客排档',
            moduleCode: 'ORDER',
            roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE']
          }
        ]
      },
      {
        key: 'clue-center-management',
        label: '客资管理',
        items: [
          {
            key: 'clue-auto-assign',
            to: '/clue-management/auto-assign',
            label: '自动分配',
            moduleCode: 'CLUE',
            roleCodes: ['CLUE_MANAGER', 'ADMIN']
          },
          {
            key: 'duty-customer-service',
            to: '/clue-management/duty-cs',
            label: '值班客服',
            moduleCode: 'CLUE',
            roleCodes: ['CLUE_MANAGER', 'ADMIN']
          },
          {
            key: 'store-schedules',
            to: '/clue-management/store-schedules',
            label: '门店档期',
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
    items: [
      {
        key: 'store-service-orders',
        to: '/store-service/orders',
        label: '订单列表',
        moduleCode: 'ORDER',
        roleCodes: ['STORE_SERVICE', 'ADMIN'],
        activePrefixes: ['/store-service/orders', '/orders', '/plan-orders', '/customers']
      }
    ]
  },
  {
    key: 'private-domain',
    label: '私域客服',
    items: [
      {
        key: 'private-domain-wecom',
        to: '/private-domain/wecom',
        label: '企业微信',
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-live-code',
        to: '/private-domain/live-code',
        label: '活码配置',
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-profile',
        to: '/private-domain/customer-profile',
        label: '客户画像',
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-moments',
        to: '/private-domain/moments',
        label: '朋友圈定时群发',
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      },
      {
        key: 'private-domain-tags',
        to: '/private-domain/tags',
        label: '便签管理',
        moduleCode: 'WECOM',
        roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE']
      }
    ]
  },
  {
    key: 'finance',
    label: '财务管理',
    sections: [
      {
        key: 'finance-dashboard',
        label: '财务看板',
        items: [
          {
            key: 'finance',
            to: '/finance',
            label: '财务看板',
            moduleCode: 'FINANCE'
          }
        ]
      },
      {
        key: 'finance-salary',
        label: '薪酬中心',
        items: [
          {
            key: 'salary-center',
            to: '/finance/salary-center',
            label: '薪酬中心',
            moduleCode: 'SALARY'
          }
        ]
      },
      {
        key: 'finance-salary-config',
        label: '薪酬配置',
        items: [
          {
            key: 'salary-config-roles',
            to: '/finance/salary-config/roles',
            label: '薪酬角色',
            moduleCode: 'SALARY',
            roleCodes: ['ADMIN']
          },
          {
            key: 'salary-config-grades',
            to: '/finance/salary-config/grades',
            label: '薪酬档位',
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
    items: [
      {
        key: 'system-departments',
        to: '/system/departments',
        label: '部门管理',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      },
      {
        key: 'system-employees',
        to: '/system/employees',
        label: '员工管理',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN', 'CLUE_MANAGER']
      },
      {
        key: 'system-positions',
        to: '/system/positions',
        label: '岗位管理',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      },
      {
        key: 'system-roles',
        to: '/system/roles',
        label: '角色管理',
        moduleCode: 'SYSTEM',
        roleCodes: ['ADMIN']
      }
    ]
  },
  {
    key: 'system-settings',
    label: '系统设置',
    sections: [
      {
        key: 'system-setting-base',
        label: '基础配置',
        items: [
          {
            key: 'settings-menu',
            to: '/settings/menu',
            label: '菜单管理',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-dictionaries',
            to: '/settings/dictionaries',
            label: '字典管理',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-parameters',
            to: '/settings/parameters',
            label: '参数管理',
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
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-callback',
            to: '/settings/integration/callback',
            label: '回调接口',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-jobs',
            to: '/settings/integration/jobs',
            label: '任务调度',
            moduleCode: 'SETTING',
            roleCodes: ['ADMIN']
          },
          {
            key: 'settings-public-api',
            to: '/settings/integration/public-api',
            label: '对外接口',
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
