package com.seedcrm.crm.scheduler.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import com.seedcrm.crm.scheduler.entity.DistributionExceptionRecord;
import com.seedcrm.crm.scheduler.mapper.DistributionExceptionRecordMapper;
import com.seedcrm.crm.scheduler.service.DistributionEventIngestService;
import com.seedcrm.crm.scheduler.service.DistributionExceptionRetryService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DistributionExceptionRetryServiceImpl implements DistributionExceptionRetryService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_RETRY_QUEUED = "RETRY_QUEUED";
    private static final String STATUS_HANDLED = "HANDLED";

    private final DistributionExceptionRecordMapper exceptionRecordMapper;
    private final DistributionEventIngestService distributionEventIngestService;
    private final ObjectMapper objectMapper;

    public DistributionExceptionRetryServiceImpl(DistributionExceptionRecordMapper exceptionRecordMapper,
                                                 DistributionEventIngestService distributionEventIngestService,
                                                 ObjectMapper objectMapper) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.distributionEventIngestService = distributionEventIngestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DistributionExceptionRecord> processRetryQueue(int limit) {
        int batchSize = limit <= 0 ? 10 : Math.min(limit, 50);
        LocalDateTime now = LocalDateTime.now();
        List<DistributionExceptionRecord> records = exceptionRecordMapper.selectList(Wrappers.<DistributionExceptionRecord>lambdaQuery()
                .eq(DistributionExceptionRecord::getHandlingStatus, STATUS_RETRY_QUEUED)
                .and(wrapper -> wrapper.isNull(DistributionExceptionRecord::getNextRetryTime)
                        .or()
                        .le(DistributionExceptionRecord::getNextRetryTime, now))
                .orderByAsc(DistributionExceptionRecord::getUpdatedAt)
                .orderByAsc(DistributionExceptionRecord::getId)
                .last("LIMIT " + batchSize));
        for (DistributionExceptionRecord record : records) {
            processOne(record);
        }
        return records.stream().map(item -> exceptionRecordMapper.selectById(item.getId())).toList();
    }

    private void processOne(DistributionExceptionRecord record) {
        LocalDateTime claimedAt = LocalDateTime.now();
        if (!tryClaim(record, claimedAt)) {
            return;
        }
        try {
            DistributionExceptionRecord current = exceptionRecordMapper.selectById(record.getId());
            validateReplayInput(current);
            JsonNode payload = objectMapper.readTree(current.getRawPayload());
            DistributionEventResponse response = distributionEventIngestService.replayFromScheduler(
                    payload,
                    current.getPartnerCode(),
                    current.getIdempotencyKey());
            validateReplayResponse(response);
            markSuccess(current, response);
        } catch (Exception exception) {
            markFailed(record.getId(), exception);
        }
    }

    private boolean tryClaim(DistributionExceptionRecord record, LocalDateTime claimedAt) {
        return exceptionRecordMapper.update(null, Wrappers.<DistributionExceptionRecord>update()
                .eq("id", record.getId())
                .eq("handling_status", STATUS_RETRY_QUEUED)
                .and(wrapper -> wrapper.isNull("next_retry_time")
                        .or()
                        .le("next_retry_time", claimedAt))
                .set("next_retry_time", claimedAt.plusMinutes(5))
                .set("updated_at", claimedAt)) > 0;
    }

    private void validateReplayInput(DistributionExceptionRecord record) {
        if (record == null) {
            throw new BusinessException("distribution exception record not found");
        }
        if (!StringUtils.hasText(record.getRawPayload())) {
            throw new BusinessException("distribution exception raw payload is required for retry");
        }
        if (!StringUtils.hasText(record.getIdempotencyKey())) {
            throw new BusinessException("distribution exception idempotency key is required for retry");
        }
        if (!StringUtils.hasText(record.getPartnerCode())) {
            throw new BusinessException("distribution exception partner code is required for retry");
        }
    }

    private void validateReplayResponse(DistributionEventResponse response) {
        if (response == null) {
            throw new BusinessException("distribution exception retry returned empty response");
        }
        if (!"SUCCESS".equalsIgnoreCase(response.getProcessStatus())) {
            throw new BusinessException(firstNonBlank(
                    response.getMessage(),
                    "distribution exception retry did not succeed"));
        }
        if ("EXCEPTION_QUEUED".equalsIgnoreCase(response.getIdempotencyResult())) {
            throw new BusinessException(firstNonBlank(
                    response.getMessage(),
                    "distribution exception still requires manual handling"));
        }
    }

    @Transactional
    protected void markSuccess(DistributionExceptionRecord record, DistributionEventResponse response) {
        record.setHandlingStatus(STATUS_HANDLED);
        record.setHandledAt(LocalDateTime.now());
        record.setNextRetryTime(null);
        record.setHandleRemark("retry succeeded, traceId="
                + (response == null ? "--" : response.getTraceId())
                + ", result="
                + (response == null ? "--" : response.getIdempotencyResult()));
        record.setUpdatedAt(LocalDateTime.now());
        exceptionRecordMapper.updateById(record);
    }

    @Transactional
    protected void markFailed(Long recordId, Exception exception) {
        DistributionExceptionRecord record = exceptionRecordMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        record.setHandlingStatus(STATUS_OPEN);
        record.setNextRetryTime(null);
        record.setErrorMessage(trim(exception == null ? null : exception.getMessage(), 512));
        record.setHandleRemark("retry failed, waiting for manual review");
        record.setUpdatedAt(LocalDateTime.now());
        exceptionRecordMapper.updateById(record);
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
            return "distribution exception retry failed";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
