package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.dto.DistributionReconciliationDtos.DistributionReconciliationResult;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import com.seedcrm.crm.scheduler.service.DistributionReconciliationService;
import com.seedcrm.crm.scheduler.support.SchedulerRestClientFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class DistributionReconciliationServiceImpl implements DistributionReconciliationService {

    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final String SOURCE_DISTRIBUTION = "distribution";
    private static final String JOB_STATUS_CHECK = "STATUS_CHECK";
    private static final String JOB_RECONCILE_PULL = "RECONCILE_PULL";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final OrderMapper orderMapper;
    private final DistributionEventIngestService distributionEventIngestService;
    private final ObjectMapper objectMapper;
    private final RestClient restClientOverride;

    @Autowired
    public DistributionReconciliationServiceImpl(IntegrationProviderConfigMapper providerConfigMapper,
                                                 OrderMapper orderMapper,
                                                 DistributionEventIngestService distributionEventIngestService,
                                                 ObjectMapper objectMapper) {
        this(providerConfigMapper, orderMapper, distributionEventIngestService, objectMapper, null);
    }

    DistributionReconciliationServiceImpl(IntegrationProviderConfigMapper providerConfigMapper,
                                          OrderMapper orderMapper,
                                          DistributionEventIngestService distributionEventIngestService,
                                          ObjectMapper objectMapper,
                                          RestClient restClient) {
        this.providerConfigMapper = providerConfigMapper;
        this.orderMapper = orderMapper;
        this.distributionEventIngestService = distributionEventIngestService;
        this.objectMapper = objectMapper;
        this.restClientOverride = restClient;
    }

    @Override
    public List<DistributionReconciliationResult> checkOrderStatus(int limit) {
        IntegrationProviderConfig provider = requireDistributionProvider();
        return processLocalOrders(provider, JOB_STATUS_CHECK, limit, false, false);
    }

    @Override
    public List<DistributionReconciliationResult> pullReconciliation(int limit) {
        IntegrationProviderConfig provider = requireDistributionProvider();
        if (isLive(provider) && StringUtils.hasText(provider.getReconciliationPullPath())) {
            return pullLiveReconciliation(provider, limit);
        }
        return processLocalOrders(provider, JOB_RECONCILE_PULL, limit, false, false);
    }

    @Override
    public List<DistributionReconciliationResult> dryRunOrderStatus(int limit) {
        IntegrationProviderConfig provider = requireDistributionProvider();
        return processLocalOrders(provider, JOB_STATUS_CHECK, limit, false, true);
    }

    @Override
    public List<DistributionReconciliationResult> dryRunReconciliation(int limit) {
        IntegrationProviderConfig provider = requireDistributionProvider();
        if (isLive(provider)) {
            return List.of(result(null, provider.getProviderCode(), JOB_RECONCILE_PULL, "DRY_RUN", "PRECHECK",
                    null, null, null, "LIVE 对账预检通过：本次不会调用外部分销系统，不会写 Customer / Order / PlanOrder / Outbox"));
        }
        return processLocalOrders(provider, JOB_RECONCILE_PULL, limit, false, true);
    }

    private List<DistributionReconciliationResult> processLocalOrders(IntegrationProviderConfig provider,
                                                                      String jobType,
                                                                      int limit,
                                                                      boolean allowPaidReplay,
                                                                      boolean dryRun) {
        List<DistributionReconciliationResult> results = new ArrayList<>();
        for (Order order : findDistributionOrders(provider.getProviderCode(), limit)) {
            try {
                if (dryRun && isLive(provider)) {
                    results.add(result(order, provider.getProviderCode(), jobType, "DRY_RUN", "PRECHECK",
                            order.getExternalOrderId(), null, null,
                            "LIVE precheck only: no external call, no event replay, no core table write"));
                    continue;
                }
                ObjectNode event = isLive(provider)
                        ? buildEventFromLiveStatusQuery(provider, order, jobType, allowPaidReplay)
                        : buildEventFromMockOrder(order, provider, jobType, allowPaidReplay);
                if (event == null) {
                    results.add(result(order, provider.getProviderCode(), jobType, "NO_CHANGE", "SUCCESS",
                            null, null, null, "外部状态无变化，本次未重放事件"));
                    continue;
                }
                if (dryRun) {
                    String eventType = text(event, "eventType");
                    String externalOrderId = text(event, "order.externalOrderId");
                    results.add(result(order, provider.getProviderCode(), jobType, "WOULD_REPLAY", "PRECHECK",
                            externalOrderId, eventType,
                            buildIdempotencyKey(provider.getProviderCode(), jobType, eventType, externalOrderId),
                            "dry-run precheck: real run would replay an inbound event; this run wrote no core table"));
                    continue;
                }
                results.add(replayEvent(provider, jobType, order, event));
            } catch (RuntimeException exception) {
                results.add(result(order, provider.getProviderCode(), jobType, "FAILED", "FAILED",
                        null, null, null, exception.getMessage()));
            }
        }
        return results;
    }

    private List<DistributionReconciliationResult> pullLiveReconciliation(IntegrationProviderConfig provider,
                                                                          int limit) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("limit", normalizeLimit(limit));
        if (provider.getLastSyncTime() != null) {
            requestBody.put("lastSyncTime", provider.getLastSyncTime().toString());
        }
        requestBody.put("pullWindowMinutes", provider.getPullWindowMinutes() == null ? 60 : provider.getPullWindowMinutes());
        requestBody.put("overlapMinutes", provider.getOverlapMinutes() == null ? 10 : provider.getOverlapMinutes());
        JsonNode response = callLiveProvider(provider, provider.getReconciliationPullPath(), requestBody);
        List<JsonNode> rows = extractRows(response);
        List<DistributionReconciliationResult> results = new ArrayList<>();
        for (JsonNode row : rows.stream().limit(normalizeLimit(limit)).toList()) {
            ObjectNode event = buildEventFromExternalNode(provider, row, JOB_RECONCILE_PULL, true, null);
            if (event == null) {
                results.add(result(null, provider.getProviderCode(), JOB_RECONCILE_PULL, "NO_CHANGE", "SUCCESS",
                        text(row, "order.externalOrderId"), null, null, "外部对账记录未映射到需处理事件"));
                continue;
            }
            String externalOrderId = text(event, "order.externalOrderId");
            Order localOrder = findOrder(provider.getProviderCode(), externalOrderId);
            try {
                results.add(replayEvent(provider, JOB_RECONCILE_PULL, localOrder, event));
            } catch (RuntimeException exception) {
                results.add(result(localOrder, provider.getProviderCode(), JOB_RECONCILE_PULL, "FAILED", "FAILED",
                        externalOrderId, text(event, "eventType"), null, exception.getMessage()));
            }
        }
        return results;
    }

    private ObjectNode buildEventFromLiveStatusQuery(IntegrationProviderConfig provider,
                                                     Order order,
                                                     String jobType,
                                                     boolean allowPaidReplay) {
        String path = firstNonBlank(provider.getStatusQueryPath(), provider.getRefundQueryPath());
        if (!StringUtils.hasText(path)) {
            throw new BusinessException("distribution status query path is required in LIVE mode");
        }
        JsonNode response = callLiveProvider(provider, path, Map.of(
                "externalOrderId", order.getExternalOrderId(),
                "externalTradeNo", firstNonBlank(order.getExternalTradeNo(), ""),
                "partnerCode", firstNonBlank(order.getExternalPartnerCode(), provider.getProviderCode())));
        return buildEventFromExternalNode(provider, response, jobType, allowPaidReplay, order);
    }

    private ObjectNode buildEventFromMockOrder(Order order,
                                               IntegrationProviderConfig provider,
                                               String jobType,
                                               boolean allowPaidReplay) {
        JsonNode raw = parseJson(order.getRawData());
        String externalStatus = firstNonBlank(
                text(raw, "schedulerMockStatus"),
                text(raw, "scheduler.mockStatus"),
                text(raw, "mockStatus"),
                text(raw, "order.refundStatus"),
                order.getRefundStatus(),
                text(raw, "order.status"),
                order.getExternalStatus(),
                null);
        return buildEvent(provider, jobType, order, raw, externalStatus, allowPaidReplay);
    }

    private ObjectNode buildEventFromExternalNode(IntegrationProviderConfig provider,
                                                  JsonNode node,
                                                  String jobType,
                                                  boolean allowPaidReplay,
                                                  Order localOrder) {
        JsonNode actual = unwrapPayload(node);
        if (actual.isObject() && StringUtils.hasText(text(actual, "eventType"))) {
            ObjectNode event = ((ObjectNode) actual).deepCopy();
            ensureText(event, "partnerCode", provider.getProviderCode());
            ensureText(event, "eventId", buildEventId(jobType, text(event, "eventType"),
                    firstNonBlank(text(event, "order.externalOrderId"), localOrder == null ? null : localOrder.getExternalOrderId())));
            ensureText(event, "occurredAt", LocalDateTime.now().toString());
            return shouldReplay(text(event, "eventType"), allowPaidReplay) ? event : null;
        }
        String externalStatus = firstNonBlank(
                text(actual, "order.refundStatus"),
                text(actual, "refundStatus"),
                text(actual, "refund_status"),
                text(actual, "order.status"),
                text(actual, "status"),
                text(actual, "externalStatus"),
                text(actual, "order_status"),
                localOrder == null ? null : localOrder.getRefundStatus(),
                localOrder == null ? null : localOrder.getExternalStatus(),
                null);
        return buildEvent(provider, jobType, localOrder, actual, externalStatus, allowPaidReplay);
    }

    private ObjectNode buildEvent(IntegrationProviderConfig provider,
                                  String jobType,
                                  Order localOrder,
                                  JsonNode raw,
                                  String externalStatus,
                                  boolean allowPaidReplay) {
        String eventType = resolveEventType(provider, externalStatus);
        if (!shouldReplay(eventType, allowPaidReplay)) {
            return null;
        }
        String externalOrderId = firstNonBlank(
                text(raw, "order.externalOrderId"),
                text(raw, "externalOrderId"),
                text(raw, "external_order_id"),
                text(raw, "order_id"),
                localOrder == null ? null : localOrder.getExternalOrderId());
        if (!StringUtils.hasText(externalOrderId)) {
            throw new BusinessException("externalOrderId is required for distribution reconciliation");
        }

        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventType", eventType);
        event.put("eventId", firstNonBlank(text(raw, "eventId"), text(raw, "event_id"),
                buildEventId(jobType, eventType, externalOrderId)));
        event.put("partnerCode", firstNonBlank(
                text(raw, "partnerCode"),
                localOrder == null ? null : localOrder.getExternalPartnerCode(),
                provider.getProviderCode()));
        event.put("occurredAt", firstNonBlank(text(raw, "occurredAt"), LocalDateTime.now().toString()));

        ObjectNode order = event.putObject("order");
        order.put("externalOrderId", externalOrderId);
        putIfPresent(order, "externalTradeNo", firstNonBlank(
                text(raw, "order.externalTradeNo"),
                text(raw, "externalTradeNo"),
                text(raw, "external_trade_no"),
                localOrder == null ? null : localOrder.getExternalTradeNo()));
        putIfPresent(order, "type", firstNonBlank(text(raw, "order.type"), text(raw, "type"), "coupon"));
        putIfPresent(order, "amount", amountValue(raw, localOrder));
        putIfPresent(order, "paidAt", firstNonBlank(text(raw, "order.paidAt"), text(raw, "paidAt")));
        putIfPresent(order, "status", normalizeExternalStatus(externalStatus));
        putIfPresent(order, "refundStatus", resolveRefundStatus(eventType, externalStatus));
        putIfPresent(order, "refundAmount", decimalText(raw, "order.refundAmount", "refundAmount", "refund_amount"));
        putIfPresent(order, "refundAt", firstNonBlank(text(raw, "order.refundAt"), text(raw, "refundAt"), text(raw, "refund_at")));

        ObjectNode member = event.putObject("member");
        putIfPresent(member, "externalMemberId", firstNonBlank(
                text(raw, "member.externalMemberId"),
                text(raw, "externalMemberId"),
                text(raw, "member_id"),
                localOrder == null ? null : localOrder.getExternalMemberId()));
        putIfPresent(member, "name", firstNonBlank(text(raw, "member.name"), text(raw, "memberName"), text(raw, "name")));
        putIfPresent(member, "phone", firstNonBlank(text(raw, "member.phone"), text(raw, "phone"), text(raw, "mobile")));
        putIfPresent(member, "role", firstNonBlank(text(raw, "member.role"), text(raw, "memberRole"), "member"));

        ObjectNode promoter = event.putObject("promoter");
        putIfPresent(promoter, "externalPromoterId", firstNonBlank(
                text(raw, "promoter.externalPromoterId"),
                text(raw, "externalPromoterId"),
                text(raw, "promoter_id"),
                localOrder == null ? null : localOrder.getExternalPromoterId()));
        putIfPresent(promoter, "role", firstNonBlank(text(raw, "promoter.role"), text(raw, "promoterRole")));

        ObjectNode rawData = event.putObject("rawData");
        rawData.put("source", "scheduler");
        rawData.put("jobType", jobType);
        rawData.set("externalPayload", raw == null || raw.isMissingNode() ? objectMapper.createObjectNode() : raw);
        return event;
    }

    private DistributionReconciliationResult replayEvent(IntegrationProviderConfig provider,
                                                         String jobType,
                                                         Order localOrder,
                                                         ObjectNode event) {
        String eventType = text(event, "eventType");
        String externalOrderId = text(event, "order.externalOrderId");
        String idempotencyKey = buildIdempotencyKey(provider.getProviderCode(), jobType, eventType, externalOrderId);
        DistributionEventResponse response = distributionEventIngestService.replayFromScheduler(
                event,
                provider.getProviderCode(),
                idempotencyKey);
        return result(localOrder, provider.getProviderCode(), jobType, "REPLAYED", response.getProcessStatus(),
                externalOrderId, eventType, idempotencyKey, response.getMessage());
    }

    private JsonNode callLiveProvider(IntegrationProviderConfig provider, String path, Map<String, Object> payload) {
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            throw new BusinessException("distribution provider baseUrl is required in LIVE mode");
        }
        if (!StringUtils.hasText(provider.getClientSecret())) {
            throw new BusinessException("distribution provider secret is required in LIVE mode");
        }
        String url = joinUrl(provider.getBaseUrl(), path);
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String nonce = UUID.randomUUID().toString();
        String traceId = "distribution-live-query-" + nonce;
        String idempotencyKey = normalizeUpper(provider.getProviderCode()) + ":LIVE_QUERY:"
                + normalizeLower(path).replace("/", "_") + ":" + nonce;
        String requestBody = serializeJson(payload);
        String signature = hmacSha256(provider.getClientSecret(),
                timestamp + "|" + nonce + "|" + idempotencyKey + "|" + requestBody);
        try {
            String response = restClient(provider).post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Partner-Code", provider.getProviderCode())
                    .header("X-App-Id", firstNonBlank(provider.getAppId(), provider.getClientKey(), ""))
                    .header("X-Idempotency-Key", idempotencyKey)
                    .header("X-Trace-Id", traceId)
                    .header("X-Timestamp", timestamp)
                    .header("X-Nonce", nonce)
                    .header("X-Signature", signature)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseJson(response);
        } catch (RuntimeException exception) {
            throw new BusinessException("distribution live reconciliation call failed: " + exception.getMessage());
        }
    }

    private RestClient restClient(IntegrationProviderConfig provider) {
        return restClientOverride == null ? SchedulerRestClientFactory.build(provider) : restClientOverride;
    }

    private IntegrationProviderConfig requireDistributionProvider() {
        IntegrationProviderConfig provider = providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, PROVIDER_DISTRIBUTION)
                .last("LIMIT 1"));
        if (provider == null) {
            throw new BusinessException("distribution provider is not configured");
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            throw new BusinessException("distribution provider is disabled");
        }
        if (!StringUtils.hasText(provider.getProviderCode())) {
            provider.setProviderCode(PROVIDER_DISTRIBUTION);
        }
        return provider;
    }

    private List<Order> findDistributionOrders(String partnerCode, int limit) {
        return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(Order::getSource, SOURCE_DISTRIBUTION)
                        .or()
                        .eq(Order::getExternalPartnerCode, firstNonBlank(partnerCode, PROVIDER_DISTRIBUTION)))
                .isNotNull(Order::getExternalOrderId)
                .ne(Order::getExternalOrderId, "")
                .orderByDesc(Order::getUpdateTime)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId)
                .last("LIMIT " + normalizeLimit(limit)));
    }

    private Order findOrder(String partnerCode, String externalOrderId) {
        if (!StringUtils.hasText(externalOrderId)) {
            return null;
        }
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getExternalPartnerCode, firstNonBlank(partnerCode, PROVIDER_DISTRIBUTION))
                .eq(Order::getExternalOrderId, externalOrderId)
                .last("LIMIT 1"));
    }

    private List<JsonNode> extractRows(JsonNode response) {
        JsonNode actual = unwrapPayload(response);
        JsonNode rows = firstExisting(actual, "items", "records", "list", "orders", "data.items", "data.records", "data.list", "data.orders");
        if (rows instanceof ArrayNode arrayNode) {
            List<JsonNode> result = new ArrayList<>();
            arrayNode.forEach(result::add);
            return result;
        }
        if (actual instanceof ArrayNode arrayNode) {
            List<JsonNode> result = new ArrayList<>();
            arrayNode.forEach(result::add);
            return result;
        }
        return actual.isObject() && actual.size() > 0 ? List.of(actual) : List.of();
    }

    private JsonNode unwrapPayload(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        JsonNode data = node.path("data");
        if (data.isObject() && StringUtils.hasText(text(data, "eventType"))) {
            return data;
        }
        return node;
    }

    private JsonNode firstExisting(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                current = current.path(segment);
            }
            if (!current.isMissingNode() && !current.isNull()) {
                return current;
            }
        }
        return objectMapper.missingNode();
    }

    private boolean shouldReplay(String eventType, boolean allowPaidReplay) {
        if (!StringUtils.hasText(eventType)) {
            return false;
        }
        if ("distribution.order.paid".equals(normalizeLower(eventType))) {
            return allowPaidReplay;
        }
        return eventType.startsWith("distribution.order.");
    }

    private String resolveEventType(IntegrationProviderConfig provider, String status) {
        String normalizedStatus = normalizeExternalStatus(status);
        if (!StringUtils.hasText(normalizedStatus)) {
            return null;
        }
        Map<String, String> mapping = parseStatusMapping(provider == null ? null : provider.getStatusMapping());
        return firstNonBlank(mapping.get(normalizedStatus), switch (normalizedStatus) {
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

    private String resolveRefundStatus(String eventType, String externalStatus) {
        String normalizedEvent = normalizeLower(eventType);
        if ("distribution.order.refunded".equals(normalizedEvent)) {
            return "refunded";
        }
        if ("distribution.order.refund_pending".equals(normalizedEvent)) {
            return "refund_pending";
        }
        return normalizeExternalStatus(externalStatus);
    }

    private DistributionReconciliationResult result(Order order,
                                                    String partnerCode,
                                                    String jobType,
                                                    String action,
                                                    String status,
                                                    String externalOrderId,
                                                    String eventType,
                                                    String idempotencyKey,
                                                    String message) {
        DistributionReconciliationResult result = new DistributionReconciliationResult();
        result.setOrderId(order == null ? null : order.getId());
        result.setExternalOrderId(firstNonBlank(externalOrderId, order == null ? null : order.getExternalOrderId()));
        result.setPartnerCode(firstNonBlank(partnerCode, order == null ? null : order.getExternalPartnerCode(), PROVIDER_DISTRIBUTION));
        result.setJobType(jobType);
        result.setAction(action);
        result.setStatus(status);
        result.setEventType(eventType);
        result.setIdempotencyKey(idempotencyKey);
        result.setProcessStatus(status);
        result.setMessage(message);
        result.setCheckedAt(LocalDateTime.now());
        return result;
    }

    private void ensureText(ObjectNode node, String field, String value) {
        if (!StringUtils.hasText(text(node, field)) && StringUtils.hasText(value)) {
            node.put(field, value);
        }
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value);
        }
    }

    private void putIfPresent(ObjectNode node, String field, BigDecimal value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private BigDecimal amountValue(JsonNode raw, Order localOrder) {
        String value = decimalText(raw, "order.amount", "amount", "payAmount", "pay_amount");
        if (StringUtils.hasText(value)) {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (localOrder == null || localOrder.getAmount() == null) {
            return null;
        }
        return localOrder.getAmount().multiply(BigDecimal.valueOf(100));
    }

    private String decimalText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = nested(node, path);
            if (value.isNumber()) {
                return value.decimalValue().toPlainString();
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String text(JsonNode node, String path) {
        JsonNode value = nested(node, path);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return StringUtils.hasText(value.asText()) ? value.asText().trim() : null;
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return null;
    }

    private JsonNode nested(JsonNode node, String path) {
        if (node == null || !StringUtils.hasText(path)) {
            return objectMapper.missingNode();
        }
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private JsonNode parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String buildEventId(String jobType, String eventType, String externalOrderId) {
        return "scheduler_" + normalizeLower(firstNonBlank(jobType, "job")) + "_"
                + normalizeLower(firstNonBlank(eventType, "event")).replace('.', '_')
                + "_" + firstNonBlank(externalOrderId, "unknown");
    }

    private String buildIdempotencyKey(String providerCode, String jobType, String eventType, String externalOrderId) {
        return normalizeUpper(firstNonBlank(providerCode, PROVIDER_DISTRIBUTION)) + ":"
                + normalizeUpper(firstNonBlank(jobType, "JOB")) + ":"
                + normalizeLower(firstNonBlank(eventType, "event"))
                + ":" + firstNonBlank(externalOrderId, "unknown");
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.trim();
        String suffix = path.trim();
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base + suffix.substring(1);
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    private String serializeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception exception) {
            throw new BusinessException("distribution live request serialization failed");
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
            throw new BusinessException("distribution live request signature generation failed");
        }
    }

    private boolean isLive(IntegrationProviderConfig provider) {
        return "LIVE".equalsIgnoreCase(provider.getExecutionMode());
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
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

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeLower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
