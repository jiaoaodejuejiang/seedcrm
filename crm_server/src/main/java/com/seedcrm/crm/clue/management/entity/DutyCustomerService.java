package com.seedcrm.crm.clue.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("duty_customer_service")
public class DutyCustomerService {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("store_id")
    private Long storeId;

    @TableField("user_id")
    private Long userId;

    @TableField("username")
    private String accountName;

    @TableField("user_name")
    private String userName;

    @TableField("shift_label")
    private String shiftLabel;

    @TableField("on_duty")
    private Integer onDuty;

    @TableField("on_leave")
    private Integer onLeave;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("remark")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
