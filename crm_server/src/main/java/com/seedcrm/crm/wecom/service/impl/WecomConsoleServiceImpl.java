package com.seedcrm.crm.wecom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.wecom.entity.WecomAppConfig;
import com.seedcrm.crm.wecom.entity.WecomLiveCodeConfig;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.entity.WecomTouchRule;
import com.seedcrm.crm.wecom.mapper.WecomAppConfigMapper;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomLiveCodeConfigMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchRuleMapper;
import com.seedcrm.crm.wecom.service.WecomConsoleService;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.support.WecomBindingStateCodec;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class WecomConsoleServiceImpl implements WecomConsoleService {

    private static final String MODE_MOCK = "MOCK";
    private static final String MODE_LIVE = "LIVE";
    private static final String DEFAULT_APP_CODE = "PRIVATE_DOMAIN";
    private static final String DEFAULT_STRATEGY = "ROUND_ROBIN";
    private static final String AUTH_MODE_SELF_BUILT = "SELF_BUILT";
    private static final String AUTH_MODE_SERVICE_PROVIDER = "SERVICE_PROVIDER";

    private final WecomAppConfigMapper wecomAppConfigMapper;
    private final WecomTouchRuleMapper wecomTouchRuleMapper;
    private final WecomTouchLogMapper wecomTouchLogMapper;
    private final WecomLiveCodeConfigMapper wecomLiveCodeConfigMapper;
    private final CustomerWecomRelationMapper customerWecomRelationMapper;
    private final IntegrationCallbackEventLogMapper integrationCallbackEventLogMapper;
    private final OrderMapper orderMapper;
    private final StaffDirectoryService staffDirectoryService;
    private final ObjectMapper objectMapper;
    private final Map<Long, AccessTokenCacheEntry> accessTokenCache = new ConcurrentHashMap<>();

    public WecomConsoleServiceImpl(WecomAppConfigMapper wecomAppConfigMapper,
                                   WecomTouchRuleMapper wecomTouchRuleMapper,
                                   WecomTouchLogMapper wecomTouchLogMapper,
                                     WecomLiveCodeConfigMapper wecomLiveCodeConfigMapper,
                                     CustomerWecomRelationMapper customerWecomRelationMapper,
                                     IntegrationCallbackEventLogMapper integrationCallbackEventLogMapper,
                                     OrderMapper orderMapper,
                                     StaffDirectoryService staffDirectoryService,
                                     ObjectMapper objectMapper) {
        this.wecomAppConfigMapper = wecomAppConfigMapper;
        this.wecomTouchRuleMapper = wecomTouchRuleMapper;
        this.wecomTouchLogMapper = wecomTouchLogMapper;
        this.wecomLiveCodeConfigMapper = wecomLiveCodeConfigMapper;
        this.customerWecomRelationMapper = customerWecomRelationMapper;
        this.integrationCallbackEventLogMapper = integrationCallbackEventLogMapper;
        this.orderMapper = orderMapper;
        this.staffDirectoryService = staffDirectoryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WecomAppConfig getConfig() {
        WecomAppConfig config = findConfig();
        return maskConfig(config == null ? defaultConfig() : config);
    }

    @Override
    public WecomAppConfig saveConfig(WecomAppConfig config) {
        if (config == null) {
            throw new BusinessException("企业微信配置不能为空");
        }
        if (MODE_LIVE.equals(normalizeExecutionMode(config.getExecutionMode()))
                && config.getSkipVerify() != null
                && config.getSkipVerify() == 1
                && StringUtils.hasText(config.getCallbackUrl())
                && !isLocalUrl(config.getCallbackUrl())) {
            throw new BusinessException("LIVE 模式下仅允许本地回调地址使用跳过验证");
        }
        WecomAppConfig existing = findConfig(config);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            WecomAppConfig entity = defaultConfig();
            applyConfig(entity, config, null);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            if (wecomAppConfigMapper.insert(entity) <= 0) {
                throw new BusinessException("保存企业微信配置失败");
            }
            return maskConfig(entity);
        }
        applyConfig(existing, config, existing);
        existing.setUpdatedAt(now);
        if (wecomAppConfigMapper.updateById(existing) <= 0) {
            throw new BusinessException("更新企业微信配置失败");
        }
        return maskConfig(existing);
    }

    @Override
    public WecomAppConfig testConfig(WecomAppConfig config) {
        WecomAppConfig working = mergeConfig(config);
        if (MODE_MOCK.equals(working.getExecutionMode())) {
            working.setLastTokenStatus("SUCCESS");
            working.setLastTokenMessage("当前为 MOCK 模式，可直接用于页面联调与活码演示");
            working.setLastTokenCheckedAt(LocalDateTime.now());
            updateTokenStateIfPersisted(working);
            return maskConfig(working);
        }
        String accessToken = resolveAccessToken(working);
        working.setLastTokenStatus("SUCCESS");
        working.setLastTokenMessage("连接成功，已获取 access_token：" + maskValue(accessToken));
        working.setLastTokenCheckedAt(LocalDateTime.now());
        updateTokenStateIfPersisted(working);
        return maskConfig(working);
    }

    @Override
    public String resolveAccessToken(WecomAppConfig config) {
        WecomAppConfig working = config == null ? findConfig() : mergeConfig(config);
        if (working == null) {
            throw new BusinessException("请先配置企业微信应用信息");
        }
        if (!MODE_LIVE.equals(working.getExecutionMode())) {
            throw new BusinessException("当前未启用企业微信 LIVE 模式");
        }
        String authMode = normalizeAuthMode(working.getAuthMode());
        if (AUTH_MODE_SERVICE_PROVIDER.equals(authMode)) {
            AccessTokenCacheEntry cached = working.getId() == null ? null : accessTokenCache.get(working.getId());
            if (cached != null && cached.expiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
                return cached.accessToken();
            }
            String accessToken = resolveServiceProviderAccessToken(working);
            if (working.getId() != null) {
                LocalDateTime expiresAt = working.getTokenExpiresAt() == null
                        ? LocalDateTime.now().plusMinutes(110)
                        : working.getTokenExpiresAt().minusMinutes(5);
                accessTokenCache.put(working.getId(), new AccessTokenCacheEntry(accessToken, expiresAt));
            }
            working.setLastTokenStatus("SUCCESS");
            working.setLastTokenMessage("服务商 access_token 已就绪");
            working.setLastTokenCheckedAt(LocalDateTime.now());
            working.setAuthStatus("CONNECTED");
            updateTokenStateIfPersisted(working);
            return accessToken;
        }
        if (!StringUtils.hasText(working.getCorpId()) || !StringUtils.hasText(working.getAppSecret())) {
            throw new BusinessException("LIVE 模式必须配置 corpId 和应用 Secret");
        }
        AccessTokenCacheEntry cached = working.getId() == null ? null : accessTokenCache.get(working.getId());
        if (cached != null && cached.expiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return cached.accessToken();
        }
        RestClient restClient = RestClient.create();
        String uri = UriComponentsBuilder
                .fromHttpUrl("https://qyapi.weixin.qq.com/cgi-bin/gettoken")
                .queryParam("corpid", working.getCorpId().trim())
                .queryParam("corpsecret", working.getAppSecret().trim())
                .toUriString();
        JsonNode response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
        Integer errorCode = response == null ? null : response.path("errcode").asInt(0);
        String accessToken = response == null ? null : response.path("access_token").asText();
        if (errorCode != null && errorCode != 0) {
            String message = response.path("errmsg").asText("获取 access_token 失败");
            working.setLastTokenStatus("FAILED");
            working.setLastTokenMessage(message);
            working.setLastTokenCheckedAt(LocalDateTime.now());
            working.setAuthStatus("FAILED");
            updateTokenStateIfPersisted(working);
            throw new BusinessException(message);
        }
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("企业微信 access_token 返回为空");
        }
        int expiresIn = response.path("expires_in").asInt(7200);
        working.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        if (working.getId() != null) {
            accessTokenCache.put(working.getId(), new AccessTokenCacheEntry(
                    accessToken.trim(),
                    LocalDateTime.now().plusSeconds(Math.max(300, expiresIn - 300L))));
        }
        working.setAccessToken(accessToken.trim());
        working.setAuthStatus("CONNECTED");
        working.setLastTokenStatus("SUCCESS");
        working.setLastTokenMessage("access_token 获取成功");
        working.setLastTokenCheckedAt(LocalDateTime.now());
        updateTokenStateIfPersisted(working);
        return accessToken.trim();
    }

    @Override
    public List<IntegrationCallbackEventLog> listCallbackLogs(String appCode) {
        return integrationCallbackEventLogMapper.selectList(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                        .eq(IntegrationCallbackEventLog::getProviderCode, "WECOM")
                        .eq(StringUtils.hasText(appCode), IntegrationCallbackEventLog::getAppCode, normalize(appCode))
                        .orderByDesc(IntegrationCallbackEventLog::getReceivedAt)
                        .orderByDesc(IntegrationCallbackEventLog::getId)
                        .last("LIMIT 50"));
    }

    @Override
    public String receiveCallback(String appCode,
                                  String callbackPath,
                                  String requestMethod,
                                  Map<String, String> parameters,
                                  String payload) {
        WecomAppConfig config = findConfigByAppCode(appCode);
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> normalizedParameters = normalizeParameters(parameters);
        JsonNode payloadNode = parsePayload(payload);
        String signatureStatus = resolveSignatureStatus(config, requestMethod, normalizedParameters, payload);
        boolean trustedCallback = isTrustedCallback(config, signatureStatus);
        String decryptedPayload = tryDecryptCallbackPayload(config, normalizedParameters, requestMethod, payload, signatureStatus);
        String callbackEventPayload = StringUtils.hasText(decryptedPayload) ? decryptedPayload : payload;
        String authCode = firstNonBlank(
                normalizedParameters.get("auth_code"),
                normalizedParameters.get("code"),
                extractText(payloadNode, "auth_code", "code"),
                extractXmlValue(callbackEventPayload, "AuthCode"));
        String accessToken = firstNonBlank(
                normalizedParameters.get("access_token"),
                extractText(payloadNode, "access_token"));
        String refreshToken = firstNonBlank(
                normalizedParameters.get("refresh_token"),
                extractText(payloadNode, "refresh_token"));
        String suiteTicket = firstNonBlank(
                normalizedParameters.get("suite_ticket"),
                extractXmlValue(callbackEventPayload, "SuiteTicket"));
        String infoType = firstNonBlank(
                normalizedParameters.get("info_type"),
                extractXmlValue(callbackEventPayload, "InfoType"));
        String authCorpId = firstNonBlank(
                normalizedParameters.get("auth_corpid"),
                extractXmlValue(callbackEventPayload, "AuthCorpId"));
        String callbackState = firstNonBlank(
                normalizedParameters.get("state"),
                extractText(payloadNode, "state"),
                extractXmlValue(callbackEventPayload, "State"));
        String eventType = firstNonBlank(
                infoType,
                normalizedParameters.get("event"),
                normalizedParameters.get("event_type"),
                extractText(payloadNode, "event", "event_type", "Event"),
                extractXmlValue(callbackEventPayload, "Event"));
        String externalUserId = firstNonBlank(
                normalizedParameters.get("external_userid"),
                extractText(payloadNode, "external_userid", "ExternalUserID"),
                extractXmlValue(callbackEventPayload, "ExternalUserID"));
        String wecomUserId = firstNonBlank(
                normalizedParameters.get("userid"),
                normalizedParameters.get("user_id"),
                extractText(payloadNode, "userid", "user_id", "UserID"),
                extractXmlValue(callbackEventPayload, "UserID"));
        String errorMessage = firstNonBlank(
                normalizedParameters.get("error_description"),
                normalizedParameters.get("error_msg"),
                normalizedParameters.get("errmsg"),
                normalizedParameters.get("error"),
                extractText(payloadNode, "error_description", "error_msg", "errmsg", "message", "error"));
        String traceId = UUID.randomUUID().toString();
        String processStatus = StringUtils.hasText(errorMessage)
                ? "FAILED"
                : !trustedCallback && MODE_LIVE.equals(config == null ? null : config.getExecutionMode())
                ? "UNVERIFIED"
                : StringUtils.hasText(authCode) || StringUtils.hasText(accessToken)
                || StringUtils.hasText(externalUserId) || StringUtils.hasText(suiteTicket)
                ? "SUCCESS"
                : "RECEIVED";
        String processMessage = StringUtils.hasText(errorMessage)
                ? errorMessage
                : !trustedCallback && MODE_LIVE.equals(config == null ? null : config.getExecutionMode())
                ? "已收到回调，但未通过验签，仅记录审计日志"
                : StringUtils.hasText(authCode)
                ? "已接收企业微信授权回调"
                : StringUtils.hasText(externalUserId)
                ? "已接收企业微信客户事件"
                : "已接收企业微信回调";
        String bindingMessage = bindCustomerByStateIfPossible(callbackState, externalUserId, wecomUserId, trustedCallback);
        if (StringUtils.hasText(bindingMessage)) {
            boolean bindingFailed = bindingMessage.startsWith("BIND_FAILED:");
            processMessage = bindingFailed ? bindingMessage.substring("BIND_FAILED:".length()) : bindingMessage;
            processStatus = bindingFailed ? "FAILED" : "SUCCESS";
        }

        if (config != null) {
            config.setLastCallbackStatus(processStatus);
            config.setLastCallbackMessage(trimMessage(processMessage));
            config.setLastCallbackAt(now);
            config.setLastCallbackPayload(trimPayload(buildCallbackPayload(normalizedParameters, payload)));
            if (trustedCallback) {
                config.setSuiteTicket(resolveSensitiveValue(suiteTicket, config.getSuiteTicket()));
                config.setAuthCode(resolveSensitiveValue(authCode, config.getAuthCode()));
                config.setAccessToken(resolveSensitiveValue(accessToken, config.getAccessToken()));
                config.setRefreshToken(resolveSensitiveValue(refreshToken, config.getRefreshToken()));
                config.setAuthCorpId(trimToNull(firstNonBlank(authCorpId, config.getAuthCorpId())));
                String providerMessage = null;
                try {
                    providerMessage = processServiceProviderCallback(config, infoType, suiteTicket, authCode, authCorpId, now);
                } catch (BusinessException exception) {
                    processStatus = "FAILED";
                    providerMessage = exception.getMessage();
                }
                config.setAuthStatus(resolveAuthStatus(config.getAuthStatus(), errorMessage, authCode, accessToken, externalUserId));
                config.setLastAuthCodeAt(StringUtils.hasText(authCode) ? now : config.getLastAuthCodeAt());
                if (StringUtils.hasText(providerMessage)) {
                    processMessage = providerMessage;
                    config.setLastCallbackMessage(trimMessage(providerMessage));
                }
            }
            config.setUpdatedAt(now);
            wecomAppConfigMapper.updateById(config);
        }

        IntegrationCallbackEventLog log = new IntegrationCallbackEventLog();
        log.setProviderCode("WECOM");
        log.setCallbackName(StringUtils.hasText(appCode) ? appCode.trim() : DEFAULT_APP_CODE);
        log.setAppCode(config == null || !StringUtils.hasText(config.getAppCode()) ? DEFAULT_APP_CODE : config.getAppCode());
        log.setRequestMethod(StringUtils.hasText(requestMethod) ? requestMethod.trim().toUpperCase(Locale.ROOT) : "POST");
        log.setCallbackPath(trimToNull(callbackPath));
        log.setQueryString(trimToNull(toCompactJson(maskSensitiveEntries(normalizedParameters))));
        log.setRequestPayload(trimPayload(sanitizePayload(payload)));
        log.setAuthCode(maskValue(authCode));
        log.setCallbackState(trimToNull(callbackState));
        log.setEventType(trimToNull(eventType));
        log.setTraceId(traceId);
        log.setSignatureStatus(signatureStatus);
        log.setProcessStatus(processStatus);
        log.setProcessMessage(trimMessage(processMessage));
        log.setReceivedAt(now);
        log.setCreatedAt(now);
        integrationCallbackEventLogMapper.insert(log);

        String echostr = normalizedParameters.get("echostr");
        if ("GET".equalsIgnoreCase(requestMethod) && StringUtils.hasText(echostr)) {
            if (config != null && config.getSkipVerify() != null && config.getSkipVerify() == 0) {
                try {
                    return verifyAndDecryptEchoStr(config, normalizedParameters, echostr);
                } catch (Exception exception) {
                    if (config != null) {
                        config.setLastCallbackStatus("FAILED");
                        config.setLastCallbackMessage(trimMessage("URL 校验失败：" + exception.getMessage()));
                        config.setLastCallbackAt(now);
                        config.setUpdatedAt(now);
                        wecomAppConfigMapper.updateById(config);
                    }
                    throw new BusinessException("企业微信 URL 校验失败");
                }
            }
            return echostr;
        }
        return "success";
    }

    private String bindCustomerByStateIfPossible(String callbackState,
                                                 String externalUserId,
                                                 String wecomUserId,
                                                 boolean trustedCallback) {
        if (!trustedCallback || !StringUtils.hasText(callbackState) || !StringUtils.hasText(externalUserId)) {
            return null;
        }
        WecomBindingStateCodec.BindingState bindingState = WecomBindingStateCodec.decode(callbackState);
        if (bindingState == null || bindingState.customerId() == null) {
            return null;
        }
        String expectedPhone = staffDirectoryService.getUserPhone(bindingState.userId());
        if (!WecomBindingStateCodec.matchesPhoneHash(expectedPhone, bindingState.userPhoneHash())) {
            return "BIND_FAILED:企微 state 员工手机号校验失败，已拒绝绑定客户关系";
        }
        Order order = orderMapper.selectById(bindingState.orderId());
        if (order == null) {
            return "BIND_FAILED:企微 state 对应订单不存在，已拒绝绑定客户关系";
        }
        if (!Objects.equals(order.getCustomerId(), bindingState.customerId())) {
            return "BIND_FAILED:企微 state 客户与订单客户不一致，已拒绝绑定客户关系";
        }
        String expectedWecomAccount = staffDirectoryService.getWecomAccount(bindingState.userId());
        if (StringUtils.hasText(wecomUserId)
                && StringUtils.hasText(expectedWecomAccount)
                && !expectedWecomAccount.equals(wecomUserId.trim())) {
            return "BIND_FAILED:企微回调员工账号与 state 员工不一致，已拒绝绑定客户关系";
        }
        CustomerWecomRelation existing = customerWecomRelationMapper.selectOne(Wrappers.<CustomerWecomRelation>lambdaQuery()
                .eq(CustomerWecomRelation::getCustomerId, bindingState.customerId())
                .last("LIMIT 1"));
        if (existing == null) {
            CustomerWecomRelation relation = new CustomerWecomRelation();
            relation.setCustomerId(bindingState.customerId());
            relation.setExternalUserid(externalUserId);
            relation.setWecomUserId(StringUtils.hasText(wecomUserId) ? wecomUserId.trim() : String.valueOf(bindingState.userId()));
            relation.setCreateTime(LocalDateTime.now());
            customerWecomRelationMapper.insert(relation);
        } else {
            existing.setExternalUserid(externalUserId);
            existing.setWecomUserId(StringUtils.hasText(wecomUserId) ? wecomUserId.trim() : String.valueOf(bindingState.userId()));
            customerWecomRelationMapper.updateById(existing);
        }
        return "已根据企微 state 绑定客户与当前员工，订单ID：" + bindingState.orderId();
    }

    @Override
    public List<WecomTouchRule> listRules() {
        return wecomTouchRuleMapper.selectList(Wrappers.<WecomTouchRule>lambdaQuery()
                        .orderByAsc(WecomTouchRule::getTriggerType)
                        .orderByAsc(WecomTouchRule::getId))
                .stream()
                .peek(this::applyRuleDefaults)
                .toList();
    }

    @Override
    public WecomTouchRule saveRule(WecomTouchRule rule) {
        if (rule == null
                || !StringUtils.hasText(rule.getTriggerType())
                || !StringUtils.hasText(rule.getMessageTemplate())) {
            throw new BusinessException("触达场景和消息模板不能为空");
        }
        WecomTouchRule existing = rule.getId() == null ? null : wecomTouchRuleMapper.selectById(rule.getId());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            applyRuleDefaults(rule);
            rule.setTriggerType(normalize(rule.getTriggerType()));
            rule.setRuleName(StringUtils.hasText(rule.getRuleName()) ? rule.getRuleName().trim() : rule.getTriggerType());
            rule.setTag(StringUtils.hasText(rule.getTag()) ? rule.getTag().trim() : rule.getTriggerType());
            rule.setMessageTemplate(rule.getMessageTemplate().trim());
            rule.setCreateTime(now);
            if (wecomTouchRuleMapper.insert(rule) <= 0) {
                throw new BusinessException("保存触达规则失败");
            }
            return rule;
        }
        existing.setRuleName(StringUtils.hasText(rule.getRuleName()) ? rule.getRuleName().trim() : existing.getRuleName());
        existing.setTag(StringUtils.hasText(rule.getTag()) ? rule.getTag().trim() : existing.getTag());
        existing.setTriggerType(normalize(rule.getTriggerType()));
        existing.setMessageTemplate(rule.getMessageTemplate().trim());
        existing.setIsEnabled(rule.getIsEnabled() == null ? existing.getIsEnabled() : rule.getIsEnabled());
        if (wecomTouchRuleMapper.updateById(existing) <= 0) {
            throw new BusinessException("更新触达规则失败");
        }
        applyRuleDefaults(existing);
        return existing;
    }

    @Override
    public WecomTouchRule toggleRule(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new BusinessException("ruleId 不能为空");
        }
        WecomTouchRule rule = wecomTouchRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("未找到对应触达规则");
        }
        rule.setIsEnabled(rule.getIsEnabled() != null && rule.getIsEnabled() == 1 ? 0 : 1);
        wecomTouchRuleMapper.updateById(rule);
        applyRuleDefaults(rule);
        return rule;
    }

    @Override
    public List<WecomTouchLog> listLogs() {
        return wecomTouchLogMapper.selectList(Wrappers.<WecomTouchLog>lambdaQuery()
                .orderByDesc(WecomTouchLog::getCreateTime)
                .orderByDesc(WecomTouchLog::getId)
                .last("LIMIT 50"));
    }

    @Override
    public List<WecomLiveCodeConfig> listLiveCodeConfigs() {
        return wecomLiveCodeConfigMapper.selectList(Wrappers.<WecomLiveCodeConfig>lambdaQuery()
                        .orderByDesc(WecomLiveCodeConfig::getGeneratedAt)
                        .orderByDesc(WecomLiveCodeConfig::getUpdatedAt)
                        .orderByDesc(WecomLiveCodeConfig::getId))
                .stream()
                .map(this::inflateLiveCodeConfig)
                .toList();
    }

    @Override
    public WecomLiveCodeConfig saveLiveCodeConfig(WecomLiveCodeConfig config) {
        if (config == null || !StringUtils.hasText(config.getCodeName())) {
            throw new BusinessException("活码名称不能为空");
        }
        if ((config.getEmployeeAccounts() == null || config.getEmployeeAccounts().isEmpty())
                && (config.getEmployeeNames() == null || config.getEmployeeNames().isEmpty())) {
            throw new BusinessException("请至少选择一名活码承接员工");
        }
        WecomLiveCodeConfig existing = config.getId() == null ? null : wecomLiveCodeConfigMapper.selectById(config.getId());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            WecomLiveCodeConfig entity = new WecomLiveCodeConfig();
            applyLiveCodeConfig(entity, config);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            if (wecomLiveCodeConfigMapper.insert(entity) <= 0) {
                throw new BusinessException("保存活码配置失败");
            }
            return inflateLiveCodeConfig(entity);
        }
        applyLiveCodeConfig(existing, config);
        existing.setUpdatedAt(now);
        if (wecomLiveCodeConfigMapper.updateById(existing) <= 0) {
            throw new BusinessException("更新活码配置失败");
        }
        return inflateLiveCodeConfig(existing);
    }

    @Override
    public WecomLiveCodeConfig publishLiveCodeConfig(Long configId, List<String> storeNames) {
        if (configId == null || configId <= 0) {
            throw new BusinessException("活码配置 ID 不能为空");
        }
        WecomLiveCodeConfig existing = wecomLiveCodeConfigMapper.selectById(configId);
        if (existing == null) {
            throw new BusinessException("未找到对应的活码配置");
        }
        if (!StringUtils.hasText(existing.getQrCodeUrl())) {
            throw new BusinessException("请先生成二维码后再发布到门店");
        }
        List<String> normalizedStores = storeNames == null ? List.of() : storeNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedStores.isEmpty()) {
            throw new BusinessException("至少选择一个发布门店");
        }
        existing.setStoreNamesJson(writeStringList(normalizedStores));
        existing.setPublishedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        if (wecomLiveCodeConfigMapper.updateById(existing) <= 0) {
            throw new BusinessException("发布活码到门店失败");
        }
        return inflateLiveCodeConfig(existing);
    }

    private WecomAppConfig findConfig() {
        return wecomAppConfigMapper.selectOne(Wrappers.<WecomAppConfig>lambdaQuery()
                .orderByAsc(WecomAppConfig::getId)
                .last("LIMIT 1"));
    }

    private WecomAppConfig findConfigByAppCode(String appCode) {
        if (StringUtils.hasText(appCode)) {
            WecomAppConfig appConfig = wecomAppConfigMapper.selectOne(Wrappers.<WecomAppConfig>lambdaQuery()
                    .eq(WecomAppConfig::getAppCode, normalize(appCode))
                    .last("LIMIT 1"));
            if (appConfig != null) {
                return appConfig;
            }
        }
        return findConfig();
    }

    private WecomAppConfig findConfig(WecomAppConfig config) {
        if (config == null) {
            return findConfig();
        }
        if (config.getId() != null && config.getId() > 0) {
            return wecomAppConfigMapper.selectById(config.getId());
        }
        if (StringUtils.hasText(config.getAppCode())) {
            return wecomAppConfigMapper.selectOne(Wrappers.<WecomAppConfig>lambdaQuery()
                    .eq(WecomAppConfig::getAppCode, normalize(config.getAppCode()))
                    .last("LIMIT 1"));
        }
        return findConfig();
    }

    private WecomAppConfig mergeConfig(WecomAppConfig config) {
        WecomAppConfig working = defaultConfig();
        WecomAppConfig existing = findConfig(config);
        if (existing != null) {
            working.setId(existing.getId());
            working.setAppCode(existing.getAppCode());
            working.setAppId(existing.getAppId());
            working.setSuiteId(existing.getSuiteId());
            working.setAuthMode(existing.getAuthMode());
            working.setCorpId(existing.getCorpId());
            working.setAuthCorpId(existing.getAuthCorpId());
            working.setAgentId(existing.getAgentId());
            working.setAppSecret(existing.getAppSecret());
            working.setSuiteSecret(existing.getSuiteSecret());
            working.setAuthCode(existing.getAuthCode());
            working.setSuiteTicket(existing.getSuiteTicket());
            working.setPermanentCode(existing.getPermanentCode());
            working.setAccessToken(existing.getAccessToken());
            working.setRefreshToken(existing.getRefreshToken());
            working.setSuiteAccessToken(existing.getSuiteAccessToken());
            working.setCorpAccessToken(existing.getCorpAccessToken());
            working.setExecutionMode(existing.getExecutionMode());
            working.setCallbackUrl(existing.getCallbackUrl());
            working.setRedirectUri(existing.getRedirectUri());
            working.setTrustedDomain(existing.getTrustedDomain());
            working.setCallbackToken(existing.getCallbackToken());
            working.setEncodingAesKey(existing.getEncodingAesKey());
            working.setLiveCodeType(existing.getLiveCodeType());
            working.setLiveCodeScene(existing.getLiveCodeScene());
            working.setLiveCodeStyle(existing.getLiveCodeStyle());
            working.setSkipVerify(existing.getSkipVerify());
            working.setStateTemplate(existing.getStateTemplate());
            working.setMarkSource(existing.getMarkSource());
            working.setEnabled(existing.getEnabled());
            working.setLastTokenStatus(existing.getLastTokenStatus());
            working.setLastTokenMessage(existing.getLastTokenMessage());
            working.setLastTokenCheckedAt(existing.getLastTokenCheckedAt());
            working.setSuiteTicketAt(existing.getSuiteTicketAt());
            working.setAuthStatus(existing.getAuthStatus());
            working.setLastCallbackStatus(existing.getLastCallbackStatus());
            working.setLastCallbackMessage(existing.getLastCallbackMessage());
            working.setLastAuthCodeAt(existing.getLastAuthCodeAt());
            working.setTokenExpiresAt(existing.getTokenExpiresAt());
            working.setSuiteAccessTokenExpiresAt(existing.getSuiteAccessTokenExpiresAt());
            working.setCorpAccessTokenExpiresAt(existing.getCorpAccessTokenExpiresAt());
            working.setLastCallbackAt(existing.getLastCallbackAt());
            working.setLastCallbackPayload(existing.getLastCallbackPayload());
        }
        applyConfig(working, config, existing);
        return working;
    }

    private void applyConfig(WecomAppConfig target, WecomAppConfig source, WecomAppConfig existing) {
        target.setAppCode(StringUtils.hasText(source.getAppCode()) ? normalize(source.getAppCode()) : DEFAULT_APP_CODE);
        target.setAppId(trimToNull(source.getAppId()));
        target.setSuiteId(trimToNull(source.getSuiteId()));
        target.setAuthMode(normalizeAuthMode(source.getAuthMode()));
        target.setCorpId(trimToNull(source.getCorpId()));
        target.setAuthCorpId(trimToNull(source.getAuthCorpId()));
        target.setAgentId(trimToNull(source.getAgentId()));
        target.setAppSecret(resolveSensitiveValue(source.getAppSecret(), existing == null ? null : existing.getAppSecret()));
        target.setSuiteSecret(resolveSensitiveValue(source.getSuiteSecret(), existing == null ? null : existing.getSuiteSecret()));
        target.setAuthCode(resolveSensitiveValue(source.getAuthCode(), existing == null ? null : existing.getAuthCode()));
        target.setSuiteTicket(resolveSensitiveValue(source.getSuiteTicket(), existing == null ? null : existing.getSuiteTicket()));
        target.setPermanentCode(resolveSensitiveValue(source.getPermanentCode(), existing == null ? null : existing.getPermanentCode()));
        target.setAccessToken(resolveSensitiveValue(source.getAccessToken(), existing == null ? null : existing.getAccessToken()));
        target.setRefreshToken(resolveSensitiveValue(source.getRefreshToken(), existing == null ? null : existing.getRefreshToken()));
        target.setSuiteAccessToken(resolveSensitiveValue(source.getSuiteAccessToken(), existing == null ? null : existing.getSuiteAccessToken()));
        target.setCorpAccessToken(resolveSensitiveValue(source.getCorpAccessToken(), existing == null ? null : existing.getCorpAccessToken()));
        target.setExecutionMode(normalizeExecutionMode(source.getExecutionMode()));
        target.setCallbackUrl(trimToNull(source.getCallbackUrl()));
        target.setRedirectUri(trimToNull(source.getRedirectUri()));
        target.setTrustedDomain(trimToNull(source.getTrustedDomain()));
        target.setCallbackToken(resolveSensitiveValue(source.getCallbackToken(), existing == null ? null : existing.getCallbackToken()));
        target.setEncodingAesKey(resolveSensitiveValue(source.getEncodingAesKey(), existing == null ? null : existing.getEncodingAesKey()));
        target.setLiveCodeType(source.getLiveCodeType() == null || source.getLiveCodeType() <= 0 ? 2 : source.getLiveCodeType());
        target.setLiveCodeScene(source.getLiveCodeScene() == null || source.getLiveCodeScene() <= 0 ? 2 : source.getLiveCodeScene());
        target.setLiveCodeStyle(source.getLiveCodeStyle() == null || source.getLiveCodeStyle() <= 0 ? 1 : source.getLiveCodeStyle());
        target.setSkipVerify(source.getSkipVerify() == null ? 1 : source.getSkipVerify());
        target.setStateTemplate(StringUtils.hasText(source.getStateTemplate())
                ? source.getStateTemplate().trim()
                : "seedcrm:{scene}:{strategy}:{codeName}");
        target.setMarkSource(trimToNull(source.getMarkSource()));
        target.setEnabled(source.getEnabled() == null ? 1 : source.getEnabled());
        if (!StringUtils.hasText(target.getAuthStatus())) {
            target.setAuthStatus(MODE_MOCK.equals(target.getExecutionMode()) ? "MOCK" : "UNAUTHORIZED");
        }
    }

    private WecomAppConfig defaultConfig() {
        WecomAppConfig config = new WecomAppConfig();
        config.setAppCode(DEFAULT_APP_CODE);
        config.setAuthMode(AUTH_MODE_SELF_BUILT);
        config.setExecutionMode(MODE_MOCK);
        config.setLiveCodeType(2);
        config.setLiveCodeScene(2);
        config.setLiveCodeStyle(1);
        config.setSkipVerify(1);
        config.setEnabled(1);
        config.setStateTemplate("seedcrm:{scene}:{strategy}:{codeName}");
        config.setLastTokenStatus("UNCONFIGURED");
        config.setLastTokenMessage("尚未完成企业微信应用配置");
        config.setAuthStatus("UNAUTHORIZED");
        return config;
    }

    private String resolveServiceProviderAccessToken(WecomAppConfig working) {
        if (!StringUtils.hasText(working.getSuiteId()) || !StringUtils.hasText(working.getSuiteSecret())) {
            throw new BusinessException("服务商模式必须配置 suiteId 和 suiteSecret");
        }
        if (!StringUtils.hasText(working.getAuthCorpId()) && StringUtils.hasText(working.getCorpId())) {
            working.setAuthCorpId(working.getCorpId().trim());
        }
        String suiteAccessToken = resolveSuiteAccessToken(working);
        exchangePermanentCodeIfNecessary(working, suiteAccessToken);
        if (!StringUtils.hasText(working.getAuthCorpId())) {
            throw new BusinessException("服务商模式必须先拿到授权企业 corpId");
        }
        if (!StringUtils.hasText(working.getPermanentCode())) {
            throw new BusinessException("服务商模式必须先完成 permanent_code 换取");
        }
        JsonNode response = RestClient.create()
                .post()
                .uri("https://qyapi.weixin.qq.com/cgi-bin/service/get_corp_token?suite_access_token={suiteAccessToken}",
                        suiteAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "auth_corpid", working.getAuthCorpId().trim(),
                        "permanent_code", working.getPermanentCode().trim()))
                .retrieve()
                .body(JsonNode.class);
        assertWecomSuccess(response, "获取企业授权 access_token 失败");
        String accessToken = extractText(response, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("企业授权 access_token 返回为空");
        }
        int expiresIn = response.path("expires_in").asInt(7200);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
        working.setCorpAccessToken(accessToken.trim());
        working.setAccessToken(accessToken.trim());
        working.setCorpAccessTokenExpiresAt(expiresAt);
        working.setTokenExpiresAt(expiresAt);
        return accessToken.trim();
    }

    private String resolveSuiteAccessToken(WecomAppConfig working) {
        if (StringUtils.hasText(working.getSuiteAccessToken())
                && working.getSuiteAccessTokenExpiresAt() != null
                && working.getSuiteAccessTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return working.getSuiteAccessToken().trim();
        }
        if (!StringUtils.hasText(working.getSuiteTicket())) {
            throw new BusinessException("服务商模式必须先收到 suite_ticket 回调");
        }
        JsonNode response = RestClient.create()
                .post()
                .uri("https://qyapi.weixin.qq.com/cgi-bin/service/get_suite_token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "suite_id", working.getSuiteId().trim(),
                        "suite_secret", working.getSuiteSecret().trim(),
                        "suite_ticket", working.getSuiteTicket().trim()))
                .retrieve()
                .body(JsonNode.class);
        assertWecomSuccess(response, "获取 suite_access_token 失败");
        String suiteAccessToken = extractText(response, "suite_access_token");
        if (!StringUtils.hasText(suiteAccessToken)) {
            throw new BusinessException("suite_access_token 返回为空");
        }
        int expiresIn = response.path("expires_in").asInt(7200);
        working.setSuiteAccessToken(suiteAccessToken.trim());
        working.setSuiteAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        return suiteAccessToken.trim();
    }

    private void exchangePermanentCodeIfNecessary(WecomAppConfig working, String suiteAccessToken) {
        if (StringUtils.hasText(working.getPermanentCode())) {
            return;
        }
        if (!StringUtils.hasText(working.getAuthCode())) {
            throw new BusinessException("服务商模式必须先收到 auth_code 回调");
        }
        JsonNode response = RestClient.create()
                .post()
                .uri("https://qyapi.weixin.qq.com/cgi-bin/service/get_permanent_code?suite_access_token={suiteAccessToken}",
                        suiteAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "suite_id", working.getSuiteId().trim(),
                        "auth_code", working.getAuthCode().trim()))
                .retrieve()
                .body(JsonNode.class);
        assertWecomSuccess(response, "换取 permanent_code 失败");
        String permanentCode = extractText(response, "permanent_code");
        if (!StringUtils.hasText(permanentCode)) {
            throw new BusinessException("permanent_code 返回为空");
        }
        String authCorpId = extractText(response, "auth_corp_info.corpid");
        working.setPermanentCode(permanentCode.trim());
        if (StringUtils.hasText(authCorpId)) {
            working.setAuthCorpId(authCorpId.trim());
            if (!StringUtils.hasText(working.getCorpId())) {
                working.setCorpId(authCorpId.trim());
            }
        }
    }

    private String processServiceProviderCallback(WecomAppConfig config,
                                                  String infoType,
                                                  String suiteTicket,
                                                  String authCode,
                                                  String authCorpId,
                                                  LocalDateTime now) {
        if (config == null || !AUTH_MODE_SERVICE_PROVIDER.equals(normalizeAuthMode(config.getAuthMode()))) {
            return null;
        }
        String normalizedInfoType = normalize(infoType);
        if ("SUITE_TICKET".equals(normalizedInfoType)) {
            if (StringUtils.hasText(suiteTicket)) {
                config.setSuiteTicket(suiteTicket.trim());
                config.setSuiteTicketAt(now);
                if (!StringUtils.hasText(config.getAuthStatus()) || "UNAUTHORIZED".equals(config.getAuthStatus())) {
                    config.setAuthStatus("SUITE_TICKET_READY");
                }
            }
            return "已接收 suite_ticket";
        }
        if ("CANCEL_AUTH".equals(normalizedInfoType)) {
            config.setAuthStatus("CANCELLED");
            config.setCorpAccessToken(null);
            config.setAccessToken(null);
            config.setCorpAccessTokenExpiresAt(null);
            config.setTokenExpiresAt(null);
            return "已接收取消授权通知";
        }
        if ("CREATE_AUTH".equals(normalizedInfoType)
                || "CHANGE_AUTH".equals(normalizedInfoType)
                || "RESET_PERMANENT_CODE".equals(normalizedInfoType)) {
            if (StringUtils.hasText(authCorpId)) {
                config.setAuthCorpId(authCorpId.trim());
                if (!StringUtils.hasText(config.getCorpId())) {
                    config.setCorpId(authCorpId.trim());
                }
            }
            if (StringUtils.hasText(authCode)) {
                config.setAuthCode(authCode.trim());
                config.setLastAuthCodeAt(now);
                String suiteAccessToken = resolveSuiteAccessToken(config);
                exchangePermanentCodeIfNecessary(config, suiteAccessToken);
                config.setAuthStatus("AUTHORIZED");
                return "已接收授权码并完成 permanent_code 换取";
            }
            return "已接收授权变更通知";
        }
        return null;
    }

    private void assertWecomSuccess(JsonNode response, String defaultMessage) {
        int errorCode = response == null ? -1 : response.path("errcode").asInt(0);
        if (errorCode != 0) {
            String message = response == null ? defaultMessage : response.path("errmsg").asText(defaultMessage);
            throw new BusinessException(message);
        }
    }

    private void updateTokenStateIfPersisted(WecomAppConfig config) {
        if (config.getId() == null || config.getId() <= 0) {
            return;
        }
        WecomAppConfig existing = wecomAppConfigMapper.selectById(config.getId());
        if (existing == null) {
            return;
        }
        existing.setLastTokenStatus(config.getLastTokenStatus());
        existing.setLastTokenMessage(trimMessage(config.getLastTokenMessage()));
        existing.setLastTokenCheckedAt(config.getLastTokenCheckedAt());
        existing.setAuthStatus(config.getAuthStatus());
        existing.setAccessToken(resolveSensitiveValue(config.getAccessToken(), existing.getAccessToken()));
        existing.setSuiteAccessToken(resolveSensitiveValue(config.getSuiteAccessToken(), existing.getSuiteAccessToken()));
        existing.setCorpAccessToken(resolveSensitiveValue(config.getCorpAccessToken(), existing.getCorpAccessToken()));
        existing.setSuiteTicket(resolveSensitiveValue(config.getSuiteTicket(), existing.getSuiteTicket()));
        existing.setPermanentCode(resolveSensitiveValue(config.getPermanentCode(), existing.getPermanentCode()));
        existing.setAuthCorpId(trimToNull(config.getAuthCorpId()));
        existing.setSuiteTicketAt(config.getSuiteTicketAt());
        existing.setTokenExpiresAt(config.getTokenExpiresAt());
        existing.setSuiteAccessTokenExpiresAt(config.getSuiteAccessTokenExpiresAt());
        existing.setCorpAccessTokenExpiresAt(config.getCorpAccessTokenExpiresAt());
        existing.setUpdatedAt(LocalDateTime.now());
        wecomAppConfigMapper.updateById(existing);
    }

    private WecomAppConfig maskConfig(WecomAppConfig config) {
        if (config == null) {
            return null;
        }
        config.setAppSecretMasked(maskValue(config.getAppSecret()));
        config.setSuiteSecretMasked(maskValue(config.getSuiteSecret()));
        config.setAuthCodeMasked(maskValue(config.getAuthCode()));
        config.setSuiteTicketMasked(maskValue(config.getSuiteTicket()));
        config.setPermanentCodeMasked(maskValue(config.getPermanentCode()));
        config.setAccessTokenMasked(maskValue(config.getAccessToken()));
        config.setRefreshTokenMasked(maskValue(config.getRefreshToken()));
        config.setSuiteAccessTokenMasked(maskValue(config.getSuiteAccessToken()));
        config.setCorpAccessTokenMasked(maskValue(config.getCorpAccessToken()));
        config.setCallbackTokenMasked(maskValue(config.getCallbackToken()));
        config.setEncodingAesKeyMasked(maskValue(config.getEncodingAesKey()));
        return config;
    }

    private void applyRuleDefaults(WecomTouchRule rule) {
        if (rule == null) {
            return;
        }
        rule.setIsEnabled(rule.getIsEnabled() == null ? 1 : rule.getIsEnabled());
        if (!StringUtils.hasText(rule.getRuleName())) {
            rule.setRuleName(StringUtils.hasText(rule.getTriggerType()) ? rule.getTriggerType() : "未命名规则");
        }
    }

    private WecomLiveCodeConfig inflateLiveCodeConfig(WecomLiveCodeConfig config) {
        if (config == null) {
            return null;
        }
        config.setEmployeeNames(parseStringList(config.getEmployeeNamesJson()));
        config.setEmployeeAccounts(parseStringList(config.getEmployeeAccountsJson()));
        config.setStoreNames(parseStringList(config.getStoreNamesJson()));
        if (isLegacyMockContactLink(config.getShortLink()) && StringUtils.hasText(config.getContactWayId())) {
            config.setShortLink("/wecom/mock-contact/" + config.getContactWayId().trim());
        }
        return config;
    }

    private boolean isLegacyMockContactLink(String shortLink) {
        return StringUtils.hasText(shortLink)
                && shortLink.trim().toLowerCase(Locale.ROOT).startsWith("https://wecom.seedcrm.local/contact/");
    }

    private void applyLiveCodeConfig(WecomLiveCodeConfig target, WecomLiveCodeConfig source) {
        target.setCodeName(source.getCodeName().trim());
        target.setScene(trimToNull(source.getScene()));
        target.setStrategy(StringUtils.hasText(source.getStrategy()) ? source.getStrategy().trim() : DEFAULT_STRATEGY);
        target.setEmployeeNamesJson(writeStringList(source.getEmployeeNames()));
        target.setEmployeeAccountsJson(writeStringList(source.getEmployeeAccounts()));
        target.setStoreNamesJson(writeStringList(source.getStoreNames()));
        target.setRemark(trimToNull(source.getRemark()));
        target.setIsEnabled(source.getIsEnabled() == null ? 1 : source.getIsEnabled());
    }

    private List<String> parseStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList());
        } catch (Exception exception) {
            throw new BusinessException("活码员工列表序列化失败");
        }
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

    private String normalizeAuthMode(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return AUTH_MODE_SELF_BUILT;
        }
        return AUTH_MODE_SERVICE_PROVIDER.equals(normalized) ? AUTH_MODE_SERVICE_PROVIDER : AUTH_MODE_SELF_BUILT;
    }

    private String resolveSensitiveValue(String incoming, String existing) {
        return StringUtils.hasText(incoming) ? incoming.trim() : existing;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimMessage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) : trimmed;
    }

    private String trimPayload(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
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
            JsonNode node = parsePayload(payload);
            merged.put("body", node == null ? sanitizePayload(payload) : sanitizeValue(objectMapper.convertValue(node, new TypeReference<Object>() {
            })));
        }
        return merged.isEmpty() ? null : toCompactJson(merged);
    }

    private String tryDecryptCallbackPayload(WecomAppConfig config,
                                             Map<String, String> parameters,
                                             String requestMethod,
                                             String payload,
                                             String signatureStatus) {
        if (!"POST".equalsIgnoreCase(requestMethod)
                || config == null
                || !StringUtils.hasText(payload)
                || !StringUtils.hasText(config.getEncodingAesKey())
                || "INVALID".equals(signatureStatus)) {
            return null;
        }
        String encrypt = extractXmlValue(payload, "Encrypt");
        if (!StringUtils.hasText(encrypt)) {
            return null;
        }
        try {
            return decryptWecomMessage(config, encrypt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toCompactJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
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

    private String extractXmlValue(String payload, String tagName) {
        if (!StringUtils.hasText(payload) || !StringUtils.hasText(tagName)) {
            return null;
        }
        Pattern pattern = Pattern.compile("<" + tagName + "><!\\[CDATA\\[(.*?)]]></" + tagName + ">|<" + tagName + ">(.*?)</" + tagName + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        if (!StringUtils.hasText(value)) {
            value = matcher.group(2);
        }
        return trimToNull(value);
    }

    private String resolveAuthStatus(String currentStatus,
                                     String errorMessage,
                                     String authCode,
                                     String accessToken,
                                     String externalUserId) {
        if (StringUtils.hasText(errorMessage)) {
            return "FAILED";
        }
        if ("AUTHORIZED".equals(currentStatus) || "CONNECTED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            return currentStatus;
        }
        if (StringUtils.hasText(accessToken) || StringUtils.hasText(externalUserId)) {
            return "AUTHORIZED";
        }
        if (StringUtils.hasText(authCode)) {
            return "AUTH_CODE_RECEIVED";
        }
        return StringUtils.hasText(currentStatus) ? currentStatus : "RECEIVED";
    }

    private String resolveSignatureStatus(WecomAppConfig config,
                                          String requestMethod,
                                          Map<String, String> parameters,
                                          String payload) {
        if (!"POST".equalsIgnoreCase(requestMethod)) {
            return config != null && config.getSkipVerify() != null && config.getSkipVerify() == 0
                    ? "PENDING_VERIFY"
                    : "SKIPPED";
        }
        if (config == null || config.getSkipVerify() == null || config.getSkipVerify() == 1) {
            return "SKIPPED";
        }
        String encrypt = extractXmlValue(payload, "Encrypt");
        String msgSignature = parameters.get("msg_signature");
        String timestamp = parameters.get("timestamp");
        String nonce = parameters.get("nonce");
        if (!StringUtils.hasText(encrypt) || !StringUtils.hasText(msgSignature)
                || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            return "INVALID";
        }
        try {
            String expected = calculateSha1(config.getCallbackToken(), timestamp, nonce, encrypt);
            return expected.equals(msgSignature) ? "VERIFIED" : "INVALID";
        } catch (Exception ignored) {
            return "INVALID";
        }
    }

    private boolean isTrustedCallback(WecomAppConfig config, String signatureStatus) {
        if (config == null || !MODE_LIVE.equals(config.getExecutionMode())) {
            return true;
        }
        if (config.getSkipVerify() != null && config.getSkipVerify() == 1) {
            return isLocalUrl(config.getCallbackUrl());
        }
        return "VERIFIED".equals(signatureStatus) || "PENDING_VERIFY".equals(signatureStatus);
    }

    private String verifyAndDecryptEchoStr(WecomAppConfig config,
                                           Map<String, String> parameters,
                                           String echostr) throws Exception {
        if (config == null || !StringUtils.hasText(config.getCallbackToken())
                || !StringUtils.hasText(config.getEncodingAesKey())) {
            throw new IllegalStateException("缺少回调验签配置");
        }
        String msgSignature = parameters.get("msg_signature");
        String timestamp = parameters.get("timestamp");
        String nonce = parameters.get("nonce");
        if (!StringUtils.hasText(msgSignature) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            throw new IllegalStateException("缺少企业微信回调验签参数");
        }
        String expected = calculateSha1(config.getCallbackToken(), timestamp, nonce, echostr);
        if (!expected.equals(msgSignature)) {
            throw new IllegalStateException("msg_signature 校验失败");
        }
        return decryptWecomMessage(config, echostr);
    }

    private String calculateSha1(String token, String timestamp, String nonce, String payload) throws Exception {
        String[] values = new String[]{
                token == null ? "" : token.trim(),
                timestamp == null ? "" : timestamp.trim(),
                nonce == null ? "" : nonce.trim(),
                payload == null ? "" : payload.trim()
        };
        Arrays.sort(values);
        String source = String.join("", values);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hashed = digest.digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        for (byte value : hashed) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String decryptWecomMessage(WecomAppConfig config, String encrypted) throws Exception {
        byte[] aesKey = Base64.getDecoder().decode(config.getEncodingAesKey().trim() + "=");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
        byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted.trim()));
        byte[] bytes = removePkcs7Padding(original);
        int messageLength = ByteBuffer.wrap(bytes, 16, 4).getInt();
        if (messageLength <= 0 || bytes.length < 20 + messageLength) {
            throw new IllegalStateException("企业微信回调消息解密长度异常");
        }
        return new String(bytes, 20, messageLength, StandardCharsets.UTF_8);
    }

    private byte[] removePkcs7Padding(byte[] decrypted) {
        if (decrypted == null || decrypted.length == 0) {
            return new byte[0];
        }
        int pad = decrypted[decrypted.length - 1] & 0xFF;
        if (pad <= 0 || pad > 32) {
            return decrypted;
        }
        return Arrays.copyOf(decrypted, decrypted.length - pad);
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
        sanitized = sanitized.replaceAll("(?i)(<AuthCode><!\\[CDATA\\[)(.*?)(]]></AuthCode>)", "$1****$3");
        sanitized = sanitized.replaceAll("(?i)(<SuiteTicket><!\\[CDATA\\[)(.*?)(]]></SuiteTicket>)", "$1****$3");
        sanitized = sanitized.replaceAll("(?i)(<PermanentCode><!\\[CDATA\\[)(.*?)(]]></PermanentCode>)", "$1****$3");
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
                || normalizedKey.equals("app_secret")
                || normalizedKey.equals("suite_secret")
                || normalizedKey.equals("suite_ticket")
                || normalizedKey.equals("permanent_code")
                || normalizedKey.equals("corpsecret")
                || normalizedKey.equals("encodingaeskey")
                || normalizedKey.equals("encoding_aes_key");
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

    private record AccessTokenCacheEntry(String accessToken, LocalDateTime expiresAt) {
    }
}
