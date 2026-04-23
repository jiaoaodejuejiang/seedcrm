package com.seedcrm.crm.clue.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("clue_assignment_strategy")
public class ClueAssignmentStrategy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("store_id")
    private Long storeId;

    @TableField("enabled")
    private Integer enabled;

    @TableField("assignment_mode")
    private String assignmentMode;

    @TableField("last_assigned_user_id")
    private Long lastAssignedUserId;

    @TableField("updated_by")
    private Long updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
