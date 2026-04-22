<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <p class="brand-mark">Seed CRM</p>
        <h1>门店业务工作台</h1>
        <p>围绕线索、订单、到店、企微和收益，把真正会被操作的页面串起来。</p>
      </div>

      <nav class="nav-stack">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-card"
          :class="{ 'is-active': route.path.startsWith(item.to) }"
        >
          <span class="nav-card__label">{{ item.label }}</span>
          <small>{{ item.description }}</small>
        </RouterLink>
      </nav>

      <div class="sidebar-tip">
        <strong>最短操作流</strong>
        <p>线索页转订单，订单页直接进入到店服务，到店页内完成状态和角色处理。</p>
      </div>
    </aside>

    <main class="main-panel">
      <section class="page-header">
        <div>
          <p class="page-header__eyebrow">Store Operation</p>
          <h2>{{ route.meta.title }}</h2>
          <p>{{ route.meta.description }}</p>
        </div>
      </section>

      <section class="page-body">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup>
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  {
    to: '/clues',
    label: '线索管理',
    description: '接收、认领、转订单'
  },
  {
    to: '/orders',
    label: '订单管理',
    description: '订单列表与到店入口'
  },
  {
    to: '/plan-orders',
    label: '到店服务',
    description: '状态推进与角色切换'
  },
  {
    to: '/distributors',
    label: '分销管理',
    description: '带客与收益表现'
  },
  {
    to: '/finance',
    label: '财务概览',
    description: '收入、收益、提现'
  }
]
</script>
