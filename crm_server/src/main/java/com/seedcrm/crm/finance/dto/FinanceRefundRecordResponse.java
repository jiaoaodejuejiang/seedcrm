package com.seedcrm.crm.finance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceRefundRecordResponse {

    private Long refundRecordId;
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private String customerPhoneMasked;
    private String storeName;
    private Long planOrderId;
    private String refundScene;
    private String refundObject;
    private BigDecimal refundAmount;
    private String refundReasonType;
    private String refundReasonMasked;
    private String status;
    private String platformChannel;
    private Long operatorUserId;
    private String operatorName;
    private Boolean reverseStorePerformance;
    private Boolean reverseCustomerService;
    private Boolean reverseDistributor;
    private Integer salaryReversalCount;
    private BigDecimal salaryReversalAmount;
    private Boolean fundsTransferred;
    private Boolean ledgerOnly;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
