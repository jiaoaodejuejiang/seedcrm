package com.seedcrm.crm.customer.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class MemberResponses {

    private MemberResponses() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberListResponse {
        private List<MemberListItemResponse> records;
        private Long total;
        private Integer page;
        private Integer pageSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberListItemResponse {
        private Long customerId;
        private String name;
        private String phone;
        private String source;
        private String sourceChannel;
        private String sourceDisplayName;
        private String externalPartnerCode;
        private String externalMemberId;
        private String externalMemberRole;
        private Boolean wecomBound;
        private String primaryTag;
        private Long latestOrderId;
        private String latestOrderNo;
        private String latestOrderStatus;
        private BigDecimal latestOrderAmount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime latestOrderTime;
        private Long orderCount;
        private BigDecimal totalOrderAmount;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastSyncTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }
}
