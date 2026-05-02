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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final String SYSTEM_BASE_URL_KEY = "system.domain.systemBaseUrl";
    private static final String API_BASE_URL_KEY = "system.domain.apiBaseUrl";
    private static final String DEFAULT_SYSTEM_BASE_URL = "http://127.0.0.1:8003";
    private static final String DEFAULT_API_BASE_URL = "http://127.0.0.1:8004";
    private static final String MASKED_VALUE = "******";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

    public SystemConfigServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                       actor_role_code, actor_user_id, summary, create_time
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
        response.setValidationMessage("validation passed");
        return response;
    }

    @Override
    @Transactional
    public SystemConfigDtos.ConfigResponse saveConfig(SystemConfigDtos.SaveConfigRequest request, PermissionRequestContext context) {
        NormalizedConfigInput input = normalizeConfigRequest(request);
        String beforeValue = findValue(input.key(), input.scopeType(), input.scopeId());
        LocalDateTime now = LocalDateTime.now();
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
                    config_key, scope_type, scope_id, before_value, after_value, actor_role_code, actor_user_id, summary, create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, input.key(), input.scopeType(), input.scopeId(), beforeValue, input.configValue(),
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                StringUtils.hasText(request.getSummary()) ? request.getSummary().trim() : "更新系统配置",
                now);
        return listConfigs(input.key()).stream()
                .filter(item -> input.key().equals(item.getConfigKey()))
                .filter(item -> input.scopeType().equals(item.getScopeType()))
                .filter(item -> input.scopeId().equals(item.getScopeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("config save failed"));
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
            throw new BusinessException("domain settings are required");
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
        item.setChangeType(resolveChangeType(beforeValue, afterValue));
        item.setRiskLevel(resolveRiskLevel(key));
        item.setImpactModules(resolveImpactModules(key));
        item.setActorRoleCode(rs.getString("actor_role_code"));
        item.setActorUserId(rs.getObject("actor_user_id") == null ? null : rs.getLong("actor_user_id"));
        item.setSummary(rs.getString("summary"));
        item.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        return item;
    }

    private NormalizedConfigInput normalizeConfigRequest(SystemConfigDtos.SaveConfigRequest request) {
        if (request == null || !StringUtils.hasText(request.getConfigKey())) {
            throw new BusinessException("configKey is required");
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

    private String maskIfSensitive(String key, String value) {
        if (!isSensitiveKey(key) || !StringUtils.hasText(value)) {
            return value;
        }
        return MASKED_VALUE;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace("-", "_").replace(".", "_");
        return SENSITIVE_KEY_MARKERS.stream().anyMatch(normalized::contains);
    }
}
