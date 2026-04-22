package com.seedcrm.crm.distributor.dto;

import lombok.Data;

@Data
public class DistributorWithdrawApproveRequest {

    private Long withdrawId;
    private String status;
}
