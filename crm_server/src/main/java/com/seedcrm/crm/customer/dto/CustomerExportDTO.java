package com.seedcrm.crm.customer.dto;

import java.util.List;
import lombok.Data;

@Data
public class CustomerExportDTO {

    private Long id;
    private String name;
    private String phone;
    private String wechat;
    private Long sourceClueId;
    private String sourceChannel;
    private Long sourceId;
    private String status;
    private String level;
    private List<String> tags;
}
