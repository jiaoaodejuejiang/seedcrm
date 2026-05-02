package com.seedcrm.crm.clue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("clue_record")
public class ClueRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("clue_id")
    private Long clueId;

    @TableField("record_key")
    private String recordKey;

    @TableField("record_type")
    private String recordType;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("external_record_id")
    private String externalRecordId;

    @TableField("external_order_id")
    private String externalOrderId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("occurred_at")
    private LocalDateTime occurredAt;

    @TableField("raw_data")
    private String rawData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
