package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.permission.dto.PermissionCheckRequest;
import com.seedcrm.crm.permission.dto.PermissionCheckResponse;
import com.seedcrm.crm.permission.service.PermissionService;
import com.seedcrm.crm.wecom.entity.CustomerWecomRelation;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerPermissionGuardTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthService authService;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private CustomerWecomRelationMapper customerWecomRelationMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderPermissionResourceResolver resourceResolver;

    @Mock
    private StaffDirectoryService staffDirectoryService;

    private CustomerPermissionGuard guard;

    @BeforeEach
    void setUp() {
        OrderPermissionGuard orderPermissionGuard = new OrderPermissionGuard(permissionService, authService, resourceResolver);
        guard = new CustomerPermissionGuard(permissionService, authService, customerMapper, customerWecomRelationMapper,
                orderMapper, orderPermissionGuard, resourceResolver, staffDirectoryService);
    }

    @Test
    void shouldAllowPrivateDomainToViewBoundCustomer() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L));
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(relation(1L, "private_domain"));
        when(staffDirectoryService.getWecomAccount(1101L)).thenReturn("private_domain");
        when(permissionService.check(any())).thenAnswer(invocation -> {
            PermissionCheckRequest request = invocation.getArgument(0);
            return new PermissionCheckResponse(request.getResourceOwnerId() != null,
                    "CUSTOMER:VIEW:PRIVATE_DOMAIN_SERVICE:SELF",
                    "SELF",
                    request.getResourceOwnerId() == null ? "ABAC scope rejected" : "allowed");
        });

        assertThat(guard.canView(privateDomainContext(), 1L)).isTrue();
    }

    @Test
    void shouldRejectPrivateDomainWhenCustomerIsNotBound() {
        when(customerMapper.selectById(2L)).thenReturn(customer(2L));
        when(customerWecomRelationMapper.selectOne(any())).thenReturn(relation(2L, "other_wecom"));
        when(staffDirectoryService.getWecomAccount(1101L)).thenReturn("private_domain");
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(false, null, "SELF", "ABAC scope rejected"));

        assertThatThrownBy(() -> guard.checkView(privateDomainContext(), 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("customer view denied");
    }

    @Test
    void shouldAllowStoreRoleThroughRelatedOrderScope() {
        when(customerMapper.selectById(3L)).thenReturn(customer(3L));
        when(orderMapper.selectList(any())).thenReturn(List.of(order(31L, 3L)));
        when(resourceResolver.resolveOrderStoreScopeOwnerId(31L)).thenReturn(5101L);
        when(authService.resolveStoreId(5101L)).thenReturn(10L);
        when(permissionService.check(any())).thenReturn(new PermissionCheckResponse(true,
                "CUSTOMER:VIEW:STORE_SERVICE:STORE",
                "STORE",
                "allowed"));

        guard.checkView(storeContext(), 3L);
    }

    @Test
    void shouldRejectCustomerWithoutAccessibleRelatedOrder() {
        when(customerMapper.selectById(4L)).thenReturn(customer(4L));
        when(orderMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> guard.checkView(storeContext(), 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no accessible customer order");
    }

    private Customer customer(Long id) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("member-" + id);
        return customer;
    }

    private Order order(Long id, Long customerId) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customerId);
        return order;
    }

    private CustomerWecomRelation relation(Long customerId, String wecomUserId) {
        CustomerWecomRelation relation = new CustomerWecomRelation();
        relation.setCustomerId(customerId);
        relation.setExternalUserid("wm_" + customerId);
        relation.setWecomUserId(wecomUserId);
        return relation;
    }

    private PermissionRequestContext privateDomainContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("PRIVATE_DOMAIN_SERVICE");
        context.setDataScope("SELF");
        context.setCurrentUserId(1101L);
        return context;
    }

    private PermissionRequestContext storeContext() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        context.setDataScope("STORE");
        context.setCurrentUserId(5101L);
        context.setCurrentStoreId(10L);
        return context;
    }
}
