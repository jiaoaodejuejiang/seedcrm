package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scheduler_job_log")
public class SchedulerJobLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("job_code")
    private String jobCode;

    @TableField("queue_name")
    private String queueName;

    @TableField("provider_id")
    private Long providerId;

    @TableField("sync_mode")
    private String syncMode;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("payload")
    private String payload;

    @TableField("error_message")
    private String errorMessage;

    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("imported_count")
    private Integer importedCount;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
