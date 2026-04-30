package com.seedcrm.crm.scheduler.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse.DuplicateGroup;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse.DuplicateLogSample;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobAuditLog;
import com.seedcrm.crm.scheduler.entity.SchedulerJobLog;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SchedulerSensitiveDataMasker {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern TEXT_AMOUNT_PATTERN = Pattern.compile(
            "(?i)((?:amount|price|fee|money|金额|费用|价格)\\s*[:=：]?\\s*)(\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern JSON_STRING_SECRET_PATTERN = Pattern.compile(
            "(?i)(\"(?:auth_code|code|access_token|refresh_token|token|callback_token|signature|sign|msg_signature|client_secret|app_secret|corpsecret|encodingaeskey|encoding_aes_key|phone|mobile|tel|telephone)\"\\s*:\\s*\")(.*?)(\")");
    private static final Pattern QUERY_SECRET_PATTERN = Pattern.compile(
            "(?i)([?&](?:auth_code|code|access_token|refresh_token|token|callback_token|signature|sign|msg_signature|client_secret|app_secret|corpsecret|encodingaeskey|encoding_aes_key|phone|mobile|tel|telephone|amount|price|fee)=)([^&#\\s]+)");
    private static final Set<String> FULL_DATA_ROLES = Set.of("ADMIN", "INTEGRATION_ADMIN");
    private static final Set<String> SECRET_KEYS = Set.of(
            "auth_code",
            "code",
            "access_token",
            "refresh_token",
            "token",
            "callback_token",
            "idempotency_key",
            "idempotencykey",
            "signature",
            "sign",
            "msg_signature",
            "client_secret",
            "app_secret",
            "corpsecret",
            "encodingaeskey",
            "encoding_aes_key",
            "__header_authorization");
    private static final Set<String> PHONE_KEYS = Set.of(
            "phone",
            "mobile",
            "tel",
            "telephone",
            "contact_phone",
            "customer_phone",
            "member_phone",
            "memberphone",
            "buyer_phone",
            "buyerphone");
    private static final Set<String> AMOUNT_KEYS = Set.of(
            "amount",
            "money",
            "price",
            "fee",
            "total_amount",
            "pay_amount",
            "paid_amount",
            "refund_amount",
            "order_amount",
            "amount_cent",
            "amountcent",
            "amount_yuan",
            "amountyuan");
    private static final Set<String> SENSITIVE_ID_KEYS = Set.of(
            "external_member_id",
            "externalmemberid",
            "external_promoter_id",
            "externalpromoterid",
            "external_trade_no",
            "externaltradeno",
            "trade_no",
            "tradeno",
            "transaction_id",
            "transactionid");
    private static final Set<String> NAME_KEYS = Set.of(
            "name",
            "member_name",
            "membername",
            "customer_name",
            "customername",
            "buyer_name",
            "buyername");
    private static final Set<String> CONFLICT_VALUE_KEYS = Set.of(
            "existing_value",
            "existingvalue",
            "incoming_value",
            "incomingvalue");

    private final ObjectMapper objectMapper;

    public SchedulerSensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DistributionExceptionRecord> maskDistributionExceptions(List<DistributionExceptionRecord> records,
                                                                        PermissionRequestContext context) {
        if (records == null || canViewFullData(context)) {
            return records;
        }
        return records.stream().map(this::maskDistributionException).toList();
    }

    public List<IntegrationCallbackEventLog> maskCallbackLogs(List<IntegrationCallbackEventLog> logs,
                                                              PermissionRequestContext context) {
        if (logs == null || canViewFullData(context)) {
            return logs;
        }
        return logs.stream().map(this::maskCallbackLog).toList();
    }

    public List<SchedulerOutboxEvent> maskOutboxEvents(List<SchedulerOutboxEvent> events,
                                                       PermissionRequestContext context) {
        if (events == null || canViewFullData(context)) {
            return events;
        }
        return events.stream().map(this::maskOutboxEvent).toList();
    }

    public List<SchedulerJobLog> maskJobLogs(List<SchedulerJobLog> logs,
                                             PermissionRequestContext context) {
        if (logs == null || canViewFullData(context)) {
            return logs;
        }
        return logs.stream().map(this::maskJobLog).toList();
    }

    public List<SchedulerJobAuditLog> maskAuditLogs(List<SchedulerJobAuditLog> logs,
                                                    PermissionRequestContext context) {
        if (logs == null || canViewFullData(context)) {
            return logs;
        }
        return logs.stream().map(this::maskAuditLog).toList();
    }

    public SchedulerIdempotencyHealthResponse maskIdempotencyHealth(SchedulerIdempotencyHealthResponse response,
                                                                    PermissionRequestContext context) {
        if (response == null || canViewFullData(context)) {
            return response;
        }
        SchedulerIdempotencyHealthResponse target = new SchedulerIdempotencyHealthResponse();
        BeanUtils.copyProperties(response, target);
        target.setDuplicateGroups(maskDuplicateGroups(response.getDuplicateGroups()));
        return target;
    }

    public SchedulerOutboxEvent maskOutboxEvent(SchedulerOutboxEvent event,
                                                PermissionRequestContext context) {
        if (event == null || canViewFullData(context)) {
            return event;
        }
        return maskOutboxEvent(event);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> maskDryRunResult(Map<String, Object> result,
                                                PermissionRequestContext context) {
        if (result == null || canViewFullData(context)) {
            return result;
        }
        Object redacted = redactJsonValue(result);
        if (redacted instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private DistributionExceptionRecord maskDistributionException(DistributionExceptionRecord source) {
        if (source == null) {
            return null;
        }
        DistributionExceptionRecord target = new DistributionExceptionRecord();
        BeanUtils.copyProperties(source, target);
        target.setPhone(maskPhone(source.getPhone()));
        target.setConflictDetailJson(redactText(source.getConflictDetailJson()));
        target.setRawPayload(redactText(source.getRawPayload()));
        return target;
    }

    private IntegrationCallbackEventLog maskCallbackLog(IntegrationCallbackEventLog source) {
        if (source == null) {
            return null;
        }
        IntegrationCallbackEventLog target = new IntegrationCallbackEventLog();
        BeanUtils.copyProperties(source, target);
        target.setQueryString(redactText(source.getQueryString()));
        target.setRequestPayload(redactText(source.getRequestPayload()));
        target.setAuthCode(maskValue(source.getAuthCode()));
        target.setCallbackState(redactText(source.getCallbackState()));
        return target;
    }

    private SchedulerOutboxEvent maskOutboxEvent(SchedulerOutboxEvent source) {
        SchedulerOutboxEvent target = new SchedulerOutboxEvent();
        BeanUtils.copyProperties(source, target);
        target.setPayload(redactText(source.getPayload()));
        target.setDestinationUrl(redactText(source.getDestinationUrl()));
        target.setLastError(redactText(source.getLastError()));
        target.setLastResponse(redactText(source.getLastResponse()));
        return target;
    }

    private SchedulerJobLog maskJobLog(SchedulerJobLog source) {
        if (source == null) {
            return null;
        }
        SchedulerJobLog target = new SchedulerJobLog();
        BeanUtils.copyProperties(source, target);
        target.setPayload(redactText(source.getPayload()));
        target.setErrorMessage(redactText(source.getErrorMessage()));
        return target;
    }

    private SchedulerJobAuditLog maskAuditLog(SchedulerJobAuditLog source) {
        if (source == null) {
            return null;
        }
        SchedulerJobAuditLog target = new SchedulerJobAuditLog();
        BeanUtils.copyProperties(source, target);
        target.setSummary(redactText(source.getSummary()));
        target.setDetail(redactText(source.getDetail()));
        return target;
    }

    private List<DuplicateGroup> maskDuplicateGroups(List<DuplicateGroup> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(this::maskDuplicateGroup).toList();
    }

    private DuplicateGroup maskDuplicateGroup(DuplicateGroup source) {
        if (source == null) {
            return null;
        }
        DuplicateGroup target = new DuplicateGroup();
        BeanUtils.copyProperties(source, target);
        target.setDuplicateKey(maskValue(source.getDuplicateKey()));
        target.setSampleTraceIds(maskValues(source.getSampleTraceIds()));
        target.setLogSamples(maskDuplicateLogSamples(source.getLogSamples()));
        return target;
    }

    private List<DuplicateLogSample> maskDuplicateLogSamples(List<DuplicateLogSample> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(this::maskDuplicateLogSample).toList();
    }

    private DuplicateLogSample maskDuplicateLogSample(DuplicateLogSample source) {
        if (source == null) {
            return null;
        }
        DuplicateLogSample target = new DuplicateLogSample();
        BeanUtils.copyProperties(source, target);
        target.setTraceId(maskValue(source.getTraceId()));
        target.setBodyHash(maskValue(source.getBodyHash()));
        return target;
    }

    private List<String> maskValues(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(this::maskValue).toList();
    }

    private boolean canViewFullData(PermissionRequestContext context) {
        String roleCode = context == null ? null : context.getRoleCode();
        return StringUtils.hasText(roleCode) && FULL_DATA_ROLES.contains(roleCode.trim().toUpperCase(Locale.ROOT));
    }

    private String redactText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        Object parsed = parseJson(trimmed);
        if (parsed != null) {
            return toJson(redactJsonValue(parsed));
        }
        String masked = JSON_STRING_SECRET_PATTERN.matcher(trimmed).replaceAll("$1****$3");
        masked = QUERY_SECRET_PATTERN.matcher(masked).replaceAll("$1****");
        return maskAmounts(maskPhones(masked));
    }

    private Object parseJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Object>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object redactJsonValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nestedValue = entry.getValue();
                redacted.put(key, redactJsonEntry(key, nestedValue));
            }
            return redacted;
        }
        if (value instanceof List<?> listValue) {
            List<Object> redacted = new ArrayList<>();
            for (Object item : listValue) {
                redacted.add(redactJsonValue(item));
            }
            return redacted;
        }
        if (value instanceof String stringValue) {
            return redactText(stringValue);
        }
        return value;
    }

    private Object redactJsonEntry(String key, Object value) {
        String normalizedKey = normalizeKey(key);
        if (SECRET_KEYS.contains(normalizedKey) || normalizedKey.contains("secret")) {
            return maskValue(value == null ? null : String.valueOf(value));
        }
        if (PHONE_KEYS.contains(normalizedKey) || normalizedKey.endsWith("_phone") || normalizedKey.endsWith("phone")) {
            return maskPhone(value == null ? null : String.valueOf(value));
        }
        if (AMOUNT_KEYS.contains(normalizedKey) || normalizedKey.endsWith("_amount") || normalizedKey.endsWith("amount")) {
            return "****";
        }
        if (CONFLICT_VALUE_KEYS.contains(normalizedKey)) {
            return maskValue(value == null ? null : String.valueOf(value));
        }
        if (SENSITIVE_ID_KEYS.contains(normalizedKey) || NAME_KEYS.contains(normalizedKey)) {
            return maskValue(value == null ? null : String.valueOf(value));
        }
        return redactJsonValue(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String maskPhones(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        Matcher matcher = PHONE_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(maskPhone(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String maskAmounts(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        Matcher matcher = TEXT_AMOUNT_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + "****"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String maskPhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        Matcher matcher = PHONE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
        }
        return maskPhones(trimmed);
    }

    private String maskValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.contains("****")) {
            return trimmed;
        }
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }
}
