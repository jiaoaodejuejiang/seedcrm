package com.seedcrm.crm.clue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("clue")
public class Clue {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("phone")
    private String phone;

    @TableField("wechat")
    private String wechat;

    @TableField("name")
    private String name;

    @TableField("source")
    private String source;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("source_id")
    private Long sourceId;

    @TableField("raw_data")
    private String rawData;

    @TableField("status")
    private String status;

    @TableField("current_owner_id")
    private Long currentOwnerId;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
