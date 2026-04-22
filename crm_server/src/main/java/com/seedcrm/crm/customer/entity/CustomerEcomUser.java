package com.seedcrm.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("customer_ecom_user")
public class CustomerEcomUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("customer_id")
    private Long customerId;

    @TableField("platform")
    private String platform;

    @TableField("ecom_user_id")
    private String ecomUserId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
