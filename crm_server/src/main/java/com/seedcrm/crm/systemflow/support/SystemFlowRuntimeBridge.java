package com.seedcrm.crm.systemflow.support;

import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.systemflow.dto.SystemFlowDtos;
import com.seedcrm.crm.systemflow.service.SystemFlowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class SystemFlowRuntimeBridge {

    private static final String FLOW_CODE = "ORDER_MAIN_FLOW";
    private static final String SYSTEM_ROLE_CODE = "SYSTEM";

    private final SystemFlowService systemFlowService;
    private final SystemConfigService systemConfigService;

    public SystemFlowRuntimeBridge(SystemFlowService systemFlowService,
                                   SystemConfigService systemConfigService) {
        this.systemFlowService = systemFlowService;
        this.systemConfigService = systemConfigService;
    }

    public void recordOrderAction(Order order,
                                  String startNodeCode,
                                  String actionCode,
                                  PermissionRequestContext context,
                                  String summary) {
        if (!systemConfigService.getBoolean("workflow.system_flow_runtime.enabled", false)) {
            return;
        }
        if (order == null || order.getId() == null || !StringUtils.hasText(actionCode)) {
            return;
        }
        Long orderId = order.getId();
        String orderNo = order.getOrderNo();
        String remark = order.getRemark();
        PermissionRequestContext runtimeContext = systemContext();
        String actionSummary = appendActor(summary, context);
        try {
            afterCommit(() -> {
                SystemFlowDtos.StartInstanceRequest startRequest = new SystemFlowDtos.StartInstanceRequest();
                startRequest.setFlowCode(FLOW_CODE);
                startRequest.setBusinessObject("ORDER");
                startRequest.setBusinessId(orderId);
                startRequest.setStartNodeCode(startNodeCode);
                startRequest.setTitle(buildOrderTitle(orderId, orderNo, remark));
                startRequest.setRemark("业务动作触发流程运行态旁路记录");
                SystemFlowDtos.InstanceResponse instance = systemFlowService.startInstance(startRequest, runtimeContext);

                SystemFlowDtos.TransitionInstanceRequest transitionRequest = new SystemFlowDtos.TransitionInstanceRequest();
                transitionRequest.setInstanceId(instance.getId());
                transitionRequest.setActionCode(actionCode);
                transitionRequest.setRemark(actionSummary);
                systemFlowService.transitionInstance(transitionRequest, runtimeContext);
            }, orderId, actionCode);
        } catch (Exception exception) {
            log.warn("system flow runtime bridge ignored action, orderId={}, actionCode={}, reason={}",
                    orderId, actionCode, exception.getMessage());
        }
    }

    public void recordOrderAction(Order order,
                                  String startNodeCode,
                                  String actionCode,
                                  Long operatorUserId,
                                  String operatorRoleCode,
                                  String summary) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setCurrentUserId(operatorUserId);
        context.setRoleCode(operatorRoleCode);
        recordOrderAction(order, startNodeCode, actionCode, context, summary);
    }

    private void afterCommit(Runnable task, Long orderId, String actionCode) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (Exception exception) {
                log.warn("system flow runtime bridge ignored action after commit, orderId={}, actionCode={}, reason={}",
                        orderId, actionCode, exception.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guarded.run();
            }
        });
    }

    private String buildOrderTitle(Long orderId, String orderNoValue, String remark) {
        String orderNo = StringUtils.hasText(orderNoValue) ? orderNoValue : String.valueOf(orderId);
        String customer = StringUtils.hasText(remark) ? remark : "";
        return StringUtils.hasText(customer) ? "订单 " + orderNo + " / " + customer : "订单 " + orderNo;
    }

    private PermissionRequestContext systemContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(SYSTEM_ROLE_CODE);
        return context;
    }

    private String appendActor(String summary, PermissionRequestContext context) {
        String base = StringUtils.hasText(summary) ? summary.trim() : "业务动作已完成";
        if (context == null || (!StringUtils.hasText(context.getRoleCode()) && context.getCurrentUserId() == null)) {
            return base;
        }
        String roleCode = StringUtils.hasText(context.getRoleCode()) ? context.getRoleCode() : "--";
        String userId = context.getCurrentUserId() == null ? "--" : String.valueOf(context.getCurrentUserId());
        return base + "；实际操作人：" + roleCode + "/" + userId;
    }
}
