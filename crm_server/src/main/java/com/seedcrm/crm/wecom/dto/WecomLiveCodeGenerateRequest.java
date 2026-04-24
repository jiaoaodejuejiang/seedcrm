package com.seedcrm.crm.wecom.dto;

import java.util.List;
import lombok.Data;

@Data
public class WecomLiveCodeGenerateRequest {

    private String codeName;
    private String scene;
    private String strategy;
    private List<String> employeeNames;
    private List<String> employeeAccounts;
}
