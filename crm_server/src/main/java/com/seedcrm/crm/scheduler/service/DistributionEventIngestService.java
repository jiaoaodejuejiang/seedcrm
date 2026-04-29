package com.seedcrm.crm.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionEventResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface DistributionEventIngestService {

    DistributionEventResponse ingest(JsonNode payload, HttpServletRequest request);

    DistributionEventResponse replayFromScheduler(JsonNode payload, String partnerCode, String idempotencyKey);
}
