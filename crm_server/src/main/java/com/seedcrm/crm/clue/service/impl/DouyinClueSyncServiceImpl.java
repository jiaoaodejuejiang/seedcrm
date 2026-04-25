package com.seedcrm.crm.clue.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.clue.service.DouyinClueSyncService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DouyinClueSyncServiceImpl implements DouyinClueSyncService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ClueService clueService;
    private final ObjectMapper objectMapper;

    public DouyinClueSyncServiceImpl(ClueService clueService, ObjectMapper objectMapper) {
        this.clueService = clueService;
        this.objectMapper = objectMapper;
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
        String accessToken = fetchClientToken(providerConfig);
        LocalDateTime endTime = LocalDateTime.now().minusMinutes(10);
        LocalDateTime startTime = providerConfig.getLastSyncTime() == null
                ? endTime.minusHours(1)
                : providerConfig.getLastSyncTime();
        JsonNode response = RestClient.create()
                .get()
                .uri(buildClueQueryUri(providerConfig, startTime, endTime))
                .header("access-token", accessToken)
                .header("content-type", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(JsonNode.class);

        List<JsonNode> records = extractRecords(response);
        int imported = 0;
        for (JsonNode record : records) {
            if (importRecord(record)) {
                imported++;
            }
        }
        return imported;
    }

    private String fetchClientToken(IntegrationProviderConfig providerConfig) {
        if (!StringUtils.hasText(providerConfig.getClientKey()) || !StringUtils.hasText(providerConfig.getClientSecret())) {
            throw new BusinessException("抖音来客 LIVE 模式必须配置 clientKey 和 clientSecret");
        }
        String tokenUrl = StringUtils.hasText(providerConfig.getTokenUrl())
                ? providerConfig.getTokenUrl().trim()
                : trimTrailingSlash(providerConfig.getBaseUrl()) + "/oauth/client_token/";
        RestClient restClient = RestClient.create();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", providerConfig.getClientKey().trim());
        form.add("client_secret", providerConfig.getClientSecret().trim());
        try {
            JsonNode response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String token = extractText(response, "data.client_token", "client_token", "access_token");
            if (StringUtils.hasText(token)) {
                return token;
            }
        } catch (Exception ignored) {
            // 兼容不同环境的 client_token 调用方式。
        }
        JsonNode fallback = restClient.get()
                .uri(tokenUrl + "?client_key=" + providerConfig.getClientKey().trim()
                        + "&client_secret=" + providerConfig.getClientSecret().trim())
                .retrieve()
                .body(JsonNode.class);
        String token = extractText(fallback, "data.client_token", "client_token", "access_token");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("抖音来客 client_token 获取失败");
        }
        return token;
    }

    private String buildClueQueryUri(IntegrationProviderConfig providerConfig,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime) {
        if (!StringUtils.hasText(providerConfig.getBaseUrl()) || !StringUtils.hasText(providerConfig.getAccountId())) {
            throw new BusinessException("抖音来客 LIVE 模式必须配置 baseUrl 和 accountId");
        }
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(trimTrailingSlash(providerConfig.getBaseUrl()) + resolveEndpointPath(providerConfig.getEndpointPath()))
                .queryParam("account_id", providerConfig.getAccountId().trim())
                .queryParam("start_time", toEpochSeconds(startTime))
                .queryParam("end_time", toEpochSeconds(endTime))
                .queryParam("page", 1)
                .queryParam("page_size", providerConfig.getPageSize() == null || providerConfig.getPageSize() <= 0
                        ? 20 : providerConfig.getPageSize());
        if (StringUtils.hasText(providerConfig.getOpenId())) {
            builder.queryParam("open_id", providerConfig.getOpenId().trim());
        }
        if (StringUtils.hasText(providerConfig.getLifeAccountIds())) {
            for (String lifeAccountId : providerConfig.getLifeAccountIds().split(",")) {
                if (StringUtils.hasText(lifeAccountId)) {
                    builder.queryParam("life_account_id[]", lifeAccountId.trim());
                }
            }
        }
        return builder.toUriString();
    }

    private List<JsonNode> extractRecords(JsonNode response) {
        List<JsonNode> result = new ArrayList<>();
        if (response == null || response.isNull()) {
            return result;
        }
        JsonNode[] candidates = new JsonNode[] {
                response.path("data").path("clues"),
                response.path("data").path("list"),
                response.path("data").path("records"),
                response.path("data").path("items"),
                response.path("data"),
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
                "phone", "mobile", "customer_phone", "user_phone", "phone_info.phone");
        if (!StringUtils.hasText(phone)) {
            return false;
        }
        String externalId = extractText(record, "clue_id", "clueId", "lead_id", "id");
        String name = extractText(record, "name", "customer_name", "user_name", "nickname");
        String wechat = extractText(record, "wechat", "weixin", "wx");
        Clue clue = new Clue();
        clue.setName(StringUtils.hasText(name) ? name : "抖音客资-" + (StringUtils.hasText(externalId) ? externalId : TIME_FORMATTER.format(LocalDateTime.now())));
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

    private long toEpochSeconds(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private String resolveEndpointPath(String endpointPath) {
        if (!StringUtils.hasText(endpointPath)) {
            return "/goodlife/v1/open_api/crm/clue/query/";
        }
        return endpointPath.startsWith("/") ? endpointPath.trim() : "/" + endpointPath.trim();
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

    private String buildPhone(LocalDateTime now, int index) {
        String seed = TIME_FORMATTER.format(now) + index;
        String numeric = seed.substring(Math.max(0, seed.length() - 8));
        return "139" + String.format("%08d", Integer.parseInt(numeric));
    }
}
