package com.seedcrm.crm.systemconfig.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.dto.SystemGoLiveDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.systemconfig.service.SystemGoLiveService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemGoLiveServiceImpl implements SystemGoLiveService {

    private static final String ENVIRONMENT_MODE_KEY = "system.environment.mode";
    private static final String LAST_INITIALIZED_AT_KEY = "system.go_live.lastInitializedAt";
    private static final String DEFAULT_ENVIRONMENT_MODE = "TEST";
    private static final String CONFIRM_INIT = "INIT_SYSTEM";
    private static final String CONFIRM_INIT_PROD = "INIT_PROD_SYSTEM";
    private static final String CONFIRM_CLEAR_TEST_DATA = "CLEAR_TEST_DATA";

    private static final List<TableTarget> BUSINESS_DATA_TABLES = List.of(
            table("order_role_record", "履约角色"),
            table("order_refund_record", "退款记录"),
            table("order_action_record", "订单动作"),
            table("plan_order", "计划单"),
            table("order_info", "订单"),
            table("customer_wecom_relation", "企微绑定"),
            table("customer_tag_detail", "客户标签"),
            table("customer_ecom_user", "会员身份"),
            table("customer", "客户"),
            table("clue", "客资"),
            table("clue_profile", "客资运营状态"),
            table("clue_record", "客资记录"),
            table("salary_detail", "薪酬明细"),
            table("salary_settlement", "薪酬结算"),
            table("distributor_income_detail", "分销收入"),
            table("distributor_settlement", "分销结算"),
            table("distributor_withdraw", "分销提现"),
            table("withdraw_record", "提现记录"),
            table("finance_check_record", "财务记录"),
            table("ledger", "账务流水"),
            table("account", "账户"));

    private static final List<TableTarget> QUEUE_DATA_TABLES = List.of(
            table("scheduler_outbox_event", "Outbox 队列"),
            table("distribution_exception_record", "异常队列"),
            table("idempotent_record", "幂等记录"),
            table("integration_callback_event_log", "回调记录"),
            table("scheduler_job_log", "调度执行记录"),
            table("system_flow_event_log", "流程事件"),
            table("system_flow_task", "流程任务"),
            table("system_flow_instance", "流程实例"));

    private static final List<TableTarget> OPERATIONAL_LOG_TABLES = List.of(
            table("scheduler_job_audit_log", "调度审计"),
            table("system_config_change_log", "配置变更"),
            table("system_flow_audit_log", "流程审计"),
            table("plan_order_service_form_template_audit_log", "服务单模板审计"),
            table("wecom_touch_log", "企微触达日志"),
            table("sys_audit_log", "系统审计"));

    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigService systemConfigService;
    private final Environment environment;

    @Autowired
    public SystemGoLiveServiceImpl(JdbcTemplate jdbcTemplate,
                                   SystemConfigService systemConfigService,
                                   Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.systemConfigService = systemConfigService;
        this.environment = environment;
    }

    public SystemGoLiveServiceImpl(JdbcTemplate jdbcTemplate, SystemConfigService systemConfigService) {
        this(jdbcTemplate, systemConfigService, null);
    }

    @Override
    public SystemGoLiveDtos.SummaryResponse summary() {
        String environmentMode = currentEnvironmentMode();
        SystemConfigDtos.DomainSettingsResponse domainSettings = systemConfigService.getDomainSettings();
        SystemGoLiveDtos.SummaryResponse response = new SystemGoLiveDtos.SummaryResponse();
        response.setEnvironmentMode(environmentMode);
        response.setSafeToClearTestData(isDestructiveClearAllowed(environmentMode));
        response.setDomainSettings(domainSettings);
        response.setCheckedAt(LocalDateTime.now());
        response.setTableCounts(allClearTargets().stream().map(this::countTable).toList());
        response.setReadinessItems(buildReadinessItems(environmentMode, domainSettings));
        response.setWarnings(buildWarnings(environmentMode, domainSettings));
        return response;
    }

    @Override
    @Transactional
    public SystemGoLiveDtos.OperationResponse initialize(SystemGoLiveDtos.InitializeRequest request,
                                                         PermissionRequestContext context) {
        if (request == null) {
            throw new BusinessException("initialize request is required");
        }
        String targetEnvironment = normalizeEnvironment(request.getTargetEnvironment());
        String expectedConfirm = "PROD".equals(targetEnvironment) ? CONFIRM_INIT_PROD : CONFIRM_INIT;
        requireConfirmText(request.getConfirmText(), expectedConfirm);

        List<String> warnings = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemBaseUrl()) || StringUtils.hasText(request.getApiBaseUrl())) {
            SystemConfigDtos.SaveDomainSettingsRequest domainRequest = new SystemConfigDtos.SaveDomainSettingsRequest();
            domainRequest.setSystemBaseUrl(StringUtils.hasText(request.getSystemBaseUrl())
                    ? request.getSystemBaseUrl()
                    : systemConfigService.getDomainSettings().getSystemBaseUrl());
            domainRequest.setApiBaseUrl(StringUtils.hasText(request.getApiBaseUrl())
                    ? request.getApiBaseUrl()
                    : systemConfigService.getDomainSettings().getApiBaseUrl());
            systemConfigService.saveDomainSettings(domainRequest, context);
        }

        upsertConfig(ENVIRONMENT_MODE_KEY, targetEnvironment, "STRING", "系统运行环境：LOCAL/TEST/STAGING/PROD", context);
        upsertConfig(LAST_INITIALIZED_AT_KEY, LocalDateTime.now().toString(), "STRING", "最近一次上线初始化时间", context);

        int affectedRows = 0;
        if (Boolean.TRUE.equals(request.getResetIntegrationToMock())) {
            if ("PROD".equals(targetEnvironment)) {
                warnings.add("正式环境不会自动重置接口为 MOCK，避免误切真实联调配置。");
            } else {
                affectedRows += resetIntegrationProvidersToMock();
            }
        }
        if (Boolean.TRUE.equals(request.getDisableSchedulers())) {
            affectedRows += disableSchedulers();
        }

        SystemGoLiveDtos.OperationResponse response = baseOperationResponse("INITIALIZE", targetEnvironment, false);
        response.setStatus("SUCCESS");
        response.setAffectedRows(affectedRows);
        response.setWarnings(warnings);
        return response;
    }

    @Override
    @Transactional
    public SystemGoLiveDtos.OperationResponse clearTestData(SystemGoLiveDtos.ClearTestDataRequest request,
                                                            PermissionRequestContext context) {
        if (request == null) {
            throw new BusinessException("clear test data request is required");
        }
        requireConfirmText(request.getConfirmText(), CONFIRM_CLEAR_TEST_DATA);
        String environmentMode = currentEnvironmentMode();
        if (!isNonProductionEnvironment(environmentMode)) {
            throw new BusinessException("current environment is " + environmentMode + ", clear test data is blocked");
        }

        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        if (!dryRun && !isDestructiveClearAllowed(environmentMode)) {
            throw new BusinessException("destructive clear is disabled, set SEEDCRM_ALLOW_CLEAR_TEST_DATA=true only in a verified test environment");
        }
        List<TableTarget> targets = new ArrayList<>();
        targets.addAll(BUSINESS_DATA_TABLES);
        targets.addAll(QUEUE_DATA_TABLES);
        if (Boolean.TRUE.equals(request.getIncludeOperationalLogs())) {
            targets.addAll(OPERATIONAL_LOG_TABLES);
        }

        int affectedRows = 0;
        List<SystemGoLiveDtos.TableOperationResult> tables = new ArrayList<>();
        for (TableTarget target : targets) {
            SystemGoLiveDtos.TableOperationResult result = clearTable(target, dryRun);
            affectedRows += result.getAffectedRows() == null ? 0 : result.getAffectedRows();
            tables.add(result);
        }

        SystemGoLiveDtos.OperationResponse response = baseOperationResponse("CLEAR_TEST_DATA", environmentMode, dryRun);
        response.setStatus(dryRun ? "DRY_RUN" : "SUCCESS");
        response.setAffectedRows(affectedRows);
        response.setTables(tables);
        response.setWarnings(buildWarnings(environmentMode, systemConfigService.getDomainSettings()));
        return response;
    }

    private List<SystemGoLiveDtos.ReadinessItemResponse> buildReadinessItems(String environmentMode,
                                                                             SystemConfigDtos.DomainSettingsResponse domainSettings) {
        List<SystemGoLiveDtos.ReadinessItemResponse> items = new ArrayList<>();
        items.add(readiness("ENVIRONMENT", "运行环境", StringUtils.hasText(environmentMode),
                "当前环境：" + environmentMode));
        items.add(readiness("API_DOMAIN", "API 域名", isValidHttpUrl(domainSettings.getApiBaseUrl()),
                "回调、OpenAPI、Swagger 地址会使用 API 域名拼接。"));
        items.add(readiness("SYSTEM_DOMAIN", "系统域名", isValidHttpUrl(domainSettings.getSystemBaseUrl()),
                "后台页面、扫码服务单会使用系统域名拼接。"));
        items.add(readiness("SCHEDULER_JOBS", "调度任务", tableCount("scheduler_job") > 0,
                "上线前建议先用 dry-run 预检，再启用真实调度。"));
        items.add(providerReadiness("DISTRIBUTION_PROVIDER", "分销券核销配置", "DISTRIBUTION"));
        items.add(providerReadiness("DOUYIN_PROVIDER", "抖音券核销配置", "DOUYIN_LAIKE"));
        items.add(readiness("CLEAR_PROTECTION", "清理硬保护", isDestructiveClearAllowed(environmentMode),
                "真实清理测试数据需要非 PROD 环境，且显式设置 SEEDCRM_ALLOW_CLEAR_TEST_DATA=true。"));
        return items;
    }

    private SystemGoLiveDtos.ReadinessItemResponse providerReadiness(String key, String label, String providerCode) {
        ProviderReadiness readiness = integrationProviderReadiness(providerCode);
        return readiness(key, label, readiness.ok(), readiness.message());
    }

    private SystemGoLiveDtos.ReadinessItemResponse readiness(String key, String label, boolean ok, String message) {
        SystemGoLiveDtos.ReadinessItemResponse item = new SystemGoLiveDtos.ReadinessItemResponse();
        item.setKey(key);
        item.setLabel(label);
        item.setStatus(ok ? "PASS" : "WARN");
        item.setSeverity(ok ? "PASS" : "BLOCKER");
        item.setMessage(message);
        return item;
    }

    private List<String> buildWarnings(String environmentMode, SystemConfigDtos.DomainSettingsResponse domainSettings) {
        List<String> warnings = new ArrayList<>();
        if ("PROD".equals(environmentMode) && isLocalUrl(domainSettings.getApiBaseUrl())) {
            warnings.add("正式环境 API 域名仍是本地地址，外部平台无法回调。");
        }
        if ("PROD".equals(environmentMode) && isLocalUrl(domainSettings.getSystemBaseUrl())) {
            warnings.add("正式环境系统域名仍是本地地址，扫码服务单与后台跳转不可对外访问。");
        }
        if (!"PROD".equals(environmentMode)) {
            warnings.add("当前非正式环境，允许 dry-run 与清理测试数据；切换 PROD 前请完成接口真实联调。");
        }
        if (!isDestructiveClearAllowed(environmentMode)) {
            warnings.add("真实清理测试数据已被服务端硬保护阻断；如需在测试环境清理，请确认非生产库后显式设置 SEEDCRM_ALLOW_CLEAR_TEST_DATA=true。");
        }
        return warnings;
    }

    private SystemGoLiveDtos.TableCountResponse countTable(TableTarget target) {
        SystemGoLiveDtos.TableCountResponse item = new SystemGoLiveDtos.TableCountResponse();
        item.setTableName(target.tableName());
        item.setCategory(target.category());
        try {
            item.setRowCount(tableCount(target.tableName()));
            item.setExists(true);
        } catch (DataAccessException exception) {
            item.setRowCount(0L);
            item.setExists(false);
        }
        return item;
    }

    private SystemGoLiveDtos.TableOperationResult clearTable(TableTarget target, boolean dryRun) {
        SystemGoLiveDtos.TableOperationResult result = new SystemGoLiveDtos.TableOperationResult();
        result.setTableName(target.tableName());
        result.setCategory(target.category());
        try {
            long count = tableCount(target.tableName());
            result.setRowCountBefore(count);
            if (dryRun) {
                result.setAffectedRows(0);
                result.setSkipped(true);
                result.setMessage("dry-run only");
                return result;
            }
            int affectedRows = jdbcTemplate.update("DELETE FROM " + target.tableName());
            result.setAffectedRows(affectedRows);
            result.setSkipped(false);
            result.setMessage("cleared");
            return result;
        } catch (DataAccessException exception) {
            result.setRowCountBefore(0L);
            result.setAffectedRows(0);
            result.setSkipped(true);
            result.setMessage("table missing or unavailable");
            return result;
        }
    }

    private long tableCount(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + tableName, Long.class);
        return count == null ? 0L : count;
    }

    private int integrationProviderCount(String providerCode) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM integration_provider_config
                    WHERE provider_code = ?
                    """, Integer.class, providerCode);
            return count == null ? 0 : count;
        } catch (DataAccessException exception) {
            return 0;
        }
    }

    private ProviderReadiness integrationProviderReadiness(String providerCode) {
        try {
            Map<String, Object> provider = jdbcTemplate.queryForMap("""
                    SELECT provider_code, enabled, execution_mode, base_url, voucher_verify_path, verify_code_field,
                           auth_status, access_token, refresh_token, token_expires_at, refresh_token_expires_at,
                           last_test_status
                    FROM integration_provider_config
                    WHERE provider_code = ?
                    LIMIT 1
                    """, providerCode);
            List<String> missing = new ArrayList<>();
            if (!isEnabled(provider.get("enabled"))) {
                missing.add("接口未启用");
            }
            if (!"LIVE".equals(normalizeValue(provider.get("execution_mode")))) {
                missing.add("运行模式不是 LIVE");
            }
            if (!StringUtils.hasText(asString(provider.get("base_url")))) {
                missing.add("开放平台域名");
            }
            if (!StringUtils.hasText(asString(provider.get("voucher_verify_path")))) {
                missing.add("券核销接口路径");
            }
            if (!StringUtils.hasText(asString(provider.get("verify_code_field")))) {
                missing.add("券码字段名");
            }
            if (!"SUCCESS".equals(normalizeValue(provider.get("last_test_status")))) {
                missing.add("最近连接/核销配置测试未通过");
            }
            if ("DOUYIN_LAIKE".equals(providerCode) && !hasUsableDouyinAuthorization(provider)) {
                missing.add("抖音授权 Token 不可用或已过期");
            }
            if (missing.isEmpty()) {
                return new ProviderReadiness(true, providerCode + " 已切到 LIVE，券核销配置和最近测试均已通过。");
            }
            return new ProviderReadiness(false, providerCode + " 未就绪：" + String.join("、", missing)
                    + "；真实团购核销会被阻断，订单不会进入服务确认单。");
        } catch (EmptyResultDataAccessException exception) {
            return new ProviderReadiness(false, providerCode + " 未配置，真实团购核销会被阻断。");
        } catch (DataAccessException exception) {
            return new ProviderReadiness(false, providerCode + " 配置表不可用，无法完成上线预检。");
        }
    }

    private boolean hasUsableDouyinAuthorization(Map<String, Object> provider) {
        String authStatus = normalizeValue(provider.get("auth_status"));
        if (!List.of("AUTHORIZED", "CONNECTED").contains(authStatus)) {
            return false;
        }
        LocalDateTime tokenExpiresAt = asLocalDateTime(provider.get("token_expires_at"));
        if (StringUtils.hasText(asString(provider.get("access_token")))
                && tokenExpiresAt != null
                && tokenExpiresAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
            return true;
        }
        LocalDateTime refreshExpiresAt = asLocalDateTime(provider.get("refresh_token_expires_at"));
        return StringUtils.hasText(asString(provider.get("refresh_token")))
                && (refreshExpiresAt == null || refreshExpiresAt.isAfter(LocalDateTime.now().plusMinutes(5)));
    }

    private boolean isEnabled(Object value) {
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return "1".equals(asString(value)) || "true".equalsIgnoreCase(asString(value));
    }

    private String normalizeValue(Object value) {
        return StringUtils.hasText(asString(value)) ? asString(value).trim().toUpperCase(Locale.ROOT) : "";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (!StringUtils.hasText(asString(value))) {
            return null;
        }
        try {
            return LocalDateTime.parse(asString(value).replace(' ', 'T'));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private int resetIntegrationProvidersToMock() {
        if (!hasTable("integration_provider_config")) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE integration_provider_config
                SET execution_mode = 'MOCK',
                    auth_status = 'MOCK',
                    last_test_status = NULL,
                    last_test_message = NULL,
                    updated_at = ?
                """, LocalDateTime.now());
    }

    private int disableSchedulers() {
        if (!hasTable("scheduler_job")) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE scheduler_job
                SET status = 'DISABLED',
                    updated_at = ?
                """, LocalDateTime.now());
    }

    private boolean hasTable(String tableName) {
        try {
            tableCount(tableName);
            return true;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private void upsertConfig(String key,
                              String value,
                              String valueType,
                              String description,
                              PermissionRequestContext context) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_config
                WHERE scope_type = 'GLOBAL' AND scope_id = 'GLOBAL' AND config_key = ?
                """, Integer.class, key);
        LocalDateTime now = LocalDateTime.now();
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE system_config
                    SET config_value = ?, value_type = ?, description = ?, enabled = 1, update_time = ?
                    WHERE scope_type = 'GLOBAL' AND scope_id = 'GLOBAL' AND config_key = ?
                    """, value, valueType, description, now, key);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO system_config(config_key, config_value, value_type, scope_type, scope_id, enabled, description, create_time, update_time)
                    VALUES (?, ?, ?, 'GLOBAL', 'GLOBAL', 1, ?, ?, ?)
                    """, key, value, valueType, description, now, now);
        }
        jdbcTemplate.update("""
                INSERT INTO system_config_change_log(
                    config_key, scope_type, scope_id, before_value, after_value, actor_role_code, actor_user_id, summary, create_time
                )
                VALUES (?, 'GLOBAL', 'GLOBAL', NULL, ?, ?, ?, ?, ?)
                """, key, value,
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                "上线工具初始化配置",
                now);
    }

    private SystemGoLiveDtos.OperationResponse baseOperationResponse(String operation,
                                                                     String environmentMode,
                                                                     boolean dryRun) {
        SystemGoLiveDtos.OperationResponse response = new SystemGoLiveDtos.OperationResponse();
        response.setOperation(operation);
        response.setEnvironmentMode(environmentMode);
        response.setDryRun(dryRun);
        response.setAffectedRows(0);
        response.setOperatedAt(LocalDateTime.now());
        return response;
    }

    private String currentEnvironmentMode() {
        return normalizeEnvironment(systemConfigService.getString(ENVIRONMENT_MODE_KEY, DEFAULT_ENVIRONMENT_MODE));
    }

    private String normalizeEnvironment(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_ENVIRONMENT_MODE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (List.of("LOCAL", "DEV", "TEST", "STAGING", "PROD").contains(normalized)) {
            return normalized;
        }
        return DEFAULT_ENVIRONMENT_MODE;
    }

    private boolean isNonProductionEnvironment(String environmentMode) {
        return !"PROD".equals(normalizeEnvironment(environmentMode));
    }

    private boolean isDestructiveClearAllowed(String environmentMode) {
        return isNonProductionEnvironment(environmentMode)
                && !isProdProfileActive()
                && isClearFlagEnabled();
    }

    private boolean isProdProfileActive() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private boolean isClearFlagEnabled() {
        if (environment == null) {
            return true;
        }
        return Boolean.parseBoolean(environment.getProperty(
                "seedcrm.go-live.allow-destructive-clear-test-data",
                "false"));
    }

    private void requireConfirmText(String actual, String expected) {
        if (!expected.equals(StringUtils.hasText(actual) ? actual.trim() : "")) {
            throw new BusinessException("confirmText must be " + expected);
        }
    }

    private boolean isValidHttpUrl(String value) {
        return StringUtils.hasText(value)
                && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private boolean isLocalUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("127.0.0.1")
                || normalized.contains("localhost")
                || normalized.contains("0.0.0.0");
    }

    private List<TableTarget> allClearTargets() {
        List<TableTarget> targets = new ArrayList<>();
        targets.addAll(BUSINESS_DATA_TABLES);
        targets.addAll(QUEUE_DATA_TABLES);
        targets.addAll(OPERATIONAL_LOG_TABLES);
        return targets;
    }

    private static TableTarget table(String tableName, String category) {
        return new TableTarget(tableName, category);
    }

    private record TableTarget(String tableName, String category) {
    }

    private record ProviderReadiness(boolean ok, String message) {
    }
}
