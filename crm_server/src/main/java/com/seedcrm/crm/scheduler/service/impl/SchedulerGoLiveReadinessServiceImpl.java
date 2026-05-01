package com.seedcrm.crm.scheduler.service.impl;

import com.seedcrm.crm.scheduler.dto.SchedulerGoLiveReadinessResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerGoLiveReadinessResponse.ReadinessCheck;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerJob;
import com.seedcrm.crm.scheduler.service.SchedulerGoLiveReadinessService;
import com.seedcrm.crm.scheduler.service.SchedulerIdempotencyHealthService;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.service.SchedulerService;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos.DomainSettingsResponse;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SchedulerGoLiveReadinessServiceImpl implements SchedulerGoLiveReadinessService {

    private static final String DEFAULT_PROVIDER = "DISTRIBUTION";
    private static final List<String> DISTRIBUTION_JOBS = List.of(
            "DISTRIBUTION_OUTBOX_PROCESS",
            "DISTRIBUTION_EXCEPTION_RETRY",
            "DISTRIBUTION_STATUS_CHECK",
            "DISTRIBUTION_RECONCILE_PULL");

    private final SystemConfigService systemConfigService;
    private final SchedulerIntegrationService schedulerIntegrationService;
    private final SchedulerService schedulerService;
    private final SchedulerIdempotencyHealthService schedulerIdempotencyHealthService;
    private final boolean openApiEnabled;
    private final boolean localTokenBypassEnabled;

    public SchedulerGoLiveReadinessServiceImpl(SystemConfigService systemConfigService,
                                               SchedulerIntegrationService schedulerIntegrationService,
                                               SchedulerService schedulerService,
                                               SchedulerIdempotencyHealthService schedulerIdempotencyHealthService,
                                               @Value("${seedcrm.openapi.enabled:true}") boolean openApiEnabled,
                                               @Value("${seedcrm.openapi.allow-local-without-token:true}") boolean localTokenBypassEnabled) {
        this.systemConfigService = systemConfigService;
        this.schedulerIntegrationService = schedulerIntegrationService;
        this.schedulerService = schedulerService;
        this.schedulerIdempotencyHealthService = schedulerIdempotencyHealthService;
        this.openApiEnabled = openApiEnabled;
        this.localTokenBypassEnabled = localTokenBypassEnabled;
    }

    @Override
    public SchedulerGoLiveReadinessResponse inspect(String providerCode) {
        String normalizedProviderCode = normalizeOrDefault(providerCode, DEFAULT_PROVIDER);
        DomainSettingsResponse domains = systemConfigService.getDomainSettings();
        IntegrationProviderConfig provider = findProvider(normalizedProviderCode);
        SchedulerIdempotencyHealthResponse health = schedulerIdempotencyHealthService.inspect(normalizedProviderCode);
        List<SchedulerJob> jobs = schedulerService.listJobs();

        List<ReadinessCheck> checks = new ArrayList<>();
        checks.add(checkHttpsPublic(
                "SYSTEM_DOMAIN",
                "系统后台域名",
                domains.getSystemBaseUrl(),
                "必须是线上 HTTPS 域名，供后台页面、扫码页和操作员访问"));
        checks.add(checkHttpsPublic(
                "API_DOMAIN",
                "API 域名",
                domains.getApiBaseUrl(),
                "必须是线上 HTTPS 域名，供 Open API、回调、Swagger/OpenAPI 和三方平台调用"));
        checks.add(checkUrlPath(
                "DISTRIBUTION_EVENT_INGEST_URL",
                "分销已支付订单入站地址",
                domains.getEventIngestUrl(),
                "/open/distribution/events",
                "外部分销系统必须回调到该地址，已支付订单才允许进入 Customer + Order(paid)"));
        checks.add(checkProvider(provider));
        checks.add(checkProviderSecret(provider));
        checks.add(checkProviderCallback(provider));
        checks.add(checkProviderStatusPath(provider));
        checks.add(checkProviderReconcilePath(provider));
        checks.add(checkIdempotency(health));
        checks.add(checkOpenApiProductionGuard());
        checks.add(checkLocalTokenBypassGuard());
        checks.add(checkSchedulerJobs(jobs));

        SchedulerGoLiveReadinessResponse response = new SchedulerGoLiveReadinessResponse();
        response.setGeneratedAt(LocalDateTime.now());
        response.setProviderCode(normalizedProviderCode);
        response.setEnvironmentMode(provider == null ? "UNKNOWN" : firstNonBlank(provider.getExecutionMode(), "UNKNOWN"));
        response.setChecks(checks);
        response.setBlockers(checks.stream()
                .filter(item -> "BLOCKER".equals(item.getSeverity()) && !"PASS".equals(item.getStatus()))
                .map(ReadinessCheck::getRecommendedAction)
                .filter(StringUtils::hasText)
                .toList());
        response.setRecommendedActions(checks.stream()
                .filter(item -> !"PASS".equals(item.getStatus()))
                .map(ReadinessCheck::getRecommendedAction)
                .filter(StringUtils::hasText)
                .toList());
        response.setOverallStatus(resolveOverallStatus(checks));
        return response;
    }

    private IntegrationProviderConfig findProvider(String providerCode) {
        return schedulerIntegrationService.listProviders().stream()
                .filter(item -> same(providerCode, item.getProviderCode()))
                .findFirst()
                .orElse(null);
    }

    private ReadinessCheck checkHttpsPublic(String code, String title, String value, String impact) {
        if (!StringUtils.hasText(value)) {
            return check(code, title, "FAIL", "BLOCKER", value, "https://your-domain.example", impact, "请先在域名配置中填写线上 HTTPS 域名");
        }
        if (!isValidUrl(value)) {
            return check(code, title, "FAIL", "BLOCKER", value, "合法 URL", impact, "当前域名格式不正确，请重新填写");
        }
        if (!value.startsWith("https://")) {
            return check(code, title, "FAIL", "BLOCKER", value, "https://", impact, "正式上线前必须使用 HTTPS 域名");
        }
        if (isLocalAddress(value)) {
            return check(code, title, "FAIL", "BLOCKER", value, "公网可访问域名", impact, "正式上线前不能使用 127.0.0.1、localhost 或内网地址");
        }
        return check(code, title, "PASS", "INFO", value, "线上 HTTPS 公网域名", impact, null);
    }

    private ReadinessCheck checkUrlPath(String code, String title, String value, String expectedPath, String impact) {
        if (!StringUtils.hasText(value)) {
            return check(code, title, "FAIL", "BLOCKER", value, expectedPath, impact, "请先生成并复制正确的入站地址");
        }
        if (!value.endsWith(expectedPath)) {
            return check(code, title, "WARN", "WARNING", value, expectedPath, impact, "请确认反向代理不会改写分销入站路径");
        }
        return check(code, title, "PASS", "INFO", value, expectedPath, impact, null);
    }

    private ReadinessCheck checkProvider(IntegrationProviderConfig provider) {
        if (provider == null) {
            return check("PROVIDER_CONFIG", "分销接口配置", "FAIL", "BLOCKER", null, DEFAULT_PROVIDER,
                    "缺少分销接入配置时，方案 B 无法接收外部已支付订单",
                    "请先在分销接口页面保存 DISTRIBUTION 渠道配置");
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            return check("PROVIDER_CONFIG", "分销接口配置", "FAIL", "BLOCKER", "已停用", "启用",
                    "渠道停用后不会进行正式联调和调度补偿",
                    "请确认后启用分销接口配置");
        }
        if (!"LIVE".equals(normalize(provider.getExecutionMode()))) {
            return check("PROVIDER_CONFIG", "分销接口配置", "WARN", "WARNING", provider.getExecutionMode(), "LIVE",
                    "MOCK 模式只能用于联调，不能承接正式外部分销订单",
                    "联调通过后再受控切换为 LIVE 模式");
        }
        return check("PROVIDER_CONFIG", "分销接口配置", "PASS", "INFO", "LIVE", "LIVE",
                "分销渠道已处于真实模式", null);
    }

    private ReadinessCheck checkProviderSecret(IntegrationProviderConfig provider) {
        boolean configured = provider != null && Boolean.TRUE.equals(provider.getClientSecretConfigured());
        return configured
                ? check("PROVIDER_SECRET", "分销签名密钥", "PASS", "INFO", "已配置", "已配置",
                "LIVE 入站、回推、状态回查和对账必须具备签名密钥", null)
                : check("PROVIDER_SECRET", "分销签名密钥", "FAIL", "BLOCKER", "未配置", "已配置",
                "缺少密钥会导致正式请求无法验签或签名",
                "请在分销接口配置中填写 AppSecret / 签名密钥");
    }

    private ReadinessCheck checkProviderCallback(IntegrationProviderConfig provider) {
        String value = provider == null ? null : firstNonBlank(provider.getCallbackUrl(), provider.getEndpointPath());
        if (!StringUtils.hasText(value)) {
            return check("FULFILLMENT_CALLBACK", "履约回推目标", "FAIL", "BLOCKER", null, "外部分销系统回推 URL",
                    "Order used 后需要通过 Outbox 异步回推外部分销系统",
                    "请配置履约状态回推目标地址");
        }
        if ("LIVE".equals(normalize(provider == null ? null : provider.getExecutionMode())) && !value.startsWith("https://")) {
            return check("FULFILLMENT_CALLBACK", "履约回推目标", "FAIL", "BLOCKER", value, "https://",
                    "正式回推地址必须使用 HTTPS",
                    "请将履约回推目标改为 HTTPS 地址");
        }
        return check("FULFILLMENT_CALLBACK", "履约回推目标", "PASS", "INFO", value, "外部分销系统回推 URL",
                "Outbox 会异步推送 crm.order.used", null);
    }

    private ReadinessCheck checkProviderStatusPath(IntegrationProviderConfig provider) {
        return checkProviderPath(
                "STATUS_QUERY_PATH",
                "状态回查路径",
                provider == null ? null : provider.getStatusQueryPath(),
                "/open/distribution/orders/status",
                "状态回查用于取消、退款、异常补偿，不允许直接写核心表");
    }

    private ReadinessCheck checkProviderReconcilePath(IntegrationProviderConfig provider) {
        return checkProviderPath(
                "RECONCILE_PULL_PATH",
                "对账拉取路径",
                provider == null ? null : provider.getReconciliationPullPath(),
                "/open/distribution/orders/reconcile",
                "对账拉取只读取外部记录，如需改变订单状态必须转成入站事件重放");
    }

    private ReadinessCheck checkProviderPath(String code, String title, String value, String expected, String impact) {
        if (!StringUtils.hasText(value)) {
            return check(code, title, "FAIL", "BLOCKER", null, expected, impact, "请配置该接口路径，dry-run 通过后再开启真实调度");
        }
        return check(code, title, "PASS", "INFO", value, expected, impact, null);
    }

    private ReadinessCheck checkIdempotency(SchedulerIdempotencyHealthResponse health) {
        if (health != null && health.isHealthy()) {
            return check("IDEMPOTENCY_HEALTH", "防重复健康", "PASS", "INFO", health.getStatus(), "HEALTHY",
                    "幂等唯一约束和历史重复数据会影响正式入站防重", null);
        }
        String current = health == null ? "UNKNOWN" : health.getStatus() + ", duplicateGroups=" + health.getDuplicateGroupCount();
        return check("IDEMPOTENCY_HEALTH", "防重复健康", "FAIL", "BLOCKER", current, "HEALTHY",
                "存在重复接收记录或唯一约束缺失时，正式入站可能重复处理",
                "请先处理 callback log 重复数据并确认唯一索引已生效");
    }

    private ReadinessCheck checkOpenApiProductionGuard() {
        return openApiEnabled
                ? check("OPENAPI_PRODUCTION_GUARD", "生产 Swagger/OpenAPI", "WARN", "WARNING", "已开启", "生产默认关闭",
                "生产环境建议默认关闭 Swagger UI，仅在受控联调窗口开启",
                "上线 profile 中设置 SEEDCRM_OPENAPI_ENABLED=false")
                : check("OPENAPI_PRODUCTION_GUARD", "生产 Swagger/OpenAPI", "PASS", "INFO", "已关闭", "生产默认关闭",
                "生产 OpenAPI 暴露已受控", null);
    }

    private ReadinessCheck checkLocalTokenBypassGuard() {
        return localTokenBypassEnabled
                ? check("LOCAL_TOKEN_BYPASS", "本地免 token 开关", "FAIL", "BLOCKER", "已开启", "生产关闭",
                "生产环境开启本地免 token 会绕过正式鉴权边界",
                "上线 profile 中设置 SEEDCRM_OPENAPI_ALLOW_LOCAL_WITHOUT_TOKEN=false")
                : check("LOCAL_TOKEN_BYPASS", "本地免 token 开关", "PASS", "INFO", "已关闭", "生产关闭",
                "Open API 本地免 token 已关闭", null);
    }

    private ReadinessCheck checkSchedulerJobs(List<SchedulerJob> jobs) {
        List<String> enabledJobs = jobs == null ? List.of() : jobs.stream()
                .filter(item -> DISTRIBUTION_JOBS.contains(normalize(item.getJobCode())))
                .filter(item -> "ENABLED".equals(normalize(item.getStatus())) || "ACTIVE".equals(normalize(item.getStatus())))
                .map(SchedulerJob::getJobCode)
                .toList();
        if (enabledJobs.containsAll(DISTRIBUTION_JOBS)) {
            return check("DISTRIBUTION_SCHEDULER_JOBS", "分销调度任务", "PASS", "INFO", String.join(",", enabledJobs),
                    String.join(",", DISTRIBUTION_JOBS),
                    "方案 B 需要 Outbox、异常重试、状态回查和对账拉取四类任务", null);
        }
        List<String> missing = DISTRIBUTION_JOBS.stream()
                .filter(item -> !enabledJobs.contains(item))
                .toList();
        return check("DISTRIBUTION_SCHEDULER_JOBS", "分销调度任务", "WARN", "WARNING", String.join(",", enabledJobs),
                String.join(",", DISTRIBUTION_JOBS),
                "缺少调度任务会影响回推、重试、补偿或对账闭环",
                "请在任务调度中启用缺失任务：" + String.join("、", missing));
    }

    private ReadinessCheck check(String code,
                                 String title,
                                 String status,
                                 String severity,
                                 String currentValue,
                                 String expectedValue,
                                 String impact,
                                 String recommendedAction) {
        ReadinessCheck item = new ReadinessCheck();
        item.setCheckCode(code);
        item.setTitle(title);
        item.setStatus(status);
        item.setSeverity(severity);
        item.setCurrentValue(currentValue);
        item.setExpectedValue(expectedValue);
        item.setImpact(impact);
        item.setRecommendedAction(recommendedAction);
        return item;
    }

    private String resolveOverallStatus(List<ReadinessCheck> checks) {
        boolean hasBlocker = checks.stream()
                .anyMatch(item -> "BLOCKER".equals(item.getSeverity()) && !"PASS".equals(item.getStatus()));
        if (hasBlocker) {
            return "BLOCKED";
        }
        boolean hasWarning = checks.stream().anyMatch(item -> !"PASS".equals(item.getStatus()));
        return hasWarning ? "WARNING" : "READY";
    }

    private boolean isValidUrl(String value) {
        try {
            URI uri = URI.create(value);
            return StringUtils.hasText(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isLocalAddress(String value) {
        try {
            String host = URI.create(value).getHost();
            if (!StringUtils.hasText(host)) {
                return true;
            }
            String normalized = host.trim().toLowerCase(Locale.ROOT);
            return normalized.equals("localhost")
                    || normalized.equals("127.0.0.1")
                    || normalized.equals("0.0.0.0")
                    || normalized.startsWith("192.168.")
                    || normalized.startsWith("10.")
                    || normalized.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? normalize(value) : defaultValue;
    }

    private boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
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
}
