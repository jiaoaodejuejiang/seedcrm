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
@TableName("salary_settlement_policy")
public class SalarySettlementPolicy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("source_policy_id")
    private Long sourcePolicyId;

    @TableField("policy_name")
    private String policyName;

    @TableField("subject_type")
    private String subjectType;

    @TableField("scope_type")
    private String scopeType;

    @TableField("role_codes")
    private String roleCodes;

    @TableField("amount_min")
    private BigDecimal amountMin;

    @TableField("amount_max")
    private BigDecimal amountMax;

    @TableField("settlement_cycle")
    private String settlementCycle;

    @TableField("settlement_mode")
    private String settlementMode;

    @TableField("audit_threshold_amount")
    private BigDecimal auditThresholdAmount;

    @TableField("priority")
    private Integer priority;

    @TableField("enabled")
    private Integer enabled;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("update_time")
    private LocalDateTime updateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("published_time")
    private LocalDateTime publishedTime;
}
