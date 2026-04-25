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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
    private static final String DOUYIN_TOKEN_URL = "https://api.oceanengine.com/open_api/oauth2/access_token/";
    private static final String DOUYIN_REFRESH_TOKEN_URL = "https://api.oceanengine.com/open_api/oauth2/refresh_token/";

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
            working.setLastTestMessage("当前为 MOCK 模式，可直接用于联调与调度测试");
            working.setLastTestAt(LocalDateTime.now());
            updateProviderTestStateIfPersisted(working);
            return maskProvider(working);
        }
        if (!StringUtils.hasText(working.getBaseUrl())) {
            throw new BusinessException("LIVE 模式必须配置 baseUrl");
        }
        assertHttps(working.getBaseUrl(), "baseUrl");
        if (PROVIDER_DOUYIN.equals(working.getProviderCode())) {
            String token = resolveDouyinAccessToken(working);
            working.setLastTestStatus("SUCCESS");
            working.setLastTestMessage("连接成功，已获取 access_token：" + maskValue(token));
            working.setLastTestAt(LocalDateTime.now());
            updateProviderTestStateIfPersisted(working);
            return maskProvider(working);
        }
        working.setLastTestStatus("SUCCESS");
        working.setLastTestMessage("配置校验通过，当前未配置专属探测逻辑");
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

        IntegrationProviderConfig provider = providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, normalizedProviderCode)
                .last("LIMIT 1"));
        IntegrationCallbackConfig callbackConfig = findCallbackByProviderOrName(normalizedProviderCode, callbackName);
        boolean trustedCallback = isTrustedCallback(provider, callbackConfig);
        String callbackStatus = StringUtils.hasText(errorMessage)
                ? "FAILED"
                : !trustedCallback && MODE_LIVE.equals(provider == null ? null : provider.getExecutionMode())
                ? "UNVERIFIED"
                : StringUtils.hasText(authCode) || StringUtils.hasText(accessToken)
                ? "SUCCESS"
                : "RECEIVED";
        String callbackMessage = StringUtils.hasText(errorMessage)
                ? errorMessage
                : !trustedCallback && MODE_LIVE.equals(provider == null ? null : provider.getExecutionMode())
                ? "已收到回调，但未完成验签，仅记录审计日志"
                : StringUtils.hasText(authCode)
                ? "已接收授权码回调"
                : "已接收回调请求";
        String callbackPayload = buildCallbackPayload(normalizedParameters, payload);
        if (provider != null) {
            provider.setLastCallbackStatus(callbackStatus);
            provider.setLastCallbackMessage(trimMessage(callbackMessage));
            provider.setLastCallbackAt(now);
            provider.setLastCallbackPayload(trimPayload(callbackPayload));
            if (trustedCallback) {
                provider.setAuthCode(resolveSensitiveValue(authCode, provider.getAuthCode()));
                provider.setAuthCodeStatus(StringUtils.hasText(authCode) ? "RECEIVED" : provider.getAuthCodeStatus());
                provider.setAccessToken(resolveSensitiveValue(accessToken, provider.getAccessToken()));
                provider.setRefreshToken(resolveSensitiveValue(refreshToken, provider.getRefreshToken()));
                try {
                    if (PROVIDER_DOUYIN.equals(normalizedProviderCode)) {
                        resolveDouyinAccessToken(provider);
                        if (StringUtils.hasText(authCode)) {
                            callbackMessage = "已接收 auth_code 并完成 access_token 换取";
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

        if (callbackConfig != null) {
            callbackConfig.setLastCallbackStatus(callbackStatus);
            callbackConfig.setLastCallbackMessage(trimMessage(callbackMessage));
            callbackConfig.setLastCallbackAt(now);
            callbackConfig.setLastTraceId(traceId);
            if (trustedCallback) {
                callbackConfig.setLastAuthCode(resolveSensitiveValue(authCode, callbackConfig.getLastAuthCode()));
            }
            callbackConfig.setUpdatedAt(now);
            callbackConfigMapper.updateById(callbackConfig);
        }

        IntegrationCallbackEventLog eventLog = new IntegrationCallbackEventLog();
        eventLog.setProviderCode(normalizedProviderCode);
        eventLog.setCallbackName(StringUtils.hasText(callbackName) ? callbackName.trim() : normalizedProviderCode + "-CALLBACK");
        eventLog.setRequestMethod(StringUtils.hasText(requestMethod) ? requestMethod.trim().toUpperCase(Locale.ROOT) : "GET");
        eventLog.setCallbackPath(trimToNull(callbackPath));
        eventLog.setQueryString(trimToNull(toCompactJson(maskSensitiveEntries(normalizedParameters))));
        eventLog.setRequestPayload(trimPayload(sanitizePayload(payload)));
        eventLog.setAuthCode(maskValue(authCode));
        eventLog.setCallbackState(trimToNull(callbackState));
        eventLog.setEventType(trimToNull(eventType));
        eventLog.setTraceId(traceId);
        eventLog.setSignatureStatus(resolveProviderSignatureStatus(provider, trustedCallback));
        eventLog.setProcessStatus(callbackStatus);
        eventLog.setProcessMessage(trimMessage(callbackMessage));
        eventLog.setReceivedAt(now);
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
        target.setAuthType(StringUtils.hasText(source.getAuthType())
                ? source.getAuthType().trim()
                : PROVIDER_DOUYIN.equals(normalize(source.getProviderCode())) ? AUTH_TYPE_AUTH_CODE : "CLIENT_TOKEN");
        target.setAppId(trimToNull(source.getAppId()));
        target.setBaseUrl(trimToNull(source.getBaseUrl()));
        target.setTokenUrl(trimToNull(source.getTokenUrl()));
        target.setEndpointPath(trimToNull(source.getEndpointPath()));
        target.setClientKey(trimToNull(source.getClientKey()));
        target.setClientSecret(resolveSensitiveValue(source.getClientSecret(), existing == null ? null : existing.getClientSecret()));
        target.setRedirectUri(trimToNull(source.getRedirectUri()));
        target.setScope(trimToNull(source.getScope()));
        target.setAuthCode(resolveSensitiveValue(source.getAuthCode(), existing == null ? null : existing.getAuthCode()));
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
        target.setPageSize(source.getPageSize() == null || source.getPageSize() <= 0 ? 20 : source.getPageSize());
        target.setPullWindowMinutes(source.getPullWindowMinutes() == null || source.getPullWindowMinutes() <= 0 ? 60 : source.getPullWindowMinutes());
        target.setOverlapMinutes(source.getOverlapMinutes() == null || source.getOverlapMinutes() < 0 ? 10 : source.getOverlapMinutes());
        target.setRequestTimeoutMs(source.getRequestTimeoutMs() == null || source.getRequestTimeoutMs() <= 0 ? 10000 : source.getRequestTimeoutMs());
        target.setCallbackUrl(trimToNull(source.getCallbackUrl()));
        target.setEnabled(source.getEnabled() == null ? 1 : source.getEnabled());
        target.setRemark(trimToNull(source.getRemark()));
        if (!StringUtils.hasText(target.getAuthStatus())) {
            target.setAuthStatus(MODE_MOCK.equals(target.getExecutionMode()) ? "MOCK" : "UNAUTHORIZED");
        }
    }

    private void applyCallback(IntegrationCallbackConfig target,
                               IntegrationCallbackConfig source,
                               IntegrationCallbackConfig existing) {
        target.setProviderCode(StringUtils.hasText(source.getProviderCode()) ? normalize(source.getProviderCode()) : null);
        target.setCallbackName(source.getCallbackName().trim());
        target.setCallbackUrl(source.getCallbackUrl().trim());
        target.setSignatureMode(StringUtils.hasText(source.getSignatureMode()) ? source.getSignatureMode().trim() : "NONE");
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
            return exchangeDouyinAuthCode(config);
        }
        throw new BusinessException("抖音来客 LIVE 模式必须先完成 auth_code 授权");
    }

    private String exchangeDouyinAuthCode(IntegrationProviderConfig config) {
        String appId = resolveDouyinAppId(config);
        String secret = resolveDouyinSecret(config);
        config.setLastAuthCodeAt(LocalDateTime.now());
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
        return applyDouyinTokenResponse(config, response, "换取 access_token 失败", "EXCHANGED");
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
            throw new BusinessException("抖音来客必须配置 appId");
        }
        return appId.trim();
    }

    private String resolveDouyinSecret(IntegrationProviderConfig config) {
        String secret = config.getClientSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("抖音来客必须配置应用 Secret");
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

    private boolean isTrustedCallback(IntegrationProviderConfig provider, IntegrationCallbackConfig callbackConfig) {
        if (provider == null || !MODE_LIVE.equals(provider.getExecutionMode())) {
            return true;
        }
        return isLocalUrl(provider.getCallbackUrl()) || (callbackConfig != null && isLocalUrl(callbackConfig.getCallbackUrl()));
    }

    private String resolveProviderSignatureStatus(IntegrationProviderConfig provider, boolean trustedCallback) {
        if (provider == null || !MODE_LIVE.equals(provider.getExecutionMode())) {
            return "SKIPPED";
        }
        return trustedCallback ? "LOCAL_BYPASS" : "NOT_VERIFIED";
    }

    private boolean isLocalUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return true;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            return host == null
                    || "127.0.0.1".equals(host)
                    || "localhost".equalsIgnoreCase(host)
                    || "0.0.0.0".equals(host);
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
}
