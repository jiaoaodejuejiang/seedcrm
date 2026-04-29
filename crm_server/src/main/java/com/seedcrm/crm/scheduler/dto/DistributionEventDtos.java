package com.seedcrm.crm.scheduler.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

public final class DistributionEventDtos {

    private DistributionEventDtos() {
    }

    @Data
    public static class DistributionEventRequest {
        private String eventType;
        private String eventId;
        private String partnerCode;
        private String occurredAt;
        private DistributionMemberPayload member;
        private DistributionPromoterPayload promoter;
        private DistributionOrderPayload order;
        private Map<String, Object> rawData;
    }

    @Data
    public static class DistributionMemberPayload {
        private String externalMemberId;
        private String name;
        private String phone;
        private String role;
    }

    @Data
    public static class DistributionPromoterPayload {
        private String externalPromoterId;
        private String role;
    }

    @Data
    public static class DistributionOrderPayload {
        private String externalOrderId;
        private String externalTradeNo;
        private String type;
        private BigDecimal amount;
        private String paidAt;
        private String storeCode;
        private String status;
        private String refundStatus;
        private BigDecimal refundAmount;
        private String refundAt;
    }

    @Data
    public static class DistributionEventResponse {
        private String traceId;
        private String idempotencyResult;
        private Long customerId;
        private Long orderId;
        private String processStatus;
        private String message;
    }
}
