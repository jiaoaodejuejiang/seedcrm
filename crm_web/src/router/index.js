import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import ClueManagement from '../views/ClueManagement.vue'
import CustomerDetail from '../views/CustomerDetail.vue'
import DistributorManagement from '../views/DistributorManagement.vue'
import FinanceOverview from '../views/FinanceOverview.vue'
import OrderManagement from '../views/OrderManagement.vue'
import PlanOrderService from '../views/PlanOrderService.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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
            description: '客服接收新线索、认领跟进，并一键转成订单。'
          }
        },
        {
          path: 'orders',
          name: 'orders',
          component: OrderManagement,
          meta: {
            title: '订单管理',
            description: '用订单作为业务中台入口，直接进入到店服务。'
          }
        },
        {
          path: 'plan-orders/:id?',
          name: 'plan-orders',
          component: PlanOrderService,
          meta: {
            title: '到店服务',
            description: '在一个页面里完成到店、服务、角色切换和完成闭环。'
          }
        },
        {
          path: 'customers/:id',
          name: 'customers',
          component: CustomerDetail,
          meta: {
            title: '客户详情',
            description: '查看客户画像、历史订单、企微绑定和电商沉淀。'
          }
        },
        {
          path: 'distributors',
          name: 'distributors',
          component: DistributorManagement,
          meta: {
            title: '分销管理',
            description: '聚焦分销带客结果、订单转化和收益表现。'
          }
        },
        {
          path: 'finance',
          name: 'finance',
          component: FinanceOverview,
          meta: {
            title: '财务概览',
            description: '只保留门店真正需要看的收入、收益和提现记录。'
          }
        }
      ]
    }
  ]
})

export default router
