package com.seedcrm.crm.wecom.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WecomLiveCodeGenerateResponse {

    private String codeName;
    private String scene;
    private String strategy;
    private String contactWayId;
    private String qrCodeUrl;
    private String shortLink;
    private Integer employeeCount;
    private List<String> employeeNames;
    private String generatedAt;
    private String summary;
}
