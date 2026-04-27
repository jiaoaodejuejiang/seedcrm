package com.seedcrm.crm.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_refund_record")
public class OrderRefundRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("plan_order_id")
    private Long planOrderId;

    @TableField("refund_scene")
    private String refundScene;

    @TableField("refund_object")
    private String refundObject;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("refund_reason_type")
    private String refundReasonType;

    @TableField("refund_reason")
    private String refundReason;

    @TableField("status")
    private String status;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("out_order_no")
    private String outOrderNo;

    @TableField("out_refund_no")
    private String outRefundNo;

    @TableField("external_refund_id")
    private String externalRefundId;

    @TableField("item_order_id")
    private String itemOrderId;

    @TableField("notify_url")
    private String notifyUrl;

    @TableField("platform_channel")
    private String platformChannel;

    @TableField("operator_user_id")
    private Long operatorUserId;

    @TableField("reverse_store_performance")
    private Integer reverseStorePerformance;

    @TableField("reverse_customer_service")
    private Integer reverseCustomerService;

    @TableField("reverse_distributor")
    private Integer reverseDistributor;

    @TableField("raw_request")
    private String rawRequest;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("raw_notify")
    private String rawNotify;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
