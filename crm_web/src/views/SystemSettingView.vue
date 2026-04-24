<template>
  <div class="stack-page">
    <section class="metrics-row">
      <article class="metric-card">
        <span>{{ metrics.primaryLabel }}</span>
        <strong>{{ metrics.primaryValue }}</strong>
        <small>{{ metrics.primaryHint }}</small>
      </article>
      <article class="metric-card">
        <span>{{ metrics.secondaryLabel }}</span>
        <strong>{{ metrics.secondaryValue }}</strong>
        <small>{{ metrics.secondaryHint }}</small>
      </article>
      <article class="metric-card">
        <span>{{ metrics.tertiaryLabel }}</span>
        <strong>{{ metrics.tertiaryValue }}</strong>
        <small>{{ metrics.tertiaryHint }}</small>
      </article>
    </section>

    <section v-if="currentMode === 'menu'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>菜单管理</h3>
          <p>按角色编排页面权限，保持菜单入口和实际业务职责一致。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>一级菜单</span>
          <el-input v-model="menuForm.menuGroup" placeholder="如 系统设置" />
        </label>
        <label>
          <span>菜单名称</span>
          <el-input v-model="menuForm.menuName" placeholder="请输入菜单名称" />
        </label>
        <label>
          <span>路由地址</span>
          <el-input v-model="menuForm.routePath" placeholder="如 /settings/menu" />
        </label>
        <label>
          <span>模块编码</span>
          <el-select v-model="menuForm.moduleCode" placeholder="请选择模块">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label class="full-span">
          <span>可见角色</span>
          <el-select v-model="menuForm.roleCodes" multiple placeholder="请选择角色">
            <el-option v-for="item in state.roles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
          </el-select>
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveMenu">保存菜单</el-button>
        <el-button @click="resetMenuForm">重置表单</el-button>
      </div>

      <el-table :data="menuPagination.rows" stripe>
        <el-table-column label="菜单" min-width="220">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.menuGroup }} / {{ row.menuName }}</strong>
              <span>{{ row.routePath }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            {{ formatModuleCode(row.moduleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            {{ (row.roleCodes || []).map(formatRoleCode).join(' / ') || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickMenu(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCollectionItem('menuConfigs', row.id, 'isEnabled')">
                {{ row.isEnabled === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="menuPagination.total"
          :current-page="menuPagination.currentPage"
          :page-size="menuPagination.pageSize"
          :page-sizes="menuPagination.pageSizes"
          @size-change="menuPagination.handleSizeChange"
          @current-change="menuPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'third-party'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>三方接口</h3>
          <p>当前客资中心数据来源于三方拉取，这里可以配置接口地址、认证方式和同步模式。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>接口名称</span>
          <el-input v-model="thirdPartyForm.apiName" placeholder="请输入接口名称" />
        </label>
        <label>
          <span>业务模块</span>
          <el-select v-model="thirdPartyForm.moduleCode">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </label>
        <label>
          <span>请求方式</span>
          <el-select v-model="thirdPartyForm.method">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
          </el-select>
        </label>
        <label>
          <span>认证方式</span>
          <el-input v-model="thirdPartyForm.authType" placeholder="如 Bearer Token" />
        </label>
        <label>
          <span>同步模式</span>
          <el-input v-model="thirdPartyForm.syncMode" placeholder="如 增量同步" />
        </label>
        <label>
          <span>绑定任务编码</span>
          <el-input v-model="thirdPartyForm.scheduleJobCode" placeholder="如 DOUYIN_CLUE_INCREMENTAL" />
        </label>
        <label class="full-span">
          <span>接口地址</span>
          <el-input v-model="thirdPartyForm.baseUrl" placeholder="请输入三方接口地址" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveCollectionItem('thirdPartyApis', thirdPartyForm, resetThirdPartyForm, '接口配置已保存')">保存接口</el-button>
        <el-button @click="resetThirdPartyForm">重置表单</el-button>
      </div>

      <el-table :data="thirdPartyPagination.rows" stripe>
        <el-table-column label="接口" min-width="220">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.apiName }}</strong>
              <span>{{ row.baseUrl }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            {{ formatModuleCode(row.moduleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="方式" width="100" prop="method" />
        <el-table-column label="认证" width="140" prop="authType" />
        <el-table-column label="任务编码" min-width="180" prop="scheduleJobCode" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickCollectionItem(thirdPartyForm, row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCollectionItem('thirdPartyApis', row.id, 'enabled')">
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="thirdPartyPagination.total"
          :current-page="thirdPartyPagination.currentPage"
          :page-size="thirdPartyPagination.pageSize"
          :page-sizes="thirdPartyPagination.pageSizes"
          @size-change="thirdPartyPagination.handleSizeChange"
          @current-change="thirdPartyPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'callback'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>回调接口</h3>
          <p>配置三方回调地址和签名校验方式，统一接收异步通知。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>回调名称</span>
          <el-input v-model="callbackForm.callbackName" placeholder="请输入回调名称" />
        </label>
        <label>
          <span>签名方式</span>
          <el-input v-model="callbackForm.signatureMode" placeholder="如 HMAC-SHA256" />
        </label>
        <label class="full-span">
          <span>回调地址</span>
          <el-input v-model="callbackForm.callbackUrl" placeholder="请输入回调接口地址" />
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="callbackForm.remark" placeholder="请输入回调说明" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveCollectionItem('callbackApis', callbackForm, resetCallbackForm, '回调接口已保存')">保存回调</el-button>
        <el-button @click="resetCallbackForm">重置表单</el-button>
      </div>

      <el-table :data="callbackPagination.rows" stripe>
        <el-table-column label="回调名称" min-width="180" prop="callbackName" />
        <el-table-column label="回调地址" min-width="260" prop="callbackUrl" />
        <el-table-column label="签名方式" width="160" prop="signatureMode" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickCollectionItem(callbackForm, row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCollectionItem('callbackApis', row.id, 'enabled')">
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="callbackPagination.total"
          :current-page="callbackPagination.currentPage"
          :page-size="callbackPagination.pageSize"
          :page-sizes="callbackPagination.pageSizes"
          @size-change="callbackPagination.handleSizeChange"
          @current-change="callbackPagination.handleCurrentChange"
        />
      </div>
    </section>

    <template v-else-if="currentMode === 'jobs'">
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>任务调度</h3>
            <p>参考任务调度中心方式，管理三方接口调用、定时同步和失败重试。</p>
          </div>
        </div>

        <div class="form-grid">
          <label>
            <span>任务编码</span>
            <el-input v-model="jobForm.jobCode" />
          </label>
          <label>
            <span>模块</span>
            <el-select v-model="jobForm.moduleCode">
              <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </label>
          <label>
            <span>同步方式</span>
            <el-select v-model="jobForm.syncMode">
              <el-option label="增量同步" value="INCREMENTAL" />
              <el-option label="全量同步" value="FULL" />
              <el-option label="手动触发" value="MANUAL" />
            </el-select>
          </label>
          <label>
            <span>间隔分钟</span>
            <el-input-number v-model="jobForm.intervalMinutes" :min="1" controls-position="right" />
          </label>
          <label>
            <span>重试次数</span>
            <el-input-number v-model="jobForm.retryLimit" :min="0" controls-position="right" />
          </label>
          <label>
            <span>状态</span>
            <el-select v-model="jobForm.status">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </label>
          <label>
            <span>队列名</span>
            <el-input v-model="jobForm.queueName" />
          </label>
          <label class="full-span">
            <span>接口地址</span>
            <el-input v-model="jobForm.endpoint" />
          </label>
        </div>

        <div class="action-group">
          <el-button type="primary" @click="handleSaveJob">保存任务</el-button>
          <el-button @click="loadSchedulerData">刷新</el-button>
        </div>

        <el-table :data="jobPagination.rows" stripe>
          <el-table-column label="任务编码" min-width="180" prop="jobCode" />
          <el-table-column label="模块" width="120">
            <template #default="{ row }">
              {{ formatModuleCode(row.moduleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="同步方式" width="130">
            <template #default="{ row }">
              {{ formatSyncMode(row.syncMode) }}
            </template>
          </el-table-column>
          <el-table-column label="周期" width="110">
            <template #default="{ row }">
              {{ row.intervalMinutes }} 分钟
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下次执行" min-width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.nextRunTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="240" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" type="primary" @click="handleTrigger(row)">立即触发</el-button>
                <el-button size="small" plain @click="handleRetry(row)">重试失败</el-button>
                <el-button size="small" @click="pickJob(row)">载入表单</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="jobPagination.total"
            :current-page="jobPagination.currentPage"
            :page-size="jobPagination.pageSize"
            :page-sizes="jobPagination.pageSizes"
            @size-change="jobPagination.handleSizeChange"
            @current-change="jobPagination.handleCurrentChange"
          />
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h3>执行日志</h3>
            <p>查看任务执行载荷、错误信息和下次重试时间。</p>
          </div>
          <el-select v-model="selectedJobCode" clearable placeholder="按任务筛选" style="width: 220px" @change="loadLogs">
            <el-option v-for="item in jobs" :key="item.jobCode" :label="item.jobCode" :value="item.jobCode" />
          </el-select>
        </div>

        <el-table :data="logPagination.rows" stripe>
          <el-table-column label="任务编码" min-width="180" prop="jobCode" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ formatSchedulerStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试次数" width="100" prop="retryCount" />
          <el-table-column label="载荷" min-width="220" prop="payload" />
          <el-table-column label="错误信息" min-width="220" prop="errorMessage" />
          <el-table-column label="下次重试" min-width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.nextRetryTime) }}
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="logPagination.total"
            :current-page="logPagination.currentPage"
            :page-size="logPagination.pageSize"
            :page-sizes="logPagination.pageSizes"
            @size-change="logPagination.handleSizeChange"
            @current-change="logPagination.handleCurrentChange"
          />
        </div>
      </section>
    </template>

    <section v-else-if="currentMode === 'public-api'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>对外接口</h3>
          <p>选择数据源表或联合查询结果，统一做字段映射、限流、缓存和认证控制。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>接口名称</span>
          <el-input v-model="publicApiForm.apiName" placeholder="请输入对外接口名称" />
        </label>
        <label>
          <span>数据源</span>
          <el-input v-model="publicApiForm.sourceTable" placeholder="如 order + customer + plan_order" />
        </label>
        <label class="full-span">
          <span>输出字段</span>
          <el-input v-model="publicApiForm.outputFields" placeholder="如 orderNo, customerName, amount, statusLabel" />
        </label>
        <label>
          <span>认证方式</span>
          <el-input v-model="publicApiForm.authMode" placeholder="如 签名 + Token" />
        </label>
        <label>
          <span>限流策略</span>
          <el-input v-model="publicApiForm.rateLimit" placeholder="如 60 次/分钟" />
        </label>
        <label>
          <span>缓存策略</span>
          <el-input v-model="publicApiForm.cachePolicy" placeholder="如 30 秒缓存" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveCollectionItem('publicApis', publicApiForm, resetPublicApiForm, '对外接口已保存')">保存接口</el-button>
        <el-button @click="resetPublicApiForm">重置表单</el-button>
      </div>

      <el-table :data="publicApiPagination.rows" stripe>
        <el-table-column label="接口名称" min-width="180" prop="apiName" />
        <el-table-column label="数据源" min-width="220" prop="sourceTable" />
        <el-table-column label="输出字段" min-width="220" prop="outputFields" />
        <el-table-column label="认证 / 限流 / 缓存" min-width="260">
          <template #default="{ row }">
            {{ row.authMode }} / {{ row.rateLimit }} / {{ row.cachePolicy }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickCollectionItem(publicApiForm, row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCollectionItem('publicApis', row.id, 'enabled')">
                {{ row.enabled === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="publicApiPagination.total"
          :current-page="publicApiPagination.currentPage"
          :page-size="publicApiPagination.pageSize"
          :page-sizes="publicApiPagination.pageSizes"
          @size-change="publicApiPagination.handleSizeChange"
          @current-change="publicApiPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'dictionary'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>字典管理</h3>
          <p>维护编码和值的对应关系，让页面显示中文、数据存储编码。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>字典类型</span>
          <el-input v-model="dictionaryForm.dictType" placeholder="如 order_status" />
        </label>
        <label>
          <span>编码值</span>
          <el-input v-model="dictionaryForm.itemCode" placeholder="如 APPOINTMENT" />
        </label>
        <label>
          <span>显示值</span>
          <el-input v-model="dictionaryForm.itemLabel" placeholder="如 已预约" />
        </label>
        <label>
          <span>排序</span>
          <el-input-number v-model="dictionaryForm.sortOrder" :min="1" controls-position="right" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveCollectionItem('dictionaries', dictionaryForm, resetDictionaryForm, '字典项已保存')">保存字典</el-button>
        <el-button @click="resetDictionaryForm">重置表单</el-button>
      </div>

      <el-table :data="dictionaryPagination.rows" stripe>
        <el-table-column label="字典类型" width="180" prop="dictType" />
        <el-table-column label="编码值" width="180" prop="itemCode" />
        <el-table-column label="显示值" width="180" prop="itemLabel" />
        <el-table-column label="排序" width="100" prop="sortOrder" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickCollectionItem(dictionaryForm, row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCollectionItem('dictionaries', row.id, 'isEnabled')">
                {{ row.isEnabled === 1 ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="dictionaryPagination.total"
          :current-page="dictionaryPagination.currentPage"
          :page-size="dictionaryPagination.pageSize"
          :page-sizes="dictionaryPagination.pageSizes"
          @size-change="dictionaryPagination.handleSizeChange"
          @current-change="dictionaryPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else class="panel">
      <div class="panel-heading">
        <div>
          <h3>参数管理</h3>
          <p>维护系统参数键值，方便各模块统一调用。</p>
        </div>
      </div>

      <div class="form-grid">
        <label>
          <span>参数键</span>
          <el-input v-model="parameterForm.paramKey" placeholder="请输入参数键" />
        </label>
        <label>
          <span>参数值</span>
          <el-input v-model="parameterForm.paramValue" placeholder="请输入参数值" />
        </label>
        <label>
          <span>分类</span>
          <el-input v-model="parameterForm.category" placeholder="如 系统 / 客资 / 订单" />
        </label>
        <label class="full-span">
          <span>备注</span>
          <el-input v-model="parameterForm.remark" placeholder="请输入参数说明" />
        </label>
      </div>

      <div class="action-group">
        <el-button type="primary" @click="saveCollectionItem('parameters', parameterForm, resetParameterForm, '参数已保存')">保存参数</el-button>
        <el-button @click="resetParameterForm">重置表单</el-button>
      </div>

      <el-table :data="parameterPagination.rows" stripe>
        <el-table-column label="参数键" min-width="220" prop="paramKey" />
        <el-table-column label="参数值" min-width="180" prop="paramValue" />
        <el-table-column label="分类" width="120" prop="category" />
        <el-table-column label="备注" min-width="220" prop="remark" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickCollectionItem(parameterForm, row)">编辑</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="parameterPagination.total"
          :current-page="parameterPagination.currentPage"
          :page-size="parameterPagination.pageSize"
          :page-sizes="parameterPagination.pageSizes"
          @size-change="parameterPagination.handleSizeChange"
          @current-change="parameterPagination.handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { fetchSchedulerJobs, fetchSchedulerLogs, retrySchedulerJob, saveSchedulerJob, triggerSchedulerJob } from '../api/scheduler'
import { useTablePagination } from '../composables/useTablePagination'
import {
  formatDateTime,
  formatModuleCode,
  formatRoleCode,
  formatSchedulerStatus,
  formatSyncMode,
  statusTagType
} from '../utils/format'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const route = useRoute()
const state = reactive(loadSystemConsoleState())
const jobs = ref([])
const logs = ref([])
const selectedJobCode = ref('')
const menuPagination = useTablePagination(computed(() => state.menuConfigs))
const thirdPartyPagination = useTablePagination(computed(() => state.thirdPartyApis))
const callbackPagination = useTablePagination(computed(() => state.callbackApis))
const jobPagination = useTablePagination(jobs)
const logPagination = useTablePagination(logs)
const publicApiPagination = useTablePagination(computed(() => state.publicApis))
const dictionaryPagination = useTablePagination(computed(() => state.dictionaries))
const parameterPagination = useTablePagination(computed(() => state.parameters))

const currentMode = computed(() => route.meta.settingMode || 'menu')
const moduleOptions = [
  { label: '客资', value: 'CLUE' },
  { label: '订单', value: 'ORDER' },
  { label: '服务单', value: 'PLANORDER' },
  { label: '薪酬', value: 'SALARY' },
  { label: '财务', value: 'FINANCE' },
  { label: '系统管理', value: 'SYSTEM' },
  { label: '系统设置', value: 'SETTING' },
  { label: '私域客服', value: 'WECOM' }
]

const menuForm = reactive(createMenuForm())
const thirdPartyForm = reactive(createThirdPartyForm())
const callbackForm = reactive(createCallbackForm())
const publicApiForm = reactive(createPublicApiForm())
const dictionaryForm = reactive(createDictionaryForm())
const parameterForm = reactive(createParameterForm())
const jobForm = reactive({
  jobCode: 'DOUYIN_CLUE_INCREMENTAL',
  moduleCode: 'CLUE',
  syncMode: 'INCREMENTAL',
  intervalMinutes: 1,
  retryLimit: 3,
  queueName: 'douyin-clue-sync',
  endpoint: '/clue/add',
  status: 'ENABLED'
})

const metrics = computed(() => {
  if (currentMode.value === 'menu') {
    return {
      primaryLabel: '菜单数量',
      primaryValue: state.menuConfigs.length,
      primaryHint: '当前已编排的后台菜单数',
      secondaryLabel: '启用菜单',
      secondaryValue: state.menuConfigs.filter((item) => item.isEnabled === 1).length,
      secondaryHint: '当前仍对角色开放的菜单',
      tertiaryLabel: '角色覆盖',
      tertiaryValue: new Set(state.menuConfigs.flatMap((item) => item.roleCodes || [])).size,
      tertiaryHint: '菜单已覆盖的角色数量'
    }
  }
  if (currentMode.value === 'third-party') {
    return {
      primaryLabel: '三方接口',
      primaryValue: state.thirdPartyApis.length,
      primaryHint: '当前接入的三方接口配置数',
      secondaryLabel: '启用接口',
      secondaryValue: state.thirdPartyApis.filter((item) => item.enabled === 1).length,
      secondaryHint: '当前处于启用状态的接口',
      tertiaryLabel: '客资拉取任务',
      tertiaryValue: state.thirdPartyApis.filter((item) => item.moduleCode === 'CLUE').length,
      tertiaryHint: '客资中心自动拉取依赖这些接口'
    }
  }
  if (currentMode.value === 'callback') {
    return {
      primaryLabel: '回调接口',
      primaryValue: state.callbackApis.length,
      primaryHint: '已配置的回调地址数量',
      secondaryLabel: '启用回调',
      secondaryValue: state.callbackApis.filter((item) => item.enabled === 1).length,
      secondaryHint: '当前仍接收通知的回调',
      tertiaryLabel: '签名方式',
      tertiaryValue: new Set(state.callbackApis.map((item) => item.signatureMode)).size,
      tertiaryHint: '确保不同回调都具备认证校验'
    }
  }
  if (currentMode.value === 'jobs') {
    return {
      primaryLabel: '任务总数',
      primaryValue: jobs.value.length,
      primaryHint: '当前调度任务数量',
      secondaryLabel: '启用任务',
      secondaryValue: jobs.value.filter((item) => ['ACTIVE', 'ENABLED'].includes(item.status)).length,
      secondaryHint: '已启用的自动同步任务',
      tertiaryLabel: '失败日志',
      tertiaryValue: logs.value.filter((item) => ['FAIL', 'FAILED'].includes(item.status)).length,
      tertiaryHint: '失败任务可在下方继续重试'
    }
  }
  if (currentMode.value === 'public-api') {
    return {
      primaryLabel: '对外接口',
      primaryValue: state.publicApis.length,
      primaryHint: '当前开放的外部查询接口数',
      secondaryLabel: '启用接口',
      secondaryValue: state.publicApis.filter((item) => item.enabled === 1).length,
      secondaryHint: '启用后可对外提供访问',
      tertiaryLabel: '缓存策略',
      tertiaryValue: state.publicApis.filter((item) => item.cachePolicy).length,
      tertiaryHint: '已配置缓存的接口数量'
    }
  }
  if (currentMode.value === 'dictionary') {
    return {
      primaryLabel: '字典项',
      primaryValue: state.dictionaries.length,
      primaryHint: '系统内全部字典项数量',
      secondaryLabel: '启用字典',
      secondaryValue: state.dictionaries.filter((item) => item.isEnabled === 1).length,
      secondaryHint: '当前生效中的字典项',
      tertiaryLabel: '字典类型',
      tertiaryValue: new Set(state.dictionaries.map((item) => item.dictType)).size,
      tertiaryHint: '不同业务共用的字典类型数量'
    }
  }
  return {
    primaryLabel: '系统参数',
    primaryValue: state.parameters.length,
    primaryHint: '系统级参数总数',
    secondaryLabel: '业务分类',
    secondaryValue: new Set(state.parameters.map((item) => item.category)).size,
    secondaryHint: '参数覆盖的业务分类数',
    tertiaryLabel: '关键参数',
    tertiaryValue: state.parameters.filter((item) => item.paramKey.includes('enabled')).length,
    tertiaryHint: '直接影响功能开关的参数数'
  }
})

function createMenuForm() {
  return {
    id: null,
    menuGroup: '',
    menuName: '',
    routePath: '',
    roleCodes: [],
    moduleCode: 'SYSTEM'
  }
}

function createThirdPartyForm() {
  return {
    id: null,
    apiName: '',
    moduleCode: 'CLUE',
    baseUrl: '',
    method: 'GET',
    authType: '',
    enabled: 1,
    syncMode: '增量同步',
    scheduleJobCode: ''
  }
}

function createCallbackForm() {
  return {
    id: null,
    callbackName: '',
    callbackUrl: '',
    signatureMode: '',
    enabled: 1,
    remark: ''
  }
}

function createPublicApiForm() {
  return {
    id: null,
    apiName: '',
    sourceTable: '',
    outputFields: '',
    authMode: '',
    rateLimit: '',
    cachePolicy: '',
    enabled: 1
  }
}

function createDictionaryForm() {
  return {
    id: null,
    dictType: '',
    itemCode: '',
    itemLabel: '',
    sortOrder: 10,
    isEnabled: 1
  }
}

function createParameterForm() {
  return {
    id: null,
    paramKey: '',
    paramValue: '',
    category: '',
    remark: ''
  }
}

function replaceState(nextState) {
  saveSystemConsoleState(nextState)
  Object.assign(state, loadSystemConsoleState())
}

function resetMenuForm() {
  Object.assign(menuForm, createMenuForm())
}

function resetThirdPartyForm() {
  Object.assign(thirdPartyForm, createThirdPartyForm())
}

function resetCallbackForm() {
  Object.assign(callbackForm, createCallbackForm())
}

function resetPublicApiForm() {
  Object.assign(publicApiForm, createPublicApiForm())
}

function resetDictionaryForm() {
  Object.assign(dictionaryForm, createDictionaryForm())
}

function resetParameterForm() {
  Object.assign(parameterForm, createParameterForm())
}

function pickMenu(row) {
  Object.assign(menuForm, {
    ...row,
    roleCodes: [...(row.roleCodes || [])]
  })
}

function saveMenu() {
  if (!menuForm.menuGroup || !menuForm.menuName || !menuForm.routePath) {
    ElMessage.warning('请先完整填写菜单信息')
    return
  }
  const nextItems = [...state.menuConfigs]
  if (menuForm.id) {
    const index = nextItems.findIndex((item) => item.id === menuForm.id)
    nextItems[index] = { ...nextItems[index], ...menuForm }
  } else {
    nextItems.push({
      ...menuForm,
      id: nextSystemId(nextItems),
      isEnabled: 1
    })
  }
  replaceState({ ...state, menuConfigs: nextItems })
  ElMessage.success('菜单配置已保存')
  resetMenuForm()
}

function pickCollectionItem(form, row) {
  Object.assign(form, { ...row })
}

function saveCollectionItem(collectionName, form, resetForm, successMessage) {
  const nextItems = [...state[collectionName]]
  if (form.id) {
    const index = nextItems.findIndex((item) => item.id === form.id)
    nextItems[index] = { ...nextItems[index], ...form }
  } else {
    nextItems.push({
      ...form,
      id: nextSystemId(nextItems)
    })
  }
  replaceState({
    ...state,
    [collectionName]: nextItems
  })
  ElMessage.success(successMessage)
  resetForm()
}

function toggleCollectionItem(collectionName, id, fieldName) {
  const nextItems = state[collectionName].map((item) =>
    item.id === id ? { ...item, [fieldName]: item[fieldName] === 1 ? 0 : 1 } : item
  )
  replaceState({
    ...state,
    [collectionName]: nextItems
  })
  ElMessage.success('状态已更新')
}

async function loadJobs() {
  try {
    jobs.value = await fetchSchedulerJobs()
  } catch {
    jobs.value = []
  }
}

async function loadLogs() {
  try {
    logs.value = await fetchSchedulerLogs(selectedJobCode.value || undefined)
  } catch {
    logs.value = []
  }
}

async function loadSchedulerData() {
  await Promise.all([loadJobs(), loadLogs()])
}

async function handleSaveJob() {
  try {
    await saveSchedulerJob({ ...jobForm })
    ElMessage.success('调度任务已保存')
    await loadSchedulerData()
  } catch {
    // HTTP 层统一处理错误
  }
}

async function handleTrigger(row) {
  try {
    await triggerSchedulerJob({
      jobCode: row.jobCode,
      payload: JSON.stringify({ source: 'system-setting' })
    })
    ElMessage.success('任务已触发')
    selectedJobCode.value = row.jobCode
    await loadSchedulerData()
  } catch {
    // HTTP 层统一处理错误
  }
}

async function handleRetry(row) {
  try {
    await retrySchedulerJob(row.jobCode)
    ElMessage.success('失败日志已重新入队')
    selectedJobCode.value = row.jobCode
    await loadSchedulerData()
  } catch {
    // HTTP 层统一处理错误
  }
}

function pickJob(row) {
  Object.assign(jobForm, {
    jobCode: row.jobCode,
    moduleCode: row.moduleCode,
    syncMode: row.syncMode,
    intervalMinutes: row.intervalMinutes,
    retryLimit: row.retryLimit,
    queueName: row.queueName,
    endpoint: row.endpoint,
    status: row.status
  })
}

watch(
  () => currentMode.value,
  async (mode) => {
    if (mode === 'jobs') {
      await loadSchedulerData()
    }
  },
  { immediate: true }
)
</script>
