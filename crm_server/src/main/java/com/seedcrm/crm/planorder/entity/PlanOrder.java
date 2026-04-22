package com.seedcrm.crm.planorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("plan_order")
public class PlanOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("status")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("arrive_time")
    private LocalDateTime arriveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("finish_time")
    private LocalDateTime finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
