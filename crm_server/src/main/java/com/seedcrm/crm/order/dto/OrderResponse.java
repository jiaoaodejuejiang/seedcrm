package com.seedcrm.crm.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OrderResponse {

    private Long id;
    private String orderNo;
    private Long clueId;
    private Long customerId;
    private String sourceChannel;
    private Long sourceId;
    private String type;
    private BigDecimal amount;
    private BigDecimal deposit;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime arriveTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completeTime;
    private String remark;
    private String serviceDetailJson;
    private String verificationStatus;
    private String verificationMethod;
    private String verificationCode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime verificationTime;
    private Long verificationOperatorId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    public static OrderResponse from(Order order) {
        if (order == null) {
            return null;
        }
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setClueId(order.getClueId());
        response.setCustomerId(order.getCustomerId());
        response.setSourceChannel(order.getSourceChannel());
        response.setSourceId(order.getSourceId());
        response.setType(OrderType.toApiValue(order.getType()));
        response.setAmount(order.getAmount());
        response.setDeposit(order.getDeposit());
        response.setStatus(OrderStatus.toApiValue(order.getStatus()));
        response.setAppointmentTime(order.getAppointmentTime());
        response.setArriveTime(order.getArriveTime());
        response.setCompleteTime(order.getCompleteTime());
        response.setRemark(order.getRemark());
        response.setServiceDetailJson(order.getServiceDetailJson());
        response.setVerificationStatus(order.getVerificationStatus());
        response.setVerificationMethod(order.getVerificationMethod());
        response.setVerificationCode(order.getVerificationCode());
        response.setVerificationTime(order.getVerificationTime());
        response.setVerificationOperatorId(order.getVerificationOperatorId());
        response.setCreateTime(order.getCreateTime());
        response.setUpdateTime(order.getUpdateTime());
        return response;
    }

    public OrderResponse maskAmounts() {
        this.amount = null;
        this.deposit = null;
        return this;
    }
}
