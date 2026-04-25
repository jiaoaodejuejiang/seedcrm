package com.seedcrm.crm.clue.service;

import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;

public interface DouyinClueSyncService {

    int syncIncremental();

    int syncIncremental(IntegrationProviderConfig providerConfig);
}
