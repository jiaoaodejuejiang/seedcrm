package com.seedcrm.crm.systemconfig.dto;

import java.time.LocalDateTime;
import lombok.Data;

public final class SystemConfigDtos {

    private SystemConfigDtos() {
    }

    @Data
    public static class ConfigResponse {
        private Long id;
        private String configKey;
        private String configValue;
        private String valueType;
        private String scopeType;
        private String scopeId;
        private Integer enabled;
        private String description;
        private Boolean sensitive;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class SaveConfigRequest {
        private String configKey;
        private String configValue;
        private String valueType;
        private String scopeType;
        private String scopeId;
        private Integer enabled;
        private String description;
        private String summary;
    }

    @Data
    public static class DomainSettingsResponse {
        private String systemBaseUrl;
        private String apiBaseUrl;
        private String eventIngestUrl;
        private String swaggerUiUrl;
        private String openApiDocsUrl;
    }

    @Data
    public static class SaveDomainSettingsRequest {
        private String systemBaseUrl;
        private String apiBaseUrl;
    }
}
