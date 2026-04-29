package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.mapper.SchedulerOutboxEventMapper;
import com.seedcrm.crm.scheduler.service.SchedulerOutboxService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class SchedulerOutboxServiceImpl implements SchedulerOutboxService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final int MAX_RETRY = 5;

    private final SchedulerOutboxEventMapper outboxEventMapper;
    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SchedulerOutboxServiceImpl(SchedulerOutboxEventMapper outboxEventMapper,
                                      IntegrationProviderConfigMapper providerConfigMapper,
                                      ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.providerConfigMapper = providerConfigMapper;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    @Override
    @Transactional
    public SchedulerOutboxEvent enqueueFulfillmentEvent(Order order, PlanOrder planOrder, String eventType) {
        if (order == null || planOrder == null || !StringUtils.hasText(order.getExternalPartnerCode())
                || !StringUtils.hasText(order.getExternalOrderId())) {
            return null;
        }
        String providerCode = normalize(firstNonBlank(order.getExternalPartnerCode(), "DISTRIBUTION"));
        String normalizedEventType = normalizeEventType(eventType);
        String eventKey = providerCode + ":" + normalizedEventType + ":" + order.getId() + ":" + planOrder.getId();
        SchedulerOutboxEvent existing = outboxEventMapper.selectOne(Wrappers.<SchedulerOutboxEvent>lambdaQuery()
                .eq(SchedulerOutboxEvent::getEventKey, eventKey)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        IntegrationProviderConfig provider = findProvider(providerCode);
        LocalDateTime now = LocalDateTime.now();
        SchedulerOutboxEvent event = new SchedulerOutboxEvent();
        event.setEventKey(eventKey);
        event.setEventType(normalizedEventType);
        event.setProviderCode(providerCode);
        event.setRelatedOrderId(order.getId());
        event.setRelatedPlanOrderId(planOrder.getId());
        event.setExternalPartnerCode(order.getExternalPartnerCode());
        event.setExternalOrderId(order.getExternalOrderId());
        event.setDestinationUrl(resolveDestinationUrl(provider));
        event.setPayload(buildPayload(order, planOrder, normalizedEventType));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        if (outboxEventMapper.insert(event) <= 0) {
            throw new BusinessException("failed to enqueue outbox event");
        }
        return event;
    }

    @Override
    public List<SchedulerOutboxEvent> list(String status) {
        return outboxEventMapper.selectList(Wrappers.<SchedulerOutboxEvent>lambdaQuery()
                .eq(StringUtils.hasText(status), SchedulerOutboxEvent::getStatus, normalize(status))
                .orderByDesc(SchedulerOutboxEvent::getCreatedAt)
                .orderByDesc(SchedulerOutboxEvent::getId)
                .last("LIMIT 100"));
    }

    @Override
    @Transactional
    public SchedulerOutboxEvent retry(Long id, PermissionRequestContext context) {
        SchedulerOutboxEvent event = getOrThrow(id);
        if (STATUS_SUCCESS.equals(normalize(event.getStatus()))) {
            throw new BusinessException("successful outbox event cannot be retried");
        }
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(event.getRetryCount() == null ? 1 : event.getRetryCount() + 1);
        event.setNextRetryTime(null);
        event.setLastError(null);
        event.setUpdatedAt(LocalDateTime.now());
        outboxEventMapper.updateById(event);
        return outboxEventMapper.selectById(id);
    }

    @Override
    public List<SchedulerOutboxEvent> processDue(int limit) {
        int batchSize = limit <= 0 ? 20 : Math.min(limit, 100);
        LocalDateTime now = LocalDateTime.now();
        List<SchedulerOutboxEvent> events = outboxEventMapper.selectList(Wrappers.<SchedulerOutboxEvent>lambdaQuery()
                .in(SchedulerOutboxEvent::getStatus, List.of(STATUS_PENDING, STATUS_FAILED))
                .and(wrapper -> wrapper.isNull(SchedulerOutboxEvent::getNextRetryTime)
                        .or()
                        .le(SchedulerOutboxEvent::getNextRetryTime, now))
                .orderByAsc(SchedulerOutboxEvent::getCreatedAt)
                .orderByAsc(SchedulerOutboxEvent::getId)
                .last("LIMIT " + batchSize));
        for (SchedulerOutboxEvent event : events) {
            processOne(event);
        }
        return events.stream().map(item -> outboxEventMapper.selectById(item.getId())).toList();
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 15000)
    public void processDueOutboxEvents() {
        processDue(20);
    }

    private void processOne(SchedulerOutboxEvent event) {
        LocalDateTime startedAt = LocalDateTime.now();
        if (!tryClaim(event, startedAt)) {
            return;
        }
        SchedulerOutboxEvent current = outboxEventMapper.selectById(event.getId());
        try {
            IntegrationProviderConfig provider = findProvider(current.getProviderCode());
            String response = push(provider, current);
            current.setStatus(STATUS_SUCCESS);
            current.setLastError(null);
            current.setLastResponse(trim(response, 2000));
            current.setSentAt(LocalDateTime.now());
            current.setNextRetryTime(null);
        } catch (Exception exception) {
            int retryCount = current.getRetryCount() == null ? 0 : current.getRetryCount();
            current.setStatus(retryCount >= MAX_RETRY ? STATUS_DEAD_LETTER : STATUS_FAILED);
            current.setLastError(trim(exception.getMessage(), 512));
            current.setNextRetryTime(retryCount >= MAX_RETRY ? null : LocalDateTime.now().plusMinutes(Math.min(retryCount + 1, 5)));
        }
        current.setUpdatedAt(LocalDateTime.now());
        outboxEventMapper.updateById(current);
    }

    private boolean tryClaim(SchedulerOutboxEvent event, LocalDateTime startedAt) {
        return outboxEventMapper.update(null, Wrappers.<SchedulerOutboxEvent>update()
                .eq("id", event.getId())
                .in("status", List.of(STATUS_PENDING, STATUS_FAILED))
                .and(wrapper -> wrapper.isNull("next_retry_time")
                        .or()
                        .le("next_retry_time", startedAt))
                .set("status", STATUS_PROCESSING)
                .set("retry_count", event.getRetryCount() == null ? 1 : event.getRetryCount() + 1)
                .set("updated_at", startedAt)) > 0;
    }

    private String push(IntegrationProviderConfig provider, SchedulerOutboxEvent event) {
        if (provider == null) {
            throw new BusinessException("distribution provider is not configured");
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            throw new BusinessException("distribution provider is disabled");
        }
        if (!"LIVE".equalsIgnoreCase(provider.getExecutionMode())) {
            return "{\"mode\":\"MOCK\",\"message\":\"outbox event simulated\"}";
        }
        String destinationUrl = firstNonBlank(event.getDestinationUrl(), resolveDestinationUrl(provider));
        if (!StringUtils.hasText(destinationUrl)) {
            throw new BusinessException("distribution fulfillment callback url is required in LIVE mode");
        }
        if (!StringUtils.hasText(provider.getClientSecret())) {
            throw new BusinessException("distribution provider secret is required for LIVE outbox push");
        }
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
        String nonce = UUID.randomUUID().toString();
        String idempotencyKey = event.getEventKey();
        String signature = hmacSha256(provider.getClientSecret(),
                timestamp + "|" + nonce + "|" + idempotencyKey + "|" + event.getPayload());
        return restClient.post()
                .uri(destinationUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Partner-Code", event.getProviderCode())
                .header("X-Idempotency-Key", idempotencyKey)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(event.getPayload())
                .retrieve()
                .body(String.class);
    }

    private IntegrationProviderConfig findProvider(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return null;
        }
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, normalize(providerCode))
                .last("LIMIT 1"));
    }

    private SchedulerOutboxEvent getOrThrow(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("outbox event id is required");
        }
        SchedulerOutboxEvent event = outboxEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException("outbox event not found");
        }
        return event;
    }

    private String buildPayload(Order order, PlanOrder planOrder, String eventType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("eventId", eventType + "_" + order.getId() + "_" + planOrder.getId());
            payload.put("occurredAt", LocalDateTime.now().toString());
            payload.put("partnerCode", firstNonBlank(order.getExternalPartnerCode(), "DISTRIBUTION"));
            payload.put("order", Map.of(
                    "externalOrderId", order.getExternalOrderId(),
                    "internalOrderId", order.getId(),
                    "orderStatus", "COMPLETED",
                    "usedAt", order.getCompleteTime() == null ? LocalDateTime.now().toString() : order.getCompleteTime().toString()));
            payload.put("planOrder", Map.of(
                    "planOrderId", planOrder.getId(),
                    "planOrderStatus", planOrder.getStatus(),
                    "finishedAt", planOrder.getFinishTime() == null ? LocalDateTime.now().toString() : planOrder.getFinishTime().toString()));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new BusinessException("failed to build outbox payload");
        }
    }

    private String resolveDestinationUrl(IntegrationProviderConfig provider) {
        if (provider == null) {
            return null;
        }
        return firstNonBlank(provider.getCallbackUrl(), joinUrl(provider.getBaseUrl(), provider.getEndpointPath()));
    }

    private String joinUrl(String baseUrl, String path) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(path)) {
            return null;
        }
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

    private String normalizeEventType(String eventType) {
        return StringUtils.hasText(eventType) ? eventType.trim().toLowerCase(Locale.ROOT) : "crm.order.used";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
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

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
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
            throw new BusinessException("distribution outbox signature generation failed");
        }
    }
}
