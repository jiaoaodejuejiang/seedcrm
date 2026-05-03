package com.seedcrm.crm.systemconfig.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.support.DistributionOrderTypeMappingResolver;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final String SYSTEM_BASE_URL_KEY = "system.domain.systemBaseUrl";
    private static final String API_BASE_URL_KEY = "system.domain.apiBaseUrl";
    private static final String DEFAULT_SYSTEM_BASE_URL = "http://127.0.0.1:8003";
    private static final String DEFAULT_API_BASE_URL = "http://127.0.0.1:8004";
    private static final String MASKED_VALUE = "******";
    private static final String DRAFT_STATUS_DRAFT = "DRAFT";
    private static final String DRAFT_STATUS_PUBLISHING = "PUBLISHING";
    private static final String DRAFT_STATUS_PUBLISHED = "PUBLISHED";
    private static final String DRAFT_STATUS_DISCARDED = "DISCARDED";
    private static final String DRAFT_SOURCE_MANUAL = "MANUAL";
    private static final String DRAFT_SOURCE_ROLLBACK = "ROLLBACK";
    private static final String PUBLISH_STATUS_SUCCESS = "SUCCESS";
    private static final String PUBLISH_STATUS_FAILED = "FAILED";
    private static final String RUNTIME_EVENT_STATUS_PENDING = "PENDING";
    private static final String RUNTIME_EVENT_STATUS_RETRYING = "RETRYING";
    private static final String RUNTIME_EVENT_STATUS_SUCCESS = "SUCCESS";
    private static final String RUNTIME_EVENT_STATUS_FAILED = "FAILED";
    private static final String RUNTIME_EVENT_STATUS_TERMINATED = "TERMINATED";
    private static final int DEFAULT_RUNTIME_EVENT_MAX_RETRY = 3;
    private static final int RUNTIME_EVENT_LOCK_TIMEOUT_MINUTES = 5;
    private static final String NULL_HASH_MARKER = "<NULL>";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_RUNTIME_EVENT_TYPES = Set.of(
            "CONFIG_PUBLISHED",
            "RUNTIME_REFRESH",
            "CACHE_EVICT");
    private static final Set<String> SUPPORTED_RUNTIME_MODULES = Set.of(
            "CLUE",
            "FINANCE",
            "PLANORDER",
            "SCHEDULER",
            "STORE_SERVICE",
            "SYSTEM_CONFIG",
            "SYSTEM_FLOW",
            "SYSTEM_SETTING",
            "WECOM");
    private static final List<String> ALLOWED_CONFIG_PREFIXES = List.of(
            "system.domain.",
            "workflow.",
            "deposit.",
            "amount.",
            "clue.",
            "form_designer.",
            "distribution.",
            "scheduler.",
            "douyin.",
            "wecom.",
            "payment.");
    private static final List<String> SENSITIVE_KEY_MARKERS = List.of(
            "secret",
            "token",
            "password",
            "private_key",
            "privatekey",
            "client_secret",
            "clientsecret",
            "api_key",
            "apikey",
            "api_v3_key",
            "sign_key",
            "signkey",
            "signature_key");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public SystemConfigServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTransactionTemplate = null;
    }

    @Autowired
    public SystemConfigServiceImpl(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public List<SystemConfigDtos.ConfigResponse> listConfigs(String prefix) {
        if (StringUtils.hasText(prefix)) {
            return jdbcTemplate.query(selectSql() + """
                    WHERE config_key LIKE ?
                    ORDER BY config_key ASC, scope_type ASC, scope_id ASC
                    """, (rs, rowNum) -> mapConfig(rs), prefix.trim() + "%");
        }
        return jdbcTemplate.query(selectSql() + """
                ORDER BY config_key ASC, scope_type ASC, scope_id ASC
                """, (rs, rowNum) -> mapConfig(rs));
    }

    @Override
    public List<SystemConfigDtos.ChangeLogResponse> listChangeLogs(String prefix, String configKey, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT id, config_key, scope_type, scope_id, before_value, after_value,
                       actor_role_code, actor_user_id, summary, change_type, risk_level,
                       impact_modules_json, create_time
                FROM system_config_change_log
                """);
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(configKey)) {
            sql.append("WHERE config_key = ?\n");
            args.add(configKey.trim());
        } else if (StringUtils.hasText(prefix)) {
            sql.append("WHERE config_key LIKE ?\n");
            args.add(prefix.trim() + "%");
        }
        sql.append("ORDER BY create_time DESC, id DESC LIMIT ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapChangeLog(rs), args.toArray());
    }

    @Override
    public List<SystemConfigDtos.DraftResponse> listDrafts(String status, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, draft_no, status, source_type, source_change_log_id, risk_level,
                       impact_modules_json, created_by_role_code, created_by_user_id, summary,
                       create_time, update_time, published_at, discarded_at,
                       last_dry_run_hash, last_dry_run_status, last_dry_run_at,
                       last_dry_run_by_role_code, last_dry_run_by_user_id
                FROM system_config_draft
                """);
        if (StringUtils.hasText(status)) {
            sql.append("WHERE status = ?\n");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append("ORDER BY create_time DESC, id DESC LIMIT ?");
        args.add(safeLimit);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapDraft(rs, true), args.toArray());
    }

    @Override
    public SystemConfigDtos.DraftResponse getDraft(String draftNo) {
        return toDraftResponse(loadDraft(draftNo), true);
    }

    @Override
    public List<SystemConfigDtos.CapabilityResponse> listCapabilities() {
        return jdbcTemplate.query("""
                SELECT id, capability_code, config_key_pattern, owner_module, value_type,
                       scope_type_allowed_json, risk_level, sensitive_flag, validator_code,
                       runtime_reload_strategy, enabled, create_time, update_time
                FROM system_config_capability
                WHERE enabled = 1
                ORDER BY owner_module ASC, capability_code ASC
                """, (rs, rowNum) -> mapCapability(rs));
    }

    @Override
    public SystemConfigDtos.RuntimeOverviewResponse getRuntimeOverview() {
        SystemConfigDtos.RuntimeOverviewResponse response = new SystemConfigDtos.RuntimeOverviewResponse();
        response.setCapabilityCount(countOf("system_config_capability", "enabled = 1"));
        response.setDraftCount(countOf("system_config_draft", "status = 'DRAFT'"));
        response.setHighRiskDraftCount(countOf("system_config_draft", "status = 'DRAFT' AND risk_level = 'HIGH'"));
        response.setPublishSuccessCount(countOf("system_config_publish_record", "status = 'SUCCESS'"));
        response.setPublishFailedCount(countOf("system_config_publish_record", "status = 'FAILED'"));
        response.setRuntimeEventPendingCount(countOf("system_config_runtime_event", "status = 'PENDING'"));
        response.setRuntimeEventRetryingCount(countOf("system_config_runtime_event", "status = 'RETRYING'"));
        response.setRuntimeEventSuccessCount(countOf("system_config_runtime_event", "status = 'SUCCESS'"));
        response.setRuntimeEventFailedCount(countOf("system_config_runtime_event", "status = 'FAILED'"));
        response.setRuntimeEventTerminatedCount(countOf("system_config_runtime_event", "status = 'TERMINATED'"));
        List<LocalDateTime> runtimeRows = jdbcTemplate.query("""
                SELECT handled_at
                FROM system_config_runtime_event
                WHERE status = 'SUCCESS' AND handled_at IS NOT NULL
                ORDER BY handled_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> toLocalDateTime(rs.getTimestamp("handled_at")));
        response.setLatestRuntimeHandledAt(runtimeRows.isEmpty() ? null : runtimeRows.get(0));
        List<LocalDateTime> rows = jdbcTemplate.query("""
                SELECT published_at
                FROM system_config_publish_record
                WHERE status = 'SUCCESS'
                ORDER BY published_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> toLocalDateTime(rs.getTimestamp("published_at")));
        response.setLastPublishedAt(rows.isEmpty() ? null : rows.get(0));
        return response;
    }

    @Override
    public SystemConfigDtos.ValidationResponse validateDraft(String draftNo) {
        RawDraft draft = loadDraft(draftNo);
        List<RawDraftItem> items = loadDraftItems(draft.draftNo());
        SystemConfigDtos.ValidationResponse response = new SystemConfigDtos.ValidationResponse();
        response.setDraftNo(draft.draftNo());
        response.setRiskLevel(draft.riskLevel());
        response.setImpactModules(fromJsonList(draft.impactModulesJson()));
        response.setSummary(draft.summary());
        List<SystemConfigDtos.ValidationItemResponse> validationItems = new ArrayList<>();
        for (RawDraftItem item : items) {
            validationItems.add(validateDraftItem(item));
        }
        response.setItems(validationItems);
        response.setValid(validationItems.stream().noneMatch(item -> "BLOCK".equals(item.getStatus())));
        return response;
    }

    @Override
    public SystemConfigDtos.DryRunResponse dryRunDraft(String draftNo) {
        return dryRunDraft(draftNo, null);
    }

    @Override
    @Transactional
    public SystemConfigDtos.DryRunResponse dryRunDraft(String draftNo, PermissionRequestContext context) {
        RawDraft draft = loadDraft(draftNo);
        if (!DRAFT_STATUS_DRAFT.equals(draft.status())) {
            throw new BusinessException("只有草稿状态可以执行发布预检查");
        }
        SystemConfigDtos.ValidationResponse validation = validateDraft(draftNo);
        SystemConfigDtos.DryRunResponse response = new SystemConfigDtos.DryRunResponse();
        response.setDraftNo(validation.getDraftNo());
        response.setRunnable(Boolean.TRUE.equals(validation.getValid()));
        response.setItems(validation.getItems());
        if (!Boolean.TRUE.equals(validation.getValid())) {
            response.setSummary("发布预检查被校验阻断");
            persistDryRunResult(draft, response, context);
            return response;
        }
        List<String> events = new ArrayList<>();
        for (String module : ownerModulesForDraft(validation.getDraftNo())) {
            events.add("配置发布事件 -> " + moduleLabel(module));
            events.add("运行态刷新事件 -> " + moduleLabel(module));
        }
        response.setRuntimeEvents(events);
        response.setSummary(events.isEmpty()
                ? "无需记录运行态刷新事件"
                : "发布预检查通过；发布后会记录运行态刷新事件");
        persistDryRunResult(draft, response, context);
        return response;
    }

    @Override
    public List<SystemConfigDtos.PublishRecordResponse> listPublishRecords(Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query("""
                SELECT id, publish_no, draft_no, status, risk_level, impact_modules_json,
                       before_hash, after_hash, before_snapshot_masked_json, after_snapshot_masked_json,
                       validation_result_json, failure_reason, published_by_role_code, published_by_user_id, published_at
                FROM system_config_publish_record
                ORDER BY published_at DESC, id DESC
                LIMIT ?
                """, (rs, rowNum) -> mapPublishRecord(rs, false), safeLimit);
    }

    @Override
    public SystemConfigDtos.PublishRecordResponse getPublishRecord(String publishNo) {
        RawPublishRecord record = loadPublishRecord(publishNo);
        return toPublishRecordResponse(record, true);
    }

    @Override
    @Transactional
    public SystemConfigDtos.PublishRecordResponse refreshPublishRuntime(String publishNo, PermissionRequestContext context) {
        RawPublishRecord record = loadPublishRecord(publishNo);
        if (!PUBLISH_STATUS_SUCCESS.equals(record.status())) {
            throw new BusinessException("只有发布成功的批次可以记录运行态刷新事件");
        }
        List<String> modules = fromJsonList(record.impactModulesJson());
        if (modules.isEmpty()) {
            modules = ownerModulesForDraft(record.draftNo());
        }
        for (String module : modules) {
            insertRuntimeEvent(record.publishNo(), module, "CACHE_EVICT", "PENDING",
                    "手工触发运行态刷新，操作角色：" + (context == null ? "SYSTEM" : context.getRoleCode()), null);
        }
        return getPublishRecord(record.publishNo());
    }

    @Override
    @Transactional
    public SystemConfigDtos.PublishRecordResponse processPublishRuntimeEvents(String publishNo,
                                                                              PermissionRequestContext context) {
        RawPublishRecord record = loadPublishRecord(publishNo);
        if (!PUBLISH_STATUS_SUCCESS.equals(record.status())) {
            throw new BusinessException("只有发布成功的批次可以处理运行态刷新事件");
        }
        for (RawRuntimeEvent event : loadRuntimeEventsForManualProcess(record.publishNo())) {
            processRuntimeEvent(event, context, true);
        }
        return getPublishRecord(record.publishNo());
    }

    @Override
    @Transactional
    public List<SystemConfigDtos.RuntimeEventResponse> processDueRuntimeEvents(Integer limit) {
        List<SystemConfigDtos.RuntimeEventResponse> processed = new ArrayList<>();
        for (RawRuntimeEvent event : loadDueRuntimeEvents(limit)) {
            processRuntimeEvent(event, null, false);
            processed.add(toRuntimeEventResponse(loadRuntimeEventRecord(event.id())));
        }
        return processed;
    }

    @Override
    @Transactional
    public SystemConfigDtos.DraftResponse createDraft(SystemConfigDtos.SaveConfigRequest request,
                                                      PermissionRequestContext context) {
        return createDraftInternal(request, context, DRAFT_SOURCE_MANUAL, null);
    }

    @Override
    @Transactional
    public SystemConfigDtos.DraftResponse publishDraft(String draftNo, PermissionRequestContext context) {
        RawDraft draft = loadDraft(draftNo);
        if (!DRAFT_STATUS_DRAFT.equals(draft.status())) {
            throw new BusinessException("只有草稿状态可以发布");
        }
        List<RawDraftItem> items = loadDraftItems(draft.draftNo());
        if (items.isEmpty()) {
            throw new BusinessException("草稿没有配置明细，不能发布");
        }
        SystemConfigDtos.ValidationResponse validation = validateDraft(draft.draftNo());
        if (!Boolean.TRUE.equals(validation.getValid())) {
            recordFailedPublish(nextPublishNo(), draft, items, validation,
                    "草稿校验存在阻断项，发布已停止", context);
            throw new BusinessException("草稿校验未通过，请先处理阻断项后再发布");
        }
        String currentDraftHash = draftContentHash(items);
        if (!"PASS".equalsIgnoreCase(draft.lastDryRunStatus())
                || !Objects.equals(currentDraftHash, draft.lastDryRunHash())) {
            recordFailedPublish(nextPublishNo(), draft, items, validation,
                    "发布预检查未通过或已失效，发布已停止", context);
            throw new BusinessException("发布预检查未通过或已失效，请重新执行发布预检查后再发布");
        }
        for (RawDraftItem item : items) {
            String currentValue = findValue(item.configKey(), item.scopeType(), item.scopeId());
            String currentHash = hashValue(currentValue);
            if (!Objects.equals(currentHash, item.baseCurrentValueHash())) {
                recordFailedPublish(nextPublishNo(), draft, items, validation,
                        "草稿创建后运行中配置已变化，发布已停止", context);
                throw new BusinessException("草稿创建后运行中配置已变化，请重新预览并生成草稿");
            }
        }
        int claimedRows = jdbcTemplate.update("""
                UPDATE system_config_draft
                SET status = ?, update_time = ?
                WHERE draft_no = ? AND status = ?
                """, DRAFT_STATUS_PUBLISHING, LocalDateTime.now(), draft.draftNo(), DRAFT_STATUS_DRAFT);
        if (claimedRows != 1) {
            throw new BusinessException("草稿正在发布或状态已变化，请刷新后再试");
        }
        String publishNo = nextPublishNo();
        try {
            for (RawDraftItem item : items) {
                SystemConfigDtos.SaveConfigRequest saveRequest = new SystemConfigDtos.SaveConfigRequest();
                saveRequest.setConfigKey(item.configKey());
                saveRequest.setConfigValue(item.afterValue());
                saveRequest.setValueType(item.valueType());
                saveRequest.setScopeType(item.scopeType());
                saveRequest.setScopeId(item.scopeId());
                saveRequest.setEnabled(item.enabled());
                saveRequest.setDescription(item.description());
                saveRequest.setSummary(buildPublishSummary(draft, item));
                saveConfig(saveRequest, context);
            }
            jdbcTemplate.update("""
                    UPDATE system_config_draft
                    SET status = ?, published_at = ?, update_time = ?
                    WHERE draft_no = ? AND status = ?
                    """, DRAFT_STATUS_PUBLISHED, LocalDateTime.now(), LocalDateTime.now(),
                    draft.draftNo(), DRAFT_STATUS_PUBLISHING);
            insertPublishRecord(publishNo, draft, items, PUBLISH_STATUS_SUCCESS, validation, null, context);
            for (String module : ownerModulesForDraft(draft.draftNo())) {
                insertRuntimeEvent(publishNo, module, "CONFIG_PUBLISHED", "PENDING",
                        "配置已发布，等待模块感知草稿 " + draft.draftNo(), null);
                insertRuntimeEvent(publishNo, module, "RUNTIME_REFRESH", "PENDING",
                        "等待刷新模块运行态配置：" + module, null);
            }
        } catch (RuntimeException exception) {
            recordFailedPublish(publishNo, draft, items, validation,
                    "发布写入失败：" + exception.getMessage(), context);
            throw exception;
        }
        return getDraft(draft.draftNo());
    }

    @Override
    @Transactional
    public SystemConfigDtos.DraftResponse discardDraft(String draftNo, PermissionRequestContext context) {
        RawDraft draft = loadDraft(draftNo);
        if (!DRAFT_STATUS_DRAFT.equals(draft.status())) {
            throw new BusinessException("只有草稿状态可以作废");
        }
        int updatedRows = jdbcTemplate.update("""
                UPDATE system_config_draft
                SET status = ?, discarded_at = ?, update_time = ?
                WHERE draft_no = ? AND status = ?
                """, DRAFT_STATUS_DISCARDED, LocalDateTime.now(), LocalDateTime.now(),
                draft.draftNo(), DRAFT_STATUS_DRAFT);
        if (updatedRows != 1) {
            throw new BusinessException("草稿状态已变化，请刷新后再试");
        }
        return getDraft(draft.draftNo());
    }

    @Override
    public SystemConfigDtos.ConfigPreviewResponse rollbackPreview(Long changeLogId) {
        ChangeLogRecord changeLog = loadChangeLog(changeLogId);
        SystemConfigDtos.SaveConfigRequest request = rollbackRequest(changeLog);
        return previewConfig(request);
    }

    @Override
    @Transactional
    public SystemConfigDtos.DraftResponse createRollbackDraft(Long changeLogId, PermissionRequestContext context) {
        ChangeLogRecord changeLog = loadChangeLog(changeLogId);
        SystemConfigDtos.SaveConfigRequest request = rollbackRequest(changeLog);
        return createDraftInternal(request, context, DRAFT_SOURCE_ROLLBACK, changeLog.id());
    }

    @Override
    public SystemConfigDtos.ConfigPreviewResponse previewConfig(SystemConfigDtos.SaveConfigRequest request) {
        NormalizedConfigInput input = normalizeConfigRequest(request);
        String beforeValue = findValue(input.key(), input.scopeType(), input.scopeId());
        boolean sensitive = isSensitiveKey(input.key());
        SystemConfigDtos.ConfigPreviewResponse response = new SystemConfigDtos.ConfigPreviewResponse();
        response.setConfigKey(input.key());
        response.setScopeType(input.scopeType());
        response.setScopeId(input.scopeId());
        response.setBeforeValue(maskIfSensitive(input.key(), beforeValue));
        response.setAfterValue(maskIfSensitive(input.key(), input.configValue()));
        response.setValueType(input.valueType());
        response.setEnabled(input.enabled());
        response.setSensitive(sensitive);
        response.setChanged(!Objects.equals(beforeValue, input.configValue()));
        response.setChangeType(resolveChangeType(beforeValue, input.configValue()));
        response.setRiskLevel(resolveRiskLevel(input.key()));
        response.setImpactModules(resolveImpactModules(input.key()));
        response.setWarnings(resolvePreviewWarnings(input.key(), beforeValue, input.configValue(), response.getRiskLevel(), sensitive));
        response.setValidationPassed(true);
        response.setValidationMessage("预览校验通过");
        return response;
    }

    @Override
    @Transactional
    public SystemConfigDtos.ConfigResponse saveLegacyConfig(SystemConfigDtos.SaveConfigRequest request,
                                                            PermissionRequestContext context) {
        NormalizedConfigInput input = normalizeConfigRequest(request);
        assertLegacyDirectSaveAllowed(input.key());
        return saveConfig(request, context);
    }

    @Override
    @Transactional
    public SystemConfigDtos.ConfigResponse saveConfig(SystemConfigDtos.SaveConfigRequest request, PermissionRequestContext context) {
        NormalizedConfigInput input = normalizeConfigRequest(request);
        String beforeValue = findValue(input.key(), input.scopeType(), input.scopeId());
        LocalDateTime now = LocalDateTime.now();
        String changeType = resolveChangeType(beforeValue, input.configValue());
        String riskLevel = resolveRiskLevel(input.key());
        List<String> impactModules = resolveImpactModules(input.key());
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_config
                WHERE config_key = ? AND scope_type = ? AND scope_id = ?
                """, Integer.class, input.key(), input.scopeType(), input.scopeId());
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE system_config
                    SET config_value = ?, value_type = ?, enabled = ?, description = ?, update_time = ?
                    WHERE config_key = ? AND scope_type = ? AND scope_id = ?
                    """, input.configValue(), input.valueType(), input.enabled(),
                    request.getDescription(), now, input.key(), input.scopeType(), input.scopeId());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, input.key(), input.configValue(), input.valueType(), input.scopeType(), input.scopeId(),
                    input.enabled(), request.getDescription(), now, now);
        }
        jdbcTemplate.update("""
                INSERT INTO system_config_change_log(
                    config_key, scope_type, scope_id, before_value, after_value, actor_role_code, actor_user_id,
                    summary, change_type, risk_level, impact_modules_json, create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, input.key(), input.scopeType(), input.scopeId(), beforeValue, input.configValue(),
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                StringUtils.hasText(request.getSummary()) ? request.getSummary().trim() : "更新系统配置",
                changeType,
                riskLevel,
                toJson(impactModules),
                now);
        return listConfigs(input.key()).stream()
                .filter(item -> input.key().equals(item.getConfigKey()))
                .filter(item -> input.scopeType().equals(item.getScopeType()))
                .filter(item -> input.scopeId().equals(item.getScopeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("系统配置保存失败"));
    }

    private SystemConfigDtos.DraftResponse createDraftInternal(SystemConfigDtos.SaveConfigRequest request,
                                                               PermissionRequestContext context,
                                                               String sourceType,
                                                               Long sourceChangeLogId) {
        NormalizedConfigInput input = normalizeConfigRequest(request);
        String beforeValue = findValue(input.key(), input.scopeType(), input.scopeId());
        if (Objects.equals(beforeValue, input.configValue())) {
            throw new BusinessException("配置值没有变化，无需生成草稿");
        }
        SystemConfigDtos.ConfigPreviewResponse preview = previewConfig(request);
        String draftNo = nextDraftNo();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO system_config_draft(
                    draft_no, status, source_type, source_change_log_id, risk_level, impact_modules_json,
                    created_by_role_code, created_by_user_id, summary, create_time, update_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, draftNo, DRAFT_STATUS_DRAFT, sourceType, sourceChangeLogId,
                preview.getRiskLevel(), toJson(preview.getImpactModules()),
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                StringUtils.hasText(request.getSummary()) ? request.getSummary().trim() : "创建系统配置草稿",
                now, now);
        jdbcTemplate.update("""
                INSERT INTO system_config_draft_item(
                    draft_no, config_key, scope_type, scope_id, value_type, before_value, after_value,
                    base_current_value_hash, enabled, description, change_type, sensitive_flag,
                    validation_status, validation_message, create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, draftNo, input.key(), input.scopeType(), input.scopeId(), input.valueType(),
                beforeValue, input.configValue(), hashValue(beforeValue), input.enabled(),
                request.getDescription(), preview.getChangeType(), Boolean.TRUE.equals(preview.getSensitive()) ? 1 : 0,
                Boolean.TRUE.equals(preview.getValidationPassed()) ? "PASS" : "FAIL",
                preview.getValidationMessage(), now);
        return getDraft(draftNo);
    }

    @Override
    public SystemConfigDtos.DomainSettingsResponse getDomainSettings() {
        String systemBaseUrl = normalizeBaseUrl(getString(SYSTEM_BASE_URL_KEY, DEFAULT_SYSTEM_BASE_URL));
        String apiBaseUrl = normalizeBaseUrl(getString(API_BASE_URL_KEY, DEFAULT_API_BASE_URL));
        return domainResponse(systemBaseUrl, apiBaseUrl);
    }

    @Override
    @Transactional
    public SystemConfigDtos.DomainSettingsResponse saveDomainSettings(SystemConfigDtos.SaveDomainSettingsRequest request,
                                                                      PermissionRequestContext context) {
        if (request == null) {
            throw new BusinessException("域名配置不能为空");
        }
        String systemBaseUrl = normalizeBaseUrl(request.getSystemBaseUrl());
        String apiBaseUrl = normalizeBaseUrl(request.getApiBaseUrl());
        validateBaseUrl(systemBaseUrl, "系统基础域名");
        validateBaseUrl(apiBaseUrl, "API 域名");
        saveConfig(configRequest(SYSTEM_BASE_URL_KEY, systemBaseUrl, "系统后台访问基础域名"), context);
        saveConfig(configRequest(API_BASE_URL_KEY, apiBaseUrl, "系统 API 基础域名"), context);
        return domainResponse(systemBaseUrl, apiBaseUrl);
    }

    @Override
    public boolean getBoolean(String configKey, boolean defaultValue) {
        String value = findValue(configKey, "GLOBAL", "GLOBAL");
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    @Override
    public String getString(String configKey, String defaultValue) {
        String value = findValue(configKey, "GLOBAL", "GLOBAL");
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String selectSql() {
        return """
                SELECT id, config_key, config_value, value_type, scope_type, scope_id, enabled,
                       description, create_time, update_time
                FROM system_config
                """;
    }

    private String findValue(String key, String scopeType, String scopeId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT config_value
                FROM system_config
                WHERE config_key = ? AND scope_type = ? AND scope_id = ? AND enabled = 1
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("config_value"), key, scopeType, scopeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SystemConfigDtos.ConfigResponse mapConfig(ResultSet rs) throws SQLException {
        SystemConfigDtos.ConfigResponse item = new SystemConfigDtos.ConfigResponse();
        item.setId(rs.getLong("id"));
        String key = rs.getString("config_key");
        item.setConfigKey(key);
        item.setSensitive(isSensitiveKey(key));
        item.setConfigValue(maskIfSensitive(key, rs.getString("config_value")));
        item.setValueType(rs.getString("value_type"));
        item.setScopeType(rs.getString("scope_type"));
        item.setScopeId(rs.getString("scope_id"));
        item.setEnabled(rs.getInt("enabled"));
        item.setDescription(rs.getString("description"));
        item.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        item.setUpdateTime(rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
        return item;
    }

    private SystemConfigDtos.ChangeLogResponse mapChangeLog(ResultSet rs) throws SQLException {
        SystemConfigDtos.ChangeLogResponse item = new SystemConfigDtos.ChangeLogResponse();
        item.setId(rs.getLong("id"));
        String key = rs.getString("config_key");
        String beforeValue = rs.getString("before_value");
        String afterValue = rs.getString("after_value");
        item.setConfigKey(key);
        item.setScopeType(rs.getString("scope_type"));
        item.setScopeId(rs.getString("scope_id"));
        item.setSensitive(isSensitiveKey(key));
        item.setBeforeValue(maskIfSensitive(key, beforeValue));
        item.setAfterValue(maskIfSensitive(key, afterValue));
        item.setChangeType(firstNonBlank(rs.getString("change_type"), resolveChangeType(beforeValue, afterValue)));
        item.setRiskLevel(firstNonBlank(rs.getString("risk_level"), resolveRiskLevel(key)));
        List<String> storedImpactModules = fromJsonList(rs.getString("impact_modules_json"));
        item.setImpactModules(storedImpactModules.isEmpty() ? resolveImpactModules(key) : storedImpactModules);
        item.setActorRoleCode(rs.getString("actor_role_code"));
        item.setActorUserId(rs.getObject("actor_user_id") == null ? null : rs.getLong("actor_user_id"));
        item.setSummary(rs.getString("summary"));
        item.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        return item;
    }

    private SystemConfigDtos.CapabilityResponse mapCapability(ResultSet rs) throws SQLException {
        SystemConfigDtos.CapabilityResponse item = new SystemConfigDtos.CapabilityResponse();
        item.setId(rs.getLong("id"));
        item.setCapabilityCode(rs.getString("capability_code"));
        item.setConfigKeyPattern(rs.getString("config_key_pattern"));
        item.setOwnerModule(rs.getString("owner_module"));
        item.setValueType(rs.getString("value_type"));
        item.setScopeTypes(fromJsonList(rs.getString("scope_type_allowed_json")));
        item.setRiskLevel(rs.getString("risk_level"));
        item.setSensitive(rs.getInt("sensitive_flag") == 1);
        item.setValidatorCode(rs.getString("validator_code"));
        item.setRuntimeReloadStrategy(rs.getString("runtime_reload_strategy"));
        item.setEnabled(rs.getInt("enabled"));
        item.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        item.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
        return item;
    }

    private SystemConfigDtos.PublishRecordResponse mapPublishRecord(ResultSet rs, boolean includeEvents) throws SQLException {
        RawPublishRecord record = new RawPublishRecord(
                rs.getLong("id"),
                rs.getString("publish_no"),
                rs.getString("draft_no"),
                rs.getString("status"),
                rs.getString("risk_level"),
                rs.getString("impact_modules_json"),
                rs.getString("before_hash"),
                rs.getString("after_hash"),
                rs.getString("before_snapshot_masked_json"),
                rs.getString("after_snapshot_masked_json"),
                rs.getString("validation_result_json"),
                rs.getString("failure_reason"),
                rs.getString("published_by_role_code"),
                getLongOrNull(rs, "published_by_user_id"),
                toLocalDateTime(rs.getTimestamp("published_at")));
        return toPublishRecordResponse(record, includeEvents);
    }

    private SystemConfigDtos.RuntimeEventResponse mapRuntimeEvent(ResultSet rs) throws SQLException {
        return toRuntimeEventResponse(mapRawRuntimeEvent(rs));
    }

    private RawRuntimeEvent mapRawRuntimeEvent(ResultSet rs) throws SQLException {
        return new RawRuntimeEvent(
                rs.getLong("id"),
                rs.getString("publish_no"),
                rs.getString("module_code"),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getString("payload_json"),
                rs.getString("error_message"),
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("handled_at")),
                rs.getInt("retry_count"),
                rs.getInt("max_retry_count"),
                toLocalDateTime(rs.getTimestamp("next_retry_at")),
                rs.getString("locked_by"),
                toLocalDateTime(rs.getTimestamp("locked_at")),
                toLocalDateTime(rs.getTimestamp("last_attempt_at")));
    }

    private SystemConfigDtos.RuntimeEventResponse toRuntimeEventResponse(RawRuntimeEvent event) {
        SystemConfigDtos.RuntimeEventResponse item = new SystemConfigDtos.RuntimeEventResponse();
        item.setId(event.id());
        item.setPublishNo(event.publishNo());
        item.setModuleCode(event.moduleCode());
        item.setEventType(event.eventType());
        item.setStatus(event.status());
        item.setPayloadJson(event.payloadJson());
        item.setErrorMessage(event.errorMessage());
        item.setRetryCount(event.retryCount());
        item.setMaxRetryCount(event.maxRetryCount());
        item.setNextRetryAt(event.nextRetryAt());
        item.setLockedBy(event.lockedBy());
        item.setLockedAt(event.lockedAt());
        item.setLastAttemptAt(event.lastAttemptAt());
        item.setCreateTime(event.createTime());
        item.setHandledAt(event.handledAt());
        return item;
    }

    private SystemConfigDtos.DraftResponse mapDraft(ResultSet rs, boolean includeItems) throws SQLException {
        RawDraft draft = new RawDraft(
                rs.getLong("id"),
                rs.getString("draft_no"),
                rs.getString("status"),
                rs.getString("source_type"),
                getLongOrNull(rs, "source_change_log_id"),
                rs.getString("risk_level"),
                rs.getString("impact_modules_json"),
                rs.getString("created_by_role_code"),
                getLongOrNull(rs, "created_by_user_id"),
                rs.getString("summary"),
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time")),
                toLocalDateTime(rs.getTimestamp("published_at")),
                toLocalDateTime(rs.getTimestamp("discarded_at")),
                rs.getString("last_dry_run_hash"),
                rs.getString("last_dry_run_status"),
                toLocalDateTime(rs.getTimestamp("last_dry_run_at")),
                rs.getString("last_dry_run_by_role_code"),
                getLongOrNull(rs, "last_dry_run_by_user_id"));
        return toDraftResponse(draft, includeItems);
    }

    private SystemConfigDtos.DraftResponse toDraftResponse(RawDraft draft, boolean includeItems) {
        SystemConfigDtos.DraftResponse response = new SystemConfigDtos.DraftResponse();
        response.setId(draft.id());
        response.setDraftNo(draft.draftNo());
        response.setStatus(draft.status());
        response.setSourceType(draft.sourceType());
        response.setSourceChangeLogId(draft.sourceChangeLogId());
        response.setRiskLevel(draft.riskLevel());
        response.setImpactModules(fromJsonList(draft.impactModulesJson()));
        response.setCreatedByRoleCode(draft.createdByRoleCode());
        response.setCreatedByUserId(draft.createdByUserId());
        response.setSummary(draft.summary());
        response.setCreateTime(draft.createTime());
        response.setUpdateTime(draft.updateTime());
        response.setPublishedAt(draft.publishedAt());
        response.setDiscardedAt(draft.discardedAt());
        response.setLastDryRunStatus(draft.lastDryRunStatus());
        response.setLastDryRunAt(draft.lastDryRunAt());
        response.setLastDryRunByRoleCode(draft.lastDryRunByRoleCode());
        response.setLastDryRunByUserId(draft.lastDryRunByUserId());
        if (includeItems) {
            response.setItems(loadDraftItems(draft.draftNo()).stream()
                    .map(this::toDraftItemResponse)
                    .toList());
        }
        return response;
    }

    private SystemConfigDtos.DraftItemResponse toDraftItemResponse(RawDraftItem item) {
        SystemConfigDtos.DraftItemResponse response = new SystemConfigDtos.DraftItemResponse();
        response.setId(item.id());
        response.setDraftNo(item.draftNo());
        response.setConfigKey(item.configKey());
        response.setScopeType(item.scopeType());
        response.setScopeId(item.scopeId());
        response.setValueType(item.valueType());
        response.setBeforeValue(maskIfSensitive(item.configKey(), item.beforeValue()));
        response.setAfterValue(maskIfSensitive(item.configKey(), item.afterValue()));
        response.setBaseCurrentValueHash(item.baseCurrentValueHash());
        response.setEnabled(item.enabled());
        response.setDescription(item.description());
        response.setChangeType(item.changeType());
        response.setSensitive(item.sensitive());
        response.setValidationStatus(item.validationStatus());
        response.setValidationMessage(item.validationMessage());
        return response;
    }

    private RawDraft loadDraft(String draftNo) {
        if (!StringUtils.hasText(draftNo)) {
            throw new BusinessException("草稿号不能为空");
        }
        List<RawDraft> rows = jdbcTemplate.query("""
                SELECT id, draft_no, status, source_type, source_change_log_id, risk_level,
                       impact_modules_json, created_by_role_code, created_by_user_id, summary,
                       create_time, update_time, published_at, discarded_at,
                       last_dry_run_hash, last_dry_run_status, last_dry_run_at,
                       last_dry_run_by_role_code, last_dry_run_by_user_id
                FROM system_config_draft
                WHERE draft_no = ?
                LIMIT 1
                """, (rs, rowNum) -> new RawDraft(
                rs.getLong("id"),
                rs.getString("draft_no"),
                rs.getString("status"),
                rs.getString("source_type"),
                getLongOrNull(rs, "source_change_log_id"),
                rs.getString("risk_level"),
                rs.getString("impact_modules_json"),
                rs.getString("created_by_role_code"),
                getLongOrNull(rs, "created_by_user_id"),
                rs.getString("summary"),
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time")),
                toLocalDateTime(rs.getTimestamp("published_at")),
                toLocalDateTime(rs.getTimestamp("discarded_at")),
                rs.getString("last_dry_run_hash"),
                rs.getString("last_dry_run_status"),
                toLocalDateTime(rs.getTimestamp("last_dry_run_at")),
                rs.getString("last_dry_run_by_role_code"),
                getLongOrNull(rs, "last_dry_run_by_user_id")), draftNo.trim());
        if (rows.isEmpty()) {
            throw new BusinessException("草稿不存在");
        }
        return rows.get(0);
    }

    private List<RawDraftItem> loadDraftItems(String draftNo) {
        return jdbcTemplate.query("""
                SELECT id, draft_no, config_key, scope_type, scope_id, value_type, before_value,
                       after_value, base_current_value_hash, enabled, description, change_type,
                       sensitive_flag, validation_status, validation_message
                FROM system_config_draft_item
                WHERE draft_no = ?
                ORDER BY id ASC
                """, (rs, rowNum) -> new RawDraftItem(
                rs.getLong("id"),
                rs.getString("draft_no"),
                rs.getString("config_key"),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                rs.getString("value_type"),
                rs.getString("before_value"),
                rs.getString("after_value"),
                rs.getString("base_current_value_hash"),
                rs.getInt("enabled"),
                rs.getString("description"),
                rs.getString("change_type"),
                rs.getInt("sensitive_flag") == 1,
                rs.getString("validation_status"),
                rs.getString("validation_message")), draftNo);
    }

    private ChangeLogRecord loadChangeLog(Long changeLogId) {
        if (changeLogId == null) {
            throw new BusinessException("变更日志 ID 不能为空");
        }
        List<ChangeLogRecord> rows = jdbcTemplate.query("""
                SELECT id, config_key, scope_type, scope_id, before_value, after_value,
                       actor_role_code, actor_user_id, summary, create_time
                FROM system_config_change_log
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> new ChangeLogRecord(
                rs.getLong("id"),
                rs.getString("config_key"),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                rs.getString("before_value"),
                rs.getString("after_value"),
                rs.getString("summary"),
                toLocalDateTime(rs.getTimestamp("create_time"))), changeLogId);
        if (rows.isEmpty()) {
            throw new BusinessException("变更日志不存在");
        }
        return rows.get(0);
    }

    private RawPublishRecord loadPublishRecord(String publishNo) {
        if (!StringUtils.hasText(publishNo)) {
            throw new BusinessException("发布号不能为空");
        }
        List<RawPublishRecord> rows = jdbcTemplate.query("""
                SELECT id, publish_no, draft_no, status, risk_level, impact_modules_json,
                       before_hash, after_hash, before_snapshot_masked_json, after_snapshot_masked_json,
                       validation_result_json, failure_reason, published_by_role_code, published_by_user_id, published_at
                FROM system_config_publish_record
                WHERE publish_no = ?
                LIMIT 1
                """, (rs, rowNum) -> new RawPublishRecord(
                rs.getLong("id"),
                rs.getString("publish_no"),
                rs.getString("draft_no"),
                rs.getString("status"),
                rs.getString("risk_level"),
                rs.getString("impact_modules_json"),
                rs.getString("before_hash"),
                rs.getString("after_hash"),
                rs.getString("before_snapshot_masked_json"),
                rs.getString("after_snapshot_masked_json"),
                rs.getString("validation_result_json"),
                rs.getString("failure_reason"),
                rs.getString("published_by_role_code"),
                getLongOrNull(rs, "published_by_user_id"),
                toLocalDateTime(rs.getTimestamp("published_at"))), publishNo.trim());
        if (rows.isEmpty()) {
            throw new BusinessException("发布记录不存在");
        }
        return rows.get(0);
    }

    private SystemConfigDtos.PublishRecordResponse toPublishRecordResponse(RawPublishRecord record, boolean includeEvents) {
        SystemConfigDtos.PublishRecordResponse response = new SystemConfigDtos.PublishRecordResponse();
        response.setId(record.id());
        response.setPublishNo(record.publishNo());
        response.setDraftNo(record.draftNo());
        response.setStatus(record.status());
        response.setRiskLevel(record.riskLevel());
        response.setImpactModules(fromJsonList(record.impactModulesJson()));
        response.setBeforeHash(record.beforeHash());
        response.setAfterHash(record.afterHash());
        response.setBeforeSnapshotMaskedJson(record.beforeSnapshotMaskedJson());
        response.setAfterSnapshotMaskedJson(record.afterSnapshotMaskedJson());
        response.setValidationResultJson(record.validationResultJson());
        response.setFailureReason(record.failureReason());
        response.setPublishedByRoleCode(record.publishedByRoleCode());
        response.setPublishedByUserId(record.publishedByUserId());
        response.setPublishedAt(record.publishedAt());
        if (includeEvents) {
            response.setEvents(loadRuntimeEvents(record.publishNo()));
        }
        return response;
    }

    private List<SystemConfigDtos.RuntimeEventResponse> loadRuntimeEvents(String publishNo) {
        return jdbcTemplate.query("""
                SELECT id, publish_no, module_code, event_type, status, payload_json,
                       error_message, retry_count, max_retry_count, next_retry_at,
                       locked_by, locked_at, last_attempt_at, create_time, handled_at
                FROM system_config_runtime_event
                WHERE publish_no = ?
                ORDER BY create_time ASC, id ASC
                """, (rs, rowNum) -> mapRuntimeEvent(rs), publishNo);
    }

    private List<RawRuntimeEvent> loadRuntimeEventsForManualProcess(String publishNo) {
        return jdbcTemplate.query("""
                SELECT id, publish_no, module_code, event_type, status, payload_json,
                       error_message, retry_count, max_retry_count, next_retry_at,
                       locked_by, locked_at, last_attempt_at, create_time, handled_at
                FROM system_config_runtime_event
                WHERE publish_no = ?
                  AND status IN ('PENDING', 'FAILED', 'RETRYING', 'TERMINATED')
                ORDER BY create_time ASC, id ASC
                """, (rs, rowNum) -> mapRawRuntimeEvent(rs), publishNo);
    }

    private List<RawRuntimeEvent> loadDueRuntimeEvents(Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(RUNTIME_EVENT_LOCK_TIMEOUT_MINUTES);
        return jdbcTemplate.query("""
                SELECT id, publish_no, module_code, event_type, status, payload_json,
                       error_message, retry_count, max_retry_count, next_retry_at,
                       locked_by, locked_at, last_attempt_at, create_time, handled_at
                FROM system_config_runtime_event
                WHERE (
                    status IN ('PENDING', 'FAILED')
                    AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ) OR (
                    status = 'RETRYING'
                    AND (locked_at IS NULL OR locked_at < ?)
                )
                ORDER BY create_time ASC, id ASC
                LIMIT ?
                """, (rs, rowNum) -> mapRawRuntimeEvent(rs), now, staleBefore, safeLimit);
    }

    private RawRuntimeEvent loadRuntimeEventRecord(Long eventId) {
        List<RawRuntimeEvent> rows = jdbcTemplate.query("""
                SELECT id, publish_no, module_code, event_type, status, payload_json,
                       error_message, retry_count, max_retry_count, next_retry_at,
                       locked_by, locked_at, last_attempt_at, create_time, handled_at
                FROM system_config_runtime_event
                WHERE id = ?
                LIMIT 1
                """, (rs, rowNum) -> mapRawRuntimeEvent(rs), eventId);
        if (rows.isEmpty()) {
            throw new BusinessException("runtime event not found");
        }
        return rows.get(0);
    }

    private void processRuntimeEvent(RawRuntimeEvent event,
                                     PermissionRequestContext context,
                                     boolean manual) {
        LocalDateTime startedAt = LocalDateTime.now();
        String worker = runtimeWorkerId(context);
        if (!tryClaimRuntimeEvent(event.id(), startedAt, worker, manual)) {
            return;
        }
        RawRuntimeEvent current = loadRuntimeEventRecord(event.id());
        try {
            handleRuntimeEvent(current);
            jdbcTemplate.update("""
                    UPDATE system_config_runtime_event
                    SET status = ?, error_message = NULL, next_retry_at = NULL,
                        locked_by = NULL, locked_at = NULL, handled_at = ?
                    WHERE id = ?
                    """, RUNTIME_EVENT_STATUS_SUCCESS, LocalDateTime.now(), current.id());
        } catch (RuntimeException exception) {
            int retryCount = current.retryCount() == null ? 1 : current.retryCount();
            int maxRetryCount = current.maxRetryCount() == null || current.maxRetryCount() <= 0
                    ? DEFAULT_RUNTIME_EVENT_MAX_RETRY
                    : current.maxRetryCount();
            boolean exhausted = retryCount >= maxRetryCount;
            jdbcTemplate.update("""
                    UPDATE system_config_runtime_event
                    SET status = ?, error_message = ?, next_retry_at = ?,
                        locked_by = NULL, locked_at = NULL, handled_at = NULL
                    WHERE id = ?
                    """,
                    exhausted ? RUNTIME_EVENT_STATUS_TERMINATED : RUNTIME_EVENT_STATUS_FAILED,
                    truncate(exception.getMessage(), 1000),
                    exhausted ? null : LocalDateTime.now().plusMinutes(Math.min(retryCount, 15)),
                    current.id());
        }
    }

    private boolean tryClaimRuntimeEvent(Long eventId,
                                         LocalDateTime startedAt,
                                         String worker,
                                         boolean manual) {
        LocalDateTime staleBefore = startedAt.minusMinutes(RUNTIME_EVENT_LOCK_TIMEOUT_MINUTES);
        if (manual) {
            return jdbcTemplate.update("""
                    UPDATE system_config_runtime_event
                    SET status = ?, retry_count = COALESCE(retry_count, 0) + 1,
                        locked_by = ?, locked_at = ?, last_attempt_at = ?
                    WHERE id = ?
                      AND (
                          status IN ('PENDING', 'FAILED', 'TERMINATED')
                          OR (status = 'RETRYING' AND (locked_at IS NULL OR locked_at < ?))
                      )
                    """, RUNTIME_EVENT_STATUS_RETRYING, worker, startedAt, startedAt, eventId, staleBefore) > 0;
        }
        return jdbcTemplate.update("""
                UPDATE system_config_runtime_event
                SET status = ?, retry_count = COALESCE(retry_count, 0) + 1,
                    locked_by = ?, locked_at = ?, last_attempt_at = ?
                WHERE id = ?
                  AND (
                      (
                          status IN ('PENDING', 'FAILED')
                          AND (next_retry_at IS NULL OR next_retry_at <= ?)
                      )
                      OR (status = 'RETRYING' AND (locked_at IS NULL OR locked_at < ?))
                  )
                """, RUNTIME_EVENT_STATUS_RETRYING, worker, startedAt, startedAt,
                eventId, startedAt, staleBefore) > 0;
    }

    private void handleRuntimeEvent(RawRuntimeEvent event) {
        RawPublishRecord record = loadPublishRecord(event.publishNo());
        if (!PUBLISH_STATUS_SUCCESS.equals(record.status())) {
            throw new BusinessException("runtime event publish record is not successful");
        }
        String eventType = normalizeOrDefault(event.eventType(), "");
        if (!SUPPORTED_RUNTIME_EVENT_TYPES.contains(eventType)) {
            throw new BusinessException("unsupported runtime event type: " + event.eventType());
        }
        String moduleCode = normalizeOrDefault(event.moduleCode(), "SYSTEM_CONFIG");
        if (!SUPPORTED_RUNTIME_MODULES.contains(moduleCode)) {
            throw new BusinessException("unsupported runtime module: " + event.moduleCode());
        }
        // P0 local adapter only confirms runtime configuration visibility; core business data is not mutated here.
    }

    private String runtimeWorkerId(PermissionRequestContext context) {
        if (context == null) {
            return "SYSTEM_CONFIG_SCHEDULER";
        }
        return "USER:" + firstNonBlank(context.getRoleCode(), "UNKNOWN")
                + ":" + (context.getCurrentUserId() == null ? "0" : context.getCurrentUserId());
    }

    private Long countOf(String table, String condition) {
        String sql = switch (table + "|" + condition) {
            case "system_config_capability|enabled = 1" ->
                    "SELECT COUNT(1) FROM system_config_capability WHERE enabled = 1";
            case "system_config_draft|status = 'DRAFT'" ->
                    "SELECT COUNT(1) FROM system_config_draft WHERE status = 'DRAFT'";
            case "system_config_draft|status = 'DRAFT' AND risk_level = 'HIGH'" ->
                    "SELECT COUNT(1) FROM system_config_draft WHERE status = 'DRAFT' AND risk_level = 'HIGH'";
            case "system_config_publish_record|status = 'SUCCESS'" ->
                    "SELECT COUNT(1) FROM system_config_publish_record WHERE status = 'SUCCESS'";
            case "system_config_publish_record|status = 'FAILED'" ->
                    "SELECT COUNT(1) FROM system_config_publish_record WHERE status = 'FAILED'";
            case "system_config_runtime_event|status = 'PENDING'" ->
                    "SELECT COUNT(1) FROM system_config_runtime_event WHERE status = 'PENDING'";
            case "system_config_runtime_event|status = 'RETRYING'" ->
                    "SELECT COUNT(1) FROM system_config_runtime_event WHERE status = 'RETRYING'";
            case "system_config_runtime_event|status = 'SUCCESS'" ->
                    "SELECT COUNT(1) FROM system_config_runtime_event WHERE status = 'SUCCESS'";
            case "system_config_runtime_event|status = 'FAILED'" ->
                    "SELECT COUNT(1) FROM system_config_runtime_event WHERE status = 'FAILED'";
            case "system_config_runtime_event|status = 'TERMINATED'" ->
                    "SELECT COUNT(1) FROM system_config_runtime_event WHERE status = 'TERMINATED'";
            default -> throw new BusinessException("不支持的系统配置统计查询");
        };
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private SystemConfigDtos.ValidationItemResponse validateDraftItem(RawDraftItem item) {
        RawCapability capability = matchCapability(item.configKey());
        if (capability == null) {
            return validationItem(item.configKey(), "BLOCK", null, null, "NONE",
                    "配置 Key 未登记到受控能力清单",
                    "请先在能力注册表中登记该配置能力，再进入发布预检查。");
        }
        if ("BLOCKED".equalsIgnoreCase(capability.riskLevel())
                || "BLOCKED".equalsIgnoreCase(capability.validatorCode())) {
            return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), "该能力被明确阻断，不能通过运行时配置发布",
                    "请使用对应业务模块的专用流程处理，不要用配置项绕过主链路。");
        }
        String scopeType = normalizeOrDefault(item.scopeType(), "GLOBAL");
        if (!capability.scopeTypes().isEmpty()
                && capability.scopeTypes().stream().noneMatch(scope -> scopeType.equalsIgnoreCase(scope))) {
            return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), "该能力不允许使用当前作用域类型",
                    "请使用以下作用域之一：" + String.join(", ", capability.scopeTypes()));
        }
        String capabilityValueType = normalizeOrDefault(capability.valueType(), "STRING");
        String itemValueType = normalizeOrDefault(item.valueType(), "STRING");
        if (List.of("BOOLEAN", "URL", "JSON").contains(capabilityValueType)
                && !capabilityValueType.equals(itemValueType)) {
            return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), "配置值类型与能力元数据不一致",
                    "请使用值类型：" + capabilityValueType + "。");
        }
        try {
            return validateDraftItemByValidator(item, capability);
        } catch (BusinessException exception) {
            return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), exception.getMessage(), "请修正配置值后重新执行发布预检查。");
        }
    }

    private SystemConfigDtos.ValidationItemResponse validateDraftItemByValidator(RawDraftItem item,
                                                                                RawCapability capability) {
        String validator = normalizeOrDefault(capability.validatorCode(), "NONE");
        return switch (validator) {
            case "BOOLEAN" -> validateBooleanItem(item, capability);
            case "DOMAIN_URL" -> {
                validateBaseUrl(normalizeBaseUrl(item.afterValue()), item.configKey());
                yield validationItem(item.configKey(), "PASS", capability.ownerModule(), capability.capabilityCode(),
                        validator, "域名地址校验通过", null);
            }
            case "DISTRIBUTION_MAPPING" -> {
                JsonNode root = parseJson(item.configKey(), item.afterValue());
                validateDistributionOrderTypeMapping(root);
                yield validationItem(item.configKey(), "PASS", capability.ownerModule(), capability.capabilityCode(),
                        validator, "分销订单类型映射校验通过", null);
            }
            case "CLUE_DEDUP" -> validateClueDedupItem(item, capability);
            case "FINANCE_VISIBILITY" -> validateFinanceVisibilityItem(item, capability);
            case "BLOCKED" -> validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    validator, "该能力已被阻断", "请使用专用业务流程处理。");
            default -> validationItem(item.configKey(), "PASS", capability.ownerModule(), capability.capabilityCode(),
                    validator, "能力校验通过", null);
        };
    }

    private SystemConfigDtos.ValidationItemResponse validateBooleanItem(RawDraftItem item, RawCapability capability) {
        if (!isBooleanText(item.afterValue())) {
            return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), "布尔配置只允许填写 true 或 false",
                    "请将配置值设置为 true 或 false。");
        }
        return validationItem(item.configKey(), "PASS", capability.ownerModule(), capability.capabilityCode(),
                capability.validatorCode(), "布尔配置校验通过", null);
    }

    private SystemConfigDtos.ValidationItemResponse validateClueDedupItem(RawDraftItem item, RawCapability capability) {
        String normalizedKey = normalizeConfigKey(item.configKey());
        if (normalizedKey.endsWith(".enabled")) {
            return validateBooleanItem(item, capability);
        }
        if (normalizedKey.endsWith(".window_days")) {
            int days;
            try {
                days = Integer.parseInt(String.valueOf(item.afterValue()).trim());
            } catch (Exception exception) {
                return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                        capability.validatorCode(), "客资去重窗口必须是数字",
                        "请填写 1 到 3650 之间的整数。");
            }
            if (days < 1 || days > 3650) {
                return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                        capability.validatorCode(), "客资去重窗口超出允许范围",
                        "请填写 1 到 3650 之间的整数。");
            }
            return validationItem(item.configKey(), "PASS", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), "客资去重窗口校验通过", null);
        }
        return validationItem(item.configKey(), "WARN", capability.ownerModule(), capability.capabilityCode(),
                capability.validatorCode(), "该客资去重 Key 已登记但没有专用校验器",
                "请确认消费模块能读取该配置 Key。");
    }

    private SystemConfigDtos.ValidationItemResponse validateFinanceVisibilityItem(RawDraftItem item,
                                                                                 RawCapability capability) {
        String normalizedKey = normalizeConfigKey(item.configKey());
        if (normalizedKey.startsWith("payment.")
                || normalizedKey.contains("withdraw")
                || normalizedKey.contains("ledger")
                || normalizedKey.contains("cash")
                || normalizedKey.contains("fund")) {
            return validationItem(item.configKey(), "BLOCK", capability.ownerModule(), capability.capabilityCode(),
                    capability.validatorCode(), "资金、账本、提现和支付行为不能在这里直接配置",
                    "请使用财务模块配置和审计流程。");
        }
        return validationItem(item.configKey(), "PASS", capability.ownerModule(), capability.capabilityCode(),
                capability.validatorCode(), "财务可见性配置校验通过", null);
    }

    private JsonNode parseJson(String key, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(key + " JSON 配置不能为空");
        }
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            throw new BusinessException(key + " 不是有效 JSON");
        }
    }

    private boolean isBooleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "false".equals(normalized);
    }

    private SystemConfigDtos.ValidationItemResponse validationItem(String configKey,
                                                                  String status,
                                                                  String moduleCode,
                                                                  String capabilityCode,
                                                                  String validatorCode,
                                                                  String message,
                                                                  String suggestion) {
        SystemConfigDtos.ValidationItemResponse response = new SystemConfigDtos.ValidationItemResponse();
        response.setConfigKey(configKey);
        response.setStatus(status);
        response.setModuleCode(moduleCode);
        response.setCapabilityCode(capabilityCode);
        response.setValidatorCode(validatorCode);
        response.setMessage(message);
        response.setSuggestion(suggestion);
        return response;
    }

    private RawCapability matchCapability(String configKey) {
        String normalizedKey = normalizeConfigKey(configKey);
        return loadEnabledCapabilities().stream()
                .filter(capability -> capabilityMatches(capability.configKeyPattern(), normalizedKey))
                .max(Comparator.comparingInt(capability -> capability.configKeyPattern().length()))
                .orElse(null);
    }

    private boolean capabilityMatches(String pattern, String normalizedKey) {
        String normalizedPattern = normalizeConfigKey(pattern);
        if (normalizedPattern.endsWith("%")) {
            return normalizedKey.startsWith(normalizedPattern.substring(0, normalizedPattern.length() - 1));
        }
        return normalizedKey.equals(normalizedPattern);
    }

    private List<RawCapability> loadEnabledCapabilities() {
        return jdbcTemplate.query("""
                SELECT id, capability_code, config_key_pattern, owner_module, value_type,
                       scope_type_allowed_json, risk_level, sensitive_flag, validator_code,
                       runtime_reload_strategy, enabled, create_time, update_time
                FROM system_config_capability
                WHERE enabled = 1
                """, (rs, rowNum) -> new RawCapability(
                rs.getLong("id"),
                rs.getString("capability_code"),
                rs.getString("config_key_pattern"),
                rs.getString("owner_module"),
                rs.getString("value_type"),
                fromJsonList(rs.getString("scope_type_allowed_json")),
                rs.getString("risk_level"),
                rs.getInt("sensitive_flag") == 1,
                rs.getString("validator_code"),
                rs.getString("runtime_reload_strategy"),
                rs.getInt("enabled"),
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time"))));
    }

    private List<String> ownerModulesForDraft(String draftNo) {
        List<String> modules = new ArrayList<>();
        for (RawDraftItem item : loadDraftItems(draftNo)) {
            RawCapability capability = matchCapability(item.configKey());
            if (capability != null && StringUtils.hasText(capability.ownerModule())
                    && !modules.contains(capability.ownerModule())) {
                modules.add(capability.ownerModule());
            }
        }
        if (modules.isEmpty()) {
            modules.add("SYSTEM_CONFIG");
        }
        return modules;
    }

    private void persistDryRunResult(RawDraft draft,
                                     SystemConfigDtos.DryRunResponse response,
                                     PermissionRequestContext context) {
        List<RawDraftItem> items = loadDraftItems(draft.draftNo());
        jdbcTemplate.update("""
                UPDATE system_config_draft
                SET last_dry_run_hash = ?,
                    last_dry_run_status = ?,
                    last_dry_run_at = ?,
                    last_dry_run_by_role_code = ?,
                    last_dry_run_by_user_id = ?,
                    update_time = ?
                WHERE draft_no = ? AND status = ?
                """, draftContentHash(items),
                Boolean.TRUE.equals(response.getRunnable()) ? "PASS" : "BLOCK",
                LocalDateTime.now(),
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                LocalDateTime.now(),
                draft.draftNo(),
                DRAFT_STATUS_DRAFT);
    }

    private String draftContentHash(List<RawDraftItem> items) {
        return hashValue(snapshotJson(items, true, false));
    }

    private String moduleLabel(String moduleCode) {
        return switch (String.valueOf(moduleCode)) {
            case "SYSTEM_SETTING" -> "系统设置";
            case "SYSTEM_FLOW" -> "系统流程";
            case "STORE_SERVICE" -> "门店服务";
            case "FINANCE" -> "财务管理";
            case "CLUE" -> "客资中心";
            case "PLANORDER" -> "门店排档";
            case "SCHEDULER" -> "调度中心";
            case "WECOM" -> "私域客服";
            case "SYSTEM_CONFIG" -> "系统配置";
            default -> moduleCode;
        };
    }

    private String nextPublishNo() {
        return "PUB-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private void recordFailedPublish(String publishNo,
                                     RawDraft draft,
                                     List<RawDraftItem> items,
                                     SystemConfigDtos.ValidationResponse validation,
                                     String failureReason,
                                     PermissionRequestContext context) {
        if (requiresNewTransactionTemplate == null) {
            insertPublishRecord(publishNo, draft, items, PUBLISH_STATUS_FAILED, validation, failureReason, context);
            return;
        }
        requiresNewTransactionTemplate.executeWithoutResult(status ->
                insertPublishRecord(publishNo, draft, items, PUBLISH_STATUS_FAILED, validation, failureReason, context));
    }

    private void insertPublishRecord(String publishNo,
                                     RawDraft draft,
                                     List<RawDraftItem> items,
                                     String status,
                                     SystemConfigDtos.ValidationResponse validation,
                                     String failureReason,
                                     PermissionRequestContext context) {
        List<String> modules = ownerModulesForDraft(draft.draftNo());
        String beforeRawSnapshot = snapshotJson(items, false, false);
        String afterRawSnapshot = snapshotJson(items, true, false);
        jdbcTemplate.update("""
                INSERT INTO system_config_publish_record(
                    publish_no, draft_no, status, risk_level, impact_modules_json,
                    before_hash, after_hash, before_snapshot_masked_json, after_snapshot_masked_json,
                    validation_result_json, failure_reason, published_by_role_code, published_by_user_id, published_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, publishNo, draft.draftNo(), status, draft.riskLevel(), toJson(modules),
                hashValue(beforeRawSnapshot), hashValue(afterRawSnapshot),
                snapshotJson(items, false, true), snapshotJson(items, true, true),
                toJsonObject(validation), truncate(failureReason, 1000),
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                LocalDateTime.now());
    }

    private String snapshotJson(List<RawDraftItem> items, boolean after, boolean masked) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RawDraftItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("configKey", item.configKey());
            row.put("scopeType", item.scopeType());
            row.put("scopeId", item.scopeId());
            row.put("valueType", item.valueType());
            String value = after ? item.afterValue() : item.beforeValue();
            row.put("value", masked ? maskIfSensitive(item.configKey(), value) : value);
            row.put("changeType", item.changeType());
            rows.add(row);
        }
        return toJsonObject(rows);
    }

    private void insertRuntimeEvent(String publishNo,
                                    String moduleCode,
                                    String eventType,
                                    String status,
                                    String payload,
                                    String errorMessage) {
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("publishNo", publishNo);
        eventPayload.put("moduleCode", moduleCode);
        eventPayload.put("eventType", eventType);
        eventPayload.put("message", payload);
        jdbcTemplate.update("""
                INSERT INTO system_config_runtime_event(
                    publish_no, module_code, event_type, status, payload_json, error_message,
                    retry_count, max_retry_count, create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, publishNo, moduleCode, eventType, status,
                toJsonObject(eventPayload), truncate(errorMessage, 1000),
                0, DEFAULT_RUNTIME_EVENT_MAX_RETRY, LocalDateTime.now());
    }

    private SystemConfigDtos.SaveConfigRequest rollbackRequest(ChangeLogRecord changeLog) {
        if (changeLog.beforeValue() == null) {
            throw new BusinessException("新增类型的变更日志没有历史值，不能回滚");
        }
        ConfigMeta meta = findConfigMeta(changeLog.configKey(), changeLog.scopeType(), changeLog.scopeId());
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey(changeLog.configKey());
        request.setConfigValue(changeLog.beforeValue());
        request.setValueType(meta.valueType());
        request.setScopeType(changeLog.scopeType());
        request.setScopeId(changeLog.scopeId());
        request.setEnabled(meta.enabled());
        request.setDescription(meta.description());
        request.setSummary("根据变更日志 #" + changeLog.id() + " 生成回滚草稿");
        return request;
    }

    private ConfigMeta findConfigMeta(String key, String scopeType, String scopeId) {
        List<ConfigMeta> rows = jdbcTemplate.query("""
                SELECT value_type, enabled, description
                FROM system_config
                WHERE config_key = ? AND scope_type = ? AND scope_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new ConfigMeta(
                rs.getString("value_type"),
                rs.getInt("enabled"),
                rs.getString("description")), key, scopeType, scopeId);
        return rows.isEmpty() ? new ConfigMeta("STRING", 1, "回滚系统配置") : rows.get(0);
    }

    private NormalizedConfigInput normalizeConfigRequest(SystemConfigDtos.SaveConfigRequest request) {
        if (request == null || !StringUtils.hasText(request.getConfigKey())) {
            throw new BusinessException("配置 Key 不能为空");
        }
        String key = request.getConfigKey().trim();
        assertAllowedConfigKey(key);
        String scopeType = normalizeOrDefault(request.getScopeType(), "GLOBAL");
        String scopeId = StringUtils.hasText(request.getScopeId()) ? request.getScopeId().trim() : "GLOBAL";
        String valueType = normalizeOrDefault(request.getValueType(), "STRING");
        String configValue = "URL".equals(valueType) ? normalizeBaseUrl(request.getConfigValue()) : request.getConfigValue();
        if ("URL".equals(valueType)) {
            validateBaseUrl(configValue, key);
        }
        if ("JSON".equals(valueType)) {
            validateJsonConfig(key, configValue);
        }
        return new NormalizedConfigInput(
                key,
                scopeType,
                scopeId,
                valueType,
                configValue,
                request.getEnabled() == null ? 1 : request.getEnabled());
    }

    private String resolveChangeType(String beforeValue, String afterValue) {
        if (beforeValue == null) {
            return "CREATE";
        }
        if (Objects.equals(beforeValue, afterValue)) {
            return "NO_CHANGE";
        }
        return "UPDATE";
    }

    private String resolveRiskLevel(String key) {
        String normalized = normalizeConfigKey(key);
        if (isSensitiveKey(key)
                || normalized.startsWith("payment.")
                || normalized.startsWith("system.domain.")
                || normalized.startsWith("workflow.")
                || normalized.startsWith("amount.")
                || normalized.startsWith("douyin.")
                || normalized.startsWith("wecom.")) {
            return "HIGH";
        }
        if (normalized.startsWith("deposit.")
                || normalized.startsWith("clue.")
                || normalized.startsWith("distribution.")
                || normalized.startsWith("form_designer.")
                || normalized.startsWith("scheduler.")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> resolveImpactModules(String key) {
        String normalized = normalizeConfigKey(key);
        List<String> modules = new ArrayList<>();
        if (normalized.startsWith("clue.")) {
            modules.add("客资中心");
        }
        if (normalized.startsWith("deposit.") || normalized.startsWith("workflow.") || normalized.startsWith("form_designer.")) {
            modules.add("门店服务");
        }
        if (normalized.startsWith("amount.") || normalized.startsWith("payment.")) {
            modules.add("财务管理");
        }
        if (normalized.startsWith("distribution.") || normalized.startsWith("douyin.") || normalized.startsWith("wecom.")
                || normalized.startsWith("scheduler.") || normalized.startsWith("system.domain.")) {
            modules.add("系统设置");
        }
        if (modules.isEmpty()) {
            modules.add("系统配置");
        }
        return modules;
    }

    private List<String> resolvePreviewWarnings(String key, String beforeValue, String afterValue, String riskLevel, boolean sensitive) {
        List<String> warnings = new ArrayList<>();
        String normalized = normalizeConfigKey(key);
        if (Objects.equals(beforeValue, afterValue)) {
            warnings.add("配置值未变化，保存后仍会留下审计记录。");
        }
        if ("HIGH".equals(riskLevel)) {
            warnings.add("高风险配置，发布前请确认影响页面、角色和外部接口。");
        }
        if (sensitive) {
            warnings.add("敏感配置已脱敏展示，请确认密钥来源和有效期。");
        }
        if (normalized.startsWith("workflow.")) {
            warnings.add("流程配置会影响状态流转，建议先在系统流程页完成模拟验证。");
        } else if (normalized.startsWith("amount.")) {
            warnings.add("金额可见规则会影响门店角色看到的定金、团购和核销金额。");
        } else if (normalized.startsWith("clue.dedup.")) {
            warnings.add("客资去重配置会影响后续接口拉取入库和客资记录合并方式。");
        } else if (normalized.startsWith("deposit.direct.")) {
            warnings.add("定金免码配置会影响定金订单是否可直接进入后续服务流程。");
        } else if (normalized.startsWith("system.domain.")) {
            warnings.add("域名配置会影响回调地址、Swagger/OpenAPI 地址和第三方联调地址。");
        } else if (normalized.startsWith("distribution.") || normalized.startsWith("douyin.") || normalized.startsWith("wecom.")) {
            warnings.add("外部集成配置会影响接口同步、回调和定时任务联调。");
        }
        return warnings;
    }

    private String normalizeConfigKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private String nextDraftNo() {
        return "CFG-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private String buildPublishSummary(RawDraft draft, RawDraftItem item) {
        String summary = StringUtils.hasText(draft.summary()) ? draft.summary().trim() : "发布系统配置草稿";
        return summary + "（草稿号=" + draft.draftNo() + "，配置Key=" + item.configKey() + "）";
    }

    private String toJson(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String toJsonObject(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private List<String> fromJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            List<String> values = new ArrayList<>();
            if (root != null && root.isArray()) {
                root.forEach(node -> {
                    if (node != null && node.isTextual()) {
                        values.add(node.asText());
                    }
                });
            }
            return values;
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? NULL_HASH_MARKER : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("当前运行环境不支持 SHA-256");
        }
    }

    private Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value == null ? null : rs.getLong(columnName);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record RawDraft(
            Long id,
            String draftNo,
            String status,
            String sourceType,
            Long sourceChangeLogId,
            String riskLevel,
            String impactModulesJson,
            String createdByRoleCode,
            Long createdByUserId,
            String summary,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            LocalDateTime publishedAt,
            LocalDateTime discardedAt,
            String lastDryRunHash,
            String lastDryRunStatus,
            LocalDateTime lastDryRunAt,
            String lastDryRunByRoleCode,
            Long lastDryRunByUserId) {
    }

    private record RawDraftItem(
            Long id,
            String draftNo,
            String configKey,
            String scopeType,
            String scopeId,
            String valueType,
            String beforeValue,
            String afterValue,
            String baseCurrentValueHash,
            Integer enabled,
            String description,
            String changeType,
            Boolean sensitive,
            String validationStatus,
            String validationMessage) {
    }

    private record ChangeLogRecord(
            Long id,
            String configKey,
            String scopeType,
            String scopeId,
            String beforeValue,
            String afterValue,
            String summary,
            LocalDateTime createTime) {
    }

    private record RawPublishRecord(
            Long id,
            String publishNo,
            String draftNo,
            String status,
            String riskLevel,
            String impactModulesJson,
            String beforeHash,
            String afterHash,
            String beforeSnapshotMaskedJson,
            String afterSnapshotMaskedJson,
            String validationResultJson,
            String failureReason,
            String publishedByRoleCode,
            Long publishedByUserId,
            LocalDateTime publishedAt) {
    }

    private record RawRuntimeEvent(
            Long id,
            String publishNo,
            String moduleCode,
            String eventType,
            String status,
            String payloadJson,
            String errorMessage,
            LocalDateTime createTime,
            LocalDateTime handledAt,
            Integer retryCount,
            Integer maxRetryCount,
            LocalDateTime nextRetryAt,
            String lockedBy,
            LocalDateTime lockedAt,
            LocalDateTime lastAttemptAt) {
    }

    private record RawCapability(
            Long id,
            String capabilityCode,
            String configKeyPattern,
            String ownerModule,
            String valueType,
            List<String> scopeTypes,
            String riskLevel,
            Boolean sensitive,
            String validatorCode,
            String runtimeReloadStrategy,
            Integer enabled,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
    }

    private record ConfigMeta(
            String valueType,
            Integer enabled,
            String description) {
    }

    private record NormalizedConfigInput(
            String key,
            String scopeType,
            String scopeId,
            String valueType,
            String configValue,
            Integer enabled) {
    }

    private SystemConfigDtos.SaveConfigRequest configRequest(String key, String value, String description) {
        SystemConfigDtos.SaveConfigRequest request = new SystemConfigDtos.SaveConfigRequest();
        request.setConfigKey(key);
        request.setConfigValue(value);
        request.setValueType("URL");
        request.setScopeType("GLOBAL");
        request.setScopeId("GLOBAL");
        request.setEnabled(1);
        request.setDescription(description);
        request.setSummary("更新上线域名配置");
        return request;
    }

    private SystemConfigDtos.DomainSettingsResponse domainResponse(String systemBaseUrl, String apiBaseUrl) {
        SystemConfigDtos.DomainSettingsResponse response = new SystemConfigDtos.DomainSettingsResponse();
        response.setSystemBaseUrl(systemBaseUrl);
        response.setApiBaseUrl(apiBaseUrl);
        response.setEventIngestUrl(joinUrl(apiBaseUrl, "/open/distribution/events"));
        response.setSwaggerUiUrl(joinUrl(apiBaseUrl, "/swagger-ui.html"));
        response.setOpenApiDocsUrl(joinUrl(apiBaseUrl, "/v3/api-docs/distribution-open-api"));
        return response;
    }

    private void validateBaseUrl(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(label + "不能为空");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new BusinessException(label + "必须以 http:// 或 https:// 开头");
        }
        try {
            URI uri = new URI(value);
            if (!StringUtils.hasText(uri.getHost())) {
                throw new BusinessException(label + "必须包含有效域名或 IP");
            }
            if (StringUtils.hasText(uri.getQuery()) || StringUtils.hasText(uri.getFragment())) {
                throw new BusinessException(label + "不能包含 query 或 fragment");
            }
        } catch (URISyntaxException exception) {
            throw new BusinessException(label + "格式不正确");
        }
    }

    private void validateJsonConfig(String key, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(key + " JSON 配置不能为空");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(value);
        } catch (Exception exception) {
            throw new BusinessException(key + " 不是有效 JSON");
        }
        if (DistributionOrderTypeMappingResolver.CONFIG_KEY.equals(key)) {
            validateDistributionOrderTypeMapping(root);
        }
    }

    private void validateDistributionOrderTypeMapping(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new BusinessException("分销订单类型映射必须是 JSON 对象");
        }
        validateConfigOrderType(root.path("default").asText("coupon"), "default");
        JsonNode aliases = root.path("aliases");
        if (!aliases.isMissingNode()) {
            if (!aliases.isObject()) {
                throw new BusinessException("分销订单类型映射 aliases 必须是对象");
            }
            aliases.fields().forEachRemaining(entry ->
                    validateConfigOrderType(entry.getValue().asText(null), "aliases." + entry.getKey()));
        }
        JsonNode rules = root.path("rules");
        if (!rules.isMissingNode() && !rules.isArray()) {
            throw new BusinessException("分销订单类型映射 rules 必须是数组");
        }
        if (rules.isArray()) {
            int index = 0;
            for (JsonNode rule : rules) {
                if (rule == null || !rule.isObject()) {
                    throw new BusinessException("分销订单类型映射 rules[" + index + "] 必须是对象");
                }
                if (rule.path("enabled").asBoolean(true)) {
                    String type = firstNonBlank(
                            text(rule, "internalOrderType"),
                            text(rule, "orderType"),
                            text(rule, "targetOrderType"));
                    validateConfigOrderType(type, "rules[" + index + "].internalOrderType");
                }
                index++;
            }
        }
    }

    private void validateConfigOrderType(String value, String fieldName) {
        String normalized = normalizeConfigOrderType(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(fieldName + " 只能填写 coupon 或 deposit");
        }
    }

    private String normalizeConfigOrderType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "coupon", "groupbuy", "voucher", "团购", "团购券" -> "coupon";
            case "deposit", "prepay", "prepaid", "定金", "预付定金" -> "deposit";
            default -> null;
        };
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String joinUrl(String baseUrl, String path) {
        String base = normalizeBaseUrl(baseUrl);
        String normalizedPath = StringUtils.hasText(path) ? path.trim() : "";
        if (!StringUtils.hasText(base)) {
            return normalizedPath;
        }
        if (!StringUtils.hasText(normalizedPath)) {
            return base;
        }
        return base + (normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
    }

    private void assertAllowedConfigKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_CONFIG_PREFIXES.stream().anyMatch(normalized::startsWith);
        if (!allowed) {
            throw new BusinessException("不允许保存未登记的系统配置项: " + key);
        }
    }

    private void assertLegacyDirectSaveAllowed(String key) {
        RawCapability capability = matchCapability(key);
        if (capability == null) {
            return;
        }
        String riskLevel = normalizeOrDefault(capability.riskLevel(), "LOW");
        if ("BLOCKED".equals(riskLevel) || "BLOCKED".equalsIgnoreCase(capability.validatorCode())) {
            throw new BusinessException("该配置能力已被阻断，不能通过旧保存入口直写，请使用对应业务模块流程");
        }
        if ("HIGH".equals(riskLevel) || Boolean.TRUE.equals(capability.sensitive())) {
            throw new BusinessException("该配置属于高风险或敏感能力，请在配置发布中心生成草稿、完成发布预检查后再发布");
        }
    }

    private String maskIfSensitive(String key, String value) {
        if (!isSensitiveKey(key) || !StringUtils.hasText(value)) {
            return value;
        }
        return MASKED_VALUE;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(".", "_");
        if (SENSITIVE_KEY_MARKERS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        RawCapability capability = matchCapability(key);
        return capability != null && Boolean.TRUE.equals(capability.sensitive());
    }
}
