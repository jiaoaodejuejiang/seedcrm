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

    default List<SystemConfigDtos.CapabilityResponse> listCapabilities() {
        return List.of();
    }

    default SystemConfigDtos.RuntimeOverviewResponse getRuntimeOverview() {
        throw new UnsupportedOperationException("system config runtime overview is not supported");
    }

    default SystemConfigDtos.ValidationResponse validateDraft(String draftNo) {
        throw new UnsupportedOperationException("system config draft validation is not supported");
    }

    default SystemConfigDtos.DryRunResponse dryRunDraft(String draftNo) {
        throw new UnsupportedOperationException("system config draft dry-run is not supported");
    }

    default SystemConfigDtos.DryRunResponse dryRunDraft(String draftNo, PermissionRequestContext context) {
        return dryRunDraft(draftNo);
    }

    default List<SystemConfigDtos.PublishRecordResponse> listPublishRecords(Integer limit) {
        return List.of();
    }

    default SystemConfigDtos.PublishRecordResponse getPublishRecord(String publishNo) {
        throw new UnsupportedOperationException("system config publish record is not supported");
    }

    default SystemConfigDtos.PublishRecordResponse refreshPublishRuntime(String publishNo,
                                                                         PermissionRequestContext context) {
        throw new UnsupportedOperationException("system config runtime refresh is not supported");
    }

    default SystemConfigDtos.PublishRecordResponse processPublishRuntimeEvents(String publishNo,
                                                                               PermissionRequestContext context) {
        throw new UnsupportedOperationException("system config runtime event processing is not supported");
    }

    default List<SystemConfigDtos.RuntimeEventResponse> processDueRuntimeEvents(Integer limit) {
        return List.of();
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

    default SystemConfigDtos.ConfigResponse saveLegacyConfig(SystemConfigDtos.SaveConfigRequest request,
                                                             PermissionRequestContext context) {
        return saveConfig(request, context);
    }

    SystemConfigDtos.ConfigResponse saveConfig(SystemConfigDtos.SaveConfigRequest request, PermissionRequestContext context);

    SystemConfigDtos.DomainSettingsResponse getDomainSettings();

    SystemConfigDtos.DomainSettingsResponse saveDomainSettings(SystemConfigDtos.SaveDomainSettingsRequest request,
                                                               PermissionRequestContext context);

    boolean getBoolean(String configKey, boolean defaultValue);

    String getString(String configKey, String defaultValue);
}
