package com.seedcrm.crm.systemconfig.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import java.util.List;

public interface SystemConfigService {

    List<SystemConfigDtos.ConfigResponse> listConfigs(String prefix);

    default List<SystemConfigDtos.ChangeLogResponse> listChangeLogs(String prefix, String configKey, Integer limit) {
        return List.of();
    }

    default List<SystemConfigDtos.DraftResponse> listDrafts(String status, Integer limit) {
        return List.of();
    }

    default SystemConfigDtos.DraftResponse getDraft(String draftNo) {
        throw new UnsupportedOperationException("system config draft is not supported");
    }

    default SystemConfigDtos.ConfigPreviewResponse previewConfig(SystemConfigDtos.SaveConfigRequest request) {
        throw new UnsupportedOperationException("system config preview is not supported");
    }

    default SystemConfigDtos.DraftResponse createDraft(SystemConfigDtos.SaveConfigRequest request,
                                                       PermissionRequestContext context) {
        throw new UnsupportedOperationException("system config draft is not supported");
    }

    default SystemConfigDtos.DraftResponse publishDraft(String draftNo, PermissionRequestContext context) {
        throw new UnsupportedOperationException("system config draft publish is not supported");
    }

    default SystemConfigDtos.DraftResponse discardDraft(String draftNo, PermissionRequestContext context) {
        throw new UnsupportedOperationException("system config draft discard is not supported");
    }

    default SystemConfigDtos.ConfigPreviewResponse rollbackPreview(Long changeLogId) {
        throw new UnsupportedOperationException("system config rollback preview is not supported");
    }

    default SystemConfigDtos.DraftResponse createRollbackDraft(Long changeLogId, PermissionRequestContext context) {
        throw new UnsupportedOperationException("system config rollback draft is not supported");
    }

    SystemConfigDtos.ConfigResponse saveConfig(SystemConfigDtos.SaveConfigRequest request, PermissionRequestContext context);

    SystemConfigDtos.DomainSettingsResponse getDomainSettings();

    SystemConfigDtos.DomainSettingsResponse saveDomainSettings(SystemConfigDtos.SaveDomainSettingsRequest request,
                                                               PermissionRequestContext context);

    boolean getBoolean(String configKey, boolean defaultValue);

    String getString(String configKey, String defaultValue);
}
