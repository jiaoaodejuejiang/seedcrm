package com.seedcrm.crm.distributor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("distributor_income_detail")
public class DistributorIncomeDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("distributor_id")
    private Long distributorId;

    @TableField("order_id")
    private Long orderId;

    @TableField("order_amount")
    private BigDecimal orderAmount;

    @TableField("income_amount")
    private BigDecimal incomeAmount;

    @TableField("settlement_id")
    private Long settlementId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("settlement_time")
    private LocalDateTime settlementTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
