package com.seedcrm.crm.clue.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.service.ClueRecordService;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class DouyinClueSyncServiceImpl implements DouyinClueSyncService {

    private static final Logger log = LoggerFactory.getLogger(DouyinClueSyncServiceImpl.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DOUYIN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String OCEAN_ENGINE_CLUE_BASE_URL = "https://api.oceanengine.com";
    private static final String OCEAN_ENGINE_CLUE_PATH = "/open_api/2/tools/clue/life/get/";
    private static final int MAX_PULL_PAGES = 200;

    private final ClueService clueService;
    private final ObjectMapper objectMapper;
    private final SchedulerIntegrationService schedulerIntegrationService;
    private final ClueRecordService clueRecordService;

    public DouyinClueSyncServiceImpl(ClueService clueService,
                                     ObjectMapper objectMapper,
                                     SchedulerIntegrationService schedulerIntegrationService,
                                     ClueRecordService clueRecordService) {
        this.clueService = clueService;
        this.objectMapper = objectMapper;
        this.schedulerIntegrationService = schedulerIntegrationService;
        this.clueRecordService = clueRecordService;
    }

    @Override
    public int syncIncremental() {
        return syncMock(null);
    }

    @Override
    public int syncIncremental(IntegrationProviderConfig providerConfig) {
        if (providerConfig == null || !"LIVE".equalsIgnoreCase(providerConfig.getExecutionMode())) {
            return syncMock(providerConfig);
        }
        return syncLive(providerConfig);
    }

    private int syncMock(IntegrationProviderConfig providerConfig) {
        LocalDateTime now = LocalDateTime.now();
        int imported = 0;
        String providerCode = providerConfig == null ? "DOUYIN_LAIKE" : providerConfig.getProviderCode();
        String executionMode = providerConfig == null ? "MOCK" : providerConfig.getExecutionMode();
        for (int index = 0; index < 2; index++) {
            Clue clue = new Clue();
            String stamp = TIME_FORMATTER.format(now.minusSeconds(index * 5L));
            clue.setName("抖音客资-" + stamp + "-" + (index + 1));
            clue.setPhone(buildPhone(now, index));
            clue.setWechat("douyin_" + stamp + "_" + (index + 1));
            clue.setSourceChannel("DOUYIN");
            clue.setSource("douyin");
            String rawData = "{\"source\":\"douyin-api\",\"providerCode\":\""
                    + providerCode + "\",\"executionMode\":\"" + executionMode + "\",\"timestamp\":\"" + now + "\"}";
            clue.setRawData(rawData);
            Clue savedClue = clueService.addClue(clue);
            if (recordDouyinRecord(savedClue, null, rawData, now)) {
                imported++;
            }
        }
        return imported;
    }

    private int syncLive(IntegrationProviderConfig providerConfig) {
        String accessToken = schedulerIntegrationService.resolveProviderAccessToken(providerConfig);
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("抖音来客未完成授权，无法拉取线索");
        }

        LocalDateTime endTime = LocalDateTime.now();
        int overlapMinutes = providerConfig.getOverlapMinutes() == null ? 10 : Math.max(0, providerConfig.getOverlapMinutes());
        int pullWindowMinutes = providerConfig.getPullWindowMinutes() == null ? 60 : Math.max(10, providerConfig.getPullWindowMinutes());
        LocalDateTime startTime = providerConfig.getLastSyncTime() == null
                ? endTime.minusMinutes(pullWindowMinutes)
                : providerConfig.getLastSyncTime().minusMinutes(overlapMinutes);

        int page = 1;
        int pageSize = resolvePageSize(providerConfig);
        SyncStats stats = new SyncStats(startTime, endTime);
        RestClient restClient = RestClient.create();
        String endpoint = resolveClueEndpoint(providerConfig);
        try {
            while (page <= MAX_PULL_PAGES) {
                JsonNode response = restClient
                        .post()
                        .uri(endpoint)
                        .header("Access-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildPullPayload(providerConfig, startTime, endTime, page))
                        .retrieve()
                        .body(JsonNode.class);
                stats.pagesFetched++;

                int code = response == null ? -1 : response.path("code").asInt(0);
                if (code != 0) {
                    String message = extractText(response, "message", "data.description");
                    stats.failedPage = page;
                    log.warn("douyin clue sync failed, {}", stats.failureSummary(message));
                    throw new BusinessException(StringUtils.hasText(message) ? message : "抖音来客线索拉取失败");
                }

                List<JsonNode> records = extractRecords(response);
                stats.recordsFetched += records.size();
                for (JsonNode record : records) {
                    ImportResult result = importRecord(record);
                    if (result == ImportResult.IMPORTED) {
                        stats.recordsImported++;
                    } else if (result == ImportResult.DUPLICATED) {
                        stats.recordsDuplicated++;
                    } else if (result == ImportResult.SKIPPED_UNMATCHED_IDENTITY) {
                        stats.recordsSkippedUnmatchedIdentity++;
                    }
                }
                if (!shouldFetchNextPage(response, records, page, pageSize)) {
                    stats.stopReason = "NO_MORE_PAGES";
                    log.info("douyin clue sync finished, {}", stats.successSummary());
                    return stats.recordsImported;
                }
                page++;
            }
            stats.failedPage = page;
            log.warn("douyin clue sync stopped by max page limit, {}", stats.failureSummary("MAX_PULL_PAGES"));
            throw new BusinessException("抖音来客线索拉取超过最大分页限制，请缩短同步时间窗口或调小 page_size");
        } catch (RuntimeException exception) {
            if (stats.failedPage == null) {
                stats.failedPage = page;
                log.warn("douyin clue sync failed, {}", stats.failureSummary(exception.getMessage()));
            }
            throw exception;
        }
    }

    private List<JsonNode> extractRecords(JsonNode response) {
        List<JsonNode> result = new ArrayList<>();
        if (response == null || response.isNull()) {
            return result;
        }
        JsonNode[] candidates = new JsonNode[] {
                response.path("data"),
                response.path("data").path("clues"),
                response.path("data").path("list"),
                response.path("data").path("records"),
                response.path("data").path("items"),
                response
        };
        for (JsonNode candidate : candidates) {
            if (!candidate.isArray()) {
                continue;
            }
            Iterator<JsonNode> iterator = candidate.elements();
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return result;
    }

    ImportResult importRecord(JsonNode record) {
        String phone = extractText(record,
                "telephone", "phone", "mobile", "customer_phone", "user_phone", "phone_info.phone");
        String externalId = extractText(record, "clue_id", "clueId", "lead_id", "id");
        String name = extractText(record, "name", "customer_name", "user_name", "nickname");
        String wechat = extractText(record, "weixin", "wechat", "wx");
        String rawData;
        try {
            rawData = objectMapper.writeValueAsString(record);
        } catch (Exception exception) {
            rawData = record.toString();
        }

        Clue savedClue;
        if (StringUtils.hasText(phone) || StringUtils.hasText(wechat)) {
            Clue clue = new Clue();
            clue.setName(StringUtils.hasText(name) ? name : "抖音客资-" + (StringUtils.hasText(externalId)
                    ? externalId
                    : TIME_FORMATTER.format(LocalDateTime.now())));
            clue.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
            clue.setWechat(StringUtils.hasText(wechat) ? wechat.trim() : null);
            clue.setSourceChannel("DOUYIN");
            clue.setSource("douyin");
            clue.setRawData(rawData);
            savedClue = clueService.addClue(clue);
        } else {
            Long matchedClueId = resolveExistingClueIdByExternalIdentity(record);
            if (matchedClueId == null || matchedClueId <= 0) {
                return ImportResult.SKIPPED_UNMATCHED_IDENTITY;
            }
            savedClue = new Clue();
            savedClue.setId(matchedClueId);
        }
        return recordDouyinRecord(savedClue, record, rawData, null) ? ImportResult.IMPORTED : ImportResult.DUPLICATED;
    }

    private Long resolveExistingClueIdByExternalIdentity(JsonNode record) {
        if (clueRecordService == null) {
            return null;
        }
        return clueRecordService.findClueIdByExternalIdentity(
                "DOUYIN",
                firstNonBlank(
                        extractText(record, "clue_id", "clueId", "lead_id", "id"),
                        extractText(record, "event_id", "eventId", "action_id", "actionId", "record_id", "recordId", "log_id", "logId")),
                extractText(record,
                        "order_id", "orderId", "item_order_id", "itemOrderId", "order_info.order_id", "order.trade_order_id"));
    }

    private boolean recordDouyinRecord(Clue clue, JsonNode record, String rawData, LocalDateTime fallbackOccurredAt) {
        if (clue == null || clue.getId() == null || clueRecordService == null) {
            return true;
        }
        ClueRecord clueRecord = new ClueRecord();
        clueRecord.setClueId(clue.getId());
        clueRecord.setRecordKey(buildRecordKey(record, rawData));
        clueRecord.setRecordType(resolveRecordType(record));
        clueRecord.setSourceChannel("DOUYIN");
        clueRecord.setExternalRecordId(firstNonBlank(
                extractText(record, "event_id", "eventId", "action_id", "actionId", "record_id", "recordId", "log_id", "logId"),
                extractText(record, "clue_id", "clueId", "lead_id", "id")));
        clueRecord.setExternalOrderId(extractText(record,
                "order_id", "orderId", "item_order_id", "itemOrderId", "order_info.order_id", "order.trade_order_id"));
        clueRecord.setTitle(buildRecordTitle(record));
        clueRecord.setContent(buildRecordContent(record));
        clueRecord.setOccurredAt(resolveOccurredAt(record, fallbackOccurredAt));
        clueRecord.setRawData(rawData);
        return clueRecordService.addRecordIfAbsent(clueRecord);
    }

    String buildRecordKey(JsonNode record, String rawData) {
        String recordType = resolveRecordType(record);
        String businessKey = firstNonBlank(
                extractText(record, "event_id", "eventId", "action_id", "actionId", "record_id", "recordId", "log_id", "logId"),
                extractText(record, "order_id", "orderId", "item_order_id", "itemOrderId",
                        "order_info.order_id", "order.trade_order_id"),
                extractText(record, "certificate_id", "certificateId", "coupon_id", "couponId"),
                extractText(record, "clue_id", "clueId", "lead_id", "id"));
        if (StringUtils.hasText(businessKey)) {
            return "douyin:" + recordType.toLowerCase(Locale.ROOT) + ":" + businessKey.trim()
                    + recordKeySuffix(record);
        }
        return "douyin:raw:" + sha256(rawData == null ? "" : rawData);
    }

    private String recordKeySuffix(JsonNode record) {
        String occurredAt = extractText(record,
                "pay_time", "paid_time", "order_create_time", "create_time", "created_at",
                "submit_time", "action_time", "update_time", "clue_time");
        String state = firstNonBlank(
                extractText(record, "action_type", "actionType", "event_type", "eventType", "operate_type"),
                extractText(record, "order_status", "status", "order.status", "order_info.status"));
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(state)) {
            parts.add(state.trim());
        }
        if (StringUtils.hasText(occurredAt)) {
            parts.add(occurredAt.trim());
        }
        return parts.isEmpty() ? "" : ":" + sha256(String.join("|", parts)).substring(0, 12);
    }

    private String resolveRecordType(JsonNode record) {
        if (StringUtils.hasText(extractText(record,
                "order_id", "orderId", "item_order_id", "itemOrderId", "order_info.order_id", "order.trade_order_id"))) {
            return "ORDER";
        }
        if (StringUtils.hasText(extractText(record,
                "event_id", "eventId", "action_id", "actionId", "record_id", "recordId", "log_id", "logId",
                "action_type", "actionType", "event_type", "eventType", "operate_type"))) {
            return "ACTION";
        }
        return "CLUE";
    }

    private String buildRecordTitle(JsonNode record) {
        String recordType = resolveRecordType(record);
        String status = extractText(record, "order_status", "status", "order.status", "order_info.status");
        if ("ORDER".equals(recordType)) {
            return StringUtils.hasText(status) ? "订单同步：" + status.trim() : "订单同步";
        }
        if ("ACTION".equals(recordType)) {
            String action = extractText(record, "action_type", "actionType", "event_type", "eventType", "operate_type");
            return StringUtils.hasText(action) ? "动作记录：" + action.trim() : "动作记录";
        }
        return "留资记录";
    }

    private String buildRecordContent(JsonNode record) {
        List<String> parts = new ArrayList<>();
        appendPart(parts, "订单号", extractText(record,
                "order_id", "orderId", "item_order_id", "itemOrderId", "order_info.order_id", "order.trade_order_id"));
        appendPart(parts, "商品", extractText(record, "product_name", "item_name", "goods_name", "order.product_name"));
        appendPart(parts, "状态", extractText(record, "order_status", "status", "order.status", "order_info.status"));
        appendPart(parts, "动作", extractText(record, "action_type", "actionType", "event_type", "eventType", "operate_type"));
        appendPart(parts, "金额", extractText(record, "amount", "pay_amount", "order_amount", "order.pay_amount"));
        appendPart(parts, "门店", extractText(record, "store_name", "poi_name", "shop_name", "order.store_name"));
        String content = String.join(" / ", parts);
        if (StringUtils.hasText(content)) {
            return content.length() > 500 ? content.substring(0, 500) : content;
        }
        String phone = extractText(record, "telephone", "phone", "mobile", "customer_phone", "user_phone", "phone_info.phone");
        return StringUtils.hasText(phone) ? "客户留资：" + phone : "接口同步客资记录";
    }

    private void appendPart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
    }

    private LocalDateTime resolveOccurredAt(JsonNode record, LocalDateTime fallbackOccurredAt) {
        String raw = extractText(record,
                "pay_time", "paid_time", "order_create_time", "create_time", "created_at",
                "submit_time", "action_time", "update_time", "clue_time");
        if (!StringUtils.hasText(raw)) {
            return fallbackOccurredAt == null ? LocalDateTime.now() : fallbackOccurredAt;
        }
        String value = raw.trim();
        try {
            if (value.matches("\\d{13}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), ZoneId.systemDefault());
            }
            if (value.matches("\\d{10}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value)), ZoneId.systemDefault());
            }
            if (value.matches("\\d{14}")) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            }
            if (value.contains("T")) {
                return LocalDateTime.parse(value.replace("Z", ""));
            }
            return LocalDateTime.parse(value, DOUYIN_TIME_FORMATTER);
        } catch (DateTimeParseException | NumberFormatException exception) {
            return fallbackOccurredAt == null ? LocalDateTime.now() : fallbackOccurredAt;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return String.valueOf(Math.abs(value.hashCode()));
        }
    }

    String resolveClueEndpoint(IntegrationProviderConfig providerConfig) {
        String endpointPath = StringUtils.hasText(providerConfig.getEndpointPath())
                ? providerConfig.getEndpointPath().trim()
                : OCEAN_ENGINE_CLUE_PATH;
        if (endpointPath.startsWith("http")) {
            URI uri = URI.create(endpointPath);
            assertOfficialOceanEngineEndpoint(uri.getHost(), uri.getPath());
            return OCEAN_ENGINE_CLUE_BASE_URL + OCEAN_ENGINE_CLUE_PATH;
        }
        String path = endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
        assertOfficialOceanEngineEndpoint("api.oceanengine.com", path);
        return OCEAN_ENGINE_CLUE_BASE_URL + OCEAN_ENGINE_CLUE_PATH;
    }

    private void assertOfficialOceanEngineEndpoint(String host, String path) {
        String normalizedPath = path == null ? "" : (path.endsWith("/") ? path : path + "/");
        if (!"api.oceanengine.com".equalsIgnoreCase(host) || !OCEAN_ENGINE_CLUE_PATH.equals(normalizedPath)) {
            throw new BusinessException("客资列表数据源已固定为巨量 OceanEngine 线索接口：POST " + OCEAN_ENGINE_CLUE_BASE_URL + OCEAN_ENGINE_CLUE_PATH);
        }
    }

    Map<String, Object> buildPullPayload(IntegrationProviderConfig providerConfig,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime) {
        return buildPullPayload(providerConfig, startTime, endTime, 1);
    }

    Map<String, Object> buildPullPayload(IntegrationProviderConfig providerConfig,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime,
                                         int page) {
        return Map.of(
                "local_account_ids", resolveLocalAccountIds(providerConfig),
                "start_time", DOUYIN_TIME_FORMATTER.format(startTime),
                "end_time", DOUYIN_TIME_FORMATTER.format(endTime),
                "page", Math.max(1, page),
                "page_size", resolvePageSize(providerConfig));
    }

    boolean shouldFetchNextPage(JsonNode response, List<JsonNode> records, int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || records == null || records.isEmpty()) {
            return false;
        }
        Boolean explicitHasMore = extractBoolean(response,
                "data.has_more", "data.hasMore", "data.page_info.has_more", "data.page_info.hasMore", "has_more", "hasMore");
        if (explicitHasMore != null) {
            return explicitHasMore;
        }
        Integer total = extractInteger(response,
                "data.total", "data.total_count", "data.totalCount", "data.page_info.total",
                "data.page_info.total_count", "data.page_info.totalCount", "total", "total_count");
        if (total != null && total >= 0) {
            return page * pageSize < total;
        }
        return records.size() >= pageSize;
    }

    private int resolvePageSize(IntegrationProviderConfig providerConfig) {
        Integer pageSize = providerConfig == null ? null : providerConfig.getPageSize();
        return pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
    }

    List<Long> resolveLocalAccountIds(IntegrationProviderConfig providerConfig) {
        String raw = StringUtils.hasText(providerConfig.getLocalAccountIds())
                ? providerConfig.getLocalAccountIds()
                : firstNonBlank(providerConfig.getLifeAccountIds(), providerConfig.getAccountId());
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("抖音来客必须配置 local_account_ids");
        }
        List<Long> accountIds = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                try {
                    accountIds.add(Long.valueOf(part.trim()));
                } catch (NumberFormatException exception) {
                    throw new BusinessException("抖音来客 local_account_ids 必须是数字，多个账号用英文逗号分隔");
                }
            }
        }
        if (accountIds.isEmpty()) {
            throw new BusinessException("抖音来客 local_account_ids 不能为空");
        }
        if (accountIds.size() > 50) {
            throw new BusinessException("抖音来客 local_account_ids 最多配置 50 个");
        }
        return accountIds;
    }

    private String extractText(JsonNode node, String... candidates) {
        if (node == null || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            JsonNode current = node;
            for (String part : candidate.split("\\.")) {
                current = current == null ? null : current.path(part);
            }
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private Boolean extractBoolean(JsonNode node, String... candidates) {
        String value = extractText(node, candidates);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private Integer extractInteger(JsonNode node, String... candidates) {
        String value = extractText(node, candidates);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
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

    enum ImportResult {
        IMPORTED,
        DUPLICATED,
        SKIPPED_UNMATCHED_IDENTITY
    }

    private static class SyncStats {

        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private int pagesFetched;
        private int recordsFetched;
        private int recordsImported;
        private int recordsDuplicated;
        private int recordsSkippedUnmatchedIdentity;
        private Integer failedPage;
        private String stopReason = "RUNNING";

        private SyncStats(LocalDateTime startTime, LocalDateTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        private String successSummary() {
            return "window=" + DOUYIN_TIME_FORMATTER.format(startTime) + "~" + DOUYIN_TIME_FORMATTER.format(endTime)
                    + ", pagesFetched=" + pagesFetched
                    + ", recordsFetched=" + recordsFetched
                    + ", recordsImported=" + recordsImported
                    + ", recordsDuplicated=" + recordsDuplicated
                    + ", recordsSkippedUnmatchedIdentity=" + recordsSkippedUnmatchedIdentity
                    + ", stopReason=" + stopReason;
        }

        private String failureSummary(String reason) {
            return successSummary()
                    + ", failedPage=" + failedPage
                    + ", reason=" + (StringUtils.hasText(reason) ? reason : "UNKNOWN");
        }
    }

    private String buildPhone(LocalDateTime now, int index) {
        String seed = TIME_FORMATTER.format(now) + index;
        String numeric = seed.substring(Math.max(0, seed.length() - 8));
        return "139" + String.format("%08d", Integer.parseInt(numeric));
    }
}
