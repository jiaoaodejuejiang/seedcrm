package com.seedcrm.crm.salary.dto;

import lombok.Data;

@Data
public class WithdrawApproveRequest {

    private Long withdrawId;
    private String status;
}
