package com.seedcrm.crm.customer.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.dto.CustomerExportDTO;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.service.CustomerService;
import com.seedcrm.crm.permission.support.CustomerPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final CustomerPermissionGuard customerPermissionGuard;

    public CustomerController(CustomerService customerService,
                              PermissionRequestContextResolver permissionRequestContextResolver,
                              CustomerPermissionGuard customerPermissionGuard) {
        this.customerService = customerService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.customerPermissionGuard = customerPermissionGuard;
    }

    @GetMapping("/{id}")
    public ApiResponse<Customer> getById(@PathVariable Long id, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        customerPermissionGuard.checkView(context, id);
        return ApiResponse.success(customerService.getByIdOrThrow(id));
    }

    @GetMapping("/by-phone")
    public ApiResponse<Customer> getByPhone(@RequestParam String phone, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        Customer customer = customerService.getByPhone(phone);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }
        customerPermissionGuard.checkView(context, customer.getId());
        return ApiResponse.success(customer);
    }

    @GetMapping("/export")
    public ApiResponse<List<CustomerExportDTO>> export(HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        List<CustomerExportDTO> rows = customerService.exportCustomers().stream()
                .filter(customer -> customerPermissionGuard.canView(context, customer.getId()))
                .collect(Collectors.toList());
        return ApiResponse.success(rows);
    }
}
