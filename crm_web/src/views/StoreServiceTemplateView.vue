<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>模板总数</span>
        <strong>{{ state.serviceFormTemplates.length }}</strong>
      </article>
      <article class="metric-card">
        <span>有效门店绑定</span>
        <strong>{{ state.serviceFormBindings.filter((item) => item.enabled === 1).length }}</strong>
      </article>
      <article class="metric-card">
        <span>推荐模板</span>
        <strong>{{ state.serviceFormTemplates.filter((item) => item.recommended === 1).length }}</strong>
      </article>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>模板库</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="resetTemplateForm">新增模板</el-button>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>模板名称</span>
          <el-input v-model="templateForm.templateName" placeholder="请输入模板名称" />
        </label>
        <label>
          <span>模板编码</span>
          <el-input v-model="templateForm.templateCode" placeholder="如 STORE_CLASSIC" />
        </label>
        <label>
          <span>表单标题</span>
          <el-input v-model="templateForm.title" placeholder="请输入服务单标题" />
        </label>
        <label>
          <span>适用行业</span>
          <el-input v-model="templateForm.industry" placeholder="如 肖像摄影 / 医美咨询" />
        </label>
        <label>
          <span>布局模式</span>
          <el-select v-model="templateForm.layoutMode">
            <el-option label="经典版" value="classic" />
            <el-option label="紧凑版" value="compact" />
            <el-option label="高级版" value="premium" />
          </el-select>
        </label>
        <label>
          <span>推荐模板</span>
          <el-select v-model="templateForm.recommended">
            <el-option :value="1" label="是" />
            <el-option :value="0" label="否" />
          </el-select>
        </label>
        <label class="full-span">
          <span>模板说明</span>
          <el-input v-model="templateForm.description" placeholder="请输入模板说明" />
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveTemplate">保存模板</el-button>
        <el-button @click="resetTemplateForm">重置</el-button>
      </div>

      <el-table :data="templatePagination.rows" stripe>
        <el-table-column label="模板" min-width="220">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.templateName }}</strong>
              <span>{{ row.templateCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="180" prop="title" />
        <el-table-column label="行业" width="140" prop="industry" />
        <el-table-column label="布局" width="120" prop="layoutMode" />
        <el-table-column label="推荐" width="90">
          <template #default="{ row }">
            <el-tag :type="row.recommended === 1 ? 'success' : 'info'">{{ row.recommended === 1 ? '推荐' : '普通' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickTemplate(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleTemplate(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>门店绑定</h3>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>门店</span>
          <el-select v-model="bindingForm.storeName" placeholder="请选择门店">
            <el-option v-for="storeName in storeNames" :key="storeName" :label="storeName" :value="storeName" />
          </el-select>
        </label>
        <label>
          <span>默认模板</span>
          <el-select v-model="bindingForm.templateId" placeholder="请选择模板">
            <el-option
              v-for="item in availableTemplates"
              :key="item.id"
              :label="`${item.templateName} / ${item.title}`"
              :value="item.id"
            />
          </el-select>
        </label>
        <label>
          <span>生效时间</span>
          <el-input v-model="bindingForm.effectiveFrom" placeholder="如 2026-05-01" />
        </label>
        <label>
          <span>允许门店覆盖</span>
          <el-select v-model="bindingForm.allowOverride">
            <el-option :value="1" label="允许" />
            <el-option :value="0" label="不允许" />
          </el-select>
        </label>
      </div>

      <div class="action-group action-group--section">
        <el-button type="primary" @click="saveBinding">保存绑定</el-button>
        <el-button @click="resetBindingForm">重置</el-button>
      </div>

      <el-table :data="bindingPagination.rows" stripe>
        <el-table-column label="门店" min-width="160" prop="storeName" />
        <el-table-column label="生效模板" min-width="220">
          <template #default="{ row }">
            {{ templateName(row.templateId) }}
          </template>
        </el-table-column>
        <el-table-column label="生效时间" width="140" prop="effectiveFrom" />
        <el-table-column label="允许覆盖" width="120">
          <template #default="{ row }">
            {{ row.allowOverride === 1 ? '允许' : '不允许' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickBinding(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleBinding(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useTablePagination } from '../composables/useTablePagination'
import { listStoreNames, loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const state = reactive(loadSystemConsoleState())
const templatePagination = useTablePagination(computed(() => state.serviceFormTemplates))
const bindingPagination = useTablePagination(computed(() => state.serviceFormBindings))
const storeNames = computed(() => listStoreNames(state))
const availableTemplates = computed(() => state.serviceFormTemplates.filter((item) => item.enabled !== 0))

const templateForm = reactive(createTemplateForm())
const bindingForm = reactive(createBindingForm())

function createTemplateForm() {
  return {
    id: null,
    templateCode: '',
    templateName: '',
    title: '',
    industry: '',
    layoutMode: 'classic',
    description: '',
    recommended: 0
  }
}

function createBindingForm() {
  return {
    id: null,
    storeName: '',
    templateId: null,
    effectiveFrom: '',
    allowOverride: 0
  }
}

function persistState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetTemplateForm() {
  Object.assign(templateForm, createTemplateForm())
}

function resetBindingForm() {
  Object.assign(bindingForm, createBindingForm())
}

function saveTemplate() {
  if (!templateForm.templateName || !templateForm.templateCode || !templateForm.title) {
    ElMessage.warning('请完整填写模板信息')
    return
  }
  const items = [...state.serviceFormTemplates]
  const nextItem = {
    ...templateForm,
    id: templateForm.id || nextSystemId(items),
    enabled: templateForm.id ? (items.find((item) => item.id === templateForm.id)?.enabled ?? 1) : 1
  }
  if (templateForm.id) {
    items.splice(
      items.findIndex((item) => item.id === templateForm.id),
      1,
      nextItem
    )
  } else {
    items.push(nextItem)
  }
  persistState({ ...state, serviceFormTemplates: items })
  templatePagination.reset()
  resetTemplateForm()
  ElMessage.success('模板已保存')
}

function saveBinding() {
  if (!bindingForm.storeName || !bindingForm.templateId) {
    ElMessage.warning('请选择门店和模板')
    return
  }
  const items = [...state.serviceFormBindings]
  const nextItem = {
    ...bindingForm,
    id: bindingForm.id || nextSystemId(items),
    enabled: bindingForm.id ? (items.find((item) => item.id === bindingForm.id)?.enabled ?? 1) : 1
  }
  if (bindingForm.id) {
    items.splice(
      items.findIndex((item) => item.id === bindingForm.id),
      1,
      nextItem
    )
  } else {
    items.push(nextItem)
  }
  persistState({ ...state, serviceFormBindings: items })
  bindingPagination.reset()
  resetBindingForm()
  ElMessage.success('门店绑定已保存')
}

function pickTemplate(row) {
  Object.assign(templateForm, { ...row })
}

function toggleTemplate(row) {
  persistState({
    ...state,
    serviceFormTemplates: state.serviceFormTemplates.map((item) =>
      item.id === row.id ? { ...item, enabled: item.enabled === 1 ? 0 : 1 } : item
    )
  })
}

function pickBinding(row) {
  Object.assign(bindingForm, { ...row })
}

function toggleBinding(row) {
  persistState({
    ...state,
    serviceFormBindings: state.serviceFormBindings.map((item) =>
      item.id === row.id ? { ...item, enabled: item.enabled === 1 ? 0 : 1 } : item
    )
  })
}

function templateName(templateId) {
  return state.serviceFormTemplates.find((item) => item.id === templateId)?.templateName || '--'
}
</script>
