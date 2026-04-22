package com.seedcrm.crm.distributor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("distributor")
public class Distributor {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("contact_info")
    private String contactInfo;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;
}
