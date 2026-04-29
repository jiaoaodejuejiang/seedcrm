package com.seedcrm.crm.planorder.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.planorder.dto.ServiceFormTemplateDtos;
import com.seedcrm.crm.planorder.service.ServiceFormTemplateService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ServiceFormTemplateServiceImpl implements ServiceFormTemplateService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public ServiceFormTemplateServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ServiceFormTemplateDtos.TemplateResponse> listTemplates() {
        return jdbcTemplate.query("""
                SELECT id, source_template_id, template_code, template_name, title, industry, layout_mode,
                       config_json, recommended, enabled, status, description, create_time, update_time, published_time
                FROM plan_order_service_form_template
                ORDER BY FIELD(status, 'DRAFT', 'PUBLISHED', 'DISABLED', 'ARCHIVED'), recommended DESC, id DESC
                """, (rs, rowNum) -> mapTemplate(rs));
    }

    @Override
    public List<ServiceFormTemplateDtos.BindingResponse> listBindings(String storeName, Long storeId) {
        if (storeId != null && storeId > 0 && StringUtils.hasText(storeName)) {
            return jdbcTemplate.query(bindingSelectSql() + """
                            WHERE (b.store_id = ? OR (b.store_id IS NULL AND b.store_name = ?))
                            ORDER BY b.id DESC
                            """,
                    (rs, rowNum) -> mapBinding(rs), storeId, storeName.trim());
        }
        if (storeId != null && storeId > 0) {
            return jdbcTemplate.query(bindingSelectSql() + " WHERE b.store_id = ? ORDER BY b.id DESC",
                    (rs, rowNum) -> mapBinding(rs), storeId);
        }
        if (StringUtils.hasText(storeName)) {
            return jdbcTemplate.query(bindingSelectSql() + " WHERE b.store_name = ? ORDER BY b.id DESC",
                    (rs, rowNum) -> mapBinding(rs), storeName.trim());
        }
        return jdbcTemplate.query(bindingSelectSql() + " ORDER BY b.id DESC", (rs, rowNum) -> mapBinding(rs));
    }

    @Override
    @Transactional
    public ServiceFormTemplateDtos.TemplateResponse saveTemplateDraft(ServiceFormTemplateDtos.SaveTemplateRequest request,
                                                                      PermissionRequestContext context) {
        validateTemplateRequest(request);
        ServiceFormTemplateDtos.TemplateResponse existing = request.getId() == null ? null : getTemplate(request.getId());
        LocalDateTime now = LocalDateTime.now();
        Long targetId = request.getId();
        if (existing == null) {
            jdbcTemplate.update("""
                    INSERT INTO plan_order_service_form_template(
                        template_code, template_name, title, industry, layout_mode, config_json,
                        recommended, enabled, status, description, create_time, update_time
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'DRAFT', ?, ?, ?)
                    """, normalizeCode(request.getTemplateCode()), request.getTemplateName().trim(), request.getTitle().trim(),
                    trimToNull(request.getIndustry()), normalizeLayoutMode(request.getLayoutMode()), normalizeConfigJson(request.getConfigJson()),
                    request.getRecommended() == null ? 0 : request.getRecommended(), request.getDescription(), now, now);
            targetId = latestTemplateId(normalizeCode(request.getTemplateCode()), STATUS_DRAFT);
        } else if (STATUS_PUBLISHED.equalsIgnoreCase(existing.getStatus())) {
            jdbcTemplate.update("""
                    INSERT INTO plan_order_service_form_template(
                        source_template_id, template_code, template_name, title, industry, layout_mode, config_json,
                        recommended, enabled, status, description, create_time, update_time
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'DRAFT', ?, ?, ?)
                    """, existing.getId(), normalizeCode(request.getTemplateCode()), request.getTemplateName().trim(),
                    request.getTitle().trim(), trimToNull(request.getIndustry()), normalizeLayoutMode(request.getLayoutMode()),
                    normalizeConfigJson(request.getConfigJson()), request.getRecommended() == null ? 0 : request.getRecommended(),
                    request.getDescription(), now, now);
            targetId = latestTemplateId(normalizeCode(request.getTemplateCode()), STATUS_DRAFT);
        } else {
            jdbcTemplate.update("""
                    UPDATE plan_order_service_form_template
                    SET template_code = ?, template_name = ?, title = ?, industry = ?, layout_mode = ?,
                        config_json = ?, recommended = ?, description = ?, update_time = ?
                    WHERE id = ?
                    """, normalizeCode(request.getTemplateCode()), request.getTemplateName().trim(), request.getTitle().trim(),
                    trimToNull(request.getIndustry()), normalizeLayoutMode(request.getLayoutMode()), normalizeConfigJson(request.getConfigJson()),
                    request.getRecommended() == null ? 0 : request.getRecommended(), request.getDescription(), now, existing.getId());
        }
        ServiceFormTemplateDtos.TemplateResponse saved = getTemplate(targetId);
        audit("TEMPLATE", saved.getId(), "SAVE_DRAFT", context, "保存服务单模板草稿", templateSnapshot(saved));
        return saved;
    }

    @Override
    @Transactional
    public ServiceFormTemplateDtos.TemplateResponse publishTemplate(ServiceFormTemplateDtos.TemplateStatusRequest request,
                                                                    PermissionRequestContext context) {
        Long templateId = request == null ? null : request.getTemplateId();
        ServiceFormTemplateDtos.TemplateResponse template = getTemplate(templateId);
        if (STATUS_PUBLISHED.equalsIgnoreCase(template.getStatus())) {
            return template;
        }
        if (!STATUS_DRAFT.equalsIgnoreCase(template.getStatus())) {
            throw new BusinessException("只有草稿模板可以发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (template.getSourceTemplateId() != null) {
            jdbcTemplate.update("""
                    UPDATE plan_order_service_form_template
                    SET status = 'ARCHIVED', enabled = 0, update_time = ?
                    WHERE id = ? AND status = 'PUBLISHED'
                    """, now, template.getSourceTemplateId());
        }
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_template
                SET status = 'ARCHIVED', enabled = 0, update_time = ?
                WHERE template_code = ?
                  AND status = 'PUBLISHED'
                  AND id <> ?
                """, now, template.getTemplateCode(), template.getId());
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_template
                SET status = 'PUBLISHED', enabled = 1, published_time = ?, update_time = ?
                WHERE id = ?
                """, now, now, template.getId());
        ServiceFormTemplateDtos.TemplateResponse published = getTemplate(template.getId());
        audit("TEMPLATE", published.getId(), "PUBLISH", context,
                appendReason("发布服务单模板", request == null ? null : request.getReason()), templateSnapshot(published));
        return published;
    }

    @Override
    @Transactional
    public ServiceFormTemplateDtos.TemplateResponse disableTemplate(ServiceFormTemplateDtos.TemplateStatusRequest request,
                                                                    PermissionRequestContext context) {
        ServiceFormTemplateDtos.TemplateResponse template = getTemplate(request == null ? null : request.getTemplateId());
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_template
                SET status = 'DISABLED', enabled = 0, update_time = ?
                WHERE id = ?
                """, LocalDateTime.now(), template.getId());
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_binding
                SET enabled = 0, update_time = ?
                WHERE template_id = ?
                  AND enabled = 1
                """, LocalDateTime.now(), template.getId());
        ServiceFormTemplateDtos.TemplateResponse disabled = getTemplate(template.getId());
        audit("TEMPLATE", disabled.getId(), "DISABLE", context,
                appendReason("停用服务单模板", request == null ? null : request.getReason()), templateSnapshot(disabled));
        return disabled;
    }

    @Override
    @Transactional
    public ServiceFormTemplateDtos.BindingResponse saveBinding(ServiceFormTemplateDtos.SaveBindingRequest request,
                                                               PermissionRequestContext context) {
        validateBindingRequest(request);
        ServiceFormTemplateDtos.TemplateResponse template = getTemplate(request.getTemplateId());
        if (!STATUS_PUBLISHED.equalsIgnoreCase(template.getStatus()) || template.getEnabled() == null || template.getEnabled() == 0) {
            throw new BusinessException("只能发布到已发布且启用的模板");
        }
        LocalDateTime now = LocalDateTime.now();
        Long resolvedStoreId = resolveBindingStoreId(request, context);
        if (request.getId() == null) {
            disableActiveBindingsForStore(now, request.getStoreName(), resolvedStoreId, null);
            jdbcTemplate.update("""
                    INSERT INTO plan_order_service_form_binding(
                        store_name, store_id, template_id, template_snapshot_json, effective_from,
                        allow_override, enabled, create_time, update_time
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                    """, request.getStoreName().trim(), resolvedStoreId, request.getTemplateId(),
                    templateSnapshot(template), trimToNull(request.getEffectiveFrom()),
                    request.getAllowOverride() == null ? 0 : request.getAllowOverride(), now, now);
            Long bindingId = latestBindingId(request.getStoreName());
            ServiceFormTemplateDtos.BindingResponse binding = getBinding(bindingId);
            audit("BINDING", binding.getId(), "SAVE_BINDING", context,
                    appendReason("发布模板到门店", request.getReason()), bindingSnapshot(binding));
            return binding;
        }
        ServiceFormTemplateDtos.BindingResponse existing = getBinding(request.getId());
        assertStoreScope(context, existing.getStoreName(), existing.getStoreId());
        if (!existing.getStoreName().trim().equalsIgnoreCase(request.getStoreName().trim())) {
            throw new BusinessException("不能通过编辑绑定变更门店，请停用后重新发布");
        }
        disableActiveBindingsForStore(now, request.getStoreName(), resolvedStoreId, existing.getId());
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_binding
                SET store_name = ?, store_id = ?, template_id = ?, template_snapshot_json = ?,
                    effective_from = ?, allow_override = ?, enabled = 1, update_time = ?
                WHERE id = ?
                """, request.getStoreName().trim(), resolvedStoreId, request.getTemplateId(),
                templateSnapshot(template), trimToNull(request.getEffectiveFrom()),
                request.getAllowOverride() == null ? 0 : request.getAllowOverride(), now, existing.getId());
        ServiceFormTemplateDtos.BindingResponse binding = getBinding(existing.getId());
        audit("BINDING", binding.getId(), "SAVE_BINDING", context,
                appendReason("更新门店模板绑定", request.getReason()), bindingSnapshot(binding));
        return binding;
    }

    @Override
    @Transactional
    public ServiceFormTemplateDtos.BindingResponse disableBinding(ServiceFormTemplateDtos.BindingStatusRequest request,
                                                                  PermissionRequestContext context) {
        ServiceFormTemplateDtos.BindingResponse binding = getBinding(request == null ? null : request.getBindingId());
        assertStoreScope(context, binding.getStoreName(), binding.getStoreId());
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_binding
                SET enabled = 0, update_time = ?
                WHERE id = ?
                """, LocalDateTime.now(), binding.getId());
        ServiceFormTemplateDtos.BindingResponse disabled = getBinding(binding.getId());
        audit("BINDING", disabled.getId(), "DISABLE_BINDING", context,
                appendReason("停用门店模板绑定", request == null ? null : request.getReason()), bindingSnapshot(disabled));
        return disabled;
    }

    @Override
    public ServiceFormTemplateDtos.PreviewResponse preview(Long templateId, String storeName, Long storeId, boolean includeUnpublished) {
        ServiceFormTemplateDtos.BindingResponse binding = null;
        ServiceFormTemplateDtos.TemplateResponse template = null;
        String message = "模板预览仅影响展示";
        if (templateId != null && templateId > 0) {
            template = getTemplate(templateId);
            if (!includeUnpublished && !isPublishedEnabled(template)) {
                throw new BusinessException("只能预览已发布且启用的服务单模板");
            }
        } else if (StringUtils.hasText(storeName)) {
            List<ServiceFormTemplateDtos.BindingResponse> bindings = listBindings(storeName, storeId).stream()
                    .filter(item -> item.getEnabled() == null || item.getEnabled() == 1)
                    .filter(this::isEffectiveBinding)
                    .toList();
            if (!bindings.isEmpty()) {
                binding = bindings.get(0);
                template = getTemplate(binding.getTemplateId());
                if (!isPublishedEnabled(template)) {
                    template = null;
                    message = "门店绑定模板不可用，已展示默认模板";
                }
            }
        }
        if (template == null) {
            template = listTemplates().stream()
                    .filter(this::isPublishedEnabled)
                    .findFirst()
                    .orElse(null);
        }
        ServiceFormTemplateDtos.PreviewResponse response = new ServiceFormTemplateDtos.PreviewResponse();
        response.setStoreName(storeName);
        response.setBinding(binding);
        response.setTemplate(template);
        response.setMessage(template == null ? "暂无可用服务单模板" : message);
        return response;
    }

    private String bindingSelectSql() {
        return """
                SELECT b.id, b.store_id, b.store_name, b.template_id, t.template_name, t.title AS template_title,
                       b.template_snapshot_json, b.effective_from, b.allow_override, b.enabled, b.create_time, b.update_time
                FROM plan_order_service_form_binding b
                LEFT JOIN plan_order_service_form_template t ON t.id = b.template_id
                """;
    }

    private void validateTemplateRequest(ServiceFormTemplateDtos.SaveTemplateRequest request) {
        if (request == null) {
            throw new BusinessException("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getTemplateCode())
                || !StringUtils.hasText(request.getTemplateName())
                || !StringUtils.hasText(request.getTitle())) {
            throw new BusinessException("模板编码、模板名称和表单标题不能为空");
        }
        if (StringUtils.hasText(request.getConfigJson())) {
            try {
                OBJECT_MAPPER.readTree(request.getConfigJson());
            } catch (Exception ex) {
                throw new BusinessException("模板配置 JSON 格式不正确");
            }
        }
    }

    private void validateBindingRequest(ServiceFormTemplateDtos.SaveBindingRequest request) {
        if (request == null || !StringUtils.hasText(request.getStoreName())) {
            throw new BusinessException("门店不能为空");
        }
        if (request.getTemplateId() == null || request.getTemplateId() <= 0) {
            throw new BusinessException("模板不能为空");
        }
    }

    private void disableActiveBindingsForStore(LocalDateTime now, String storeName, Long storeId, Long excludedBindingId) {
        String normalizedStoreName = storeName.trim();
        if (storeId != null && storeId > 0) {
            if (excludedBindingId != null) {
                jdbcTemplate.update("""
                        UPDATE plan_order_service_form_binding
                        SET enabled = 0, update_time = ?
                        WHERE enabled = 1
                          AND id <> ?
                          AND (store_id = ? OR (store_id IS NULL AND store_name = ?))
                        """, now, excludedBindingId, storeId, normalizedStoreName);
                return;
            }
            jdbcTemplate.update("""
                    UPDATE plan_order_service_form_binding
                    SET enabled = 0, update_time = ?
                    WHERE enabled = 1
                      AND (store_id = ? OR (store_id IS NULL AND store_name = ?))
                    """, now, storeId, normalizedStoreName);
            return;
        }
        if (excludedBindingId != null) {
            jdbcTemplate.update("""
                    UPDATE plan_order_service_form_binding
                    SET enabled = 0, update_time = ?
                    WHERE store_name = ?
                      AND id <> ?
                      AND enabled = 1
                    """, now, normalizedStoreName, excludedBindingId);
            return;
        }
        jdbcTemplate.update("""
                UPDATE plan_order_service_form_binding
                SET enabled = 0, update_time = ?
                WHERE store_name = ?
                  AND enabled = 1
                """, now, normalizedStoreName);
    }

    private ServiceFormTemplateDtos.TemplateResponse getTemplate(Long templateId) {
        if (templateId == null || templateId <= 0) {
            throw new BusinessException("templateId 不能为空");
        }
        List<ServiceFormTemplateDtos.TemplateResponse> rows = jdbcTemplate.query("""
                SELECT id, source_template_id, template_code, template_name, title, industry, layout_mode,
                       config_json, recommended, enabled, status, description, create_time, update_time, published_time
                FROM plan_order_service_form_template
                WHERE id = ?
                """, (rs, rowNum) -> mapTemplate(rs), templateId);
        if (rows.isEmpty()) {
            throw new BusinessException("服务单模板不存在");
        }
        return rows.get(0);
    }

    private ServiceFormTemplateDtos.BindingResponse getBinding(Long bindingId) {
        if (bindingId == null || bindingId <= 0) {
            throw new BusinessException("bindingId 不能为空");
        }
        List<ServiceFormTemplateDtos.BindingResponse> rows = jdbcTemplate.query(bindingSelectSql() + " WHERE b.id = ?",
                (rs, rowNum) -> mapBinding(rs), bindingId);
        if (rows.isEmpty()) {
            throw new BusinessException("门店模板绑定不存在");
        }
        return rows.get(0);
    }

    private Long latestTemplateId(String templateCode, String status) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM plan_order_service_form_template
                WHERE template_code = ? AND status = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, templateCode, status);
    }

    private Long latestBindingId(String storeName) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM plan_order_service_form_binding
                WHERE store_name = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, storeName);
    }

    private ServiceFormTemplateDtos.TemplateResponse mapTemplate(ResultSet rs) throws SQLException {
        ServiceFormTemplateDtos.TemplateResponse item = new ServiceFormTemplateDtos.TemplateResponse();
        item.setId(rs.getLong("id"));
        long sourceTemplateId = rs.getLong("source_template_id");
        item.setSourceTemplateId(rs.wasNull() ? null : sourceTemplateId);
        item.setTemplateCode(rs.getString("template_code"));
        item.setTemplateName(rs.getString("template_name"));
        item.setTitle(rs.getString("title"));
        item.setIndustry(rs.getString("industry"));
        item.setLayoutMode(rs.getString("layout_mode"));
        item.setConfigJson(rs.getString("config_json"));
        item.setRecommended(rs.getInt("recommended"));
        item.setEnabled(rs.getInt("enabled"));
        item.setStatus(rs.getString("status"));
        item.setDescription(rs.getString("description"));
        item.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        item.setUpdateTime(rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
        item.setPublishedTime(rs.getTimestamp("published_time") == null ? null : rs.getTimestamp("published_time").toLocalDateTime());
        return item;
    }

    private ServiceFormTemplateDtos.BindingResponse mapBinding(ResultSet rs) throws SQLException {
        ServiceFormTemplateDtos.BindingResponse item = new ServiceFormTemplateDtos.BindingResponse();
        item.setId(rs.getLong("id"));
        long storeId = rs.getLong("store_id");
        item.setStoreId(rs.wasNull() ? null : storeId);
        item.setStoreName(rs.getString("store_name"));
        item.setTemplateId(rs.getLong("template_id"));
        item.setTemplateName(rs.getString("template_name"));
        item.setTemplateTitle(rs.getString("template_title"));
        item.setTemplateSnapshotJson(rs.getString("template_snapshot_json"));
        item.setEffectiveFrom(rs.getString("effective_from"));
        item.setAllowOverride(rs.getInt("allow_override"));
        item.setEnabled(rs.getInt("enabled"));
        item.setCreateTime(rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime());
        item.setUpdateTime(rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime());
        return item;
    }

    private void audit(String targetType,
                       Long targetId,
                       String actionType,
                       PermissionRequestContext context,
                       String summary,
                       String snapshotJson) {
        jdbcTemplate.update("""
                INSERT INTO plan_order_service_form_template_audit_log(
                    target_type, target_id, action_type, actor_role_code, actor_user_id, summary, snapshot_json, create_time
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, targetType, targetId, actionType,
                context == null ? null : context.getRoleCode(),
                context == null ? null : context.getCurrentUserId(),
                summary,
                snapshotJson,
                LocalDateTime.now());
    }

    private String templateSnapshot(ServiceFormTemplateDtos.TemplateResponse template) {
        return "{"
                + "\"id\":" + template.getId()
                + ",\"templateCode\":\"" + escape(template.getTemplateCode()) + "\""
                + ",\"templateName\":\"" + escape(template.getTemplateName()) + "\""
                + ",\"title\":\"" + escape(template.getTitle()) + "\""
                + ",\"layoutMode\":\"" + escape(template.getLayoutMode()) + "\""
                + ",\"status\":\"" + escape(template.getStatus()) + "\""
                + "}";
    }

    private String bindingSnapshot(ServiceFormTemplateDtos.BindingResponse binding) {
        return "{"
                + "\"id\":" + binding.getId()
                + ",\"storeName\":\"" + escape(binding.getStoreName()) + "\""
                + ",\"templateId\":" + binding.getTemplateId()
                + ",\"templateName\":\"" + escape(binding.getTemplateName()) + "\""
                + ",\"enabled\":" + binding.getEnabled()
                + "}";
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLayoutMode(String value) {
        String mode = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "classic";
        if (!List.of("classic", "compact", "premium").contains(mode)) {
            return "classic";
        }
        return mode;
    }

    private String normalizeConfigJson(String value) {
        return StringUtils.hasText(value) ? value.trim() : "{\"sections\":[\"基础信息\",\"服务确认\",\"偏好与补充\",\"客户签名\"]}";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long resolveBindingStoreId(ServiceFormTemplateDtos.SaveBindingRequest request, PermissionRequestContext context) {
        if (context != null && "STORE".equalsIgnoreCase(context.getDataScope()) && context.getCurrentStoreId() != null) {
            return context.getCurrentStoreId();
        }
        return request == null ? null : request.getStoreId();
    }

    private boolean isEffectiveBinding(ServiceFormTemplateDtos.BindingResponse binding) {
        String effectiveFrom = binding == null ? null : binding.getEffectiveFrom();
        if (!StringUtils.hasText(effectiveFrom)) {
            return true;
        }
        try {
            return !LocalDate.parse(effectiveFrom.trim()).isAfter(LocalDate.now());
        } catch (DateTimeParseException ex) {
            return true;
        }
    }

    private boolean isPublishedEnabled(ServiceFormTemplateDtos.TemplateResponse template) {
        return template != null
                && STATUS_PUBLISHED.equalsIgnoreCase(template.getStatus())
                && (template.getEnabled() == null || template.getEnabled() == 1);
    }

    private String appendReason(String summary, String reason) {
        if (!StringUtils.hasText(reason)) {
            return summary;
        }
        return summary + "，原因：" + reason.trim();
    }

    private void assertStoreScope(PermissionRequestContext context, String storeName) {
        assertStoreScope(context, storeName, null);
    }

    private void assertStoreScope(PermissionRequestContext context, String storeName, Long storeId) {
        if (context == null || !"STORE".equalsIgnoreCase(context.getDataScope())) {
            return;
        }
        if (storeId != null && context.getCurrentStoreId() != null && !context.getCurrentStoreId().equals(storeId)) {
            throw new BusinessException("只能操作当前门店的服务单模板绑定");
        }
        if (!StringUtils.hasText(storeName)) {
            return;
        }
        String currentStoreName = context.getCurrentStoreName();
        if (!StringUtils.hasText(currentStoreName) || !currentStoreName.trim().equalsIgnoreCase(storeName.trim())) {
            throw new BusinessException("只能操作当前门店的服务单模板绑定");
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
