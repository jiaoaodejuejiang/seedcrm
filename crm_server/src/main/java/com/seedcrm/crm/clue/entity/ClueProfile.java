package com.seedcrm.crm.clue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("clue_profile")
public class ClueProfile {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("clue_id")
    private Long clueId;

    @TableField("display_name")
    private String displayName;

    @TableField("phone")
    private String phone;

    @TableField("call_status")
    private String callStatus;

    @TableField("lead_stage")
    private String leadStage;

    @TableField("lead_tags_json")
    private String leadTagsJson;

    @TableField("follow_records_json")
    private String followRecordsJson;

    @TableField("intended_store_name")
    private String intendedStoreName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("assigned_at")
    private LocalDateTime assignedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
