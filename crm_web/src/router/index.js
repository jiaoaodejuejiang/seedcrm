import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { getFirstAccessibleRoute, hasAccess, initializeAuth } from '../utils/auth'
import ClueManagement from '../views/ClueManagement.vue'
import CustomerDetail from '../views/CustomerDetail.vue'
import DistributorManagement from '../views/DistributorManagement.vue'
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
      description: '登录后按角色自动应用权限和数据范围。'
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
          title: '线索管理',
          description: '统一接入抖音与分销客资，支持自动拉取、分配、回收与后续转化。',
          moduleCode: 'CLUE'
        }
      },
      {
        path: 'orders',
        name: 'orders',
        component: OrderManagement,
        meta: {
          title: '订单管理',
          description: '订单必须绑定客户，且仅允许定金、卡券与已支付/已使用状态。',
          moduleCode: 'ORDER'
        }
      },
      {
        path: 'plan-orders/:id?',
        name: 'plan-orders',
        component: PlanOrderService,
        meta: {
          title: '服务单履约',
          description: '严格执行已到店 -> 服务中 -> 已完成，并完整记录角色流转。',
          moduleCode: 'PLANORDER'
        }
      },
      {
        path: 'customers/:id',
        name: 'customers',
        component: CustomerDetail,
        meta: {
          title: '客户详情',
          description: '只读查看客户资料、订单历史、企微绑定与履约信息。',
          moduleCode: 'ORDER'
        }
      },
      {
        path: 'scheduler',
        name: 'scheduler',
        component: SchedulerCenter,
        meta: {
          title: '调度中心',
          description: '管理 1 分钟增量同步、队列处理、失败重试与执行日志。',
          moduleCode: 'SCHEDULER'
        }
      },
      {
        path: 'permission',
        name: 'permission',
        component: PermissionCenter,
        meta: {
          title: '权限中心',
          description: '维护 RBAC/ABAC 规则，并基于当前登录用户实时校验权限。',
          moduleCode: 'PERMISSION'
        }
      },
      {
        path: 'salary',
        name: 'salary',
        component: SalaryCenter,
        meta: {
          title: '薪酬中心',
          description: '基于 order_role_record 计算薪酬，并支持结算、打款和提现。',
          moduleCode: 'SALARY'
        }
      },
      {
        path: 'distributors',
        name: 'distributors',
        component: DistributorManagement,
        meta: {
          title: '分销概览',
          description: '查看分销线索贡献、订单转化和收益表现。',
          moduleCode: 'DISTRIBUTOR'
        }
      },
      {
        path: 'finance',
        name: 'finance',
        component: FinanceOverview,
        meta: {
          title: '财务总览',
          description: '查看员工与分销两侧的收入与提现记录。',
          moduleCode: 'FINANCE'
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

  if (to.meta.moduleCode && !hasAccess(to.meta.moduleCode)) {
    return target
  }

  return true
})

export default router
