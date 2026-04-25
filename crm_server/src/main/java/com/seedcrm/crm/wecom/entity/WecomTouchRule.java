package com.seedcrm.crm.wecom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("wecom_touch_rule")
public class WecomTouchRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tag")
    private String tag;

    @TableField("rule_name")
    private String ruleName;

    @TableField("message_template")
    private String messageTemplate;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("is_enabled")
    private Integer isEnabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
