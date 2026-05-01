package com.seedcrm.crm.systemflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.systemflow.dto.SystemFlowDtos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemFlowServiceImplTest {

    @Test
    void simulateShouldAllowConfiguredTransitionAndReturnNextNode() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());

        SystemFlowDtos.SimulateRequest request = new SystemFlowDtos.SimulateRequest();
        request.setFlowCode("ORDER_MAIN_FLOW");
        request.setCurrentNodeCode("PLAN_FINISHED");
        request.setActionCode("ORDER_COMPLETE");
        request.setRoleCode("STORE_SERVICE");

        SystemFlowDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getNextNodeCode()).isEqualTo("ORDER_USED");
        assertThat(response.getMatchedTriggers()).extracting(SystemFlowDtos.TriggerResponse::getTargetCode)
                .containsExactly("SALARY_SETTLEMENT_METADATA");
        assertThat(response.getPath()).contains("PLAN_FINISHED:服务完成", "ORDER_USED:订单已使用");
    }

    @Test
    void simulateShouldRejectUnknownTransitionWithoutChangingBusinessState() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());

        SystemFlowDtos.SimulateRequest request = new SystemFlowDtos.SimulateRequest();
        request.setFlowCode("ORDER_MAIN_FLOW");
        request.setCurrentNodeCode("VERIFY");
        request.setActionCode("ORDER_COMPLETE");

        SystemFlowDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getNextNodeCode()).isNull();
        assertThat(response.getMessage()).contains("不会改动真实订单");
        assertThat(response.getPath()).endsWith("VERIFY:门店核销");
    }

    @Test
    void simulateShouldRejectRoleMismatchWithoutChangingBusinessState() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());

        SystemFlowDtos.SimulateRequest request = new SystemFlowDtos.SimulateRequest();
        request.setFlowCode("ORDER_MAIN_FLOW");
        request.setCurrentNodeCode("PLAN_FINISHED");
        request.setActionCode("ORDER_COMPLETE");
        request.setRoleCode("FINANCE");

        SystemFlowDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getNextNodeCode()).isNull();
        assertThat(response.getMessage()).contains("角色不匹配");
        assertThat(response.getPath()).endsWith("PLAN_FINISHED:服务完成");
    }

    @Test
    void simulateShouldAllowSystemRoleForSidecarRuntime() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());

        SystemFlowDtos.SimulateRequest request = new SystemFlowDtos.SimulateRequest();
        request.setFlowCode("ORDER_MAIN_FLOW");
        request.setCurrentNodeCode("PLAN_FINISHED");
        request.setActionCode("ORDER_COMPLETE");
        request.setRoleCode("SYSTEM");

        SystemFlowDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getNextNodeCode()).isEqualTo("ORDER_USED");
    }

    @Test
    void orderMainFlowShouldKeepRequiredBusinessChainOrder() {
        SystemFlowDtos.DetailResponse detail = buildOrderMainFlow();

        assertThat(detail.getNodes()).extracting(SystemFlowDtos.NodeResponse::getNodeCode)
                .containsSubsequence(
                        "CLUE_INTAKE",
                        "CLUE_FOLLOW",
                        "CUSTOMER_CREATED",
                        "ORDER_PAID",
                        "APPOINTMENT",
                        "VERIFY",
                        "PLAN_CREATED",
                        "PLAN_ARRIVED",
                        "SERVICE_FORM_CONFIRMED",
                        "PLAN_SERVICING",
                        "PLAN_FINISHED",
                        "ORDER_USED");
        assertThat(detail.getNodes()).extracting(SystemFlowDtos.NodeResponse::getDomainCode)
                .containsSubsequence("CLUE", "CUSTOMER", "ORDER", "PLANORDER");
        assertThat(detail.getTransitions()).extracting(SystemFlowDtos.TransitionResponse::getActionCode)
                .contains("PAYMENT_SYNC", "ORDER_APPOINTMENT", "ORDER_VERIFY", "PLAN_CREATE",
                        "SERVICE_FORM_CONFIRM", "PLAN_FINISH");
    }

    @Test
    void saveDraftShouldRejectFlowThatSkipsCustomer() {
        SystemFlowServiceImpl service = new SystemFlowServiceImpl(mock(JdbcTemplate.class));
        SystemFlowDtos.SaveDraftRequest request = new SystemFlowDtos.SaveDraftRequest();
        request.setFlowCode("ORDER_MAIN_FLOW");
        request.setFlowName("订单主流程");
        request.setModuleCode("SETTING");
        request.setBusinessObject("ORDER");
        request.setNodes(List.of(
                node("CLUE", "CLUE_INTAKE", "客资入库", "intake", 10),
                node("ORDER", "ORDER_PAID", "订单已付款", "paid", 20),
                node("ORDER", "ORDER_USED", "订单已使用", "used", 30),
                node("PLANORDER", "PLAN_ARRIVED", "到店", "arrived", 40),
                node("PLANORDER", "PLAN_SERVICING", "服务中", "servicing", 50),
                node("PLANORDER", "PLAN_FINISHED", "服务完成", "finished", 60)));
        request.setTransitions(List.of(transition("CLUE_INTAKE", "ORDER_PAID", "PAYMENT_SYNC")));

        assertThatThrownBy(() -> service.saveDraft(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void saveDraftShouldRejectUnsupportedFlowCodeInV1() {
        SystemFlowServiceImpl service = new SystemFlowServiceImpl(mock(JdbcTemplate.class));
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        request.setFlowCode("CUSTOM_FLOW");

        assertThatThrownBy(() -> service.saveDraft(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ORDER_MAIN_FLOW");
    }

    @Test
    void saveDraftShouldRejectSchedulerDomainInMainChainNodes() {
        SystemFlowServiceImpl service = new SystemFlowServiceImpl(mock(JdbcTemplate.class));
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        request.setNodes(List.of(
                node("CLUE", "CLUE_INTAKE", "客资入库", "intake", 10),
                node("CLUE", "CLUE_ASSIGN", "分配/公海", "assigned", 20),
                node("CLUE", "CLUE_FOLLOW", "跟进", "following", 30),
                node("CUSTOMER", "CUSTOMER_CREATED", "生成客户", "created", 40),
                node("ORDER", "ORDER_PAID", "订单已付款", "paid", 50),
                node("SCHEDULER", "APPOINTMENT", "预约排档", "scheduled", 60),
                node("ORDER", "VERIFY", "门店核销", "paid", 70),
                node("PLANORDER", "PLAN_CREATED", "创建计划单", "arrived", 80),
                node("PLANORDER", "PLAN_ARRIVED", "到店", "arrived", 90),
                node("PLANORDER", "SERVICE_FORM_CONFIRMED", "纸质确认单已确认", "service_form_confirmed", 100),
                node("PLANORDER", "PLAN_SERVICING", "服务中", "servicing", 110),
                node("PLANORDER", "PLAN_FINISHED", "服务完成", "finished", 120, "STORE_SERVICE"),
                node("ORDER", "ORDER_USED", "订单已使用", "used", 130)));

        assertThatThrownBy(() -> service.saveDraft(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Scheduler must stay in trigger metadata");
    }

    @Test
    void saveDraftShouldRejectTransitionGraphThatSkipsCustomer() {
        SystemFlowServiceImpl service = new SystemFlowServiceImpl(mock(JdbcTemplate.class));
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        request.setTransitions(List.of(
                transition("CLUE_INTAKE", "CLUE_ASSIGN", "CLUE_AUTO_ASSIGN"),
                transition("CLUE_ASSIGN", "CLUE_FOLLOW", "CLUE_FOLLOW"),
                transition("CLUE_FOLLOW", "ORDER_PAID", "PAYMENT_SYNC"),
                transition("CUSTOMER_CREATED", "ORDER_PAID", "ORDER_MARK_PAID"),
                transition("ORDER_PAID", "APPOINTMENT", "ORDER_APPOINTMENT"),
                transition("APPOINTMENT", "VERIFY", "ORDER_VERIFY"),
                transition("VERIFY", "PLAN_CREATED", "PLAN_CREATE"),
                transition("PLAN_CREATED", "PLAN_ARRIVED", "PLAN_ARRIVE"),
                transition("PLAN_ARRIVED", "SERVICE_FORM_CONFIRMED", "SERVICE_FORM_CONFIRM"),
                transition("SERVICE_FORM_CONFIRMED", "PLAN_SERVICING", "PLAN_START"),
                transition("PLAN_SERVICING", "PLAN_FINISHED", "PLAN_FINISH"),
                transition("PLAN_FINISHED", "ORDER_USED", "ORDER_COMPLETE")));

        assertThatThrownBy(() -> service.saveDraft(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transition graph must follow Clue -> Customer -> Order -> PlanOrder");
    }

    @Test
    void saveDraftShouldRejectExecutableTriggerTarget() {
        SystemFlowServiceImpl service = new SystemFlowServiceImpl(mock(JdbcTemplate.class));
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        SystemFlowDtos.TriggerResponse trigger = trigger("ORDER_USED", "orderSettlementService.settleCompletedOrder");
        request.setTriggers(List.of(trigger));

        assertThatThrownBy(() -> service.saveDraft(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("metadata code only");
    }

    @Test
    void saveDraftShouldRejectExecutableTriggerMode() {
        SystemFlowServiceImpl service = new SystemFlowServiceImpl(mock(JdbcTemplate.class));
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        SystemFlowDtos.TriggerResponse trigger = trigger("CLUE_INTAKE", "DOUYIN_CLUE_INCREMENTAL");
        trigger.setExecutionMode("ASYNC");
        request.setTriggers(List.of(trigger));

        assertThatThrownBy(() -> service.saveDraft(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("metadata code only");
    }

    @Test
    void previewDiffShouldReturnStructuredChangesWithoutWritingBusinessData() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        request.setDescription("门店履约流程配置说明");
        request.getNodes().get(0).setNodeName("客资统一入库");
        request.setTriggers(new ArrayList<>(request.getTriggers()));
        request.getTriggers().add(trigger("ORDER_USED", "ORDER_SETTLEMENT_METADATA"));

        SystemFlowDtos.DiffPreviewResponse response = service.previewDiff(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getBaseVersionNo()).isEqualTo(1);
        assertThat(response.getItems()).extracting(SystemFlowDtos.DiffItemResponse::getDomain)
                .contains("DEFINITION", "NODE", "TRIGGER");
        assertThat(response.getItems()).extracting(SystemFlowDtos.DiffItemResponse::getImpact)
                .allMatch(impact -> impact.contains("不") || impact.contains("仅") || impact.contains("真实"));
    }

    @Test
    void previewDiffShouldExposeValidationErrorInsteadOfPublishingUnsafeDraft() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());
        SystemFlowDtos.SaveDraftRequest request = validDraftRequest();
        SystemFlowDtos.TriggerResponse trigger = trigger("ORDER_USED", "https://example.com/run");
        request.setTriggers(List.of(trigger));

        SystemFlowDtos.DiffPreviewResponse response = service.previewDiff(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getValidationMessage()).contains("metadata code only");
        assertThat(response.getItems()).extracting(SystemFlowDtos.DiffItemResponse::getDomain)
                .contains("TRIGGER");
    }

    @Test
    void validationReportShouldPassStandardOrderMainFlow() {
        SystemFlowServiceImpl service = new StubSystemFlowService(buildOrderMainFlow());

        SystemFlowDtos.ValidationReportResponse response = service.validationReport("ORDER_MAIN_FLOW", null);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getVersionNo()).isEqualTo(1);
        assertThat(response.getItems()).extracting(SystemFlowDtos.ValidationItemResponse::getCheckCode)
                .contains("REQUIRED_DOMAIN", "ORDER_CORE_STATES", "PLANORDER_CORE_STATES", "TRIGGER_REFERENCES");
        assertThat(response.getItems()).allMatch(SystemFlowDtos.ValidationItemResponse::isPassed);
    }

    @Test
    void validationReportShouldExposeMainChainBreakageWithoutWritingBusinessData() {
        SystemFlowDtos.DetailResponse invalidDetail = buildOrderMainFlow();
        List<SystemFlowDtos.NodeResponse> nodes = new ArrayList<>(invalidDetail.getNodes());
        nodes.set(3, node("SCHEDULER", "CUSTOMER_CREATED", "生成客户", "created", 40));
        invalidDetail.setNodes(nodes);
        SystemFlowServiceImpl service = new StubSystemFlowService(invalidDetail);

        SystemFlowDtos.ValidationReportResponse response = service.validationReport("ORDER_MAIN_FLOW", null);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getItems()).filteredOn(item -> !item.isPassed())
                .extracting(SystemFlowDtos.ValidationItemResponse::getCheckCode)
                .contains("REQUIRED_DOMAIN", "ALLOWED_DOMAINS", "DOMAIN_ORDER");
        assertThat(response.getItems()).filteredOn(item -> !item.isPassed())
                .extracting(SystemFlowDtos.ValidationItemResponse::getSuggestion)
                .anyMatch(suggestion -> suggestion.contains("Clue -> Customer -> Order -> PlanOrder"));
    }

    @Test
    void triggerLinkageReportShouldTreatMetadataTargetsAsReadOnlyLinked() {
        SystemFlowDtos.DetailResponse detail = buildOrderMainFlow();
        detail.setTriggers(List.of(
                trigger("PLAN_FINISHED", "ORDER_SETTLEMENT_METADATA"),
                trigger("ORDER_USED", "SALARY_SETTLEMENT_METADATA")));
        SystemFlowServiceImpl service = new StubSystemFlowService(detail);

        SystemFlowDtos.TriggerLinkageReportResponse response = service.triggerLinkageReport("ORDER_MAIN_FLOW", null);

        assertThat(response.isHealthy()).isTrue();
        assertThat(response.getItems()).extracting(SystemFlowDtos.TriggerLinkageItemResponse::getStatus)
                .containsExactly("LINKED", "LINKED");
        assertThat(response.getItems()).extracting(SystemFlowDtos.TriggerLinkageItemResponse::getMessage)
                .allMatch(message -> message.contains("元数据"));
    }

    @Test
    void triggerLinkageReportShouldFlagMissingSchedulerJobAsBlocker() {
        SystemFlowDtos.DetailResponse detail = buildOrderMainFlow();
        detail.setTriggers(List.of(trigger("CLUE_INTAKE", "DOUYIN_CLUE_INCREMENTAL")));
        SystemFlowServiceImpl service = new StubSystemFlowService(detail);

        SystemFlowDtos.TriggerLinkageReportResponse response = service.triggerLinkageReport("ORDER_MAIN_FLOW", null);

        assertThat(response.isHealthy()).isFalse();
        assertThat(response.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo("BLOCKER");
                    assertThat(item.getLinkedModule()).isEqualTo("SCHEDULER");
                    assertThat(item.getMessage()).contains("未找到抖音客资增量同步任务");
                });
    }

    private static SystemFlowDtos.DetailResponse buildOrderMainFlow() {
        SystemFlowDtos.DetailResponse detail = new SystemFlowDtos.DetailResponse();
        SystemFlowDtos.DefinitionResponse definition = new SystemFlowDtos.DefinitionResponse();
        definition.setFlowCode("ORDER_MAIN_FLOW");
        definition.setFlowName("订单主流程");
        definition.setModuleCode("SETTING");
        definition.setBusinessObject("ORDER");
        definition.setDescription("默认订单主链路");
        definition.setEnabled(1);
        detail.setDefinition(definition);

        SystemFlowDtos.VersionResponse version = new SystemFlowDtos.VersionResponse();
        version.setVersionNo(1);
        detail.setVersion(version);

        detail.setNodes(List.of(
                node("CLUE", "CLUE_INTAKE", "客资入库", "intake", 10),
                node("CLUE", "CLUE_ASSIGN", "分配/公海", "assigned", 20),
                node("CLUE", "CLUE_FOLLOW", "跟进", "following", 30),
                node("CUSTOMER", "CUSTOMER_CREATED", "生成客户", "created", 40),
                node("ORDER", "ORDER_PAID", "订单已付款", "paid", 50),
                node("ORDER", "APPOINTMENT", "预约排档", "paid", 60),
                node("ORDER", "VERIFY", "门店核销", "paid", 70),
                node("PLANORDER", "PLAN_CREATED", "创建计划单", "arrived", 80),
                node("PLANORDER", "PLAN_ARRIVED", "到店", "arrived", 90),
                node("PLANORDER", "SERVICE_FORM_CONFIRMED", "纸质确认单已确认", "service_form_confirmed", 100),
                node("PLANORDER", "PLAN_SERVICING", "服务中", "servicing", 110),
                node("PLANORDER", "PLAN_FINISHED", "服务完成", "finished", 120, "STORE_SERVICE"),
                node("ORDER", "ORDER_USED", "订单已使用", "used", 130)));
        detail.setTransitions(List.of(
                transition("CLUE_INTAKE", "CLUE_ASSIGN", "CLUE_AUTO_ASSIGN"),
                transition("CLUE_ASSIGN", "CLUE_FOLLOW", "CLUE_FOLLOW"),
                transition("CLUE_FOLLOW", "CUSTOMER_CREATED", "PAYMENT_SYNC"),
                transition("CUSTOMER_CREATED", "ORDER_PAID", "ORDER_MARK_PAID"),
                transition("ORDER_PAID", "APPOINTMENT", "ORDER_APPOINTMENT"),
                transition("APPOINTMENT", "VERIFY", "ORDER_VERIFY"),
                transition("VERIFY", "PLAN_CREATED", "PLAN_CREATE"),
                transition("PLAN_CREATED", "PLAN_ARRIVED", "PLAN_ARRIVE"),
                transition("PLAN_ARRIVED", "SERVICE_FORM_CONFIRMED", "SERVICE_FORM_CONFIRM"),
                transition("SERVICE_FORM_CONFIRMED", "PLAN_SERVICING", "PLAN_START"),
                transition("PLAN_SERVICING", "PLAN_FINISHED", "PLAN_FINISH"),
                transition("PLAN_FINISHED", "ORDER_USED", "ORDER_COMPLETE")));
        detail.setTriggers(List.of(trigger("ORDER_USED", "SALARY_SETTLEMENT_METADATA")));
        return detail;
    }

    private static SystemFlowDtos.SaveDraftRequest validDraftRequest() {
        SystemFlowDtos.SaveDraftRequest request = new SystemFlowDtos.SaveDraftRequest();
        request.setFlowCode("ORDER_MAIN_FLOW");
        request.setFlowName("订单主流程");
        request.setModuleCode("SETTING");
        request.setBusinessObject("ORDER");
        request.setNodes(buildOrderMainFlow().getNodes());
        request.setTransitions(buildOrderMainFlow().getTransitions());
        request.setTriggers(List.of());
        return request;
    }

    private static SystemFlowDtos.NodeResponse node(String domainCode, String code, String name, String businessState, int sortOrder) {
        SystemFlowDtos.NodeResponse node = new SystemFlowDtos.NodeResponse();
        node.setDomainCode(domainCode);
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setBusinessState(businessState);
        node.setSortOrder(sortOrder);
        return node;
    }

    private static SystemFlowDtos.NodeResponse node(String domainCode, String code, String name, String businessState, int sortOrder, String roleCode) {
        SystemFlowDtos.NodeResponse node = node(domainCode, code, name, businessState, sortOrder);
        node.setRoleCode(roleCode);
        return node;
    }

    private static SystemFlowDtos.TransitionResponse transition(String from, String to, String action) {
        SystemFlowDtos.TransitionResponse transition = new SystemFlowDtos.TransitionResponse();
        transition.setFromNodeCode(from);
        transition.setToNodeCode(to);
        transition.setActionCode(action);
        transition.setActionName(action);
        return transition;
    }

    private static SystemFlowDtos.TriggerResponse trigger(String nodeCode, String targetCode) {
        SystemFlowDtos.TriggerResponse trigger = new SystemFlowDtos.TriggerResponse();
        trigger.setNodeCode(nodeCode);
        trigger.setTargetCode(targetCode);
        trigger.setEnabled(1);
        return trigger;
    }

    private static class StubSystemFlowService extends SystemFlowServiceImpl {
        private final SystemFlowDtos.DetailResponse detail;

        StubSystemFlowService(SystemFlowDtos.DetailResponse detail) {
            super(mock(JdbcTemplate.class));
            this.detail = detail;
        }

        @Override
        public SystemFlowDtos.DetailResponse detail(String flowCode, Long versionId) {
            return detail;
        }
    }
}
