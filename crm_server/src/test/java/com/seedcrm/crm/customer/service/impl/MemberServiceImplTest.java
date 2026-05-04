package com.seedcrm.crm.customer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.SensitiveDataProjectionService;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private CustomerWecomRelationMapper customerWecomRelationMapper;

    @Mock
    private StaffDirectoryService staffDirectoryService;

    @Mock
    private SystemConfigService systemConfigService;

    private MemberServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MemberServiceImpl(
                customerMapper,
                orderMapper,
                planOrderMapper,
                customerWecomRelationMapper,
                staffDirectoryService,
                new SensitiveDataProjectionService(new ObjectMapper(), systemConfigService));
    }

    @Test
    void shouldLimitPrivateDomainMembersToBoundWecomCustomers() {
        when(customerMapper.selectList(any())).thenReturn(List.of(customer(1L), customer(2L)));
        when(orderMapper.selectList(any())).thenReturn(List.of(order(10L, 1L), order(20L, 2L)));
        when(customerWecomRelationMapper.selectList(any())).thenReturn(List.of(
                relation(1L, "private_domain"),
                relation(2L, "other_wecom")));
        when(planOrderMapper.selectList(any())).thenReturn(List.of());
        when(staffDirectoryService.getWecomAccount(1101L)).thenReturn("private_domain");

        var response = service.listMembers("distribution", null, null, null, 1, 30, privateDomainContext());

        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords()).extracting("customerId").containsExactly(1L);
        assertThat(response.getRecords().get(0).getLatestOrderAmount()).isNull();
        assertThat(response.getRecords().get(0).getTotalOrderAmount()).isNull();
    }

    @Test
    void shouldAllowAdminToViewAllPaidMembers() {
        when(customerMapper.selectList(any())).thenReturn(List.of(customer(1L), customer(2L)));
        when(orderMapper.selectList(any())).thenReturn(List.of(order(10L, 1L), order(20L, 2L)));
        when(customerWecomRelationMapper.selectList(any())).thenReturn(List.of(
                relation(1L, "private_domain"),
                relation(2L, "other_wecom")));
        when(planOrderMapper.selectList(any())).thenReturn(List.of(planOrder(100L, 10L, "FINISHED")));

        var response = service.listMembers("distribution", null, null, null, 1, 30, adminContext());

        assertThat(response.getTotal()).isEqualTo(2L);
        assertThat(response.getRecords()).extracting("customerId").containsExactly(1L, 2L);
        assertThat(response.getRecords().get(0).getPrivateDomainOwner()).isEqualTo("private_domain");
        assertThat(response.getRecords().get(0).getLatestPlanOrderId()).isEqualTo(100L);
        assertThat(response.getRecords().get(0).getLatestPlanOrderStatus()).isEqualTo("FINISHED");
        assertThat(response.getRecords().get(0).getLatestOrderAmount()).isEqualByComparingTo("199");
        assertThat(response.getRecords().get(0).getTotalOrderAmount()).isEqualByComparingTo("199");
    }

    private Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("会员" + id);
        customer.setPhone("1380000000" + id);
        customer.setSource("distribution");
        customer.setSourceChannel("distribution");
        customer.setExternalPartnerCode("DISTRIBUTION");
        customer.setExternalMemberId("m_" + id);
        customer.setExternalMemberRole("member");
        customer.setUpdateTime(LocalDateTime.now());
        customer.setCreateTime(LocalDateTime.now());
        return customer;
    }

    private Order order(Long id, Long customerId) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setOrderNo("ORD-" + id);
        order.setStatus("PAID");
        order.setAmount(BigDecimal.valueOf(199));
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    private CustomerWecomRelation relation(Long customerId, String wecomUserId) {
        CustomerWecomRelation relation = new CustomerWecomRelation();
        relation.setCustomerId(customerId);
        relation.setExternalUserid("wm_" + customerId);
        relation.setWecomUserId(wecomUserId);
        return relation;
    }

    private PlanOrder planOrder(Long id, Long orderId, String status) {
        PlanOrder planOrder = new PlanOrder();
        planOrder.setId(id);
        planOrder.setOrderId(orderId);
        planOrder.setStatus(status);
        planOrder.setCreateTime(LocalDateTime.now());
        return planOrder;
    }

    private PermissionRequestContext privateDomainContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setCurrentUserId(1101L);
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");
        context.setDataScope("SELF");
        return context;
    }

    private PermissionRequestContext adminContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setCurrentUserId(1L);
        context.setRoleCode("ADMIN");
        context.setDataScope("ALL");
        return context;
    }
}
