package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scheduler_job")
public class SchedulerJob {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("job_code")
    private String jobCode;

    @TableField("module_code")
    private String moduleCode;

    @TableField("sync_mode")
    private String syncMode;

    @TableField("interval_minutes")
    private Integer intervalMinutes;

    @TableField("retry_limit")
    private Integer retryLimit;

    @TableField("queue_name")
    private String queueName;

    @TableField("provider_id")
    private Long providerId;

    @TableField("endpoint")
    private String endpoint;

    @TableField("status")
    private String status;

    @TableField("last_run_time")
    private LocalDateTime lastRunTime;

    @TableField("next_run_time")
    private LocalDateTime nextRunTime;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
