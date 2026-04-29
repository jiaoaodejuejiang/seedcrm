package com.seedcrm.crm.planorder.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.ServiceFormTemplatePermissionGuard;
import com.seedcrm.crm.planorder.dto.ServiceFormTemplateDtos;
import com.seedcrm.crm.planorder.service.ServiceFormTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/planOrder/service-form-templates")
public class ServiceFormTemplateController {

    private final ServiceFormTemplateService serviceFormTemplateService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final ServiceFormTemplatePermissionGuard serviceFormTemplatePermissionGuard;

    public ServiceFormTemplateController(ServiceFormTemplateService serviceFormTemplateService,
                                         PermissionRequestContextResolver permissionRequestContextResolver,
                                         ServiceFormTemplatePermissionGuard serviceFormTemplatePermissionGuard) {
        this.serviceFormTemplateService = serviceFormTemplateService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.serviceFormTemplatePermissionGuard = serviceFormTemplatePermissionGuard;
    }

    @GetMapping("/templates")
    public ApiResponse<List<ServiceFormTemplateDtos.TemplateResponse>> templates(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        serviceFormTemplatePermissionGuard.checkCatalog(context);
        List<ServiceFormTemplateDtos.TemplateResponse> templates = serviceFormTemplateService.listTemplates();
        if (!"ADMIN".equalsIgnoreCase(context.getRoleCode())) {
            templates = templates.stream()
                    .filter(item -> "PUBLISHED".equalsIgnoreCase(item.getStatus()))
                    .filter(item -> item.getEnabled() == null || item.getEnabled() == 1)
                    .toList();
        }
        return ApiResponse.success(templates);
    }

    @GetMapping("/bindings")
    public ApiResponse<List<ServiceFormTemplateDtos.BindingResponse>> bindings(@RequestParam(required = false) String storeName,
                                                                               HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        String scopedStoreName = resolveScopedStoreName(context, storeName);
        serviceFormTemplatePermissionGuard.checkBindingUpdate(context, scopedStoreName);
        return ApiResponse.success(serviceFormTemplateService.listBindings(scopedStoreName, resolveScopedStoreId(context)));
    }

    @GetMapping("/preview")
    public ApiResponse<ServiceFormTemplateDtos.PreviewResponse> preview(@RequestParam(required = false) Long templateId,
                                                                        @RequestParam(required = false) String storeName,
                                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        String scopedStoreName = resolveScopedStoreName(context, storeName);
        serviceFormTemplatePermissionGuard.checkView(context, scopedStoreName);
        boolean includeUnpublished = "ADMIN".equalsIgnoreCase(context.getRoleCode());
        return ApiResponse.success(serviceFormTemplateService.preview(templateId, scopedStoreName, resolveScopedStoreId(context), includeUnpublished));
    }

    @PostMapping("/templates/save-draft")
    public ApiResponse<ServiceFormTemplateDtos.TemplateResponse> saveTemplateDraft(
            @RequestBody ServiceFormTemplateDtos.SaveTemplateRequest requestBody,
            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        serviceFormTemplatePermissionGuard.checkTemplateManage(context);
        return ApiResponse.success(serviceFormTemplateService.saveTemplateDraft(requestBody, context));
    }

    @PostMapping("/templates/publish")
    public ApiResponse<ServiceFormTemplateDtos.TemplateResponse> publishTemplate(
            @RequestBody ServiceFormTemplateDtos.TemplateStatusRequest requestBody,
            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        serviceFormTemplatePermissionGuard.checkTemplateManage(context);
        return ApiResponse.success(serviceFormTemplateService.publishTemplate(requestBody, context));
    }

    @PostMapping("/templates/disable")
    public ApiResponse<ServiceFormTemplateDtos.TemplateResponse> disableTemplate(
            @RequestBody ServiceFormTemplateDtos.TemplateStatusRequest requestBody,
            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        serviceFormTemplatePermissionGuard.checkTemplateManage(context);
        return ApiResponse.success(serviceFormTemplateService.disableTemplate(requestBody, context));
    }

    @PostMapping("/bindings/save")
    public ApiResponse<ServiceFormTemplateDtos.BindingResponse> saveBinding(
            @RequestBody ServiceFormTemplateDtos.SaveBindingRequest requestBody,
            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        String scopedStoreName = resolveScopedStoreName(context, requestBody == null ? null : requestBody.getStoreName());
        serviceFormTemplatePermissionGuard.checkBindingUpdate(context, scopedStoreName);
        if (requestBody != null && StringUtils.hasText(scopedStoreName)) {
            requestBody.setStoreName(scopedStoreName);
            if ("STORE".equalsIgnoreCase(context.getDataScope()) && context.getCurrentStoreId() != null) {
                requestBody.setStoreId(context.getCurrentStoreId());
            }
        }
        return ApiResponse.success(serviceFormTemplateService.saveBinding(requestBody, context));
    }

    @PostMapping("/bindings/disable")
    public ApiResponse<ServiceFormTemplateDtos.BindingResponse> disableBinding(
            @RequestBody ServiceFormTemplateDtos.BindingStatusRequest requestBody,
            HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        serviceFormTemplatePermissionGuard.checkBindingUpdate(context, null);
        return ApiResponse.success(serviceFormTemplateService.disableBinding(requestBody, context));
    }

    private String resolveScopedStoreName(PermissionRequestContext context, String requestedStoreName) {
        if (context != null && "STORE".equalsIgnoreCase(context.getDataScope())
                && StringUtils.hasText(context.getCurrentStoreName())) {
            return context.getCurrentStoreName();
        }
        return StringUtils.hasText(requestedStoreName) ? requestedStoreName.trim() : null;
    }

    private Long resolveScopedStoreId(PermissionRequestContext context) {
        if (context != null && "STORE".equalsIgnoreCase(context.getDataScope())) {
            return context.getCurrentStoreId();
        }
        return null;
    }
}
