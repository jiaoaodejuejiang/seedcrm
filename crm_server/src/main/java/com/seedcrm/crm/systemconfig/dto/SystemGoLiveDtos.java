package com.seedcrm.crm.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class SystemGoLiveDtos {

    private SystemGoLiveDtos() {
    }

    @Data
    @Schema(name = "SystemGoLiveSummaryResponse", description = "上线准备摘要")
    public static class SummaryResponse {
        private String environmentMode;
        private Boolean safeToClearTestData;
        private SystemConfigDtos.DomainSettingsResponse domainSettings;
        private List<ReadinessItemResponse> readinessItems = new ArrayList<>();
        private List<TableCountResponse> tableCounts = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private LocalDateTime checkedAt;
    }

    @Data
    @Schema(name = "SystemGoLiveInitializeRequest", description = "一键初始化上线配置")
    public static class InitializeRequest {
        private String targetEnvironment;
        private String systemBaseUrl;
        private String apiBaseUrl;
        private Boolean resetIntegrationToMock;
        private Boolean disableSchedulers;
        private String confirmText;
    }

    @Data
    @Schema(name = "SystemGoLiveClearTestDataRequest", description = "清理测试数据请求")
    public static class ClearTestDataRequest {
        private String confirmText;
        private Boolean includeOperationalLogs;
        private Boolean dryRun;
    }

    @Data
    @Schema(name = "SystemGoLiveOperationResponse", description = "上线操作结果")
    public static class OperationResponse {
        private String operation;
        private String status;
        private String environmentMode;
        private Boolean dryRun;
        private Integer affectedRows;
        private List<TableOperationResult> tables = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private LocalDateTime operatedAt;
    }

    @Data
    @Schema(name = "SystemGoLiveReadinessItemResponse", description = "上线检查项")
    public static class ReadinessItemResponse {
        private String key;
        private String label;
        private String status;
        private String severity;
        private String message;
    }

    @Data
    @Schema(name = "SystemGoLiveTableCountResponse", description = "上线清理表数据量")
    public static class TableCountResponse {
        private String tableName;
        private String category;
        private Long rowCount;
        private Boolean exists;
    }

    @Data
    @Schema(name = "SystemGoLiveTableOperationResult", description = "表清理结果")
    public static class TableOperationResult {
        private String tableName;
        private String category;
        private Long rowCountBefore;
        private Integer affectedRows;
        private Boolean skipped;
        private String message;
    }
}
