package com.seedcrm.crm.systemflow.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.systemflow.dto.SystemFlowDtos;
import com.seedcrm.crm.systemflow.service.SystemFlowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SystemFlowRuntimeBridgeTest {

    @Mock
    private SystemFlowService systemFlowService;

    @Mock
    private SystemConfigService systemConfigService;

    private SystemFlowRuntimeBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new SystemFlowRuntimeBridge(systemFlowService, systemConfigService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recordOrderActionShouldDoNothingWhenRuntimeDisabled() {
        when(systemConfigService.getBoolean("workflow.system_flow_runtime.enabled", false)).thenReturn(false);

        bridge.recordOrderAction(order(), "ORDER_PAID", "ORDER_APPOINTMENT", 7L, "STORE_SERVICE", "appointment");

        verifyNoInteractions(systemFlowService);
    }

    @Test
    void recordOrderActionShouldStartAndTransitionImmediatelyWithoutTransaction() {
        when(systemConfigService.getBoolean("workflow.system_flow_runtime.enabled", false)).thenReturn(true);
        SystemFlowDtos.InstanceResponse instance = new SystemFlowDtos.InstanceResponse();
        instance.setId(99L);
        when(systemFlowService.startInstance(any(SystemFlowDtos.StartInstanceRequest.class), any())).thenReturn(instance);

        bridge.recordOrderAction(order(), "ORDER_PAID", "ORDER_APPOINTMENT", 7L, "STORE_SERVICE", "appointment");

        ArgumentCaptor<SystemFlowDtos.StartInstanceRequest> startCaptor =
                ArgumentCaptor.forClass(SystemFlowDtos.StartInstanceRequest.class);
        verify(systemFlowService).startInstance(startCaptor.capture(), any());
        assertThatCode(() -> {
            SystemFlowDtos.StartInstanceRequest request = startCaptor.getValue();
            org.assertj.core.api.Assertions.assertThat(request.getFlowCode()).isEqualTo("ORDER_MAIN_FLOW");
            org.assertj.core.api.Assertions.assertThat(request.getBusinessObject()).isEqualTo("ORDER");
            org.assertj.core.api.Assertions.assertThat(request.getBusinessId()).isEqualTo(10L);
            org.assertj.core.api.Assertions.assertThat(request.getStartNodeCode()).isEqualTo("ORDER_PAID");
        }).doesNotThrowAnyException();

        ArgumentCaptor<SystemFlowDtos.TransitionInstanceRequest> transitionCaptor =
                ArgumentCaptor.forClass(SystemFlowDtos.TransitionInstanceRequest.class);
        verify(systemFlowService).transitionInstance(transitionCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(transitionCaptor.getValue().getInstanceId()).isEqualTo(99L);
        org.assertj.core.api.Assertions.assertThat(transitionCaptor.getValue().getActionCode()).isEqualTo("ORDER_APPOINTMENT");
        org.assertj.core.api.Assertions.assertThat(transitionCaptor.getValue().getRemark()).contains("STORE_SERVICE/7");
    }

    @Test
    void recordOrderActionShouldRunOnlyAfterCommitWhenTransactionActive() {
        when(systemConfigService.getBoolean("workflow.system_flow_runtime.enabled", false)).thenReturn(true);
        SystemFlowDtos.InstanceResponse instance = new SystemFlowDtos.InstanceResponse();
        instance.setId(101L);
        when(systemFlowService.startInstance(any(SystemFlowDtos.StartInstanceRequest.class), any())).thenReturn(instance);
        TransactionSynchronizationManager.initSynchronization();

        bridge.recordOrderAction(order(), "ORDER_PAID", "ORDER_APPOINTMENT", 7L, "STORE_SERVICE", "appointment");

        verify(systemFlowService, never()).startInstance(any(), any());
        TransactionSynchronizationManager.getSynchronizations().forEach(item -> item.afterCommit());
        verify(systemFlowService).startInstance(any(SystemFlowDtos.StartInstanceRequest.class), any());
        verify(systemFlowService).transitionInstance(any(SystemFlowDtos.TransitionInstanceRequest.class), any());
    }

    @Test
    void recordOrderActionShouldSwallowSidecarException() {
        when(systemConfigService.getBoolean("workflow.system_flow_runtime.enabled", false)).thenReturn(true);
        when(systemFlowService.startInstance(any(SystemFlowDtos.StartInstanceRequest.class), any()))
                .thenThrow(new IllegalStateException("sidecar failed"));

        assertThatCode(() -> bridge.recordOrderAction(order(), "ORDER_PAID", "ORDER_APPOINTMENT",
                7L, "STORE_SERVICE", "appointment")).doesNotThrowAnyException();

        verify(systemFlowService).startInstance(any(SystemFlowDtos.StartInstanceRequest.class), any());
        verify(systemFlowService, never()).transitionInstance(any(SystemFlowDtos.TransitionInstanceRequest.class), any());
    }

    @Test
    void recordOrderActionShouldIgnoreInvalidPayload() {
        when(systemConfigService.getBoolean("workflow.system_flow_runtime.enabled", false)).thenReturn(true);

        bridge.recordOrderAction(new Order(), "ORDER_PAID", "ORDER_APPOINTMENT", 7L, "STORE_SERVICE", "appointment");
        bridge.recordOrderAction(order(), "ORDER_PAID", "", 7L, "STORE_SERVICE", "appointment");

        verifyNoInteractions(systemFlowService);
    }

    private Order order() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("ORD-10");
        order.setRemark("Alice");
        return order;
    }
}
