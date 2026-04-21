package com.seedcrm.crm.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.entity.Order;

public interface OrderService extends IService<Order> {

    Order createOrder(OrderCreateDTO orderCreateDTO);

    Order payDeposit(OrderPayDTO orderPayDTO);

    Order appointment(OrderAppointmentDTO orderAppointmentDTO);

    Order arrive(OrderActionDTO orderActionDTO);

    Order serving(OrderActionDTO orderActionDTO);

    Order complete(OrderActionDTO orderActionDTO);

    Order cancel(OrderActionDTO orderActionDTO);

    Order refund(OrderActionDTO orderActionDTO);
}
