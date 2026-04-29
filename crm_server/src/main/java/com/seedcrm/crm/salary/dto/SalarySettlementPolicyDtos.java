package com.seedcrm.crm.salary.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class SalarySettlementPolicyDtos {

    private SalarySettlementPolicyDtos() {
    }

    @Data
    public static class PolicyResponse {
        private Long id;
        private Long sourcePolicyId;
        private String policyName;
        private String subjectType;
        private String scopeType;
        private List<String> roleCodes = new ArrayList<>();
        private BigDecimal amountMin;
        private BigDecimal amountMax;
        private String settlementCycle;
        private String settlementMode;
        private BigDecimal auditThresholdAmount;
        private Integer priority;
        private Integer enabled;
        private String status;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime publishedTime;
    }

    @Data
    public static class SavePolicyRequest {
        private Long id;
        private String policyName;
        private String subjectType;
        private String scopeType;
        private List<String> roleCodes = new ArrayList<>();
        private BigDecimal amountMin;
        private BigDecimal amountMax;
        private String settlementCycle;
        private String settlementMode;
        private BigDecimal auditThresholdAmount;
        private Integer priority;
        private String remark;
    }

    @Data
    public static class PolicyStatusRequest {
        private Long policyId;
        private String reason;
    }

    @Data
    public static class SimulateRequest {
        private String subjectType;
        private String roleCode;
        private BigDecimal amount;
    }

    @Data
    public static class SimulateResponse {
        private boolean matched;
        private String subjectType;
        private String roleCode;
        private BigDecimal amount;
        private String settlementCycle;
        private String settlementMode;
        private boolean ledgerOnly;
        private boolean autoApprove;
        private boolean requiresAudit;
        private String nextAction;
        private String message;
        private PolicyResponse matchedPolicy;
    }
}
