<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>企微联系人</span>
        <strong>{{ state.wecomContacts.length }}</strong>
        <small>维护企业微信触达用的客服或服务账号。</small>
      </article>
      <article class="metric-card">
        <span>触达规则</span>
        <strong>{{ state.wecomRules.filter((item) => item.isEnabled === 1).length }}</strong>
        <small>按预约、完成等节点做自动触达编排。</small>
      </article>
      <article class="metric-card">
        <span>最近发送</span>
        <strong>{{ sendLogs.length }}</strong>
        <small>支持手工发送消息并查看最近发送结果。</small>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>企业微信手动触达</h3>
          <p>针对已绑定客户发送企业微信消息，方便私域客服跟进。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>客户 ID</span>
          <el-input-number v-model="sendForm.customerId" :min="1" controls-position="right" />
        </label>
        <label>
          <span>触达联系人</span>
          <el-select v-model="selectedContactId" placeholder="请选择企微联系人">
            <el-option v-for="item in state.wecomContacts" :key="item.id" :label="item.contactName" :value="item.id" />
          </el-select>
        </label>
        <label class="full-span">
          <span>发送内容</span>
          <el-input v-model="sendForm.message" type="textarea" :rows="4" placeholder="请输入企业微信触达内容" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" :loading="sending" @click="handleSend">发送消息</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>触达规则</h3>
          <p>可维护不同业务节点的私域触达模板和启停状态。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>规则名称</span>
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </label>
        <label>
          <span>触发场景</span>
          <el-input v-model="ruleForm.triggerScene" placeholder="如 APPOINTMENT / COMPLETED" />
        </label>
        <label class="full-span">
          <span>消息模板</span>
          <el-input v-model="ruleForm.template" type="textarea" :rows="3" placeholder="请输入触达模板" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveRule">保存规则</el-button>
        <el-button @click="resetRuleForm">重置表单</el-button>
      </div>

      <el-table :data="state.wecomRules" stripe>
        <el-table-column label="规则名称" min-width="180" prop="ruleName" />
        <el-table-column label="触发场景" width="160" prop="triggerScene" />
        <el-table-column label="消息模板" min-width="260" prop="template" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickRule(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleRule(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>企微联系人</h3>
          <p>设置不同触达场景使用的企业微信联系人或客服账号。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>联系人名称</span>
          <el-input v-model="contactForm.contactName" placeholder="请输入联系人名称" />
        </label>
        <label>
          <span>企微外部 ID</span>
          <el-input v-model="contactForm.externalUserId" placeholder="请输入企微外部 ID" />
        </label>
        <label class="full-span">
          <span>服务场景</span>
          <el-input v-model="contactForm.scene" placeholder="如 术前咨询 / 术后回访" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveContact">保存联系人</el-button>
        <el-button @click="resetContactForm">重置表单</el-button>
      </div>

      <el-table :data="state.wecomContacts" stripe>
        <el-table-column label="联系人" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.contactName }}</strong>
              <span>{{ row.externalUserId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="服务场景" min-width="180" prop="scene" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickContact(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleContact(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>最近发送记录</h3>
          <p>手工发送成功后会在这里追加最近一条触达记录。</p>
        </div>
      </div>

      <el-table :data="sendLogs" stripe>
        <el-table-column label="发送时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt || row.sentAt) }}
          </template>
        </el-table-column>
        <el-table-column label="客户 ID" width="120" prop="customerId" />
        <el-table-column label="联系人" width="160" prop="contactName" />
        <el-table-column label="内容" min-width="260" prop="message" />
        <el-table-column label="结果" width="120">
          <template #default="{ row }">
            <el-tag type="success">{{ row.statusLabel || '已发送' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sendWecomMessage } from '../api/wecom'
import { formatDateTime } from '../utils/format'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const sending = ref(false)
const selectedContactId = ref(state.wecomContacts[0]?.id || null)
const sendLogs = ref([])

const sendForm = reactive({
  customerId: 301,
  message: '您好，这里是私域客服，欢迎添加企业微信继续咨询。'
})

const ruleForm = reactive(createRuleForm())
const contactForm = reactive(createContactForm())

function createRuleForm() {
  return {
    id: null,
    ruleName: '',
    triggerScene: '',
    template: ''
  }
}

function createContactForm() {
  return {
    id: null,
    contactName: '',
    externalUserId: '',
    scene: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetRuleForm() {
  Object.assign(ruleForm, createRuleForm())
}

function resetContactForm() {
  Object.assign(contactForm, createContactForm())
}

async function handleSend() {
  if (!sendForm.customerId || !sendForm.message) {
    ElMessage.warning('请先填写客户 ID 和发送内容')
    return
  }
  sending.value = true
  try {
    const contact = state.wecomContacts.find((item) => item.id === selectedContactId.value)
    const response = await sendWecomMessage({
      customerId: sendForm.customerId,
      message: sendForm.message
    })
    sendLogs.value = [
      {
        ...response,
        sentAt: new Date().toISOString(),
        contactName: contact?.contactName || '--',
        statusLabel: '已发送',
        message: sendForm.message
      },
      ...sendLogs.value
    ].slice(0, 10)
    ElMessage.success('企业微信消息已发送')
  } finally {
    sending.value = false
  }
}

function saveRule() {
  if (!ruleForm.ruleName || !ruleForm.triggerScene || !ruleForm.template) {
    ElMessage.warning('请先完整填写规则信息')
    return
  }
  const nextRules = [...state.wecomRules]
  if (ruleForm.id) {
    const index = nextRules.findIndex((item) => item.id === ruleForm.id)
    nextRules[index] = { ...nextRules[index], ...ruleForm }
  } else {
    nextRules.push({
      ...ruleForm,
      id: nextSystemId(nextRules),
      isEnabled: 1
    })
  }
  replaceState({ ...state, wecomRules: nextRules })
  ElMessage.success('触达规则已保存')
  resetRuleForm()
}

function pickRule(row) {
  Object.assign(ruleForm, { ...row })
}

function toggleRule(row) {
  const nextRules = state.wecomRules.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, wecomRules: nextRules })
  ElMessage.success('规则状态已更新')
}

function saveContact() {
  if (!contactForm.contactName || !contactForm.externalUserId) {
    ElMessage.warning('请先填写联系人信息')
    return
  }
  const nextContacts = [...state.wecomContacts]
  if (contactForm.id) {
    const index = nextContacts.findIndex((item) => item.id === contactForm.id)
    nextContacts[index] = { ...nextContacts[index], ...contactForm }
  } else {
    nextContacts.push({
      ...contactForm,
      id: nextSystemId(nextContacts),
      isEnabled: 1
    })
  }
  replaceState({ ...state, wecomContacts: nextContacts })
  ElMessage.success('企微联系人已保存')
  resetContactForm()
}

function pickContact(row) {
  Object.assign(contactForm, { ...row })
}

function toggleContact(row) {
  const nextContacts = state.wecomContacts.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, wecomContacts: nextContacts })
  ElMessage.success('联系人状态已更新')
}
</script>
