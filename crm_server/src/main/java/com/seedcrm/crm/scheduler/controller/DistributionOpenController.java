package com.seedcrm.crm.scheduler.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventRequest;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/distribution")
@Tag(name = "分销 Open API", description = "外部分销方案 B 受控入口。已支付订单进入 Customer + Order(paid)，不进入 Clue。")
public class DistributionOpenController {

    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final String DISTRIBUTION_EVENT_ENDPOINT = "/open/distribution/events";
    private static final Duration LIVE_TIMESTAMP_WINDOW = Duration.ofMinutes(10);
    private static final Duration NONCE_TTL = Duration.ofMinutes(15);
    private static final Map<String, Instant> LIVE_NONCE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, RateLimitWindow> RATE_LIMIT_WINDOWS = new ConcurrentHashMap<>();
    private static final Map<String, CachedLeadResponse> LEAD_RESPONSE_CACHE = new ConcurrentHashMap<>();

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final DistributionEventIngestService distributionEventIngestService;

    public DistributionOpenController(IntegrationProviderConfigMapper providerConfigMapper,
                                      DistributionEventIngestService distributionEventIngestService) {
        this.providerConfigMapper = providerConfigMapper;
        this.distributionEventIngestService = distributionEventIngestService;
    }

    @GetMapping("/leads")
    @Operation(
            summary = "兼容旧分销客资拉取接口（GET）",
            description = "方案 B 不再向外部分销系统输出 Clue 客资；该接口仅返回空列表和迁移提示，不创建 Clue / Customer / Order。")
    public ApiResponse<Map<String, Object>> listLeadsByGet(@RequestParam Map<String, String> parameters,
                                                           HttpServletRequest request) {
        return ApiResponse.success(buildLeadResponse(parameters, request));
    }

    @PostMapping("/leads")
    @Operation(
            summary = "兼容旧分销客资拉取接口（POST）",
            description = "方案 B 不再向外部分销系统输出 Clue 客资；该接口仅返回空列表和迁移提示，不创建 Clue / Customer / Order。")
    public ApiResponse<Map<String, Object>> listLeadsByPost(@RequestParam Map<String, String> parameters,
                                                            @RequestBody(required = false) Map<String, Object> body,
                                                            HttpServletRequest request) {
        Map<String, String> merged = new LinkedHashMap<>(parameters == null ? Map.of() : parameters);
        if (body != null) {
            body.forEach((key, value) -> merged.put(key, value == null ? null : String.valueOf(value)));
        }
        return ApiResponse.success(buildLeadResponse(merged, request));
    }

    @PostMapping("/events")
    @Operation(
            summary = "接收外部分销已支付 / 退款 / 取消事件",
            description = "所有事件必须经 DistributionEventIngestService 统一处理。仅 distribution.order.paid 允许在同一事务内创建或匹配 Customer，并创建 Order(paid)。",
            security = {
                    @SecurityRequirement(name = "PartnerCode"),
                    @SecurityRequirement(name = "IdempotencyKey"),
                    @SecurityRequirement(name = "Timestamp"),
                    @SecurityRequirement(name = "Nonce"),
                    @SecurityRequirement(name = "Signature")
            },
            parameters = {
                    @Parameter(name = "X-Partner-Code", in = ParameterIn.HEADER, required = true, description = "外部合作方编码"),
                    @Parameter(name = "X-Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "幂等键 / 防重复编号"),
                    @Parameter(name = "X-Timestamp", in = ParameterIn.HEADER, required = true, description = "签名时间戳"),
                    @Parameter(name = "X-Nonce", in = ParameterIn.HEADER, required = true, description = "防重放随机串"),
                    @Parameter(name = "X-Signature", in = ParameterIn.HEADER, required = true, description = "HMAC-SHA256 签名")
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "外部分销入站事件报文",
                    content = @Content(schema = @Schema(implementation = DistributionEventRequest.class))))
    public ApiResponse<DistributionEventResponse> receiveEvent(@RequestBody JsonNode body,
                                                               HttpServletRequest request) {
        IntegrationProviderConfig provider = findProvider(resolvePartnerCode(body, request));
        enforceRateLimit(provider, request);
        return ApiResponse.success(distributionEventIngestService.ingest(body, request));
    }

    private Map<String, Object> buildLeadResponse(Map<String, String> parameters, HttpServletRequest request) {
        IntegrationProviderConfig provider = findDistributionProvider();
        validateOpenRequest(provider, parameters);
        enforceRateLimit(provider, request);
        int page = Math.max(1, parseInt(parameters.get("page"), 1));
        int pageSize = Math.min(100, Math.max(1, parseInt(parameters.get("page_size"), parseInt(parameters.get("pageSize"), 30))));
        CachedLeadResponse cached = readLeadCache(provider, page, pageSize);
        if (cached != null) {
            return decorateLeadResponse(cached.response(), request, true, cached.expiresAt());
        }
        Map<String, Object> response = buildLeadResponseBody(provider, page, pageSize);
        Instant cacheExpiresAt = writeLeadCache(provider, page, pageSize, response);
        return decorateLeadResponse(response, request, false, cacheExpiresAt);
    }

    private Map<String, Object> buildLeadResponseBody(IntegrationProviderConfig provider, int page, int pageSize) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", provider == null ? "MOCK" : normalize(provider.getExecutionMode(), "MOCK"));
        response.put("providerCode", PROVIDER_DISTRIBUTION);
        response.put("deprecatedEndpoint", true);
        response.put("contractVersion", "distribution-scheme-b-v1");
        response.put("message", "当前分销方案B不再向外部分销系统输出 Clue 客资；外部分销已支付订单请推送到 " + DISTRIBUTION_EVENT_ENDPOINT);
        response.put("dataPolicy", "distribution paid order 不进入 Clue；SeedCRM 只承接 Customer + Order(paid) + PlanOrder 履约状态回推");
        response.put("securityPolicy", "LIVE 模式必须携带 app_id、timestamp、nonce、sign；签名源 app_id|timestamp|nonce|page|page_size");
        response.put("rateLimitPerMinute", provider == null ? null : provider.getRateLimitPerMinute());
        response.put("cacheTtlSeconds", provider == null ? null : provider.getCacheTtlSeconds());
        response.put("supportedInboundEndpoint", DISTRIBUTION_EVENT_ENDPOINT);
        response.put("clueCreated", false);
        response.put("customerOrderCreatedByThisEndpoint", false);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("records", java.util.List.of());
        return response;
    }

    private Map<String, Object> decorateLeadResponse(Map<String, Object> source,
                                                     HttpServletRequest request,
                                                     boolean cacheHit,
                                                     Instant cacheExpiresAt) {
        Map<String, Object> response = new LinkedHashMap<>(source);
        response.put("traceId", UUID.randomUUID().toString());
        response.put("receivedAt", LocalDateTime.now().toString());
        response.put("requestIp", request == null ? null : request.getRemoteAddr());
        response.put("cacheHit", cacheHit);
        response.put("cacheExpiresAt", cacheExpiresAt == null ? null : cacheExpiresAt.toString());
        return response;
    }

    private void validateOpenRequest(IntegrationProviderConfig provider, Map<String, String> parameters) {
        if (provider == null || !"LIVE".equalsIgnoreCase(provider.getExecutionMode())) {
            return;
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            throw new BusinessException("分销接口已停用");
        }
        String appId = firstNonBlank(parameters.get("app_id"), parameters.get("appId"));
        String timestamp = parameters.get("timestamp");
        String nonce = parameters.get("nonce");
        String sign = parameters.get("sign");
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(sign)) {
            throw new BusinessException("LIVE 分销接口必须携带 app_id、timestamp、nonce、sign");
        }
        if (!appId.equals(provider.getAppId())) {
            throw new BusinessException("分销接口 app_id 不匹配");
        }
        validateTimestampWindow(timestamp);
        validateNonceNotReplayed(appId, nonce);
        if (!StringUtils.hasText(provider.getClientSecret())) {
            throw new BusinessException("分销接口未配置 AppSecret");
        }
        String page = firstNonBlank(parameters.get("page"), "1");
        String pageSize = firstNonBlank(parameters.get("page_size"), parameters.get("pageSize"), "30");
        String source = appId + "|" + timestamp + "|" + nonce + "|" + page + "|" + pageSize;
        if (!hmacSha256(source, provider.getClientSecret()).equalsIgnoreCase(sign.trim())) {
            throw new BusinessException("分销接口签名校验失败");
        }
    }

    private IntegrationProviderConfig findDistributionProvider() {
        return findProvider(PROVIDER_DISTRIBUTION);
    }

    private IntegrationProviderConfig findProvider(String providerCode) {
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, normalize(providerCode, PROVIDER_DISTRIBUTION))
                .last("LIMIT 1"));
    }

    private String resolvePartnerCode(JsonNode body, HttpServletRequest request) {
        String headerPartnerCode = request == null ? null : request.getHeader("X-Partner-Code");
        if (StringUtils.hasText(headerPartnerCode)) {
            return headerPartnerCode.trim();
        }
        JsonNode partnerCodeNode = body == null ? null : body.get("partnerCode");
        return partnerCodeNode == null || partnerCodeNode.isNull() ? PROVIDER_DISTRIBUTION : partnerCodeNode.asText(PROVIDER_DISTRIBUTION);
    }

    private int parseInt(String value, int fallback) {
        try {
            return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
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

    private void validateTimestampWindow(String timestamp) {
        Instant requestTime = parseTimestamp(timestamp);
        Instant now = Instant.now();
        Duration skew = requestTime.isAfter(now)
                ? Duration.between(now, requestTime)
                : Duration.between(requestTime, now);
        if (skew.compareTo(LIVE_TIMESTAMP_WINDOW) > 0) {
            throw new BusinessException("分销接口 timestamp 已过期或超出允许时间窗");
        }
    }

    private Instant parseTimestamp(String timestamp) {
        if (!StringUtils.hasText(timestamp)) {
            throw new BusinessException("分销接口 timestamp 不能为空");
        }
        String value = timestamp.trim();
        try {
            long raw = Long.parseLong(value);
            return raw > 9_999_999_999L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException exception) {
                try {
                    return OffsetDateTime.parse(value).toInstant();
                } catch (DateTimeParseException nested) {
                    throw new BusinessException("分销接口 timestamp 格式不正确");
                }
            }
        }
    }

    private void validateNonceNotReplayed(String appId, String nonce) {
        pruneNonceCache();
        String cacheKey = appId.trim() + ":" + nonce.trim();
        Instant now = Instant.now();
        Instant previous = LIVE_NONCE_CACHE.putIfAbsent(cacheKey, now.plus(NONCE_TTL));
        if (previous != null && previous.isAfter(now)) {
            throw new BusinessException("分销接口 nonce 已被使用，请重新生成");
        }
        LIVE_NONCE_CACHE.put(cacheKey, now.plus(NONCE_TTL));
    }

    private void pruneNonceCache() {
        Instant now = Instant.now();
        LIVE_NONCE_CACHE.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isAfter(now));
    }

    private CachedLeadResponse readLeadCache(IntegrationProviderConfig provider, int page, int pageSize) {
        Integer cacheTtlSeconds = provider == null ? null : provider.getCacheTtlSeconds();
        if (cacheTtlSeconds == null || cacheTtlSeconds <= 0) {
            return null;
        }
        pruneLeadCache();
        CachedLeadResponse cached = LEAD_RESPONSE_CACHE.get(leadCacheKey(provider, page, pageSize));
        return cached == null || !cached.expiresAt().isAfter(Instant.now()) ? null : cached;
    }

    private Instant writeLeadCache(IntegrationProviderConfig provider, int page, int pageSize, Map<String, Object> response) {
        Integer cacheTtlSeconds = provider == null ? null : provider.getCacheTtlSeconds();
        if (cacheTtlSeconds == null || cacheTtlSeconds <= 0) {
            return null;
        }
        Instant expiresAt = Instant.now().plusSeconds(cacheTtlSeconds);
        LEAD_RESPONSE_CACHE.put(
                leadCacheKey(provider, page, pageSize),
                new CachedLeadResponse(expiresAt, new LinkedHashMap<>(response)));
        return expiresAt;
    }

    private String leadCacheKey(IntegrationProviderConfig provider, int page, int pageSize) {
        return normalize(provider == null ? PROVIDER_DISTRIBUTION : provider.getProviderCode(), PROVIDER_DISTRIBUTION)
                + ":" + normalize(provider == null ? null : provider.getExecutionMode(), "MOCK")
                + ":" + page + ":" + pageSize;
    }

    private void pruneLeadCache() {
        Instant now = Instant.now();
        LEAD_RESPONSE_CACHE.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().expiresAt().isAfter(now));
    }

    private void enforceRateLimit(IntegrationProviderConfig provider, HttpServletRequest request) {
        if (provider == null || provider.getRateLimitPerMinute() == null || provider.getRateLimitPerMinute() <= 0) {
            return;
        }
        String providerCode = normalize(provider.getProviderCode(), PROVIDER_DISTRIBUTION);
        String clientKey = firstNonBlank(
                request == null ? null : request.getHeader("X-Partner-Code"),
                request == null ? null : request.getHeader("X-Forwarded-For"),
                request == null ? null : request.getRemoteAddr(),
                "unknown");
        String bucketKey = providerCode + ":" + clientKey.split(",")[0].trim();
        long minute = Instant.now().getEpochSecond() / 60;
        synchronized (RATE_LIMIT_WINDOWS) {
            RateLimitWindow window = RATE_LIMIT_WINDOWS.compute(bucketKey, (key, current) -> {
                if (current == null || current.minute() != minute) {
                    return new RateLimitWindow(minute, 0);
                }
                return current;
            });
            int nextCount = window.count() + 1;
            if (nextCount > provider.getRateLimitPerMinute()) {
                throw new BusinessException("分销接口请求过于频繁，请稍后重试");
            }
            RATE_LIMIT_WINDOWS.put(bucketKey, new RateLimitWindow(minute, nextCount));
            RATE_LIMIT_WINDOWS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().minute() < minute - 1);
        }
    }

    private String hmacSha256(String source, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new BusinessException("分销接口签名生成失败");
        }
    }

    private record RateLimitWindow(long minute, int count) {
    }

    private record CachedLeadResponse(Instant expiresAt, Map<String, Object> response) {
    }
}
