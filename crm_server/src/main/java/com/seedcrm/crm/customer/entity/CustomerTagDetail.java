package com.seedcrm.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("customer_tag_detail")
public class CustomerTagDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("customer_id")
    private Long customerId;

    @TableField("tag_code")
    private String tagCode;

    @TableField("tag_name")
    private String tagName;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
