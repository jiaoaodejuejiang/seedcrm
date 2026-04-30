package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventRequest;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.mapper.DistributionExceptionRecordMapper;
import com.seedcrm.crm.scheduler.service.DistributionExceptionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DistributionExceptionServiceImpl implements DistributionExceptionService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_RETRY_QUEUED = "RETRY_QUEUED";
    private static final String STATUS_HANDLED = "HANDLED";

    private final DistributionExceptionRecordMapper exceptionRecordMapper;
    private final ObjectMapper objectMapper;

    public DistributionExceptionServiceImpl(DistributionExceptionRecordMapper exceptionRecordMapper,
                                            ObjectMapper objectMapper) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String partnerCode,
                              DistributionEventRequest event,
                              String rawPayload,
                              String traceId,
                              String idempotencyKey,
                              String errorCode,
                              String errorMessage) {
        recordFailure(partnerCode, event, rawPayload, traceId, idempotencyKey, errorCode, errorMessage, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String partnerCode,
                              DistributionEventRequest event,
                              String rawPayload,
                              String traceId,
                              String idempotencyKey,
                              String errorCode,
                              String errorMessage,
                              Long relatedOrderId,
                              String relatedOrderNo) {
        recordFailure(partnerCode, event, rawPayload, traceId, idempotencyKey, errorCode, errorMessage,
                relatedOrderId, relatedOrderNo, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String partnerCode,
                              DistributionEventRequest event,
                              String rawPayload,
                              String traceId,
                              String idempotencyKey,
                              String errorCode,
                              String errorMessage,
                              Long relatedOrderId,
                              String relatedOrderNo,
                              String conflictDetailJson) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedPartnerCode = normalize(firstNonBlank(partnerCode, event == null ? null : event.getPartnerCode(), "DISTRIBUTION"));
        DistributionExceptionRecord record = findExisting(
                normalizedPartnerCode,
                idempotencyKey,
                event == null ? null : event.getEventId(),
                traceId);
        boolean creating = record == null;
        if (creating) {
            record = new DistributionExceptionRecord();
            record.setCreatedAt(now);
            record.setRetryCount(0);
        }
        record.setPartnerCode(normalizedPartnerCode);
        record.setEventType(event == null ? null : event.getEventType());
        record.setEventId(event == null ? null : event.getEventId());
        record.setIdempotencyKey(idempotencyKey);
        record.setExternalOrderId(resolveExternalOrderId(event, rawPayload));
        record.setRelatedOrderId(relatedOrderId);
        record.setRelatedOrderNo(trim(relatedOrderNo, 64));
        record.setExternalMemberId(resolveExternalMemberId(event, rawPayload));
        record.setPhone(resolvePhone(event, rawPayload));
        record.setErrorCode(errorCode);
        record.setErrorMessage(trim(errorMessage, 512));
        record.setConflictDetailJson(conflictDetailJson);
        record.setRawPayload(rawPayload);
        record.setCallbackLogTraceId(traceId);
        record.setHandlingStatus(STATUS_OPEN);
        record.setUpdatedAt(now);
        if (creating) {
            exceptionRecordMapper.insert(record);
        } else {
            exceptionRecordMapper.updateById(record);
        }
    }

    @Override
    public List<DistributionExceptionRecord> list(String status) {
        return exceptionRecordMapper.selectList(Wrappers.<DistributionExceptionRecord>lambdaQuery()
                .eq(StringUtils.hasText(status), DistributionExceptionRecord::getHandlingStatus, normalize(status))
                .orderByDesc(DistributionExceptionRecord::getCreatedAt)
                .orderByDesc(DistributionExceptionRecord::getId)
                .last("LIMIT 100"));
    }

    @Override
    @Transactional
    public DistributionExceptionRecord retry(Long id, PermissionRequestContext context, String remark) {
        DistributionExceptionRecord record = getOrThrow(id);
        record.setHandlingStatus(STATUS_RETRY_QUEUED);
        record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
        record.setNextRetryTime(null);
        applyHandler(record, context, remark);
        exceptionRecordMapper.updateById(record);
        return exceptionRecordMapper.selectById(id);
    }

    @Override
    @Transactional
    public DistributionExceptionRecord markHandled(Long id, PermissionRequestContext context, String remark) {
        DistributionExceptionRecord record = getOrThrow(id);
        record.setHandlingStatus(STATUS_HANDLED);
        record.setHandledAt(LocalDateTime.now());
        applyHandler(record, context, remark);
        exceptionRecordMapper.updateById(record);
        return exceptionRecordMapper.selectById(id);
    }

    private void applyHandler(DistributionExceptionRecord record,
                              PermissionRequestContext context,
                              String remark) {
        record.setHandlerUserId(context == null ? null : context.getCurrentUserId());
        record.setHandlerRoleCode(context == null ? null : normalize(context.getRoleCode()));
        record.setHandleRemark(StringUtils.hasText(remark) ? trim(remark, 512) : null);
        record.setUpdatedAt(LocalDateTime.now());
    }

    private DistributionExceptionRecord findExisting(String partnerCode, String idempotencyKey, String eventId, String traceId) {
        if (!StringUtils.hasText(idempotencyKey) && !StringUtils.hasText(eventId) && !StringUtils.hasText(traceId)) {
            return null;
        }
        return exceptionRecordMapper.selectOne(Wrappers.<DistributionExceptionRecord>lambdaQuery()
                .eq(StringUtils.hasText(partnerCode), DistributionExceptionRecord::getPartnerCode, partnerCode)
                .and(wrapper -> wrapper
                        .eq(StringUtils.hasText(idempotencyKey), DistributionExceptionRecord::getIdempotencyKey, idempotencyKey)
                        .or()
                        .eq(StringUtils.hasText(eventId), DistributionExceptionRecord::getEventId, eventId)
                        .or()
                        .eq(StringUtils.hasText(traceId), DistributionExceptionRecord::getCallbackLogTraceId, traceId))
                .orderByDesc(DistributionExceptionRecord::getId)
                .last("LIMIT 1"));
    }

    private DistributionExceptionRecord getOrThrow(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("distribution exception id is required");
        }
        DistributionExceptionRecord record = exceptionRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("distribution exception not found");
        }
        return record;
    }

    private String resolveExternalOrderId(DistributionEventRequest event, String rawPayload) {
        return firstNonBlank(
                event == null || event.getOrder() == null ? null : event.getOrder().getExternalOrderId(),
                readText(rawPayload, "order.externalOrderId"));
    }

    private String resolveExternalMemberId(DistributionEventRequest event, String rawPayload) {
        return firstNonBlank(
                event == null || event.getMember() == null ? null : event.getMember().getExternalMemberId(),
                readText(rawPayload, "member.externalMemberId"));
    }

    private String resolvePhone(DistributionEventRequest event, String rawPayload) {
        return firstNonBlank(
                event == null || event.getMember() == null ? null : event.getMember().getPhone(),
                readText(rawPayload, "member.phone"));
    }

    private String readText(String rawPayload, String path) {
        if (!StringUtils.hasText(rawPayload) || !StringUtils.hasText(path)) {
            return null;
        }
        try {
            JsonNode current = objectMapper.readTree(rawPayload);
            for (String part : path.split("\\.")) {
                current = current == null ? null : current.get(part);
            }
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            return current.isTextual() ? current.asText() : current.toString();
        } catch (Exception ignored) {
            return null;
        }
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
}
