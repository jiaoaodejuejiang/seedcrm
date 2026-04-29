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
@TableName("withdraw_record")
public class WithdrawRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("subject_type")
    private String subjectType;

    @TableField("settlement_mode")
    private String settlementMode;

    @TableField("audit_required")
    private Integer auditRequired;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("status")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("approve_time")
    private LocalDateTime approveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("paid_time")
    private LocalDateTime paidTime;
}
