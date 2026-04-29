package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("scheduler_job_audit_log")
public class SchedulerJobAuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("job_code")
    private String jobCode;

    @TableField("log_id")
    private Long logId;

    @TableField("action_type")
    private String actionType;

    @TableField("actor_type")
    private String actorType;

    @TableField("actor_user_id")
    private Long actorUserId;

    @TableField("actor_role_code")
    private String actorRoleCode;

    @TableField("status")
    private String status;

    @TableField("summary")
    private String summary;

    @TableField("detail")
    private String detail;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
