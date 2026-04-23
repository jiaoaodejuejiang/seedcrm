package com.seedcrm.crm.planorder.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.enums.PlanOrderStatus;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PlanOrderResponse {

    private Long id;
    private Long orderId;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime arriveTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    public static PlanOrderResponse from(PlanOrder planOrder) {
        if (planOrder == null) {
            return null;
        }
        PlanOrderResponse response = new PlanOrderResponse();
        response.setId(planOrder.getId());
        response.setOrderId(planOrder.getOrderId());
        response.setStatus(PlanOrderStatus.toApiValue(planOrder.getStatus()));
        response.setArriveTime(planOrder.getArriveTime());
        response.setStartTime(planOrder.getStartTime());
        response.setFinishTime(planOrder.getFinishTime());
        response.setCreateTime(planOrder.getCreateTime());
        return response;
    }
}
