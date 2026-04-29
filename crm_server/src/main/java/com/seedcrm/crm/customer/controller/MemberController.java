package com.seedcrm.crm.customer.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.customer.dto.MemberResponses.MemberListResponse;
import com.seedcrm.crm.customer.service.MemberService;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;

    public MemberController(MemberService memberService,
                            PermissionRequestContextResolver permissionRequestContextResolver) {
        this.memberService = memberService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
    }

    @GetMapping
    public ApiResponse<MemberListResponse> listMembers(@RequestParam(required = false) String sourceTab,
                                                       @RequestParam(required = false) String phone,
                                                       @RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String externalMemberId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer pageSize,
                                                       HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        return ApiResponse.success(memberService.listMembers(
                sourceTab,
                phone,
                name,
                externalMemberId,
                page,
                pageSize,
                context));
    }
}
