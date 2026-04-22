package com.seedcrm.crm.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.customer.dto.CustomerExportDTO;
import com.seedcrm.crm.customer.entity.Customer;
import java.util.List;

public interface CustomerService extends IService<Customer> {

    Customer getByIdOrThrow(Long customerId);

    Customer getByPhone(String phone);

    Customer getOrCreateByClue(Clue clue);

    Customer refreshCustomerLifecycle(Long customerId);

    List<CustomerExportDTO> exportCustomers();
}
