package com.seedcrm.crm.clue.management.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class ClueManagementDtos {

    private ClueManagementDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentStrategyRequest {
        private Integer enabled;
        private String assignmentMode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentStrategyResponse {
        private Long id;
        private Long storeId;
        private Integer enabled;
        private String assignmentMode;
        private Long lastAssignedUserId;
        private Long updatedBy;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DedupConfigRequest {
        private Integer enabled;
        private Integer windowDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DedupConfigResponse {
        private Integer enabled;
        private Integer windowDays;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    public static class DutyCustomerServiceBatchRequest {
        private List<DutyCustomerServiceItemRequest> staff = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DutyCustomerServiceItemRequest {
        private Long id;
        private Long userId;
        @JsonAlias("username")
        private String accountName;
        private String userName;
        private String shiftLabel;
        private Integer onDuty;
        private Integer onLeave;
        private Integer sortOrder;
        private String remark;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DutyCustomerServiceResponse {
        private Long id;
        private Long storeId;
        private Long userId;
        private String accountName;
        private String userName;
        private String shiftLabel;
        private Integer onDuty;
        private Integer onLeave;
        private Integer sortOrder;
        private String remark;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updatedAt;
    }
}
