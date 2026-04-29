package com.seedcrm.crm.salary.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.salary.dto.SalarySettlementPolicyDtos;
import com.seedcrm.crm.salary.entity.SalarySettlementPolicy;
import com.seedcrm.crm.salary.mapper.SalarySettlementPolicyMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalarySettlementPolicyServiceImplTest {

    @Mock
    private SalarySettlementPolicyMapper policyMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SalarySettlementPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SalarySettlementPolicyServiceImpl(policyMapper, jdbcTemplate);
    }

    @Test
    void simulateShouldMatchInternalStaffMonthlyLedgerPolicy() {
        when(policyMapper.selectList(any())).thenReturn(List.of(policy(
                1L,
                "内部员工按月记账",
                "INTERNAL_STAFF",
                "ROLE",
                "ONLINE_CUSTOMER_SERVICE,STORE_SERVICE",
                null,
                null,
                "MONTHLY",
                "LEDGER_ONLY",
                10)));

        SalarySettlementPolicyDtos.SimulateRequest request = new SalarySettlementPolicyDtos.SimulateRequest();
        request.setSubjectType("INTERNAL_STAFF");
        request.setRoleCode("STORE_SERVICE");
        request.setAmount(new BigDecimal("880.00"));

        SalarySettlementPolicyDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isMatched()).isTrue();
        assertThat(response.getSettlementCycle()).isEqualTo("MONTHLY");
        assertThat(response.getSettlementMode()).isEqualTo("LEDGER_ONLY");
        assertThat(response.isLedgerOnly()).isTrue();
    }

    @Test
    void simulateShouldMatchDistributorSmallAmountDirectWithdrawPolicy() {
        when(policyMapper.selectList(any())).thenReturn(List.of(
                policy(2L, "分销小额自动提现", "DISTRIBUTOR", "AMOUNT", "",
                        new BigDecimal("0.00"), new BigDecimal("2999.99"), "INSTANT", "WITHDRAW_DIRECT", 20),
                policy(3L, "分销大额提现审核", "DISTRIBUTOR", "AMOUNT", "",
                        new BigDecimal("3000.00"), null, "INSTANT", "WITHDRAW_AUDIT", 30)));

        SalarySettlementPolicyDtos.SimulateRequest request = new SalarySettlementPolicyDtos.SimulateRequest();
        request.setSubjectType("DISTRIBUTOR");
        request.setAmount(new BigDecimal("120.00"));

        SalarySettlementPolicyDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isMatched()).isTrue();
        assertThat(response.getSettlementCycle()).isEqualTo("INSTANT");
        assertThat(response.getSettlementMode()).isEqualTo("WITHDRAW_DIRECT");
        assertThat(response.isAutoApprove()).isTrue();
        assertThat(response.isRequiresAudit()).isFalse();
    }

    @Test
    void simulateShouldMatchDistributorLargeAmountAuditPolicy() {
        when(policyMapper.selectList(any())).thenReturn(List.of(
                policy(2L, "分销小额自动提现", "DISTRIBUTOR", "AMOUNT", "",
                        new BigDecimal("0.00"), new BigDecimal("2999.99"), "INSTANT", "WITHDRAW_DIRECT", 20),
                policy(3L, "分销大额提现审核", "DISTRIBUTOR", "AMOUNT", "",
                        new BigDecimal("3000.00"), null, "INSTANT", "WITHDRAW_AUDIT", 30)));

        SalarySettlementPolicyDtos.SimulateRequest request = new SalarySettlementPolicyDtos.SimulateRequest();
        request.setSubjectType("DISTRIBUTOR");
        request.setAmount(new BigDecimal("5000.00"));

        SalarySettlementPolicyDtos.SimulateResponse response = service.simulate(request);

        assertThat(response.isMatched()).isTrue();
        assertThat(response.getSettlementMode()).isEqualTo("WITHDRAW_AUDIT");
        assertThat(response.isRequiresAudit()).isTrue();
    }

    @Test
    void publishShouldRejectOverlappingAmountRules() {
        SalarySettlementPolicy candidate = policy(9L, "新分销小额规则", "DISTRIBUTOR", "AMOUNT", "",
                new BigDecimal("100.00"), new BigDecimal("500.00"), "INSTANT", "WITHDRAW_DIRECT", 15);
        candidate.setStatus("DRAFT");
        when(policyMapper.selectById(9L)).thenReturn(candidate);
        when(policyMapper.selectList(any())).thenReturn(List.of(policy(2L, "已有小额规则", "DISTRIBUTOR", "AMOUNT", "",
                new BigDecimal("0.00"), new BigDecimal("2999.99"), "INSTANT", "WITHDRAW_DIRECT", 20)));

        SalarySettlementPolicyDtos.PolicyStatusRequest request = new SalarySettlementPolicyDtos.PolicyStatusRequest();
        request.setPolicyId(9L);

        assertThatThrownBy(() -> service.publish(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重叠");
    }

    private SalarySettlementPolicy policy(Long id,
                                          String name,
                                          String subjectType,
                                          String scopeType,
                                          String roleCodes,
                                          BigDecimal amountMin,
                                          BigDecimal amountMax,
                                          String cycle,
                                          String mode,
                                          int priority) {
        SalarySettlementPolicy policy = new SalarySettlementPolicy();
        policy.setId(id);
        policy.setPolicyName(name);
        policy.setSubjectType(subjectType);
        policy.setScopeType(scopeType);
        policy.setRoleCodes(roleCodes);
        policy.setAmountMin(amountMin);
        policy.setAmountMax(amountMax);
        policy.setSettlementCycle(cycle);
        policy.setSettlementMode(mode);
        policy.setPriority(priority);
        policy.setStatus("PUBLISHED");
        policy.setEnabled(1);
        return policy;
    }
}
