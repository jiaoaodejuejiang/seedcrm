<template>
  <div class="stack-page lowcode-platform-page">
    <section class="summary-strip summary-strip--compact">
      <article class="summary-pill">
        <span>定位</span>
        <strong>受控低代码</strong>
      </article>
      <article class="summary-pill">
        <span>主链路</span>
        <strong>固定不变</strong>
      </article>
      <article class="summary-pill">
        <span>落地阶段</span>
        <strong>L1 配置版本底座</strong>
      </article>
      <article class="summary-pill">
        <span>执行方式</span>
        <strong>入口 + 发布中心</strong>
      </article>
    </section>

    <section class="panel lowcode-hero">
      <div>
        <el-tag effect="light" type="warning">方案入口</el-tag>
        <h3>配置化平台</h3>
        <p>这里作为受控低代码的统一入口，集中查看主链路兼容方案、UI 呈现附录，并进入配置发布中心处理草稿、预览、发布和回滚。</p>
      </div>
      <div class="action-group">
        <el-button type="success" @click="goConfigPublishCenter">进入配置发布中心</el-button>
        <el-button @click="openDoc('/docs/controlled-low-code-compatibility-plan.html')">打开完整方案</el-button>
        <el-button type="primary" @click="openDoc('/docs/controlled-low-code-ui-showcase.html')">打开 UI 附录</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>方案文档</h3>
        </div>
        <div class="action-group">
          <el-button @click="activeDoc = 'plan'">受控低代码方案</el-button>
          <el-button @click="activeDoc = 'ui'">UI 呈现附录</el-button>
        </div>
      </div>

      <div class="doc-cards">
        <article
          v-for="item in docs"
          :key="item.key"
          class="doc-card"
          :class="{ 'is-active': activeDoc === item.key }"
          @click="activeDoc = item.key"
        >
          <el-tag :type="item.tagType" effect="light">{{ item.tag }}</el-tag>
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
          <el-button link type="primary" @click.stop="openDoc(item.path)">新窗口打开</el-button>
        </article>
      </div>

      <div class="doc-preview">
        <iframe :src="currentDoc.path" :title="currentDoc.title" />
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>硬边界</h3>
        </div>
      </div>
      <div class="boundary-grid">
        <article v-for="item in boundaries" :key="item.title" class="boundary-card">
          <el-tag :type="item.type" effect="light">{{ item.label }}</el-tag>
          <strong>{{ item.title }}</strong>
          <span>{{ item.description }}</span>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const docs = [
  {
    key: 'plan',
    title: '受控低代码兼容主链路方案',
    description: '说明低代码能力的边界、数据结构、接口定义、权限规则和发布流程。',
    path: '/docs/controlled-low-code-compatibility-plan.html',
    tag: '方案',
    tagType: 'primary'
  },
  {
    key: 'ui',
    title: '受控低代码 UI 呈现附录',
    description: '展示配置化平台首页、配置向导、设计器、权限配置、服务单设计和发布中心。',
    path: '/docs/controlled-low-code-ui-showcase.html',
    tag: 'UI',
    tagType: 'success'
  }
]

const boundaries = [
  {
    label: '主链路',
    type: 'danger',
    title: '不改变 Clue -> Customer -> Order -> PlanOrder',
    description: '配置化平台只影响展示、入口、校验和门禁，不改变核心业务流转。'
  },
  {
    label: '分销 B',
    type: 'warning',
    title: '分销 paid order 不进入 Clue',
    description: '外部分销成交仍走 Customer + Order(paid) + PlanOrder，不进入线索分配链路。'
  },
  {
    label: '写入',
    type: 'danger',
    title: '配置按钮不能直写核心表',
    description: '按钮只调用已有受控业务接口，不能直接写 Customer、Order、PlanOrder。'
  },
  {
    label: '调度',
    type: 'info',
    title: 'Provider / Scheduler 必须 dry-run 后发布',
    description: '外部接口和调度配置继续受 Outbox、异常队列、统一入站服务保护。'
  }
]

const activeDoc = ref('ui')
const currentDoc = computed(() => docs.find((item) => item.key === activeDoc.value) || docs[0])

function openDoc(path) {
  window.open(path, '_blank', 'noopener,noreferrer')
}

function goConfigPublishCenter() {
  router.push('/settings/base/config-audit')
}
</script>

<style scoped>
.lowcode-platform-page {
  gap: 18px;
}

.lowcode-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.lowcode-hero h3 {
  margin: 10px 0 6px;
  font-size: 24px;
}

.lowcode-hero p {
  margin: 0;
  color: #64748b;
}

.doc-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.doc-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid #e5eaf3;
  border-radius: 18px;
  background: #fbfdff;
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}

.doc-card strong {
  font-size: 16px;
  color: #172033;
}

.doc-card span {
  color: #64748b;
}

.doc-card.is-active {
  border-color: #2563eb;
  box-shadow: 0 14px 28px rgba(37, 99, 235, .12);
  transform: translateY(-1px);
}

.doc-preview {
  overflow: hidden;
  border: 1px solid #dbe5f3;
  border-radius: 18px;
  background: #f8fafc;
}

.doc-preview iframe {
  display: block;
  width: 100%;
  min-height: 680px;
  border: 0;
  background: #fff;
}

.boundary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.boundary-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid #e5eaf3;
  border-radius: 18px;
  background: #fbfdff;
}

.boundary-card strong {
  color: #172033;
}

.boundary-card span {
  color: #64748b;
}

@media (max-width: 1100px) {
  .boundary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .lowcode-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .doc-cards,
  .boundary-grid {
    grid-template-columns: 1fr;
  }

  .doc-preview iframe {
    min-height: 560px;
  }
}
</style>
