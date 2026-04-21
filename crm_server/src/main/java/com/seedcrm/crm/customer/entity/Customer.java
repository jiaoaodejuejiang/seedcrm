package com.seedcrm.crm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("customer")
public class Customer {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("phone")
    private String phone;

    @TableField("wechat")
    private String wechat;

    @TableField("source_clue_id")
    private Long sourceClueId;

    @TableField("status")
    private String status;

    @TableField("level")
    private String level;

    @TableField("tag")
    private String tag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("first_order_time")
    private LocalDateTime firstOrderTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_order_time")
    private LocalDateTime lastOrderTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
