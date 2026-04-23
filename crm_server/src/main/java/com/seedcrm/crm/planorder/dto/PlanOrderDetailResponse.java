package com.seedcrm.crm.planorder.dto;

import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlanOrderDetailResponse {

    private PlanOrderResponse planOrder;
    private List<OrderRoleRecord> roleRecords;
}
