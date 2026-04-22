package com.seedcrm.crm.customer.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.dto.CustomerExportDTO;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.service.CustomerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{id}")
    public ApiResponse<Customer> getById(@PathVariable Long id) {
        return ApiResponse.success(customerService.getByIdOrThrow(id));
    }

    @GetMapping("/by-phone")
    public ApiResponse<Customer> getByPhone(@RequestParam String phone) {
        Customer customer = customerService.getByPhone(phone);
        if (customer == null) {
            throw new BusinessException("customer not found");
        }
        return ApiResponse.success(customer);
    }

    @GetMapping("/export")
    public ApiResponse<List<CustomerExportDTO>> export() {
        return ApiResponse.success(customerService.exportCustomers());
    }
}
