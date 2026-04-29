package com.seedcrm.crm.planorder.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.planorder.dto.ServiceFormTemplateDtos;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ServiceFormTemplateServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ServiceFormTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ServiceFormTemplateServiceImpl(jdbcTemplate);
    }

    @Test
    void previewShouldRejectDraftTemplateForNonAdmin() {
        stubTemplateById(1L, template(1L, "DRAFT", 0));

        assertThatThrownBy(() -> service.preview(1L, null, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发布且启用");
    }

    @Test
    void previewShouldAllowDraftTemplateWhenAdminRequestsDirectTemplate() {
        stubTemplateById(1L, template(1L, "DRAFT", 0));

        ServiceFormTemplateDtos.PreviewResponse response = service.preview(1L, null, null, true);

        assertThat(response.getTemplate().getId()).isEqualTo(1L);
        assertThat(response.getTemplate().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void previewShouldFallbackWhenStoreBindingTemplateIsDisabled() {
        stubBindingsByStore("静安门店", List.of(binding(7L, "静安门店", 2L, 1)));
        stubTemplateById(2L, template(2L, "DISABLED", 0));
        stubTemplateList(List.of(template(3L, "PUBLISHED", 1)));

        ServiceFormTemplateDtos.PreviewResponse response = service.preview(null, "静安门店", null, false);

        assertThat(response.getBinding().getId()).isEqualTo(7L);
        assertThat(response.getTemplate().getId()).isEqualTo(3L);
        assertThat(response.getMessage()).contains("默认模板");
    }

    @Test
    void saveBindingShouldRejectUnpublishedTemplate() {
        stubTemplateById(2L, template(2L, "DRAFT", 0));
        ServiceFormTemplateDtos.SaveBindingRequest request = new ServiceFormTemplateDtos.SaveBindingRequest();
        request.setStoreName("静安门店");
        request.setTemplateId(2L);

        assertThatThrownBy(() -> service.saveBinding(request, context("ADMIN", "ALL", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发布且启用");
        verify(jdbcTemplate, never()).update(ArgumentMatchers.anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void saveDraftShouldRejectInvalidConfigJson() {
        ServiceFormTemplateDtos.SaveTemplateRequest request = new ServiceFormTemplateDtos.SaveTemplateRequest();
        request.setTemplateCode("BAD_JSON");
        request.setTemplateName("非法配置模板");
        request.setTitle("非法配置模板");
        request.setConfigJson("{bad");

        assertThatThrownBy(() -> service.saveTemplateDraft(request, context("ADMIN", "ALL", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON");
        verify(jdbcTemplate, never()).update(ArgumentMatchers.anyString(), ArgumentMatchers.<Object[]>any());
    }

    @Test
    void publishTemplateShouldArchiveOtherPublishedVersionsWithSameCode() {
        ServiceFormTemplateDtos.TemplateResponse draft = template(8L, "DRAFT", 0);
        ServiceFormTemplateDtos.TemplateResponse published = template(8L, "PUBLISHED", 1);
        stubTemplateById(8L, draft, published);
        ServiceFormTemplateDtos.TemplateStatusRequest request = new ServiceFormTemplateDtos.TemplateStatusRequest();
        request.setTemplateId(8L);

        ServiceFormTemplateDtos.TemplateResponse response = service.publishTemplate(request, context("ADMIN", "ALL", null));

        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
        verify(jdbcTemplate).update(
                ArgumentMatchers.<String>argThat(sql -> sql != null && sql.contains("WHERE template_code = ?")),
                ArgumentMatchers.any(),
                eq("TPL_8"),
                eq(8L));
    }

    @Test
    void disableTemplateShouldDisableActiveBindings() {
        ServiceFormTemplateDtos.TemplateResponse published = template(9L, "PUBLISHED", 1);
        ServiceFormTemplateDtos.TemplateResponse disabled = template(9L, "DISABLED", 0);
        stubTemplateById(9L, published, disabled);
        ServiceFormTemplateDtos.TemplateStatusRequest request = new ServiceFormTemplateDtos.TemplateStatusRequest();
        request.setTemplateId(9L);

        ServiceFormTemplateDtos.TemplateResponse response = service.disableTemplate(request, context("ADMIN", "ALL", null));

        assertThat(response.getStatus()).isEqualTo("DISABLED");
        verify(jdbcTemplate).update(
                ArgumentMatchers.<String>argThat(sql -> sql != null && sql.contains("UPDATE plan_order_service_form_binding")),
                ArgumentMatchers.any(),
                eq(9L));
    }

    private PermissionRequestContext context(String roleCode, String dataScope, String storeName) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setDataScope(dataScope);
        context.setCurrentStoreName(storeName);
        return context;
    }

    private ServiceFormTemplateDtos.TemplateResponse template(Long id, String status, Integer enabled) {
        ServiceFormTemplateDtos.TemplateResponse template = new ServiceFormTemplateDtos.TemplateResponse();
        template.setId(id);
        template.setTemplateCode("TPL_" + id);
        template.setTemplateName("服务单模板" + id);
        template.setTitle("服务确认单" + id);
        template.setLayoutMode("classic");
        template.setConfigJson("{\"sections\":[\"基础信息\",\"服务确认\",\"客户签名\"]}");
        template.setStatus(status);
        template.setEnabled(enabled);
        return template;
    }

    private ServiceFormTemplateDtos.BindingResponse binding(Long id, String storeName, Long templateId, Integer enabled) {
        ServiceFormTemplateDtos.BindingResponse binding = new ServiceFormTemplateDtos.BindingResponse();
        binding.setId(id);
        binding.setStoreName(storeName);
        binding.setTemplateId(templateId);
        binding.setTemplateName("服务单模板" + templateId);
        binding.setTemplateTitle("服务确认单" + templateId);
        binding.setEnabled(enabled);
        return binding;
    }

    private void stubTemplateById(Long id, ServiceFormTemplateDtos.TemplateResponse firstTemplate,
                                  ServiceFormTemplateDtos.TemplateResponse... nextTemplates) {
        @SuppressWarnings("unchecked")
        List<ServiceFormTemplateDtos.TemplateResponse>[] responses = new List[nextTemplates.length + 1];
        responses[0] = List.of(firstTemplate);
        for (int index = 0; index < nextTemplates.length; index += 1) {
            responses[index + 1] = List.of(nextTemplates[index]);
        }
        when(jdbcTemplate.query(
                ArgumentMatchers.<String>argThat(sql -> sql != null
                        && sql.contains("FROM plan_order_service_form_template")
                        && sql.contains("WHERE id = ?")),
                ArgumentMatchers.<RowMapper<ServiceFormTemplateDtos.TemplateResponse>>any(),
                eq(id)))
                .thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }

    private void stubTemplateList(List<ServiceFormTemplateDtos.TemplateResponse> templates) {
        when(jdbcTemplate.query(
                ArgumentMatchers.<String>argThat(sql -> sql != null
                        && sql.contains("FROM plan_order_service_form_template")
                        && sql.contains("ORDER BY FIELD")),
                ArgumentMatchers.<RowMapper<ServiceFormTemplateDtos.TemplateResponse>>any()))
                .thenReturn(templates);
    }

    private void stubBindingsByStore(String storeName, List<ServiceFormTemplateDtos.BindingResponse> bindings) {
        when(jdbcTemplate.query(
                ArgumentMatchers.<String>argThat(sql -> sql != null
                        && sql.contains("FROM plan_order_service_form_binding")
                        && sql.contains("WHERE b.store_name = ?")),
                ArgumentMatchers.<RowMapper<ServiceFormTemplateDtos.BindingResponse>>any(),
                eq(storeName)))
                .thenReturn(bindings);
    }
}
