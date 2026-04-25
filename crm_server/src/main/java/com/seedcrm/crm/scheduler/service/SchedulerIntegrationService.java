package com.seedcrm.crm.scheduler.service;

import com.seedcrm.crm.scheduler.entity.IntegrationCallbackConfig;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import java.util.Map;
import java.util.List;

public interface SchedulerIntegrationService {

    List<IntegrationProviderConfig> listProviders();

    IntegrationProviderConfig saveProvider(IntegrationProviderConfig config);

    IntegrationProviderConfig testProvider(IntegrationProviderConfig config);

    List<IntegrationCallbackConfig> listCallbacks();

    IntegrationCallbackConfig saveCallback(IntegrationCallbackConfig config);

    List<IntegrationCallbackEventLog> listCallbackLogs(String providerCode);

    IntegrationProviderConfig receiveProviderCallback(String providerCode,
                                                      String callbackName,
                                                      String callbackPath,
                                                      String requestMethod,
                                                      Map<String, String> parameters,
                                                      String payload);

    IntegrationProviderConfig getEnabledProviderOrNull(Long providerId);

    String resolveProviderAccessToken(IntegrationProviderConfig config);

    void markSyncResult(Long providerId, boolean success, String message);
}
