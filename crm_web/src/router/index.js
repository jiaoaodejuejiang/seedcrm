import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { getFirstAccessibleRoute, hasAccess, initializeAuth } from '../utils/auth'
import ClueManagement from '../views/ClueManagement.vue'
import ClueAutoAssignmentView from '../views/ClueAutoAssignmentView.vue'
import CustomerDetail from '../views/CustomerDetail.vue'
import DistributorManagement from '../views/DistributorManagement.vue'
import DutyCustomerServiceView from '../views/DutyCustomerServiceView.vue'
import FinanceOverview from '../views/FinanceOverview.vue'
import LoginView from '../views/LoginView.vue'
import OrderManagement from '../views/OrderManagement.vue'
import PermissionCenter from '../views/PermissionCenter.vue'
import PlanOrderService from '../views/PlanOrderService.vue'
import SalaryCenter from '../views/SalaryCenter.vue'
import SchedulerCenter from '../views/SchedulerCenter.vue'

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
          title: '客资中心',
          sectionTitle: '客资中心',
          description: '统一查看自动拉取的客资、手动分配、公海回收和转订单主链。',
          moduleCode: 'CLUE',
          navKey: 'clues'
        }
      },
      {
        path: 'clue-management/auto-assign',
        name: 'clue-auto-assign',
        component: ClueAutoAssignmentView,
        meta: {
          title: '自动分配',
          sectionTitle: '客资管理',
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
          sectionTitle: '客资管理',
          description: '设置客服班次、当值状态和请假情况，为自动分配提供当值名单。',
          moduleCode: 'CLUE',
          roleCodes: ['CLUE_MANAGER', 'ADMIN'],
          navKey: 'duty-customer-service'
        }
      },
      {
        path: 'store-service/orders',
        name: 'store-service-orders',
        component: OrderManagement,
        meta: {
          title: '订单列表',
          sectionTitle: '门店服务',
          description: '查看全部状态订单，切换已预约和已完成，并填写确认单与到店详细需求。',
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
          description: '严格执行到店、服务中、已完成的服务单履约链路。',
          moduleCode: 'PLANORDER',
          navKey: 'store-service-orders'
        }
      },
      {
        path: 'customers/:id',
        name: 'customers',
        component: CustomerDetail,
        meta: {
          title: '客户详情',
          sectionTitle: '客户档案',
          description: '查看客户资料、订单历史、企微绑定和最近触达记录。',
          moduleCode: 'ORDER',
          navKey: 'store-service-orders'
        }
      },
      {
        path: 'scheduler',
        name: 'scheduler',
        component: SchedulerCenter,
        meta: {
          title: '调度中心',
          sectionTitle: '调度中心',
          description: '管理外部调用、增量同步、队列处理、失败重试与日志。',
          moduleCode: 'SCHEDULER',
          navKey: 'scheduler'
        }
      },
      {
        path: 'permission',
        name: 'permission',
        component: PermissionCenter,
        meta: {
          title: '权限中心',
          sectionTitle: '权限中心',
          description: '维护 RBAC 与 ABAC 策略，并使用当前登录用户实时校验权限。',
          moduleCode: 'PERMISSION',
          navKey: 'permission'
        }
      },
      {
        path: 'salary',
        name: 'salary',
        component: SalaryCenter,
        meta: {
          title: '薪酬中心',
          sectionTitle: '薪酬中心',
          description: '基于 order_role_record 计算薪酬，并支持结算、打款和提现。',
          moduleCode: 'SALARY',
          navKey: 'salary'
        }
      },
      {
        path: 'distributors',
        name: 'distributors',
        component: DistributorManagement,
        meta: {
          title: '分销概览',
          sectionTitle: '分销概览',
          description: '查看分销线索贡献、订单转化和收益表现。',
          moduleCode: 'DISTRIBUTOR',
          navKey: 'distributors'
        }
      },
      {
        path: 'finance',
        name: 'finance',
        component: FinanceOverview,
        meta: {
          title: '财务总览',
          sectionTitle: '财务总览',
          description: '查看员工与分销两侧的收入、结算和提现记录。',
          moduleCode: 'FINANCE',
          navKey: 'finance'
        }
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

  if (!hasAccess(to.meta.moduleCode, to.meta.roleCodes)) {
    return target
  }

  return true
})

export default router
