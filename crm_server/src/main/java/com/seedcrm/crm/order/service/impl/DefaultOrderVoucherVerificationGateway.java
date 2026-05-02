package com.seedcrm.crm.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.service.OrderVoucherVerificationGateway;
import com.seedcrm.crm.order.service.OrderVoucherVerificationResult;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import com.seedcrm.crm.scheduler.support.SchedulerRestClientFactory;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class DefaultOrderVoucherVerificationGateway implements OrderVoucherVerificationGateway {

    private static final String PROVIDER_DOUYIN = "DOUYIN_LAIKE";
    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final String MODE_LIVE = "LIVE";

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final SchedulerIntegrationService schedulerIntegrationService;
    private final ObjectMapper objectMapper;

    public DefaultOrderVoucherVerificationGateway(IntegrationProviderConfigMapper providerConfigMapper,
                                                  SchedulerIntegrationService schedulerIntegrationService,
                                                  ObjectMapper objectMapper) {
        this.providerConfigMapper = providerConfigMapper;
        this.schedulerIntegrationService = schedulerIntegrationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderVoucherVerificationResult verify(Order order, String verificationCode, String verificationMethod) {
        if (!isVoucherOrder(order)) {
            return OrderVoucherVerificationResult.skipped();
        }
        String providerCode = resolveProviderCode(order);
        IntegrationProviderConfig provider = findProvider(providerCode);
        if (provider == null) {
            throw new BusinessException("券码核销接口未配置：" + providerCode);
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            throw new BusinessException("券码核销接口已停用：" + providerCode);
        }
        String idempotencyKey = buildIdempotencyKey(providerCode, order, verificationCode);
        String executionMode = normalize(provider.getExecutionMode());
        if (!MODE_LIVE.equals(executionMode)) {
            return new OrderVoucherVerificationResult(providerCode, "MOCK", idempotencyKey,
                    "{\"success\":true,\"mode\":\"MOCK\",\"message\":\"voucher verification simulated\"}", true);
        }
        String url = resolveVerifyUrl(provider);
        String payload = buildPayload(provider, order, verificationCode, verificationMethod, idempotencyKey);
        String response;
        try {
            response = restClient(provider).post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.add("X-Provider-Code", providerCode);
                        headers.add("X-Idempotency-Key", idempotencyKey);
                        headers.add("X-Order-No", nullToEmpty(order.getOrderNo()));
                        String accessToken = schedulerIntegrationService.resolveProviderAccessToken(provider);
                        if (StringUtils.hasText(accessToken)) {
                            headers.setBearerAuth(accessToken.trim());
                            headers.add("Access-Token", accessToken.trim());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new BusinessException("券码核销接口调用失败：" + safeMessage(exception));
        }
        assertProviderResponseSuccess(response);
        return new OrderVoucherVerificationResult(providerCode, "LIVE", idempotencyKey, trim(response, 1000), true);
    }

    private boolean isVoucherOrder(Order order) {
        if (order == null || order.getType() == null) {
            return false;
        }
        return order.getType() == OrderType.COUPON.getCode()
                || order.getType() == OrderType.DISTRIBUTION_PRODUCT.getCode();
    }

    private String resolveProviderCode(Order order) {
        if (StringUtils.hasText(order.getExternalPartnerCode())) {
            return normalize(order.getExternalPartnerCode());
        }
        String sourceChannel = SourceChannel.resolveCode(order.getSourceChannel(), order.getSource());
        if (SourceChannel.DISTRIBUTOR.name().equals(sourceChannel)) {
            return PROVIDER_DISTRIBUTION;
        }
        if (SourceChannel.DOUYIN.name().equals(sourceChannel)) {
            return PROVIDER_DOUYIN;
        }
        throw new BusinessException("订单来源无法匹配核销通道，未标记为已核销");
    }

    private IntegrationProviderConfig findProvider(String providerCode) {
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, providerCode)
                .last("LIMIT 1"));
    }

    private String resolveVerifyUrl(IntegrationProviderConfig provider) {
        if (!StringUtils.hasText(provider.getVoucherVerifyPath())) {
            throw new BusinessException("券码核销接口地址未配置");
        }
        String rawVerifyPath = provider.getVoucherVerifyPath().trim();
        if (isAbsoluteUrl(rawVerifyPath)) {
            return rawVerifyPath;
        }
        String verifyPath = rawVerifyPath.startsWith("/") ? rawVerifyPath : "/" + rawVerifyPath;
        if (isAbsoluteUrl(verifyPath)) {
            return verifyPath;
        }
        String baseUrl = trimTrailingSlash(provider.getBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("券码核销接口地址未配置");
        }
        return baseUrl + verifyPath;
    }

    private String buildPayload(IntegrationProviderConfig provider,
                                Order order,
                                String verificationCode,
                                String verificationMethod,
                                String idempotencyKey) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("orderId", order.getId());
        root.put("orderNo", nullToEmpty(order.getOrderNo()));
        root.put("externalOrderId", nullToEmpty(order.getExternalOrderId()));
        root.put("externalTradeNo", nullToEmpty(order.getExternalTradeNo()));
        root.put("sourceChannel", SourceChannel.resolveCode(order.getSourceChannel(), order.getSource()));
        root.put("verificationCode", verificationCode);
        root.put("verificationMethod", verificationMethod);
        root.put("idempotencyKey", idempotencyKey);

        String codeField = StringUtils.hasText(provider.getVerifyCodeField())
                ? provider.getVerifyCodeField().trim()
                : "verificationCode";
        if (codeField.toLowerCase(Locale.ROOT).contains("codes")) {
            root.putArray(codeField).add(verificationCode);
        } else {
            root.put(codeField, verificationCode);
        }
        return root.toString();
    }

    private void assertProviderResponseSuccess(String response) {
        if (!StringUtils.hasText(response)) {
            throw new BusinessException("券码核销接口返回为空，订单仍为待核销");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if (hasExplicitFailure(root)) {
                throw new BusinessException("券码核销接口返回失败：" + resolveProviderMessage(root));
            }
            if (!hasExplicitSuccess(root)) {
                throw new BusinessException("券码核销接口未返回明确成功标记，订单仍为待核销");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("券码核销接口返回不是有效 JSON，订单仍为待核销");
        }
    }

    private boolean hasExplicitFailure(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return false;
        }
        if (root.has("success") && !root.path("success").asBoolean(false)) {
            return true;
        }
        if (isNonZeroCode(root.path("code"))
                || isNonZeroCode(root.path("err_no"))
                || isNonZeroCode(root.path("error_code"))) {
            return true;
        }
        JsonNode data = root.path("data");
        return isNonZeroCode(data.path("code"))
                || isNonZeroCode(data.path("err_no"))
                || isNonZeroCode(data.path("error_code"));
    }

    private boolean hasExplicitSuccess(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return false;
        }
        JsonNode data = root.path("data");
        return isTrue(root.path("success"))
                || isSuccessCode(root.path("code"))
                || isSuccessCode(root.path("err_no"))
                || isSuccessCode(root.path("error_code"))
                || isSuccessStatus(root.path("status"))
                || isTrue(data.path("success"))
                || isSuccessCode(data.path("code"))
                || isSuccessCode(data.path("err_no"))
                || isSuccessCode(data.path("error_code"))
                || isSuccessStatus(data.path("status"));
    }

    private boolean isTrue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String value = node.asText();
        return "true".equalsIgnoreCase(value) || "success".equalsIgnoreCase(value);
    }

    private boolean isSuccessCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isNumber()) {
            return node.asLong() == 0L;
        }
        String value = node.asText();
        return "0".equals(value.trim()) || "SUCCESS".equalsIgnoreCase(value.trim()) || "OK".equalsIgnoreCase(value.trim());
    }

    private boolean isSuccessStatus(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isNumber()) {
            return node.asInt() == 0 || node.asInt() == 200;
        }
        String value = node.asText();
        return "SUCCESS".equalsIgnoreCase(value.trim())
                || "SUCCEEDED".equalsIgnoreCase(value.trim())
                || "OK".equalsIgnoreCase(value.trim())
                || "DONE".equalsIgnoreCase(value.trim())
                || "VERIFIED".equalsIgnoreCase(value.trim());
    }

    private boolean isNonZeroCode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isNumber()) {
            return node.asLong() != 0L;
        }
        String value = node.asText();
        return StringUtils.hasText(value)
                && !"0".equals(value.trim())
                && !"SUCCESS".equalsIgnoreCase(value.trim())
                && !"OK".equalsIgnoreCase(value.trim());
    }

    private String resolveProviderMessage(JsonNode root) {
        String message = firstNonBlank(
                text(root, "message"),
                text(root, "msg"),
                text(root, "err_msg"),
                text(root, "error_msg"),
                text(root, "providerMessage"),
                text(root, "detail"),
                text(root, "description"),
                text(root.path("data"), "message"),
                text(root.path("data"), "msg"),
                text(root.path("data"), "err_msg"),
                text(root.path("data"), "error_msg"),
                text(root.path("data"), "providerMessage"),
                text(root.path("data"), "detail"),
                text(root.path("data"), "description"));
        return StringUtils.hasText(message) ? message : "未知错误";
    }

    private RestClient restClient(IntegrationProviderConfig provider) {
        return SchedulerRestClientFactory.build(provider);
    }

    private String buildIdempotencyKey(String providerCode, Order order, String verificationCode) {
        return "VOUCHER_VERIFY:" + providerCode + ":" + order.getId() + ":" + verificationCode;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("/+$", "");
    }

    private boolean isAbsoluteUrl(String value) {
        return StringUtils.hasText(value)
                && (value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8));
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return StringUtils.hasText(message) ? trim(message, 300) : "未知错误";
    }
}
