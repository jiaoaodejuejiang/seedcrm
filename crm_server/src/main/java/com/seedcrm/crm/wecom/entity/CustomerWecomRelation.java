package com.seedcrm.crm.wecom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("customer_wecom_relation")
public class CustomerWecomRelation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("customer_id")
    private Long customerId;

    @TableField("external_userid")
    private String externalUserid;

    @TableField("wecom_user_id")
    private String wecomUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
