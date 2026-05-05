package com.seedcrm.crm.clue.management.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigRequest;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigResponse;
import com.seedcrm.crm.clue.management.mapper.ClueAssignmentStrategyMapper;
import com.seedcrm.crm.clue.management.mapper.DutyCustomerServiceMapper;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClueManagementServiceImplTest {

    @Mock
    private ClueAssignmentStrategyMapper clueAssignmentStrategyMapper;

    @Mock
    private DutyCustomerServiceMapper dutyCustomerServiceMapper;

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private SystemConfigService systemConfigService;

    private ClueManagementServiceImpl clueManagementService;

    @BeforeEach
    void setUp() {
        clueManagementService = new ClueManagementServiceImpl(
                clueAssignmentStrategyMapper,
                dutyCustomerServiceMapper,
                clueMapper,
                systemConfigService);
    }

    @Test
    void getDedupConfigShouldDefaultToEnabledAndNinetyDays() {
        when(systemConfigService.getBoolean(ClueManagementServiceImpl.CLUE_DEDUP_ENABLED_KEY, true))
                .thenReturn(true);
        when(systemConfigService.getString(
                ClueManagementServiceImpl.CLUE_DEDUP_WINDOW_DAYS_KEY,
                String.valueOf(ClueManagementServiceImpl.DEFAULT_DEDUP_WINDOW_DAYS)))
                .thenReturn("");
        when(systemConfigService.listConfigs("clue.dedup.")).thenReturn(List.of());

        DedupConfigResponse response = clueManagementService.getDedupConfig();

        assertThat(response.getEnabled()).isEqualTo(1);
        assertThat(response.getWindowDays()).isEqualTo(90);
        assertThat(response.getRuntimeConsumed()).isEqualTo(1);
        assertThat(response.getRuntimeScope()).contains("客资入库");
        assertThat(response.getRecordMergeRule()).contains("客资记录");
        assertThat(response.getEffectiveScope()).contains("后续");
    }

    @Test
    void saveDedupConfigShouldPersistThroughSystemConfigLegacyGuard() {
        DedupConfigRequest request = new DedupConfigRequest();
        request.setEnabled(0);
        request.setWindowDays(120);
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");
        context.setCurrentUserId(1L);
        when(systemConfigService.getBoolean(ClueManagementServiceImpl.CLUE_DEDUP_ENABLED_KEY, true))
                .thenReturn(false);
        when(systemConfigService.getString(
                ClueManagementServiceImpl.CLUE_DEDUP_WINDOW_DAYS_KEY,
                String.valueOf(ClueManagementServiceImpl.DEFAULT_DEDUP_WINDOW_DAYS)))
                .thenReturn("120");
        when(systemConfigService.listConfigs("clue.dedup.")).thenReturn(List.of());

        DedupConfigResponse response = clueManagementService.saveDedupConfig(request, context);

        ArgumentCaptor<SystemConfigDtos.SaveConfigRequest> captor =
                ArgumentCaptor.forClass(SystemConfigDtos.SaveConfigRequest.class);
        verify(systemConfigService, times(2)).saveLegacyConfig(captor.capture(), eq(context));
        assertThat(captor.getAllValues())
                .extracting(SystemConfigDtos.SaveConfigRequest::getConfigKey)
                .containsExactly(
                        ClueManagementServiceImpl.CLUE_DEDUP_ENABLED_KEY,
                        ClueManagementServiceImpl.CLUE_DEDUP_WINDOW_DAYS_KEY);
        assertThat(captor.getAllValues())
                .extracting(SystemConfigDtos.SaveConfigRequest::getConfigValue)
                .containsExactly("false", "120");
        assertThat(response.getEnabled()).isEqualTo(0);
        assertThat(response.getWindowDays()).isEqualTo(120);
        assertThat(response.getRuntimeConsumed()).isEqualTo(1);
    }
}
