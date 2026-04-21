package com.seedcrm.crm.wecom.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.wecom.dto.WecomSendRequest;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;
import com.seedcrm.crm.wecom.service.WecomTouchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wecom")
public class WecomController {

    private final WecomTouchService wecomTouchService;

    public WecomController(WecomTouchService wecomTouchService) {
        this.wecomTouchService = wecomTouchService;
    }

    @PostMapping("/send")
    public ApiResponse<WecomTouchLog> send(@RequestBody WecomSendRequest request) {
        return ApiResponse.success(wecomTouchService.manualSend(
                request == null ? null : request.getCustomerId(),
                request == null ? null : request.getMessage()));
    }
}
