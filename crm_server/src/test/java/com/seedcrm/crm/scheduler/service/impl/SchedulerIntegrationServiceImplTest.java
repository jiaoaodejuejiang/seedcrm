package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackConfigMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulerIntegrationServiceImplTest {

    @Mock
    private IntegrationProviderConfigMapper providerConfigMapper;

    @Mock
    private IntegrationCallbackConfigMapper callbackConfigMapper;

    @Mock
    private IntegrationCallbackEventLogMapper callbackEventLogMapper;

    private SchedulerIntegrationServiceImpl schedulerIntegrationService;

    @BeforeEach
    void setUp() {
        schedulerIntegrationService = new SchedulerIntegrationServiceImpl(
                providerConfigMapper,
                callbackConfigMapper,
                callbackEventLogMapper,
                new ObjectMapper());
    }

    @Test
    void saveDistributionProviderShouldUseDistributionVoucherDefaults() {
        when(providerConfigMapper.insert(any(IntegrationProviderConfig.class))).thenReturn(1);

        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DISTRIBUTION");
        request.setProviderName("外部分销系统");
        request.setModuleCode("SCHEDULER");
        request.setExecutionMode("LIVE");
        request.setAuthType("SIGN_TOKEN");
        request.setEndpointPath("/open/distribution/events");
        request.setStatusQueryPath("/open/distribution/orders/status");
        request.setReconciliationPullPath("/open/distribution/orders/reconcile");
        request.setEnabled(1);

        schedulerIntegrationService.saveProvider(request);

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).insert(providerCaptor.capture());
        IntegrationProviderConfig persisted = providerCaptor.getValue();
        assertThat(persisted.getVoucherVerifyPath()).isEqualTo("/open/distribution/vouchers/verify");
        assertThat(persisted.getVerifyCodeField()).isEqualTo("voucherCode");
        assertThat(persisted.getRefundApplyPath()).isNull();
    }

    @Test
    void testDistributionProviderShouldValidateVoucherConfigInLiveMode() {
        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DISTRIBUTION");
        request.setProviderName("外部分销系统");
        request.setModuleCode("SCHEDULER");
        request.setExecutionMode("LIVE");
        request.setAuthType("SIGN_TOKEN");
        request.setBaseUrl("https://partner.example.com");
        request.setClientSecret("secret-001");
        request.setVoucherVerifyPath("/open/distribution/vouchers/verify");
        request.setVerifyCodeField("voucherCode");

        IntegrationProviderConfig result = schedulerIntegrationService.testProvider(request);

        assertThat(result.getLastTestStatus()).isEqualTo("SUCCESS");
        assertThat(result.getLastTestMessage()).contains("分销券核销配置模拟检查通过");
        assertThat(result.getLastTestMessage()).contains("券码字段");
        verify(providerConfigMapper, never()).updateById(any(IntegrationProviderConfig.class));
    }

    @Test
    void testDistributionProviderShouldRejectLiveModeWithoutSecret() {
        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DISTRIBUTION");
        request.setProviderName("外部分销系统");
        request.setModuleCode("SCHEDULER");
        request.setExecutionMode("LIVE");
        request.setAuthType("SIGN_TOKEN");
        request.setBaseUrl("https://partner.example.com");
        request.setVoucherVerifyPath("/open/distribution/vouchers/verify");
        request.setVerifyCodeField("voucherCode");

        assertThatThrownBy(() -> schedulerIntegrationService.testProvider(request))
                .hasMessageContaining("签名密钥");

        verify(providerConfigMapper, never()).updateById(any(IntegrationProviderConfig.class));
    }

    @Test
    void testDistributionProviderShouldRejectInvalidVoucherPath() {
        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DISTRIBUTION");
        request.setProviderName("外部分销系统");
        request.setModuleCode("SCHEDULER");
        request.setExecutionMode("LIVE");
        request.setAuthType("SIGN_TOKEN");
        request.setBaseUrl("https://partner.example.com");
        request.setClientSecret("secret-001");
        request.setVoucherVerifyPath("open/distribution/vouchers/verify");
        request.setVerifyCodeField("voucherCode");

        assertThatThrownBy(() -> schedulerIntegrationService.testProvider(request))
                .hasMessageContaining("门店核销调用路径");

        verify(providerConfigMapper, never()).updateById(any(IntegrationProviderConfig.class));
    }

    @Test
    void shouldReceiveDouyinCallbackAndUpdateStatus() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("抖音来客线索");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");
        provider.setAccessToken("live-access-token");
        provider.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");
        callback.setCallbackName("抖音来客授权回调");

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        IntegrationProviderConfig result = schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                "/scheduler/oauth/douyin/callback",
                "GET",
                Map.of("code", "auth-code-001", "state", "seedcrm-douyin"),
                null);

        assertThat(result).isNotNull();
        assertThat(result.getAuthStatus()).isEqualTo("AUTH_CODE_RECEIVED");
        assertThat(result.getAuthCodeMasked()).isNotBlank();

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getAuthCode()).isEqualTo("auth-code-001");
        assertThat(providerCaptor.getValue().getLastCallbackStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProviderCode()).isEqualTo("DOUYIN_LAIKE");
        assertThat(logCaptor.getValue().getAuthCode()).contains("****");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("NEW");
        assertThat(logCaptor.getValue().getProcessPolicy()).isEqualTo("AUTH_UPDATE_ONLY");
    }

    @Test
    void shouldPersistDouyinAuthCodeEvenWhenTokenExchangeCannotRunYet() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("Douyin");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");
        callback.setCallbackName("Douyin OAuth");

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        IntegrationProviderConfig result = schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "Douyin OAuth",
                "/scheduler/oauth/douyin/callback",
                "GET",
                Map.of("auth_code", "auth-code-002", "state", "seedcrm-douyin"),
                null);

        assertThat(result).isNotNull();
        assertThat(result.getAuthStatus()).isEqualTo("AUTH_CODE_RECEIVED");
        assertThat(result.getAuthCodeMasked()).isNotBlank();

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        IntegrationProviderConfig persisted = providerCaptor.getValue();
        assertThat(persisted.getAuthCode()).isEqualTo("auth-code-002");
        assertThat(persisted.getAuthCodeStatus()).isEqualTo("RECEIVED");
        assertThat(persisted.getLastAuthCodeAt()).isNotNull();
        assertThat(persisted.getLastCallbackStatus()).isEqualTo("FAILED");
        assertThat(persisted.getLastCallbackMessage()).contains("appId");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAuthCode()).contains("****");
        assertThat(logCaptor.getValue().getProcessPolicy()).isEqualTo("AUTH_UPDATE_ONLY");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldNotUpdateAuthorizationFieldsFromBusinessEventCallback() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("Douyin");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("http://127.0.0.1:8080/scheduler/callback/douyin/refund");

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("http://127.0.0.1:8080/scheduler/callback/douyin/refund");
        callback.setCallbackName("Douyin Event");

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "Douyin Event",
                "/scheduler/callback/douyin/refund",
                "POST",
                Map.of("event_type", "clue_record_update"),
                "{\"auth_code\":\"event-auth-code\"}");

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        IntegrationProviderConfig persisted = providerCaptor.getValue();
        assertThat(persisted.getAuthCode()).isNull();
        assertThat(persisted.getAuthCodeStatus()).isNull();
        assertThat(persisted.getLastAuthCodeAt()).isNull();
        assertThat(persisted.getLastCallbackStatus()).isEqualTo("RECEIVED");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAuthCode()).contains("****");
        assertThat(logCaptor.getValue().getProcessPolicy()).isEqualTo("LOG_ONLY");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("RECEIVED");
    }

    @Test
    void shouldIgnoreDuplicateCallbackWithoutUpdatingProviderAgain() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("抖音来客线索");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("http://127.0.0.1:8080/scheduler/oauth/douyin/callback");
        callback.setCallbackName("抖音来客授权回调");

        IntegrationCallbackEventLog duplicate = new IntegrationCallbackEventLog();
        duplicate.setId(9L);
        duplicate.setIdempotencyKey("duplicate-key");

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);
        when(callbackEventLogMapper.selectOne(any())).thenReturn(duplicate);

        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                "/scheduler/oauth/douyin/callback",
                "GET",
                Map.of("code", "auth-code-001", "state", "seedcrm-douyin"),
                null);

        verify(providerConfigMapper, never()).updateById(any(IntegrationProviderConfig.class));
        verify(callbackConfigMapper, never()).updateById(any(IntegrationCallbackConfig.class));

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("DUPLICATE");
        assertThat(logCaptor.getValue().getIdempotencyStatus()).isEqualTo("DUPLICATE");
        assertThat(logCaptor.getValue().getProcessPolicy()).isEqualTo("LOG_ONLY");
    }

    @Test
    void shouldNotSaveAuthCodeForUnsignedLiveCallback() {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("抖音来客线索");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("https://crm.example.com/scheduler/oauth/douyin/callback");

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("https://crm.example.com/scheduler/oauth/douyin/callback");
        callback.setCallbackName("抖音来客授权回调");
        callback.setSignatureMode("NONE_LOCAL_ONLY");

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                "/scheduler/oauth/douyin/callback",
                "GET",
                Map.of("code", "auth-code-001", "state", "seedcrm-douyin"),
                null);

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getAuthCode()).isNull();
        assertThat(providerCaptor.getValue().getLastCallbackStatus()).isEqualTo("UNVERIFIED");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("NOT_VERIFIED");
        assertThat(logCaptor.getValue().getTrustLevel()).isEqualTo("UNVERIFIED");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("UNVERIFIED");
    }

    @Test
    void shouldSaveAuthCodeForVerifiedHmacCallback() throws Exception {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("抖音来客线索");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("https://crm.example.com/scheduler/oauth/douyin/callback");
        provider.setAccessToken("live-access-token");
        provider.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("https://crm.example.com/scheduler/oauth/douyin/callback");
        callback.setCallbackName("抖音来客授权回调");
        callback.setSignatureMode("HMAC_SHA256");
        callback.setTokenValue("secret-001");

        String timestamp = String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8)));
        String nonce = "nonce-001";
        String signature = hmacSha256("secret-001", timestamp + nonce);

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客授权回调",
                "/scheduler/oauth/douyin/callback",
                "GET",
                Map.of("code", "auth-code-001", "timestamp", timestamp, "nonce", nonce, "signature", signature),
                null);

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getAuthCode()).isEqualTo("auth-code-001");
        assertThat(providerCaptor.getValue().getLastCallbackStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSignatureMode()).isEqualTo("HMAC_SHA256");
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("VERIFIED");
        assertThat(logCaptor.getValue().getTrustLevel()).isEqualTo("VERIFIED");
        assertThat(logCaptor.getValue().getProcessPolicy()).isEqualTo("AUTH_UPDATE_ONLY");
    }

    @Test
    void shouldOnlyLogVerifiedRefundCallbackInV1() throws Exception {
        IntegrationProviderConfig provider = new IntegrationProviderConfig();
        provider.setId(1L);
        provider.setProviderCode("DOUYIN_LAIKE");
        provider.setProviderName("抖音来客线索");
        provider.setModuleCode("CLUE");
        provider.setExecutionMode("LIVE");
        provider.setCallbackUrl("https://crm.example.com/scheduler/callback/douyin/refund");

        IntegrationCallbackConfig callback = new IntegrationCallbackConfig();
        callback.setId(2L);
        callback.setProviderCode("DOUYIN_LAIKE");
        callback.setCallbackUrl("https://crm.example.com/scheduler/callback/douyin/refund");
        callback.setCallbackName("抖音来客退款回调");
        callback.setSignatureMode("HMAC_SHA256");
        callback.setTokenValue("secret-001");

        String payload = "{\"event_type\":\"refund_status_change\",\"refund_id\":\"rf-001\"}";
        String timestamp = String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(8)));
        String nonce = "nonce-001";
        String signature = hmacSha256("secret-001", timestamp + nonce + payload);

        when(providerConfigMapper.selectOne(any())).thenReturn(provider);
        when(callbackConfigMapper.selectOne(any())).thenReturn(callback);

        schedulerIntegrationService.receiveProviderCallback(
                "DOUYIN_LAIKE",
                "抖音来客退款回调",
                "/scheduler/callback/douyin/refund",
                "POST",
                Map.of("timestamp", timestamp, "nonce", nonce, "signature", signature),
                payload);

        ArgumentCaptor<IntegrationCallbackEventLog> logCaptor = ArgumentCaptor.forClass(IntegrationCallbackEventLog.class);
        verify(callbackEventLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSignatureStatus()).isEqualTo("VERIFIED");
        assertThat(logCaptor.getValue().getProcessPolicy()).isEqualTo("LOG_ONLY");
        assertThat(logCaptor.getValue().getProcessStatus()).isEqualTo("RECEIVED");
        assertThat(logCaptor.getValue().getProcessMessage()).contains("仅记录");
    }

    @Test
    void shouldPreserveVoucherConfigWhenLegacyUpdateOmitsNewFields() {
        IntegrationProviderConfig existing = new IntegrationProviderConfig();
        existing.setId(9L);
        existing.setProviderCode("DOUYIN_LAIKE");
        existing.setProviderName("抖音来客");
        existing.setModuleCode("CLUE");
        existing.setExecutionMode("LIVE");
        existing.setVoucherPreparePath("/goodlife/v1/fulfilment/certificate/prepare/");
        existing.setVoucherVerifyPath("/goodlife/v1/fulfilment/certificate/verify/");
        existing.setVoucherCancelPath("/goodlife/v1/fulfilment/certificate/cancel/");
        existing.setPoiId("poi-001");
        existing.setVerifyCodeField("encrypted_codes");
        existing.setRateLimitPerMinute(80);
        existing.setCacheTtlSeconds(45);

        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DOUYIN_LAIKE");
        request.setProviderName("抖音来客");
        request.setModuleCode("CLUE");
        request.setExecutionMode("LIVE");
        request.setAppId("app-001");

        when(providerConfigMapper.selectOne(any())).thenReturn(existing);
        when(providerConfigMapper.updateById(any(IntegrationProviderConfig.class))).thenReturn(1);

        IntegrationProviderConfig result = schedulerIntegrationService.saveProvider(request);

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());

        IntegrationProviderConfig persisted = providerCaptor.getValue();
        assertThat(persisted.getVoucherPreparePath()).isEqualTo(existing.getVoucherPreparePath());
        assertThat(persisted.getVoucherVerifyPath()).isEqualTo(existing.getVoucherVerifyPath());
        assertThat(persisted.getVoucherCancelPath()).isEqualTo(existing.getVoucherCancelPath());
        assertThat(persisted.getPoiId()).isEqualTo(existing.getPoiId());
        assertThat(persisted.getVerifyCodeField()).isEqualTo(existing.getVerifyCodeField());
        assertThat(persisted.getRateLimitPerMinute()).isEqualTo(80);
        assertThat(persisted.getCacheTtlSeconds()).isEqualTo(45);

        assertThat(result.getVoucherPreparePath()).isEqualTo(existing.getVoucherPreparePath());
        assertThat(result.getVoucherVerifyPath()).isEqualTo(existing.getVoucherVerifyPath());
        assertThat(result.getVoucherCancelPath()).isEqualTo(existing.getVoucherCancelPath());
        assertThat(result.getPoiId()).isEqualTo(existing.getPoiId());
        assertThat(result.getVerifyCodeField()).isEqualTo(existing.getVerifyCodeField());
        assertThat(result.getRateLimitPerMinute()).isEqualTo(80);
        assertThat(result.getCacheTtlSeconds()).isEqualTo(45);
    }

    @Test
    void shouldRejectExpiredAuthCodeBeforeTokenExchange() {
        IntegrationProviderConfig existing = new IntegrationProviderConfig();
        existing.setId(18L);
        existing.setProviderCode("DOUYIN_LAIKE");
        existing.setProviderName("鎶栭煶鏉ュ");
        existing.setModuleCode("CLUE");
        existing.setExecutionMode("LIVE");
        existing.setAuthType("AUTH_CODE");
        existing.setAppId("app-001");
        existing.setBaseUrl("https://open.douyin.com");
        existing.setClientSecret("secret-001");
        existing.setAuthCode("expired-code");
        existing.setLastAuthCodeAt(LocalDateTime.now().minusMinutes(10));

        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DOUYIN_LAIKE");
        request.setProviderName("鎶栭煶鏉ュ");
        request.setModuleCode("CLUE");
        request.setExecutionMode("LIVE");
        request.setBaseUrl("https://open.douyin.com");
        when(providerConfigMapper.selectOne(any())).thenReturn(existing);
        when(providerConfigMapper.selectById(18L)).thenReturn(existing);

        assertThatThrownBy(() -> schedulerIntegrationService.testProvider(request))
                .hasMessageContaining("auth_code");

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getAuthStatus()).isEqualTo("EXPIRED");
        assertThat(providerCaptor.getValue().getAuthCodeStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void shouldSaveDistributionRateLimitAndCacheConfig() {
        IntegrationProviderConfig existing = new IntegrationProviderConfig();
        existing.setId(12L);
        existing.setProviderCode("DISTRIBUTION");
        existing.setProviderName("外部分销系统");
        existing.setModuleCode("SCHEDULER");
        existing.setExecutionMode("MOCK");

        IntegrationProviderConfig request = new IntegrationProviderConfig();
        request.setProviderCode("DISTRIBUTION");
        request.setProviderName("外部分销系统");
        request.setModuleCode("SCHEDULER");
        request.setExecutionMode("LIVE");
        request.setRateLimitPerMinute(120);
        request.setCacheTtlSeconds(90);

        when(providerConfigMapper.selectOne(any())).thenReturn(existing);
        when(providerConfigMapper.updateById(any(IntegrationProviderConfig.class))).thenReturn(1);

        IntegrationProviderConfig result = schedulerIntegrationService.saveProvider(request);

        ArgumentCaptor<IntegrationProviderConfig> providerCaptor = ArgumentCaptor.forClass(IntegrationProviderConfig.class);
        verify(providerConfigMapper).updateById(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getRateLimitPerMinute()).isEqualTo(120);
        assertThat(providerCaptor.getValue().getCacheTtlSeconds()).isEqualTo(90);
        assertThat(result.getRateLimitPerMinute()).isEqualTo(120);
        assertThat(result.getCacheTtlSeconds()).isEqualTo(90);
    }

    private String hmacSha256(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
