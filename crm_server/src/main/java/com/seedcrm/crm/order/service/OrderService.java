package com.seedcrm.crm.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.dto.OrderServiceDetailDTO;
import com.seedcrm.crm.order.dto.OrderVoucherVerifyDTO;
import com.seedcrm.crm.order.entity.Order;

public interface OrderService extends IService<Order> {

    Order createOrder(OrderCreateDTO orderCreateDTO);

    Order payDeposit(OrderPayDTO orderPayDTO);

    Order appointment(OrderAppointmentDTO orderAppointmentDTO);

    Order appointment(OrderAppointmentDTO orderAppointmentDTO, Long operatorUserId, String operatorRoleCode);

    Order cancelAppointment(OrderActionDTO orderActionDTO);

    Order cancelAppointment(OrderActionDTO orderActionDTO, Long operatorUserId, String operatorRoleCode);

    Order arrive(OrderActionDTO orderActionDTO);

    Order serving(OrderActionDTO orderActionDTO);

    Order complete(OrderActionDTO orderActionDTO);

    Order complete(OrderActionDTO orderActionDTO, Long operatorUserId);

    Order cancel(OrderActionDTO orderActionDTO);

    Order refund(OrderActionDTO orderActionDTO);

    Order refund(OrderActionDTO orderActionDTO, Long operatorUserId);

    Order verifyVoucher(OrderVoucherVerifyDTO orderVoucherVerifyDTO, Long operatorUserId);

    Order verifyVoucher(OrderVoucherVerifyDTO orderVoucherVerifyDTO, Long operatorUserId, String operatorRoleCode);

    Order updateServiceDetail(OrderServiceDetailDTO orderServiceDetailDTO);

    Order updateServiceDetail(OrderServiceDetailDTO orderServiceDetailDTO, String operatorRoleCode);
}
