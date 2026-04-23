package com.seedcrm.crm.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("permission_policy")
public class PermissionPolicy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("module_code")
    private String moduleCode;

    @TableField("action_code")
    private String actionCode;

    @TableField("role_code")
    private String roleCode;

    @TableField("data_scope")
    private String dataScope;

    @TableField("condition_rule")
    private String conditionRule;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
