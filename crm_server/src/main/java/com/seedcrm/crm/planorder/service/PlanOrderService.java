package com.seedcrm.crm.planorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderDetailResponse;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;

public interface PlanOrderService extends IService<PlanOrder> {

    PlanOrder createPlanOrder(PlanOrderCreateDTO planOrderCreateDTO);

    PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder start(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder finish(PlanOrderActionDTO planOrderActionDTO);

    OrderRoleRecord assignRole(PlanOrderAssignRoleDTO planOrderAssignRoleDTO);

    PlanOrderDetailResponse getDetail(Long planOrderId);
}
