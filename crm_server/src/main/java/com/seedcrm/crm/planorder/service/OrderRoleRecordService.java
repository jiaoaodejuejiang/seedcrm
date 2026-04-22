package com.seedcrm.crm.planorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import java.util.List;

public interface OrderRoleRecordService extends IService<OrderRoleRecord> {

    OrderRoleRecord assignRole(Long planOrderId, String roleCode, Long userId);

    List<OrderRoleRecord> listByPlanOrderId(Long planOrderId);
}
