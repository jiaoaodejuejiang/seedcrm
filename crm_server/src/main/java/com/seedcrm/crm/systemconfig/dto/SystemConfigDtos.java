package com.seedcrm.crm.systemconfig.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public final class SystemConfigDtos {

    private SystemConfigDtos() {
    }

    @Data
    @Schema(name = "SystemConfigResponse", description = "系统配置项响应，敏感值会脱敏")
    public static class ConfigResponse {
        @Schema(description = "配置 ID")
        private Long id;
        @Schema(description = "配置 key", example = "system.domain.apiBaseUrl")
        private String configKey;
        @Schema(description = "配置值；敏感配置会返回 ******")
        private String configValue;
        @Schema(description = "值类型", example = "STRING")
        private String valueType;
        @Schema(description = "作用域类型", example = "GLOBAL")
        private String scopeType;
        @Schema(description = "作用域 ID", example = "GLOBAL")
        private String scopeId;
        @Schema(description = "启用状态：1 启用，0 停用", example = "1")
        private Integer enabled;
        @Schema(description = "配置说明")
        private String description;
        @Schema(description = "是否敏感配置")
        private Boolean sensitive;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Schema(name = "SaveSystemConfigRequest", description = "保存受控系统配置项请求")
    public static class SaveConfigRequest {
        @Schema(description = "配置 key，必须在后端白名单中登记", example = "workflow.service_order.enabled")
        private String configKey;
        @Schema(description = "配置值", example = "true")
        private String configValue;
        @Schema(description = "值类型", example = "BOOLEAN")
        private String valueType;
        @Schema(description = "作用域类型", example = "GLOBAL")
        private String scopeType;
        @Schema(description = "作用域 ID", example = "GLOBAL")
        private String scopeId;
        @Schema(description = "启用状态", example = "1")
        private Integer enabled;
        @Schema(description = "配置说明")
        private String description;
        @Schema(description = "变更摘要，写入审计日志")
        private String summary;
    }

    @Data
    @Schema(name = "SystemConfigPreviewResponse", description = "配置变更预览结果")
    public static class ConfigPreviewResponse {
        private String configKey;
        private String scopeType;
        private String scopeId;
        private String beforeValue;
        private String afterValue;
        private String valueType;
        private Integer enabled;
        private Boolean sensitive;
        private Boolean changed;
        private String changeType;
        private String riskLevel;
        private List<String> impactModules = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private Boolean validationPassed;
        private String validationMessage;
    }

    @Data
    @Schema(name = "SystemConfigChangeLogResponse", description = "配置变更审计日志")
    public static class ChangeLogResponse {
        private Long id;
        private String configKey;
        private String scopeType;
        private String scopeId;
        private String beforeValue;
        private String afterValue;
        private Boolean sensitive;
        private String changeType;
        private String riskLevel;
        private List<String> impactModules = new ArrayList<>();
        private String actorRoleCode;
        private Long actorUserId;
        private String summary;
        private LocalDateTime createTime;
    }

    @Data
    @Schema(name = "SystemConfigDraftResponse", description = "待发布的系统配置草稿")
    public static class DraftResponse {
        private Long id;
        private String draftNo;
        private String status;
        private String sourceType;
        private Long sourceChangeLogId;
        private String riskLevel;
        private List<String> impactModules = new ArrayList<>();
        private String createdByRoleCode;
        private Long createdByUserId;
        private String summary;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime publishedAt;
        private LocalDateTime discardedAt;
        private String lastDryRunStatus;
        private LocalDateTime lastDryRunAt;
        private String lastDryRunByRoleCode;
        private Long lastDryRunByUserId;
        private List<DraftItemResponse> items = new ArrayList<>();
    }

    @Data
    @Schema(name = "SystemConfigDraftItemResponse", description = "系统配置草稿明细差异")
    public static class DraftItemResponse {
        private Long id;
        private String draftNo;
        private String configKey;
        private String scopeType;
        private String scopeId;
        private String valueType;
        private String beforeValue;
        private String afterValue;
        private String baseCurrentValueHash;
        private Integer enabled;
        private String description;
        private String changeType;
        private Boolean sensitive;
        private String validationStatus;
        private String validationMessage;
    }

    @Data
    @Schema(name = "SystemConfigCapabilityResponse", description = "受控配置能力元数据")
    public static class CapabilityResponse {
        private Long id;
        private String capabilityCode;
        private String configKeyPattern;
        private String ownerModule;
        private String valueType;
        private List<String> scopeTypes = new ArrayList<>();
        private String riskLevel;
        private Boolean sensitive;
        private String validatorCode;
        private String runtimeReloadStrategy;
        private Integer enabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @Schema(name = "SystemConfigValidationResponse", description = "配置草稿校验结果")
    public static class ValidationResponse {
        private String draftNo;
        private Boolean valid;
        private String riskLevel;
        private List<String> impactModules = new ArrayList<>();
        private String summary;
        private List<ValidationItemResponse> items = new ArrayList<>();
    }

    @Data
    @Schema(name = "SystemConfigValidationItemResponse", description = "配置草稿校验明细")
    public static class ValidationItemResponse {
        private String configKey;
        private String status;
        private String moduleCode;
        private String capabilityCode;
        private String validatorCode;
        private String message;
        private String suggestion;
    }

    @Data
    @Schema(name = "SystemConfigDryRunResponse", description = "配置草稿 dry-run 结果")
    public static class DryRunResponse {
        private String draftNo;
        private Boolean runnable;
        private String summary;
        private List<ValidationItemResponse> items = new ArrayList<>();
        private List<String> runtimeEvents = new ArrayList<>();
    }

    @Data
    @Schema(name = "SystemConfigPublishRecordResponse", description = "配置发布记录")
    public static class PublishRecordResponse {
        private Long id;
        private String publishNo;
        private String draftNo;
        private String status;
        private String riskLevel;
        private List<String> impactModules = new ArrayList<>();
        private String beforeHash;
        private String afterHash;
        private String beforeSnapshotMaskedJson;
        private String afterSnapshotMaskedJson;
        private String validationResultJson;
        private String failureReason;
        private String publishedByRoleCode;
        private Long publishedByUserId;
        private LocalDateTime publishedAt;
        private List<RuntimeEventResponse> events = new ArrayList<>();
    }

    @Data
    @Schema(name = "SystemConfigRuntimeEventResponse", description = "配置运行时刷新事件")
    public static class RuntimeEventResponse {
        private Long id;
        private String publishNo;
        private String moduleCode;
        private String eventType;
        private String status;
        private String payloadJson;
        private String errorMessage;
        private Integer retryCount;
        private Integer maxRetryCount;
        private LocalDateTime nextRetryAt;
        private String lockedBy;
        private LocalDateTime lockedAt;
        private LocalDateTime lastAttemptAt;
        private LocalDateTime createTime;
        private LocalDateTime handledAt;
    }

    @Data
    @Schema(name = "SystemConfigRuntimeOverviewResponse", description = "配置发布运行态概览")
    public static class RuntimeOverviewResponse {
        private Long capabilityCount;
        private Long draftCount;
        private Long highRiskDraftCount;
        private Long publishSuccessCount;
        private Long publishFailedCount;
        private Long runtimeEventPendingCount;
        private Long runtimeEventRetryingCount;
        private Long runtimeEventSuccessCount;
        private Long runtimeEventFailedCount;
        private Long runtimeEventTerminatedCount;
        private LocalDateTime latestRuntimeHandledAt;
        private LocalDateTime lastPublishedAt;
    }

    @Data
    @Schema(name = "DomainSettingsResponse", description = "系统基础域名和 API 域名派生结果")
    public static class DomainSettingsResponse {
        @Schema(description = "系统基础域名，面向后台页面和对外展示链接", example = "https://crm.seedcrm.com")
        private String systemBaseUrl;
        @Schema(description = "API 基础域名，面向回调、Open API、Swagger 和联调接口", example = "https://api.seedcrm.com")
        private String apiBaseUrl;
        @Schema(description = "分销已支付订单入站地址", example = "https://api.seedcrm.com/open/distribution/events")
        private String eventIngestUrl;
        @Schema(description = "Swagger UI 地址", example = "https://api.seedcrm.com/swagger-ui.html")
        private String swaggerUiUrl;
        @Schema(description = "分销 OpenAPI JSON 地址", example = "https://api.seedcrm.com/v3/api-docs/distribution-open-api")
        private String openApiDocsUrl;
    }

    @Data
    @Schema(name = "SaveDomainSettingsRequest", description = "保存系统基础域名和 API 域名")
    public static class SaveDomainSettingsRequest {
        @Schema(description = "系统基础域名", example = "https://crm.seedcrm.com")
        private String systemBaseUrl;
        @Schema(description = "API 基础域名", example = "https://api.seedcrm.com")
        private String apiBaseUrl;
    }
}
