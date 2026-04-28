<template>
  <div class="stack-page">
    <section v-if="currentMode === 'jobs'" class="panel compact-panel">
      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="jobWorkspace">
            <el-radio-button value="schedule">任务配置</el-radio-button>
            <el-radio-button value="logs">执行日志</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </section>

    <section v-if="currentMode === 'menu'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>菜单管理</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openSettingEditor('menu')">新增菜单</el-button>
        </div>
      </div>

      <el-drawer v-model="menuEditorVisible" class="config-editor-drawer" :size="drawerSize" :close-on-click-modal="false">
        <template #header>
          <div class="drawer-editor__header">
            <span class="drawer-editor__eyebrow">菜单管理</span>
            <h3>{{ menuForm.id ? `编辑菜单：${menuForm.menuName || '未命名'}` : '新增菜单' }}</h3>
            <div class="drawer-editor__meta">
              <span>{{ menuForm.menuGroup || '未设置一级菜单' }}</span>
              <span>{{ menuForm.routePath || '未设置路由' }}</span>
            </div>
          </div>
        </template>

        <div class="drawer-editor__body">
          <div class="form-grid">
            <div class="form-control full-span">
              <span>归属目录</span>
              <el-radio-group v-model="menuGroupMode">
                <el-radio-button value="existing">选择已有目录</el-radio-button>
                <el-radio-button value="new">新增一级目录</el-radio-button>
              </el-radio-group>
              <el-select
                v-if="menuGroupMode === 'existing'"
                v-model="menuForm.menuGroup"
                filterable
                placeholder="请选择菜单归属目录"
              >
                <el-option v-for="item in menuGroupOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input v-else v-model="newMenuGroupName" placeholder="请输入新的一级目录名称，如 系统设置" />
            </div>
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
        </div>

        <template #footer>
          <div class="drawer-editor__footer">
            <el-button @click="closeSettingEditor('menu')">取消</el-button>
            <el-button @click="resetMenuForm">重置表单</el-button>
            <el-button type="primary" @click="saveMenu">{{ menuForm.id ? '保存修改' : '保存菜单' }}</el-button>
          </div>
        </template>
      </el-drawer>

      <el-table :data="menuPagination.rows" :row-class-name="menuRowClassName" stripe>
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
          <h3>接口配置</h3>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openSettingEditor('third-party')">新增接口</el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="settingEditorMode === 'third-party'" class="inline-editor-shell">
          <div class="panel-heading compact">
            <div>
              <h3>{{ thirdPartyForm.id ? '编辑接口' : '新增接口' }}</h3>
            </div>
          </div>

          <div class="status-strip">
            <div class="status-pill">
              <span>授权状态</span>
              <strong>{{ formatAuthStatus(thirdPartyForm.authStatus || 'UNAUTHORIZED') }}</strong>
            </div>
            <div class="status-pill">
              <span>最近回调</span>
              <strong>{{ formatDateTime(thirdPartyForm.lastCallbackAt) || '--' }}</strong>
            </div>
            <div class="status-pill">
              <span>最近检测</span>
              <strong>{{ formatDateTime(thirdPartyForm.lastTestAt) || '--' }}</strong>
            </div>
          </div>

          <div class="form-grid">
            <div class="full-span form-group-title">基础鉴权</div>
            <label>
              <span>平台编码</span>
              <el-input v-model="thirdPartyForm.providerCode" placeholder="如 DOUYIN_LAIKE" />
            </label>
            <label>
              <span>平台名称</span>
              <el-input v-model="thirdPartyForm.providerName" placeholder="请输入平台名称" />
            </label>
            <label>
              <span>业务模块</span>
              <el-select v-model="thirdPartyForm.moduleCode">
                <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </label>
            <label>
              <span>执行模式</span>
              <el-select v-model="thirdPartyForm.executionMode">
                <el-option label="模拟" value="MOCK" />
                <el-option label="真实" value="LIVE" />
              </el-select>
            </label>
            <label>
              <span>认证方式</span>
              <el-input v-model="thirdPartyForm.authType" placeholder="如 AUTH_CODE / CLIENT_TOKEN" />
            </label>
            <label>
              <span>应用 AppId</span>
              <el-input v-model="thirdPartyForm.appId" placeholder="可填抖音开放平台 AppId" />
            </label>
            <label>
              <span>基础域名</span>
              <el-input v-model="thirdPartyForm.baseUrl" placeholder="请输入 https 地址" />
            </label>
            <label>
              <span>令牌地址</span>
              <el-input v-model="thirdPartyForm.tokenUrl" placeholder="如 https://open.douyin.com/oauth/client_token/" />
            </label>
            <label>
              <span>接口路径</span>
              <el-input v-model="thirdPartyForm.endpointPath" placeholder="如 /goodlife/v1/open_api/crm/clue/query/" />
            </label>
            <label>
              <span>客户端 Key</span>
              <el-input v-model="thirdPartyForm.clientKey" placeholder="请输入 client_key" />
            </label>
            <label>
              <span>客户端 Secret</span>
              <el-input
                v-model="thirdPartyForm.clientSecret"
                type="password"
                show-password
                :placeholder="thirdPartyForm.clientSecretMasked || '留空则保持原值'"
              />
            </label>
            <label>
              <span>授权码</span>
              <el-input
                v-model="thirdPartyForm.authCode"
                :placeholder="thirdPartyForm.authCodeMasked || '由回调自动回填，留空则保持原值'"
              />
            </label>
            <label>
              <span>回调状态</span>
              <el-input :model-value="formatCallbackProcessStatus(thirdPartyForm.lastCallbackStatus || '--')" readonly />
            </label>
            <label>
              <span>回调时间</span>
              <el-input :model-value="formatDateTime(thirdPartyForm.lastCallbackAt) || '--'" readonly />
            </label>
            <label>
              <span>授权码时间</span>
              <el-input :model-value="formatDateTime(thirdPartyForm.lastAuthCodeAt) || '--'" readonly />
            </label>
            <label>
              <span>Access Token</span>
              <el-input
                v-model="thirdPartyForm.accessToken"
                type="password"
                show-password
                :placeholder="thirdPartyForm.accessTokenMasked || '回调或测试成功后自动写入'"
              />
            </label>
            <label>
              <span>Refresh Token</span>
              <el-input
                v-model="thirdPartyForm.refreshToken"
                type="password"
                show-password
                :placeholder="thirdPartyForm.refreshTokenMasked || '如有回调返回则自动写入'"
              />
            </label>
            <div class="full-span form-group-title">回调与同步</div>
            <label>
              <span>账号 ID</span>
              <el-input v-model="thirdPartyForm.accountId" placeholder="请输入 account_id" />
            </label>
            <label>
              <span>本地生活账号</span>
              <el-input v-model="thirdPartyForm.lifeAccountIds" placeholder="多个逗号分隔" />
            </label>
            <label>
              <span>开放平台 OpenId</span>
              <el-input v-model="thirdPartyForm.openId" placeholder="可选" />
            </label>
            <label>
              <span>每页条数</span>
              <el-input-number v-model="thirdPartyForm.pageSize" :min="1" controls-position="right" />
            </label>
            <label>
              <span>超时毫秒</span>
              <el-input-number v-model="thirdPartyForm.requestTimeoutMs" :min="1000" :step="1000" controls-position="right" />
            </label>
            <label>
              <span>回调地址</span>
              <el-input v-model="thirdPartyForm.callbackUrl" placeholder="可选" />
            </label>
            <label>
              <span>重定向地址</span>
              <el-input v-model="thirdPartyForm.redirectUri" placeholder="如第三方授权回跳地址" />
            </label>
            <label>
              <span>授权范围</span>
              <el-input v-model="thirdPartyForm.scope" placeholder="可选，多个空格或逗号分隔" />
            </label>
            <label class="full-span">
              <span>最近回调结果</span>
              <el-input :model-value="thirdPartyForm.lastCallbackMessage || '--'" readonly />
            </label>
            <label class="full-span">
              <span>备注</span>
              <el-input v-model="thirdPartyForm.remark" placeholder="请输入备注" />
            </label>
          </div>

          <div class="action-group">
            <el-button type="primary" @click="handleSaveProvider">保存接口</el-button>
            <el-button @click="handleTestProvider">测试连接</el-button>
            <el-button @click="resetThirdPartyForm">重置表单</el-button>
            <el-button plain @click="closeSettingEditor('third-party')">收起</el-button>
          </div>
        </div>
      </el-collapse-transition>

      <el-table :data="thirdPartyPagination.rows" stripe>
        <el-table-column label="接口" min-width="220">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.providerName }}</strong>
              <span>{{ row.providerCode }} / {{ row.baseUrl }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            {{ formatModuleCode(row.moduleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="模式" width="100">
          <template #default="{ row }">
            {{ formatExecutionMode(row.executionMode) }}
          </template>
        </el-table-column>
        <el-table-column label="认证" width="140">
          <template #default="{ row }">
            {{ formatAuthType(row.authType) }}
          </template>
        </el-table-column>
        <el-table-column label="授权状态" width="140">
          <template #default="{ row }">
            {{ formatAuthStatus(row.authStatus || 'UNAUTHORIZED') }}
          </template>
        </el-table-column>
        <el-table-column label="最近回调" min-width="190">
          <template #default="{ row }">
            {{ formatCallbackProcessStatus(row.lastCallbackStatus || '--') }} / {{ formatDateTime(row.lastCallbackAt) || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="检测结果" min-width="200">
          <template #default="{ row }">
            {{ formatResultStatus(row.lastTestStatus || '--') }}{{ row.lastTestMessage ? ` / ${row.lastTestMessage}` : '' }}
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
              <el-button size="small" @click="pickProvider(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleProvider(row)">
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
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openSettingEditor('callback')">新增回调</el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="settingEditorMode === 'callback'" class="inline-editor-shell">
          <div class="panel-heading compact">
            <div>
              <h3>{{ callbackForm.id ? '编辑回调' : '新增回调' }}</h3>
            </div>
          </div>

          <div class="status-strip">
            <div class="status-pill">
              <span>最近状态</span>
              <strong>{{ formatCallbackProcessStatus(callbackForm.lastCallbackStatus || '--') }}</strong>
            </div>
            <div class="status-pill">
              <span>最近时间</span>
              <strong>{{ formatDateTime(callbackForm.lastCallbackAt) || '--' }}</strong>
            </div>
            <div class="status-pill">
              <span>最近 TraceId</span>
              <strong>{{ callbackForm.lastTraceId || '--' }}</strong>
            </div>
          </div>

          <div class="form-grid">
            <label>
              <span>平台编码</span>
              <el-input v-model="callbackForm.providerCode" placeholder="如 WECOM / DOUYIN_LAIKE" />
            </label>
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
            <label>
              <span>Token</span>
              <el-input
                v-model="callbackForm.tokenValue"
                type="password"
                show-password
                :placeholder="callbackForm.tokenMasked || '留空则保持原值'"
              />
            </label>
            <label>
              <span>AES Key</span>
              <el-input
                v-model="callbackForm.aesKey"
                type="password"
                show-password
                :placeholder="callbackForm.aesKeyMasked || '留空则保持原值'"
              />
            </label>
            <label class="full-span">
              <span>备注</span>
              <el-input v-model="callbackForm.remark" placeholder="请输入回调说明" />
            </label>
            <label>
              <span>最近授权码</span>
              <el-input :model-value="callbackForm.lastAuthCodeMasked || '--'" readonly />
            </label>
            <label class="full-span">
              <span>最近结果</span>
              <el-input :model-value="callbackForm.lastCallbackMessage || '--'" readonly />
            </label>
          </div>

          <div class="action-group">
            <el-button type="primary" @click="handleSaveCallback">保存回调</el-button>
            <el-button @click="resetCallbackForm">重置表单</el-button>
            <el-button plain @click="closeSettingEditor('callback')">收起</el-button>
          </div>
        </div>
      </el-collapse-transition>

      <el-table :data="callbackPagination.rows" stripe>
        <el-table-column label="平台" width="120" prop="providerCode" />
        <el-table-column label="回调名称" min-width="180" prop="callbackName" />
        <el-table-column label="回调地址" min-width="260" prop="callbackUrl" />
        <el-table-column label="签名方式" width="160" prop="signatureMode" />
        <el-table-column label="最近状态" width="140">
          <template #default="{ row }">
            {{ formatCallbackProcessStatus(row.lastCallbackStatus || '--') }}
          </template>
        </el-table-column>
        <el-table-column label="最近时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.lastCallbackAt) || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="最近结果" min-width="220">
          <template #default="{ row }">
            {{ row.lastCallbackMessage || '--' }}
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
              <el-button size="small" @click="pickCallback(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleCallback(row)">
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

      <section class="panel panel--nested">
        <div class="panel-heading compact">
          <div>
            <h3>最近回调记录</h3>
          </div>
        </div>

        <el-table :data="callbackLogPagination.rows" stripe>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.receivedAt) || '--' }}
            </template>
          </el-table-column>
          <el-table-column label="平台" width="120" prop="providerCode" />
          <el-table-column label="回调" min-width="160" prop="callbackName" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.processStatus === 'SUCCESS' ? 'success' : row.processStatus === 'FAILED' ? 'danger' : 'info'">
                {{ formatCallbackProcessStatus(row.processStatus || '--') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="验签" width="120">
            <template #default="{ row }">
              {{ formatCallbackSignatureStatus(row.signatureStatus || '--') }}
            </template>
          </el-table-column>
          <el-table-column label="授权码" min-width="150">
            <template #default="{ row }">
              {{ row.authCode ? `${row.authCode.slice(0, 2)}****${row.authCode.slice(-2)}` : '--' }}
            </template>
          </el-table-column>
          <el-table-column label="结果" min-width="220" prop="processMessage" />
          <el-table-column label="TraceId" min-width="220" prop="traceId" />
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="callbackLogPagination.total"
            :current-page="callbackLogPagination.currentPage"
            :page-size="callbackLogPagination.pageSize"
            :page-sizes="callbackLogPagination.pageSizes"
            @size-change="callbackLogPagination.handleSizeChange"
            @current-change="callbackLogPagination.handleCurrentChange"
          />
        </div>
      </section>
    </section>

    <template v-else-if="currentMode === 'jobs'">
      <section v-if="jobWorkspace === 'schedule'" class="panel">
        <div class="panel-heading">
          <div>
            <h3>任务调度</h3>
          </div>
          <div class="action-group">
            <el-button type="primary" @click="openSettingEditor('jobs')">新增任务</el-button>
            <el-button @click="loadSchedulerData">刷新</el-button>
          </div>
        </div>

        <el-collapse-transition>
          <div v-show="settingEditorMode === 'jobs'" class="inline-editor-shell">
            <div class="panel-heading compact">
              <div>
                <h3>任务配置</h3>
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
                <span>绑定接口</span>
                <el-select v-model="jobForm.providerId" clearable placeholder="请选择接口配置">
                  <el-option
                    v-for="item in providerConfigs"
                    :key="item.id"
                    :label="`${item.providerName}（${formatExecutionMode(item.executionMode)}）`"
                    :value="item.id"
                  />
                </el-select>
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
              <el-button plain @click="closeSettingEditor('jobs')">收起</el-button>
            </div>
          </div>
        </el-collapse-transition>

        <el-table :data="jobPagination.rows" stripe>
          <el-table-column label="任务编码" min-width="180" prop="jobCode" />
          <el-table-column label="模块" width="120">
            <template #default="{ row }">
              {{ formatModuleCode(row.moduleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="绑定接口" min-width="180">
            <template #default="{ row }">
              {{ formatProviderName(row.providerId) }}
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

      <section v-else class="panel">
        <div class="panel-heading">
          <div>
            <h3>执行日志</h3>
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
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openSettingEditor('public-api')">新增接口</el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="settingEditorMode === 'public-api'" class="inline-editor-shell">
          <div class="panel-heading compact">
            <div>
              <h3>{{ publicApiForm.id ? '编辑对外接口' : '新增对外接口' }}</h3>
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
            <el-button plain @click="closeSettingEditor('public-api')">收起</el-button>
          </div>
        </div>
      </el-collapse-transition>

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
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openSettingEditor('dictionary')">新增字典</el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="settingEditorMode === 'dictionary'" class="inline-editor-shell">
          <div class="panel-heading compact">
            <div>
              <h3>{{ dictionaryForm.id ? '编辑字典' : '新增字典' }}</h3>
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
            <el-button plain @click="closeSettingEditor('dictionary')">收起</el-button>
          </div>
        </div>
      </el-collapse-transition>

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
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openSettingEditor('parameter')">新增参数</el-button>
        </div>
      </div>

      <el-collapse-transition>
        <div v-show="settingEditorMode === 'parameter'" class="inline-editor-shell">
          <div class="panel-heading compact">
            <div>
              <h3>{{ parameterForm.id ? '编辑参数' : '新增参数' }}</h3>
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
            <el-button plain @click="closeSettingEditor('parameter')">收起</el-button>
          </div>
        </div>
      </el-collapse-transition>

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
import {
  fetchIntegrationCallbacks,
  fetchIntegrationCallbackLogs,
  fetchIntegrationProviders,
  fetchSchedulerJobs,
  fetchSchedulerLogs,
  retrySchedulerJob,
  saveIntegrationCallback,
  saveIntegrationProvider,
  saveSchedulerJob,
  testIntegrationProvider,
  triggerSchedulerJob
} from '../api/scheduler'
import { fetchSystemAccessSnapshot, saveSystemAccessMenu } from '../api/systemAccess'
import { useTablePagination } from '../composables/useTablePagination'
import {
  formatAuthStatus,
  formatAuthType,
  formatCallbackProcessStatus,
  formatCallbackSignatureStatus,
  formatDateTime,
  formatExecutionMode,
  formatModuleCode,
  formatResultStatus,
  formatRoleCode,
  formatSchedulerStatus,
  formatSyncMode,
  statusTagType
} from '../utils/format'
import { loadSystemConsoleState, nextSystemId, saveSystemConsoleState } from '../utils/systemConsoleStore'

const route = useRoute()
const state = reactive(loadSystemConsoleState())
const providerConfigs = ref([])
const callbackConfigs = ref([])
const callbackLogs = ref([])
const jobs = ref([])
const logs = ref([])
const selectedJobCode = ref('')
const settingEditorMode = ref('')
const menuGroupMode = ref('existing')
const newMenuGroupName = ref('')
const jobWorkspace = ref('schedule')
const menuPagination = useTablePagination(computed(() => state.menuConfigs))
const thirdPartyPagination = useTablePagination(providerConfigs)
const callbackPagination = useTablePagination(callbackConfigs)
const callbackLogPagination = useTablePagination(callbackLogs)
const jobPagination = useTablePagination(jobs)
const logPagination = useTablePagination(logs)
const publicApiPagination = useTablePagination(computed(() => state.publicApis))
const dictionaryPagination = useTablePagination(computed(() => state.dictionaries))
const parameterPagination = useTablePagination(computed(() => state.parameters))

const currentMode = computed(() => route.meta.settingMode || 'menu')
const drawerSize = 'min(92vw, 640px)'
const menuGroupOptions = computed(() => {
  const seen = new Set()
  return state.menuConfigs
    .map((item) => String(item.menuGroup || '').trim())
    .filter(Boolean)
    .filter((group) => {
      if (seen.has(group)) {
        return false
      }
      seen.add(group)
      return true
    })
    .map((group) => ({
      label: group,
      value: group
    }))
})
const menuEditorVisible = computed({
  get: () => settingEditorMode.value === 'menu',
  set: (visible) => {
    if (!visible) {
      closeSettingEditor('menu')
    }
  }
})
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
  providerId: null,
  endpoint: '/clue/add',
  status: 'ENABLED'
})
function createMenuForm() {
  return {
    id: null,
    menuGroup: '',
    menuName: '',
    routePath: '',
    roleCodes: [],
    moduleCode: 'SYSTEM',
    permissionCode: '',
    componentKey: '',
    isEnabled: 1,
    sortOrder: null
  }
}

function createThirdPartyForm() {
  return {
    id: null,
    providerCode: 'DOUYIN_LAIKE',
    providerName: '抖音来客线索',
    moduleCode: 'CLUE',
    executionMode: 'MOCK',
    authType: 'CLIENT_TOKEN',
    appId: '',
    baseUrl: '',
    tokenUrl: '',
    endpointPath: '/goodlife/v1/open_api/crm/clue/query/',
    clientKey: '',
    clientSecret: '',
    clientSecretMasked: '',
    redirectUri: '',
    scope: '',
    authCode: '',
    authCodeMasked: '',
    accessToken: '',
    accessTokenMasked: '',
    refreshToken: '',
    refreshTokenMasked: '',
    accountId: '',
    lifeAccountIds: '',
    openId: '',
    pageSize: 20,
    requestTimeoutMs: 10000,
    callbackUrl: '',
    authStatus: '',
    lastCallbackStatus: '',
    lastCallbackMessage: '',
    lastCallbackAt: '',
    lastAuthCodeAt: '',
    enabled: 1,
    remark: ''
  }
}

function createCallbackForm() {
  return {
    id: null,
    providerCode: 'WECOM',
    callbackName: '',
    callbackUrl: '',
    signatureMode: 'WECOM_CALLBACK',
    tokenValue: '',
    tokenMasked: '',
    aesKey: '',
    aesKeyMasked: '',
    lastCallbackStatus: '',
    lastCallbackMessage: '',
    lastCallbackAt: '',
    lastTraceId: '',
    lastAuthCode: '',
    lastAuthCodeMasked: '',
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

function applyAccessSnapshot(snapshot) {
  if (!snapshot) {
    return
  }
  replaceState({
    ...state,
    menuConfigs: (snapshot.menus || []).map(normalizeAccessMenu),
    roles: (snapshot.roles || []).map(normalizeAccessRole)
  })
  menuPagination.reset()
}

function normalizeAccessMenu(item) {
  return {
    id: item.id,
    menuGroup: item.menuGroup || '',
    menuName: item.menuName || '',
    routePath: item.routePath || '',
    roleCodes: item.roleCodes || [],
    moduleCode: item.moduleCode || 'SYSTEM',
    permissionCode: item.permissionCode || '',
    componentKey: item.componentKey || '',
    isEnabled: item.isEnabled ?? 1,
    sortOrder: item.sortOrder ?? Number(item.id || 0)
  }
}

function normalizeAccessRole(item) {
  return {
    id: item.id,
    roleCode: item.roleCode || '',
    roleName: item.roleName || '',
    dataScope: item.dataScope || 'SELF',
    roleType: item.roleType || 'BUSINESS',
    moduleCodes: item.moduleCodes || [],
    menuRoutes: item.menuRoutes || [],
    permissionCodes: item.permissionCodes || [],
    isEnabled: item.isEnabled ?? 1,
    sort: item.sortOrder ?? Number(item.id || 0)
  }
}

async function loadSystemAccessConfig() {
  try {
    applyAccessSnapshot(await fetchSystemAccessSnapshot())
  } catch {
    // 请求层已经提示错误；保留本地缓存，避免配置页空白。
  }
}

function resetMenuForm() {
  Object.assign(menuForm, createMenuForm())
  menuGroupMode.value = 'existing'
  newMenuGroupName.value = ''
  menuForm.menuGroup = menuGroupOptions.value[0]?.value || ''
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

function openSettingEditor(mode) {
  settingEditorMode.value = mode
  if (mode === 'menu') {
    resetMenuForm()
  }
  if (mode === 'third-party') {
    resetThirdPartyForm()
  }
  if (mode === 'callback') {
    resetCallbackForm()
  }
  if (mode === 'jobs') {
    Object.assign(jobForm, {
      jobCode: 'DOUYIN_CLUE_INCREMENTAL',
      moduleCode: 'CLUE',
      syncMode: 'INCREMENTAL',
      intervalMinutes: 1,
      retryLimit: 3,
      queueName: 'douyin-clue-sync',
      providerId: providerConfigs.value[0]?.id || null,
      endpoint: '/clue/add',
      status: 'ENABLED'
    })
    jobWorkspace.value = 'schedule'
  }
  if (mode === 'public-api') {
    resetPublicApiForm()
  }
  if (mode === 'dictionary') {
    resetDictionaryForm()
  }
  if (mode === 'parameter') {
    resetParameterForm()
  }
}

function closeSettingEditor(mode) {
  if (settingEditorMode.value !== mode) {
    return
  }
  settingEditorMode.value = ''
}

function pickMenu(row) {
  Object.assign(menuForm, {
    ...row,
    roleCodes: [...(row.roleCodes || [])]
  })
  menuGroupMode.value = 'existing'
  newMenuGroupName.value = ''
  settingEditorMode.value = 'menu'
}

function menuRowClassName({ row }) {
  return settingEditorMode.value === 'menu' && menuForm.id === row.id ? 'is-editing-row' : ''
}

async function saveMenu() {
  const resolvedMenuGroup = menuGroupMode.value === 'new' ? newMenuGroupName.value.trim() : String(menuForm.menuGroup || '').trim()
  if (!resolvedMenuGroup || !menuForm.menuName || !menuForm.routePath) {
    ElMessage.warning('请先完整填写菜单信息')
    return
  }
  menuForm.menuGroup = resolvedMenuGroup
  try {
    await saveSystemAccessMenu({
      ...menuForm,
      menuGroup: resolvedMenuGroup,
      isEnabled: menuForm.isEnabled ?? 1,
      sortOrder: menuForm.sortOrder
    })
    await loadSystemAccessConfig()
    ElMessage.success('菜单配置已保存，重新登录后按后端授权生效')
    resetMenuForm()
    closeSettingEditor('menu')
  } catch {
    // HTTP 层统一提示
  }
}

function pickCollectionItem(form, row) {
  Object.assign(form, { ...row })
  settingEditorMode.value = currentMode.value
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
  closeSettingEditor(currentMode.value)
}

async function toggleCollectionItem(collectionName, id, fieldName) {
  if (collectionName === 'menuConfigs') {
    const current = state.menuConfigs.find((item) => item.id === id)
    if (!current) {
      return
    }
    try {
      await saveSystemAccessMenu({
        ...current,
        [fieldName]: current[fieldName] === 1 ? 0 : 1
      })
      await loadSystemAccessConfig()
      ElMessage.success('菜单状态已更新，重新登录后生效')
    } catch {
      // HTTP 层统一提示
    }
    return
  }
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

async function loadProviders() {
  try {
    providerConfigs.value = await fetchIntegrationProviders()
    thirdPartyPagination.reset()
  } catch {
    providerConfigs.value = []
  }
}

async function loadCallbacks() {
  try {
    callbackConfigs.value = await fetchIntegrationCallbacks()
    callbackPagination.reset()
  } catch {
    callbackConfigs.value = []
  }
}

async function loadCallbackLogs(providerCode) {
  try {
    callbackLogs.value = await fetchIntegrationCallbackLogs(providerCode || undefined)
    callbackLogPagination.reset()
  } catch {
    callbackLogs.value = []
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
  await Promise.all([loadJobs(), loadLogs(), loadProviders()])
}

async function handleSaveProvider() {
  const payload = await saveIntegrationProvider({ ...thirdPartyForm })
  Object.assign(thirdPartyForm, createThirdPartyForm(), payload, {
    clientSecret: '',
    authCode: '',
    accessToken: '',
    refreshToken: ''
  })
  await loadProviders()
  ElMessage.success('接口配置已保存')
}

async function handleTestProvider() {
  const payload = await testIntegrationProvider({ ...thirdPartyForm })
  Object.assign(thirdPartyForm, createThirdPartyForm(), payload, {
    clientSecret: '',
    authCode: '',
    accessToken: '',
    refreshToken: ''
  })
  ElMessage.success(payload.lastTestMessage || '接口连接检测完成')
}

function pickProvider(row) {
  Object.assign(thirdPartyForm, createThirdPartyForm(), row, {
    clientSecret: '',
    authCode: '',
    accessToken: '',
    refreshToken: ''
  })
  settingEditorMode.value = 'third-party'
}

async function toggleProvider(row) {
  await saveIntegrationProvider({
    ...row,
    enabled: row.enabled === 1 ? 0 : 1
  })
  await loadProviders()
  ElMessage.success('接口状态已更新')
}

async function handleSaveCallback() {
  const payload = await saveIntegrationCallback({ ...callbackForm })
  Object.assign(callbackForm, createCallbackForm(), payload, {
    tokenValue: '',
    aesKey: ''
  })
  await loadCallbacks()
  await loadCallbackLogs(callbackForm.providerCode || payload.providerCode)
  ElMessage.success('回调接口已保存')
}

function pickCallback(row) {
  Object.assign(callbackForm, createCallbackForm(), row, {
    tokenValue: '',
    aesKey: ''
  })
  settingEditorMode.value = 'callback'
}

async function toggleCallback(row) {
  await saveIntegrationCallback({
    ...row,
    enabled: row.enabled === 1 ? 0 : 1
  })
  await loadCallbacks()
  await loadCallbackLogs(row.providerCode)
  ElMessage.success('回调状态已更新')
}

async function handleSaveJob() {
  try {
    await saveSchedulerJob({ ...jobForm })
    ElMessage.success('调度任务已保存')
    closeSettingEditor('jobs')
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
    jobWorkspace.value = 'logs'
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
    jobWorkspace.value = 'logs'
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
    providerId: row.providerId || null,
    endpoint: row.endpoint,
    status: row.status
  })
  jobWorkspace.value = 'schedule'
  settingEditorMode.value = 'jobs'
}

function formatProviderName(providerId) {
  const matched = providerConfigs.value.find((item) => item.id === providerId)
  return matched ? matched.providerName : '--'
}

watch(
  () => currentMode.value,
  async (mode) => {
    settingEditorMode.value = ''
    if (mode === 'menu') {
      await loadSystemAccessConfig()
    }
    if (mode === 'third-party') {
      await loadProviders()
    }
    if (mode === 'callback') {
      await Promise.all([loadCallbacks(), loadCallbackLogs()])
    }
    if (mode === 'jobs') {
      jobWorkspace.value = 'schedule'
      await loadSchedulerData()
    }
  },
  { immediate: true }
)
</script>
