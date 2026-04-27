package com.seedcrm.crm.payment.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.payment.dto.PaymentConfigTestRequest;
import com.seedcrm.crm.payment.dto.PaymentConfigTestResponse;
import com.seedcrm.crm.payment.service.PaymentConfigService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentConfigServiceImpl implements PaymentConfigService {

    @Override
    public PaymentConfigTestResponse testConfig(PaymentConfigTestRequest request) {
        if (request == null || !StringUtils.hasText(request.getChannel())) {
            throw new BusinessException("channel is required");
        }
        String channel = normalize(request.getChannel());
        if (!List.of("WECHAT_PAY", "WECHAT_PAYOUT").contains(channel)) {
            throw new BusinessException("unsupported payment channel");
        }

        LocalDateTime checkedAt = LocalDateTime.now();
        String notifyUrl = joinUrl(request.getApiBaseUrl(), request.getNotifyPath());
        String refundNotifyUrl = joinUrl(request.getApiBaseUrl(), request.getRefundNotifyPath());
        String traceId = UUID.randomUUID().toString().replace("-", "");

        if (!Integer.valueOf(1).equals(request.getEnabled())) {
            return new PaymentConfigTestResponse(
                    false,
                    "SKIPPED",
                    "当前通道未启用，已跳过联通测试",
                    traceId,
                    notifyUrl,
                    refundNotifyUrl,
                    checkedAt);
        }

        List<String> missingFields = new ArrayList<>();
        require(missingFields, request.getMerchantName(), "商户主体");
        require(missingFields, request.getMchId(), "商户号");
        require(missingFields, request.getAppId(), "AppId");
        require(missingFields, request.getApiV3Key(), "APIv3 Key");
        require(missingFields, request.getSerialNo(), "证书序列号");
        require(missingFields, request.getPrivateKeyPem(), "商户私钥");
        require(missingFields, request.getApiBaseUrl(), "API 域名");
        require(missingFields, request.getNotifyPath(), "回调路径");
        if ("WECHAT_PAY".equals(channel)) {
            require(missingFields, request.getRefundNotifyPath(), "退款回调路径");
        }

        if (!missingFields.isEmpty()) {
            return new PaymentConfigTestResponse(
                    false,
                    "INVALID",
                    "缺少必填项：" + String.join("、", missingFields),
                    traceId,
                    notifyUrl,
                    refundNotifyUrl,
                    checkedAt);
        }

        if (!String.valueOf(request.getApiBaseUrl()).startsWith("http")) {
            return new PaymentConfigTestResponse(
                    false,
                    "INVALID",
                    "API 域名必须以 http:// 或 https:// 开头",
                    traceId,
                    notifyUrl,
                    refundNotifyUrl,
                    checkedAt);
        }

        if (!String.valueOf(request.getNotifyPath()).startsWith("/")) {
            return new PaymentConfigTestResponse(
                    false,
                    "INVALID",
                    "回调路径必须以 / 开头",
                    traceId,
                    notifyUrl,
                    refundNotifyUrl,
                    checkedAt);
        }

        if ("WECHAT_PAY".equals(channel) && !String.valueOf(request.getRefundNotifyPath()).startsWith("/")) {
            return new PaymentConfigTestResponse(
                    false,
                    "INVALID",
                    "退款回调路径必须以 / 开头",
                    traceId,
                    notifyUrl,
                    refundNotifyUrl,
                    checkedAt);
        }

        return new PaymentConfigTestResponse(
                true,
                "SUCCESS",
                "配置校验通过，可继续真实联调",
                traceId,
                notifyUrl,
                refundNotifyUrl,
                checkedAt);
    }

    private void require(List<String> missingFields, String value, String fieldLabel) {
        if (!StringUtils.hasText(value)) {
            missingFields.add(fieldLabel);
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBase = trimTrailingSlash(baseUrl);
        String normalizedPath = StringUtils.hasText(path) ? path.trim() : "";
        if (!StringUtils.hasText(normalizedBase)) {
            return normalizedPath;
        }
        if (!StringUtils.hasText(normalizedPath)) {
            return normalizedBase;
        }
        if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
            return normalizedPath;
        }
        return normalizedBase + (normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath);
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
