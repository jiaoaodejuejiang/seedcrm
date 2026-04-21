package com.seedcrm.crm.wecom.service;

import com.seedcrm.crm.wecom.entity.WecomTouchLog;

public interface WecomTouchService {

    void autoTrigger(Long customerId);

    WecomTouchLog manualSend(Long customerId, String message);
}
