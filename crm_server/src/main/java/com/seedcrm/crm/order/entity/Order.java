package com.seedcrm.crm.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("order_info")
public class Order {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("clue_id")
    private Long clueId;

    @TableField("customer_id")
    private Long customerId;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("source_id")
    private Long sourceId;

    @TableField("type")
    private Integer type;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("deposit")
    private BigDecimal deposit;

    @TableField("status")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("appointment_time")
    private LocalDateTime appointmentTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("arrive_time")
    private LocalDateTime arriveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("complete_time")
    private LocalDateTime completeTime;

    @TableField("remark")
    private String remark;

    @TableField("service_detail_json")
    private String serviceDetailJson;

    @TableField("verification_status")
    private String verificationStatus;

    @TableField("verification_method")
    private String verificationMethod;

    @TableField("verification_code")
    private String verificationCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("verification_time")
    private LocalDateTime verificationTime;

    @TableField("verification_operator_id")
    private Long verificationOperatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
