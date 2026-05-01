package com.seedcrm.crm.scheduler.dto;

import java.math.BigDecimal;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public final class DistributionEventDtos {

    private DistributionEventDtos() {
    }

    @Data
    @Schema(name = "DistributionEventRequest", description = "外部分销方案 B 入站事件。只有 distribution.order.paid 允许创建或匹配 Customer + Order(paid)，不得进入 Clue。")
    public static class DistributionEventRequest {
        @Schema(description = "事件类型", example = "distribution.order.paid")
        private String eventType;
        @Schema(description = "外部事件 ID，用于日志追踪与重复事件识别", example = "evt_202604290001")
        private String eventId;
        @Schema(description = "外部合作方编码，必须与 X-Partner-Code 一致", example = "DISTRIBUTION")
        private String partnerCode;
        @Schema(description = "事件发生时间，建议 ISO-8601", example = "2026-04-29T10:00:00+08:00")
        private String occurredAt;
        @Schema(description = "购买会员信息，入站事务内用于创建或匹配 Customer")
        private DistributionMemberPayload member;
        @Schema(description = "外部分销者 / 团长 / 团员信息，仅做来源身份快照，不参与 SeedCRM 分佣提现")
        private DistributionPromoterPayload promoter;
        @Schema(description = "外部分销已支付订单信息，入站后生成或更新 Order(paid)")
        private DistributionOrderPayload order;
        @Schema(description = "外部原始报文，必须保留以支持追踪、补偿和对账")
        private Map<String, Object> rawData;
    }

    @Data
    @Schema(name = "DistributionMemberPayload", description = "外部分销购买会员信息")
    public static class DistributionMemberPayload {
        @Schema(description = "外部会员 ID，与 partnerCode 共同构成会员身份匹配条件", example = "m_10001")
        private String externalMemberId;
        @Schema(description = "会员姓名", example = "张三")
        private String name;
        @Schema(description = "会员手机号，本地标准化后用于 Customer 匹配兜底", example = "13800000000")
        private String phone;
        @Schema(description = "外部会员角色，仅展示 / 画像使用", example = "member")
        private String role;
    }

    @Data
    @Schema(name = "DistributionPromoterPayload", description = "外部分销推广者信息")
    public static class DistributionPromoterPayload {
        @Schema(description = "外部分销者 / 团长 / 团员 ID", example = "p_90001")
        private String externalPromoterId;
        @Schema(description = "外部推广角色，仅作为来源身份快照", example = "leader")
        private String role;
    }

    @Data
    @Schema(name = "DistributionOrderPayload", description = "外部分销已支付订单信息")
    public static class DistributionOrderPayload {
        @Schema(description = "外部订单 ID，与 partnerCode 构成订单幂等唯一键", example = "o_20001")
        private String externalOrderId;
        @Schema(description = "外部支付流水号", example = "pay_30001")
        private String externalTradeNo;
        @Schema(description = "订单类型：coupon / deposit / distribution_product", example = "coupon")
        private String type;
        @Schema(description = "外部商品 ID，用于商品 / SKU 映射规则", example = "prod_001")
        private String externalProductId;
        @Schema(description = "外部 SKU ID，用于商品 / SKU 映射规则", example = "sku_001")
        private String externalSkuId;
        @Schema(description = "外部商品名称，仅做快照展示和映射排查", example = "亲子摄影定金")
        private String productName;
        @Schema(description = "外部渠道编码，用于区分抖音、分销等来源下的商品映射", example = "distribution")
        private String channelCode;
        @Schema(description = "已支付金额，单位由字段映射配置约定，默认按分处理", example = "19900")
        private BigDecimal amount;
        @Schema(description = "支付时间", example = "2026-04-29T09:58:00+08:00")
        private String paidAt;
        @Schema(description = "外部门店编码，用于匹配 SeedCRM 门店", example = "store_001")
        private String storeCode;
        @Schema(description = "外部订单状态，需经状态映射后才可驱动内部处理", example = "paid")
        private String status;
        @Schema(description = "外部退款状态快照", example = "none")
        private String refundStatus;
        @Schema(description = "外部退款金额快照，不在 SeedCRM 内处理资金流", example = "0")
        private BigDecimal refundAmount;
        @Schema(description = "外部退款时间快照", example = "2026-04-30T09:58:00+08:00")
        private String refundAt;
    }

    @Data
    @Schema(name = "DistributionEventResponse", description = "分销入站事件处理结果")
    public static class DistributionEventResponse {
        @Schema(description = "联调追踪编号", example = "trace_001")
        private String traceId;
        @Schema(description = "幂等处理结果：CREATED / UPDATED / DUPLICATE / EXCEPTION_QUEUED", example = "CREATED")
        private String idempotencyResult;
        @Schema(description = "匹配或创建的 Customer ID", example = "10001")
        private Long customerId;
        @Schema(description = "创建或更新的 Order ID", example = "20001")
        private Long orderId;
        @Schema(description = "处理状态", example = "SUCCESS")
        private String processStatus;
        @Schema(description = "处理说明")
        private String message;
    }
}
