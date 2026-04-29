package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.enums.CustomerStatus;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.util.OrderNoGenerator;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventRequest;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionMemberPayload;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionOrderPayload;
import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.mapper.IntegrationCallbackEventLogMapper;
import com.seedcrm.crm.scheduler.mapper.IntegrationProviderConfigMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import com.seedcrm.crm.scheduler.service.DistributionExceptionService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DistributionEventIngestServiceImpl implements DistributionEventIngestService {

    private static final String PROVIDER_DISTRIBUTION = "DISTRIBUTION";
    private static final String SOURCE_DISTRIBUTION = "distribution";
    private static final String SOURCE_CHANNEL_DISTRIBUTOR = "DISTRIBUTOR";
    private static final Duration LIVE_SIGNATURE_TIME_WINDOW = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "distribution.order.paid",
            "distribution.order.cancelled",
            "distribution.order.refund_pending",
            "distribution.order.refunded",
            "distribution.promoter.synced",
            "distribution.member.updated");

    private final IntegrationProviderConfigMapper providerConfigMapper;
    private final IntegrationCallbackEventLogMapper eventLogMapper;
    private final DistributionEventLogWriter eventLogWriter;
    private final DistributionExceptionService distributionExceptionService;
    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    public DistributionEventIngestServiceImpl(IntegrationProviderConfigMapper providerConfigMapper,
                                              IntegrationCallbackEventLogMapper eventLogMapper,
                                              DistributionEventLogWriter eventLogWriter,
                                              DistributionExceptionService distributionExceptionService,
                                              CustomerMapper customerMapper,
                                              OrderMapper orderMapper,
                                              ObjectMapper objectMapper) {
        this.providerConfigMapper = providerConfigMapper;
        this.eventLogMapper = eventLogMapper;
        this.eventLogWriter = eventLogWriter;
        this.distributionExceptionService = distributionExceptionService;
        this.customerMapper = customerMapper;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public DistributionEventResponse ingest(JsonNode payload, HttpServletRequest request) {
        return ingestInternal(payload, RequestMeta.from(request));
    }

    @Override
    @Transactional
    public DistributionEventResponse replayFromScheduler(JsonNode payload, String partnerCode, String idempotencyKey) {
        return ingestInternal(payload, RequestMeta.schedulerReplay(partnerCode, idempotencyKey));
    }

    private DistributionEventResponse ingestInternal(JsonNode payload, RequestMeta request) {
        String rawPayload = payload == null ? "{}" : payload.toString();
        String traceId = UUID.randomUUID().toString();
        String idempotencyKey = firstNonBlank(header(request, "X-Idempotency-Key"), parameter(request, "idempotency_key"));
        DistributionEventRequest event = null;
        String partnerCode = normalizeUpper(firstNonBlank(header(request, "X-Partner-Code"), PROVIDER_DISTRIBUTION));
        IntegrationProviderConfig provider = null;

        try {
            event = parsePayload(payload);
            partnerCode = resolvePartnerCode(event, request);
            String eventType = normalizeLower(event.getEventType());
            validateEnvelope(event, eventType, idempotencyKey);
            validatePartnerCodeConsistency(event, partnerCode, request);

            provider = findProvider(partnerCode);
            validatePartner(provider, rawPayload, idempotencyKey, request);

            IntegrationCallbackEventLog duplicateLog = findDuplicateLog(partnerCode, event.getEventId(), idempotencyKey);
            if (duplicateLog != null && "SUCCESS".equalsIgnoreCase(duplicateLog.getProcessStatus())) {
                DistributionEventResponse response = response(traceId, "DUPLICATE",
                        duplicateLog.getRelatedCustomerId(), duplicateLog.getRelatedOrderId(),
                        "SUCCESS", "duplicate event ignored");
                insertLog(provider, partnerCode, event, request, rawPayload, traceId, idempotencyKey,
                        "DUPLICATE", "SUCCESS", "duplicate event ignored",
                        duplicateLog.getRelatedCustomerId(), duplicateLog.getRelatedOrderId(), null, null, null);
                return response;
            }

            validateNonceNotReplayed(provider, partnerCode, request);

            DistributionEventResponse response;
            if ("distribution.order.paid".equals(eventType)) {
                response = processPaidEvent(event, partnerCode, rawPayload, traceId);
            } else if (eventType.startsWith("distribution.order.")) {
                response = processOrderStatusEvent(event, partnerCode, rawPayload, traceId, eventType);
            } else {
                response = response(traceId, "LOGGED", null, null, "RECEIVED", "event logged only in V1");
            }

            insertLog(provider, partnerCode, event, request, rawPayload, traceId, idempotencyKey,
                    response.getIdempotencyResult(), response.getProcessStatus(), response.getMessage(),
                    response.getCustomerId(), response.getOrderId(), null, null, null);
            return response;
        } catch (RuntimeException exception) {
            insertFailureLog(provider, partnerCode, event, request, rawPayload, traceId, idempotencyKey, exception);
            throw exception;
        }
    }

    private DistributionEventResponse processPaidEvent(DistributionEventRequest event,
                                                       String partnerCode,
                                                       String rawPayload,
                                                       String traceId) {
        DistributionOrderPayload orderPayload = requireOrder(event);
        DistributionMemberPayload member = requireMember(event);
        String externalOrderId = requireText(orderPayload.getExternalOrderId(), "externalOrderId is required");
        String externalMemberId = requireText(member.getExternalMemberId(), "externalMemberId is required");
        String phone = requireText(member.getPhone(), "member.phone is required");

        Order existingOrder = findOrder(partnerCode, externalOrderId);
        if (existingOrder != null) {
            refreshExternalOrderSnapshot(existingOrder, event, rawPayload);
            orderMapper.updateById(existingOrder);
            return response(traceId, "EXISTING", existingOrder.getCustomerId(), existingOrder.getId(),
                    "SUCCESS", "external order already exists");
        }

        Customer customer = resolveCustomer(partnerCode, externalMemberId, phone);
        LocalDateTime paidAt = parseDateTime(orderPayload.getPaidAt(), LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        if (customer == null) {
            customer = new Customer();
            customer.setName(trimToNull(member.getName()));
            customer.setPhone(phone);
            customer.setSource(SOURCE_DISTRIBUTION);
            customer.setSourceChannel(SOURCE_CHANNEL_DISTRIBUTOR);
            customer.setExternalPartnerCode(partnerCode);
            customer.setExternalMemberId(externalMemberId);
            customer.setExternalMemberRole(trimToNull(member.getRole()));
            customer.setRawData(rawPayload);
            customer.setStatus(CustomerStatus.PAID.name());
            customer.setFirstOrderTime(paidAt);
            customer.setLastOrderTime(paidAt);
            customer.setCreateTime(now);
            customer.setUpdateTime(now);
            if (customerMapper.insert(customer) <= 0) {
                throw new BusinessException("failed to create distribution customer");
            }
        } else {
            boolean changed = false;
            if (!StringUtils.hasText(customer.getExternalPartnerCode())) {
                customer.setExternalPartnerCode(partnerCode);
                changed = true;
            }
            if (!StringUtils.hasText(customer.getExternalMemberId())) {
                customer.setExternalMemberId(externalMemberId);
                changed = true;
            }
            if (!StringUtils.hasText(customer.getExternalMemberRole()) && StringUtils.hasText(member.getRole())) {
                customer.setExternalMemberRole(member.getRole().trim());
                changed = true;
            }
            if (!StringUtils.hasText(customer.getSource())) {
                customer.setSource(SOURCE_DISTRIBUTION);
                changed = true;
            }
            if (!StringUtils.hasText(customer.getSourceChannel())) {
                customer.setSourceChannel(SOURCE_CHANNEL_DISTRIBUTOR);
                changed = true;
            }
            if (!StringUtils.hasText(customer.getRawData())) {
                customer.setRawData(rawPayload);
                changed = true;
            }
            customer.setLastOrderTime(maxDate(customer.getLastOrderTime(), paidAt));
            if (customer.getFirstOrderTime() == null) {
                customer.setFirstOrderTime(paidAt);
            }
            if (CustomerStatus.NEW.name().equalsIgnoreCase(customer.getStatus()) || !StringUtils.hasText(customer.getStatus())) {
                customer.setStatus(CustomerStatus.PAID.name());
            }
            customer.setUpdateTime(now);
            changed = true;
            if (changed && customerMapper.updateById(customer) <= 0) {
                throw new BusinessException("failed to update distribution customer");
            }
        }

        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.generate());
        order.setCustomerId(customer.getId());
        order.setSource(SOURCE_DISTRIBUTION);
        order.setSourceChannel(SOURCE_CHANNEL_DISTRIBUTOR);
        order.setExternalPartnerCode(partnerCode);
        order.setExternalOrderId(externalOrderId);
        order.setExternalTradeNo(trimToNull(orderPayload.getExternalTradeNo()));
        order.setExternalMemberId(externalMemberId);
        order.setExternalPromoterId(event.getPromoter() == null ? null : trimToNull(event.getPromoter().getExternalPromoterId()));
        order.setExternalStatus(firstNonBlank(orderPayload.getStatus(), "paid"));
        order.setRefundStatus(trimToNull(orderPayload.getRefundStatus()));
        order.setRawData(rawPayload);
        order.setType(resolveOrderType(orderPayload.getType()));
        order.setAmount(scaleMoneyFromCent(orderPayload.getAmount()));
        order.setDeposit(resolveDeposit(order));
        order.setStatus(OrderStatus.PAID_DEPOSIT.name());
        order.setRemark("External distribution paid order synced");
        order.setCreateTime(now);
        order.setUpdateTime(now);
        if (orderMapper.insert(order) <= 0) {
            throw new BusinessException("failed to create distribution order");
        }
        return response(traceId, "CREATED", customer.getId(), order.getId(), "SUCCESS",
                "distribution paid order created");
    }

    private DistributionEventResponse processOrderStatusEvent(DistributionEventRequest event,
                                                              String partnerCode,
                                                              String rawPayload,
                                                              String traceId,
                                                              String eventType) {
        DistributionOrderPayload orderPayload = requireOrder(event);
        String externalOrderId = requireText(orderPayload.getExternalOrderId(), "externalOrderId is required");
        Order order = findOrder(partnerCode, externalOrderId);
        if (order == null) {
            throw new BusinessException("external order does not exist for status event");
        }
        refreshExternalOrderSnapshot(order, event, rawPayload);
        if ("distribution.order.cancelled".equals(eventType) && canApplyExternalTerminalStatus(order)) {
            order.setStatus(OrderStatus.CANCELLED.name());
        }
        if ("distribution.order.refunded".equals(eventType) && canApplyExternalTerminalStatus(order)) {
            order.setStatus(OrderStatus.REFUNDED.name());
        }
        order.setUpdateTime(LocalDateTime.now());
        if (orderMapper.updateById(order) <= 0) {
            throw new BusinessException("failed to update distribution order status");
        }
        return response(traceId, "UPDATED", order.getCustomerId(), order.getId(), "SUCCESS",
                "external order status updated");
    }

    private void refreshExternalOrderSnapshot(Order order, DistributionEventRequest event, String rawPayload) {
        DistributionOrderPayload orderPayload = requireOrder(event);
        order.setExternalTradeNo(firstNonBlank(orderPayload.getExternalTradeNo(), order.getExternalTradeNo()));
        order.setExternalMemberId(firstNonBlank(
                event.getMember() == null ? null : event.getMember().getExternalMemberId(),
                order.getExternalMemberId()));
        order.setExternalPromoterId(firstNonBlank(
                event.getPromoter() == null ? null : event.getPromoter().getExternalPromoterId(),
                order.getExternalPromoterId()));
        order.setExternalStatus(firstNonBlank(orderPayload.getStatus(), normalizeLower(event.getEventType()), order.getExternalStatus()));
        order.setRefundStatus(firstNonBlank(orderPayload.getRefundStatus(), resolveRefundStatus(event.getEventType()), order.getRefundStatus()));
        order.setRawData(rawPayload);
        order.setUpdateTime(LocalDateTime.now());
    }

    private Customer resolveCustomer(String partnerCode, String externalMemberId, String phone) {
        Customer byExternalMember = customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getExternalPartnerCode, partnerCode)
                .eq(Customer::getExternalMemberId, externalMemberId)
                .last("LIMIT 1"));
        if (byExternalMember != null) {
            return byExternalMember;
        }
        return customerMapper.selectOne(Wrappers.<Customer>lambdaQuery()
                .eq(Customer::getPhone, phone)
                .last("LIMIT 1"));
    }

    private Order findOrder(String partnerCode, String externalOrderId) {
        if (!StringUtils.hasText(externalOrderId)) {
            return null;
        }
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .eq(Order::getExternalPartnerCode, partnerCode)
                .eq(Order::getExternalOrderId, externalOrderId)
                .last("LIMIT 1"));
    }

    private IntegrationCallbackEventLog findDuplicateLog(String partnerCode, String eventId, String idempotencyKey) {
        return eventLogMapper.selectOne(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                .eq(IntegrationCallbackEventLog::getProviderCode, partnerCode)
                .and(wrapper -> wrapper
                        .eq(IntegrationCallbackEventLog::getIdempotencyKey, idempotencyKey)
                        .or()
                        .eq(StringUtils.hasText(eventId), IntegrationCallbackEventLog::getEventId, eventId))
                .orderByDesc(IntegrationCallbackEventLog::getId)
                .last("LIMIT 1"));
    }

    private void insertFailureLog(IntegrationProviderConfig provider,
                                  String partnerCode,
                                  DistributionEventRequest event,
                                  RequestMeta request,
                                  String rawPayload,
                                  String traceId,
                                  String idempotencyKey,
                                  RuntimeException exception) {
        FailureLogMeta meta = classifyFailure(exception);
        try {
            insertLog(provider, partnerCode, event, request, rawPayload, traceId, idempotencyKey,
                    StringUtils.hasText(idempotencyKey) ? "FAILED" : "MISSING",
                    "FAILED",
                    safeMessage(exception),
                    null,
                    null,
                    meta.errorCode(),
                    safeMessage(exception),
                    meta.signatureStatus());
        } catch (RuntimeException ignored) {
            // Preserve the original business exception. The current transaction must still roll back.
        }
        try {
            distributionExceptionService.recordFailure(partnerCode, event, rawPayload, traceId, idempotencyKey,
                    meta.errorCode(), safeMessage(exception));
        } catch (RuntimeException ignored) {
            // Failure queue writes are best-effort and must not hide the original exception.
        }
    }

    private void insertLog(IntegrationProviderConfig provider,
                           String partnerCode,
                           DistributionEventRequest event,
                           RequestMeta request,
                           String rawPayload,
                           String traceId,
                           String idempotencyKey,
                           String idempotencyStatus,
                           String processStatus,
                           String processMessage,
                           Long customerId,
                           Long orderId,
                           String errorCode,
                           String errorMessage,
                           String signatureStatusOverride) {
        IntegrationCallbackEventLog log = new IntegrationCallbackEventLog();
        log.setProviderCode(partnerCode);
        log.setProviderId(provider == null ? null : provider.getId());
        log.setCallbackName("Distribution paid order event");
        log.setAppCode(partnerCode);
        log.setRequestMethod(request == null ? "POST" : request.getMethod());
        log.setCallbackPath(request == null ? "/open/distribution/events" : request.getRequestURI());
        log.setQueryString(request == null ? null : request.getQueryString());
        log.setRequestPayload(rawPayload);
        log.setEventType(event == null ? null : event.getEventType());
        log.setEventId(event == null ? null : event.getEventId());
        log.setIdempotencyKey(idempotencyKey);
        log.setIdempotencyStatus(idempotencyStatus);
        log.setTraceId(traceId);
        log.setSignatureMode("HMAC_SHA256");
        log.setSignatureValueMasked(mask(header(request, "X-Signature")));
        log.setSignatureStatus(firstNonBlank(signatureStatusOverride, resolveSignatureStatus(provider, request)));
        log.setTrustLevel(request != null && request.isTrustedSchedulerReplay()
                ? "INTERNAL_REPLAY"
                : ("LIVE".equalsIgnoreCase(provider == null ? null : provider.getExecutionMode()) ? "VERIFIED" : "MOCK"));
        log.setReceivedIp(request == null ? null : request.getRemoteAddr());
        log.setUserAgent(header(request, "User-Agent"));
        log.setTimestampValue(header(request, "X-Timestamp"));
        log.setNonce(header(request, "X-Nonce"));
        log.setBodyHash(sha256Hex(rawPayload));
        log.setProcessPolicy("DISTRIBUTION_EVENT_INGEST");
        log.setProcessStatus(processStatus);
        log.setProcessMessage(processMessage);
        log.setRelatedCustomerId(customerId);
        log.setRelatedOrderId(orderId);
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage);
        LocalDateTime now = LocalDateTime.now();
        log.setReceivedAt(now);
        log.setProcessedAt(now);
        log.setCreatedAt(now);
        eventLogWriter.write(log);
    }

    private void validateEnvelope(DistributionEventRequest event, String eventType, String idempotencyKey) {
        if (event == null) {
            throw new BusinessException("distribution event payload is required");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException("X-Idempotency-Key is required");
        }
        if (!StringUtils.hasText(event.getEventId())) {
            throw new BusinessException("eventId is required");
        }
        if (!ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new BusinessException("unsupported distribution event type");
        }
    }

    private void validatePartner(IntegrationProviderConfig provider,
                                 String rawPayload,
                                 String idempotencyKey,
                                 RequestMeta request) {
        if (provider != null && provider.getEnabled() != null && provider.getEnabled() == 0) {
            throw new BusinessException("distribution provider is disabled");
        }
        if (provider == null) {
            throw new BusinessException("distribution provider is not configured");
        }
        if (request != null && request.isTrustedSchedulerReplay()) {
            return;
        }
        if (!"LIVE".equalsIgnoreCase(provider.getExecutionMode())) {
            return;
        }
        String timestamp = header(request, "X-Timestamp");
        String nonce = header(request, "X-Nonce");
        String signature = header(request, "X-Signature");
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw new BusinessException("LIVE distribution event requires timestamp, nonce and signature");
        }
        validateTimestampWindow(timestamp);
        if (!StringUtils.hasText(provider.getClientSecret())) {
            throw new BusinessException("distribution provider secret is not configured");
        }
        String source = timestamp.trim() + "|" + nonce.trim() + "|" + idempotencyKey.trim() + "|" + rawPayload;
        String expected = hmacSha256(provider.getClientSecret(), source);
        if (!expected.equalsIgnoreCase(signature.trim())) {
            throw new BusinessException("distribution event signature invalid");
        }
    }

    private void validateNonceNotReplayed(IntegrationProviderConfig provider,
                                          String partnerCode,
                                          RequestMeta request) {
        if (provider == null
                || (request != null && request.isTrustedSchedulerReplay())
                || !"LIVE".equalsIgnoreCase(provider.getExecutionMode())) {
            return;
        }
        String nonce = header(request, "X-Nonce");
        if (!StringUtils.hasText(nonce)) {
            return;
        }
        IntegrationCallbackEventLog replayed = eventLogMapper.selectOne(Wrappers.<IntegrationCallbackEventLog>lambdaQuery()
                .eq(IntegrationCallbackEventLog::getProviderCode, partnerCode)
                .eq(IntegrationCallbackEventLog::getNonce, nonce.trim())
                .eq(IntegrationCallbackEventLog::getSignatureStatus, "VERIFIED")
                .orderByDesc(IntegrationCallbackEventLog::getId)
                .last("LIMIT 1"));
        if (replayed != null) {
            throw new BusinessException("distribution event nonce replayed");
        }
    }

    private DistributionEventRequest parsePayload(JsonNode payload) {
        try {
            return objectMapper.treeToValue(payload == null ? objectMapper.createObjectNode() : payload,
                    DistributionEventRequest.class);
        } catch (Exception exception) {
            throw new BusinessException("distribution event payload format invalid");
        }
    }

    private IntegrationProviderConfig findProvider(String partnerCode) {
        return providerConfigMapper.selectOne(Wrappers.<IntegrationProviderConfig>lambdaQuery()
                .eq(IntegrationProviderConfig::getProviderCode, partnerCode)
                .last("LIMIT 1"));
    }

    private String resolvePartnerCode(DistributionEventRequest event, RequestMeta request) {
        return normalizeUpper(firstNonBlank(
                header(request, "X-Partner-Code"),
                event == null ? null : event.getPartnerCode(),
                PROVIDER_DISTRIBUTION));
    }

    private void validatePartnerCodeConsistency(DistributionEventRequest event,
                                                String resolvedPartnerCode,
                                                RequestMeta request) {
        String headerPartner = normalizeUpper(header(request, "X-Partner-Code"));
        String bodyPartner = normalizeUpper(event == null ? null : event.getPartnerCode());
        if (StringUtils.hasText(headerPartner)
                && StringUtils.hasText(bodyPartner)
                && !headerPartner.equals(bodyPartner)) {
            throw new BusinessException("distribution partner code mismatch between header and payload");
        }
        if (!StringUtils.hasText(resolvedPartnerCode)) {
            throw new BusinessException("distribution partner code is required");
        }
    }

    private DistributionMemberPayload requireMember(DistributionEventRequest event) {
        if (event == null || event.getMember() == null) {
            throw new BusinessException("member payload is required");
        }
        return event.getMember();
    }

    private DistributionOrderPayload requireOrder(DistributionEventRequest event) {
        if (event == null || event.getOrder() == null) {
            throw new BusinessException("order payload is required");
        }
        return event.getOrder();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private int resolveOrderType(String type) {
        String normalized = normalizeLower(type);
        if ("deposit".equals(normalized)) {
            return OrderType.DEPOSIT.getCode();
        }
        return OrderType.COUPON.getCode();
    }

    private BigDecimal resolveDeposit(Order order) {
        if (order == null || order.getAmount() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return order.getType() != null && order.getType() == OrderType.DEPOSIT.getCode()
                ? order.getAmount()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleMoneyFromCent(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException("order.amount is required");
        }
        return amount.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private boolean canApplyExternalTerminalStatus(Order order) {
        String status = normalizeUpper(order == null ? null : order.getStatus());
        return "CREATED".equals(status) || "PAID_DEPOSIT".equals(status) || "APPOINTMENT".equals(status);
    }

    private String resolveRefundStatus(String eventType) {
        String normalized = normalizeLower(eventType);
        if (normalized.endsWith("refund_pending")) {
            return "refund_pending";
        }
        if (normalized.endsWith("refunded")) {
            return "refunded";
        }
        if (normalized.endsWith("cancelled")) {
            return "cancelled";
        }
        return null;
    }

    private DistributionEventResponse response(String traceId,
                                               String idempotencyResult,
                                               Long customerId,
                                               Long orderId,
                                               String processStatus,
                                               String message) {
        DistributionEventResponse response = new DistributionEventResponse();
        response.setTraceId(traceId);
        response.setIdempotencyResult(idempotencyResult);
        response.setCustomerId(customerId);
        response.setOrderId(orderId);
        response.setProcessStatus(processStatus);
        response.setMessage(message);
        return response;
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (Exception exception) {
                return fallback;
            }
        }
    }

    private LocalDateTime maxDate(LocalDateTime current, LocalDateTime candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
    }

    private String resolveSignatureStatus(IntegrationProviderConfig provider, RequestMeta request) {
        if (request != null && request.isTrustedSchedulerReplay()) {
            return "TRUSTED_REPLAY";
        }
        if (provider == null) {
            return "UNKNOWN";
        }
        if (!"LIVE".equalsIgnoreCase(provider.getExecutionMode())) {
            return "MOCK_SKIPPED";
        }
        return "VERIFIED";
    }

    private FailureLogMeta classifyFailure(RuntimeException exception) {
        String message = normalizeLower(safeMessage(exception));
        if (message.contains("nonce replayed")) {
            return new FailureLogMeta("NONCE_REPLAYED", "REPLAYED");
        }
        if (message.contains("timestamp expired")) {
            return new FailureLogMeta("TIMESTAMP_EXPIRED", "EXPIRED");
        }
        if (message.contains("timestamp invalid")) {
            return new FailureLogMeta("TIMESTAMP_INVALID", "FAILED");
        }
        if (message.contains("signature invalid")) {
            return new FailureLogMeta("SIGNATURE_INVALID", "FAILED");
        }
        if (message.contains("requires timestamp, nonce and signature")) {
            return new FailureLogMeta("SIGNATURE_MISSING", "MISSING");
        }
        if (message.contains("secret is not configured")) {
            return new FailureLogMeta("SIGNATURE_SECRET_MISSING", "MISSING");
        }
        if (message.contains("provider is not configured") || message.contains("provider is disabled")) {
            return new FailureLogMeta("PROVIDER_INVALID", null);
        }
        if (message.contains("partner code mismatch")) {
            return new FailureLogMeta("PARTNER_MISMATCH", null);
        }
        if (message.contains("idempotency") && message.contains("required")) {
            return new FailureLogMeta("IDEMPOTENCY_MISSING", null);
        }
        if (message.contains("payload") || message.contains("eventid") || message.contains("unsupported distribution event type")) {
            return new FailureLogMeta("PAYLOAD_INVALID", null);
        }
        return new FailureLogMeta("INGEST_FAILED", null);
    }

    private String safeMessage(RuntimeException exception) {
        return exception == null || !StringUtils.hasText(exception.getMessage())
                ? "distribution event ingest failed"
                : exception.getMessage();
    }

    private String header(RequestMeta request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private String parameter(RequestMeta request, String name) {
        return request == null ? null : request.getParameter(name);
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeLower(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private void validateTimestampWindow(String timestamp) {
        try {
            OffsetDateTime requestTime = OffsetDateTime.parse(timestamp.trim());
            Duration skew = Duration.between(requestTime.toInstant(), OffsetDateTime.now(ZoneOffset.UTC).toInstant()).abs();
            if (skew.compareTo(LIVE_SIGNATURE_TIME_WINDOW) > 0) {
                throw new BusinessException("distribution event timestamp expired");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("distribution event timestamp invalid");
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
            throw new BusinessException("sha256 generation failed");
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
            throw new BusinessException("signature generation failed");
        }
    }

    private static final class RequestMeta {

        private final HttpServletRequest servletRequest;
        private final Map<String, String> headers;
        private final Map<String, String> parameters;
        private final boolean trustedSchedulerReplay;
        private final String method;
        private final String requestUri;
        private final String queryString;
        private final String remoteAddr;

        private RequestMeta(HttpServletRequest servletRequest,
                            Map<String, String> headers,
                            Map<String, String> parameters,
                            boolean trustedSchedulerReplay,
                            String method,
                            String requestUri,
                            String queryString,
                            String remoteAddr) {
            this.servletRequest = servletRequest;
            this.headers = headers == null ? Map.of() : headers;
            this.parameters = parameters == null ? Map.of() : parameters;
            this.trustedSchedulerReplay = trustedSchedulerReplay;
            this.method = method;
            this.requestUri = requestUri;
            this.queryString = queryString;
            this.remoteAddr = remoteAddr;
        }

        static RequestMeta from(HttpServletRequest request) {
            return new RequestMeta(request, Map.of(), Map.of(), false,
                    request == null ? "POST" : request.getMethod(),
                    request == null ? "/open/distribution/events" : request.getRequestURI(),
                    request == null ? null : request.getQueryString(),
                    request == null ? null : request.getRemoteAddr());
        }

        static RequestMeta schedulerReplay(String partnerCode, String idempotencyKey) {
            Map<String, String> headers = new LinkedHashMap<>();
            if (StringUtils.hasText(partnerCode)) {
                headers.put("x-partner-code", partnerCode.trim());
            }
            if (StringUtils.hasText(idempotencyKey)) {
                headers.put("x-idempotency-key", idempotencyKey.trim());
            }
            headers.put("user-agent", "seedcrm-scheduler-replay");
            return new RequestMeta(null, headers, Map.of(), true,
                    "POST", "/scheduler/distribution/exceptions/replay", null, "127.0.0.1");
        }

        String getHeader(String name) {
            if (servletRequest != null) {
                return servletRequest.getHeader(name);
            }
            return headers.get(StringUtils.hasText(name) ? name.trim().toLowerCase(Locale.ROOT) : "");
        }

        String getParameter(String name) {
            if (servletRequest != null) {
                return servletRequest.getParameter(name);
            }
            return parameters.get(StringUtils.hasText(name) ? name.trim() : "");
        }

        String getMethod() {
            return method;
        }

        String getRequestURI() {
            return requestUri;
        }

        String getQueryString() {
            return queryString;
        }

        String getRemoteAddr() {
            return remoteAddr;
        }

        boolean isTrustedSchedulerReplay() {
            return trustedSchedulerReplay;
        }
    }

    private record FailureLogMeta(String errorCode, String signatureStatus) {
    }
}
