package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.dto.SchedulerInterfaceDebugRequest;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DistributionEventDryRunService {

    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final Duration LIVE_SIGNATURE_TIME_WINDOW = Duration.ofMinutes(10);

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final IntegrationCallbackEventLogMapper eventLogMapper;
    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public DistributionEventDryRunService(IntegrationProviderConfigMapper providerConfigMapper,
                                          IntegrationCallbackEventLogMapper eventLogMapper,
                                          CustomerMapper customerMapper,
                                          OrderMapper orderMapper,
                                          ObjectMapper objectMapper) {
        this.providerConfigMapper = providerConfigMapper;
        this.eventLogMapper = eventLogMapper;
        this.customerMapper = customerMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> dryRun(SchedulerInterfaceDebugRequest request) {
        String rawPayload = StringUtils.hasText(request == null ? null : request.getPayload())
                ? request.getPayload().trim()
                : "{}";
        JsonNode payload = parsePayload(rawPayload);
        Map<String, Object> parameters = request == null || request.getParameters() == null
                ? Map.of()
                : request.getParameters();
        String providerCode = normalizeUpper(firstNonBlank(
                header(parameters, "X-Partner-Code"),
                text(payload, "partnerCode"),
                request == null ? null : request.getProviderCode(),
                PROVIDER_DISTRIBUTION));
        String mode = normalizeUpper(firstNonBlank(request == null ? null : request.getMode(), "MOCK"));
        IntegrationProviderConfig provider = findProvider(providerCode);
        String interfaceCode = normalizeUpper(request == null ? null : request.getInterfaceCode());
        if ("DISTRIBUTION_STATUS_CHECK".equals(interfaceCode)
                || "DISTRIBUTION_RECONCILE_PULL".equals(interfaceCode)) {
            return schedulerReconciliationPreview(interfaceCode, mode, providerCode, provider, payload);
        }

        String eventType = normalizeLower(text(payload, "eventType"));
        String eventId = text(payload, "eventId");
        String idempotencyKey = firstNonBlank(
                header(parameters, "X-Idempotency-Key"),
                stringValue(parameters.get("idempotency_key")),
                stringValue(parameters.get("idempotencyKey")));
        String externalMemberId = text(payload, "member.externalMemberId");
        String phone = text(payload, "member.phone");
        String externalOrderId = text(payload, "order.externalOrderId");

        Customer externalCustomer = findCustomerByExternal(providerCode, externalMemberId);
        Customer phoneCustomer = externalCustomer == null ? findCustomerByPhone(phone) : null;
        Customer matchedCustomer = externalCustomer == null ? phoneCustomer : externalCustomer;
        Order existingOrder = findOrder(providerCode, externalOrderId);
        IntegrationCallbackEventLog duplicateLog = findDuplicateLog(providerCode, eventId, idempotencyKey);
        IntegrationCallbackEventLog nonceLog = findNonceLog(providerCode, header(parameters, "X-Nonce"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dryRun", true);
        response.put("success", true);
        response.put("message", "分销已支付订单入站 dry-run 完成，本次调试不写入 Customer / Order / PlanOrder");
        response.put("provider", providerPreview(providerCode, provider, mode));
        response.put("envelope", envelopePreview(eventType, eventId, providerCode, idempotencyKey));
        response.put("signaturePreview", signaturePreview(mode, provider, rawPayload, idempotencyKey, parameters, nonceLog));
        response.put("fieldMapping", fieldMapping(payload));
        response.put("statusMapping", statusMapping(eventType));
        response.put("customerMatch", customerMatchPreview(matchedCustomer, externalCustomer, phoneCustomer, externalMemberId, phone));
        response.put("orderIdempotency", orderIdempotencyPreview(existingOrder, duplicateLog, externalOrderId, payload));
        response.put("validation", validationPreview(provider, eventType, eventId, idempotencyKey, externalMemberId, phone, externalOrderId, payload));
        response.put("willWrite", willWritePreview(eventType, matchedCustomer, existingOrder));
        response.put("rawDataPolicy", Map.of(
                "storedInFormalIngest", true,
                "dryRunStored", false,
                "display", "默认脱敏，仅授权角色可查看完整 raw_data"));
        return response;
    }

    private Map<String, Object> providerPreview(String providerCode, IntegrationProviderConfig provider, String mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerCode", providerCode);
        result.put("configured", provider != null);
        result.put("enabled", provider != null && (provider.getEnabled() == null || provider.getEnabled() == 1));
        result.put("executionMode", provider == null ? mode : provider.getExecutionMode());
        result.put("secretConfigured", provider != null && StringUtils.hasText(provider.getClientSecret()));
        result.put("endpointPath", provider == null ? "/open/distribution/events" : provider.getEndpointPath());
        result.put("statusQueryPath", provider == null ? null : provider.getStatusQueryPath());
        result.put("reconciliationPullPath", provider == null ? null : provider.getReconciliationPullPath());
        result.put("statusMapping", provider == null ? null : provider.getStatusMapping());
        return result;
    }

    private Map<String, Object> schedulerReconciliationPreview(String interfaceCode,
                                                               String mode,
                                                               String providerCode,
                                                               IntegrationProviderConfig provider,
                                                               JsonNode payload) {
        String externalOrderId = firstNonBlank(
                text(payload, "order.externalOrderId"),
                text(payload, "externalOrderId"),
                text(payload, "external_order_id"),
                text(payload, "order_id"));
        String externalStatus = firstNonBlank(
                text(payload, "order.refundStatus"),
                text(payload, "refundStatus"),
                text(payload, "refund_status"),
                text(payload, "order.status"),
                text(payload, "status"),
                text(payload, "externalStatus"));
        String mappedEventType = mapExternalStatusToEvent(provider, externalStatus);
        Order existingOrder = findOrder(providerCode, externalOrderId);
        boolean statusCheck = "DISTRIBUTION_STATUS_CHECK".equals(interfaceCode);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dryRun", true);
        response.put("success", true);
        response.put("message", statusCheck
                ? "分销状态回查 dry-run 完成，本次调试不调用外部接口、不写核心业务表"
                : "分销对账拉取 dry-run 完成，本次调试不调用外部接口、不写核心业务表");
        response.put("provider", providerPreview(providerCode, provider, mode));
        response.put("schedulerJob", Map.of(
                "jobCode", interfaceCode,
                "queueName", statusCheck ? "distribution-status-check" : "distribution-reconcile-pull",
                "controllerEndpoint", statusCheck
                        ? "/scheduler/distribution/status-check/process"
                        : "/scheduler/distribution/reconcile/process",
                "executionMode", provider == null ? mode : firstNonBlank(provider.getExecutionMode(), mode)));
        Map<String, Object> fieldMapping = new LinkedHashMap<>();
        fieldMapping.put("externalOrderId", externalOrderId);
        fieldMapping.put("externalStatus", externalStatus);
        fieldMapping.put("mappedEventType", mappedEventType);
        fieldMapping.put("localOrderMatched", existingOrder != null);
        fieldMapping.put("localOrderId", existingOrder == null ? null : existingOrder.getId());
        response.put("fieldMapping", fieldMapping);

        Map<String, Object> statusMappingPreview = new LinkedHashMap<>();
        statusMappingPreview.put("externalStatus", externalStatus);
        statusMappingPreview.put("configuredMapping", provider == null ? null : provider.getStatusMapping());
        statusMappingPreview.put("mappedEventType", mappedEventType);
        statusMappingPreview.put("paidStatusPolicy", statusCheck
                ? "状态回查遇到 paid 视为无变化，不反向创建订单"
                : "对账拉取可将 paid 记录转为入站事件，由统一入站服务决定是否创建 Customer + Order(paid)");
        response.put("statusMapping", statusMappingPreview);
        response.put("willExecute", Map.of(
                "willCallExternalInDryRun", false,
                "willCallExternalWhenLiveJobRuns", "LIVE".equals(provider == null ? mode : normalizeUpper(provider.getExecutionMode())),
                "directWriteCustomerOrderPlanOrder", false,
                "replayThrough", "DistributionEventIngestService.replayFromScheduler",
                "clueCreated", false,
                "planOrderCreated", false));
        response.put("validation", schedulerReconciliationValidation(provider, interfaceCode, externalOrderId, externalStatus, mappedEventType));
        response.put("rawDataPolicy", Map.of(
                "dryRunStored", false,
                "formalJobPolicy", "正式任务只通过分销事件重放写入日志和受控业务服务"));
        return response;
    }

    private java.util.List<Map<String, Object>> schedulerReconciliationValidation(IntegrationProviderConfig provider,
                                                                                  String interfaceCode,
                                                                                  String externalOrderId,
                                                                                  String externalStatus,
                                                                                  String mappedEventType) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        addValidation(items, provider != null, "PROVIDER_CONFIG", "ERROR", "未找到分销接口配置");
        addValidation(items, provider == null || provider.getEnabled() == null || provider.getEnabled() == 1,
                "PROVIDER_ENABLED", "ERROR", "分销接口配置已停用");
        if ("DISTRIBUTION_STATUS_CHECK".equals(interfaceCode)) {
            addValidation(items, StringUtils.hasText(externalOrderId), "EXTERNAL_ORDER_ID", "ERROR", "状态回查需要 externalOrderId");
        }
        addValidation(items, StringUtils.hasText(externalStatus), "EXTERNAL_STATUS", "WARN", "未提供外部状态时只能预览任务配置，无法预览事件映射");
        if (StringUtils.hasText(externalStatus)) {
            addValidation(items, StringUtils.hasText(mappedEventType), "STATUS_MAPPING", "WARN", "当前外部状态未映射到 distribution.order.* 事件");
        }
        if (items.isEmpty()) {
            items.add(Map.of("code", "READY", "level", "INFO", "message", "调度预检通过，可进入任务调度或接口联调"));
        }
        return items;
    }

    private Map<String, Object> envelopePreview(String eventType,
                                                String eventId,
                                                String providerCode,
                                                String idempotencyKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventType", eventType);
        result.put("eventId", eventId);
        result.put("partnerCode", providerCode);
        result.put("idempotencyKeyPresent", StringUtils.hasText(idempotencyKey));
        result.put("acceptedEvents", java.util.List.of(
                "distribution.order.paid",
                "distribution.order.cancelled",
                "distribution.order.refund_pending",
                "distribution.order.refunded",
                "distribution.promoter.synced",
                "distribution.member.updated"));
        return result;
    }

    private Map<String, Object> signaturePreview(String mode,
                                                 IntegrationProviderConfig provider,
                                                 String rawPayload,
                                                 String idempotencyKey,
                                                 Map<String, Object> parameters,
                                                 IntegrationCallbackEventLog nonceLog) {
        String effectiveMode = normalizeUpper(firstNonBlank(provider == null ? null : provider.getExecutionMode(), mode));
        String timestamp = header(parameters, "X-Timestamp");
        String nonce = header(parameters, "X-Nonce");
        String signature = header(parameters, "X-Signature");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", effectiveMode);
        result.put("algorithm", "HMAC_SHA256(timestamp|nonce|idempotencyKey|rawPayload)");
        result.put("required", "LIVE".equals(effectiveMode));
        result.put("timestampPresent", StringUtils.hasText(timestamp));
        result.put("noncePresent", StringUtils.hasText(nonce));
        result.put("signaturePresent", StringUtils.hasText(signature));
        result.put("nonceReplayed", nonceLog != null);
        if (!"LIVE".equals(effectiveMode)) {
            result.put("result", "MOCK_SKIPPED");
            result.put("message", "MOCK 模式只做结构预检，不强制验签");
            return result;
        }
        if (provider == null || !StringUtils.hasText(provider.getClientSecret())) {
            result.put("result", "SECRET_MISSING");
            result.put("message", "LIVE 模式需要先在分销接口配置 clientSecret");
            return result;
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            result.put("result", "HEADER_MISSING");
            result.put("message", "LIVE 模式需要 X-Timestamp、X-Nonce、X-Signature");
            return result;
        }
        String timestampResult = validateTimestamp(timestamp);
        if (!"OK".equals(timestampResult)) {
            result.put("result", timestampResult);
            result.put("message", "时间戳超出允许窗口或格式不正确");
            return result;
        }
        String expected = hmacSha256(provider.getClientSecret(), timestamp.trim() + "|" + nonce.trim() + "|"
                + String.valueOf(idempotencyKey).trim() + "|" + rawPayload);
        result.put("result", expected.equalsIgnoreCase(signature.trim()) ? "VERIFIED" : "SIGNATURE_INVALID");
        result.put("signatureMasked", mask(signature));
        result.put("expectedSignatureMasked", mask(expected));
        return result;
    }

    private Map<String, Object> fieldMapping(JsonNode payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("externalMemberId", text(payload, "member.externalMemberId"));
        member.put("name", text(payload, "member.name"));
        member.put("phone", text(payload, "member.phone"));
        member.put("role", text(payload, "member.role"));
        result.put("member", member);

        Map<String, Object> promoter = new LinkedHashMap<>();
        promoter.put("externalPromoterId", text(payload, "promoter.externalPromoterId"));
        promoter.put("role", text(payload, "promoter.role"));
        result.put("promoter", promoter);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("externalOrderId", text(payload, "order.externalOrderId"));
        order.put("externalTradeNo", text(payload, "order.externalTradeNo"));
        order.put("type", firstNonBlank(text(payload, "order.type"), "coupon"));
        order.put("amountCent", decimalText(payload, "order.amount"));
        order.put("amountYuan", amountYuan(payload));
        order.put("paidAt", text(payload, "order.paidAt"));
        order.put("storeCode", text(payload, "order.storeCode"));
        order.put("externalStatus", text(payload, "order.status"));
        order.put("refundStatus", text(payload, "order.refundStatus"));
        result.put("order", order);
        return result;
    }

    private Map<String, Object> statusMapping(String eventType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventType", eventType);
        if ("distribution.order.paid".equals(eventType)) {
            result.put("internalAction", "创建或匹配 Customer，并创建或更新 Order(paid)");
            result.put("targetOrderStatus", "PAID_DEPOSIT");
            result.put("createCustomerAllowed", true);
            result.put("createOrderAllowed", true);
            result.put("planOrderImpact", "不创建 PlanOrder，后续由顾客排档创建");
            return result;
        }
        if (eventType.startsWith("distribution.order.")) {
            result.put("internalAction", "仅更新已有订单的外部状态快照和履约影响");
            result.put("targetOrderStatus", "保持或按安全终态映射");
            result.put("createCustomerAllowed", false);
            result.put("createOrderAllowed", false);
            result.put("planOrderImpact", "不创建 PlanOrder");
            return result;
        }
        result.put("internalAction", "仅记录事件");
        result.put("targetOrderStatus", null);
        result.put("createCustomerAllowed", false);
        result.put("createOrderAllowed", false);
        result.put("planOrderImpact", "无");
        return result;
    }

    private Map<String, Object> customerMatchPreview(Customer matched,
                                                     Customer externalCustomer,
                                                     Customer phoneCustomer,
                                                     String externalMemberId,
                                                     String phone) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("externalMemberId", externalMemberId);
        result.put("phoneMasked", maskPhone(phone));
        result.put("matched", matched != null);
        result.put("matchType", externalCustomer != null ? "external_partner_code+external_member_id"
                : phoneCustomer != null ? "phone" : "none");
        result.put("customerId", matched == null ? null : matched.getId());
        result.put("nextAction", matched == null ? "正式入站会创建 Customer" : "正式入站会复用并更新 Customer 快照");
        return result;
    }

    private Map<String, Object> orderIdempotencyPreview(Order order,
                                                        IntegrationCallbackEventLog duplicateLog,
                                                        String externalOrderId,
                                                        JsonNode payload) {
        List<String> conflicts = detectExistingOrderConflicts(order, payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("externalOrderId", externalOrderId);
        result.put("orderExists", order != null);
        result.put("orderId", order == null ? null : order.getId());
        result.put("currentOrderStatus", order == null ? null : order.getStatus());
        result.put("eventDuplicate", duplicateLog != null && "SUCCESS".equalsIgnoreCase(duplicateLog.getProcessStatus()));
        result.put("conflicts", conflicts);
        if (duplicateLog != null && "SUCCESS".equalsIgnoreCase(duplicateLog.getProcessStatus())) {
            result.put("idempotencyResult", "DUPLICATE");
            result.put("nextAction", "duplicate event ignored");
        } else if (order == null) {
            result.put("idempotencyResult", "NEW_ORDER_AVAILABLE");
            result.put("nextAction", "formal ingest can create Customer + Order(paid)");
        } else if (!conflicts.isEmpty()) {
            result.put("idempotencyResult", "EXCEPTION_QUEUED");
            result.put("nextAction", "formal ingest will queue EXTERNAL_ORDER_CONFLICT and will not update core order fields");
        } else {
            result.put("idempotencyResult", "EXISTING_ORDER_UPDATE");
            result.put("nextAction", "formal ingest can refresh external order snapshot");
        }
        return result;
    }

    private List<String> detectExistingOrderConflicts(Order order, JsonNode payload) {
        List<String> conflicts = new ArrayList<>();
        if (order == null || payload == null) {
            return conflicts;
        }
        addTextConflict(conflicts, "externalTradeNo", order.getExternalTradeNo(), text(payload, "order.externalTradeNo"));
        addTextConflict(conflicts, "externalMemberId", order.getExternalMemberId(), text(payload, "member.externalMemberId"));

        String incomingType = text(payload, "order.type");
        Integer existingType = OrderType.normalizeCode(order.getType());
        Integer incomingTypeCode = resolveOrderType(incomingType);
        if (StringUtils.hasText(incomingType) && existingType != null && incomingTypeCode != null
                && !existingType.equals(incomingTypeCode)) {
            conflicts.add("type existing=" + OrderType.toApiValue(order.getType())
                    + " incoming=" + normalizeLower(incomingType));
        }

        BigDecimal incomingAmount = amountYuanValue(payload);
        if (incomingAmount != null && order.getAmount() != null
                && order.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(incomingAmount) != 0) {
            conflicts.add("amount existing=" + order.getAmount().setScale(2, RoundingMode.HALF_UP)
                    + " incoming=" + incomingAmount);
        }
        return conflicts;
    }

    private void addTextConflict(List<String> conflicts, String fieldName, String existingValue, String incomingValue) {
        if (StringUtils.hasText(existingValue)
                && StringUtils.hasText(incomingValue)
                && !existingValue.trim().equals(incomingValue.trim())) {
            conflicts.add(fieldName + " existing=" + existingValue.trim() + " incoming=" + incomingValue.trim());
        }
    }

    private java.util.List<Map<String, Object>> validationPreview(IntegrationProviderConfig provider,
                                                                  String eventType,
                                                                  String eventId,
                                                                  String idempotencyKey,
                                                                  String externalMemberId,
                                                                  String phone,
                                                                  String externalOrderId,
                                                                  JsonNode payload) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        addValidation(items, provider != null, "PROVIDER_CONFIG", "ERROR", "未找到分销接口配置");
        addValidation(items, StringUtils.hasText(eventType), "EVENT_TYPE", "ERROR", "eventType 不能为空");
        addValidation(items, StringUtils.hasText(eventId), "EVENT_ID", "ERROR", "eventId 不能为空");
        addValidation(items, StringUtils.hasText(idempotencyKey), "IDEMPOTENCY_KEY", "ERROR", "X-Idempotency-Key 不能为空");
        if ("distribution.order.paid".equals(eventType)) {
            addValidation(items, StringUtils.hasText(externalMemberId), "EXTERNAL_MEMBER_ID", "ERROR", "member.externalMemberId 不能为空");
            addValidation(items, StringUtils.hasText(phone), "MEMBER_PHONE", "ERROR", "member.phone 不能为空");
            addValidation(items, StringUtils.hasText(externalOrderId), "EXTERNAL_ORDER_ID", "ERROR", "order.externalOrderId 不能为空");
            addValidation(items, payload.at("/order/amount").isNumber(), "ORDER_AMOUNT", "ERROR", "order.amount 必须是分为单位的数字");
        }
        if (items.isEmpty()) {
            items.add(Map.of("code", "READY", "level", "INFO", "message", "结构预检通过，可进入正式入站联调"));
        }
        return items;
    }

    private Map<String, Object> willWritePreview(String eventType, Customer customer, Order order) {
        boolean paidEvent = "distribution.order.paid".equals(eventType);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clue", false);
        result.put("customer", paidEvent && customer == null);
        result.put("order", paidEvent && order == null);
        result.put("planOrder", false);
        result.put("note", "dry-run 不落库；正式入站也不会创建 Clue / PlanOrder");
        return result;
    }

    private void addValidation(java.util.List<Map<String, Object>> items,
                               boolean ok,
                               String code,
                               String level,
                               String message) {
        if (!ok) {
            items.add(Map.of("code", code, "level", level, "message", message));
        }
    }

    private IntegrationProviderConfig findProvider(String providerCode) {
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, providerCode)
                .last("LIMIT 1"));
    }

    private Customer findCustomerByExternal(String partnerCode, String externalMemberId) {
        if (!StringUtils.hasText(partnerCode) || !StringUtils.hasText(externalMemberId)) {
            return null;
        }
        return customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getExternalPartnerCode, partnerCode)
                .eq(Customer::getExternalMemberId, externalMemberId)
                .last("LIMIT 1"));
    }

    private Customer findCustomerByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getPhone, phone.trim())
                .last("LIMIT 1"));
    }

    private Order findOrder(String partnerCode, String externalOrderId) {
        if (!StringUtils.hasText(partnerCode) || !StringUtils.hasText(externalOrderId)) {
            return null;
        }
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getExternalPartnerCode, partnerCode)
                .eq(Order::getExternalOrderId, externalOrderId)
                .last("LIMIT 1"));
    }

    private IntegrationCallbackEventLog findDuplicateLog(String partnerCode, String eventId, String idempotencyKey) {
        if (!StringUtils.hasText(partnerCode) || (!StringUtils.hasText(eventId) && !StringUtils.hasText(idempotencyKey))) {
            return null;
        }
        return eventLogMapper.selectOne(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                .eq(IntegrationCallbackEventLog::getProviderCode, partnerCode)
                .and(wrapper -> wrapper
                        .eq(StringUtils.hasText(idempotencyKey), IntegrationCallbackEventLog::getIdempotencyKey, idempotencyKey)
                        .or()
                        .eq(StringUtils.hasText(eventId), IntegrationCallbackEventLog::getEventId, eventId))
                .orderByDesc(IntegrationCallbackEventLog::getId)
                .last("LIMIT 1"));
    }

    private IntegrationCallbackEventLog findNonceLog(String partnerCode, String nonce) {
        if (!StringUtils.hasText(partnerCode) || !StringUtils.hasText(nonce)) {
            return null;
        }
        return eventLogMapper.selectOne(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                .eq(IntegrationCallbackEventLog::getProviderCode, partnerCode)
                .eq(IntegrationCallbackEventLog::getNonce, nonce.trim())
                .eq(IntegrationCallbackEventLog::getSignatureStatus, "VERIFIED")
                .last("LIMIT 1"));
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(StringUtils.hasText(payload) ? payload : "{}");
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String header(Map<String, Object> parameters, String headerName) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        String normalized = normalizeKey(headerName);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (normalized.equals(key) || ("__header_" + normalized).equals(key)) {
                return stringValue(entry.getValue());
            }
        }
        return null;
    }

    private String text(JsonNode node, String path) {
        if (node == null || !StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current == null ? null : current.get(part);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isTextual() ? current.asText() : current.toString();
    }

    private String decimalText(JsonNode node, String path) {
        String value = text(node, path);
        return StringUtils.hasText(value) ? value : null;
    }

    private String amountYuan(JsonNode payload) {
        BigDecimal value = amountYuanValue(payload);
        return value == null ? null : value.toPlainString();
    }

    private BigDecimal amountYuanValue(JsonNode payload) {
        JsonNode node = payload.at("/order/amount");
        if (!node.isNumber()) {
            return null;
        }
        return node.decimalValue().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private Integer resolveOrderType(String type) {
        String normalized = normalizeLower(type);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if ("deposit".equals(normalized)) {
            return OrderType.DEPOSIT.getCode();
        }
        return OrderType.COUPON.getCode();
    }

    private String validateTimestamp(String timestamp) {
        try {
            OffsetDateTime requestTime = OffsetDateTime.parse(timestamp.trim());
            Duration skew = Duration.between(requestTime.toInstant(), OffsetDateTime.now(ZoneOffset.UTC).toInstant()).abs();
            return skew.compareTo(LIVE_SIGNATURE_TIME_WINDOW) > 0 ? "TIMESTAMP_EXPIRED" : "OK";
        } catch (Exception exception) {
            return "TIMESTAMP_INVALID";
        }
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.trim().length() < 7) {
            return phone;
        }
        String trimmed = phone.trim();
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String mapExternalStatusToEvent(IntegrationProviderConfig provider, String status) {
        String normalizedStatus = normalizeExternalStatus(status);
        if (!StringUtils.hasText(normalizedStatus)) {
            return null;
        }
        Map<String, String> configured = parseStatusMapping(provider == null ? null : provider.getStatusMapping());
        return firstNonBlank(configured.get(normalizedStatus), switch (normalizedStatus) {
            case "paid" -> "distribution.order.paid";
            case "cancelled" -> "distribution.order.cancelled";
            case "refund_pending" -> "distribution.order.refund_pending";
            case "refunded" -> "distribution.order.refunded";
            default -> null;
        });
    }

    private Map<String, String> parseStatusMapping(String value) {
        Map<String, String> mapping = new LinkedHashMap<>();
        if (!StringUtils.hasText(value)) {
            return mapping;
        }
        for (String item : value.split(",")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2 && StringUtils.hasText(pair[0]) && StringUtils.hasText(pair[1])) {
                mapping.put(normalizeExternalStatus(pair[0]), pair[1].trim().toLowerCase(Locale.ROOT));
            }
        }
        return mapping;
    }

    private String normalizeExternalStatus(String status) {
        String value = normalizeLower(status);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        value = value.replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "paid_deposit", "appointment", "arrived", "serving", "success", "pay_success", "paid_success" -> "paid";
            case "cancel", "canceled", "cancelled", "closed" -> "cancelled";
            case "refunding", "refund_auditing", "refund_pending", "refund_apply" -> "refund_pending";
            case "refund_success", "refund_finished", "refunded" -> "refunded";
            default -> value;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeLower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_");
    }

    @SuppressWarnings("unused")
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }
}
