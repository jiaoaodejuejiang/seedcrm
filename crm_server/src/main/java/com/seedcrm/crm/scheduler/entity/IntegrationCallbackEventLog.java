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

    @TableField("trace_id")
    private String traceId;

    @TableField("signature_status")
    private String signatureStatus;

    @TableField("process_status")
    private String processStatus;

    @TableField("process_message")
    private String processMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("received_at")
    private LocalDateTime receivedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;
}
