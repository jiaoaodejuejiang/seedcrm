package com.seedcrm.crm.clue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileResponse;
import com.seedcrm.crm.clue.dto.ClueProfileDtos.ClueProfileUpsertRequest;
import com.seedcrm.crm.clue.dto.DistributorClueCreateRequest;
import com.seedcrm.crm.clue.service.ClueProfileService;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.permission.support.CluePermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClueControllerTest {

    @Mock
    private ClueService clueService;

    @Mock
    private ClueProfileService clueProfileService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private CluePermissionGuard cluePermissionGuard;

    private ClueController clueController;

    @BeforeEach
    void setUp() {
        clueController = new ClueController(clueService, clueProfileService, permissionRequestContextResolver, cluePermissionGuard);
    }

    @Test
    void distributionClueEndpointShouldBeDisabledForSchemeB() {
        DistributorClueCreateRequest request = new DistributorClueCreateRequest();
        request.setDistributorId(1L);
        request.setPhone("13800000000");

        assertThatThrownBy(() -> clueController.addDistributionClue(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GONE));

        verify(clueService, never()).createDistributorClue(1L, "13800000000", null);
    }

    @Test
    void saveProfileShouldRequireUpdatePermission() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setCurrentUserId(7L);
        when(permissionRequestContextResolver.resolve(null)).thenReturn(context);
        ClueProfileUpsertRequest request = new ClueProfileUpsertRequest();
        request.setClueId(1L);
        when(clueProfileService.saveProfile(request, 7L)).thenReturn(new ClueProfileResponse());

        clueController.saveProfile(request, null);

        verify(cluePermissionGuard).checkUpdate(context, 1L);
        verify(clueProfileService).saveProfile(request, 7L);
    }
}
