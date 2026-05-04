<template>
  <div class="stack-page service-template-page">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h3>服务单设计</h3>
        </div>
        <div class="action-group">
          <el-button :loading="loading" @click="loadAll">刷新</el-button>
          <el-button v-if="canManageTemplates" type="primary" @click="openTemplateEditor()">新增模板</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="service-template-tabs">
        <el-tab-pane label="模板库" name="templates">
          <div class="template-toolbar">
            <div class="template-summary">
              <article>
                <span>模板</span>
                <strong>{{ templates.length }}</strong>
              </article>
              <article>
                <span>已发布</span>
                <strong>{{ templates.filter((item) => item.status === 'PUBLISHED').length }}</strong>
              </article>
              <article>
                <span>推荐</span>
                <strong>{{ templates.filter((item) => item.recommended === 1).length }}</strong>
              </article>
            </div>
          </div>

          <el-table v-loading="loading" :data="templatePagination.rows" stripe>
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
            <el-table-column label="布局" width="110">
              <template #default="{ row }">{{ formatLayout(row.layoutMode) }}</template>
            </el-table-column>
            <el-table-column label="设计器" width="140">
              <template #default="{ row }">{{ formatDesignerEngine(row.designerEngine) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="templateStatusType(row.status)">{{ formatTemplateStatus(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ row.updateTime || row.createTime || '--' }}</template>
            </el-table-column>
            <el-table-column label="操作" :width="canManageTemplates ? 270 : 100" fixed="right">
              <template #default="{ row }">
                <div class="action-group">
                  <el-button size="small" plain @click="openPreview(row)">预览</el-button>
                  <el-button v-if="canManageTemplates" size="small" @click="openTemplateEditor(row)">编辑</el-button>
                  <el-button
                    v-if="canManageTemplates"
                    size="small"
                    type="success"
                    plain
                    :disabled="row.status === 'PUBLISHED'"
                    @click="publishTemplate(row)"
                  >
                    发布
                  </el-button>
                  <el-button
                    v-if="canManageTemplates"
                    size="small"
                    plain
                    :disabled="row.status === 'DISABLED'"
                    @click="disableTemplate(row)"
                  >
                    停用
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="templatePagination.total > templatePagination.pageSize"
            class="table-pagination"
            background
            layout="total, sizes, prev, pager, next"
            :current-page="templatePagination.currentPage"
            :page-size="templatePagination.pageSize"
            :page-sizes="templatePagination.pageSizes"
            :total="templatePagination.total"
            @size-change="templatePagination.handleSizeChange"
            @current-change="templatePagination.handleCurrentChange"
          />
        </el-tab-pane>

        <el-tab-pane label="应用到门店" name="bindings">
          <div class="binding-workbench">
            <aside class="binding-card">
              <h4>{{ bindingForm.id ? '调整门店模板' : '应用到门店' }}</h4>
              <label>
                <span>门店</span>
                <el-select v-model="bindingForm.storeName" filterable placeholder="请选择门店" :disabled="!isAdmin">
                  <el-option v-for="storeName in storeNames" :key="storeName" :label="storeName" :value="storeName" />
                </el-select>
              </label>
              <label>
                <span>服务单模板</span>
                <el-select v-model="bindingForm.templateId" filterable placeholder="请选择已发布模板">
                  <el-option
                    v-for="item in publishedTemplates"
                    :key="item.id"
                    :label="`${item.templateName} / ${item.title}`"
                    :value="item.id"
                  />
                </el-select>
              </label>
              <label>
                <span>生效日期</span>
                <el-date-picker v-model="bindingForm.effectiveFrom" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" />
              </label>
              <label>
                <span>门店可改</span>
                <el-select v-model="bindingForm.allowOverride">
                  <el-option :value="0" label="不可改" />
                  <el-option :value="1" label="可改标题和字段" />
                </el-select>
              </label>
              <div class="binding-card__actions">
                <el-button type="primary" :loading="savingBinding" :disabled="!canManageBindings" @click="saveBinding">保存应用</el-button>
                <el-button @click="resetBindingForm">重置</el-button>
              </div>
            </aside>

            <div class="binding-table">
              <el-table v-loading="loading" :data="bindingPagination.rows" stripe>
                <el-table-column label="门店" min-width="150" prop="storeName" />
                <el-table-column label="生效模板" min-width="220">
                  <template #default="{ row }">
                    <div class="table-primary">
                      <strong>
                        {{ row.templateName || templateName(row.templateId) }}
                        <el-tag v-if="row.enabled === 1" class="binding-active-tag" size="small" type="success">当前生效</el-tag>
                      </strong>
                      <span>{{ row.templateTitle || '--' }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="生效日期" width="130" prop="effectiveFrom" />
                <el-table-column label="门店可改" width="140">
                  <template #default="{ row }">{{ row.allowOverride === 1 ? '可改标题和字段' : '不可改' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column v-if="canManageBindings" label="操作" width="190" fixed="right">
                  <template #default="{ row }">
                    <div class="action-group">
                      <el-button size="small" @click="pickBinding(row)">编辑</el-button>
                      <el-button size="small" plain :disabled="row.enabled !== 1" @click="disableBinding(row)">停用</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>

              <el-pagination
                v-if="bindingPagination.total > bindingPagination.pageSize"
                class="table-pagination"
                background
                layout="total, sizes, prev, pager, next"
                :current-page="bindingPagination.currentPage"
                :page-size="bindingPagination.pageSize"
                :page-sizes="bindingPagination.pageSizes"
                :total="bindingPagination.total"
                @size-change="bindingPagination.handleSizeChange"
                @current-change="bindingPagination.handleCurrentChange"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="效果预览" name="preview">
          <div class="preview-grid">
            <aside class="preview-selector">
              <label>
                <span>预览门店</span>
                <el-select v-model="previewForm.storeName" filterable clearable placeholder="按门店读取生效模板">
                  <el-option v-for="storeName in storeNames" :key="storeName" :label="storeName" :value="storeName" />
                </el-select>
              </label>
              <label>
                <span>指定模板</span>
                <el-select v-model="previewForm.templateId" filterable clearable placeholder="可直接预览某个模板">
                  <el-option
                    v-for="item in templates"
                    :key="item.id"
                    :label="`${item.templateName} / ${formatTemplateStatus(item.status)}`"
                    :value="item.id"
                  />
                </el-select>
              </label>
              <el-button type="primary" :loading="previewLoading" @click="loadPreview">生成预览</el-button>
            </aside>

            <section class="service-template-preview" :class="`service-template-preview--${previewTemplate?.layoutMode || 'classic'}`">
              <div v-if="previewTemplate" class="preview-paper">
                <header>
                  <span>{{ previewTemplate.industry || '通用服务' }}</span>
                  <h2>{{ previewTemplate.title }}</h2>
                  <small>{{ previewMessage }}</small>
                </header>
                <div class="preview-section" v-for="section in previewSections" :key="section">
                  <strong>{{ section }}</strong>
                  <div class="preview-field-chips">
                    <em v-for="field in corePreviewFields" :key="field">{{ field }}</em>
                  </div>
                </div>
              </div>
              <el-empty v-else description="请选择门店或模板后预览" />
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="templateDialogVisible" :title="templateForm.id ? '编辑模板草稿' : '新增模板草稿'" width="720px" destroy-on-close>
      <div class="template-form">
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
          <span>设计器引擎</span>
          <el-select v-model="templateForm.designerEngine">
            <el-option label="系统轻量 Schema" value="INTERNAL_SCHEMA" />
            <el-option label="Formily 适配" value="FORMILY" />
            <el-option label="VForm 3 适配" value="VFORM3" />
            <el-option label="阿里 LowCode Engine 适配" value="LOWCODE_ENGINE" />
            <el-option label="JSON Schema 适配" value="JSON_SCHEMA" />
          </el-select>
        </label>
        <label>
          <span>推荐模板</span>
          <el-select v-model="templateForm.recommended">
            <el-option :value="1" label="是" />
            <el-option :value="0" label="否" />
          </el-select>
        </label>
        <label class="template-form__wide">
          <span>模板说明</span>
          <el-input v-model="templateForm.description" type="textarea" :rows="2" placeholder="请输入模板说明" />
        </label>
        <div class="template-form__wide designer-import-panel">
          <div>
            <strong>第三方设计器导入</strong>
            <span>支持 VForm 3、Formily、LowCode Engine、JSON Schema；纸质签名留位会保留，电子签名组件会拦截。</span>
          </div>
          <div class="action-group">
            <el-button plain @click="triggerImportSchema">导入设计器 JSON</el-button>
            <el-button plain @click="validateTemplateSchema">校验字段</el-button>
          </div>
          <input ref="schemaImportInputRef" class="hidden-file-input" type="file" accept=".json,application/json" @change="handleImportSchemaFile" />
        </div>
        <div v-if="schemaImportReport" class="template-form__wide schema-import-report" :class="{ 'schema-import-report--blocked': schemaImportReport.blocked.length }">
          <div class="schema-import-report__header">
            <strong>导入预检</strong>
            <el-tag :type="schemaImportReport.blocked.length ? 'danger' : 'success'" effect="plain">
              {{ schemaImportReport.blocked.length ? '存在拦截项' : '可以保存草稿' }}
            </el-tag>
          </div>
          <div class="schema-import-report__grid">
            <span>识别类型：{{ schemaImportReport.engineLabel }}</span>
            <span>字段数量：{{ schemaImportReport.fieldCount }}</span>
            <span>组件数量：{{ schemaImportReport.componentCount }}</span>
            <span>标准化版本：{{ schemaImportReport.version }}</span>
            <span>纸质签名：{{ schemaImportReport.paperSignatureReady ? '已保留' : '保存时补齐' }}</span>
          </div>
          <p v-if="schemaImportReport.blocked.length">拦截内容：{{ schemaImportReport.blocked.join('、') }}</p>
          <p v-else>已生成标准 Schema 预览；保存时后端会再次执行安全校验，草稿发布前不会影响门店正在使用的模板。</p>
        </div>
        <details class="template-form__wide template-advanced">
          <summary>高级适配数据</summary>
          <div class="template-advanced__body">
            <label>
              <span>模板配置 JSON</span>
              <el-input v-model="templateForm.configJson" type="textarea" :rows="5" placeholder='{"sections":["基础信息","服务确认","纸质签名留位"]}' />
            </label>
            <label>
              <span>设计器原始 Schema</span>
              <el-input v-model="templateForm.rawSchemaJson" type="textarea" :rows="4" placeholder="第三方设计器导出的原始 JSON，可留空" />
            </label>
            <label>
              <span>系统标准 Schema</span>
              <el-input v-model="templateForm.normalizedSchemaJson" type="textarea" :rows="4" placeholder="平台字段模型 JSON；留空时系统自动生成" />
            </label>
          </div>
        </details>
      </div>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplateDraft">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTablePagination } from '../composables/useTablePagination'
import {
  disableServiceFormBinding,
  disableServiceFormTemplate,
  fetchServiceFormBindings,
  fetchServiceFormTemplates,
  previewServiceFormTemplate,
  publishServiceFormTemplate,
  saveServiceFormBinding,
  saveServiceFormTemplateDraft
} from '../api/serviceFormTemplate'
import { currentUser } from '../utils/auth'
import { listStoreNames, loadSystemConsoleState } from '../utils/systemConsoleStore'

const fallbackState = reactive(loadSystemConsoleState())
const activeTab = ref('templates')
const loading = ref(false)
const savingTemplate = ref(false)
const savingBinding = ref(false)
const previewLoading = ref(false)
const templateDialogVisible = ref(false)
const schemaImportInputRef = ref(null)
const schemaImportReport = ref(null)
const templates = ref([])
const bindings = ref([])
const previewResult = ref(null)

const templatePagination = useTablePagination(computed(() => templates.value))
const bindingPagination = useTablePagination(computed(() => bindings.value))
const isAdmin = computed(() => String(currentUser.value?.roleCode || '').toUpperCase() === 'ADMIN')
const canManageTemplates = computed(() => isAdmin.value)
const canManageBindings = computed(() => ['ADMIN', 'STORE_MANAGER'].includes(String(currentUser.value?.roleCode || '').toUpperCase()))
const storeNames = computed(() => {
  if (!isAdmin.value && currentUser.value?.storeName) {
    return [currentUser.value.storeName]
  }
  return listStoreNames(fallbackState)
})
const publishedTemplates = computed(() => templates.value.filter((item) => item.status === 'PUBLISHED' && item.enabled === 1))
const previewTemplate = computed(() => previewResult.value?.template || null)
const previewMessage = computed(() => previewResult.value?.message || '预览模式')
const previewSections = computed(() => parsePreviewSections(previewTemplate.value?.configJson))
const corePreviewFields = ['客户信息', '服务项目', '确认金额', '纸质签名留位']

const templateForm = reactive(createTemplateForm())
const bindingForm = reactive(createBindingForm())
const previewForm = reactive({
  storeName: '',
  templateId: null
})

function createTemplateForm(payload = {}) {
  return {
    id: payload.id ?? null,
    templateCode: payload.templateCode || '',
    templateName: payload.templateName || '',
    title: payload.title || '',
    industry: payload.industry || '',
    layoutMode: payload.layoutMode || 'classic',
    designerEngine: payload.designerEngine || 'INTERNAL_SCHEMA',
    configJson: payload.configJson || '{"sections":["基础信息","服务确认","偏好与补充","纸质签名留位"]}',
    rawSchemaJson: payload.rawSchemaJson || '',
    normalizedSchemaJson: payload.normalizedSchemaJson || '',
    description: payload.description || '',
    recommended: payload.recommended ?? 0
  }
}

function createBindingForm(payload = {}) {
  return {
    id: payload.id ?? null,
    storeName: payload.storeName || (!isAdmin.value && currentUser.value?.storeName ? currentUser.value.storeName : ''),
    templateId: payload.templateId ?? null,
    effectiveFrom: payload.effectiveFrom || '',
    allowOverride: payload.allowOverride ?? 0
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [templateRows, bindingRows] = await Promise.all([fetchServiceFormTemplates(), fetchServiceFormBindings()])
    templates.value = Array.isArray(templateRows) ? templateRows : []
    bindings.value = Array.isArray(bindingRows) ? bindingRows : []
    templatePagination.reset()
    bindingPagination.reset()
    ensureScopedStoreSelected()
    if (previewForm.storeName) {
      await loadPreview()
    } else if (!previewResult.value && templates.value.length) {
      previewResult.value = { template: publishedTemplates.value[0] || templates.value[0], message: '默认预览' }
    }
  } finally {
    loading.value = false
  }
}

function openTemplateEditor(row = null) {
  if (!canManageTemplates.value) {
    ElMessage.warning('当前角色只能预览模板，不能编辑全局模板')
    return
  }
  Object.assign(templateForm, createTemplateForm(row || {}))
  schemaImportReport.value = buildSchemaImportReport(templateForm.rawSchemaJson || templateForm.normalizedSchemaJson)
  templateDialogVisible.value = true
}

async function saveTemplateDraft() {
  if (!templateForm.templateName || !templateForm.templateCode || !templateForm.title) {
    ElMessage.warning('请完整填写模板名称、编码和标题')
    return
  }
  if (!isJsonLike(templateForm.configJson)) {
    ElMessage.warning('模板配置 JSON 格式不正确')
    return
  }
  if (templateForm.rawSchemaJson && !isJsonLike(templateForm.rawSchemaJson)) {
    ElMessage.warning('设计器原始 Schema JSON 格式不正确')
    return
  }
  if (templateForm.normalizedSchemaJson && !isJsonLike(templateForm.normalizedSchemaJson)) {
    ElMessage.warning('系统标准 Schema JSON 格式不正确')
    return
  }
  templateForm.configJson = ensurePaperSignatureConfig(templateForm.configJson)
  templateForm.normalizedSchemaJson = ensurePaperSignatureNormalizedSchema(templateForm.normalizedSchemaJson)
  const unsafe = findUnsafeSchemaText(templateForm.rawSchemaJson || templateForm.normalizedSchemaJson)
  if (unsafe) {
    ElMessage.warning(`Schema 包含不允许的内容：${unsafe}`)
    return
  }
  savingTemplate.value = true
  try {
    await saveServiceFormTemplateDraft({ ...templateForm })
    ElMessage.success('模板草稿已保存')
    templateDialogVisible.value = false
    await loadAll()
  } finally {
    savingTemplate.value = false
  }
}

function triggerImportSchema() {
  schemaImportInputRef.value?.click?.()
}

async function handleImportSchemaFile(event) {
  const file = event?.target?.files?.[0]
  if (!file) {
    return
  }
  try {
    const text = await file.text()
    const parsed = JSON.parse(text)
    const pretty = JSON.stringify(parsed, null, 2)
    templateForm.rawSchemaJson = pretty
    templateForm.normalizedSchemaJson = JSON.stringify(buildNormalizedSchemaPreview(parsed), null, 2)
    schemaImportReport.value = buildSchemaImportReport(pretty)
    ElMessage.success('设计器 JSON 已导入，请校验字段后保存草稿')
  } catch (error) {
    ElMessage.warning(`导入失败：${error.message || '文件不是有效 JSON'}`)
  } finally {
    if (event?.target) {
      event.target.value = ''
    }
  }
}

function validateTemplateSchema() {
  if (!isJsonLike(templateForm.configJson)) {
    ElMessage.warning('模板配置 JSON 格式不正确')
    return false
  }
  if (templateForm.rawSchemaJson && !isJsonLike(templateForm.rawSchemaJson)) {
    ElMessage.warning('设计器原始 Schema JSON 格式不正确')
    return false
  }
  if (templateForm.normalizedSchemaJson && !isJsonLike(templateForm.normalizedSchemaJson)) {
    ElMessage.warning('系统标准 Schema JSON 格式不正确')
    return false
  }
  templateForm.configJson = ensurePaperSignatureConfig(templateForm.configJson)
  templateForm.normalizedSchemaJson = ensurePaperSignatureNormalizedSchema(templateForm.normalizedSchemaJson)
  const unsafe = findUnsafeSchemaText(templateForm.rawSchemaJson || templateForm.normalizedSchemaJson)
  schemaImportReport.value = buildSchemaImportReport(templateForm.rawSchemaJson || templateForm.normalizedSchemaJson)
  if (unsafe) {
    ElMessage.warning(`Schema 包含不允许的内容：${unsafe}`)
    return false
  }
  ElMessage.success('字段校验通过，保存时后端会再次执行白名单校验')
  return true
}

async function publishTemplate(row) {
  try {
    await ElMessageBox.confirm(
      `确认发布「${row.templateName}」？发布后仅影响后续新服务单展示，历史服务单不变。`,
      '发布模板',
      { confirmButtonText: '发布', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await publishServiceFormTemplate({ templateId: row.id, reason: '页面发布' })
  ElMessage.success('模板已发布')
  await loadAll()
}

async function disableTemplate(row) {
  try {
    await ElMessageBox.confirm(`确认停用「${row.templateName}」？已绑定门店将无法继续使用该模板。`, '停用模板', {
      confirmButtonText: '停用',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await disableServiceFormTemplate({ templateId: row.id, reason: '页面停用' })
  ElMessage.success('模板已停用')
  await loadAll()
}

async function saveBinding() {
  if (!bindingForm.storeName || !bindingForm.templateId) {
    ElMessage.warning('请选择门店和已发布模板')
    return
  }
  savingBinding.value = true
  try {
    await saveServiceFormBinding({ ...bindingForm })
    ElMessage.success('门店模板已保存')
    resetBindingForm()
    await loadAll()
  } finally {
    savingBinding.value = false
  }
}

function pickBinding(row) {
  Object.assign(bindingForm, createBindingForm(row))
}

async function disableBinding(row) {
  try {
    await ElMessageBox.confirm(`确认停用「${row.storeName}」的服务单模板绑定？`, '停用绑定', {
      confirmButtonText: '停用',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await disableServiceFormBinding({ bindingId: row.id, reason: '页面停用' })
  ElMessage.success('门店模板已停用')
  await loadAll()
}

function resetBindingForm() {
  Object.assign(bindingForm, createBindingForm())
  ensureScopedStoreSelected()
}

function openPreview(row) {
  activeTab.value = 'preview'
  previewForm.templateId = row.id
  previewForm.storeName = ''
  void loadPreview()
}

async function loadPreview() {
  previewLoading.value = true
  try {
    previewResult.value = await previewServiceFormTemplate({
      templateId: previewForm.templateId || undefined,
      storeName: previewForm.storeName || undefined
    })
  } finally {
    previewLoading.value = false
  }
}

function templateName(templateId) {
  return templates.value.find((item) => item.id === templateId)?.templateName || '--'
}

function formatTemplateStatus(status) {
  const value = String(status || '').toUpperCase()
  const map = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    DISABLED: '已停用',
    ARCHIVED: '已归档'
  }
  return map[value] || value || '--'
}

function templateStatusType(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'PUBLISHED') {
    return 'success'
  }
  if (value === 'DRAFT') {
    return 'warning'
  }
  return 'info'
}

function formatLayout(layoutMode) {
  const map = {
    classic: '经典版',
    compact: '紧凑版',
    premium: '高级版'
  }
  return map[String(layoutMode || '').toLowerCase()] || layoutMode || '--'
}

function formatDesignerEngine(value) {
  const map = {
    INTERNAL_SCHEMA: '系统 Schema',
    FORMILY: 'Formily',
    VFORM3: 'VForm 3',
    LOWCODE_ENGINE: 'LowCode Engine',
    JSON_SCHEMA: 'JSON Schema'
  }
  return map[String(value || 'INTERNAL_SCHEMA').toUpperCase()] || value || '系统 Schema'
}

function parsePreviewSections(configJson) {
  try {
    const parsed = JSON.parse(configJson || '{}')
    if (Array.isArray(parsed.sections) && parsed.sections.length) {
      return parsed.sections.map((item) => String(item || '').trim()).filter(Boolean)
    }
  } catch {
    // Invalid custom config should not break preview.
  }
  return ['基础信息', '服务确认', '偏好与补充', '纸质签名留位']
}

function isJsonLike(value) {
  if (!value || !String(value).trim()) {
    return true
  }
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

function ensurePaperSignatureConfig(value) {
  try {
    const parsed = JSON.parse(value || '{}')
    const sections = Array.isArray(parsed.sections) ? parsed.sections : []
    if (!sections.includes('纸质签名留位')) {
      parsed.sections = [...sections, '纸质签名留位']
    }
    parsed.paperSignatureRequired = true
    parsed.signatureMode = 'PAPER'
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}

function ensurePaperSignatureNormalizedSchema(value) {
  if (!value || !String(value).trim()) {
    return value
  }
  try {
    const parsed = JSON.parse(value)
    parsed.paperSignatureRequired = true
    if (!Array.isArray(parsed.printSignatureBlocks) || !parsed.printSignatureBlocks.length) {
      parsed.printSignatureBlocks = ['customerHandwrittenSignature', 'storeOperator', 'signedDate']
    }
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}

function buildSchemaImportReport(value) {
  if (!value || !String(value).trim()) {
    return null
  }
  let schema
  try {
    schema = typeof value === 'string' ? JSON.parse(value) : value
  } catch {
    return {
      engineLabel: engineLabel(templateForm.designerEngine),
      fieldCount: 0,
      componentCount: 0,
      version: 'unknown',
      blocked: ['JSON 格式不正确']
    }
  }
  const stats = scanSchemaStats(schema)
  const unsafe = findUnsafeSchemaText(JSON.stringify(schema))
  return {
    engineLabel: detectDesignerEngine(schema),
    fieldCount: stats.fieldCount,
    componentCount: stats.components.size,
    version: resolveSchemaVersion(schema),
    paperSignatureReady: hasPaperSignaturePlaceholder(schema),
    blocked: unsafe ? [unsafe] : []
  }
}

function hasPaperSignaturePlaceholder(schema) {
  const text = JSON.stringify(schema || '').toLowerCase()
  return text.includes('papersignatureplaceholder') || text.includes('纸质签名留位') || text.includes('客户手写签名')
}

function scanSchemaStats(node, key = '') {
  const stats = { fieldCount: 0, components: new Set() }
  walkSchema(node, key, stats)
  return stats
}

function walkSchema(node, key, stats) {
  if (Array.isArray(node)) {
    node.forEach((item) => walkSchema(item, key, stats))
    return
  }
  if (!node || typeof node !== 'object') {
    return
  }
  Object.entries(node).forEach(([childKey, childValue]) => {
    const normalizedKey = normalizeSchemaToken(childKey)
    if (['field', 'fieldname', 'name', 'model', 'prop', 'key'].includes(normalizedKey) && typeof childValue === 'string' && childValue.trim()) {
      stats.fieldCount += 1
    }
    if (['component', 'componentname', 'xcomponent', 'widget', 'control', 'type'].includes(normalizedKey) && typeof childValue === 'string' && childValue.trim()) {
      stats.components.add(normalizeSchemaToken(childValue))
    }
    walkSchema(childValue, childKey, stats)
  })
}

function detectDesignerEngine(schema) {
  const text = JSON.stringify(schema || {}).toLowerCase()
  if (text.includes('x-component') || text.includes('formily')) {
    return 'Formily 适配'
  }
  if (text.includes('vform') || text.includes('widgetlist') || text.includes('formconfig')) {
    return 'VForm 3 适配'
  }
  if (text.includes('lowcode') || text.includes('componentname')) {
    return 'LowCode Engine 适配'
  }
  if (text.includes('"properties"') || text.includes('"jsonschema"')) {
    return 'JSON Schema 适配'
  }
  return engineLabel(templateForm.designerEngine)
}

function engineLabel(value) {
  return {
    INTERNAL_SCHEMA: '系统轻量 Schema',
    FORMILY: 'Formily 适配',
    VFORM3: 'VForm 3 适配',
    LOWCODE_ENGINE: 'LowCode Engine 适配',
    JSON_SCHEMA: 'JSON Schema 适配'
  }[value] || value || '未知设计器'
}

function normalizeSchemaToken(value) {
  return String(value || '').trim().toLowerCase().replace(/[^a-z0-9]/g, '')
}

function buildNormalizedSchemaPreview(schema) {
  return {
    schemaVersion: 'service-form-template.v1',
    designerEngine: templateForm.designerEngine || 'INTERNAL_SCHEMA',
    designerEngineVersion: resolveSchemaVersion(schema),
    source: 'RAW_SCHEMA',
    componentWhitelist: [
      'input',
      'textarea',
      'select',
      'radio',
      'checkbox',
      'datepicker',
      'number',
      'switch',
      'table',
      'upload',
      'tabs',
      'subform',
      'card',
      'text',
      'divider',
      'paperSignaturePlaceholder'
    ],
    paperSignatureRequired: true,
    printSignatureBlocks: ['customerHandwrittenSignature', 'storeOperator', 'signedDate'],
    schema
  }
}

function resolveSchemaVersion(schema) {
  if (!schema || typeof schema !== 'object') {
    return 'unknown'
  }
  return schema.designerEngineVersion || schema.engineVersion || schema.version || schema.designer?.version || 'unknown'
}

function findUnsafeSchemaText(value) {
  if (!value || !String(value).trim()) {
    return ''
  }
  const text = String(value).toLowerCase()
  const blocked = [
    '<script',
    'javascript:',
    'iframe',
    'webview',
    'electronicsignature',
    'electronic-signature',
    'canvas-signature',
    'canvassignature',
    '"signature"',
    '"html"'
  ]
  return blocked.find((item) => text.includes(item)) || ''
}

function ensureScopedStoreSelected() {
  if (!isAdmin.value && currentUser.value?.storeName) {
    bindingForm.storeName = currentUser.value.storeName
    if (!previewForm.storeName) {
      previewForm.storeName = currentUser.value.storeName
    }
  }
}

onMounted(loadAll)
</script>

<style scoped>
.service-template-page {
  min-width: 0;
}

.service-template-tabs {
  margin-top: 16px;
}

.template-toolbar {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 14px;
}

.template-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(110px, 1fr));
  gap: 12px;
  width: min(520px, 100%);
}

.template-summary article {
  padding: 12px 14px;
  border: 1px solid #e5edf4;
  border-radius: 16px;
  background: #f8fafc;
}

.template-summary span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.template-summary strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 24px;
}

.binding-workbench,
.preview-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.binding-card,
.preview-selector {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid #e5edf4;
  border-radius: 18px;
  background: #f8fafc;
}

.binding-card h4 {
  margin: 0;
  color: #0f172a;
}

.binding-card label,
.preview-selector label,
.template-form label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 13px;
}

.binding-card__actions {
  display: flex;
  gap: 10px;
}

.binding-table {
  min-width: 0;
}

.binding-active-tag {
  margin-left: 8px;
  vertical-align: 1px;
}

.template-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.template-form__wide {
  grid-column: 1 / -1;
}

.designer-import-panel {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: #f8fbff;
}

.designer-import-panel div:first-child {
  display: grid;
  gap: 4px;
}

.designer-import-panel strong {
  color: #0f172a;
}

.designer-import-panel span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.hidden-file-input {
  display: none;
}

.schema-import-report {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #bbf7d0;
  border-radius: 16px;
  background: #f0fdf4;
}

.schema-import-report--blocked {
  border-color: #fecaca;
  background: #fff1f2;
}

.schema-import-report__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.schema-import-report__header strong {
  color: #0f172a;
}

.schema-import-report__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  color: #334155;
  font-size: 13px;
}

.schema-import-report p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.template-advanced {
  border: 1px solid #e5edf4;
  border-radius: 16px;
  padding: 12px 14px;
  background: #ffffff;
}

.template-advanced summary {
  cursor: pointer;
  color: #334155;
  font-weight: 700;
}

.template-advanced__body {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.service-template-preview {
  min-height: 520px;
  border: 1px solid #e5edf4;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.12), transparent 32%),
    #f8fafc;
  padding: 20px;
}

.preview-paper {
  display: grid;
  gap: 14px;
  max-width: 760px;
  margin: 0 auto;
  padding: 24px;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
}

.preview-paper header {
  padding-bottom: 16px;
  border-bottom: 1px solid #e5edf4;
}

.preview-paper header span {
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.preview-paper h2 {
  margin: 8px 0;
  color: #0f172a;
  font-size: 28px;
}

.preview-paper small {
  color: #64748b;
}

.preview-section {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #e5edf4;
  border-radius: 16px;
}

.preview-section strong {
  color: #0f172a;
}

.preview-field-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-field-chips em {
  padding: 5px 9px;
  border-radius: 999px;
  background: #eef6ff;
  color: #2563eb;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.service-template-preview--compact .preview-paper {
  max-width: 620px;
  padding: 18px;
}

.service-template-preview--premium .preview-paper {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(135deg, #ffffff 0%, #fefce8 100%);
}

.table-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .binding-workbench,
  .preview-grid {
    grid-template-columns: 1fr;
  }

  .template-form {
    grid-template-columns: 1fr;
  }

  .designer-import-panel {
    display: grid;
  }

  .schema-import-report__grid {
    grid-template-columns: 1fr;
  }
}
</style>
