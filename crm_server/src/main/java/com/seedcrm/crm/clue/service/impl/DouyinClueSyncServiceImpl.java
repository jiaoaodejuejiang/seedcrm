package com.seedcrm.crm.clue.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class DouyinClueSyncServiceImpl implements DouyinClueSyncService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DOUYIN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClueService clueService;
    private final ObjectMapper objectMapper;
    private final SchedulerIntegrationService schedulerIntegrationService;

    public DouyinClueSyncServiceImpl(ClueService clueService,
                                     ObjectMapper objectMapper,
                                     SchedulerIntegrationService schedulerIntegrationService) {
        this.clueService = clueService;
        this.objectMapper = objectMapper;
        this.schedulerIntegrationService = schedulerIntegrationService;
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
            clue.setRawData("{\"source\":\"douyin-api\",\"providerCode\":\""
                    + providerCode + "\",\"executionMode\":\"" + executionMode + "\",\"timestamp\":\"" + now + "\"}");
            clueService.addClue(clue);
            imported++;
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

        JsonNode response = RestClient.create()
                .post()
                .uri(resolveClueEndpoint(providerConfig))
                .header("Access-Token", accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "local_account_ids", resolveLocalAccountIds(providerConfig),
                        "start_time", DOUYIN_TIME_FORMATTER.format(startTime),
                        "end_time", DOUYIN_TIME_FORMATTER.format(endTime),
                        "page_number", 1,
                        "page_size", providerConfig.getPageSize() == null || providerConfig.getPageSize() <= 0
                                ? 20 : Math.min(providerConfig.getPageSize(), 100)))
                .retrieve()
                .body(JsonNode.class);

        int code = response == null ? -1 : response.path("code").asInt(0);
        if (code != 0) {
            String message = extractText(response, "message", "data.description");
            throw new BusinessException(StringUtils.hasText(message) ? message : "抖音来客线索拉取失败");
        }

        List<JsonNode> records = extractRecords(response);
        int imported = 0;
        for (JsonNode record : records) {
            if (importRecord(record)) {
                imported++;
            }
        }
        return imported;
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

    private boolean importRecord(JsonNode record) {
        String phone = extractText(record,
                "telephone", "phone", "mobile", "customer_phone", "user_phone", "phone_info.phone");
        if (!StringUtils.hasText(phone)) {
            return false;
        }
        String externalId = extractText(record, "clue_id", "clueId", "lead_id", "id");
        String name = extractText(record, "name", "customer_name", "user_name", "nickname");
        String wechat = extractText(record, "weixin", "wechat", "wx");
        Clue clue = new Clue();
        clue.setName(StringUtils.hasText(name) ? name : "抖音客资-" + (StringUtils.hasText(externalId)
                ? externalId
                : TIME_FORMATTER.format(LocalDateTime.now())));
        clue.setPhone(phone.trim());
        clue.setWechat(StringUtils.hasText(wechat) ? wechat.trim() : null);
        clue.setSourceChannel("DOUYIN");
        clue.setSource("douyin");
        try {
            clue.setRawData(objectMapper.writeValueAsString(record));
        } catch (Exception exception) {
            clue.setRawData(record.toString());
        }
        clueService.addClue(clue);
        return true;
    }

    private String resolveClueEndpoint(IntegrationProviderConfig providerConfig) {
        String baseUrl = StringUtils.hasText(providerConfig.getBaseUrl())
                ? trimTrailingSlash(providerConfig.getBaseUrl())
                : "https://api.oceanengine.com";
        String endpointPath = StringUtils.hasText(providerConfig.getEndpointPath())
                ? providerConfig.getEndpointPath().trim()
                : "/open_api/2/tools/clue/life/get/";
        return endpointPath.startsWith("http") ? endpointPath : baseUrl + (endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath);
    }

    private List<String> resolveLocalAccountIds(IntegrationProviderConfig providerConfig) {
        String raw = StringUtils.hasText(providerConfig.getLocalAccountIds())
                ? providerConfig.getLocalAccountIds()
                : firstNonBlank(providerConfig.getLifeAccountIds(), providerConfig.getAccountId());
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("抖音来客必须配置 local_account_ids");
        }
        List<String> accountIds = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                accountIds.add(part.trim());
            }
        }
        if (accountIds.isEmpty()) {
            throw new BusinessException("抖音来客 local_account_ids 不能为空");
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

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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

    private String buildPhone(LocalDateTime now, int index) {
        String seed = TIME_FORMATTER.format(now) + index;
        String numeric = seed.substring(Math.max(0, seed.length() - 8));
        return "139" + String.format("%08d", Integer.parseInt(numeric));
    }
}
