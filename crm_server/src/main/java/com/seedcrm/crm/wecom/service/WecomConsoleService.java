package com.seedcrm.crm.wecom.service;

import com.seedcrm.crm.wecom.entity.WecomAppConfig;
import com.seedcrm.crm.wecom.entity.WecomLiveCodeConfig;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.entity.WecomTouchRule;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import java.util.Map;
import java.util.List;

public interface WecomConsoleService {

    WecomAppConfig getConfig();

    WecomAppConfig saveConfig(WecomAppConfig config);

    WecomAppConfig testConfig(WecomAppConfig config);

    String resolveAccessToken(WecomAppConfig config);

    List<IntegrationCallbackEventLog> listCallbackLogs(String appCode);

    String receiveCallback(String appCode,
                           String callbackPath,
                           String requestMethod,
                           Map<String, String> parameters,
                           String payload);

    List<WecomTouchRule> listRules();

    WecomTouchRule saveRule(WecomTouchRule rule);

    WecomTouchRule toggleRule(Long ruleId);

    List<WecomTouchLog> listLogs();

    List<WecomLiveCodeConfig> listLiveCodeConfigs();

    WecomLiveCodeConfig saveLiveCodeConfig(WecomLiveCodeConfig config);

    WecomLiveCodeConfig publishLiveCodeConfig(Long configId, List<String> storeNames);
}
