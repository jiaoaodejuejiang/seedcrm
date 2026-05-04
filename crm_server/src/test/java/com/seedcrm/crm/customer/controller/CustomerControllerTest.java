package com.seedcrm.crm.customer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.customer.dto.CustomerExportDTO;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.service.CustomerService;
import com.seedcrm.crm.permission.support.CustomerPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private CustomerPermissionGuard customerPermissionGuard;

    @Mock
    private HttpServletRequest request;

    private CustomerController controller;
    private PermissionRequestContext context;

    @BeforeEach
    void setUp() {
        controller = new CustomerController(customerService, permissionRequestContextResolver, customerPermissionGuard);
        context = new PermissionRequestContext();
        context.setRoleCode("STORE_SERVICE");
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context);
    }

    @Test
    void getByIdShouldCheckCustomerPermissionBeforeReturningCustomer() {
        Customer customer = new Customer();
        customer.setId(11L);
        when(customerService.getByIdOrThrow(11L)).thenReturn(customer);

        var response = controller.getById(11L, request);

        verify(customerPermissionGuard).checkView(context, 11L);
        assertThat(response.getData().getId()).isEqualTo(11L);
    }

    @Test
    void getByPhoneShouldCheckResolvedCustomerPermission() {
        Customer customer = new Customer();
        customer.setId(22L);
        customer.setPhone("13800000000");
        when(customerService.getByPhone("13800000000")).thenReturn(customer);

        var response = controller.getByPhone("13800000000", request);

        verify(customerPermissionGuard).checkView(context, 22L);
        assertThat(response.getData().getPhone()).isEqualTo("13800000000");
    }

    @Test
    void exportShouldFilterRowsByCustomerPermission() {
        CustomerExportDTO visible = exportRow(1L);
        CustomerExportDTO denied = exportRow(2L);
        when(customerService.exportCustomers()).thenReturn(List.of(visible, denied));
        when(customerPermissionGuard.canView(context, 1L)).thenReturn(true);
        when(customerPermissionGuard.canView(context, 2L)).thenReturn(false);

        var response = controller.export(request);

        assertThat(response.getData()).extracting(CustomerExportDTO::getId).containsExactly(1L);
    }

    private CustomerExportDTO exportRow(Long id) {
        CustomerExportDTO dto = new CustomerExportDTO();
        dto.setId(id);
        dto.setName("customer-" + id);
        return dto;
    }
}
