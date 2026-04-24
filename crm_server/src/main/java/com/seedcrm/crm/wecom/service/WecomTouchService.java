package com.seedcrm.crm.wecom.service;

import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.dto.WecomLiveCodeGenerateResponse;
import java.util.List;

public interface WecomTouchService {

    void autoTrigger(Long customerId);

    WecomTouchLog manualSend(Long customerId, String message);

    WecomLiveCodeGenerateResponse generateLiveCode(
            String codeName,
            String scene,
            String strategy,
            List<String> employeeNames,
            List<String> employeeAccounts);
}
