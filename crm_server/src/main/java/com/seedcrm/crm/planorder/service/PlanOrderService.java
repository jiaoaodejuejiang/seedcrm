package com.seedcrm.crm.planorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.planorder.dto.PlanOrderActionDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderAssignRoleDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderCreateDTO;
import com.seedcrm.crm.planorder.dto.PlanOrderDetailResponse;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.wecom.entity.WecomTouchLog;

public interface PlanOrderService extends IService<PlanOrder> {

    PlanOrder createPlanOrder(PlanOrderCreateDTO planOrderCreateDTO);

    default PlanOrder createPlanOrder(PlanOrderCreateDTO planOrderCreateDTO, Long operatorUserId, String operatorRoleCode) {
        return createPlanOrder(planOrderCreateDTO);
    }

    PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder arrive(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode);

    PlanOrder start(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder start(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode);

    PlanOrder printServiceForm(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder printServiceForm(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode);

    PlanOrder confirmServiceForm(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder confirmServiceForm(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode);

    PlanOrder finish(PlanOrderActionDTO planOrderActionDTO);

    PlanOrder finish(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId);

    PlanOrder finish(PlanOrderActionDTO planOrderActionDTO, Long operatorUserId, String operatorRoleCode);

    OrderRoleRecord assignRole(PlanOrderAssignRoleDTO planOrderAssignRoleDTO);

    PlanOrderDetailResponse getDetail(Long planOrderId);

    WecomTouchLog sendServiceForm(Long planOrderId, String message);
}
