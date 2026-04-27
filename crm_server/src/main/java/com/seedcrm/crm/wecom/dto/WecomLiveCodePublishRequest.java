package com.seedcrm.crm.wecom.dto;

import java.util.List;
import lombok.Data;

@Data
public class WecomLiveCodePublishRequest {

    private Long configId;
    private List<String> storeNames;
}
