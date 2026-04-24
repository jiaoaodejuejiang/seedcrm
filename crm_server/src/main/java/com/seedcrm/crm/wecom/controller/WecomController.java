package com.seedcrm.crm.wecom.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.wecom.dto.WecomLiveCodeGenerateRequest;
import com.seedcrm.crm.wecom.dto.WecomLiveCodeGenerateResponse;
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

    @PostMapping("/live-code/generate")
    public ApiResponse<WecomLiveCodeGenerateResponse> generateLiveCode(
            @RequestBody WecomLiveCodeGenerateRequest request) {
        return ApiResponse.success(wecomTouchService.generateLiveCode(
                request == null ? null : request.getCodeName(),
                request == null ? null : request.getScene(),
                request == null ? null : request.getStrategy(),
                request == null ? null : request.getEmployeeNames(),
                request == null ? null : request.getEmployeeAccounts()));
    }
}
