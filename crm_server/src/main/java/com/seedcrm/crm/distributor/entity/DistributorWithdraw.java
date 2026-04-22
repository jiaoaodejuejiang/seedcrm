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
@TableName("distributor_withdraw")
public class DistributorWithdraw {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("distributor_id")
    private Long distributorId;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("status")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;
}
