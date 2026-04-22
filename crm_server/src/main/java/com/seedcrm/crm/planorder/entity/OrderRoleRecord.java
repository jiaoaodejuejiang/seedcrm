package com.seedcrm.crm.planorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_role_record")
public class OrderRoleRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("plan_order_id")
    private Long planOrderId;

    @TableField("role_code")
    private String roleCode;

    @TableField("user_id")
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("is_current")
    private Integer isCurrent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
