package com.seedcrm.crm.planorder.service;

import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.planorder.dto.ServiceFormTemplateDtos;
import java.util.List;

public interface ServiceFormTemplateService {

    List<ServiceFormTemplateDtos.TemplateResponse> listTemplates();

    List<ServiceFormTemplateDtos.BindingResponse> listBindings(String storeName, Long storeId);

    ServiceFormTemplateDtos.TemplateResponse saveTemplateDraft(ServiceFormTemplateDtos.SaveTemplateRequest request,
                                                               PermissionRequestContext context);

    ServiceFormTemplateDtos.TemplateResponse publishTemplate(ServiceFormTemplateDtos.TemplateStatusRequest request,
                                                            PermissionRequestContext context);

    ServiceFormTemplateDtos.TemplateResponse disableTemplate(ServiceFormTemplateDtos.TemplateStatusRequest request,
                                                            PermissionRequestContext context);

    ServiceFormTemplateDtos.BindingResponse saveBinding(ServiceFormTemplateDtos.SaveBindingRequest request,
                                                        PermissionRequestContext context);

    ServiceFormTemplateDtos.BindingResponse disableBinding(ServiceFormTemplateDtos.BindingStatusRequest request,
                                                          PermissionRequestContext context);

    ServiceFormTemplateDtos.PreviewResponse preview(Long templateId, String storeName, Long storeId, boolean includeUnpublished);
}
