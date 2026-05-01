package com.seedcrm.crm.systemflow.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemflow.dto.SystemFlowDtos;
import com.seedcrm.crm.systemflow.service.SystemFlowService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemFlowServiceImpl implements SystemFlowService {

    private static final String DEFAULT_FLOW_CODE = "ORDER_MAIN_FLOW";
    private static final Set<String> SYSTEM_FLOW_V1_SUPPORTED_CODES = Set.of(DEFAULT_FLOW_CODE);
    private static final List<String> REQUIRED_ORDER_MAIN_CHAIN = List.of("CLUE", "CUSTOMER", "ORDER", "PLANORDER");
    private static final Set<String> ALLOWED_ORDER_MAIN_DOMAINS = Set.of("CLUE", "CUSTOMER", "ORDER", "PLANORDER");
    private static final Map<String, Set<String>> CORE_DOMAIN_STATES = Map.of(
            "ORDER", Set.of("paid", "used"),
            "PLANORDER", Set.of("arrived", "service_form_confirmed", "servicing", "finished"));
    private static final Set<String> SAFE_TRIGGER_TARGET_CODES = Set.of(
            "DOUYIN_CLUE_INCREMENTAL",
            "DOUYIN_VOUCHER_VERIFY",
            "ORDER_SETTLEMENT_METADATA",
            "SALARY_SETTLEMENT_METADATA");
    private static final Set<String> SAFE_TRIGGER_EXECUTION_MODES = Set.of("MANUAL", "METADATA_ONLY");

    private final JdbcTemplate jdbcTemplate;

    public SystemFlowServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SystemFlowDtos.DefinitionResponse> listDefinitions() {
        return jdbcTemplate.query("""
                SELECT d.id,
                       d.flow_code,
                       d.flow_name,
                       d.module_code,
                       d.business_object,
                       d.description,
                       d.enabled,
                       d.current_version_id,
                       d.updated_at,
                       v.version_no,
                       v.status,
                       COALESCE(n.node_count, 0) AS node_count,
                       COALESCE(t.trigger_count, 0) AS trigger_count
                FROM system_flow_definition d
                LEFT JOIN system_flow_version v ON v.id = d.current_version_id
                LEFT JOIN (
                    SELECT version_id, COUNT(1) AS node_count
                    FROM system_flow_node
                    GROUP BY version_id
                ) n ON n.version_id = d.current_version_id
                LEFT JOIN (
                    SELECT version_id, COUNT(1) AS trigger_count
                    FROM system_flow_trigger
                    GROUP BY version_id
                ) t ON t.version_id = d.current_version_id
                ORDER BY d.id ASC
                """, (rs, rowNum) -> {
                SystemFlowDtos.DefinitionResponse item = new SystemFlowDtos.DefinitionResponse();
                item.setId(rs.getLong("id"));
                item.setFlowCode(rs.getString("flow_code"));
                item.setFlowName(rs.getString("flow_name"));
                item.setModuleCode(rs.getString("module_code"));
                item.setBusinessObject(rs.getString("business_object"));
                item.setDescription(rs.getString("description"));
                item.setEnabled(rs.getInt("enabled"));
                long currentVersionId = rs.getLong("current_version_id");
                item.setCurrentVersionId(rs.wasNull() ? null : currentVersionId);
                int versionNo = rs.getInt("version_no");
                item.setCurrentVersionNo(rs.wasNull() ? null : versionNo);
                item.setCurrentVersionStatus(rs.getString("status"));
                item.setNodeCount(rs.getInt("node_count"));
                item.setTriggerCount(rs.getInt("trigger_count"));
                item.setUpdatedAt(rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
                return item;
            });
    }

    @Override
    public List<SystemFlowDtos.VersionResponse> listVersions(String flowCode) {
        String normalizedFlowCode = normalizeFlowCode(flowCode);
        SystemFlowDtos.DefinitionResponse definition = getDefinition(normalizedFlowCode);
        return jdbcTemplate.query("""
                SELECT id, definition_id, version_no, status, change_summary, published_at, created_at, updated_at
                FROM system_flow_version
                WHERE definition_id = ?
                ORDER BY version_no DESC, id DESC
                """, (rs, rowNum) -> mapVersion(rs), definition.getId());
    }

    @Override
    public SystemFlowDtos.DetailResponse detail(String flowCode, Long versionId) {
        String normalizedFlowCode = normalizeFlowCode(flowCode);
        SystemFlowDtos.DefinitionResponse definition = getDefinition(normalizedFlowCode);
        Long targetVersionId = versionId == null ? definition.getCurrentVersionId() : versionId;
        if (targetVersionId == null) {
            throw new BusinessException("flow version is missing");
        }
        SystemFlowDtos.VersionResponse version = getVersion(targetVersionId);
        if (!Objects.equals(version.getDefinitionId(), definition.getId())) {
            throw new BusinessException("version does not belong to flow");
        }
        SystemFlowDtos.DetailResponse response = new SystemFlowDtos.DetailResponse();
        response.setDefinition(definition);
        response.setVersion(version);
        response.setNodes(listNodes(targetVersionId));
        response.setTransitions(listTransitions(targetVersionId));
        response.setTriggers(listTriggers(targetVersionId));
        return response;
    }

    @Override
    @Transactional
    public SystemFlowDtos.DetailResponse saveDraft(SystemFlowDtos.SaveDraftRequest request, PermissionRequestContext context) {
        validateDraft(request);
        String flowCode = normalizeFlowCode(request.getFlowCode());
        Long definitionId = upsertDefinition(request, flowCode);
        int nextVersionNo = nextVersionNo(definitionId);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO system_flow_version(definition_id, version_no, status, change_summary, created_at, updated_at)
                VALUES (?, ?, 'DRAFT', ?, ?, ?)
                """, definitionId, nextVersionNo, request.getChangeSummary(), now, now);
        Long versionId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM system_flow_version
                WHERE definition_id = ? AND version_no = ?
                """, Long.class, definitionId, nextVersionNo);
        insertNodes(versionId, request.getNodes());
        insertTransitions(versionId, request.getTransitions());
        insertTriggers(versionId, request.getTriggers());
        audit(flowCode, nextVersionNo, "SAVE_DRAFT", context, "保存流程草稿");
        return detail(flowCode, versionId);
    }

    @Override
    @Transactional
    public SystemFlowDtos.DetailResponse publish(SystemFlowDtos.PublishRequest request, PermissionRequestContext context) {
        String flowCode = normalizeFlowCode(request == null ? null : request.getFlowCode());
        SystemFlowDtos.DefinitionResponse definition = getDefinition(flowCode);
        Long versionId = request == null ? null : request.getVersionId();
        if (versionId == null) {
            versionId = latestVersionId(definition.getId());
        }
        SystemFlowDtos.VersionResponse version = getVersion(versionId);
        if (!Objects.equals(version.getDefinitionId(), definition.getId())) {
            throw new BusinessException("version does not belong to flow");
        }
        if (!"DRAFT".equalsIgnoreCase(version.getStatus())) {
            throw new BusinessException("only draft system flow version can be published");
        }
        validateVersionPublishable(versionId);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE system_flow_version
                SET status = 'ARCHIVED', updated_at = ?
                WHERE definition_id = ? AND status = 'PUBLISHED'
                """, now, definition.getId());
        jdbcTemplate.update("""
                UPDATE system_flow_version
                SET status = 'PUBLISHED', change_summary = ?, published_at = ?, updated_at = ?
                WHERE id = ?
                """, StringUtils.hasText(request == null ? null : request.getSummary()) ? request.getSummary().trim() : version.getChangeSummary(),
                now, now, versionId);
        jdbcTemplate.update("""
                UPDATE system_flow_definition
                SET current_version_id = ?, enabled = 1, updated_at = ?
                WHERE id = ?
                """, versionId, now, definition.getId());
        audit(flowCode, version.getVersionNo(), "PUBLISH", context, "发布流程版本");
        return detail(flowCode, versionId);
    }

    @Override
    @Transactional
    public SystemFlowDtos.DefinitionResponse disable(SystemFlowDtos.DisableRequest request, PermissionRequestContext context) {
        String flowCode = normalizeFlowCode(request == null ? null : request.getFlowCode());
        getDefinition(flowCode);
        jdbcTemplate.update("""
                UPDATE system_flow_definition
                SET enabled = 0, updated_at = ?
                WHERE flow_code = ?
                """, LocalDateTime.now(), flowCode);
        audit(flowCode, null, "DISABLE", context,
                StringUtils.hasText(request == null ? null : request.getReason()) ? request.getReason().trim() : "停用流程");
        return getDefinition(flowCode);
    }

    @Override
    public SystemFlowDtos.SimulateResponse simulate(SystemFlowDtos.SimulateRequest request) {
        String flowCode = normalizeFlowCode(request == null ? null : request.getFlowCode());
        SystemFlowDtos.DetailResponse detail = detail(flowCode, null);
        String currentNodeCode = normalizeCode(request == null ? null : request.getCurrentNodeCode());
        if (!StringUtils.hasText(currentNodeCode)) {
            currentNodeCode = detail.getNodes().isEmpty() ? null : detail.getNodes().get(0).getNodeCode();
        }
        String actionCode = normalizeCode(request == null ? null : request.getActionCode());
        final String sourceNodeCode = currentNodeCode;
        final String requestedActionCode = actionCode;
        SystemFlowDtos.TransitionResponse matchedTransition = detail.getTransitions().stream()
                .filter(item -> item.getFromNodeCode().equalsIgnoreCase(sourceNodeCode))
                .filter(item -> !StringUtils.hasText(requestedActionCode) || item.getActionCode().equalsIgnoreCase(requestedActionCode))
                .findFirst()
                .orElse(null);
        SystemFlowDtos.NodeResponse sourceNode = findNode(detail.getNodes(), currentNodeCode);

        SystemFlowDtos.SimulateResponse response = new SystemFlowDtos.SimulateResponse();
        response.setFlowCode(flowCode);
        response.setVersionNo(detail.getVersion().getVersionNo());
        response.setCurrentNodeCode(currentNodeCode);
        response.setActionCode(actionCode);
        response.setAllowed(matchedTransition != null);
        if (matchedTransition == null) {
            response.setMessage("未命中可流转规则；不会改动真实订单");
            response.setPath(buildPath(detail.getNodes(), currentNodeCode, null));
            return response;
        }
        if (!isRoleAllowedForSimulation(request == null ? null : request.getRoleCode(), sourceNode)) {
            response.setAllowed(false);
            response.setMessage("角色不匹配；只读模拟拒绝，未改动真实订单、服务单、薪酬或三方接口");
            response.setPath(buildPath(detail.getNodes(), currentNodeCode, null));
            return response;
        }
        response.setNextNodeCode(matchedTransition.getToNodeCode());
        response.setMessage("只读模拟通过；仅返回触发器元数据，不执行订单、薪酬、调度或三方接口");
        response.setPath(buildPath(detail.getNodes(), currentNodeCode, matchedTransition.getToNodeCode()));
        response.setMatchedTriggers(detail.getTriggers().stream()
                .filter(item -> item.getEnabled() == null || item.getEnabled() == 1)
                .filter(item -> item.getNodeCode().equalsIgnoreCase(matchedTransition.getToNodeCode()))
                .toList());
        return response;
    }

    @Override
    public SystemFlowDtos.DiffPreviewResponse previewDiff(SystemFlowDtos.SaveDraftRequest request) {
        String flowCode = normalizeFlowCode(request == null ? null : request.getFlowCode());
        SystemFlowDtos.DetailResponse base = detail(flowCode, null);
        SystemFlowDtos.DiffPreviewResponse response = new SystemFlowDtos.DiffPreviewResponse();
        response.setFlowCode(flowCode);
        response.setBaseVersionNo(base.getVersion() == null ? null : base.getVersion().getVersionNo());
        try {
            validateDraft(request);
            response.setValid(true);
            response.setValidationMessage("VALID");
        } catch (BusinessException exception) {
            response.setValid(false);
            response.setValidationMessage(exception.getMessage());
        }
        if (request == null) {
            return response;
        }
        compareDefinitionDiff(response.getItems(), base.getDefinition(), request);
        compareNodes(response.getItems(), base.getNodes(), request.getNodes());
        compareTransitions(response.getItems(), base.getTransitions(), request.getTransitions());
        compareTriggers(response.getItems(), base.getTriggers(), request.getTriggers());
        return response;
    }

    @Override
    public SystemFlowDtos.ValidationReportResponse validationReport(String flowCode, Long versionId) {
        SystemFlowDtos.DetailResponse detail = detail(flowCode, versionId);
        SystemFlowDtos.ValidationReportResponse response = new SystemFlowDtos.ValidationReportResponse();
        response.setFlowCode(detail.getDefinition() == null ? normalizeFlowCode(flowCode) : detail.getDefinition().getFlowCode());
        response.setVersionNo(detail.getVersion() == null ? null : detail.getVersion().getVersionNo());
        response.setVersionStatus(detail.getVersion() == null ? null : detail.getVersion().getStatus());
        response.setCheckedAt(LocalDateTime.now());

        List<SystemFlowDtos.ValidationItemResponse> items = response.getItems();
        Set<String> domains = collectDomains(detail.getNodes());
        for (String requiredDomain : REQUIRED_ORDER_MAIN_CHAIN) {
            boolean present = domains.contains(requiredDomain);
            addValidationItem(items, requiredDomain, "REQUIRED_DOMAIN", present,
                    present ? requiredDomain + " 节点已存在" : "缺少 " + requiredDomain + " 节点",
                    "ORDER_MAIN_FLOW 必须保持 Clue -> Customer -> Order -> PlanOrder 主链路");
        }

        Set<String> invalidDomains = new LinkedHashSet<>(domains);
        invalidDomains.removeAll(ALLOWED_ORDER_MAIN_DOMAINS);
        addValidationItem(items, "FLOW", "ALLOWED_DOMAINS", invalidDomains.isEmpty(),
                invalidDomains.isEmpty() ? "节点域均在主链范围内" : "存在不允许的节点域: " + String.join(",", invalidDomains),
                "Scheduler、三方接口、薪酬等能力只能放在触发器元数据或下游配置中");

        boolean orderStatesValid = collectStates(detail.getNodes(), "ORDER").containsAll(CORE_DOMAIN_STATES.get("ORDER"));
        addValidationItem(items, "ORDER", "ORDER_CORE_STATES", orderStatesValid,
                orderStatesValid ? "Order 已包含 paid、used 状态" : "Order 缺少 paid 或 used 状态",
                "不要新增核心订单状态；预约、核销属于 paid -> used 之间的受控动作");
        boolean planOrderStatesValid = collectStates(detail.getNodes(), "PLANORDER").containsAll(CORE_DOMAIN_STATES.get("PLANORDER"));
        addValidationItem(items, "PLANORDER", "PLANORDER_CORE_STATES", planOrderStatesValid,
                planOrderStatesValid ? "PlanOrder 已包含 arrived、service_form_confirmed、servicing、finished 状态" : "PlanOrder 缺少 arrived、service_form_confirmed、servicing 或 finished 状态",
                "PlanOrder 必须 1:1 绑定 Order，且不能独立存在");

        addGuardedValidation(items, "FLOW", "DOMAIN_ORDER",
                () -> validateRequiredDomainOrder(detail.getNodes()),
                "主链顺序满足 Clue -> Customer -> Order -> PlanOrder",
                "请按 Clue -> Customer -> Order -> PlanOrder 排列节点");
        addGuardedValidation(items, "TRANSITION", "TRANSITION_REFERENCES",
                () -> validateTransitionReferences(detail.getNodes(), detail.getTransitions()),
                "流转规则引用的节点均存在",
                "请修正 from/to 节点编码");
        addGuardedValidation(items, "TRANSITION", "TRANSITION_PATH",
                () -> validateTransitionDomainPath(detail.getNodes(), detail.getTransitions()),
                "流转路径未跳过 Customer 或 PlanOrder 约束",
                "流转路径不能绕过 Customer 创建规则，不能让 PlanOrder 独立存在");
        addGuardedValidation(items, "TRANSITION", "UNIQUE_ACTIONS",
                () -> validateUniqueActions(detail.getTransitions()),
                "流转动作编码未重复",
                "每个动作编码需要唯一，便于审计和配置映射");
        addGuardedValidation(items, "TRIGGER", "TRIGGER_REFERENCES",
                () -> validateTriggerTargets(detail.getNodes(), detail.getTriggers()),
                "触发器引用安全且只保留元数据",
                "触发器目标只能填写元数据编码，真实调用由调度中心或三方接口配置控制");

        response.setValid(items.stream().allMatch(SystemFlowDtos.ValidationItemResponse::isPassed));
        return response;
    }

    @Override
    public SystemFlowDtos.TriggerLinkageReportResponse triggerLinkageReport(String flowCode, Long versionId) {
        SystemFlowDtos.DetailResponse detail = detail(flowCode, versionId);
        SystemFlowDtos.TriggerLinkageReportResponse response = new SystemFlowDtos.TriggerLinkageReportResponse();
        response.setFlowCode(detail.getDefinition() == null ? normalizeFlowCode(flowCode) : detail.getDefinition().getFlowCode());
        response.setVersionNo(detail.getVersion() == null ? null : detail.getVersion().getVersionNo());
        response.setVersionStatus(detail.getVersion() == null ? null : detail.getVersion().getStatus());
        response.setCheckedAt(LocalDateTime.now());

        for (SystemFlowDtos.TriggerResponse trigger : safeList(detail.getTriggers())) {
            response.getItems().add(buildTriggerLinkageItem(trigger));
        }
        response.setHealthy(response.getItems().stream()
                .noneMatch(item -> "BLOCKER".equalsIgnoreCase(item.getStatus())));
        return response;
    }

    @Override
    public List<SystemFlowDtos.AuditLogResponse> listAuditLogs(String flowCode) {
        String normalizedFlowCode = normalizeFlowCode(flowCode);
        return jdbcTemplate.query("""
                SELECT id, flow_code, version_no, action_type, actor_role_code, actor_user_id, summary, created_at
                FROM system_flow_audit_log
                WHERE flow_code = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 100
                """, (rs, rowNum) -> {
                SystemFlowDtos.AuditLogResponse item = new SystemFlowDtos.AuditLogResponse();
                item.setId(rs.getLong("id"));
                item.setFlowCode(rs.getString("flow_code"));
                int versionNo = rs.getInt("version_no");
                item.setVersionNo(rs.wasNull() ? null : versionNo);
                item.setActionType(rs.getString("action_type"));
                item.setActorRoleCode(rs.getString("actor_role_code"));
                long actorUserId = rs.getLong("actor_user_id");
                item.setActorUserId(rs.wasNull() ? null : actorUserId);
                item.setSummary(rs.getString("summary"));
                item.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
                return item;
            }, normalizedFlowCode);
    }

    @Override
    public SystemFlowDtos.RuntimeOverviewResponse runtimeOverview(String flowCode) {
        String normalizedFlowCode = normalizeFlowCode(flowCode);
        SystemFlowDtos.RuntimeOverviewResponse response = new SystemFlowDtos.RuntimeOverviewResponse();
        response.setFlowCode(normalizedFlowCode);
        response.setRunningCount(queryCount("""
                SELECT COUNT(1)
                FROM system_flow_instance
                WHERE flow_code = ? AND status = 'RUNNING'
                """, normalizedFlowCode));
        response.setOpenTaskCount(queryCount("""
                SELECT COUNT(1)
                FROM system_flow_task
                WHERE flow_code = ? AND status = 'OPEN'
                """, normalizedFlowCode));
        response.setRecentInstances(listRuntimeInstances(normalizedFlowCode, 10));
        response.setOpenTasks(listOpenTasks(normalizedFlowCode));
        response.setRecentEvents(jdbcTemplate.query("""
                SELECT id, instance_id, flow_code, version_no, action_code, from_node_code, to_node_code,
                       actor_role_code, actor_user_id, summary, event_time
                FROM system_flow_event_log
                WHERE flow_code = ?
                ORDER BY event_time DESC, id DESC
                LIMIT 20
                """, (rs, rowNum) -> mapEvent(rs), normalizedFlowCode));
        return response;
    }

    @Override
    @Transactional
    public SystemFlowDtos.InstanceResponse startInstance(SystemFlowDtos.StartInstanceRequest request, PermissionRequestContext context) {
        String flowCode = normalizeFlowCode(request == null ? null : request.getFlowCode());
        SystemFlowDtos.DetailResponse detail = detail(flowCode, null);
        String businessObject = normalizeCode(request == null ? null : request.getBusinessObject());
        Long businessId = request == null ? null : request.getBusinessId();
        if (!StringUtils.hasText(businessObject) || businessId == null || businessId <= 0) {
            throw new BusinessException("businessObject and businessId are required");
        }
        String startNodeCode = normalizeCode(request == null ? null : request.getStartNodeCode());
        if (!StringUtils.hasText(startNodeCode)) {
            startNodeCode = detail.getNodes().isEmpty() ? null : detail.getNodes().get(0).getNodeCode();
        }
        SystemFlowDtos.NodeResponse startNode = findNode(detail.getNodes(), startNodeCode);
        if (startNode == null) {
            throw new BusinessException("start node not found");
        }
        Long existingId = queryLongOrNull("""
                SELECT id
                FROM system_flow_instance
                WHERE flow_code = ? AND business_object = ? AND business_id = ?
                LIMIT 1
                """, flowCode, businessObject, businessId);
        if (existingId != null) {
            return getRuntimeInstance(existingId);
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO system_flow_instance(
                    flow_code, version_id, version_no, business_object, business_id, current_node_code, status,
                    title, created_by_role_code, created_by_user_id, create_time, update_time
                )
                VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?)
                """, flowCode, detail.getVersion().getId(), detail.getVersion().getVersionNo(), businessObject, businessId,
                startNode.getNodeCode(), trimToNull(request == null ? null : request.getTitle()),
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                now, now);
        Long instanceId = queryLongOrNull("""
                SELECT id
                FROM system_flow_instance
                WHERE flow_code = ? AND business_object = ? AND business_id = ?
                LIMIT 1
                """, flowCode, businessObject, businessId);
        createOpenTask(instanceId, flowCode, startNode, "待处理：" + startNode.getNodeName(), null);
        insertRuntimeEvent(instanceId, flowCode, detail.getVersion().getVersionNo(), "INSTANCE_START", null, startNode.getNodeCode(),
                context, StringUtils.hasText(request == null ? null : request.getRemark()) ? request.getRemark().trim() : "创建流程实例");
        return getRuntimeInstance(instanceId);
    }

    @Override
    @Transactional
    public SystemFlowDtos.InstanceResponse transitionInstance(SystemFlowDtos.TransitionInstanceRequest request, PermissionRequestContext context) {
        Long instanceId = request == null ? null : request.getInstanceId();
        if (instanceId == null || instanceId <= 0) {
            throw new BusinessException("instanceId is required");
        }
        String actionCode = normalizeCode(request.getActionCode());
        if (!StringUtils.hasText(actionCode)) {
            throw new BusinessException("actionCode is required");
        }
        SystemFlowDtos.InstanceResponse instance = getRuntimeInstance(instanceId);
        if (!"RUNNING".equalsIgnoreCase(instance.getStatus())) {
            return instance;
        }
        SystemFlowDtos.DetailResponse detail = detail(instance.getFlowCode(), instance.getVersionId());
        SystemFlowDtos.TransitionResponse transition = detail.getTransitions().stream()
                .filter(item -> instance.getCurrentNodeCode().equalsIgnoreCase(item.getFromNodeCode()))
                .filter(item -> actionCode.equalsIgnoreCase(item.getActionCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("no transition found for current node and action"));
        SystemFlowDtos.NodeResponse sourceNode = findNode(detail.getNodes(), instance.getCurrentNodeCode());
        if (!isRoleAllowedForSimulation(context == null ? null : context.getRoleCode(), sourceNode)) {
            throw new BusinessException("current role cannot process this workflow node");
        }
        SystemFlowDtos.NodeResponse targetNode = findNode(detail.getNodes(), transition.getToNodeCode());
        if (targetNode == null) {
            throw new BusinessException("target node not found");
        }
        jdbcTemplate.update("""
                UPDATE system_flow_task
                SET status = 'DONE', completed_at = ?, remark = ?
                WHERE instance_id = ? AND status = 'OPEN'
                """, LocalDateTime.now(), trimToNull(request.getRemark()), instanceId);
        String nextStatus = "END".equalsIgnoreCase(targetNode.getNodeType()) ? "COMPLETED" : "RUNNING";
        jdbcTemplate.update("""
                UPDATE system_flow_instance
                SET current_node_code = ?, status = ?, update_time = ?
                WHERE id = ?
                """, targetNode.getNodeCode(), nextStatus, LocalDateTime.now(), instanceId);
        if ("RUNNING".equals(nextStatus)) {
            createOpenTask(instanceId, instance.getFlowCode(), targetNode, transition.getActionName(), null);
        }
        insertRuntimeEvent(instanceId, instance.getFlowCode(), instance.getVersionNo(), actionCode,
                transition.getFromNodeCode(), transition.getToNodeCode(), context,
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : transition.getActionName());
        return getRuntimeInstance(instanceId);
    }

    @Override
    public List<SystemFlowDtos.TaskResponse> listOpenTasks(String flowCode) {
        String normalizedFlowCode = normalizeFlowCode(flowCode);
        return jdbcTemplate.query("""
                SELECT id, instance_id, flow_code, node_code, node_name, task_name, role_code, assignee_user_id,
                       status, opened_at, completed_at, remark
                FROM system_flow_task
                WHERE flow_code = ? AND status = 'OPEN'
                ORDER BY opened_at DESC, id DESC
                LIMIT 50
                """, (rs, rowNum) -> mapTask(rs), normalizedFlowCode);
    }

    @Override
    public List<SystemFlowDtos.EventLogResponse> listInstanceEvents(Long instanceId) {
        if (instanceId == null || instanceId <= 0) {
            throw new BusinessException("instanceId is required");
        }
        return jdbcTemplate.query("""
                SELECT id, instance_id, flow_code, version_no, action_code, from_node_code, to_node_code,
                       actor_role_code, actor_user_id, summary, event_time
                FROM system_flow_event_log
                WHERE instance_id = ?
                ORDER BY event_time DESC, id DESC
                """, (rs, rowNum) -> mapEvent(rs), instanceId);
    }

    private void compareDefinitionDiff(List<SystemFlowDtos.DiffItemResponse> items,
                                       SystemFlowDtos.DefinitionResponse base,
                                       SystemFlowDtos.SaveDraftRequest draft) {
        if (base == null) {
            return;
        }
        if (!sameText(base.getFlowName(), draft.getFlowName())) {
            items.add(diffItem("DEFINITION", "UPDATE", base.getFlowCode(), "flowName",
                    base.getFlowName(), draft.getFlowName(), "流程名称展示变更，不影响真实业务单据"));
        }
        if (!sameText(base.getDescription(), draft.getDescription())) {
            items.add(diffItem("DEFINITION", "UPDATE", base.getFlowCode(), "description",
                    base.getDescription(), draft.getDescription(), "流程说明变更，不影响真实业务单据"));
        }
    }

    private void compareNodes(List<SystemFlowDtos.DiffItemResponse> items,
                              List<SystemFlowDtos.NodeResponse> baseItems,
                              List<SystemFlowDtos.NodeResponse> draftItems) {
        Map<String, SystemFlowDtos.NodeResponse> baseMap = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.NodeResponse item : safeList(baseItems)) {
            baseMap.put(normalizeCode(item.getNodeCode()), item);
        }
        Map<String, SystemFlowDtos.NodeResponse> draftMap = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.NodeResponse item : safeList(draftItems)) {
            draftMap.put(normalizeCode(item.getNodeCode()), item);
        }
        for (Map.Entry<String, SystemFlowDtos.NodeResponse> entry : draftMap.entrySet()) {
            SystemFlowDtos.NodeResponse base = baseMap.get(entry.getKey());
            String after = nodeFingerprint(entry.getValue());
            if (base == null) {
                items.add(diffItem("NODE", "ADD", entry.getKey(), entry.getValue().getNodeName(),
                        null, after, "新增流程节点，仅作为配置版本展示和模拟输入"));
            } else {
                String before = nodeFingerprint(base);
                if (!Objects.equals(before, after)) {
                    items.add(diffItem("NODE", "UPDATE", entry.getKey(), entry.getValue().getNodeName(),
                            before, after, "节点元数据调整，不直接改动 Clue/Customer/Order/PlanOrder 数据"));
                }
            }
        }
        for (Map.Entry<String, SystemFlowDtos.NodeResponse> entry : baseMap.entrySet()) {
            if (!draftMap.containsKey(entry.getKey())) {
                items.add(diffItem("NODE", "REMOVE", entry.getKey(), entry.getValue().getNodeName(),
                        nodeFingerprint(entry.getValue()), null, "移除流程节点展示；保存/发布前仍受主链约束校验"));
            }
        }
    }

    private void compareTransitions(List<SystemFlowDtos.DiffItemResponse> items,
                                    List<SystemFlowDtos.TransitionResponse> baseItems,
                                    List<SystemFlowDtos.TransitionResponse> draftItems) {
        Map<String, SystemFlowDtos.TransitionResponse> baseMap = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.TransitionResponse item : safeList(baseItems)) {
            baseMap.put(normalizeCode(item.getActionCode()), item);
        }
        Map<String, SystemFlowDtos.TransitionResponse> draftMap = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.TransitionResponse item : safeList(draftItems)) {
            draftMap.put(normalizeCode(item.getActionCode()), item);
        }
        for (Map.Entry<String, SystemFlowDtos.TransitionResponse> entry : draftMap.entrySet()) {
            SystemFlowDtos.TransitionResponse base = baseMap.get(entry.getKey());
            String after = transitionFingerprint(entry.getValue());
            if (base == null) {
                items.add(diffItem("TRANSITION", "ADD", entry.getKey(), entry.getValue().getActionName(),
                        null, after, "新增流转规则，仅作为配置模拟，不自动执行真实业务状态流转"));
            } else {
                String before = transitionFingerprint(base);
                if (!Objects.equals(before, after)) {
                    items.add(diffItem("TRANSITION", "UPDATE", entry.getKey(), entry.getValue().getActionName(),
                            before, after, "流转元数据调整，发布后用于只读模拟和展示"));
                }
            }
        }
        for (Map.Entry<String, SystemFlowDtos.TransitionResponse> entry : baseMap.entrySet()) {
            if (!draftMap.containsKey(entry.getKey())) {
                items.add(diffItem("TRANSITION", "REMOVE", entry.getKey(), entry.getValue().getActionName(),
                        transitionFingerprint(entry.getValue()), null, "移除流转展示；不会删除真实订单动作记录"));
            }
        }
    }

    private void compareTriggers(List<SystemFlowDtos.DiffItemResponse> items,
                                 List<SystemFlowDtos.TriggerResponse> baseItems,
                                 List<SystemFlowDtos.TriggerResponse> draftItems) {
        Map<String, SystemFlowDtos.TriggerResponse> baseMap = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.TriggerResponse item : safeList(baseItems)) {
            baseMap.put(triggerKey(item), item);
        }
        Map<String, SystemFlowDtos.TriggerResponse> draftMap = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.TriggerResponse item : safeList(draftItems)) {
            draftMap.put(triggerKey(item), item);
        }
        for (Map.Entry<String, SystemFlowDtos.TriggerResponse> entry : draftMap.entrySet()) {
            SystemFlowDtos.TriggerResponse base = baseMap.get(entry.getKey());
            String after = triggerFingerprint(entry.getValue());
            if (base == null) {
                items.add(diffItem("TRIGGER", "ADD", entry.getKey(), entry.getValue().getTriggerName(),
                        null, after, "新增触发器元数据，不会直接调用三方接口或内部服务"));
            } else {
                String before = triggerFingerprint(base);
                if (!Objects.equals(before, after)) {
                    items.add(diffItem("TRIGGER", "UPDATE", entry.getKey(), entry.getValue().getTriggerName(),
                            before, after, "触发器元数据调整，真实调用仍由调度中心配置控制"));
                }
            }
        }
        for (Map.Entry<String, SystemFlowDtos.TriggerResponse> entry : baseMap.entrySet()) {
            if (!draftMap.containsKey(entry.getKey())) {
                items.add(diffItem("TRIGGER", "REMOVE", entry.getKey(), entry.getValue().getTriggerName(),
                        triggerFingerprint(entry.getValue()), null, "移除触发器展示，不会停用真实调度任务"));
            }
        }
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private Set<String> collectDomains(List<SystemFlowDtos.NodeResponse> nodes) {
        Set<String> domains = new LinkedHashSet<>();
        for (SystemFlowDtos.NodeResponse node : safeList(nodes)) {
            String domainCode = normalizeCode(node.getDomainCode());
            if (StringUtils.hasText(domainCode)) {
                domains.add(domainCode);
            }
        }
        return domains;
    }

    private Set<String> collectStates(List<SystemFlowDtos.NodeResponse> nodes, String domainCode) {
        Set<String> states = new LinkedHashSet<>();
        String normalizedDomain = normalizeCode(domainCode);
        for (SystemFlowDtos.NodeResponse node : safeList(nodes)) {
            if (!normalizedDomain.equals(normalizeCode(node.getDomainCode()))) {
                continue;
            }
            String state = normalizeBusinessState(node.getBusinessState(), node.getDomainCode());
            if (StringUtils.hasText(state)) {
                states.add(state);
            }
        }
        return states;
    }

    private void addGuardedValidation(List<SystemFlowDtos.ValidationItemResponse> items,
                                      String domain,
                                      String checkCode,
                                      Runnable validation,
                                      String successMessage,
                                      String suggestion) {
        try {
            validation.run();
            addValidationItem(items, domain, checkCode, true, successMessage, suggestion);
        } catch (BusinessException exception) {
            addValidationItem(items, domain, checkCode, false, exception.getMessage(), suggestion);
        }
    }

    private void addValidationItem(List<SystemFlowDtos.ValidationItemResponse> items,
                                   String domain,
                                   String checkCode,
                                   boolean passed,
                                   String message,
                                   String suggestion) {
        SystemFlowDtos.ValidationItemResponse item = new SystemFlowDtos.ValidationItemResponse();
        item.setDomain(domain);
        item.setCheckCode(checkCode);
        item.setSeverity(passed ? "PASS" : "BLOCKER");
        item.setPassed(passed);
        item.setMessage(message);
        item.setSuggestion(suggestion);
        items.add(item);
    }

    private SystemFlowDtos.TriggerLinkageItemResponse buildTriggerLinkageItem(SystemFlowDtos.TriggerResponse trigger) {
        SystemFlowDtos.TriggerLinkageItemResponse item = new SystemFlowDtos.TriggerLinkageItemResponse();
        item.setNodeCode(trigger == null ? null : trigger.getNodeCode());
        item.setTriggerName(trigger == null ? null : trigger.getTriggerName());
        item.setTriggerType(trigger == null ? null : trigger.getTriggerType());
        item.setTargetCode(trigger == null ? null : trigger.getTargetCode());
        item.setExecutionMode(trigger == null ? null : trigger.getExecutionMode());

        if (trigger == null) {
            fillLinkageItem(item, "FLOW", "system_flow_trigger", "BLOCKER",
                    "触发器配置为空",
                    "请重新保存流程配置，确保触发器元数据完整");
            return item;
        }

        if (trigger.getEnabled() != null && trigger.getEnabled() == 0) {
            fillLinkageItem(item, "FLOW", "system_flow_trigger:" + text(trigger.getTargetCode()), "INFO",
                    "触发器已隐藏，当前不参与联动体检",
                    "如需启用，请先在流程草稿中打开展示开关并发布");
            return item;
        }

        String targetCode = normalizeCode(trigger.getTargetCode());
        if (!StringUtils.hasText(targetCode)) {
            fillLinkageItem(item, normalizeCode(trigger.getTriggerType()), "system_flow_trigger", "WARNING",
                    "触发器未填写目标编码，无法关联调度或三方接口配置",
                    "请使用 DOUYIN_CLUE_INCREMENTAL、DOUYIN_VOUCHER_VERIFY 或 *_METADATA 这类元数据编码");
            return item;
        }

        if ("DOUYIN_CLUE_INCREMENTAL".equals(targetCode)) {
            inspectDouyinClueScheduler(item, targetCode);
            return item;
        }
        if ("DOUYIN_VOUCHER_VERIFY".equals(targetCode)) {
            inspectDouyinVoucherProvider(item);
            return item;
        }
        if ("ORDER_SETTLEMENT_METADATA".equals(targetCode)) {
            fillLinkageItem(item, "ORDER", "system_flow_trigger:" + targetCode, "LINKED",
                    "订单后续结算仅作为流程元数据保留，真实执行由结算中心配置控制",
                    "保持元数据方式，后续如要自动执行必须先接入可审计的结算任务");
            return item;
        }
        if ("SALARY_SETTLEMENT_METADATA".equals(targetCode)) {
            fillLinkageItem(item, "SALARY", "system_flow_trigger:" + targetCode, "LINKED",
                    "薪酬入账仅作为流程元数据保留，计算来源仍以 order_role_record 为准",
                    "保持薪酬规则可配置，不在流程引擎中写死金额或分佣逻辑");
            return item;
        }
        if (targetCode.endsWith("_METADATA")) {
            fillLinkageItem(item, "METADATA", "system_flow_trigger:" + targetCode, "WARNING",
                    "扩展元数据已保留，但尚未纳入 SystemFlow V1 标准联动清单",
                    "上线前请补充对应系统配置页、权限点和审计记录");
            return item;
        }

        fillLinkageItem(item, "UNKNOWN", "system_flow_trigger:" + targetCode, "BLOCKER",
                "触发器目标不在 V1 安全白名单内",
                "请改为安全元数据编码，真实接口调用必须放到调度中心或三方接口配置中");
        return item;
    }

    private void inspectDouyinClueScheduler(SystemFlowDtos.TriggerLinkageItemResponse item, String targetCode) {
        Map<String, Object> job = querySingleOrNull("""
                SELECT job_code, module_code, sync_mode, interval_minutes, retry_limit, queue_name, provider_id, endpoint, status
                FROM scheduler_job
                WHERE job_code = ?
                LIMIT 1
                """, targetCode);
        if (job == null) {
            fillLinkageItem(item, "SCHEDULER", "scheduler_job:" + targetCode, "BLOCKER",
                    "未找到抖音客资增量同步任务",
                    "请在调度中心补齐 DOUYIN_CLUE_INCREMENTAL，频率 1 分钟、增量同步、开启队列和重试");
            return;
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!"ENABLED".equalsIgnoreCase(valueAsString(job, "status"))) {
            blockers.add("任务未启用");
        }
        if (!"CLUE".equalsIgnoreCase(valueAsString(job, "module_code"))) {
            blockers.add("任务模块不是 Clue");
        }
        if (!"INCREMENTAL".equalsIgnoreCase(valueAsString(job, "sync_mode"))) {
            blockers.add("同步方式不是增量");
        }
        Integer intervalMinutes = valueAsInteger(job, "interval_minutes");
        if (intervalMinutes == null || intervalMinutes != 1) {
            warnings.add("抖音同步频率不是 1 分钟");
        }
        Integer retryLimit = valueAsInteger(job, "retry_limit");
        if (retryLimit == null || retryLimit < 1) {
            blockers.add("未配置失败重试");
        }
        if (!StringUtils.hasText(valueAsString(job, "queue_name"))) {
            blockers.add("未配置队列名称");
        }
        if (!StringUtils.hasText(valueAsString(job, "endpoint"))) {
            warnings.add("未配置入库端点");
        }

        Long providerId = valueAsLong(job, "provider_id");
        Map<String, Object> provider = providerId == null ? queryDouyinProvider() : queryProviderById(providerId);
        if (provider == null) {
            warnings.add("任务未关联抖音 Provider 配置");
        } else if (!isEnabled(provider)) {
            warnings.add("抖音 Provider 未启用");
        }

        String status = blockers.isEmpty() ? (warnings.isEmpty() ? "LINKED" : "WARNING") : "BLOCKER";
        List<String> findings = blockers.isEmpty() ? warnings : blockers;
        fillLinkageItem(item, "SCHEDULER", "scheduler_job:" + targetCode, status,
                findings.isEmpty() ? "调度任务已对齐：Clue 增量同步、1 分钟频率、队列和重试均可用" : String.join("；", findings),
                status.equals("LINKED") ? "保持调度中心配置与流程触发器编码一致" : "请到调度中心修正任务配置，流程引擎只引用任务编码，不直接执行同步");
    }

    private void inspectDouyinVoucherProvider(SystemFlowDtos.TriggerLinkageItemResponse item) {
        Map<String, Object> provider = queryDouyinProvider();
        if (provider == null) {
            fillLinkageItem(item, "THIRD_PARTY_API", "integration_provider_config:DOUYIN_LAIKE", "BLOCKER",
                    "未找到抖音接入配置",
                    "请在系统设置的抖音接口中补齐 Provider、核销路径、授权与测试按钮配置");
            return;
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!isEnabled(provider)) {
            blockers.add("抖音接入未启用");
        }
        if (!StringUtils.hasText(valueAsString(provider, "voucher_verify_path"))) {
            blockers.add("未配置券码核销路径");
        }
        if (!StringUtils.hasText(valueAsString(provider, "verify_code_field"))) {
            warnings.add("未配置核销码字段映射");
        }
        String executionMode = valueAsString(provider, "execution_mode");
        if (!Set.of("MOCK", "LIVE", "REAL").contains(String.valueOf(executionMode).toUpperCase(Locale.ROOT))) {
            warnings.add("运行模式不是 MOCK 或真实模式");
        }

        String status = blockers.isEmpty() ? (warnings.isEmpty() ? "LINKED" : "WARNING") : "BLOCKER";
        List<String> findings = blockers.isEmpty() ? warnings : blockers;
        fillLinkageItem(item, "THIRD_PARTY_API", "integration_provider_config:DOUYIN_LAIKE", status,
                findings.isEmpty() ? "抖音券码核销配置已对齐，支持 MOCK 验证并可切换真实接口" : String.join("；", findings),
                status.equals("LINKED") ? "真实上线前仍需在抖音接口页完成授权、接口测试和回调联调" : "请在抖音接口页补齐配置，流程引擎只做元数据引用");
    }

    private Map<String, Object> queryDouyinProvider() {
        return querySingleOrNull("""
                SELECT id, provider_code, provider_name, module_code, execution_mode, enabled, voucher_verify_path, verify_code_field
                FROM integration_provider_config
                WHERE provider_code = 'DOUYIN_LAIKE'
                LIMIT 1
                """);
    }

    private Map<String, Object> queryProviderById(Long providerId) {
        return querySingleOrNull("""
                SELECT id, provider_code, provider_name, module_code, execution_mode, enabled, voucher_verify_path, verify_code_field
                FROM integration_provider_config
                WHERE id = ?
                LIMIT 1
                """, providerId);
    }

    private Map<String, Object> querySingleOrNull(String sql, Object... args) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            return rows == null || rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    private void fillLinkageItem(SystemFlowDtos.TriggerLinkageItemResponse item,
                                 String linkedModule,
                                 String linkedResource,
                                 String status,
                                 String message,
                                 String suggestion) {
        item.setLinkedModule(linkedModule);
        item.setLinkedResource(linkedResource);
        item.setStatus(status);
        item.setMessage(message);
        item.setSuggestion(suggestion);
    }

    private boolean isEnabled(Map<String, Object> row) {
        Integer enabled = valueAsInteger(row, "enabled");
        if (enabled != null) {
            return enabled == 1;
        }
        return !"DISABLED".equalsIgnoreCase(valueAsString(row, "status"));
    }

    private String valueAsString(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer valueAsInteger(Map<String, Object> row, String key) {
        if (row == null || key == null || row.get(key) == null) {
            return null;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long valueAsLong(Map<String, Object> row, String key) {
        if (row == null || key == null || row.get(key) == null) {
            return null;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private SystemFlowDtos.DiffItemResponse diffItem(String domain,
                                                     String changeType,
                                                     String objectCode,
                                                     String title,
                                                     String beforeValue,
                                                     String afterValue,
                                                     String impact) {
        SystemFlowDtos.DiffItemResponse item = new SystemFlowDtos.DiffItemResponse();
        item.setDomain(domain);
        item.setChangeType(changeType);
        item.setObjectCode(objectCode);
        item.setTitle(title);
        item.setBeforeValue(beforeValue);
        item.setAfterValue(afterValue);
        item.setImpact(impact);
        return item;
    }

    private String nodeFingerprint(SystemFlowDtos.NodeResponse node) {
        if (node == null) {
            return "";
        }
        return "domain=" + normalizeCode(node.getDomainCode())
                + "; name=" + text(node.getNodeName())
                + "; type=" + normalizeCode(node.getNodeType())
                + "; state=" + text(normalizeBusinessState(node.getBusinessState(), node.getDomainCode()))
                + "; role=" + normalizeCode(node.getRoleCode())
                + "; sort=" + (node.getSortOrder() == null ? 0 : node.getSortOrder());
    }

    private String transitionFingerprint(SystemFlowDtos.TransitionResponse transition) {
        if (transition == null) {
            return "";
        }
        return "from=" + normalizeCode(transition.getFromNodeCode())
                + "; to=" + normalizeCode(transition.getToNodeCode())
                + "; name=" + text(transition.getActionName())
                + "; guard=" + text(transition.getGuardRule())
                + "; sort=" + (transition.getSortOrder() == null ? 0 : transition.getSortOrder());
    }

    private String triggerFingerprint(SystemFlowDtos.TriggerResponse trigger) {
        if (trigger == null) {
            return "";
        }
        return "node=" + normalizeCode(trigger.getNodeCode())
                + "; type=" + normalizeCode(trigger.getTriggerType())
                + "; name=" + text(trigger.getTriggerName())
                + "; target=" + normalizeCode(trigger.getTargetCode())
                + "; mode=" + normalizeCode(trigger.getExecutionMode())
                + "; enabled=" + (trigger.getEnabled() == null ? 1 : trigger.getEnabled())
                + "; sort=" + (trigger.getSortOrder() == null ? 0 : trigger.getSortOrder());
    }

    private String triggerKey(SystemFlowDtos.TriggerResponse trigger) {
        if (trigger == null) {
            return "";
        }
        return normalizeCode(trigger.getNodeCode()) + "|"
                + normalizeCode(trigger.getTargetCode()) + "|"
                + text(trigger.getTriggerName());
    }

    private boolean sameText(String left, String right) {
        return Objects.equals(text(left), text(right));
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private Long upsertDefinition(SystemFlowDtos.SaveDraftRequest request, String flowCode) {
        assertSupportedFlowCode(flowCode);
        Long existingId = jdbcTemplate.query("""
                SELECT id
                FROM system_flow_definition
                WHERE flow_code = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, flowCode);
        LocalDateTime now = LocalDateTime.now();
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO system_flow_definition(flow_code, flow_name, module_code, business_object, description, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                    """, flowCode, request.getFlowName().trim(), normalizeCode(request.getModuleCode()),
                    normalizeCode(request.getBusinessObject()), request.getDescription(), now, now);
            return jdbcTemplate.queryForObject("SELECT id FROM system_flow_definition WHERE flow_code = ?", Long.class, flowCode);
        }
        jdbcTemplate.update("""
                UPDATE system_flow_definition
                SET flow_name = ?, module_code = ?, business_object = ?, description = ?, updated_at = ?
                WHERE id = ?
                """, request.getFlowName().trim(), normalizeCode(request.getModuleCode()),
                normalizeCode(request.getBusinessObject()), request.getDescription(), now, existingId);
        return existingId;
    }

    private void insertNodes(Long versionId, List<SystemFlowDtos.NodeResponse> nodes) {
        Set<String> codes = new LinkedHashSet<>();
        for (SystemFlowDtos.NodeResponse node : nodes) {
            String nodeCode = normalizeCode(node.getNodeCode());
            if (!StringUtils.hasText(nodeCode) || !codes.add(nodeCode)) {
                throw new BusinessException("nodeCode is required and cannot duplicate");
            }
            jdbcTemplate.update("""
                    INSERT INTO system_flow_node(version_id, domain_code, node_code, node_name, node_type, business_state, role_code, sort_order, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, versionId, normalizeCode(node.getDomainCode()), nodeCode, required(node.getNodeName(), "nodeName"),
                    normalizeOrDefault(node.getNodeType(), "TASK"), normalizeBusinessState(node.getBusinessState(), node.getDomainCode()),
                    normalizeCode(node.getRoleCode()), node.getSortOrder() == null ? 0 : node.getSortOrder(),
                    node.getDescription());
        }
    }

    private void insertTransitions(Long versionId, List<SystemFlowDtos.TransitionResponse> transitions) {
        for (SystemFlowDtos.TransitionResponse transition : transitions) {
            jdbcTemplate.update("""
                    INSERT INTO system_flow_transition(version_id, from_node_code, to_node_code, action_code, action_name, guard_rule, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, versionId, normalizeCode(transition.getFromNodeCode()), normalizeCode(transition.getToNodeCode()),
                    normalizeCode(transition.getActionCode()), required(transition.getActionName(), "actionName"),
                    transition.getGuardRule(), transition.getSortOrder() == null ? 0 : transition.getSortOrder());
        }
    }

    private void insertTriggers(Long versionId, List<SystemFlowDtos.TriggerResponse> triggers) {
        for (SystemFlowDtos.TriggerResponse trigger : triggers) {
            jdbcTemplate.update("""
                    INSERT INTO system_flow_trigger(version_id, node_code, trigger_type, trigger_name, target_code, execution_mode, enabled, sort_order, config_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, versionId, normalizeCode(trigger.getNodeCode()), normalizeOrDefault(trigger.getTriggerType(), "INTERNAL_SERVICE"),
                    required(trigger.getTriggerName(), "triggerName"), trigger.getTargetCode(),
                    normalizeOrDefault(trigger.getExecutionMode(), "METADATA_ONLY"),
                    trigger.getEnabled() == null ? 1 : trigger.getEnabled(),
                    trigger.getSortOrder() == null ? 0 : trigger.getSortOrder(), trigger.getConfigJson());
        }
    }

    private void validateDraft(SystemFlowDtos.SaveDraftRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getFlowCode())
                || !StringUtils.hasText(request.getFlowName())
                || !StringUtils.hasText(request.getModuleCode())
                || !StringUtils.hasText(request.getBusinessObject())) {
            throw new BusinessException("flowCode, flowName, moduleCode and businessObject are required");
        }
        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new BusinessException("flow nodes are required");
        }
        if (request.getTransitions() == null || request.getTransitions().isEmpty()) {
            throw new BusinessException("flow transitions are required");
        }
        validateOrderMainFlowConstraints(request);
    }

    private void validateVersionPublishable(Long versionId) {
        List<SystemFlowDtos.NodeResponse> nodes = listNodes(versionId);
        List<SystemFlowDtos.TransitionResponse> transitions = listTransitions(versionId);
        if (nodes.isEmpty()) {
            throw new BusinessException("flow version has no nodes");
        }
        if (transitions.isEmpty()) {
            throw new BusinessException("flow version has no transitions");
        }
        validateOrderMainFlowConstraints(nodes, transitions, listTriggers(versionId));
    }

    private SystemFlowDtos.DefinitionResponse getDefinition(String flowCode) {
        return listDefinitions().stream()
                .filter(item -> item.getFlowCode().equalsIgnoreCase(flowCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("system flow not found"));
    }

    private SystemFlowDtos.VersionResponse getVersion(Long versionId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, definition_id, version_no, status, change_summary, published_at, created_at, updated_at
                FROM system_flow_version
                WHERE id = ?
                """, (rs, rowNum) -> mapVersion(rs), versionId);
    }

    private SystemFlowDtos.VersionResponse mapVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
        SystemFlowDtos.VersionResponse item = new SystemFlowDtos.VersionResponse();
        item.setId(rs.getLong("id"));
        item.setDefinitionId(rs.getLong("definition_id"));
        item.setVersionNo(rs.getInt("version_no"));
        item.setStatus(rs.getString("status"));
        item.setChangeSummary(rs.getString("change_summary"));
        item.setPublishedAt(rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toLocalDateTime());
        item.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        item.setUpdatedAt(rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
        return item;
    }

    private List<SystemFlowDtos.NodeResponse> listNodes(Long versionId) {
        return jdbcTemplate.query("""
                SELECT id, domain_code, node_code, node_name, node_type, business_state, role_code, sort_order, description
                FROM system_flow_node
                WHERE version_id = ?
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> {
                SystemFlowDtos.NodeResponse item = new SystemFlowDtos.NodeResponse();
                item.setId(rs.getLong("id"));
                item.setDomainCode(rs.getString("domain_code"));
                item.setNodeCode(rs.getString("node_code"));
                item.setNodeName(rs.getString("node_name"));
                item.setNodeType(rs.getString("node_type"));
                item.setBusinessState(rs.getString("business_state"));
                item.setRoleCode(rs.getString("role_code"));
                item.setSortOrder(rs.getInt("sort_order"));
                item.setDescription(rs.getString("description"));
                return item;
            }, versionId);
    }

    private List<SystemFlowDtos.TransitionResponse> listTransitions(Long versionId) {
        return jdbcTemplate.query("""
                SELECT id, from_node_code, to_node_code, action_code, action_name, guard_rule, sort_order
                FROM system_flow_transition
                WHERE version_id = ?
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> {
                SystemFlowDtos.TransitionResponse item = new SystemFlowDtos.TransitionResponse();
                item.setId(rs.getLong("id"));
                item.setFromNodeCode(rs.getString("from_node_code"));
                item.setToNodeCode(rs.getString("to_node_code"));
                item.setActionCode(rs.getString("action_code"));
                item.setActionName(rs.getString("action_name"));
                item.setGuardRule(rs.getString("guard_rule"));
                item.setSortOrder(rs.getInt("sort_order"));
                return item;
            }, versionId);
    }

    private List<SystemFlowDtos.TriggerResponse> listTriggers(Long versionId) {
        return jdbcTemplate.query("""
                SELECT id, node_code, trigger_type, trigger_name, target_code, execution_mode, enabled, sort_order, config_json
                FROM system_flow_trigger
                WHERE version_id = ?
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> {
                SystemFlowDtos.TriggerResponse item = new SystemFlowDtos.TriggerResponse();
                item.setId(rs.getLong("id"));
                item.setNodeCode(rs.getString("node_code"));
                item.setTriggerType(rs.getString("trigger_type"));
                item.setTriggerName(rs.getString("trigger_name"));
                item.setTargetCode(rs.getString("target_code"));
                item.setExecutionMode(rs.getString("execution_mode"));
                item.setEnabled(rs.getInt("enabled"));
                item.setSortOrder(rs.getInt("sort_order"));
                item.setConfigJson(rs.getString("config_json"));
                return item;
            }, versionId);
    }

    private Long latestVersionId(Long definitionId) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM system_flow_version
                WHERE definition_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """, Long.class, definitionId);
    }

    private int nextVersionNo(Long definitionId) {
        Integer maxVersion = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0)
                FROM system_flow_version
                WHERE definition_id = ?
                """, Integer.class, definitionId);
        return (maxVersion == null ? 0 : maxVersion) + 1;
    }

    private List<String> buildPath(List<SystemFlowDtos.NodeResponse> nodes, String currentNodeCode, String nextNodeCode) {
        List<SystemFlowDtos.NodeResponse> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(item -> item.getSortOrder() == null ? 0 : item.getSortOrder()));
        List<String> result = new ArrayList<>();
        for (SystemFlowDtos.NodeResponse node : sortedNodes) {
            result.add(node.getNodeCode() + ":" + node.getNodeName());
            if (StringUtils.hasText(nextNodeCode) && node.getNodeCode().equalsIgnoreCase(nextNodeCode)) {
                break;
            }
            if (!StringUtils.hasText(nextNodeCode) && node.getNodeCode().equalsIgnoreCase(currentNodeCode)) {
                break;
            }
        }
        return result;
    }

    private List<SystemFlowDtos.InstanceResponse> listRuntimeInstances(String flowCode, int limit) {
        return jdbcTemplate.query("""
                SELECT i.id, i.flow_code, i.version_id, i.version_no, i.business_object, i.business_id,
                       i.current_node_code, n.node_name AS current_node_name, i.status, i.title,
                       i.created_by_role_code, i.created_by_user_id, i.create_time, i.update_time
                FROM system_flow_instance i
                LEFT JOIN system_flow_node n ON n.version_id = i.version_id AND n.node_code = i.current_node_code
                WHERE i.flow_code = ?
                ORDER BY i.update_time DESC, i.id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapInstance(rs), flowCode, limit);
    }

    private SystemFlowDtos.InstanceResponse getRuntimeInstance(Long instanceId) {
        List<SystemFlowDtos.InstanceResponse> rows = jdbcTemplate.query("""
                SELECT i.id, i.flow_code, i.version_id, i.version_no, i.business_object, i.business_id,
                       i.current_node_code, n.node_name AS current_node_name, i.status, i.title,
                       i.created_by_role_code, i.created_by_user_id, i.create_time, i.update_time
                FROM system_flow_instance i
                LEFT JOIN system_flow_node n ON n.version_id = i.version_id AND n.node_code = i.current_node_code
                WHERE i.id = ?
                """, (rs, rowNum) -> mapInstance(rs), instanceId);
        if (rows.isEmpty()) {
            throw new BusinessException("workflow instance not found");
        }
        return rows.get(0);
    }

    private SystemFlowDtos.InstanceResponse mapInstance(java.sql.ResultSet rs) throws java.sql.SQLException {
        SystemFlowDtos.InstanceResponse item = new SystemFlowDtos.InstanceResponse();
        item.setId(rs.getLong("id"));
        item.setFlowCode(rs.getString("flow_code"));
        item.setVersionId(rs.getLong("version_id"));
        item.setVersionNo(rs.getInt("version_no"));
        item.setBusinessObject(rs.getString("business_object"));
        item.setBusinessId(rs.getLong("business_id"));
        item.setCurrentNodeCode(rs.getString("current_node_code"));
        item.setCurrentNodeName(rs.getString("current_node_name"));
        item.setStatus(rs.getString("status"));
        item.setTitle(rs.getString("title"));
        item.setCreatedByRoleCode(rs.getString("created_by_role_code"));
        long createdByUserId = rs.getLong("created_by_user_id");
        item.setCreatedByUserId(rs.wasNull() ? null : createdByUserId);
        item.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        item.setUpdateTime(rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
        return item;
    }

    private SystemFlowDtos.TaskResponse mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
        SystemFlowDtos.TaskResponse item = new SystemFlowDtos.TaskResponse();
        item.setId(rs.getLong("id"));
        item.setInstanceId(rs.getLong("instance_id"));
        item.setFlowCode(rs.getString("flow_code"));
        item.setNodeCode(rs.getString("node_code"));
        item.setNodeName(rs.getString("node_name"));
        item.setTaskName(rs.getString("task_name"));
        item.setRoleCode(rs.getString("role_code"));
        long assigneeUserId = rs.getLong("assignee_user_id");
        item.setAssigneeUserId(rs.wasNull() ? null : assigneeUserId);
        item.setStatus(rs.getString("status"));
        item.setOpenedAt(rs.getTimestamp("opened_at") == null ? null : rs.getTimestamp("opened_at").toLocalDateTime());
        item.setCompletedAt(rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime());
        item.setRemark(rs.getString("remark"));
        return item;
    }

    private SystemFlowDtos.EventLogResponse mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        SystemFlowDtos.EventLogResponse item = new SystemFlowDtos.EventLogResponse();
        item.setId(rs.getLong("id"));
        item.setInstanceId(rs.getLong("instance_id"));
        item.setFlowCode(rs.getString("flow_code"));
        int versionNo = rs.getInt("version_no");
        item.setVersionNo(rs.wasNull() ? null : versionNo);
        item.setActionCode(rs.getString("action_code"));
        item.setFromNodeCode(rs.getString("from_node_code"));
        item.setToNodeCode(rs.getString("to_node_code"));
        item.setActorRoleCode(rs.getString("actor_role_code"));
        long actorUserId = rs.getLong("actor_user_id");
        item.setActorUserId(rs.wasNull() ? null : actorUserId);
        item.setSummary(rs.getString("summary"));
        item.setEventTime(rs.getTimestamp("event_time") == null ? null : rs.getTimestamp("event_time").toLocalDateTime());
        return item;
    }

    private void createOpenTask(Long instanceId,
                                String flowCode,
                                SystemFlowDtos.NodeResponse node,
                                String taskName,
                                Long assigneeUserId) {
        if (instanceId == null || node == null || !"TASK".equalsIgnoreCase(node.getNodeType())) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO system_flow_task(
                    instance_id, flow_code, node_code, node_name, task_name, role_code, assignee_user_id, status, opened_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
                """, instanceId, flowCode, node.getNodeCode(), node.getNodeName(),
                StringUtils.hasText(taskName) ? taskName : node.getNodeName(),
                node.getRoleCode(), assigneeUserId, LocalDateTime.now());
    }

    private void insertRuntimeEvent(Long instanceId,
                                    String flowCode,
                                    Integer versionNo,
                                    String actionCode,
                                    String fromNodeCode,
                                    String toNodeCode,
                                    PermissionRequestContext context,
                                    String summary) {
        jdbcTemplate.update("""
                INSERT INTO system_flow_event_log(
                    instance_id, flow_code, version_no, action_code, from_node_code, to_node_code,
                    actor_role_code, actor_user_id, summary, event_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, instanceId, flowCode, versionNo, normalizeCode(actionCode), fromNodeCode, toNodeCode,
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                summary, LocalDateTime.now());
    }

    private Integer queryCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private Long queryLongOrNull(String sql, Object... args) {
        List<Long> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void audit(String flowCode, Integer versionNo, String actionType, PermissionRequestContext context, String summary) {
        jdbcTemplate.update("""
                INSERT INTO system_flow_audit_log(flow_code, version_no, action_type, actor_role_code, actor_user_id, summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, flowCode, versionNo, actionType,
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                summary,
                LocalDateTime.now());
    }

    private String normalizeFlowCode(String value) {
        String flowCode = StringUtils.hasText(value) ? normalizeCode(value) : DEFAULT_FLOW_CODE;
        assertSupportedFlowCode(flowCode);
        return flowCode;
    }

    private void assertSupportedFlowCode(String flowCode) {
        if (!SYSTEM_FLOW_V1_SUPPORTED_CODES.contains(flowCode)) {
            throw new BusinessException("SystemFlow V1 only supports ORDER_MAIN_FLOW");
        }
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? normalizeCode(value) : defaultValue;
    }

    private String normalizeBusinessState(String value, String domainCode) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        String normalizedDomain = normalizeCode(domainCode);
        if (CORE_DOMAIN_STATES.containsKey(normalizedDomain)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }

    private String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SystemFlowDtos.NodeResponse findNode(List<SystemFlowDtos.NodeResponse> nodes, String nodeCode) {
        if (nodes == null || !StringUtils.hasText(nodeCode)) {
            return null;
        }
        return nodes.stream()
                .filter(item -> nodeCode.equalsIgnoreCase(item.getNodeCode()))
                .findFirst()
                .orElse(null);
    }

    private boolean isRoleAllowedForSimulation(String roleCode, SystemFlowDtos.NodeResponse sourceNode) {
        String requestedRole = normalizeCode(roleCode);
        String ownerRole = normalizeCode(sourceNode == null ? null : sourceNode.getRoleCode());
        if (!StringUtils.hasText(ownerRole) || "SYSTEM".equals(ownerRole)) {
            return true;
        }
        if ("ADMIN".equals(requestedRole) || "SYSTEM".equals(requestedRole)) {
            return true;
        }
        return ownerRole.equals(requestedRole);
    }

    private void validateOrderMainFlowConstraints(SystemFlowDtos.SaveDraftRequest request) {
        String flowCode = normalizeFlowCode(request.getFlowCode());
        if (!DEFAULT_FLOW_CODE.equals(flowCode)) {
            return;
        }
        validateOrderMainFlowConstraints(request.getNodes(), request.getTransitions(), request.getTriggers());
    }

    private void validateOrderMainFlowConstraints(List<SystemFlowDtos.NodeResponse> nodes,
                                                  List<SystemFlowDtos.TransitionResponse> transitions,
                                                  List<SystemFlowDtos.TriggerResponse> triggers) {
        validateRequiredDomains(nodes);
        validateAllowedDomains(nodes);
        validateCoreDomainStates(nodes);
        validateRequiredDomainOrder(nodes);
        validateTransitionReferences(nodes, transitions);
        validateTransitionDomainPath(nodes, transitions);
        validateUniqueActions(transitions);
        validateTriggerTargets(nodes, triggers);
    }

    private void validateRequiredDomains(List<SystemFlowDtos.NodeResponse> nodes) {
        Set<String> domains = new LinkedHashSet<>();
        for (SystemFlowDtos.NodeResponse node : nodes) {
            if (StringUtils.hasText(node.getDomainCode())) {
                domains.add(normalizeCode(node.getDomainCode()));
            }
        }
        for (String requiredDomain : REQUIRED_ORDER_MAIN_CHAIN) {
            if (!domains.contains(requiredDomain)) {
                throw new BusinessException("ORDER_MAIN_FLOW must include domain: " + requiredDomain);
            }
        }
    }

    private void validateAllowedDomains(List<SystemFlowDtos.NodeResponse> nodes) {
        for (SystemFlowDtos.NodeResponse node : nodes) {
            String domainCode = normalizeCode(node.getDomainCode());
            if (StringUtils.hasText(domainCode) && !ALLOWED_ORDER_MAIN_DOMAINS.contains(domainCode)) {
                throw new BusinessException("ORDER_MAIN_FLOW V1 only allows main-chain domains; Scheduler must stay in trigger metadata. invalid domain: " + domainCode);
            }
        }
    }

    private void validateCoreDomainStates(List<SystemFlowDtos.NodeResponse> nodes) {
        Set<String> orderStates = new LinkedHashSet<>();
        Set<String> planOrderStates = new LinkedHashSet<>();
        for (SystemFlowDtos.NodeResponse node : nodes) {
            String domainCode = normalizeCode(node.getDomainCode());
            String state = node.getBusinessState() == null ? "" : node.getBusinessState().trim().toLowerCase(Locale.ROOT);
            if (!CORE_DOMAIN_STATES.containsKey(domainCode) || !StringUtils.hasText(state)) {
                continue;
            }
            if (!CORE_DOMAIN_STATES.get(domainCode).contains(state)) {
                throw new BusinessException(domainCode + " businessState must be one of " + CORE_DOMAIN_STATES.get(domainCode));
            }
            if ("ORDER".equals(domainCode)) {
                orderStates.add(state);
            } else if ("PLANORDER".equals(domainCode)) {
                planOrderStates.add(state);
            }
        }
        if (!orderStates.containsAll(CORE_DOMAIN_STATES.get("ORDER"))) {
            throw new BusinessException("ORDER_MAIN_FLOW must include Order states: paid, used");
        }
        if (!planOrderStates.containsAll(CORE_DOMAIN_STATES.get("PLANORDER"))) {
            throw new BusinessException("ORDER_MAIN_FLOW must include PlanOrder states: arrived, service_form_confirmed, servicing, finished");
        }
    }

    private void validateRequiredDomainOrder(List<SystemFlowDtos.NodeResponse> nodes) {
        List<SystemFlowDtos.NodeResponse> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(item -> item.getSortOrder() == null ? 0 : item.getSortOrder()));
        int lastIndex = -1;
        for (String requiredDomain : REQUIRED_ORDER_MAIN_CHAIN) {
            int index = -1;
            for (int i = 0; i < sortedNodes.size(); i++) {
                if (requiredDomain.equals(normalizeCode(sortedNodes.get(i).getDomainCode()))) {
                    index = i;
                    break;
                }
            }
            if (index <= lastIndex) {
                throw new BusinessException("ORDER_MAIN_FLOW domain order must be Clue -> Customer -> Order -> PlanOrder");
            }
            lastIndex = index;
        }
    }

    private void validateTransitionDomainPath(List<SystemFlowDtos.NodeResponse> nodes,
                                              List<SystemFlowDtos.TransitionResponse> transitions) {
        Map<String, SystemFlowDtos.NodeResponse> nodeByCode = nodes.stream()
                .filter(node -> StringUtils.hasText(node.getNodeCode()))
                .collect(java.util.stream.Collectors.toMap(
                        node -> normalizeCode(node.getNodeCode()),
                        node -> node,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        Map<String, List<String>> graph = new java.util.LinkedHashMap<>();
        for (SystemFlowDtos.TransitionResponse transition : transitions) {
            String fromCode = normalizeCode(transition.getFromNodeCode());
            String toCode = normalizeCode(transition.getToNodeCode());
            SystemFlowDtos.NodeResponse fromNode = nodeByCode.get(fromCode);
            SystemFlowDtos.NodeResponse toNode = nodeByCode.get(toCode);
            if (fromNode == null || toNode == null) {
                continue;
            }
            int fromIndex = domainIndex(fromNode.getDomainCode());
            int toIndex = domainIndex(toNode.getDomainCode());
            boolean terminalOrderSync = "PLANORDER".equals(normalizeCode(fromNode.getDomainCode()))
                    && "ORDER".equals(normalizeCode(toNode.getDomainCode()))
                    && "ORDER_USED".equals(toCode)
                    && "used".equalsIgnoreCase(String.valueOf(toNode.getBusinessState()));
            if (fromIndex >= 0 && toIndex >= 0
                    && !terminalOrderSync
                    && (toIndex < fromIndex || toIndex > fromIndex + 1)) {
                throw new BusinessException("ORDER_MAIN_FLOW transition graph must follow Clue -> Customer -> Order -> PlanOrder: "
                        + transition.getActionCode());
            }
            graph.computeIfAbsent(fromCode, key -> new ArrayList<>()).add(toCode);
        }

        String startNodeCode = nodes.stream()
                .filter(node -> "CLUE".equals(normalizeCode(node.getDomainCode())))
                .min(Comparator.comparing(item -> item.getSortOrder() == null ? 0 : item.getSortOrder()))
                .map(node -> normalizeCode(node.getNodeCode()))
                .orElse(null);
        if (!StringUtils.hasText(startNodeCode)) {
            throw new BusinessException("ORDER_MAIN_FLOW transition graph must start from Clue");
        }

        Set<String> visited = new LinkedHashSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(startNodeCode);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (String next : graph.getOrDefault(current, List.of())) {
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }

        for (String requiredDomain : REQUIRED_ORDER_MAIN_CHAIN) {
            boolean reachable = visited.stream()
                    .map(nodeByCode::get)
                    .filter(Objects::nonNull)
                    .anyMatch(node -> requiredDomain.equals(normalizeCode(node.getDomainCode())));
            if (!reachable) {
                throw new BusinessException("ORDER_MAIN_FLOW transition graph must connect domain: " + requiredDomain);
            }
        }
    }

    private int domainIndex(String domainCode) {
        String normalized = normalizeCode(domainCode);
        for (int i = 0; i < REQUIRED_ORDER_MAIN_CHAIN.size(); i++) {
            if (REQUIRED_ORDER_MAIN_CHAIN.get(i).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private void validateTransitionReferences(List<SystemFlowDtos.NodeResponse> nodes,
                                              List<SystemFlowDtos.TransitionResponse> transitions) {
        Set<String> nodeCodes = new LinkedHashSet<>();
        for (SystemFlowDtos.NodeResponse node : nodes) {
            nodeCodes.add(normalizeCode(node.getNodeCode()));
        }
        for (SystemFlowDtos.TransitionResponse transition : transitions) {
            if (!nodeCodes.contains(normalizeCode(transition.getFromNodeCode()))
                    || !nodeCodes.contains(normalizeCode(transition.getToNodeCode()))) {
                throw new BusinessException("flow transition references missing node: " + transition.getActionCode());
            }
        }
    }

    private void validateUniqueActions(List<SystemFlowDtos.TransitionResponse> transitions) {
        Set<String> actionCodes = new LinkedHashSet<>();
        for (SystemFlowDtos.TransitionResponse transition : transitions) {
            String actionCode = normalizeCode(transition.getActionCode());
            if (!StringUtils.hasText(actionCode) || !actionCodes.add(actionCode)) {
                throw new BusinessException("flow transition actionCode is required and cannot duplicate");
            }
        }
    }

    private void validateTriggerTargets(List<SystemFlowDtos.NodeResponse> nodes,
                                        List<SystemFlowDtos.TriggerResponse> triggers) {
        Set<String> nodeCodes = new LinkedHashSet<>();
        for (SystemFlowDtos.NodeResponse node : nodes) {
            nodeCodes.add(normalizeCode(node.getNodeCode()));
        }
        if (triggers == null) {
            return;
        }
        for (SystemFlowDtos.TriggerResponse trigger : triggers) {
            String nodeCode = normalizeCode(trigger.getNodeCode());
            if (!nodeCodes.contains(nodeCode)) {
                throw new BusinessException("flow trigger references missing node: " + trigger.getTriggerName());
            }
        }
        validateSafeTriggerTargets(triggers);
    }

    private void validateSafeTriggerTargets(List<SystemFlowDtos.TriggerResponse> triggers) {
        if (triggers == null) {
            return;
        }
        for (SystemFlowDtos.TriggerResponse trigger : triggers) {
            String target = String.valueOf(trigger.getTargetCode() == null ? "" : trigger.getTargetCode()).trim();
            String config = String.valueOf(trigger.getConfigJson() == null ? "" : trigger.getConfigJson()).trim();
            if (!StringUtils.hasText(target)) {
                continue;
            }
            String normalizedTarget = normalizeCode(target);
            String executionMode = normalizeCode(trigger.getExecutionMode());
            String lowerTarget = target.toLowerCase(Locale.ROOT);
            String lowerConfig = config.toLowerCase(Locale.ROOT);
            if (!target.equals(normalizedTarget)
                    || !normalizedTarget.matches("[A-Z0-9_]+")
                    || (!SAFE_TRIGGER_TARGET_CODES.contains(normalizedTarget) && !normalizedTarget.endsWith("_METADATA"))
                    || (StringUtils.hasText(executionMode) && !SAFE_TRIGGER_EXECUTION_MODES.contains(executionMode))
                    || lowerTarget.startsWith("http://")
                    || lowerTarget.startsWith("https://")
                    || lowerTarget.contains(":")
                    || lowerTarget.contains(".")
                    || lowerTarget.contains("/")
                    || lowerTarget.contains("\\")
                    || lowerTarget.contains("select ")
                    || lowerTarget.contains("update ")
                    || lowerTarget.contains("delete ")
                    || lowerTarget.contains("insert ")
                    || lowerConfig.contains("\"url\"")
                    || lowerConfig.contains("http://")
                    || lowerConfig.contains("https://")
                    || lowerConfig.contains("script")
                    || lowerConfig.contains("select ")
                    || lowerConfig.contains("update ")
                    || lowerConfig.contains("delete ")
                    || lowerConfig.contains("insert ")) {
                throw new BusinessException("system flow V1 trigger target must be metadata code only, not executable target: " + target);
            }
        }
    }
}
