package com.seedcrm.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("customer_tag_rule")
public class CustomerTagRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tag_code")
    private String tagCode;

    @TableField("rule_type")
    private String ruleType;

    @TableField("rule_value")
    private String ruleValue;

    @TableField("priority")
    private Integer priority;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
