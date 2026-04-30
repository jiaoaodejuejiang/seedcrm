package com.seedcrm.crm.systemflow.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemflow.dto.SystemFlowDtos;
import java.util.List;

public interface SystemFlowService {

    List<SystemFlowDtos.DefinitionResponse> listDefinitions();

    List<SystemFlowDtos.VersionResponse> listVersions(String flowCode);

    SystemFlowDtos.DetailResponse detail(String flowCode, Long versionId);

    SystemFlowDtos.DetailResponse saveDraft(SystemFlowDtos.SaveDraftRequest request, PermissionRequestContext context);

    SystemFlowDtos.DetailResponse publish(SystemFlowDtos.PublishRequest request, PermissionRequestContext context);

    SystemFlowDtos.DefinitionResponse disable(SystemFlowDtos.DisableRequest request, PermissionRequestContext context);

    SystemFlowDtos.SimulateResponse simulate(SystemFlowDtos.SimulateRequest request);

    SystemFlowDtos.DiffPreviewResponse previewDiff(SystemFlowDtos.SaveDraftRequest request);

    SystemFlowDtos.ValidationReportResponse validationReport(String flowCode, Long versionId);

    SystemFlowDtos.TriggerLinkageReportResponse triggerLinkageReport(String flowCode, Long versionId);

    List<SystemFlowDtos.AuditLogResponse> listAuditLogs(String flowCode);

    SystemFlowDtos.RuntimeOverviewResponse runtimeOverview(String flowCode);

    SystemFlowDtos.InstanceResponse startInstance(SystemFlowDtos.StartInstanceRequest request, PermissionRequestContext context);

    SystemFlowDtos.InstanceResponse transitionInstance(SystemFlowDtos.TransitionInstanceRequest request, PermissionRequestContext context);

    List<SystemFlowDtos.TaskResponse> listOpenTasks(String flowCode);

    List<SystemFlowDtos.EventLogResponse> listInstanceEvents(Long instanceId);
}
