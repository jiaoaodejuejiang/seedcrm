package com.seedcrm.crm.distributor.dto;

import lombok.Data;

@Data
public class DistributorCreateRequest {

    private String name;
    private String contactInfo;
    private String status;
}
