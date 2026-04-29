package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("distribution_exception_record")
public class DistributionExceptionRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("partner_code")
    private String partnerCode;

    @TableField("event_type")
    private String eventType;

    @TableField("event_id")
    private String eventId;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("external_order_id")
    private String externalOrderId;

    @TableField("external_member_id")
    private String externalMemberId;

    @TableField("phone")
    private String phone;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("raw_payload")
    private String rawPayload;

    @TableField("callback_log_trace_id")
    private String callbackLogTraceId;

    @TableField("handling_status")
    private String handlingStatus;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("handler_user_id")
    private Long handlerUserId;

    @TableField("handler_role_code")
    private String handlerRoleCode;

    @TableField("handle_remark")
    private String handleRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("handled_at")
    private LocalDateTime handledAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
