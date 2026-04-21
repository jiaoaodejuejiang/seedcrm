package com.seedcrm.crm.order.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public ApiResponse<Order> create(@RequestBody OrderCreateDTO orderCreateDTO) {
        return ApiResponse.success(orderService.createOrder(orderCreateDTO));
    }

    @PostMapping("/pay")
    public ApiResponse<Order> pay(@RequestBody OrderPayDTO orderPayDTO) {
        return ApiResponse.success(orderService.payDeposit(orderPayDTO));
    }

    @PostMapping("/appointment")
    public ApiResponse<Order> appointment(@RequestBody OrderAppointmentDTO orderAppointmentDTO) {
        return ApiResponse.success(orderService.appointment(orderAppointmentDTO));
    }

    @PostMapping("/arrive")
    public ApiResponse<Order> arrive(@RequestBody OrderActionDTO orderActionDTO) {
        return ApiResponse.success(orderService.arrive(orderActionDTO));
    }

    @PostMapping("/serving")
    public ApiResponse<Order> serving(@RequestBody OrderActionDTO orderActionDTO) {
        return ApiResponse.success(orderService.serving(orderActionDTO));
    }

    @PostMapping("/complete")
    public ApiResponse<Order> complete(@RequestBody OrderActionDTO orderActionDTO) {
        return ApiResponse.success(orderService.complete(orderActionDTO));
    }

    @PostMapping("/cancel")
    public ApiResponse<Order> cancel(@RequestBody OrderActionDTO orderActionDTO) {
        return ApiResponse.success(orderService.cancel(orderActionDTO));
    }

    @PostMapping("/refund")
    public ApiResponse<Order> refund(@RequestBody OrderActionDTO orderActionDTO) {
        return ApiResponse.success(orderService.refund(orderActionDTO));
    }
}
