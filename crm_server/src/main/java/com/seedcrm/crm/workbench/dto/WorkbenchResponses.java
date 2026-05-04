package com.seedcrm.crm.workbench.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.FollowRecordResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class WorkbenchResponses {

    private WorkbenchResponses() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffMemberOption {
        private Long userId;
        private String userName;
        private String roleCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffRoleOptionResponse {
        private String roleCode;
        private String roleName;
        private List<StaffMemberOption> staffOptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClueItemResponse {
        private Long id;
        private String name;
        private String phone;
        private String wechat;
        private String sourceChannel;
        private String productSourceType;
        private Long sourceId;
        private String status;
        private Long currentOwnerId;
        private String currentOwnerName;
        private Integer isPublic;
        private String storeName;
        private Long customerId;
        private Long latestOrderId;
        private String latestOrderStatus;
        private String latestOrderType;
        private Long orderCount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
        private String displayName;
        private String contactPhone;
        private String callStatus;
        private String leadStage;
        private List<String> leadTags;
        private List<FollowRecordResponse> followRecords;
        private Long profileId;
        private String intendedStoreName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime assignedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime profileUpdatedAt;
        private List<ClueRecordItemResponse> clueRecords;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClueRecordItemResponse {
        private Long id;
        private String recordType;
        private String sourceChannel;
        private String externalRecordId;
        private String externalOrderId;
        private String title;
        private String content;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime occurredAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CluePageResponse {
        private List<ClueItemResponse> rows;
        private Long total;
        private Integer page;
        private Integer pageSize;
        private Map<String, Long> productSourceCounts;
        private Map<String, Long> queueStatusCounts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClueSyncStatusResponse {
        private Long id;
        private String jobCode;
        private String status;
        private String triggerType;
        private Integer importedCount;
        private String payload;
        private String errorMessage;
        private Long durationMs;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime finishedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private String orderNo;
        private Long clueId;
        private Long customerId;
        private String customerName;
        private String customerPhone;
        private String sourceChannel;
        private String productSourceType;
        private String storeName;
        private BigDecimal amount;
        private BigDecimal deposit;
        private String type;
        private String status;
        private String statusCategory;
        private Long planOrderId;
        private String planOrderStatus;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime appointmentTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime arriveTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime completeTime;
        private String remark;
        private String serviceDetailJson;
        private String verificationStatus;
        private String verificationMethod;
        private String verificationCode;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime verificationTime;
        private Long verificationOperatorId;
        private List<AppointmentRecordResponse> appointmentRecords;
        private List<FulfillmentRecordResponse> fulfillmentRecords;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentRecordResponse {
        private String actionType;
        private String fromStatus;
        private String toStatus;
        private Long operatorUserId;
        private String operatorUserName;
        private String remark;
        private String extraJson;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentItemResponse {
        private Long id;
        private String orderNo;
        private Long customerId;
        private String customerName;
        private String customerPhone;
        private String sourceChannel;
        private String productSourceType;
        private String storeName;
        private String type;
        private String status;
        private String statusCategory;
        private String verificationStatus;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime appointmentTime;
        private List<AppointmentRecordResponse> appointmentRecords;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FulfillmentRecordResponse {
        private String actionType;
        private String stage;
        private String fromStatus;
        private String toStatus;
        private Long operatorUserId;
        private String operatorUserName;
        private String summary;
        private List<String> detailItems;
        private String remark;
        private String extraJson;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreLiveCodePreviewResponse {
        private Long configId;
        private String codeName;
        private String storeName;
        private String contactWayId;
        private String qrCodeUrl;
        private String shortLink;
        private String bindingState;
        private Long bindingUserId;
        private String bindingUserName;
        private String bindingUserPhone;
        private String bindingStatus;
        private String bindingMessage;
        private List<String> storeNames;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime generatedAt;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime publishedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentRoleResponse {
        private String roleCode;
        private String roleName;
        private Long userId;
        private String userName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleRecordResponse {
        private Long id;
        private String roleCode;
        private String roleName;
        private Long userId;
        private String userName;
        private Integer isCurrent;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSnapshotResponse {
        private Long customerId;
        private String name;
        private String phone;
        private String wechat;
        private String sourceChannel;
        private String status;
        private String primaryTag;
        private Boolean wecomBound;
        private String wecomExternalUserid;
        private String wecomUserId;
        private Integer ecomBindingCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanOrderItemResponse {
        private Long planOrderId;
        private String planOrderStatus;
        private Long orderId;
        private String orderNo;
        private String orderStatus;
        private BigDecimal amount;
        private String sourceChannel;
        private CustomerSnapshotResponse customer;
        private List<CurrentRoleResponse> currentRoles;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime arriveTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime finishTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanOrderWorkbenchResponse {
        private PlanOrderItemResponse summary;
        private OrderItemResponse order;
        private CustomerSnapshotResponse customer;
        private List<CurrentRoleResponse> currentRoles;
        private List<RoleRecordResponse> roleRecords;
        private List<FlowTraceItemResponse> flowTrace;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowTraceItemResponse {
        private String actionCode;
        private String fromNodeCode;
        private String toNodeCode;
        private String summary;
        private String actorRoleCode;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime eventTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EcomBindingResponse {
        private String platform;
        private String ecomUserId;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WecomLogResponse {
        private String message;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerProfileResponse {
        private CustomerSnapshotResponse customer;
        private List<String> tagDetails;
        private List<OrderItemResponse> orderHistory;
        private List<EcomBindingResponse> ecomBindings;
        private List<WecomLogResponse> recentWecomLogs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributorBoardItemResponse {
        private Long distributorId;
        private String name;
        private String contactInfo;
        private String status;
        private Long clueCount;
        private Long dealCustomerCount;
        private Long orderCount;
        private BigDecimal totalIncome;
        private BigDecimal settledIncome;
        private BigDecimal unsettledIncome;
        private BigDecimal withdrawableAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawRecordResponse {
        private String ownerType;
        private Long ownerId;
        private String ownerName;
        private BigDecimal amount;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceOverviewResponse {
        private BigDecimal todayIncome;
        private BigDecimal employeeIncome;
        private BigDecimal distributorIncome;
        private List<WithdrawRecordResponse> withdrawRecords;
        private List<FinanceMonthlyStatResponse> monthlyStats;
        private List<FinanceTeamStatResponse> teamStats;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceMonthlyStatResponse {
        private String monthLabel;
        private BigDecimal orderIncome;
        private BigDecimal employeeIncome;
        private BigDecimal distributorIncome;
        private BigDecimal withdrawAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinanceTeamStatResponse {
        private String teamLabel;
        private Long memberCount;
        private Long serviceCount;
        private BigDecimal orderIncome;
        private BigDecimal incomeAmount;
    }
}
