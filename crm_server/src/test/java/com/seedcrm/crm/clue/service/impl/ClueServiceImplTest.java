package com.seedcrm.crm.clue.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.management.dto.ClueManagementDtos.DedupConfigResponse;
import com.seedcrm.crm.clue.management.service.ClueManagementService;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClueServiceImplTest {

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private DistributorService distributorService;

    @Mock
    private ClueManagementService clueManagementService;

    private ClueServiceImpl clueService;

    @BeforeEach
    void setUp() {
        clueService = new ClueServiceImpl(clueMapper, distributorService, clueManagementService);
    }

    @Test
    void addClueShouldDefaultRawDataAndNormalizeDistributionSource() {
        when(clueMapper.insert(any(Clue.class))).thenAnswer(invocation -> {
            Clue clue = invocation.getArgument(0);
            clue.setId(1L);
            return 1;
        });
        when(clueManagementService.autoAssignIfEnabled(any(Clue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Clue request = new Clue();
        request.setPhone("+86 138-0013-8000");
        request.setWechat(" DouYin_User ");
        request.setSource("distribution");
        request.setSourceChannel("distribution");

        Clue created = clueService.addClue(request);

        assertThat(created.getSourceChannel()).isEqualTo(SourceChannel.DISTRIBUTOR.name());
        assertThat(created.getSource()).isEqualTo("distribution");
        assertThat(created.getPhone()).isEqualTo("13800138000");
        assertThat(created.getWechat()).isEqualTo("douyin_user");
        assertThat(created.getRawData()).isEqualTo("{}");
    }

    @Test
    void createDistributorClueShouldUseDistributionSourcePayload() {
        when(clueMapper.insert(any(Clue.class))).thenAnswer(invocation -> {
            Clue clue = invocation.getArgument(0);
            clue.setId(2L);
            return 1;
        });
        when(clueManagementService.autoAssignIfEnabled(any(Clue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Clue created = clueService.createDistributorClue(99L, "13800138001", "Alice");

        verify(distributorService).getByIdOrThrow(99L);
        assertThat(created.getSourceChannel()).isEqualTo(SourceChannel.DISTRIBUTOR.name());
        assertThat(created.getSource()).isEqualTo("distribution");
        assertThat(created.getRawData()).contains("distribution");
    }

    @Test
    void recycleClueShouldReturnClueToPublicPool() {
        Clue existing = new Clue();
        existing.setId(3L);
        existing.setCurrentOwnerId(1001L);
        existing.setIsPublic(0);
        existing.setStatus("assigned");
        existing.setUpdatedAt(LocalDateTime.now().minusDays(1));
        when(clueMapper.selectById(3L)).thenReturn(existing);
        when(clueMapper.updateById(any(Clue.class))).thenReturn(1);

        Clue recycled = clueService.recycleClue(3L);

        assertThat(recycled.getCurrentOwnerId()).isNull();
        assertThat(recycled.getIsPublic()).isEqualTo(1);
        assertThat(recycled.getStatus()).isEqualTo("new");
    }

    @Test
    void addClueShouldInvokeAutoAssignmentWhenEnabled() {
        when(clueMapper.insert(any(Clue.class))).thenAnswer(invocation -> {
            Clue clue = invocation.getArgument(0);
            clue.setId(8L);
            return 1;
        });
        when(clueManagementService.autoAssignIfEnabled(any(Clue.class))).thenAnswer(invocation -> {
            Clue clue = invocation.getArgument(0);
            clue.setCurrentOwnerId(1001L);
            clue.setStatus("assigned");
            clue.setIsPublic(0);
            return clue;
        });

        Clue request = new Clue();
        request.setPhone("13800138088");
        request.setSourceChannel("DOUYIN");

        Clue created = clueService.addClue(request);

        assertThat(created.getCurrentOwnerId()).isEqualTo(1001L);
        assertThat(created.getStatus()).isEqualTo("assigned");
        verify(clueManagementService).autoAssignIfEnabled(any(Clue.class));
    }

    @Test
    void addClueShouldMergeSameCustomerWithinDedupWindow() {
        when(clueManagementService.getDedupConfig()).thenReturn(new DedupConfigResponse(1, 90, null));
        Clue existing = new Clue();
        existing.setId(10L);
        existing.setPhone("13800138099");
        existing.setSourceChannel("DOUYIN");
        existing.setCreatedAt(LocalDateTime.now().minusDays(2));
        when(clueMapper.selectOne(any())).thenReturn(existing);

        Clue request = new Clue();
        request.setPhone("13800138099");
        request.setName("新留资");
        request.setSourceChannel("DOUYIN");

        Clue merged = clueService.addClue(request);

        assertThat(merged.getId()).isEqualTo(10L);
        assertThat(merged.getName()).isEqualTo("新留资");
        verify(clueMapper, never()).insert(any(Clue.class));
        verify(clueManagementService, never()).autoAssignIfEnabled(any(Clue.class));
    }

    @Test
    void addClueShouldKeepIdentityGuardWhenDedupDisabled() {
        when(clueManagementService.getDedupConfig()).thenReturn(new DedupConfigResponse(0, 90, null));
        Clue existing = new Clue();
        existing.setId(11L);
        existing.setPhone("13800138100");
        when(clueMapper.selectOne(any())).thenReturn(existing);

        Clue request = new Clue();
        request.setPhone("13800138100");
        request.setSourceChannel("DOUYIN");

        Clue merged = clueService.addClue(request);

        assertThat(merged.getId()).isEqualTo(11L);
        verify(clueMapper, never()).insert(any(Clue.class));
        verify(clueManagementService, never()).autoAssignIfEnabled(any(Clue.class));
    }

    @Test
    void addClueShouldKeepIdentityGuardOutsideDedupWindow() {
        when(clueManagementService.getDedupConfig()).thenReturn(new DedupConfigResponse(1, 90, null));
        Clue existing = new Clue();
        existing.setId(12L);
        existing.setPhone("13800138101");
        existing.setSourceChannel("DOUYIN");
        existing.setCreatedAt(LocalDateTime.now().minusDays(180));
        when(clueMapper.selectOne(any())).thenReturn(null, existing);

        Clue request = new Clue();
        request.setPhone("13800138101");
        request.setSourceChannel("DOUYIN");

        Clue merged = clueService.addClue(request);

        assertThat(merged.getId()).isEqualTo(12L);
        verify(clueMapper, never()).insert(any(Clue.class));
        verify(clueManagementService, never()).autoAssignIfEnabled(any(Clue.class));
    }
}
