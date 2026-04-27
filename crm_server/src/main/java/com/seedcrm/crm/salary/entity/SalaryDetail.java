package com.seedcrm.crm.salary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("salary_detail")
public class SalaryDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("plan_order_id")
    private Long planOrderId;

    @TableField("user_id")
    private Long userId;

    @TableField("role_code")
    private String roleCode;

    @TableField("order_amount")
    private BigDecimal orderAmount;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("settlement_id")
    private Long settlementId;

    @TableField("adjustment_type")
    private String adjustmentType;

    @TableField("refund_record_id")
    private Long refundRecordId;

    @TableField("source_salary_detail_id")
    private Long sourceSalaryDetailId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("settlement_time")
    private LocalDateTime settlementTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
