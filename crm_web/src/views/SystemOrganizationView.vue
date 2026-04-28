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

    <section v-if="currentMode === 'role'" class="panel compact-panel">
      <div class="toolbar toolbar--compact">
        <div class="toolbar-tabs">
          <el-radio-group v-model="roleWorkspace">
            <el-radio-button value="role">角色信息 / 菜单入口</el-radio-button>
            <el-radio-button value="policy">接口动作 / 数据范围</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </section>

    <section v-if="currentMode === 'department'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>部门管理</h3>
          <p>设置组织架构，并约束各部门只处理自己部门内有效数据。</p>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openManagementEditor('department')">新增部门</el-button>
        </div>
      </div>

      <el-drawer v-model="departmentEditorVisible" class="config-editor-drawer" :size="drawerSize" :close-on-click-modal="false">
        <template #header>
          <div class="drawer-editor__header">
            <span class="drawer-editor__eyebrow">部门管理</span>
            <h3>{{ departmentForm.id ? `编辑部门：${departmentForm.departmentName || '未命名'}` : '新增部门' }}</h3>
            <div class="drawer-editor__meta">
              <span>{{ departmentForm.departmentCode || '未设置编码' }}</span>
              <span>{{ getDepartmentName(state.departments, departmentForm.parentCode) || '无上级部门' }}</span>
            </div>
          </div>
        </template>

        <div class="drawer-editor__body">
          <div class="form-grid">
            <label>
              <span>部门编码</span>
              <el-input v-model="departmentForm.departmentCode" placeholder="如 CLUE" />
            </label>
            <label>
              <span>部门名称</span>
              <el-input v-model="departmentForm.departmentName" placeholder="请输入部门名称" />
            </label>
            <label>
              <span>上级部门</span>
              <el-select v-model="departmentForm.parentCode" clearable placeholder="请选择上级部门">
                <el-option v-for="item in state.departments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
              </el-select>
            </label>
            <label>
              <span>管理角色</span>
              <el-select v-model="departmentForm.managerRoleCode" placeholder="请选择管理角色">
                <el-option v-for="item in state.roles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
              </el-select>
            </label>
            <label class="full-span">
              <span>数据范围规则</span>
              <el-input v-model="departmentForm.dataScopeRule" placeholder="描述部门内有效数据边界" />
            </label>
            <label class="full-span">
              <span>备注</span>
              <el-input v-model="departmentForm.remark" type="textarea" :rows="3" placeholder="请输入部门备注" />
            </label>
          </div>
        </div>

        <template #footer>
          <div class="drawer-editor__footer">
            <el-button @click="closeManagementEditor('department')">取消</el-button>
            <el-button @click="resetDepartmentForm">重置表单</el-button>
            <el-button type="primary" @click="saveDepartment">{{ departmentForm.id ? '保存修改' : '保存部门' }}</el-button>
          </div>
        </template>
      </el-drawer>

      <el-table :data="departmentPagination.rows" :row-class-name="departmentRowClassName" stripe>
        <el-table-column label="部门" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.departmentName }}</strong>
              <span>{{ row.departmentCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="上级部门" min-width="160">
          <template #default="{ row }">
            {{ getDepartmentName(state.departments, row.parentCode) }}
          </template>
        </el-table-column>
        <el-table-column label="管理角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.managerRoleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="数据范围" min-width="220" prop="dataScopeRule" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickDepartment(row)">编辑</el-button>
              <el-button size="small" plain @click="toggleDepartment(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="departmentPagination.total"
          :current-page="departmentPagination.currentPage"
          :page-size="departmentPagination.pageSize"
          :page-sizes="departmentPagination.pageSizes"
          @size-change="departmentPagination.handleSizeChange"
          @current-change="departmentPagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'employee'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>员工管理</h3>
          <p>支持新增、停用与调岗；停用前会自动转移名下数据，停用员工不可登录。</p>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openManagementEditor('employee')">新增员工</el-button>
        </div>
      </div>

      <div class="toolbar">
        <div class="toolbar__filters">
          <el-select v-model="employeeFilter.departmentCode" clearable placeholder="按部门筛选" style="width: 220px">
            <el-option v-for="item in availableDepartments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
          </el-select>
          <el-select v-model="employeeFilter.status" clearable placeholder="按状态筛选" style="width: 180px">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </div>
      </div>

      <el-drawer v-model="employeeEditorVisible" class="config-editor-drawer" :size="drawerSize" :close-on-click-modal="false">
        <template #header>
          <div class="drawer-editor__header">
            <span class="drawer-editor__eyebrow">员工管理</span>
            <h3>{{ employeeForm.id ? `编辑员工：${employeeForm.userName || '未命名'}` : '新增员工' }}</h3>
            <div class="drawer-editor__meta">
              <span>{{ employeeForm.accountName || '未设置账号' }}</span>
              <span>{{ getDepartmentName(state.departments, employeeForm.departmentCode) || '未设置部门' }}</span>
            </div>
          </div>
        </template>

        <div class="drawer-editor__body">
          <div class="form-grid">
            <label>
              <span>账号</span>
              <el-input v-model="employeeForm.accountName" placeholder="请输入登录账号" />
            </label>
            <label>
              <span>姓名</span>
              <el-input v-model="employeeForm.userName" placeholder="请输入员工姓名" />
            </label>
            <label>
              <span>部门</span>
              <el-select v-model="employeeForm.departmentCode" placeholder="请选择部门">
                <el-option v-for="item in availableDepartments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
              </el-select>
            </label>
            <label>
              <span>岗位</span>
              <el-select v-model="employeeForm.positionCode" placeholder="请选择岗位">
                <el-option v-for="item in filteredPositionsForEmployeeForm" :key="item.id" :label="item.positionName" :value="item.positionCode" />
              </el-select>
            </label>
            <label>
              <span>角色</span>
              <el-select v-model="employeeForm.roleCode" placeholder="请选择角色">
                <el-option v-for="item in availableRoles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
              </el-select>
            </label>
            <label>
              <span>名下数据量</span>
              <el-input-number v-model="employeeForm.ownedDataCount" :min="0" controls-position="right" />
            </label>
          </div>
        </div>

        <template #footer>
          <div class="drawer-editor__footer">
            <el-button @click="closeManagementEditor('employee')">取消</el-button>
            <el-button @click="resetEmployeeForm">重置表单</el-button>
            <el-button type="primary" @click="saveEmployee">{{ employeeForm.id ? '保存修改' : '保存员工' }}</el-button>
          </div>
        </template>
      </el-drawer>

      <el-table :data="employeePagination.rows" :row-class-name="employeeRowClassName" stripe>
        <el-table-column label="员工" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.userName }}</strong>
              <span>{{ row.accountName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="140">
          <template #default="{ row }">
            {{ getDepartmentName(state.departments, row.departmentCode) }}
          </template>
        </el-table-column>
        <el-table-column label="岗位" width="160">
          <template #default="{ row }">
            {{ getPositionName(state.positions, row.positionCode) }}
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            {{ formatRoleCode(row.roleCode) }}
          </template>
        </el-table-column>
        <el-table-column label="名下数据" width="110" prop="ownedDataCount" />
        <el-table-column label="登录状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.canLogin === 1 ? 'success' : 'info'">{{ row.canLogin === 1 ? '可登录' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickEmployee(row)">编辑</el-button>
              <el-button size="small" plain @click="moveEmployee(row)">调岗</el-button>
              <el-button size="small" type="warning" @click="toggleEmployeeStatus(row)">
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="employeePagination.total"
          :current-page="employeePagination.currentPage"
          :page-size="employeePagination.pageSize"
          :page-sizes="employeePagination.pageSizes"
          @size-change="employeePagination.handleSizeChange"
          @current-change="employeePagination.handleCurrentChange"
        />
      </div>
    </section>

    <section v-else-if="currentMode === 'position'" class="panel">
      <div class="panel-heading">
        <div>
          <h3>岗位管理</h3>
          <p>新增、修改、删除岗位；删除前会自动把名下员工迁移到同部门其它岗位。</p>
        </div>
        <div class="action-group">
          <el-button type="primary" @click="openManagementEditor('position')">新增岗位</el-button>
        </div>
      </div>

      <el-drawer v-model="positionEditorVisible" class="config-editor-drawer" :size="drawerSize" :close-on-click-modal="false">
        <template #header>
          <div class="drawer-editor__header">
            <span class="drawer-editor__eyebrow">岗位管理</span>
            <h3>{{ positionForm.id ? `编辑岗位：${positionForm.positionName || '未命名'}` : '新增岗位' }}</h3>
            <div class="drawer-editor__meta">
              <span>{{ positionForm.positionCode || '未设置编码' }}</span>
              <span>{{ getDepartmentName(state.departments, positionForm.departmentCode) || '未设置部门' }}</span>
            </div>
          </div>
        </template>

        <div class="drawer-editor__body">
          <div class="form-grid">
            <label>
              <span>岗位编码</span>
              <el-input v-model="positionForm.positionCode" placeholder="如 CLUE_SUPERVISOR" />
            </label>
            <label>
              <span>岗位名称</span>
              <el-input v-model="positionForm.positionName" placeholder="请输入岗位名称" />
            </label>
            <label>
              <span>所属部门</span>
              <el-select v-model="positionForm.departmentCode" placeholder="请选择部门">
                <el-option v-for="item in state.departments" :key="item.id" :label="item.departmentName" :value="item.departmentCode" />
              </el-select>
            </label>
            <label class="full-span">
              <span>备注</span>
              <el-input v-model="positionForm.remark" placeholder="请输入岗位说明" />
            </label>
          </div>
        </div>

        <template #footer>
          <div class="drawer-editor__footer">
            <el-button @click="closeManagementEditor('position')">取消</el-button>
            <el-button @click="resetPositionForm">重置表单</el-button>
            <el-button type="primary" @click="savePosition">{{ positionForm.id ? '保存修改' : '保存岗位' }}</el-button>
          </div>
        </template>
      </el-drawer>

      <el-table :data="positionPagination.rows" :row-class-name="positionRowClassName" stripe>
        <el-table-column label="岗位" min-width="180">
          <template #default="{ row }">
            <div class="table-primary">
              <strong>{{ row.positionName }}</strong>
              <span>{{ row.positionCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="140">
          <template #default="{ row }">
            {{ getDepartmentName(state.departments, row.departmentCode) }}
          </template>
        </el-table-column>
        <el-table-column label="在岗人数" width="100">
          <template #default="{ row }">
            {{ employeesInPosition(row.positionCode).length }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220" prop="remark" />
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" @click="pickPosition(row)">编辑</el-button>
              <el-button size="small" plain @click="togglePosition(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
              <el-button size="small" type="danger" @click="removePosition(row)">删除并转岗</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="positionPagination.total"
          :current-page="positionPagination.currentPage"
          :page-size="positionPagination.pageSize"
          :page-sizes="positionPagination.pageSizes"
          @size-change="positionPagination.handleSizeChange"
          @current-change="positionPagination.handleCurrentChange"
        />
      </div>
    </section>

    <template v-else>
      <section v-if="roleWorkspace === 'role'" class="panel">
        <div class="panel-heading">
          <div>
            <h3>角色管理</h3>
            <p>这里维护菜单入口可见范围；接口动作和数据范围在“接口动作 / 数据范围”中生效。</p>
          </div>
          <div class="action-group">
            <el-button type="primary" @click="openManagementEditor('role')">新增角色</el-button>
          </div>
        </div>

        <el-drawer v-model="roleEditorVisible" class="config-editor-drawer" :size="wideDrawerSize" :close-on-click-modal="false">
          <template #header>
            <div class="drawer-editor__header">
              <span class="drawer-editor__eyebrow">角色管理</span>
              <h3>{{ roleForm.id ? `编辑角色：${roleForm.roleName || '未命名'}` : '新增角色' }}</h3>
              <div class="drawer-editor__meta">
                <span>{{ roleForm.roleCode || '未设置编码' }}</span>
                <span>{{ formatScope(roleForm.dataScope) }}</span>
              </div>
            </div>
          </template>

          <div class="drawer-editor__body">
            <el-tabs v-model="roleEditorTab" class="role-editor-tabs">
              <el-tab-pane label="基础信息" name="basic">
                <div class="form-grid">
                  <label>
                    <span>角色编码</span>
                    <el-input v-model="roleForm.roleCode" :disabled="Boolean(roleForm.id)" placeholder="如 STORE_SERVICE" />
                  </label>
                  <label>
                    <span>角色名称</span>
                    <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
                  </label>
                  <label>
                    <span>数据范围</span>
                    <el-select v-model="roleForm.dataScope" placeholder="请选择数据范围">
                      <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </label>
                  <label class="full-span">
                    <span>模块权限</span>
                    <el-select v-model="roleForm.moduleCodes" multiple placeholder="请选择模块权限">
                      <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                  </label>
                  <label class="full-span">
                    <span>备注</span>
                    <el-input v-model="roleForm.remark" placeholder="请输入角色说明" />
                  </label>
                </div>
              </el-tab-pane>

              <el-tab-pane label="菜单入口" name="menus">
                <div class="permission-panel">
                  <div class="permission-panel__toolbar">
                    <div>
                      <strong>可见菜单</strong>
                      <span>已选择 {{ selectedRoleMenuPaths.length }} 个菜单入口</span>
                    </div>
                    <div class="action-group">
                      <el-button size="small" @click="selectAllRoleMenus">全选菜单</el-button>
                      <el-button size="small" plain @click="clearRoleMenus">清空</el-button>
                    </div>
                  </div>

                  <el-checkbox-group v-model="selectedRoleMenuPaths" class="menu-permission-grid">
                    <section v-for="group in roleMenuGroups" :key="group.name" class="menu-permission-group">
                      <div class="menu-permission-group__title">
                        <strong>{{ group.name }}</strong>
                        <span>{{ group.items.length }} 项</span>
                      </div>
                      <el-checkbox v-for="item in group.items" :key="item.routePath" :value="item.routePath" class="menu-permission-item">
                        <span>{{ item.menuName }}</span>
                        <small>{{ formatModuleCode(item.moduleCode) }} · {{ item.routePath }}</small>
                      </el-checkbox>
                    </section>
                  </el-checkbox-group>
                </div>
              </el-tab-pane>

              <el-tab-pane label="接口动作 / 数据范围" name="policies">
                <div class="permission-panel">
                  <div class="permission-panel__toolbar">
                    <div>
                      <strong>接口动作授权</strong>
                      <span>接口权限仍由后端策略校验，这里可查看当前角色已配置策略。</span>
                    </div>
                    <el-button size="small" type="primary" @click="openPolicyEditorForCurrentRole">新增接口策略</el-button>
                  </div>
                  <el-table :data="currentRolePolicies" stripe>
                    <el-table-column label="模块" width="120">
                      <template #default="{ row }">{{ formatModuleCode(row.moduleCode) }}</template>
                    </el-table-column>
                    <el-table-column label="动作" width="120">
                      <template #default="{ row }">{{ formatActionCode(row.actionCode) }}</template>
                    </el-table-column>
                    <el-table-column label="范围" width="120">
                      <template #default="{ row }">{{ formatScope(row.dataScope) }}</template>
                    </el-table-column>
                    <el-table-column label="条件规则" min-width="180" prop="conditionRule" />
                  </el-table>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>

          <template #footer>
            <div class="drawer-editor__footer">
              <el-button @click="closeManagementEditor('role')">取消</el-button>
              <el-button @click="resetRoleForm">重置表单</el-button>
              <el-button type="primary" @click="saveRole">{{ roleForm.id ? '保存修改' : '保存角色' }}</el-button>
            </div>
          </template>
        </el-drawer>

        <el-table :data="rolePagination.rows" :row-class-name="roleRowClassName" stripe>
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <div class="table-primary">
                <strong>{{ row.roleName }}</strong>
                <span>{{ row.roleCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="数据范围" width="120">
            <template #default="{ row }">
              {{ formatScope(row.dataScope) }}
            </template>
          </el-table-column>
          <el-table-column label="模块权限" min-width="240">
            <template #default="{ row }">
              {{ (row.moduleCodes || []).map(formatModuleCode).join(' / ') || '--' }}
            </template>
          </el-table-column>
          <el-table-column label="配置人员数" width="120">
            <template #default="{ row }">
              {{ employeesInRole(row.roleCode).length }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" @click="pickRole(row)">编辑</el-button>
                <el-button size="small" plain @click="toggleRole(row)">{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
                <el-button size="small" type="danger" plain :disabled="row.isEnabled !== 1" @click="removeRole(row)">停用保留</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="rolePagination.total"
            :current-page="rolePagination.currentPage"
            :page-size="rolePagination.pageSize"
            :page-sizes="rolePagination.pageSizes"
            @size-change="rolePagination.handleSizeChange"
            @current-change="rolePagination.handleCurrentChange"
          />
        </div>
      </section>

      <section v-else class="panel">
        <div class="panel-heading">
          <div>
            <h3>角色授权策略</h3>
            <p>这里维护真正的接口权限和 ABAC 数据范围；即使手动输入 URL，也会按这些策略校验。</p>
          </div>
          <div class="action-group">
            <el-button type="primary" @click="openPolicyEditor">新增策略</el-button>
          </div>
        </div>

        <el-drawer v-model="policyDrawerVisible" class="config-editor-drawer" :size="wideDrawerSize" :close-on-click-modal="false">
          <template #header>
            <div class="drawer-editor__header">
              <span class="drawer-editor__eyebrow">权限策略</span>
              <h3>新增授权策略</h3>
              <div class="drawer-editor__meta">
                <span>{{ formatModuleCode(policyForm.moduleCode) }}</span>
                <span>{{ formatActionCode(policyForm.actionCode) }}</span>
                <span>{{ formatRoleCode(policyForm.roleCode) }}</span>
              </div>
            </div>
          </template>

          <div class="drawer-editor__body">
            <div class="form-grid">
              <label>
                <span>模块</span>
                <el-select v-model="policyForm.moduleCode">
                  <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </label>
              <label>
                <span>动作</span>
                <el-select v-model="policyForm.actionCode">
                  <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </label>
              <label>
                <span>角色</span>
                <el-select v-model="policyForm.roleCode">
                  <el-option v-for="item in state.roles" :key="item.id" :label="item.roleName" :value="item.roleCode" />
                </el-select>
              </label>
              <label>
                <span>范围</span>
                <el-select v-model="policyForm.dataScope">
                  <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </label>
              <label class="full-span">
                <span>条件规则</span>
                <el-input v-model="policyForm.conditionRule" placeholder="如 order(status=appointment)" />
              </label>
            </div>
          </div>

          <template #footer>
            <div class="drawer-editor__footer">
              <el-button @click="closePolicyEditor">取消</el-button>
              <el-button @click="resetPolicyForm">重置表单</el-button>
              <el-button type="primary" @click="savePolicy">保存授权策略</el-button>
            </div>
          </template>
        </el-drawer>

        <el-table :data="policyPagination.rows" stripe>
          <el-table-column label="模块" width="140">
            <template #default="{ row }">
              {{ formatModuleCode(row.moduleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="动作" width="120">
            <template #default="{ row }">
              {{ formatActionCode(row.actionCode) }}
            </template>
          </el-table-column>
          <el-table-column label="角色" width="160">
            <template #default="{ row }">
              {{ formatRoleCode(row.roleCode) }}
            </template>
          </el-table-column>
          <el-table-column label="范围" width="100">
            <template #default="{ row }">
              {{ formatScope(row.dataScope) }}
            </template>
          </el-table-column>
          <el-table-column label="条件规则" min-width="220" prop="conditionRule" />
        </el-table>

        <div class="table-pagination">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="policyPagination.total"
            :current-page="policyPagination.currentPage"
            :page-size="policyPagination.pageSize"
            :page-sizes="policyPagination.pageSizes"
            @size-change="policyPagination.handleSizeChange"
            @current-change="policyPagination.handleCurrentChange"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { fetchPermissionPolicies, savePermissionPolicy } from '../api/permission'
import { fetchSystemAccessSnapshot, saveSystemAccessRole } from '../api/systemAccess'
import { useTablePagination } from '../composables/useTablePagination'
import { currentUser } from '../utils/auth'
import {
  formatActionCode,
  formatModuleCode,
  formatRoleCode,
  formatScope
} from '../utils/format'
import { scopeOptions } from '../utils/permission'
import {
  filterEmployeesByRole,
  getDepartmentName,
  getManagedDepartmentCodes,
  getPositionName,
  loadSystemConsoleState,
  nextSystemId,
  saveSystemConsoleState
} from '../utils/systemConsoleStore'

const route = useRoute()
const state = reactive(loadSystemConsoleState())
const policies = ref([])

const moduleOptions = [
  { label: '客资', value: 'CLUE' },
  { label: '订单', value: 'ORDER' },
  { label: '服务单', value: 'PLANORDER' },
  { label: '薪酬', value: 'SALARY' },
  { label: '财务', value: 'FINANCE' },
  { label: '调度', value: 'SCHEDULER' },
  { label: '系统管理', value: 'SYSTEM' },
  { label: '系统设置', value: 'SETTING' },
  { label: '私域客服', value: 'WECOM' },
  { label: '权限', value: 'PERMISSION' }
]

const actionOptions = [
  { label: '查看', value: 'VIEW' },
  { label: '创建', value: 'CREATE' },
  { label: '更新', value: 'UPDATE' },
  { label: '分配', value: 'ASSIGN' },
  { label: '回收', value: 'RECYCLE' },
  { label: '完结', value: 'FINISH' },
  { label: '门店退款', value: 'REFUND_STORE' },
  { label: '付款退款', value: 'REFUND_PAYMENT' },
  { label: '触发', value: 'TRIGGER' },
  { label: '调试', value: 'DEBUG' },
  { label: '检查', value: 'CHECK' }
]

const employeeFilter = reactive({
  departmentCode: '',
  status: ''
})
const managementEditorMode = ref('')
const policyEditorVisible = ref(false)
const roleWorkspace = ref('role')
const roleEditorTab = ref('basic')
const selectedRoleMenuPaths = ref([])
const editingRoleOriginalCode = ref('')

const departmentForm = reactive(createDepartmentForm())
const employeeForm = reactive(createEmployeeForm())
const positionForm = reactive(createPositionForm())
const roleForm = reactive(createRoleForm())
const policyForm = reactive(createPolicyForm())

const currentMode = computed(() => route.meta.orgMode || 'department')
const drawerSize = 'min(92vw, 640px)'
const wideDrawerSize = 'min(94vw, 720px)'
const departmentEditorVisible = computed({
  get: () => managementEditorMode.value === 'department',
  set: (visible) => {
    if (!visible) {
      closeManagementEditor('department')
    }
  }
})
const employeeEditorVisible = computed({
  get: () => managementEditorMode.value === 'employee',
  set: (visible) => {
    if (!visible) {
      closeManagementEditor('employee')
    }
  }
})
const positionEditorVisible = computed({
  get: () => managementEditorMode.value === 'position',
  set: (visible) => {
    if (!visible) {
      closeManagementEditor('position')
    }
  }
})
const roleEditorVisible = computed({
  get: () => managementEditorMode.value === 'role',
  set: (visible) => {
    if (!visible) {
      closeManagementEditor('role')
    }
  }
})
const policyDrawerVisible = computed({
  get: () => policyEditorVisible.value,
  set: (visible) => {
    if (!visible) {
      closePolicyEditor()
    }
  }
})
const isAdmin = computed(() => String(currentUser.value?.roleCode || '').trim().toUpperCase() === 'ADMIN')
const managedDepartmentCodes = computed(() => getManagedDepartmentCodes(currentUser.value?.roleCode))
const availableDepartments = computed(() => {
  if (managedDepartmentCodes.value === null) {
    return state.departments
  }
  return state.departments.filter((item) => managedDepartmentCodes.value.includes(item.departmentCode))
})
const availableRoles = computed(() => {
  if (isAdmin.value) {
    return state.roles
  }
  return state.roles.filter((item) => item.roleCode === 'ONLINE_CUSTOMER_SERVICE' || item.roleCode === 'CLUE_MANAGER')
})
const filteredEmployees = computed(() =>
  filterEmployeesByRole(state, currentUser.value?.roleCode).filter((item) => {
    if (employeeFilter.departmentCode && item.departmentCode !== employeeFilter.departmentCode) {
      return false
    }
    if (employeeFilter.status && item.status !== employeeFilter.status) {
      return false
    }
    return true
  })
)
const departmentPagination = useTablePagination(computed(() => state.departments))
const employeePagination = useTablePagination(() => filteredEmployees.value)
const positionPagination = useTablePagination(computed(() => state.positions))
const rolePagination = useTablePagination(computed(() => state.roles))
const policyPagination = useTablePagination(policies)
const filteredPositionsForEmployeeForm = computed(() => {
  const departmentCode = employeeForm.departmentCode
  return state.positions.filter((item) => item.departmentCode === departmentCode && item.isEnabled === 1)
})
const roleMenuGroups = computed(() => {
  const groups = new Map()
  state.menuConfigs.forEach((item) => {
    const groupName = String(item.menuGroup || '未分组').trim() || '未分组'
    if (!groups.has(groupName)) {
      groups.set(groupName, [])
    }
    groups.get(groupName).push(item)
  })
  return Array.from(groups.entries()).map(([name, items]) => ({
    name,
    items: items.slice().sort((left, right) => Number(left.id || 0) - Number(right.id || 0))
  }))
})
const currentRolePolicies = computed(() =>
  policies.value.filter((item) => String(item.roleCode || '').trim().toUpperCase() === String(roleForm.roleCode || '').trim().toUpperCase())
)
const metrics = computed(() => {
  if (currentMode.value === 'department') {
    return {
      primaryLabel: '部门数量',
      primaryValue: state.departments.length,
      primaryHint: '已建立的组织部门数量',
      secondaryLabel: '启用部门',
      secondaryValue: state.departments.filter((item) => item.isEnabled === 1).length,
      secondaryHint: '当前仍在正常运作的部门',
      tertiaryLabel: '部门负责人角色',
      tertiaryValue: new Set(state.departments.map((item) => item.managerRoleCode)).size,
      tertiaryHint: '部门管理角色已经与组织架构绑定'
    }
  }
  if (currentMode.value === 'employee') {
    const employees = filteredEmployees.value
    return {
      primaryLabel: '可管理员工',
      primaryValue: employees.length,
      primaryHint: isAdmin.value ? '管理员可查看全部员工' : '部门负责人仅查看本部门员工',
      secondaryLabel: '在职人数',
      secondaryValue: employees.filter((item) => item.status === 'ACTIVE').length,
      secondaryHint: '在职员工可以继续登录和处理数据',
      tertiaryLabel: '待转移数据',
      tertiaryValue: employees.reduce((sum, item) => sum + Number(item.ownedDataCount || 0), 0),
      tertiaryHint: '停用员工前需要先处理名下数据'
    }
  }
  if (currentMode.value === 'position') {
    return {
      primaryLabel: '岗位数量',
      primaryValue: state.positions.length,
      primaryHint: '当前系统内可分配岗位数',
      secondaryLabel: '启用岗位',
      secondaryValue: state.positions.filter((item) => item.isEnabled === 1).length,
      secondaryHint: '可继续分配给在职员工的岗位',
      tertiaryLabel: '岗位承载员工',
      tertiaryValue: state.employees.filter((item) => item.status === 'ACTIVE').length,
      tertiaryHint: '岗位删除时需迁移这些员工'
    }
  }
  return {
    primaryLabel: '角色数量',
    primaryValue: state.roles.length,
    primaryHint: '系统内当前可配置角色数',
    secondaryLabel: '启用角色',
    secondaryValue: state.roles.filter((item) => item.isEnabled === 1).length,
    secondaryHint: '启用角色可继续分配给员工',
    tertiaryLabel: '授权策略',
    tertiaryValue: policies.value.length,
    tertiaryHint: '原权限中心策略已在此页管理'
  }
})

function createDepartmentForm() {
  return {
    id: null,
    departmentCode: '',
    departmentName: '',
    parentCode: '',
    managerRoleCode: 'CLUE_MANAGER',
    dataScopeRule: '',
    remark: ''
  }
}

function createEmployeeForm() {
  return {
    id: null,
    accountName: '',
    userName: '',
    departmentCode: 'CLUE',
    positionCode: '',
    roleCode: 'ONLINE_CUSTOMER_SERVICE',
    ownedDataCount: 0
  }
}

function createPositionForm() {
  return {
    id: null,
    positionCode: '',
    positionName: '',
    departmentCode: 'CLUE',
    remark: ''
  }
}

function createRoleForm() {
  return {
    id: null,
    roleCode: '',
    roleName: '',
    dataScope: 'TEAM',
    roleType: 'BUSINESS',
    moduleCodes: [],
    menuRoutes: [],
    permissionCodes: [],
    isEnabled: 1,
    sortOrder: 0,
    remark: ''
  }
}

function createPolicyForm() {
  return {
    moduleCode: 'SYSTEM',
    actionCode: 'VIEW',
    roleCode: 'ADMIN',
    dataScope: 'ALL',
    conditionRule: ''
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
  rolePagination.reset()
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
    sortOrder: item.sortOrder ?? Number(item.id || 0),
    sort: item.sortOrder ?? Number(item.id || 0)
  }
}

async function loadSystemAccessConfig() {
  try {
    applyAccessSnapshot(await fetchSystemAccessSnapshot())
  } catch {
    // 请求层已经提示错误；保留本地缓存，避免系统管理页空白。
  }
}

function resetDepartmentForm() {
  Object.assign(departmentForm, createDepartmentForm())
}

function resetEmployeeForm() {
  Object.assign(employeeForm, createEmployeeForm())
}

function resetPositionForm() {
  Object.assign(positionForm, createPositionForm())
}

function resetRoleForm() {
  Object.assign(roleForm, createRoleForm())
  roleEditorTab.value = 'basic'
  selectedRoleMenuPaths.value = []
  editingRoleOriginalCode.value = ''
}

function resetPolicyForm() {
  Object.assign(policyForm, createPolicyForm())
}

function openManagementEditor(mode) {
  managementEditorMode.value = mode
  if (mode === 'department') {
    resetDepartmentForm()
  }
  if (mode === 'employee') {
    resetEmployeeForm()
  }
  if (mode === 'position') {
    resetPositionForm()
  }
  if (mode === 'role') {
    resetRoleForm()
    roleWorkspace.value = 'role'
  }
}

function closeManagementEditor(mode) {
  if (managementEditorMode.value !== mode) {
    return
  }
  managementEditorMode.value = ''
}

function openPolicyEditor() {
  resetPolicyForm()
  roleWorkspace.value = 'policy'
  policyEditorVisible.value = true
}

function openPolicyEditorForCurrentRole() {
  const roleCode = String(roleForm.roleCode || '').trim()
  if (!roleCode) {
    ElMessage.warning('请先填写角色编码')
    roleEditorTab.value = 'basic'
    return
  }
  Object.assign(policyForm, {
    ...createPolicyForm(),
    roleCode,
    dataScope: roleForm.dataScope || 'TEAM'
  })
  managementEditorMode.value = ''
  roleWorkspace.value = 'policy'
  policyEditorVisible.value = true
}

function closePolicyEditor() {
  policyEditorVisible.value = false
}

function pickDepartment(row) {
  Object.assign(departmentForm, { ...row })
  managementEditorMode.value = 'department'
}

function pickEmployee(row) {
  Object.assign(employeeForm, { ...row })
  managementEditorMode.value = 'employee'
}

function pickPosition(row) {
  Object.assign(positionForm, { ...row })
  managementEditorMode.value = 'position'
}

function pickRole(row) {
  Object.assign(roleForm, {
    ...row,
    moduleCodes: [...(row.moduleCodes || [])],
    menuRoutes: [...(row.menuRoutes || [])],
    permissionCodes: [...(row.permissionCodes || [])]
  })
  editingRoleOriginalCode.value = row.roleCode
  selectedRoleMenuPaths.value = row.menuRoutes?.length
    ? [...row.menuRoutes]
    : state.menuConfigs
        .filter((item) => (item.roleCodes || []).includes(row.roleCode))
        .map((item) => item.routePath)
  roleEditorTab.value = 'basic'
  roleWorkspace.value = 'role'
  managementEditorMode.value = 'role'
}

function selectAllRoleMenus() {
  selectedRoleMenuPaths.value = state.menuConfigs.map((item) => item.routePath)
}

function clearRoleMenus() {
  selectedRoleMenuPaths.value = []
}

function departmentRowClassName({ row }) {
  return managementEditorMode.value === 'department' && departmentForm.id === row.id ? 'is-editing-row' : ''
}

function employeeRowClassName({ row }) {
  return managementEditorMode.value === 'employee' && employeeForm.id === row.id ? 'is-editing-row' : ''
}

function positionRowClassName({ row }) {
  return managementEditorMode.value === 'position' && positionForm.id === row.id ? 'is-editing-row' : ''
}

function roleRowClassName({ row }) {
  return managementEditorMode.value === 'role' && roleForm.id === row.id ? 'is-editing-row' : ''
}

function saveDepartment() {
  if (!departmentForm.departmentCode || !departmentForm.departmentName) {
    ElMessage.warning('请先填写部门编码和部门名称')
    return
  }
  const nextDepartments = [...state.departments]
  if (departmentForm.id) {
    const index = nextDepartments.findIndex((item) => item.id === departmentForm.id)
    nextDepartments[index] = { ...nextDepartments[index], ...departmentForm }
  } else {
    nextDepartments.push({
      ...departmentForm,
      id: nextSystemId(nextDepartments),
      isEnabled: 1
    })
  }
  replaceState({ ...state, departments: nextDepartments })
  ElMessage.success('部门信息已保存')
  resetDepartmentForm()
  closeManagementEditor('department')
}

function toggleDepartment(row) {
  const nextDepartments = state.departments.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, departments: nextDepartments })
  ElMessage.success('部门状态已更新')
}

function saveEmployee() {
  if (!employeeForm.accountName || !employeeForm.userName || !employeeForm.departmentCode || !employeeForm.roleCode) {
    ElMessage.warning('请先完整填写员工信息')
    return
  }
  const nextEmployees = [...state.employees]
  if (employeeForm.id) {
    const index = nextEmployees.findIndex((item) => item.id === employeeForm.id)
    nextEmployees[index] = {
      ...nextEmployees[index],
      ...employeeForm
    }
  } else {
    nextEmployees.push({
      ...employeeForm,
      id: nextSystemId(nextEmployees),
      status: 'ACTIVE',
      canLogin: 1
    })
  }
  replaceState({ ...state, employees: nextEmployees })
  ElMessage.success('员工信息已保存')
  resetEmployeeForm()
  closeManagementEditor('employee')
}

function moveEmployee(row) {
  const candidate = state.positions.find((item) => item.departmentCode === row.departmentCode && item.positionCode !== row.positionCode)
  if (!candidate) {
    ElMessage.warning('当前部门没有其它可调岗岗位')
    return
  }
  const nextEmployees = state.employees.map((item) =>
    item.id === row.id ? { ...item, positionCode: candidate.positionCode } : item
  )
  replaceState({ ...state, employees: nextEmployees })
  ElMessage.success(`已将 ${row.userName} 调整到 ${candidate.positionName}`)
}

function toggleEmployeeStatus(row) {
  const nextEmployees = [...state.employees]
  const currentIndex = nextEmployees.findIndex((item) => item.id === row.id)
  const currentEmployee = { ...nextEmployees[currentIndex] }
  if (currentEmployee.status === 'ACTIVE') {
    if (Number(currentEmployee.ownedDataCount || 0) > 0) {
      const receiver = nextEmployees.find(
        (item) => item.id !== currentEmployee.id && item.departmentCode === currentEmployee.departmentCode && item.status === 'ACTIVE'
      )
      if (!receiver) {
        ElMessage.warning('当前部门没有可接收数据的在职员工，无法停用')
        return
      }
      receiver.ownedDataCount = Number(receiver.ownedDataCount || 0) + Number(currentEmployee.ownedDataCount || 0)
      currentEmployee.ownedDataCount = 0
      ElMessage.success(`已将名下数据转交给 ${receiver.userName}，并停用该员工`)
    } else {
      ElMessage.success('员工已停用，后续不能登录系统')
    }
    currentEmployee.status = 'DISABLED'
    currentEmployee.canLogin = 0
  } else {
    currentEmployee.status = 'ACTIVE'
    currentEmployee.canLogin = 1
    ElMessage.success('员工已重新启用')
  }
  nextEmployees[currentIndex] = currentEmployee
  replaceState({ ...state, employees: nextEmployees })
}

function savePosition() {
  if (!positionForm.positionCode || !positionForm.positionName || !positionForm.departmentCode) {
    ElMessage.warning('请先完整填写岗位信息')
    return
  }
  const nextPositions = [...state.positions]
  if (positionForm.id) {
    const index = nextPositions.findIndex((item) => item.id === positionForm.id)
    nextPositions[index] = { ...nextPositions[index], ...positionForm }
  } else {
    nextPositions.push({
      ...positionForm,
      id: nextSystemId(nextPositions),
      isEnabled: 1
    })
  }
  replaceState({ ...state, positions: nextPositions })
  ElMessage.success('岗位信息已保存')
  resetPositionForm()
  closeManagementEditor('position')
}

function employeesInPosition(positionCode) {
  return state.employees.filter((item) => item.positionCode === positionCode && item.status === 'ACTIVE')
}

function togglePosition(row) {
  const nextPositions = state.positions.map((item) =>
    item.id === row.id ? { ...item, isEnabled: item.isEnabled === 1 ? 0 : 1 } : item
  )
  replaceState({ ...state, positions: nextPositions })
  ElMessage.success('岗位状态已更新')
}

function removePosition(row) {
  const attachedEmployees = employeesInPosition(row.positionCode)
  const fallbackPosition = state.positions.find(
    (item) => item.positionCode !== row.positionCode && item.departmentCode === row.departmentCode && item.isEnabled === 1
  )
  if (attachedEmployees.length && !fallbackPosition) {
    ElMessage.warning('删除岗位前，需要先准备同部门其它启用岗位承接员工')
    return
  }

  const nextEmployees = state.employees.map((item) => {
    if (item.positionCode !== row.positionCode) {
      return item
    }
    return {
      ...item,
      positionCode: fallbackPosition ? fallbackPosition.positionCode : ''
    }
  })
  const nextPositions = state.positions.filter((item) => item.id !== row.id)
  replaceState({ ...state, positions: nextPositions, employees: nextEmployees })
  ElMessage.success(attachedEmployees.length ? `岗位已删除，员工已迁移到 ${fallbackPosition.positionName}` : '岗位已删除')
}

async function saveRole() {
  if (!roleForm.roleCode || !roleForm.roleName) {
    ElMessage.warning('请先填写角色编码和角色名称')
    return
  }
  const selectedMenuPathSet = new Set(selectedRoleMenuPaths.value)
  const selectedMenuModules = state.menuConfigs
    .filter((item) => selectedMenuPathSet.has(item.routePath))
    .map((item) => item.moduleCode)
    .filter(Boolean)
  const nextRoleForm = {
    ...roleForm,
    moduleCodes: Array.from(new Set([...(roleForm.moduleCodes || []), ...selectedMenuModules]))
  }
  try {
    await saveSystemAccessRole({
      id: nextRoleForm.id,
      roleCode: String(nextRoleForm.roleCode || '').trim(),
      roleName: String(nextRoleForm.roleName || '').trim(),
      dataScope: nextRoleForm.dataScope || 'TEAM',
      roleType: nextRoleForm.roleType || 'BUSINESS',
      isEnabled: nextRoleForm.isEnabled ?? 1,
      sortOrder: nextRoleForm.sortOrder ?? nextRoleForm.sort,
      menuRoutes: [...selectedMenuPathSet],
      permissionCodes: nextRoleForm.permissionCodes || []
    })
    await loadSystemAccessConfig()
    ElMessage.success('角色权限已保存，重新登录后按后端授权生效')
    resetRoleForm()
    closeManagementEditor('role')
  } catch {
    // HTTP 层统一处理提示。
  }
}

function employeesInRole(roleCode) {
  return state.employees.filter((item) => item.roleCode === roleCode && item.status === 'ACTIVE')
}

async function toggleRole(row) {
  if (row.isEnabled === 1 && employeesInRole(row.roleCode).length) {
    ElMessage.warning('停用角色前，需要先调整绑定该角色的在职员工')
    return
  }
  try {
    await saveSystemAccessRole({
      id: row.id,
      roleCode: row.roleCode,
      roleName: row.roleName,
      dataScope: row.dataScope || 'TEAM',
      roleType: row.roleType || 'BUSINESS',
      isEnabled: row.isEnabled === 1 ? 0 : 1,
      sortOrder: row.sortOrder ?? row.sort,
      menuRoutes: row.menuRoutes || state.menuConfigs
        .filter((item) => (item.roleCodes || []).includes(row.roleCode))
        .map((item) => item.routePath),
      permissionCodes: row.permissionCodes || []
    })
    await loadSystemAccessConfig()
    ElMessage.success('角色状态已更新，重新登录后生效')
  } catch {
    // HTTP 层统一处理提示。
  }
}

async function removeRole(row) {
  if (employeesInRole(row.roleCode).length) {
    ElMessage.warning('当前角色仍绑定在职员工，需先调整人员角色后再停用')
    return
  }
  try {
    await saveSystemAccessRole({
      id: row.id,
      roleCode: row.roleCode,
      roleName: row.roleName,
      dataScope: row.dataScope || 'TEAM',
      roleType: row.roleType || 'BUSINESS',
      isEnabled: 0,
      sortOrder: row.sortOrder ?? row.sort,
      menuRoutes: row.menuRoutes || state.menuConfigs
        .filter((item) => (item.roleCodes || []).includes(row.roleCode))
        .map((item) => item.routePath),
      permissionCodes: row.permissionCodes || []
    })
    await loadSystemAccessConfig()
    ElMessage.success('角色已停用并保留历史授权')
  } catch {
    // HTTP 层统一处理提示。
  }
}

async function loadPolicies() {
  try {
    policies.value = await fetchPermissionPolicies()
  } catch {
    policies.value = []
  }
}

async function savePolicy() {
  try {
    await savePermissionPolicy({
      moduleCode: policyForm.moduleCode,
      actionCode: policyForm.actionCode,
      roleCode: policyForm.roleCode,
      dataScope: policyForm.dataScope,
      conditionRule: policyForm.conditionRule || undefined,
      isEnabled: 1
    })
    ElMessage.success('授权策略已保存')
    resetPolicyForm()
    closePolicyEditor()
    await loadPolicies()
  } catch {
    // HTTP 层统一处理提示
  }
}

watch(
  () => currentMode.value,
  async (mode) => {
    managementEditorMode.value = ''
    policyEditorVisible.value = false
    if (mode === 'role') {
      roleWorkspace.value = 'role'
      await Promise.all([loadSystemAccessConfig(), loadPolicies()])
    }
  },
  { immediate: true }
)

watch(
  () => employeeForm.departmentCode,
  (departmentCode) => {
    const availablePositions = state.positions.filter(
      (item) => item.departmentCode === departmentCode && item.isEnabled === 1
    )
    if (!availablePositions.some((item) => item.positionCode === employeeForm.positionCode)) {
      employeeForm.positionCode = availablePositions[0]?.positionCode || ''
    }
  },
  { immediate: true }
)
</script>
