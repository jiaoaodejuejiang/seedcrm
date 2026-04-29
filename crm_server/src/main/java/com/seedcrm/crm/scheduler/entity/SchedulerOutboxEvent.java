package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scheduler_outbox_event")
public class SchedulerOutboxEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("event_key")
    private String eventKey;

    @TableField("event_type")
    private String eventType;

    @TableField("provider_code")
    private String providerCode;

    @TableField("related_order_id")
    private Long relatedOrderId;

    @TableField("related_plan_order_id")
    private Long relatedPlanOrderId;

    @TableField("external_partner_code")
    private String externalPartnerCode;

    @TableField("external_order_id")
    private String externalOrderId;

    @TableField("payload")
    private String payload;

    @TableField("destination_url")
    private String destinationUrl;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("last_error")
    private String lastError;

    @TableField("last_response")
    private String lastResponse;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("sent_at")
    private LocalDateTime sentAt;
}
