package com.seedcrm.crm.order.controller;

import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.dto.OrderResponse;
import com.seedcrm.crm.order.dto.OrderServiceDetailDTO;
import com.seedcrm.crm.order.service.OrderService;
import com.seedcrm.crm.permission.support.OrderPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final OrderPermissionGuard orderPermissionGuard;

    public OrderController(OrderService orderService,
                           PermissionRequestContextResolver permissionRequestContextResolver,
                           OrderPermissionGuard orderPermissionGuard) {
        this.orderService = orderService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.orderPermissionGuard = orderPermissionGuard;
    }

    @PostMapping("/create")
    public ApiResponse<OrderResponse> create(@RequestBody OrderCreateDTO orderCreateDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkCreate(context, orderCreateDTO);
        return ApiResponse.success(OrderResponse.from(orderService.createOrder(orderCreateDTO)));
    }

    @PostMapping("/pay")
    public ApiResponse<OrderResponse> pay(@RequestBody OrderPayDTO orderPayDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderPayDTO == null ? null : orderPayDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.payDeposit(orderPayDTO)));
    }

    @PostMapping("/appointment")
    public ApiResponse<OrderResponse> appointment(@RequestBody OrderAppointmentDTO orderAppointmentDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderAppointmentDTO == null ? null : orderAppointmentDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.appointment(orderAppointmentDTO)));
    }

    @PostMapping("/arrive")
    public ApiResponse<OrderResponse> arrive(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.arrive(orderActionDTO)));
    }

    @PostMapping("/serving")
    public ApiResponse<OrderResponse> serving(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.serving(orderActionDTO)));
    }

    @PostMapping("/complete")
    public ApiResponse<OrderResponse> complete(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkFinish(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.complete(orderActionDTO)));
    }

    @PostMapping("/cancel")
    public ApiResponse<OrderResponse> cancel(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.cancel(orderActionDTO)));
    }

    @PostMapping("/refund")
    public ApiResponse<OrderResponse> refund(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.refund(orderActionDTO)));
    }

    @PostMapping("/service-detail")
    public ApiResponse<OrderResponse> updateServiceDetail(@RequestBody OrderServiceDetailDTO orderServiceDetailDTO,
                                                          HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderServiceDetailDTO == null ? null : orderServiceDetailDTO.getOrderId());
        return ApiResponse.success(OrderResponse.from(orderService.updateServiceDetail(orderServiceDetailDTO)));
    }
}
