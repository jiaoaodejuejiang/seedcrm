package com.seedcrm.crm.scheduler.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
public class DistributionOpenController {

    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final String DISTRIBUTION_EVENT_ENDPOINT = "/open/distribution/events";

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final DistributionEventIngestService distributionEventIngestService;

    public DistributionOpenController(IntegrationProviderConfigMapper providerConfigMapper,
                                      DistributionEventIngestService distributionEventIngestService) {
        this.providerConfigMapper = providerConfigMapper;
        this.distributionEventIngestService = distributionEventIngestService;
    }

    @GetMapping("/leads")
    public ApiResponse<Map<String, Object>> listLeadsByGet(@RequestParam Map<String, String> parameters,
                                                           HttpServletRequest request) {
        return ApiResponse.success(buildLeadResponse(parameters, request));
    }

    @PostMapping("/leads")
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
    public ApiResponse<DistributionEventResponse> receiveEvent(@RequestBody JsonNode body,
                                                               HttpServletRequest request) {
        return ApiResponse.success(distributionEventIngestService.ingest(body, request));
    }

    private Map<String, Object> buildLeadResponse(Map<String, String> parameters, HttpServletRequest request) {
        IntegrationProviderConfig provider = findDistributionProvider();
        validateOpenRequest(provider, parameters);
        int page = Math.max(1, parseInt(parameters.get("page"), 1));
        int pageSize = Math.min(100, Math.max(1, parseInt(parameters.get("page_size"), parseInt(parameters.get("pageSize"), 30))));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("traceId", UUID.randomUUID().toString());
        response.put("mode", provider == null ? "MOCK" : normalize(provider.getExecutionMode(), "MOCK"));
        response.put("providerCode", PROVIDER_DISTRIBUTION);
        response.put("deprecatedEndpoint", true);
        response.put("contractVersion", "distribution-scheme-b-v1");
        response.put("message", "当前分销方案B不再向外部分销系统输出 Clue 客资；外部分销已支付订单请推送到 " + DISTRIBUTION_EVENT_ENDPOINT);
        response.put("dataPolicy", "distribution paid order 不进入 Clue；SeedCRM 只承接 Customer + Order(paid) + PlanOrder 履约状态回推");
        response.put("supportedInboundEndpoint", DISTRIBUTION_EVENT_ENDPOINT);
        response.put("clueCreated", false);
        response.put("customerOrderCreatedByThisEndpoint", false);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("receivedAt", LocalDateTime.now().toString());
        response.put("requestIp", request == null ? null : request.getRemoteAddr());
        response.put("records", java.util.List.of());
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
        String sign = parameters.get("sign");
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(sign)) {
            throw new BusinessException("LIVE 分销接口必须携带 app_id、timestamp、sign");
        }
        if (!appId.equals(provider.getAppId())) {
            throw new BusinessException("分销接口 app_id 不匹配");
        }
        if (!StringUtils.hasText(provider.getClientSecret())) {
            throw new BusinessException("分销接口未配置 AppSecret");
        }
        String page = firstNonBlank(parameters.get("page"), "1");
        String pageSize = firstNonBlank(parameters.get("page_size"), parameters.get("pageSize"), "30");
        String source = appId + "|" + timestamp + "|" + page + "|" + pageSize;
        if (!hmacSha256(source, provider.getClientSecret()).equalsIgnoreCase(sign.trim())) {
            throw new BusinessException("分销接口签名校验失败");
        }
    }

    private IntegrationProviderConfig findDistributionProvider() {
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, PROVIDER_DISTRIBUTION)
                .last("LIMIT 1"));
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
}
