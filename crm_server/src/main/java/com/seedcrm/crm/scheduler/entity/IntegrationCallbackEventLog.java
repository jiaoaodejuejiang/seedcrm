package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("integration_callback_event_log")
public class IntegrationCallbackEventLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("provider_code")
    private String providerCode;

    @TableField("provider_id")
    private Long providerId;

    @TableField("callback_name")
    private String callbackName;

    @TableField("app_code")
    private String appCode;

    @TableField("request_method")
    private String requestMethod;

    @TableField("callback_path")
    private String callbackPath;

    @TableField("query_string")
    private String queryString;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("auth_code")
    private String authCode;

    @TableField("callback_state")
    private String callbackState;

    @TableField("event_type")
    private String eventType;

    @TableField("event_id")
    private String eventId;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("idempotency_status")
    private String idempotencyStatus;

    @TableField("trace_id")
    private String traceId;

    @TableField("signature_mode")
    private String signatureMode;

    @TableField("signature_value_masked")
    private String signatureValueMasked;

    @TableField("signature_status")
    private String signatureStatus;

    @TableField("trust_level")
    private String trustLevel;

    @TableField("received_ip")
    private String receivedIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("timestamp_value")
    private String timestampValue;

    @TableField("nonce")
    private String nonce;

    @TableField("body_hash")
    private String bodyHash;

    @TableField("process_policy")
    private String processPolicy;

    @TableField("process_status")
    private String processStatus;

    @TableField("process_message")
    private String processMessage;

    @TableField("related_job_code")
    private String relatedJobCode;

    @TableField("related_run_id")
    private String relatedRunId;

    @TableField("related_customer_id")
    private Long relatedCustomerId;

    @TableField("related_order_id")
    private Long relatedOrderId;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("received_at")
    private LocalDateTime receivedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("processed_at")
    private LocalDateTime processedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;
}
