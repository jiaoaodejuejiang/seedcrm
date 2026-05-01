package com.seedcrm.crm.systemconfig.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import java.util.List;

public interface SystemConfigService {

    List<SystemConfigDtos.ConfigResponse> listConfigs(String prefix);

    SystemConfigDtos.ConfigResponse saveConfig(SystemConfigDtos.SaveConfigRequest request, PermissionRequestContext context);

    SystemConfigDtos.DomainSettingsResponse getDomainSettings();

    SystemConfigDtos.DomainSettingsResponse saveDomainSettings(SystemConfigDtos.SaveDomainSettingsRequest request,
                                                               PermissionRequestContext context);

    boolean getBoolean(String configKey, boolean defaultValue);

    String getString(String configKey, String defaultValue);
}
