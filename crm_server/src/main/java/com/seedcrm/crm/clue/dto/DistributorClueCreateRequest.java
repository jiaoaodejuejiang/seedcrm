package com.seedcrm.crm.clue.dto;

import lombok.Data;

@Data
public class DistributorClueCreateRequest {

    private Long distributorId;
    private String phone;
    private String name;
}
