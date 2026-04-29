package com.seedcrm.crm.planorder.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

public final class ServiceFormTemplateDtos {

    private ServiceFormTemplateDtos() {
    }

    @Data
    public static class TemplateResponse {
        private Long id;
        private Long sourceTemplateId;
        private String templateCode;
        private String templateName;
        private String title;
        private String industry;
        private String layoutMode;
        private String configJson;
        private Integer recommended;
        private Integer enabled;
        private String status;
        private String description;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime publishedTime;
    }

    @Data
    public static class BindingResponse {
        private Long id;
        private Long storeId;
        private String storeName;
        private Long templateId;
        private String templateName;
        private String templateTitle;
        private String templateSnapshotJson;
        private String effectiveFrom;
        private Integer allowOverride;
        private Integer enabled;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }

    @Data
    public static class SaveTemplateRequest {
        private Long id;
        private String templateCode;
        private String templateName;
        private String title;
        private String industry;
        private String layoutMode;
        private String configJson;
        private Integer recommended;
        private String description;
    }

    @Data
    public static class TemplateStatusRequest {
        private Long templateId;
        private String reason;
    }

    @Data
    public static class SaveBindingRequest {
        private Long id;
        private Long storeId;
        private String storeName;
        private Long templateId;
        private String effectiveFrom;
        private Integer allowOverride;
        private String reason;
    }

    @Data
    public static class BindingStatusRequest {
        private Long bindingId;
        private String reason;
    }

    @Data
    public static class PreviewResponse {
        private String storeName;
        private TemplateResponse template;
        private BindingResponse binding;
        private String message;
    }
}
