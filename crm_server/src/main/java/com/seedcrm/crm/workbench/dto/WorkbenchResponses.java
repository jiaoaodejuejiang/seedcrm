package com.seedcrm.crm.workbench.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        private Long orderCount;
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
