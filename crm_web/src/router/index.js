import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { getFirstAccessibleRoute, hasRouteAccess, initializeAuth } from '../utils/auth'
import ClueManagement from '../views/ClueManagement.vue'
import ClueAutoAssignmentView from '../views/ClueAutoAssignmentView.vue'
import CustomerDetail from '../views/CustomerDetail.vue'
import DistributionApiView from '../views/DistributionApiView.vue'
import DutyCustomerServiceView from '../views/DutyCustomerServiceView.vue'
import DomainSettingView from '../views/DomainSettingView.vue'
import FinanceOverview from '../views/FinanceOverview.vue'
import InterfaceDebugView from '../views/InterfaceDebugView.vue'
import LoginView from '../views/LoginView.vue'
import MySalaryView from '../views/MySalaryView.vue'
import OrderManagement from '../views/OrderManagement.vue'
import PaidOrderManagement from '../views/PaidOrderManagement.vue'
import PaymentSettingView from '../views/PaymentSettingView.vue'
import PlanOrderService from '../views/PlanOrderService.vue'
import PlatformDouyinConfigView from '../views/PlatformDouyinConfigView.vue'
import PlatformRuntimeView from '../views/PlatformRuntimeView.vue'
import PlatformWecomConfigView from '../views/PlatformWecomConfigView.vue'
import PrivateDomainCustomerProfileView from '../views/PrivateDomainCustomerProfileView.vue'
import PrivateDomainLiveCodeView from '../views/PrivateDomainLiveCodeView.vue'
import PrivateDomainMomentsView from '../views/PrivateDomainMomentsView.vue'
import PrivateDomainTagManagementView from '../views/PrivateDomainTagManagementView.vue'
import SalaryConfigView from '../views/SalaryConfigView.vue'
import SchedulerCenter from '../views/SchedulerCenter.vue'
import SettlementCenterView from '../views/SettlementCenterView.vue'
import SettlementConfigView from '../views/SettlementConfigView.vue'
import StorePersonnelManagementView from '../views/StorePersonnelManagementView.vue'
import StoreRoleManagementView from '../views/StoreRoleManagementView.vue'
import StoreScheduleManagementView from '../views/StoreScheduleManagementView.vue'
import StoreServiceTemplateView from '../views/StoreServiceTemplateView.vue'
import SystemOrganizationView from '../views/SystemOrganizationView.vue'
import SystemSettingView from '../views/SystemSettingView.vue'

const storeRoleCodes = ['STORE_SERVICE', 'STORE_MANAGER', 'PHOTOGRAPHER', 'MAKEUP_ARTIST', 'PHOTO_SELECTOR', 'ADMIN']

const routes = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      title: '登录'
    }
  },
  {
    path: '/service-scan/:id',
    name: 'plan-orders-scan',
    component: PlanOrderService,
    meta: {
      title: '扫码服务单',
      moduleCode: 'PLANORDER',
      roleCodes: storeRoleCodes,
      standalone: true,
      scanMode: true
    }
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/clues',
    children: [
      {
        path: 'clues',
        name: 'clues',
        component: ClueManagement,
        meta: {
          title: '客资列表',
          sectionTitle: '客资中心',
          moduleCode: 'CLUE',
          navKey: 'clues'
        }
      },
      {
        path: 'clues/scheduling',
        name: 'clues-scheduling',
        component: PaidOrderManagement,
        meta: {
          title: '顾客排档',
          sectionTitle: '客资中心',
          moduleCode: 'ORDER',
          roleCodes: ['ADMIN', 'CLUE_MANAGER', 'ONLINE_CUSTOMER_SERVICE'],
          navKey: 'paid-orders'
        }
      },
      {
        path: 'clues/payments',
        redirect: '/clues/scheduling'
      },
      {
        path: 'clue-management/auto-assign',
        name: 'clue-auto-assign',
        component: ClueAutoAssignmentView,
        meta: {
          title: '自动分配',
          sectionTitle: '客资中心 / 客资管理',
          moduleCode: 'CLUE',
          roleCodes: ['CLUE_MANAGER', 'ADMIN'],
          navKey: 'clue-auto-assign'
        }
      },
      {
        path: 'clue-management/duty-cs',
        name: 'duty-customer-service',
        component: DutyCustomerServiceView,
        meta: {
          title: '值班客服',
          sectionTitle: '客资中心 / 客资管理',
          moduleCode: 'CLUE',
          roleCodes: ['CLUE_MANAGER', 'ADMIN'],
          navKey: 'duty-customer-service'
        }
      },
      {
        path: 'clue-management/store-schedules',
        name: 'store-schedules',
        component: StoreScheduleManagementView,
        meta: {
          title: '门店档期',
          sectionTitle: '客资中心 / 客资管理',
          moduleCode: 'CLUE',
          roleCodes: ['CLUE_MANAGER', 'ADMIN'],
          navKey: 'store-schedules'
        }
      },
      {
        path: 'store-service/orders',
        name: 'store-service-orders',
        component: OrderManagement,
        meta: {
          title: '订单列表',
          sectionTitle: '门店服务',
          moduleCode: 'ORDER',
          roleCodes: storeRoleCodes,
          navKey: 'store-service-orders'
        }
      },
      {
        path: 'store-service/service-design',
        name: 'store-service-design',
        component: StoreServiceTemplateView,
        meta: {
          title: '服务单设计',
          sectionTitle: '门店服务',
          moduleCode: 'PLANORDER',
          roleCodes: ['STORE_MANAGER', 'ADMIN'],
          navKey: 'store-service-design'
        }
      },
      {
        path: 'store-service/personnel',
        name: 'store-service-personnel',
        component: StorePersonnelManagementView,
        meta: {
          title: '人员管理',
          sectionTitle: '门店服务',
          moduleCode: 'SYSTEM',
          roleCodes: ['STORE_MANAGER', 'ADMIN'],
          navKey: 'store-service-personnel'
        }
      },
      {
        path: 'store-service/roles',
        name: 'store-service-roles',
        component: StoreRoleManagementView,
        meta: {
          title: '门店角色',
          sectionTitle: '门店服务',
          moduleCode: 'SYSTEM',
          roleCodes: ['STORE_MANAGER', 'ADMIN'],
          navKey: 'store-service-roles'
        }
      },
      {
        path: 'orders',
        redirect: '/store-service/orders'
      },
      {
        path: 'plan-orders/:id?',
        name: 'plan-orders',
        component: PlanOrderService,
        meta: {
          title: '服务单',
          sectionTitle: '门店服务',
          moduleCode: 'PLANORDER',
          roleCodes: storeRoleCodes,
          navKey: 'store-service-orders',
          hidePageHeader: true
        }
      },
      {
        path: 'customers/:id',
        name: 'customers',
        component: CustomerDetail,
        meta: {
          title: '客户详情',
          sectionTitle: '门店服务',
          moduleCode: 'ORDER',
          roleCodes: storeRoleCodes,
          navKey: 'store-service-orders'
        }
      },
      {
        path: 'private-domain/wecom',
        redirect: '/settings/base/wecom'
      },
      {
        path: 'private-domain/live-code',
        name: 'private-domain-live-code',
        component: PrivateDomainLiveCodeView,
        meta: {
          title: '活码配置',
          sectionTitle: '私域客服',
          moduleCode: 'WECOM',
          roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'],
          navKey: 'private-domain-live-code'
        }
      },
      {
        path: 'private-domain/customer-profile',
        name: 'private-domain-customer-profile',
        component: PrivateDomainCustomerProfileView,
        meta: {
          title: '客户画像',
          sectionTitle: '私域客服',
          moduleCode: 'WECOM',
          roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'],
          navKey: 'private-domain-profile'
        }
      },
      {
        path: 'private-domain/moments',
        name: 'private-domain-moments',
        component: PrivateDomainMomentsView,
        meta: {
          title: '朋友圈定时群发',
          sectionTitle: '私域客服',
          moduleCode: 'WECOM',
          roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'],
          navKey: 'private-domain-moments'
        }
      },
      {
        path: 'private-domain/tags',
        name: 'private-domain-tags',
        component: PrivateDomainTagManagementView,
        meta: {
          title: '标签管理',
          sectionTitle: '私域客服',
          moduleCode: 'WECOM',
          roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'],
          navKey: 'private-domain-tags'
        }
      },
      {
        path: 'finance',
        name: 'finance',
        component: FinanceOverview,
        meta: {
          title: '财务看板',
          sectionTitle: '财务管理',
          moduleCode: 'FINANCE',
          navKey: 'finance'
        }
      },
      {
        path: 'finance/salary/my',
        name: 'salary-my',
        component: MySalaryView,
        meta: {
          title: '我的薪酬',
          sectionTitle: '财务管理',
          moduleCode: 'SALARY',
          navKey: 'salary-my'
        }
      },
      {
        path: 'finance/salary/settlements',
        name: 'salary-settlements',
        component: SettlementCenterView,
        meta: {
          title: '结算中心',
          sectionTitle: '财务管理 / 薪酬结算',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN', 'FINANCE'],
          navKey: 'salary-settlements'
        }
      },
      {
        path: 'finance/salary/withdrawals',
        name: 'salary-withdrawals',
        component: SettlementCenterView,
        meta: {
          title: '提现处理',
          sectionTitle: '财务管理 / 薪酬结算',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN', 'FINANCE'],
          navKey: 'salary-withdrawals',
          settlementCenterMode: 'withdraw'
        }
      },
      {
        path: 'finance/salary/refund-adjustments',
        name: 'salary-refund-adjustments',
        component: SettlementCenterView,
        meta: {
          title: '退款冲正',
          sectionTitle: '财务管理 / 薪酬结算',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN', 'FINANCE'],
          navKey: 'salary-refund-adjustments',
          settlementCenterMode: 'refunds'
        }
      },
      {
        path: 'finance/salary/settlement-config',
        name: 'salary-settlement-config',
        component: SettlementConfigView,
        meta: {
          title: '结算配置',
          sectionTitle: '财务管理 / 薪酬结算',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN', 'FINANCE'],
          navKey: 'salary-settlement-config'
        }
      },
      {
        path: 'finance/salary-config/roles',
        name: 'salary-config-roles',
        component: SalaryConfigView,
        meta: {
          title: '薪酬角色',
          sectionTitle: '财务管理 / 薪酬配置',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN'],
          navKey: 'salary-config-roles',
          salaryConfigMode: 'role'
        }
      },
      {
        path: 'finance/salary-config/grades',
        name: 'salary-config-grades',
        component: SalaryConfigView,
        meta: {
          title: '薪酬档位',
          sectionTitle: '财务管理 / 薪酬配置',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN'],
          navKey: 'salary-config-grades',
          salaryConfigMode: 'grade'
        }
      },
      {
        path: 'finance/salary-config/distributor',
        name: 'salary-config-distributor',
        component: SalaryConfigView,
        meta: {
          title: '分销配置',
          sectionTitle: '财务管理 / 薪酬配置',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN'],
          navKey: 'salary-config-distributor',
          salaryConfigMode: 'distributor'
        }
      },
      {
        path: 'system/departments',
        name: 'system-departments',
        component: SystemOrganizationView,
        meta: {
          title: '部门管理',
          sectionTitle: '系统管理',
          moduleCode: 'SYSTEM',
          roleCodes: ['ADMIN'],
          navKey: 'system-departments',
          orgMode: 'department'
        }
      },
      {
        path: 'system/employees',
        name: 'system-employees',
        component: SystemOrganizationView,
        meta: {
          title: '员工管理',
          sectionTitle: '系统管理',
          moduleCode: 'SYSTEM',
          roleCodes: ['ADMIN', 'CLUE_MANAGER'],
          navKey: 'system-employees',
          orgMode: 'employee'
        }
      },
      {
        path: 'system/positions',
        name: 'system-positions',
        component: SystemOrganizationView,
        meta: {
          title: '岗位管理',
          sectionTitle: '系统管理',
          moduleCode: 'SYSTEM',
          roleCodes: ['ADMIN'],
          navKey: 'system-positions',
          orgMode: 'position'
        }
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: SystemOrganizationView,
        meta: {
          title: '角色管理',
          sectionTitle: '系统管理',
          moduleCode: 'SYSTEM',
          roleCodes: ['ADMIN'],
          navKey: 'system-roles',
          orgMode: 'role'
        }
      },
      {
        path: 'platform/wecom',
        redirect: '/settings/base/wecom'
      },
      {
        path: 'platform/douyin',
        redirect: '/settings/integration/third-party'
      },
      {
        path: 'platform/runtime',
        redirect: '/settings/integration/callback'
      },
      {
        path: 'settings/integration/third-party',
        name: 'settings-third-party',
        component: PlatformDouyinConfigView,
        meta: {
          title: '抖音接口',
          sectionTitle: '系统设置 / 调度中心',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-third-party'
        }
      },
      {
        path: 'settings/integration/callback',
        name: 'settings-callback',
        component: PlatformRuntimeView,
        meta: {
          title: '回调接口',
          sectionTitle: '系统设置 / 调度中心',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-callback',
          runtimeTab: 'wecom'
        }
      },
      {
        path: 'settings/integration/jobs',
        name: 'settings-jobs',
        component: SchedulerCenter,
        meta: {
          title: '任务调度',
          sectionTitle: '系统设置 / 调度中心',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-jobs'
        }
      },
      {
        path: 'settings/integration/debug',
        name: 'settings-interface-debug',
        component: InterfaceDebugView,
        meta: {
          title: '接口调试',
          sectionTitle: '系统设置 / 调度中心',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-interface-debug'
        }
      },
      {
        path: 'settings/integration/distribution-api',
        name: 'settings-distribution-api',
        component: DistributionApiView,
        meta: {
          title: '分销接口',
          sectionTitle: '系统设置 / 调度中心',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-distribution-api'
        }
      },
      {
        path: 'settings/base/domain',
        name: 'settings-domain',
        component: DomainSettingView,
        meta: {
          title: '域名配置',
          sectionTitle: '系统设置 / 基础配置',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-domain'
        }
      },
      {
        path: 'settings/base/wecom',
        name: 'settings-wecom',
        component: PlatformWecomConfigView,
        meta: {
          title: '企业微信',
          sectionTitle: '系统设置 / 基础配置',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-wecom'
        }
      },
      {
        path: 'settings/menu',
        name: 'settings-menu',
        component: SystemSettingView,
        meta: {
          title: '菜单管理',
          sectionTitle: '系统设置',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-menu',
          settingMode: 'menu'
        }
      },
      {
        path: 'settings/integration/public-api',
        name: 'settings-public-api',
        component: SystemSettingView,
        meta: {
          title: '对外接口',
          sectionTitle: '系统设置 / 调度中心',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-public-api',
          settingMode: 'public-api'
        }
      },
      {
        path: 'settings/dictionaries',
        name: 'settings-dictionaries',
        component: SystemSettingView,
        meta: {
          title: '字典管理',
          sectionTitle: '系统设置',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-dictionaries',
          settingMode: 'dictionary'
        }
      },
      {
        path: 'settings/parameters',
        name: 'settings-parameters',
        component: SystemSettingView,
        meta: {
          title: '参数管理',
          sectionTitle: '系统设置',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-parameters',
          settingMode: 'parameter'
        }
      },
      {
        path: 'settings/payment',
        name: 'settings-payment',
        component: PaymentSettingView,
        meta: {
          title: '支付设置',
          sectionTitle: '系统设置',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-payment'
        }
      },
      {
        path: 'salary',
        redirect: '/finance/salary/my'
      },
      {
        path: 'finance/salary-center',
        redirect: '/finance/salary/my'
      },
      {
        path: 'scheduler',
        redirect: '/settings/integration/third-party'
      },
      {
        path: 'permission',
        redirect: '/system/roles'
      },
      {
        path: 'distributors',
        redirect: '/clues'
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  await initializeAuth()

  if (to.path === '/login') {
    const target = getFirstAccessibleRoute()
    return target === '/login' ? true : target
  }

  const target = getFirstAccessibleRoute()
  if (target === '/login') {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (!hasRouteAccess(to.path, to.meta?.moduleCode, to.meta?.roleCodes)) {
    return target
  }

  return true
})

export default router
