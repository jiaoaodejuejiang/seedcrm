package com.seedcrm.crm.systemflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class SystemFlowDtos {

    private SystemFlowDtos() {
    }

    @Data
    public static class DefinitionResponse {
        private Long id;
        private String flowCode;
        private String flowName;
        private String moduleCode;
        private String businessObject;
        private String description;
        private Integer enabled;
        private Long currentVersionId;
        private Integer currentVersionNo;
        private String currentVersionStatus;
        private Integer nodeCount;
        private Integer triggerCount;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class DetailResponse {
        private DefinitionResponse definition;
        private VersionResponse version;
        private List<NodeResponse> nodes = new ArrayList<>();
        private List<TransitionResponse> transitions = new ArrayList<>();
        private List<TriggerResponse> triggers = new ArrayList<>();
    }

    @Data
    public static class VersionResponse {
        private Long id;
        private Long definitionId;
        private Integer versionNo;
        private String status;
        private String changeSummary;
        private LocalDateTime publishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class NodeResponse {
        private Long id;
        private String domainCode;
        private String nodeCode;
        private String nodeName;
        private String nodeType;
        private String businessState;
        private String roleCode;
        private Integer sortOrder;
        private String description;
    }

    @Data
    public static class TransitionResponse {
        private Long id;
        private String fromNodeCode;
        private String toNodeCode;
        private String actionCode;
        private String actionName;
        private String guardRule;
        private Integer sortOrder;
    }

    @Data
    public static class TriggerResponse {
        private Long id;
        private String nodeCode;
        private String triggerType;
        private String triggerName;
        private String targetCode;
        private String executionMode;
        private Integer enabled;
        private Integer sortOrder;
        private String configJson;
    }

    @Data
    public static class SaveDraftRequest {
        private String flowCode;
        private String flowName;
        private String moduleCode;
        private String businessObject;
        private String description;
        private String changeSummary;
        private List<NodeResponse> nodes = new ArrayList<>();
        private List<TransitionResponse> transitions = new ArrayList<>();
        private List<TriggerResponse> triggers = new ArrayList<>();
    }

    @Data
    public static class PublishRequest {
        private String flowCode;
        private Long versionId;
        private String summary;
    }

    @Data
    public static class DisableRequest {
        private String flowCode;
        private String reason;
    }

    @Data
    public static class SimulateRequest {
        private String flowCode;
        private String currentNodeCode;
        private String actionCode;
        private String roleCode;
        private String businessStatus;
    }

    @Data
    public static class SimulateResponse {
        private String flowCode;
        private Integer versionNo;
        private String currentNodeCode;
        private String actionCode;
        private String nextNodeCode;
        private boolean allowed;
        private String message;
        private List<String> path = new ArrayList<>();
        private List<TriggerResponse> matchedTriggers = new ArrayList<>();
    }

    @Data
    public static class DiffPreviewResponse {
        private String flowCode;
        private Integer baseVersionNo;
        private boolean valid;
        private String validationMessage;
        private List<DiffItemResponse> items = new ArrayList<>();
    }

    @Data
    public static class DiffItemResponse {
        private String domain;
        private String changeType;
        private String objectCode;
        private String title;
        private String beforeValue;
        private String afterValue;
        private String impact;
    }

    @Data
    public static class ValidationReportResponse {
        private String flowCode;
        private Integer versionNo;
        private String versionStatus;
        private boolean valid;
        private LocalDateTime checkedAt;
        private List<ValidationItemResponse> items = new ArrayList<>();
    }

    @Data
    public static class ValidationItemResponse {
        private String domain;
        private String checkCode;
        private String severity;
        private boolean passed;
        private String message;
        private String suggestion;
    }

    @Data
    public static class TriggerLinkageReportResponse {
        private String flowCode;
        private Integer versionNo;
        private String versionStatus;
        private boolean healthy;
        private LocalDateTime checkedAt;
        private List<TriggerLinkageItemResponse> items = new ArrayList<>();
    }

    @Data
    public static class TriggerLinkageItemResponse {
        private String nodeCode;
        private String triggerName;
        private String triggerType;
        private String targetCode;
        private String executionMode;
        private String linkedModule;
        private String linkedResource;
        private String status;
        private String message;
        private String suggestion;
    }

    @Data
    public static class AuditLogResponse {
        private Long id;
        private String flowCode;
        private Integer versionNo;
        private String actionType;
        private String actorRoleCode;
        private Long actorUserId;
        private String summary;
        private LocalDateTime createdAt;
    }

    @Data
    public static class StartInstanceRequest {
        private String flowCode;
        private String businessObject;
        private Long businessId;
        private String title;
        private String startNodeCode;
        private String remark;
    }

    @Data
    public static class TransitionInstanceRequest {
        private Long instanceId;
        private String actionCode;
        private String remark;
    }

    @Data
    public static class InstanceResponse {
        private Long id;
        private String flowCode;
        private Long versionId;
        private Integer versionNo;
        private String businessObject;
        private Long businessId;
        private String currentNodeCode;
        private String currentNodeName;
        private String status;
        private String title;
        private String createdByRoleCode;
        private Long createdByUserId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class TaskResponse {
        private Long id;
        private Long instanceId;
        private String flowCode;
        private String nodeCode;
        private String nodeName;
        private String taskName;
        private String roleCode;
        private Long assigneeUserId;
        private String status;
        private LocalDateTime openedAt;
        private LocalDateTime completedAt;
        private String remark;
    }

    @Data
    public static class EventLogResponse {
        private Long id;
        private Long instanceId;
        private String flowCode;
        private Integer versionNo;
        private String actionCode;
        private String fromNodeCode;
        private String toNodeCode;
        private String actorRoleCode;
        private Long actorUserId;
        private String summary;
        private LocalDateTime eventTime;
    }

    @Data
    public static class RuntimeOverviewResponse {
        private String flowCode;
        private Integer runningCount;
        private Integer openTaskCount;
        private List<InstanceResponse> recentInstances = new ArrayList<>();
        private List<TaskResponse> openTasks = new ArrayList<>();
        private List<EventLogResponse> recentEvents = new ArrayList<>();
    }
}
