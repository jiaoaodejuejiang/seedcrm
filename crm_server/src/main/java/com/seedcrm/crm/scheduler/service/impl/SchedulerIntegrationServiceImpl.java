package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackConfigMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class SchedulerIntegrationServiceImpl implements SchedulerIntegrationService {

    private static final String MODE_MOCK = "MOCK";
    private static final String MODE_LIVE = "LIVE";
    private static final String PROVIDER_DOUYIN = "DOUYIN_LAIKE";
    private static final String AUTH_TYPE_AUTH_CODE = "AUTH_CODE";
    private static final String SIGNATURE_MODE_NONE_LOCAL_ONLY = "NONE_LOCAL_ONLY";
    private static final String SIGNATURE_MODE_TOKEN_QUERY = "TOKEN_QUERY";
    private static final String SIGNATURE_MODE_HMAC_SHA256 = "HMAC_SHA256";
    private static final String SIGNATURE_MODE_OAUTH_STATE = "OAUTH_STATE";
    private static final String SIGNATURE_MODE_DYNAMIC_PROVIDER = "DYNAMIC_PROVIDER";
    private static final String PROCESS_POLICY_LOG_ONLY = "LOG_ONLY";
    private static final String PROCESS_POLICY_AUTH_UPDATE_ONLY = "AUTH_UPDATE_ONLY";
    private static final String MASKED_VALUE = "******";
    private static final String DOUYIN_TOKEN_URL = "https://open.douyin.com/oauth/access_token/";
    private static final String DOUYIN_REFRESH_TOKEN_URL = "https://open.douyin.com/oauth/refresh_token/";
    private static final String DOUYIN_DEFAULT_VOUCHER_PREPARE_PATH = "/goodlife/v1/fulfilment/certificate/prepare/";
    private static final String DOUYIN_DEFAULT_VOUCHER_VERIFY_PATH = "/goodlife/v1/fulfilment/certificate/verify/";
    private static final String DOUYIN_DEFAULT_VOUCHER_CANCEL_PATH = "/goodlife/v1/fulfilment/certificate/cancel/";
    private static final String DOUYIN_DEFAULT_REFUND_APPLY_PATH = "/api/apps/trade/v2/refund/create_refund";
    private static final String DOUYIN_DEFAULT_REFUND_QUERY_PATH = "/api/apps/trade/v2/refund/query_refund";
    private static final String DOUYIN_DEFAULT_REFUND_LIST_PATH = "/api/apps/trade/v2/refund/refund_list";
    private static final String DOUYIN_DEFAULT_REFUND_NOTIFY_PATH = "/scheduler/callback/douyin/refund";
    private static final String DOUYIN_DEFAULT_REFUND_AUDIT_CALLBACK_PATH = "/scheduler/callback/douyin/refund-audit";
    private static final String DOUYIN_DEFAULT_REFUND_AMOUNT_UNIT = "CENT";
    private static final String DOUYIN_DEFAULT_VERIFY_CODE_FIELD = "encrypted_codes";
    private static final int AUTH_CODE_VALID_MINUTES = 5;

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final IntegrationCallbackConfigMapper callbackConfigMapper;
    private final IntegrationCallbackEventLogMapper callbackEventLogMapper;
    private final ObjectMapper objectMapper;

    public SchedulerIntegrationServiceImpl(IntegrationProviderConfigMapper providerConfigMapper,
                                           IntegrationCallbackConfigMapper callbackConfigMapper,
                                           IntegrationCallbackEventLogMapper callbackEventLogMapper,
                                           ObjectMapper objectMapper) {
        this.providerConfigMapper = providerConfigMapper;
        this.callbackConfigMapper = callbackConfigMapper;
        this.callbackEventLogMapper = callbackEventLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IntegrationProviderConfig> listProviders() {
        return providerConfigMapper.selectList(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                        .orderByAsc(IntegrationProviderConfig::getModuleCode)
                        .orderByAsc(IntegrationProviderConfig::getProviderCode)
                        .orderByAsc(IntegrationProviderConfig::getId))
                .stream()
                .map(this::maskProvider)
                .toList();
    }

    @Override
    public IntegrationProviderConfig saveProvider(IntegrationProviderConfig config) {
        if (config == null
                || !StringUtils.hasText(config.getProviderCode())
                || !StringUtils.hasText(config.getProviderName())
                || !StringUtils.hasText(config.getModuleCode())) {
            throw new BusinessException("providerCode、providerName、moduleCode 不能为空");
        }
        IntegrationProviderConfig existing = findProvider(config);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            IntegrationProviderConfig entity = new IntegrationProviderConfig();
            applyProvider(entity, config, null);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            if (providerConfigMapper.insert(entity) <= 0) {
                throw new BusinessException("保存三方接口配置失败");
            }
            return maskProvider(entity);
        }
        applyProvider(existing, config, existing);
        existing.setUpdatedAt(now);
        if (providerConfigMapper.updateById(existing) <= 0) {
            throw new BusinessException("更新三方接口配置失败");
        }
        return maskProvider(existing);
    }

    @Override
    public IntegrationProviderConfig testProvider(IntegrationProviderConfig config) {
        if (config == null) {
            throw new BusinessException("三方接口配置不能为空");
        }
        IntegrationProviderConfig working = mergeProvider(config);
        if (MODE_MOCK.equals(working.getExecutionMode())) {
            working.setLastTestStatus("SUCCESS");
            working.setLastTestMessage("当前为 MOCK 模式，可直接保存后继续联调与测试");
            working.setLastTestAt(LocalDateTime.now());
            updateProviderTestStateIfPersisted(working);
            return maskProvider(working);
        }
        if (!StringUtils.hasText(working.getBaseUrl())) {
            throw new BusinessException("LIVE 模式下必须填写 baseUrl");
        }
        assertHttps(working.getBaseUrl(), "baseUrl");
        if (PROVIDER_DOUYIN.equals(working.getProviderCode())) {
            String token;
            try {
                token = resolveDouyinAccessToken(working);
            } catch (BusinessException exception) {
                working.setLastTestStatus("FAILED");
                working.setLastTestMessage(exception.getMessage());
                working.setLastTestAt(LocalDateTime.now());
                updateProviderTestStateIfPersisted(working);
                throw exception;
            }
            working.setLastTestStatus("SUCCESS");
            working.setLastTestMessage("连接成功，已获取 access_token：" + maskValue(token));
            working.setLastTestAt(LocalDateTime.now());
            updateProviderTestStateIfPersisted(working);
            return maskProvider(working);
        }
        working.setLastTestStatus("SUCCESS");
        working.setLastTestMessage("配置校验通过，当前未配置专用探测逻辑");
        working.setLastTestAt(LocalDateTime.now());
        updateProviderTestStateIfPersisted(working);
        return maskProvider(working);
    }

    @Override
    public List<IntegrationCallbackConfig> listCallbacks() {
        return callbackConfigMapper.selectList(Wrappers.<IntegrationCallbackConfig>lambdaQuery()
                        .orderByAsc(IntegrationCallbackConfig::getProviderCode)
                        .orderByAsc(IntegrationCallbackConfig::getId))
                .stream()
                .map(this::maskCallback)
                .toList();
    }

    @Override
    public IntegrationCallbackConfig saveCallback(IntegrationCallbackConfig config) {
        if (config == null
                || !StringUtils.hasText(config.getCallbackName())
                || !StringUtils.hasText(config.getCallbackUrl())) {
            throw new BusinessException("callbackName、callbackUrl 不能为空");
        }
        IntegrationCallbackConfig existing = findCallback(config);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            IntegrationCallbackConfig entity = new IntegrationCallbackConfig();
            applyCallback(entity, config, null);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            if (callbackConfigMapper.insert(entity) <= 0) {
                throw new BusinessException("保存回调接口失败");
            }
            return maskCallback(entity);
        }
        applyCallback(existing, config, existing);
        existing.setUpdatedAt(now);
        if (callbackConfigMapper.updateById(existing) <= 0) {
            throw new BusinessException("更新回调接口失败");
        }
        return maskCallback(existing);
    }

    @Override
    public List<IntegrationCallbackEventLog> listCallbackLogs(String providerCode) {
        return callbackEventLogMapper.selectList(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                        .eq(StringUtils.hasText(providerCode),
                                IntegrationCallbackEventLog::getProviderCode,
                                normalize(providerCode))
                        .orderByDesc(IntegrationCallbackEventLog::getReceivedAt)
                        .orderByDesc(IntegrationCallbackEventLog::getId)
                        .last("LIMIT 50"));
    }

    @Override
    public IntegrationProviderConfig receiveProviderCallback(String providerCode,
                                                             String callbackName,
                                                             String callbackPath,
                                                             String requestMethod,
                                                             Map<String, String> parameters,
                                                             String payload) {
        String normalizedProviderCode = StringUtils.hasText(providerCode)
                ? normalize(providerCode)
                : PROVIDER_DOUYIN;
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> normalizedParameters = normalizeParameters(parameters);
        JsonNode payloadNode = parsePayload(payload);
        String authCode = firstNonBlank(
                normalizedParameters.get("auth_code"),
                normalizedParameters.get("code"),
                extractText(payloadNode, "auth_code", "code"));
        String accessToken = firstNonBlank(
                normalizedParameters.get("access_token"),
                extractText(payloadNode, "access_token", "data.access_token", "data.client_token", "client_token"));
        String refreshToken = firstNonBlank(
                normalizedParameters.get("refresh_token"),
                extractText(payloadNode, "refresh_token", "data.refresh_token"));
        String callbackState = firstNonBlank(
                normalizedParameters.get("state"),
                extractText(payloadNode, "state", "data.state"));
        String eventType = firstNonBlank(
                normalizedParameters.get("event_type"),
                normalizedParameters.get("event"),
                extractText(payloadNode, "event_type", "event", "action", "data.event_type"));
        String errorMessage = firstNonBlank(
                normalizedParameters.get("error_description"),
                normalizedParameters.get("error_msg"),
                normalizedParameters.get("errmsg"),
                normalizedParameters.get("error"),
                extractText(payloadNode, "error_description", "error_msg", "errmsg", "message", "error"));
        String traceId = UUID.randomUUID().toString();
        boolean authorizationCallback = StringUtils.hasText(authCode)
                || StringUtils.hasText(accessToken)
                || StringUtils.hasText(refreshToken)
                || (StringUtils.hasText(callbackName) && callbackName.contains("授权"));

        IntegrationProviderConfig provider = providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, normalizedProviderCode)
                .last("LIMIT 1"));
        IntegrationCallbackConfig callbackConfig = findCallbackByProviderOrName(normalizedProviderCode, callbackName);
        CallbackTrustResult trust = resolveCallbackTrust(provider, callbackConfig, normalizedParameters, payload, authorizationCallback);
        String bodyHash = sha256Hex(StringUtils.hasText(payload) ? payload.trim() : "");
        String queryHash = sha256Hex(toCompactJson(idempotencyParameters(normalizedParameters)));
        String eventId = firstNonBlank(
                normalizedParameters.get("event_id"),
                normalizedParameters.get("eventId"),
                extractText(payloadNode, "event_id", "eventId", "data.event_id", "data.refund_id", "refund_id"));
        String idempotencyKey = buildIdempotencyKey(
                normalizedProviderCode,
                callbackPath,
                eventType,
                callbackState,
                authCode,
                accessToken,
                eventId,
                queryHash,
                bodyHash);
        IntegrationCallbackEventLog duplicateLog = findCallbackLogByIdempotencyKey(idempotencyKey);
        boolean duplicate = duplicateLog != null;
        boolean businessEvent = isBusinessStateCallback(eventType, callbackName);
        boolean providerLive = MODE_LIVE.equals(provider == null ? null : provider.getExecutionMode());
        boolean trustedCallback = trust.trusted();
        boolean canUpdateAuthorization = !duplicate
                && trustedCallback
                && authorizationCallback
                && !businessEvent
                && !StringUtils.hasText(errorMessage);
        String processPolicy = canUpdateAuthorization ? PROCESS_POLICY_AUTH_UPDATE_ONLY : PROCESS_POLICY_LOG_ONLY;
        String callbackStatus = duplicate
                ? "DUPLICATE"
                : StringUtils.hasText(errorMessage)
                ? "FAILED"
                : !trustedCallback && providerLive
                ? "UNVERIFIED"
                : canUpdateAuthorization
                ? "SUCCESS"
                : "RECEIVED";
        String callbackMessage = duplicate
                ? "重复回调已记录并忽略，未再次处理"
                : StringUtils.hasText(errorMessage)
                ? errorMessage
                : !trustedCallback && providerLive
                ? firstNonBlank(trust.message(), "收到回调请求但未通过可信校验，已记录日志")
                : businessEvent
                ? "V1 仅记录该业务回调，不直接变更订单、服务单或薪酬"
                : canUpdateAuthorization && StringUtils.hasText(authCode)
                ? "已接收授权回调"
                : "已接收回调请求";
        String callbackPayload = buildCallbackPayload(normalizedParameters, payload);
        if (provider != null && !duplicate) {
            provider.setLastCallbackStatus(callbackStatus);
            provider.setLastCallbackMessage(trimMessage(callbackMessage));
            provider.setLastCallbackAt(now);
            provider.setLastCallbackPayload(trimPayload(callbackPayload));
            if (canUpdateAuthorization) {
                provider.setAuthCode(resolveSensitiveValue(authCode, provider.getAuthCode()));
                provider.setAuthCodeStatus(StringUtils.hasText(authCode) ? "RECEIVED" : provider.getAuthCodeStatus());
                provider.setLastAuthCodeAt(StringUtils.hasText(authCode) ? now : provider.getLastAuthCodeAt());
                provider.setAccessToken(resolveSensitiveValue(accessToken, provider.getAccessToken()));
                provider.setRefreshToken(resolveSensitiveValue(refreshToken, provider.getRefreshToken()));
                try {
                    if (PROVIDER_DOUYIN.equals(normalizedProviderCode)) {
                        resolveDouyinAccessToken(provider);
                        if (StringUtils.hasText(authCode)) {
                            callbackMessage = "已将 auth_code 换取为 access_token";
                        }
                    }
                } catch (BusinessException exception) {
                    callbackStatus = "FAILED";
                    callbackMessage = exception.getMessage();
                }
                provider.setAuthStatus(resolveAuthStatus(provider.getAuthStatus(), errorMessage, authCode, accessToken));
                provider.setLastAuthCodeAt(StringUtils.hasText(authCode) ? now : provider.getLastAuthCodeAt());
            }
            provider.setLastCallbackStatus(callbackStatus);
            provider.setLastCallbackMessage(trimMessage(callbackMessage));
            provider.setUpdatedAt(now);
            providerConfigMapper.updateById(provider);
        }

        if (callbackConfig != null && !duplicate) {
            callbackConfig.setLastCallbackStatus(callbackStatus);
            callbackConfig.setLastCallbackMessage(trimMessage(callbackMessage));
            callbackConfig.setLastCallbackAt(now);
            callbackConfig.setLastTraceId(traceId);
            if (canUpdateAuthorization) {
                callbackConfig.setLastAuthCode(resolveSensitiveValue(authCode, callbackConfig.getLastAuthCode()));
            }
            callbackConfig.setUpdatedAt(now);
            callbackConfigMapper.updateById(callbackConfig);
        }

        IntegrationCallbackEventLog eventLog = new IntegrationCallbackEventLog();
        eventLog.setProviderCode(normalizedProviderCode);
        eventLog.setProviderId(provider == null ? null : provider.getId());
        eventLog.setCallbackName(StringUtils.hasText(callbackName) ? callbackName.trim() : normalizedProviderCode + "-CALLBACK");
        eventLog.setRequestMethod(StringUtils.hasText(requestMethod) ? requestMethod.trim().toUpperCase(Locale.ROOT) : "GET");
        eventLog.setCallbackPath(trimToNull(callbackPath));
        eventLog.setQueryString(trimToNull(toCompactJson(maskSensitiveEntries(normalizedParameters))));
        eventLog.setRequestPayload(trimPayload(sanitizePayload(payload)));
        eventLog.setAuthCode(maskValue(authCode));
        eventLog.setCallbackState(trimToNull(callbackState));
        eventLog.setEventType(trimToNull(eventType));
        eventLog.setEventId(trimToNull(eventId));
        eventLog.setIdempotencyKey(idempotencyKey);
        eventLog.setIdempotencyStatus(duplicate ? "DUPLICATE" : "NEW");
        eventLog.setTraceId(traceId);
        eventLog.setSignatureMode(trust.signatureMode());
        eventLog.setSignatureValueMasked(trust.signatureValueMasked());
        eventLog.setSignatureStatus(trust.signatureStatus());
        eventLog.setTrustLevel(trust.trustLevel());
        eventLog.setReceivedIp(trimToNull(firstRemoteIp(normalizedParameters.get("__remote_ip"))));
        eventLog.setUserAgent(trimMessage(normalizedParameters.get("__user_agent")));
        eventLog.setTimestampValue(trimToNull(trust.timestampValue()));
        eventLog.setNonce(trimToNull(trust.nonce()));
        eventLog.setBodyHash(bodyHash);
        eventLog.setProcessPolicy(processPolicy);
        eventLog.setProcessStatus(callbackStatus);
        eventLog.setProcessMessage(trimMessage(callbackMessage));
        eventLog.setErrorCode(resolveCallbackErrorCode(duplicate, errorMessage, trust, providerLive));
        eventLog.setErrorMessage(trimMessage(StringUtils.hasText(errorMessage) ? errorMessage : trust.message()));
        eventLog.setReceivedAt(now);
        eventLog.setProcessedAt(now);
        eventLog.setCreatedAt(now);
        callbackEventLogMapper.insert(eventLog);

        return maskProvider(provider);
    }

    @Override
    public IntegrationProviderConfig getEnabledProviderOrNull(Long providerId) {
        if (providerId == null || providerId <= 0) {
            return null;
        }
        IntegrationProviderConfig provider = providerConfigMapper.selectById(providerId);
        if (provider == null || provider.getEnabled() == null || provider.getEnabled() != 1) {
            return null;
        }
        return provider;
    }

    @Override
    public String resolveProviderAccessToken(IntegrationProviderConfig config) {
        IntegrationProviderConfig working = mergeProvider(config);
        if (working == null || !MODE_LIVE.equals(working.getExecutionMode())) {
            return null;
        }
        if (PROVIDER_DOUYIN.equals(working.getProviderCode())) {
            String token = resolveDouyinAccessToken(working);
            updateProviderAuthorizationIfPersisted(working);
            return token;
        }
        return working.getAccessToken();
    }

    @Override
    public void markSyncResult(Long providerId, boolean success, String message) {
        if (providerId == null || providerId <= 0) {
            return;
        }
        IntegrationProviderConfig provider = providerConfigMapper.selectById(providerId);
        if (provider == null) {
            return;
        }
        provider.setLastTestStatus(success ? "SUCCESS" : "FAILED");
        provider.setLastTestMessage(trimMessage(message));
        provider.setLastTestAt(LocalDateTime.now());
        if (success) {
            provider.setLastSyncTime(LocalDateTime.now());
        }
        provider.setUpdatedAt(LocalDateTime.now());
        providerConfigMapper.updateById(provider);
    }

    private IntegrationProviderConfig mergeProvider(IntegrationProviderConfig config) {
        IntegrationProviderConfig working = new IntegrationProviderConfig();
        IntegrationProviderConfig existing = findProvider(config);
        if (existing != null) {
            working.setId(existing.getId());
            working.setProviderCode(existing.getProviderCode());
            working.setProviderName(existing.getProviderName());
            working.setModuleCode(existing.getModuleCode());
            working.setExecutionMode(existing.getExecutionMode());
            working.setAuthType(existing.getAuthType());
            working.setAppId(existing.getAppId());
            working.setBaseUrl(existing.getBaseUrl());
            working.setTokenUrl(existing.getTokenUrl());
            working.setEndpointPath(existing.getEndpointPath());
            working.setStatusQueryPath(existing.getStatusQueryPath());
            working.setReconciliationPullPath(existing.getReconciliationPullPath());
            working.setVoucherPreparePath(existing.getVoucherPreparePath());
            working.setVoucherVerifyPath(existing.getVoucherVerifyPath());
            working.setVoucherCancelPath(existing.getVoucherCancelPath());
            working.setRefundApplyPath(existing.getRefundApplyPath());
            working.setRefundQueryPath(existing.getRefundQueryPath());
            working.setRefundListPath(existing.getRefundListPath());
            working.setRefundNotifyPath(existing.getRefundNotifyPath());
            working.setRefundAuditCallbackPath(existing.getRefundAuditCallbackPath());
            working.setRefundOrderIdField(existing.getRefundOrderIdField());
            working.setRefundAmountField(existing.getRefundAmountField());
            working.setRefundReasonField(existing.getRefundReasonField());
            working.setRefundOutOrderNoField(existing.getRefundOutOrderNoField());
            working.setRefundOutRefundNoField(existing.getRefundOutRefundNoField());
            working.setRefundExternalRefundIdField(existing.getRefundExternalRefundIdField());
            working.setRefundItemOrderIdField(existing.getRefundItemOrderIdField());
            working.setRefundNotifyUrlField(existing.getRefundNotifyUrlField());
            working.setRefundAmountUnit(existing.getRefundAmountUnit());
            working.setRefundStatusMapping(existing.getRefundStatusMapping());
            working.setStatusMapping(existing.getStatusMapping());
            working.setClientKey(existing.getClientKey());
            working.setClientSecret(existing.getClientSecret());
            working.setRedirectUri(existing.getRedirectUri());
            working.setScope(existing.getScope());
            working.setAuthCode(existing.getAuthCode());
            working.setAuthCodeStatus(existing.getAuthCodeStatus());
            working.setAccessToken(existing.getAccessToken());
            working.setRefreshToken(existing.getRefreshToken());
            working.setTokenExpiresAt(existing.getTokenExpiresAt());
            working.setRefreshTokenExpiresAt(existing.getRefreshTokenExpiresAt());
            working.setLastRefreshAt(existing.getLastRefreshAt());
            working.setAccountId(existing.getAccountId());
            working.setLifeAccountIds(existing.getLifeAccountIds());
            working.setLocalAccountIds(existing.getLocalAccountIds());
            working.setOpenId(existing.getOpenId());
            working.setPoiId(existing.getPoiId());
            working.setVerifyCodeField(existing.getVerifyCodeField());
            working.setPageSize(existing.getPageSize());
            working.setPullWindowMinutes(existing.getPullWindowMinutes());
            working.setOverlapMinutes(existing.getOverlapMinutes());
            working.setRequestTimeoutMs(existing.getRequestTimeoutMs());
            working.setCallbackUrl(existing.getCallbackUrl());
            working.setEnabled(existing.getEnabled());
            working.setRemark(existing.getRemark());
            working.setAuthStatus(existing.getAuthStatus());
            working.setLastCallbackStatus(existing.getLastCallbackStatus());
            working.setLastCallbackMessage(existing.getLastCallbackMessage());
            working.setLastAuthCodeAt(existing.getLastAuthCodeAt());
            working.setLastCallbackAt(existing.getLastCallbackAt());
            working.setLastCallbackPayload(existing.getLastCallbackPayload());
            working.setLastSyncTime(existing.getLastSyncTime());
        }
        applyProvider(working, config, existing);
        return working;
    }

    private IntegrationProviderConfig findProvider(IntegrationProviderConfig config) {
        if (config == null) {
            return null;
        }
        if (config.getId() != null && config.getId() > 0) {
            return providerConfigMapper.selectById(config.getId());
        }
        if (!StringUtils.hasText(config.getProviderCode())) {
            return null;
        }
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, normalize(config.getProviderCode()))
                .last("LIMIT 1"));
    }

    private IntegrationCallbackConfig findCallback(IntegrationCallbackConfig config) {
        if (config == null) {
            return null;
        }
        if (config.getId() != null && config.getId() > 0) {
            return callbackConfigMapper.selectById(config.getId());
        }
        if (!StringUtils.hasText(config.getCallbackName())) {
            return null;
        }
        return callbackConfigMapper.selectOne(Wrappers.<IntegrationCallbackConfig>lambdaQuery()
                .eq(IntegrationCallbackConfig::getCallbackName, config.getCallbackName().trim())
                .last("LIMIT 1"));
    }

    private void applyProvider(IntegrationProviderConfig target,
                               IntegrationProviderConfig source,
                               IntegrationProviderConfig existing) {
        target.setProviderCode(normalize(source.getProviderCode()));
        target.setProviderName(source.getProviderName().trim());
        target.setModuleCode(normalize(source.getModuleCode()));
        target.setExecutionMode(normalizeExecutionMode(source.getExecutionMode()));
        target.setAuthType(PROVIDER_DOUYIN.equals(normalize(source.getProviderCode()))
                ? AUTH_TYPE_AUTH_CODE
                : StringUtils.hasText(source.getAuthType()) ? source.getAuthType().trim() : "CLIENT_TOKEN");
        target.setAppId(trimToNull(source.getAppId()));
        target.setBaseUrl(trimToNull(source.getBaseUrl()));
        target.setTokenUrl(trimToNull(source.getTokenUrl()));
        target.setEndpointPath(trimToNull(source.getEndpointPath()));
        target.setStatusQueryPath(resolveOptionalConfigValue(source.getStatusQueryPath(),
                existing == null ? null : existing.getStatusQueryPath()));
        target.setReconciliationPullPath(resolveOptionalConfigValue(source.getReconciliationPullPath(),
                existing == null ? null : existing.getReconciliationPullPath()));
        target.setVoucherPreparePath(resolveDouyinPathConfig(
                source.getVoucherPreparePath(),
                existing == null ? null : existing.getVoucherPreparePath(),
                DOUYIN_DEFAULT_VOUCHER_PREPARE_PATH));
        target.setVoucherVerifyPath(resolveDouyinPathConfig(
                source.getVoucherVerifyPath(),
                existing == null ? null : existing.getVoucherVerifyPath(),
                DOUYIN_DEFAULT_VOUCHER_VERIFY_PATH));
        target.setVoucherCancelPath(resolveDouyinPathConfig(
                source.getVoucherCancelPath(),
                existing == null ? null : existing.getVoucherCancelPath(),
                DOUYIN_DEFAULT_VOUCHER_CANCEL_PATH));
        target.setRefundApplyPath(resolveDouyinPathConfig(
                source.getRefundApplyPath(),
                existing == null ? null : existing.getRefundApplyPath(),
                DOUYIN_DEFAULT_REFUND_APPLY_PATH));
        target.setRefundQueryPath(resolveDouyinPathConfig(
                source.getRefundQueryPath(),
                existing == null ? null : existing.getRefundQueryPath(),
                DOUYIN_DEFAULT_REFUND_QUERY_PATH));
        target.setRefundListPath(resolveDouyinPathConfig(
                source.getRefundListPath(),
                existing == null ? null : existing.getRefundListPath(),
                DOUYIN_DEFAULT_REFUND_LIST_PATH));
        target.setRefundNotifyPath(resolveDouyinPathConfig(
                source.getRefundNotifyPath(),
                existing == null ? null : existing.getRefundNotifyPath(),
                DOUYIN_DEFAULT_REFUND_NOTIFY_PATH));
        target.setRefundAuditCallbackPath(resolveDouyinPathConfig(
                source.getRefundAuditCallbackPath(),
                existing == null ? null : existing.getRefundAuditCallbackPath(),
                DOUYIN_DEFAULT_REFUND_AUDIT_CALLBACK_PATH));
        target.setRefundOrderIdField(resolveOptionalConfigValue(source.getRefundOrderIdField(),
                existing == null ? null : existing.getRefundOrderIdField()));
        target.setRefundAmountField(resolveOptionalConfigValue(source.getRefundAmountField(),
                existing == null ? null : existing.getRefundAmountField()));
        target.setRefundReasonField(resolveOptionalConfigValue(source.getRefundReasonField(),
                existing == null ? null : existing.getRefundReasonField()));
        target.setRefundOutOrderNoField(resolveOptionalConfigValue(
                firstNonBlank(source.getRefundOutOrderNoField(), "out_order_no"),
                existing == null ? null : existing.getRefundOutOrderNoField()));
        target.setRefundOutRefundNoField(resolveOptionalConfigValue(
                firstNonBlank(source.getRefundOutRefundNoField(), "out_refund_no"),
                existing == null ? null : existing.getRefundOutRefundNoField()));
        target.setRefundExternalRefundIdField(resolveOptionalConfigValue(
                firstNonBlank(source.getRefundExternalRefundIdField(), "refund_id"),
                existing == null ? null : existing.getRefundExternalRefundIdField()));
        target.setRefundItemOrderIdField(resolveOptionalConfigValue(
                firstNonBlank(source.getRefundItemOrderIdField(), "item_order_id"),
                existing == null ? null : existing.getRefundItemOrderIdField()));
        target.setRefundNotifyUrlField(resolveOptionalConfigValue(
                firstNonBlank(source.getRefundNotifyUrlField(), "notify_url"),
                existing == null ? null : existing.getRefundNotifyUrlField()));
        target.setRefundAmountUnit(resolveOptionalConfigValue(
                firstNonBlank(source.getRefundAmountUnit(), DOUYIN_DEFAULT_REFUND_AMOUNT_UNIT),
                existing == null ? null : existing.getRefundAmountUnit()));
        target.setRefundStatusMapping(resolveOptionalConfigValue(source.getRefundStatusMapping(),
                existing == null ? null : existing.getRefundStatusMapping()));
        target.setStatusMapping(resolveOptionalConfigValue(source.getStatusMapping(),
                existing == null ? null : existing.getStatusMapping()));
        target.setClientKey(trimToNull(source.getClientKey()));
        target.setClientSecret(resolveSensitiveValue(source.getClientSecret(), existing == null ? null : existing.getClientSecret()));
        target.setRedirectUri(trimToNull(source.getRedirectUri()));
        target.setScope(trimToNull(source.getScope()));
        target.setAuthCode(resolveSensitiveValue(source.getAuthCode(), existing == null ? null : existing.getAuthCode()));
        if (StringUtils.hasText(source.getAuthCode()) && !MASKED_VALUE.equals(source.getAuthCode().trim())) {
            target.setLastAuthCodeAt(LocalDateTime.now());
        }
        target.setAuthCodeStatus(trimToNull(firstNonBlank(source.getAuthCodeStatus(), existing == null ? null : existing.getAuthCodeStatus())));
        target.setAccessToken(resolveSensitiveValue(source.getAccessToken(), existing == null ? null : existing.getAccessToken()));
        target.setRefreshToken(resolveSensitiveValue(source.getRefreshToken(), existing == null ? null : existing.getRefreshToken()));
        target.setTokenExpiresAt(source.getTokenExpiresAt() == null ? existing == null ? null : existing.getTokenExpiresAt() : source.getTokenExpiresAt());
        target.setRefreshTokenExpiresAt(source.getRefreshTokenExpiresAt() == null
                ? existing == null ? null : existing.getRefreshTokenExpiresAt()
                : source.getRefreshTokenExpiresAt());
        target.setLastRefreshAt(source.getLastRefreshAt() == null ? existing == null ? null : existing.getLastRefreshAt() : source.getLastRefreshAt());
        target.setAccountId(trimToNull(source.getAccountId()));
        target.setLifeAccountIds(trimToNull(source.getLifeAccountIds()));
        target.setLocalAccountIds(trimToNull(source.getLocalAccountIds()));
        target.setOpenId(trimToNull(source.getOpenId()));
        target.setPoiId(resolveOptionalConfigValue(source.getPoiId(), existing == null ? null : existing.getPoiId()));
        target.setVerifyCodeField(resolveOptionalConfigValue(
                firstNonBlank(source.getVerifyCodeField(), DOUYIN_DEFAULT_VERIFY_CODE_FIELD),
                existing == null ? null : existing.getVerifyCodeField()));
        target.setPageSize(source.getPageSize() == null || source.getPageSize() <= 0 ? 20 : source.getPageSize());
        target.setPullWindowMinutes(source.getPullWindowMinutes() == null || source.getPullWindowMinutes() <= 0 ? 60 : source.getPullWindowMinutes());
        target.setOverlapMinutes(source.getOverlapMinutes() == null || source.getOverlapMinutes() < 0 ? 10 : source.getOverlapMinutes());
        target.setRequestTimeoutMs(source.getRequestTimeoutMs() == null || source.getRequestTimeoutMs() <= 0 ? 10000 : source.getRequestTimeoutMs());
        target.setRateLimitPerMinute(source.getRateLimitPerMinute() == null || source.getRateLimitPerMinute() <= 0
                ? existing == null || existing.getRateLimitPerMinute() == null || existing.getRateLimitPerMinute() <= 0 ? 60 : existing.getRateLimitPerMinute()
                : source.getRateLimitPerMinute());
        target.setCacheTtlSeconds(source.getCacheTtlSeconds() == null || source.getCacheTtlSeconds() < 0
                ? existing == null || existing.getCacheTtlSeconds() == null || existing.getCacheTtlSeconds() < 0 ? 30 : existing.getCacheTtlSeconds()
                : source.getCacheTtlSeconds());
        target.setCallbackUrl(trimToNull(source.getCallbackUrl()));
        target.setEnabled(source.getEnabled() == null ? 1 : source.getEnabled());
        target.setRemark(trimToNull(source.getRemark()));
        if (!StringUtils.hasText(target.getAuthStatus())) {
            target.setAuthStatus(MODE_MOCK.equals(target.getExecutionMode()) ? "MOCK" : "UNAUTHORIZED");
        }
    }

    private String resolveDouyinPathConfig(String incoming, String existing, String defaultValue) {
        return firstNonBlank(trimToNull(incoming), trimToNull(existing), defaultValue);
    }

    private String resolveOptionalConfigValue(String incoming, String existing) {
        return firstNonBlank(trimToNull(incoming), trimToNull(existing));
    }

    private void applyCallback(IntegrationCallbackConfig target,
                               IntegrationCallbackConfig source,
                               IntegrationCallbackConfig existing) {
        target.setProviderCode(StringUtils.hasText(source.getProviderCode()) ? normalize(source.getProviderCode()) : null);
        target.setCallbackName(source.getCallbackName().trim());
        target.setCallbackUrl(source.getCallbackUrl().trim());
        target.setSignatureMode(StringUtils.hasText(source.getSignatureMode()) ? source.getSignatureMode().trim() : SIGNATURE_MODE_NONE_LOCAL_ONLY);
        target.setTokenValue(resolveSensitiveValue(source.getTokenValue(), existing == null ? null : existing.getTokenValue()));
        target.setAesKey(resolveSensitiveValue(source.getAesKey(), existing == null ? null : existing.getAesKey()));
        target.setEnabled(source.getEnabled() == null ? 1 : source.getEnabled());
        target.setRemark(trimToNull(source.getRemark()));
    }

    private IntegrationProviderConfig maskProvider(IntegrationProviderConfig provider) {
        if (provider == null) {
            return null;
        }
        provider.setClientSecretMasked(maskValue(provider.getClientSecret()));
        provider.setClientSecretConfigured(StringUtils.hasText(provider.getClientSecret()));
        provider.setAuthCodeMasked(maskValue(provider.getAuthCode()));
        applyAuthCodeExpiryPreview(provider);
        provider.setAccessTokenMasked(maskValue(provider.getAccessToken()));
        provider.setRefreshTokenMasked(maskValue(provider.getRefreshToken()));
        return provider;
    }

    private void updateProviderAuthorizationIfPersisted(IntegrationProviderConfig provider) {
        if (provider == null || provider.getId() == null || provider.getId() <= 0) {
            return;
        }
        IntegrationProviderConfig existing = providerConfigMapper.selectById(provider.getId());
        if (existing == null) {
            return;
        }
        existing.setAuthCode(resolveSensitiveValue(provider.getAuthCode(), existing.getAuthCode()));
        existing.setAuthCodeStatus(provider.getAuthCodeStatus());
        existing.setAccessToken(resolveSensitiveValue(provider.getAccessToken(), existing.getAccessToken()));
        existing.setRefreshToken(resolveSensitiveValue(provider.getRefreshToken(), existing.getRefreshToken()));
        existing.setTokenExpiresAt(provider.getTokenExpiresAt());
        existing.setRefreshTokenExpiresAt(provider.getRefreshTokenExpiresAt());
        existing.setLastRefreshAt(provider.getLastRefreshAt());
        existing.setAuthStatus(provider.getAuthStatus());
        existing.setLastAuthCodeAt(provider.getLastAuthCodeAt());
        existing.setUpdatedAt(LocalDateTime.now());
        providerConfigMapper.updateById(existing);
    }

    private void applyAuthCodeExpiryPreview(IntegrationProviderConfig provider) {
        if (provider == null || !AUTH_TYPE_AUTH_CODE.equals(normalize(provider.getAuthType()))) {
            return;
        }
        if (provider.getLastAuthCodeAt() == null) {
            provider.setAuthCodeExpired(StringUtils.hasText(provider.getAuthCode()));
            provider.setAuthCodeWarning(StringUtils.hasText(provider.getAuthCode())
                    ? "auth_code 缺少接收时间，请重新授权后再换取 token"
                    : "尚未收到 auth_code");
            return;
        }
        LocalDateTime expiresAt = provider.getLastAuthCodeAt().plusMinutes(AUTH_CODE_VALID_MINUTES);
        long remainingSeconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        provider.setAuthCodeExpiresAt(expiresAt);
        provider.setAuthCodeSecondsRemaining(Math.max(remainingSeconds, 0));
        boolean expired = remainingSeconds <= 0;
        provider.setAuthCodeExpired(expired);
        if (expired) {
            provider.setAuthCodeWarning("auth_code 已过期，请重新授权后再换取 token");
        } else if (remainingSeconds <= 60) {
            provider.setAuthCodeWarning("auth_code 即将过期，请尽快完成联调测试");
        } else {
            provider.setAuthCodeWarning("auth_code 有效期内");
        }
    }

    private boolean isAuthCodeExpired(IntegrationProviderConfig provider) {
        if (provider == null || !StringUtils.hasText(provider.getAuthCode())) {
            return false;
        }
        return provider.getLastAuthCodeAt() == null
                || provider.getLastAuthCodeAt().plusMinutes(AUTH_CODE_VALID_MINUTES).isBefore(LocalDateTime.now());
    }

    private IntegrationCallbackConfig maskCallback(IntegrationCallbackConfig callback) {
        if (callback == null) {
            return null;
        }
        callback.setTokenMasked(maskValue(callback.getTokenValue()));
        callback.setAesKeyMasked(maskValue(callback.getAesKey()));
        callback.setLastAuthCodeMasked(maskValue(callback.getLastAuthCode()));
        return callback;
    }

    private void updateProviderTestStateIfPersisted(IntegrationProviderConfig provider) {
        if (provider.getId() == null || provider.getId() <= 0) {
            return;
        }
        IntegrationProviderConfig existing = providerConfigMapper.selectById(provider.getId());
        if (existing == null) {
            return;
        }
        existing.setLastTestStatus(provider.getLastTestStatus());
        existing.setLastTestMessage(trimMessage(provider.getLastTestMessage()));
        existing.setLastTestAt(provider.getLastTestAt());
        existing.setAuthStatus(provider.getAuthStatus());
        existing.setAuthCodeStatus(provider.getAuthCodeStatus());
        existing.setAccessToken(resolveSensitiveValue(provider.getAccessToken(), existing.getAccessToken()));
        existing.setRefreshToken(resolveSensitiveValue(provider.getRefreshToken(), existing.getRefreshToken()));
        existing.setTokenExpiresAt(provider.getTokenExpiresAt());
        existing.setRefreshTokenExpiresAt(provider.getRefreshTokenExpiresAt());
        existing.setLastRefreshAt(provider.getLastRefreshAt());
        existing.setUpdatedAt(LocalDateTime.now());
        providerConfigMapper.updateById(existing);
    }

    private String resolveDouyinAccessToken(IntegrationProviderConfig config) {
        if (StringUtils.hasText(config.getAccessToken())
                && config.getTokenExpiresAt() != null
                && config.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return config.getAccessToken().trim();
        }
        if (StringUtils.hasText(config.getRefreshToken())) {
            return refreshDouyinAccessToken(config);
        }
        if (StringUtils.hasText(config.getAuthCode())) {
            if (isAuthCodeExpired(config)) {
                config.setAuthStatus("EXPIRED");
                config.setAuthCodeStatus("EXPIRED");
                throw new BusinessException("auth_code 已过期，请重新授权后再测试接口");
            }
            return exchangeDouyinAuthCode(config);
        }
        throw new BusinessException("当前为 LIVE 模式，请先完成 auth_code 授权");
    }

    private String exchangeDouyinAuthCode(IntegrationProviderConfig config) {
        String appId = resolveDouyinAppId(config);
        String secret = resolveDouyinSecret(config);
        JsonNode response = RestClient.create()
                .post()
                .uri(resolveDouyinTokenUrl(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "app_id", appId,
                        "secret", secret,
                        "auth_code", config.getAuthCode().trim()))
                .retrieve()
                .body(JsonNode.class);
        return applyDouyinTokenResponse(config, response, "获取 access_token 失败", "EXCHANGED");
    }

    private String refreshDouyinAccessToken(IntegrationProviderConfig config) {
        String appId = resolveDouyinAppId(config);
        JsonNode response = RestClient.create()
                .post()
                .uri(resolveDouyinRefreshTokenUrl(config))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "app_id", appId,
                        "refresh_token", config.getRefreshToken().trim()))
                .retrieve()
                .body(JsonNode.class);
        config.setLastRefreshAt(LocalDateTime.now());
        return applyDouyinTokenResponse(config, response, "刷新 access_token 失败", "REFRESHED");
    }

    private String applyDouyinTokenResponse(IntegrationProviderConfig config,
                                            JsonNode response,
                                            String defaultMessage,
                                            String authCodeStatus) {
        int code = response == null ? -1 : response.path("code").asInt(0);
        if (code != 0) {
            String message = response == null ? defaultMessage : firstNonBlank(
                    extractText(response, "message"),
                    extractText(response, "data.description"),
                    defaultMessage);
            throw new BusinessException(message);
        }
        String accessToken = extractText(response, "data.access_token", "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(defaultMessage);
        }
        String refreshToken = firstNonBlank(
                extractText(response, "data.refresh_token"),
                extractText(response, "refresh_token"),
                config.getRefreshToken());
        int expiresIn = parseInteger(extractText(response, "data.expires_in", "expires_in"), 24 * 3600);
        int refreshExpiresIn = parseInteger(
                extractText(response, "data.refresh_token_expires_in", "refresh_token_expires_in"),
                30 * 24 * 3600);
        config.setAccessToken(accessToken.trim());
        config.setRefreshToken(trimToNull(refreshToken));
        config.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        config.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiresIn));
        config.setAuthCodeStatus(authCodeStatus);
        config.setAuthStatus("AUTHORIZED");
        return accessToken.trim();
    }

    private String resolveDouyinAppId(IntegrationProviderConfig config) {
        String appId = firstNonBlank(config.getAppId(), config.getClientKey());
        if (!StringUtils.hasText(appId)) {
            throw new BusinessException("抖音来客配置必须填写 appId");
        }
        return appId.trim();
    }

    private String resolveDouyinSecret(IntegrationProviderConfig config) {
        String secret = config.getClientSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("抖音来客配置必须填写应用 Secret");
        }
        return secret.trim();
    }

    private String resolveDouyinTokenUrl(IntegrationProviderConfig config) {
        return StringUtils.hasText(config.getTokenUrl()) ? config.getTokenUrl().trim() : DOUYIN_TOKEN_URL;
    }

    private String resolveDouyinRefreshTokenUrl(IntegrationProviderConfig config) {
        String tokenUrl = resolveDouyinTokenUrl(config);
        if (tokenUrl.endsWith("/access_token/")) {
            return tokenUrl.replace("/access_token/", "/refresh_token/");
        }
        return DOUYIN_REFRESH_TOKEN_URL;
    }

    private IntegrationCallbackConfig findCallbackByProviderOrName(String providerCode, String callbackName) {
        IntegrationCallbackConfig callbackConfig = null;
        if (StringUtils.hasText(callbackName)) {
            callbackConfig = callbackConfigMapper.selectOne(Wrappers.<IntegrationCallbackConfig>lambdaQuery()
                    .eq(IntegrationCallbackConfig::getCallbackName, callbackName.trim())
                    .last("LIMIT 1"));
        }
        if (callbackConfig != null) {
            return callbackConfig;
        }
        if (!StringUtils.hasText(providerCode)) {
            return null;
        }
        return callbackConfigMapper.selectOne(Wrappers.<IntegrationCallbackConfig>lambdaQuery()
                .eq(IntegrationCallbackConfig::getProviderCode, providerCode)
                .eq(IntegrationCallbackConfig::getEnabled, 1)
                .orderByAsc(IntegrationCallbackConfig::getId)
                .last("LIMIT 1"));
    }

    private Map<String, String> normalizeParameters(Map<String, String> parameters) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (parameters == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            normalized.put(entry.getKey().trim(), trimToNull(entry.getValue()));
        }
        return normalized;
    }

    private JsonNode parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildCallbackPayload(Map<String, String> parameters, String payload) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (parameters != null && !parameters.isEmpty()) {
            merged.put("query", maskSensitiveEntries(parameters));
        }
        if (StringUtils.hasText(payload)) {
            JsonNode payloadNode = parsePayload(payload);
            merged.put("body", payloadNode == null ? sanitizePayload(payload) : sanitizeValue(objectMapper.convertValue(payloadNode, new TypeReference<Object>() {
            })));
        }
        return merged.isEmpty() ? null : toCompactJson(merged);
    }

    private String toCompactJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            if (value instanceof Map<?, ?> map) {
                return map.toString();
            }
            return String.valueOf(value);
        }
    }

    private String resolveAuthStatus(String currentStatus, String errorMessage, String authCode, String accessToken) {
        if (StringUtils.hasText(errorMessage)) {
            return "FAILED";
        }
        if ("AUTHORIZED".equals(currentStatus) || "CONNECTED".equals(currentStatus)) {
            return currentStatus;
        }
        if (StringUtils.hasText(accessToken)) {
            return "AUTHORIZED";
        }
        if (StringUtils.hasText(authCode)) {
            return "AUTH_CODE_RECEIVED";
        }
        return StringUtils.hasText(currentStatus) ? currentStatus : "RECEIVED";
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

    private CallbackTrustResult resolveCallbackTrust(IntegrationProviderConfig provider,
                                                     IntegrationCallbackConfig callbackConfig,
                                                     Map<String, String> parameters,
                                                     String payload,
                                                     boolean authorizationCallback) {
        String signatureMode = normalizeSignatureMode(callbackConfig == null ? null : callbackConfig.getSignatureMode());
        String signatureValue = firstNonBlank(
                parameters.get("signature"),
                parameters.get("sign"),
                parameters.get("msg_signature"),
                parameters.get("__header_x_signature"),
                parameters.get("__header_x_seedcrm_signature"),
                parameters.get("__header_x_douyin_signature"));
        String timestampValue = firstNonBlank(
                parameters.get("timestamp"),
                parameters.get("ts"),
                parameters.get("__header_x_timestamp"));
        String nonce = firstNonBlank(
                parameters.get("nonce"),
                parameters.get("__header_x_nonce"));
        if (provider == null) {
            return new CallbackTrustResult(signatureMode, "NOT_VERIFIED", "BLOCKED", false,
                    maskValue(signatureValue), timestampValue, nonce, "来源接口未配置");
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            return new CallbackTrustResult(signatureMode, "NOT_VERIFIED", "BLOCKED", false,
                    maskValue(signatureValue), timestampValue, nonce, "来源接口已停用");
        }
        if (callbackConfig != null && callbackConfig.getEnabled() != null && callbackConfig.getEnabled() == 0) {
            return new CallbackTrustResult(signatureMode, "NOT_VERIFIED", "BLOCKED", false,
                    maskValue(signatureValue), timestampValue, nonce, "回调配置已停用");
        }
        if (!MODE_LIVE.equals(provider.getExecutionMode())) {
            return new CallbackTrustResult(signatureMode, "SKIPPED", "MOCK", true,
                    maskValue(signatureValue), timestampValue, nonce, "模拟模式跳过验签");
        }
        if (isLocalCallback(provider, callbackConfig, parameters)) {
            return new CallbackTrustResult(signatureMode, "LOCAL_BYPASS", "LOCAL", true,
                    maskValue(signatureValue), timestampValue, nonce, "本地联调跳过验签");
        }
        if (callbackConfig == null) {
            return new CallbackTrustResult(signatureMode, "NOT_VERIFIED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "未配置回调签名策略");
        }
        return switch (signatureMode) {
            case SIGNATURE_MODE_TOKEN_QUERY -> verifyTokenQuery(callbackConfig, parameters, signatureValue, timestampValue, nonce);
            case SIGNATURE_MODE_HMAC_SHA256 -> verifyHmacSha256(callbackConfig, payload, signatureValue, timestampValue, nonce);
            case SIGNATURE_MODE_OAUTH_STATE -> verifyOauthState(callbackConfig, authorizationCallback, parameters, signatureValue, timestampValue, nonce);
            case SIGNATURE_MODE_DYNAMIC_PROVIDER -> new CallbackTrustResult(signatureMode, "NOT_VERIFIED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "平台专用验签器尚未启用");
            default -> new CallbackTrustResult(signatureMode, "NOT_VERIFIED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "LIVE 模式不允许无签名回调");
        };
    }

    private CallbackTrustResult verifyTokenQuery(IntegrationCallbackConfig callbackConfig,
                                                 Map<String, String> parameters,
                                                 String signatureValue,
                                                 String timestampValue,
                                                 String nonce) {
        String expected = callbackConfig.getTokenValue();
        String actual = firstNonBlank(
                parameters.get("token"),
                parameters.get("callback_token"),
                parameters.get("__header_authorization"),
                signatureValue);
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return new CallbackTrustResult(SIGNATURE_MODE_TOKEN_QUERY, "FAILED", "UNVERIFIED", false,
                    maskValue(actual), timestampValue, nonce, "Token 校验参数缺失");
        }
        if (!safeEquals(normalizeBearer(actual), expected.trim())) {
            return new CallbackTrustResult(SIGNATURE_MODE_TOKEN_QUERY, "FAILED", "UNVERIFIED", false,
                    maskValue(actual), timestampValue, nonce, "Token 不匹配");
        }
        return new CallbackTrustResult(SIGNATURE_MODE_TOKEN_QUERY, "VERIFIED", "VERIFIED", true,
                maskValue(actual), timestampValue, nonce, "Token 校验通过");
    }

    private CallbackTrustResult verifyHmacSha256(IntegrationCallbackConfig callbackConfig,
                                                 String payload,
                                                 String signatureValue,
                                                 String timestampValue,
                                                 String nonce) {
        String secret = firstNonBlank(callbackConfig.getTokenValue(), callbackConfig.getAesKey());
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(signatureValue)) {
            return new CallbackTrustResult(SIGNATURE_MODE_HMAC_SHA256, "FAILED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "HMAC 密钥或签名缺失");
        }
        if (!StringUtils.hasText(timestampValue) || !StringUtils.hasText(nonce)) {
            return new CallbackTrustResult(SIGNATURE_MODE_HMAC_SHA256, "FAILED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "HMAC 时间戳或 nonce 缺失");
        }
        if (isTimestampExpired(timestampValue)) {
            return new CallbackTrustResult(SIGNATURE_MODE_HMAC_SHA256, "EXPIRED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "HMAC 时间戳过期");
        }
        String base = timestampValue.trim() + nonce.trim() + (StringUtils.hasText(payload) ? payload.trim() : "");
        String hexSignature = hmacSha256(secret.trim(), base);
        String base64Signature = base64HmacSha256(secret.trim(), base);
        String cleanSignature = normalizeSignature(signatureValue);
        boolean matched = safeEquals(cleanSignature == null ? null : cleanSignature.toLowerCase(Locale.ROOT), hexSignature)
                || safeEquals(cleanSignature, base64Signature);
        return new CallbackTrustResult(SIGNATURE_MODE_HMAC_SHA256,
                matched ? "VERIFIED" : "FAILED",
                matched ? "VERIFIED" : "UNVERIFIED",
                matched,
                maskValue(signatureValue),
                timestampValue,
                nonce,
                matched ? "HMAC 校验通过" : "HMAC 签名不匹配");
    }

    private CallbackTrustResult verifyOauthState(IntegrationCallbackConfig callbackConfig,
                                                 boolean authorizationCallback,
                                                 Map<String, String> parameters,
                                                 String signatureValue,
                                                 String timestampValue,
                                                 String nonce) {
        String expectedState = callbackConfig.getTokenValue();
        String actualState = parameters.get("state");
        if (!authorizationCallback) {
            return new CallbackTrustResult(SIGNATURE_MODE_OAUTH_STATE, "FAILED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "OAUTH_STATE 只能用于授权回调");
        }
        if (!StringUtils.hasText(expectedState) || !StringUtils.hasText(actualState)) {
            return new CallbackTrustResult(SIGNATURE_MODE_OAUTH_STATE, "FAILED", "UNVERIFIED", false,
                    maskValue(signatureValue), timestampValue, nonce, "授权 state 缺失");
        }
        boolean matched = safeEquals(actualState.trim(), expectedState.trim());
        return new CallbackTrustResult(SIGNATURE_MODE_OAUTH_STATE,
                matched ? "VERIFIED" : "FAILED",
                matched ? "VERIFIED" : "UNVERIFIED",
                matched,
                maskValue(actualState),
                timestampValue,
                nonce,
                matched ? "授权 state 校验通过" : "授权 state 不匹配");
    }

    private IntegrationCallbackEventLog findCallbackLogByIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return callbackEventLogMapper.selectOne(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                .eq(IntegrationCallbackEventLog::getIdempotencyKey, idempotencyKey)
                .orderByAsc(IntegrationCallbackEventLog::getId)
                .last("LIMIT 1"));
    }

    private String buildIdempotencyKey(String providerCode,
                                       String callbackPath,
                                       String eventType,
                                       String callbackState,
                                       String authCode,
                                       String accessToken,
                                       String eventId,
                                       String queryHash,
                                       String bodyHash) {
        String keySource = String.join("|",
                nullToEmpty(providerCode),
                nullToEmpty(callbackPath),
                nullToEmpty(eventType),
                nullToEmpty(callbackState),
                nullToEmpty(authCode),
                nullToEmpty(accessToken),
                nullToEmpty(eventId),
                nullToEmpty(queryHash),
                nullToEmpty(bodyHash));
        return sha256Hex(keySource);
    }

    private Map<String, String> idempotencyParameters(Map<String, String> parameters) {
        Map<String, String> filtered = new LinkedHashMap<>();
        if (parameters == null) {
            return filtered;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key) || isTransportOnlyKey(key)) {
                continue;
            }
            filtered.put(key.trim(), entry.getValue());
        }
        return filtered;
    }

    private boolean isTransportOnlyKey(String key) {
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        return normalizedKey.startsWith("__")
                || normalizedKey.equals("signature")
                || normalizedKey.equals("sign")
                || normalizedKey.equals("msg_signature")
                || normalizedKey.contains("signature")
                || normalizedKey.equals("timestamp")
                || normalizedKey.equals("ts")
                || normalizedKey.equals("nonce")
                || normalizedKey.equals("token")
                || normalizedKey.equals("callback_token");
    }

    private boolean isBusinessStateCallback(String eventType, String callbackName) {
        String value = (nullToEmpty(eventType) + " " + nullToEmpty(callbackName)).toUpperCase(Locale.ROOT);
        return value.contains("REFUND")
                || value.contains("VERIFY")
                || value.contains("VOUCHER")
                || value.contains("ORDER")
                || value.contains("SALARY")
                || value.contains("PAY")
                || value.contains("USED")
                || value.contains("核销")
                || value.contains("退款")
                || value.contains("订单")
                || value.contains("薪酬");
    }

    private String resolveCallbackErrorCode(boolean duplicate,
                                            String errorMessage,
                                            CallbackTrustResult trust,
                                            boolean providerLive) {
        if (duplicate) {
            return "DUPLICATE";
        }
        if (StringUtils.hasText(errorMessage)) {
            return "CALLBACK_ERROR";
        }
        if (providerLive && !trust.trusted()) {
            return "SIGNATURE_UNVERIFIED";
        }
        return null;
    }

    private boolean isLocalCallback(IntegrationProviderConfig provider,
                                    IntegrationCallbackConfig callbackConfig,
                                    Map<String, String> parameters) {
        return isLocalUrl(provider.getCallbackUrl())
                || (callbackConfig != null && isLocalUrl(callbackConfig.getCallbackUrl()))
                || isLocalHost(firstRemoteIp(parameters.get("__remote_ip")));
    }

    private String normalizeSignatureMode(String signatureMode) {
        String normalized = normalize(signatureMode);
        if (!StringUtils.hasText(normalized) || "NONE".equals(normalized) || "LOCAL_BYPASS".equals(normalized)) {
            return SIGNATURE_MODE_NONE_LOCAL_ONLY;
        }
        if ("HMAC-SHA256".equals(normalized)) {
            return SIGNATURE_MODE_HMAC_SHA256;
        }
        return normalized;
    }

    private boolean isTimestampExpired(String timestampValue) {
        if (!StringUtils.hasText(timestampValue)) {
            return true;
        }
        try {
            long raw = Long.parseLong(timestampValue.trim());
            long epochSeconds = raw > 9_999_999_999L ? raw / 1000 : raw;
            long nowSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8));
            return Math.abs(nowSeconds - epochSeconds) > 600;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException("HMAC 签名计算失败");
        }
    }

    private String base64HmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException("HMAC 签名计算失败");
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException("摘要计算失败");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    private boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBearer(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) ? trimmed.substring(7).trim() : trimmed;
    }

    private String normalizeSignature(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, "sha256=", 0, 7)) {
            trimmed = trimmed.substring(7);
        }
        return trimmed.trim();
    }

    private String firstRemoteIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private boolean isLocalHost(String host) {
        return StringUtils.hasText(host)
                && ("127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "0.0.0.0".equals(host)
                || "::1".equals(host));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isLocalUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            return isLocalHost(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private Map<String, String> maskSensitiveEntries(Map<String, String> parameters) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (parameters == null) {
            return masked;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            masked.put(entry.getKey(), isSensitiveKey(entry.getKey()) ? maskValue(entry.getValue()) : entry.getValue());
        }
        return masked;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nestedValue = entry.getValue();
                sanitized.put(key, isSensitiveKey(key) ? maskValue(String.valueOf(nestedValue)) : sanitizeValue(nestedValue));
            }
            return sanitized;
        }
        if (value instanceof List<?> listValue) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : listValue) {
                sanitized.add(sanitizeValue(item));
            }
            return sanitized;
        }
        return value;
    }

    private String sanitizePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        JsonNode payloadNode = parsePayload(payload);
        if (payloadNode != null) {
            return toCompactJson(sanitizeValue(objectMapper.convertValue(payloadNode, new TypeReference<Object>() {
            })));
        }
        String sanitized = payload.trim();
        sanitized = sanitized.replaceAll("(?i)(<Encrypt><!\\[CDATA\\[)(.*?)(]]></Encrypt>)", "$1****$3");
        sanitized = sanitized.replaceAll("(?i)(\"(?:auth_code|code|access_token|refresh_token|token)\"\\s*:\\s*\")(.*?)(\")", "$1****$3");
        return trimPayload(sanitized);
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        return normalizedKey.equals("auth_code")
                || normalizedKey.equals("code")
                || normalizedKey.equals("access_token")
                || normalizedKey.equals("refresh_token")
                || normalizedKey.equals("token")
                || normalizedKey.equals("callback_token")
                || normalizedKey.equals("signature")
                || normalizedKey.equals("sign")
                || normalizedKey.equals("msg_signature")
                || normalizedKey.contains("signature")
                || normalizedKey.equals("__header_authorization")
                || normalizedKey.equals("client_secret")
                || normalizedKey.equals("app_secret")
                || normalizedKey.equals("corpsecret")
                || normalizedKey.equals("encodingaeskey")
                || normalizedKey.equals("encoding_aes_key");
    }

    private String trimPayload(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private void assertHttps(String url, String fieldName) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new BusinessException(fieldName + " 必须使用 https");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(fieldName + " 格式不正确");
        }
    }

    private String extractText(JsonNode node, String... candidates) {
        if (node == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode current = node;
            for (String part : candidate.split("\\.")) {
                if (current == null) {
                    break;
                }
                current = current.path(part);
            }
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExecutionMode(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return MODE_MOCK;
        }
        return MODE_LIVE.equals(normalized) ? MODE_LIVE : MODE_MOCK;
    }

    private String resolveSensitiveValue(String incoming, String existing) {
        if (StringUtils.hasText(incoming) && MASKED_VALUE.equals(incoming.trim())) {
            return existing;
        }
        return StringUtils.hasText(incoming) ? incoming.trim() : existing;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimMessage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) : trimmed;
    }

    private int parseInteger(String value, int defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String maskValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private record CallbackTrustResult(String signatureMode,
                                       String signatureStatus,
                                       String trustLevel,
                                       boolean trusted,
                                       String signatureValueMasked,
                                       String timestampValue,
                                       String nonce,
                                       String message) {
    }
}
