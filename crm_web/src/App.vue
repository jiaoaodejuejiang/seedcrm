<template>
  <div class="page-shell">
    <header class="hero">
      <div class="hero__copy">
        <p class="eyebrow">Seed CRM</p>
        <h1>客户管理控制台</h1>
        <p class="hero__text">
          一个基于 Vue 3 + Vite 的前端起始页，适合继续扩展客户列表、跟进记录、线索漏斗和销售仪表盘。
        </p>
        <div class="hero__actions">
          <button class="primary-btn">新建客户</button>
          <button class="ghost-btn">查看报表</button>
        </div>
      </div>
      <div class="hero__panel">
        <div class="panel-card">
          <span>本周新增线索</span>
          <strong>128</strong>
          <small>较上周增长 18%</small>
        </div>
        <div class="panel-card panel-card--accent">
          <span>待跟进商机</span>
          <strong>24</strong>
          <small>优先处理 6 个高意向客户</small>
        </div>
      </div>
    </header>

    <main class="content-grid">
      <section class="glass-card">
        <div class="section-title">
          <h2>核心数据</h2>
          <span>实时概览</span>
        </div>
        <div class="stats-grid">
          <article v-for="item in stats" :key="item.label" class="stat-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.desc }}</small>
          </article>
        </div>
      </section>

      <section class="glass-card">
        <div class="section-title">
          <h2>待办事项</h2>
          <span>今日安排</span>
        </div>
        <ul class="task-list">
          <li v-for="task in tasks" :key="task.title" class="task-item">
            <div>
              <strong>{{ task.title }}</strong>
              <p>{{ task.detail }}</p>
            </div>
            <span class="task-tag">{{ task.time }}</span>
          </li>
        </ul>
      </section>
    </main>
  </div>
</template>

<script setup>
const stats = [
  { label: '客户总数', value: '1,248', desc: '活跃客户 932 位' },
  { label: '本月成交', value: '86', desc: '签约金额 ¥486,000' },
  { label: '转化率', value: '32%', desc: '较上月提升 4.8%' },
  { label: '待回访', value: '57', desc: '重点客户 12 位' }
]

const tasks = [
  { title: '跟进华东区重点客户', detail: '确认续约方案并同步报价单。', time: '09:30' },
  { title: '整理销售周报', detail: '汇总新增线索、成交和流失原因。', time: '13:30' },
  { title: '产品演示会议', detail: '向新客户展示 CRM 自动化流程。', time: '16:00' }
]
</script>
