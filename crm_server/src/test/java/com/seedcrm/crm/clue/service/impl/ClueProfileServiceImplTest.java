package com.seedcrm.crm.clue.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileResponse;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileUpsertRequest;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.FollowRecordResponse;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.entity.ClueProfile;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.clue.mapper.ClueProfileMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClueProfileServiceImplTest {

    @Mock
    private ClueProfileMapper clueProfileMapper;

    @Mock
    private ClueMapper clueMapper;

    private ClueProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClueProfileServiceImpl(clueProfileMapper, clueMapper, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void saveProfileShouldPersistOperationalFields() {
        Clue clue = new Clue();
        clue.setId(1L);
        when(clueMapper.selectById(1L)).thenReturn(clue);
        when(clueProfileMapper.selectOne(any())).thenReturn(null).thenAnswer(invocation -> savedProfile());
        when(clueProfileMapper.insert(any(ClueProfile.class))).thenReturn(1);

        ClueProfileUpsertRequest request = new ClueProfileUpsertRequest();
        request.setClueId(1L);
        request.setDisplayName("张三");
        request.setPhone("13900001111");
        request.setCallStatus("connected");
        request.setLeadStage("deposit_paid");
        request.setLeadTags(List.of("定金", "已付款", "定金"));
        request.setFollowRecords(List.of(new FollowRecordResponse(1L, "已沟通到店时间", LocalDateTime.now())));
        request.setIntendedStoreName("静安门店");

        ClueProfileResponse response = service.saveProfile(request, 7L);

        assertThat(response.getClueId()).isEqualTo(1L);
        assertThat(response.getCallStatus()).isEqualTo("CONNECTED");
        assertThat(response.getLeadStage()).isEqualTo("DEPOSIT_PAID");
        assertThat(response.getLeadTags()).containsExactly("定金", "已付款");
        assertThat(response.getFollowRecords()).hasSize(1);
    }

    @Test
    void saveProfileShouldRetryWhenConcurrentFirstInsertWins() {
        Clue clue = new Clue();
        clue.setId(1L);
        when(clueMapper.selectById(1L)).thenReturn(clue);
        ClueProfile existing = savedProfile();
        when(clueProfileMapper.selectOne(any())).thenReturn(null, existing, existing);
        when(clueProfileMapper.insert(any(ClueProfile.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(clueProfileMapper.updateById(any(ClueProfile.class))).thenReturn(1);

        ClueProfileUpsertRequest request = new ClueProfileUpsertRequest();
        request.setClueId(1L);
        request.setCallStatus("connected");
        request.setLeadStage("intent");

        ClueProfileResponse response = service.saveProfile(request, 7L);

        assertThat(response.getLeadStage()).isEqualTo("INTENT");
        verify(clueProfileMapper).updateById(any(ClueProfile.class));
    }

    @Test
    void saveProfileShouldRejectStaleUpdate() {
        Clue clue = new Clue();
        clue.setId(1L);
        when(clueMapper.selectById(1L)).thenReturn(clue);
        ClueProfile existing = savedProfile();
        LocalDateTime currentUpdatedAt = LocalDateTime.of(2026, 5, 2, 19, 0);
        existing.setUpdatedAt(currentUpdatedAt);
        when(clueProfileMapper.selectOne(any())).thenReturn(existing);

        ClueProfileUpsertRequest request = new ClueProfileUpsertRequest();
        request.setClueId(1L);
        request.setUpdatedAt(currentUpdatedAt.minusSeconds(1));

        assertThatThrownBy(() -> service.saveProfile(request, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("please refresh");
        verify(clueProfileMapper, never()).updateById(any(ClueProfile.class));
    }

    private ClueProfile savedProfile() {
        ClueProfile profile = new ClueProfile();
        profile.setId(10L);
        profile.setClueId(1L);
        profile.setDisplayName("张三");
        profile.setPhone("13900001111");
        profile.setCallStatus("CONNECTED");
        profile.setLeadStage("DEPOSIT_PAID");
        profile.setLeadTagsJson("[\"定金\",\"已付款\"]");
        profile.setFollowRecordsJson("[{\"id\":1,\"content\":\"已沟通到店时间\",\"createdAt\":\"2026-05-02 19:00:00\"}]");
        profile.setIntendedStoreName("静安门店");
        profile.setUpdatedBy(7L);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        return profile;
    }
}
