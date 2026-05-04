package com.seedcrm.crm.clue.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.service.ClueRecordService;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.scheduler.entity.IntegrationProviderConfig;
import com.seedcrm.crm.scheduler.service.SchedulerIntegrationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DouyinClueSyncServiceImplTest {

    @Mock
    private ClueService clueService;

    @Mock
    private SchedulerIntegrationService schedulerIntegrationService;

    @Mock
    private ClueRecordService clueRecordService;

    private DouyinClueSyncServiceImpl douyinClueSyncService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        douyinClueSyncService = new DouyinClueSyncServiceImpl(
                clueService,
                objectMapper,
                schedulerIntegrationService,
                clueRecordService);
    }

    @Test
    void shouldBuildOceanEngineCluePayloadWithOfficialPageParam() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setLocalAccountIds("123, 456");
        providerConfig.setPageSize(120);

        Map<String, Object> payload = douyinClueSyncService.buildPullPayload(
                providerConfig,
                LocalDateTime.of(2026, 4, 27, 0, 0, 0),
                LocalDateTime.of(2026, 4, 27, 23, 59, 59));

        assertThat(payload).containsEntry("page", 1);
        assertThat(payload).doesNotContainKey("page_number");
        assertThat(payload).containsEntry("start_time", "2026-04-27 00:00:00");
        assertThat(payload).containsEntry("end_time", "2026-04-27 23:59:59");
        assertThat(payload).containsEntry("page_size", 100);
        assertThat(payload.get("local_account_ids")).isEqualTo(List.of(123L, 456L));
    }

    @Test
    void shouldBuildPayloadForRequestedPage() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setLocalAccountIds("123");
        providerConfig.setPageSize(30);

        Map<String, Object> payload = douyinClueSyncService.buildPullPayload(
                providerConfig,
                LocalDateTime.of(2026, 4, 27, 0, 0, 0),
                LocalDateTime.of(2026, 4, 27, 23, 59, 59),
                3);

        assertThat(payload).containsEntry("page", 3);
        assertThat(payload).containsEntry("page_size", 30);
    }

    @Test
    void shouldContinueWhenExplicitHasMoreIsTrue() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"code":0,"data":{"has_more":true,"list":[{"id":1}]}}
                """);

        assertThat(douyinClueSyncService.shouldFetchNextPage(
                response,
                List.of(response.path("data").path("list").get(0)),
                1,
                20)).isTrue();
    }

    @Test
    void shouldStopWhenTotalIsCovered() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"code":0,"data":{"total":40,"list":[{"id":1},{"id":2}]}}
                """);

        assertThat(douyinClueSyncService.shouldFetchNextPage(
                response,
                List.of(response.path("data").path("list").get(0), response.path("data").path("list").get(1)),
                2,
                20)).isFalse();
    }

    @Test
    void shouldContinueOnFullPageWhenNoPageMetadata() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"code":0,"data":{"list":[{"id":1},{"id":2}]}}
                """);

        assertThat(douyinClueSyncService.shouldFetchNextPage(
                response,
                List.of(response.path("data").path("list").get(0), response.path("data").path("list").get(1)),
                1,
                2)).isTrue();
    }

    @Test
    void shouldUseOrderOrActionIdentityBeforeClueIdentityForRecordKey() throws Exception {
        JsonNode orderRecord = objectMapper.readTree("""
                {"clue_id":"clue-1","order_id":"order-1","order_status":"PAID","update_time":"2026-04-27 10:00:00"}
                """);
        JsonNode actionRecord = objectMapper.readTree("""
                {"clue_id":"clue-1","action_id":"action-1","action_type":"ADD_WECHAT","action_time":"2026-04-27 10:05:00"}
                """);

        assertThat(douyinClueSyncService.buildRecordKey(orderRecord, orderRecord.toString()))
                .startsWith("douyin:order:order-1:");
        assertThat(douyinClueSyncService.buildRecordKey(actionRecord, actionRecord.toString()))
                .startsWith("douyin:action:action-1:");
    }

    @Test
    void shouldKeepSeparateOrderRecordsWhenSameClueHasDifferentOrderActions() throws Exception {
        JsonNode paidRecord = objectMapper.readTree("""
                {"clue_id":"clue-1","order_id":"order-1","order_status":"PAID","update_time":"2026-04-27 10:00:00"}
                """);
        JsonNode refundedRecord = objectMapper.readTree("""
                {"clue_id":"clue-1","order_id":"order-1","order_status":"REFUNDED","update_time":"2026-04-28 11:00:00"}
                """);

        assertThat(douyinClueSyncService.buildRecordKey(paidRecord, paidRecord.toString()))
                .isNotEqualTo(douyinClueSyncService.buildRecordKey(refundedRecord, refundedRecord.toString()));
    }

    @Test
    void importRecordShouldMergeOrderWithoutPhoneWhenExternalIdentityMatched() throws Exception {
        JsonNode orderRecord = objectMapper.readTree("""
                {"clue_id":"clue-1","order_id":"order-1","order_status":"PAID","update_time":"2026-04-27 10:00:00"}
                """);
        when(clueRecordService.findClueIdByExternalIdentity(eq("DOUYIN"), eq("clue-1"), eq("order-1")))
                .thenReturn(88L);
        when(clueRecordService.addRecordIfAbsent(any(ClueRecord.class))).thenReturn(true);

        DouyinClueSyncServiceImpl.ImportResult result = douyinClueSyncService.importRecord(orderRecord);

        assertThat(result).isEqualTo(DouyinClueSyncServiceImpl.ImportResult.IMPORTED);
        verify(clueService, never()).addClue(any(Clue.class));
        ArgumentCaptor<ClueRecord> recordCaptor = ArgumentCaptor.forClass(ClueRecord.class);
        verify(clueRecordService).addRecordIfAbsent(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getClueId()).isEqualTo(88L);
        assertThat(recordCaptor.getValue().getExternalOrderId()).isEqualTo("order-1");
    }

    @Test
    void importRecordShouldDropOrderWithoutPhoneWhenExternalIdentityNotMatched() throws Exception {
        JsonNode orderRecord = objectMapper.readTree("""
                {"clue_id":"clue-2","order_id":"order-2","order_status":"PAID","update_time":"2026-04-27 10:00:00"}
                """);

        DouyinClueSyncServiceImpl.ImportResult result = douyinClueSyncService.importRecord(orderRecord);

        assertThat(result).isEqualTo(DouyinClueSyncServiceImpl.ImportResult.SKIPPED_UNMATCHED_IDENTITY);
        verify(clueService, never()).addClue(any(Clue.class));
        verify(clueRecordService, never()).addRecordIfAbsent(any(ClueRecord.class));
    }

    @Test
    void shouldRejectBlankLocalAccountIds() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setLocalAccountIds(" , ");

        assertThatThrownBy(() -> douyinClueSyncService.resolveLocalAccountIds(providerConfig))
                .hasMessageContaining("local_account_ids 不能为空");
    }

    @Test
    void shouldRejectNonNumericLocalAccountIds() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setLocalAccountIds("123,abc");

        assertThatThrownBy(() -> douyinClueSyncService.resolveLocalAccountIds(providerConfig))
                .hasMessageContaining("local_account_ids 必须是数字");
    }

    @Test
    void shouldFallbackToLegacyAccountFields() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setLifeAccountIds("789");
        providerConfig.setAccountId("456");

        assertThat(douyinClueSyncService.resolveLocalAccountIds(providerConfig)).isEqualTo(List.of(789L));
    }

    @Test
    void shouldAlwaysResolveOfficialOceanEngineClueEndpoint() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setBaseUrl("https://open.douyin.com");

        assertThat(douyinClueSyncService.resolveClueEndpoint(providerConfig))
                .isEqualTo("https://api.oceanengine.com/open_api/2/tools/clue/life/get/");
    }

    @Test
    void shouldRejectDouyinLifeServiceClueEndpointOverride() {
        IntegrationProviderConfig providerConfig = new IntegrationProviderConfig();
        providerConfig.setEndpointPath("https://open.douyin.com/goodlife/v1/open_api/crm/clue/query/");

        assertThatThrownBy(() -> douyinClueSyncService.resolveClueEndpoint(providerConfig))
                .hasMessageContaining("OceanEngine 线索接口");
    }
}
