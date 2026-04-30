package com.seedcrm.crm.clue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.seedcrm.crm.clue.dto.DistributorClueCreateRequest;
import com.seedcrm.crm.clue.service.ClueService;
import com.seedcrm.crm.permission.support.CluePermissionGuard;
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
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private CluePermissionGuard cluePermissionGuard;

    private ClueController clueController;

    @BeforeEach
    void setUp() {
        clueController = new ClueController(clueService, permissionRequestContextResolver, cluePermissionGuard);
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
}
