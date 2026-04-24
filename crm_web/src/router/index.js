import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { getFirstAccessibleRoute, hasAccess, initializeAuth } from '../utils/auth'
import ClueManagement from '../views/ClueManagement.vue'
import ClueAutoAssignmentView from '../views/ClueAutoAssignmentView.vue'
import CustomerDetail from '../views/CustomerDetail.vue'
import DutyCustomerServiceView from '../views/DutyCustomerServiceView.vue'
import FinanceOverview from '../views/FinanceOverview.vue'
import LoginView from '../views/LoginView.vue'
import OrderManagement from '../views/OrderManagement.vue'
import PaidOrderManagement from '../views/PaidOrderManagement.vue'
import PlanOrderService from '../views/PlanOrderService.vue'
import PrivateDomainCustomerProfileView from '../views/PrivateDomainCustomerProfileView.vue'
import PrivateDomainLiveCodeView from '../views/PrivateDomainLiveCodeView.vue'
import PrivateDomainMomentsView from '../views/PrivateDomainMomentsView.vue'
import PrivateDomainServiceView from '../views/PrivateDomainServiceView.vue'
import PrivateDomainTagManagementView from '../views/PrivateDomainTagManagementView.vue'
import SalaryCenter from '../views/SalaryCenter.vue'
import SalaryConfigView from '../views/SalaryConfigView.vue'
import StoreScheduleManagementView from '../views/StoreScheduleManagementView.vue'
import SystemOrganizationView from '../views/SystemOrganizationView.vue'
import SystemSettingView from '../views/SystemSettingView.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      title: '登录',
      description: '登录后自动注入角色、数据范围和模块权限。'
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
          description: '统一查看自动拉取线索，支持名单整理、打标签、查看详情和预约门店档期。',
          moduleCode: 'CLUE',
          navKey: 'clues'
        }
      },
      {
        path: 'clues/scheduling',
        name: 'clues-payments',
        component: PaidOrderManagement,
        meta: {
          title: '顾客排档',
          sectionTitle: '客资中心',
          description: '查看已付款客资并按门店档期安排预约，客服在这里为门店做排档处理。',
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
          description: '由客资主管维护自动分配策略，V1 固定为自动轮询当值客服。',
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
          description: '设置客服班次、当值状态和请假情况，为自动分配提供当值名单。',
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
          sectionTitle: '客资中心 / 门店档期',
          description: '按门店配置上下班时间与每档服务时长，日历中满档日期不可继续预约。',
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
          description: '已预约订单可打开确认单进行服务项目确认，已完成订单可打开确认单查看历史内容。',
          moduleCode: 'ORDER',
          roleCodes: ['STORE_SERVICE', 'ADMIN'],
          navKey: 'store-service-orders'
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
          title: '服务单履约',
          sectionTitle: '门店服务',
          description: '服务单已归并到订单列表链路中，从确认单或订单操作直接进入履约。',
          moduleCode: 'PLANORDER',
          roleCodes: ['STORE_SERVICE', 'ADMIN'],
          navKey: 'store-service-orders'
        }
      },
      {
        path: 'customers/:id',
        name: 'customers',
        component: CustomerDetail,
        meta: {
          title: '客户详情',
          sectionTitle: '门店服务',
          description: '查看客户资料、订单历史、企微绑定和最近触达记录。',
          moduleCode: 'ORDER',
          roleCodes: ['STORE_SERVICE', 'ADMIN'],
          navKey: 'store-service-orders'
        }
      },
      {
        path: 'private-domain/wecom',
        name: 'private-domain-wecom',
        component: PrivateDomainServiceView,
        meta: {
          title: '企业微信',
          sectionTitle: '私域客服',
          description: '配置企微联系人、触达规则，并支持手工发送客户消息。',
          moduleCode: 'WECOM',
          roleCodes: ['ADMIN', 'PRIVATE_DOMAIN_SERVICE'],
          navKey: 'private-domain-wecom'
        }
      },
      {
        path: 'private-domain/live-code',
        name: 'private-domain-live-code',
        component: PrivateDomainLiveCodeView,
        meta: {
          title: '活码配置',
          sectionTitle: '私域客服',
          description: '配置轮询员工列表并生成企业微信活码，供门店服务人员营销引流时使用。',
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
          description: '读取系统参数中的画像开关，并维护在企业微信端展示的客户画像内容。',
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
          description: '面向企业微信好友朋友圈统一配置定时群发任务。',
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
          title: '便签管理',
          sectionTitle: '私域客服',
          description: '维护客户标签、同步状态和按标签统计的数据看板。',
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
          description: '查看员工与分销两侧的收入、结算和提现记录。',
          moduleCode: 'FINANCE',
          navKey: 'finance'
        }
      },
      {
        path: 'finance/salary-center',
        name: 'salary-center',
        component: SalaryCenter,
        meta: {
          title: '薪酬中心',
          sectionTitle: '财务管理',
          description: '基于 order_role_record 计算薪酬，并支持结算、打款和提现。',
          moduleCode: 'SALARY',
          navKey: 'salary-center'
        }
      },
      {
        path: 'finance/salary-config/roles',
        name: 'salary-config-roles',
        component: SalaryConfigView,
        meta: {
          title: '薪酬角色',
          sectionTitle: '财务管理 / 薪酬配置',
          description: '配置薪酬角色、角色人员和启停状态。',
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
          description: '配置个人档、团队档、转化节点和奖励金额。',
          moduleCode: 'SALARY',
          roleCodes: ['ADMIN'],
          navKey: 'salary-config-grades',
          salaryConfigMode: 'grade'
        }
      },
      {
        path: 'system/departments',
        name: 'system-departments',
        component: SystemOrganizationView,
        meta: {
          title: '部门管理',
          sectionTitle: '系统管理',
          description: '维护组织架构，并让各部门只处理本部门分配和有效的数据。',
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
          description: '添加、停用和调岗员工；停用前必须先处理名下数据。',
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
          description: '配置公司内部岗位，并在删除岗位时自动迁移名下员工。',
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
          description: '维护角色、配置人员和授权策略，原权限中心能力已融合到这里。',
          moduleCode: 'SYSTEM',
          roleCodes: ['ADMIN'],
          navKey: 'system-roles',
          orgMode: 'role'
        }
      },
      {
        path: 'settings/menu',
        name: 'settings-menu',
        component: SystemSettingView,
        meta: {
          title: '菜单管理',
          sectionTitle: '系统设置',
          description: '配置页面菜单、分配角色可见范围，并统一整理系统入口。',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-menu',
          settingMode: 'menu'
        }
      },
      {
        path: 'settings/integration/third-party',
        name: 'settings-third-party',
        component: SystemSettingView,
        meta: {
          title: '三方接口',
          sectionTitle: '系统设置 / 调度中心',
          description: '配置三方 API 地址和拉取方式，客资中心当前数据来源于此。',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-third-party',
          settingMode: 'third-party'
        }
      },
      {
        path: 'settings/integration/callback',
        name: 'settings-callback',
        component: SystemSettingView,
        meta: {
          title: '回调接口',
          sectionTitle: '系统设置 / 调度中心',
          description: '维护回调地址与签名方式，统一接收外部异步通知。',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-callback',
          settingMode: 'callback'
        }
      },
      {
        path: 'settings/integration/jobs',
        name: 'settings-jobs',
        component: SystemSettingView,
        meta: {
          title: '任务调度',
          sectionTitle: '系统设置 / 调度中心',
          description: '参考任务调度中心方式管理三方调用、定时同步和失败重试。',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-jobs',
          settingMode: 'jobs'
        }
      },
      {
        path: 'settings/integration/public-api',
        name: 'settings-public-api',
        component: SystemSettingView,
        meta: {
          title: '对外接口',
          sectionTitle: '系统设置 / 调度中心',
          description: '配置对外查询接口、字段映射、认证、限流和缓存策略。',
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
          description: '维护系统字典编码和值，让页面展示中文、字段存储编码。',
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
          description: '统一维护系统参数键值，供各业务模块直接调用。',
          moduleCode: 'SETTING',
          roleCodes: ['ADMIN'],
          navKey: 'settings-parameters',
          settingMode: 'parameter'
        }
      },
      {
        path: 'salary',
        redirect: '/finance/salary-center'
      },
      {
        path: 'scheduler',
        redirect: '/settings/integration/jobs'
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

  if (!hasAccess(to.meta?.moduleCode, to.meta?.roleCodes)) {
    return target
  }

  return true
})

export default router
