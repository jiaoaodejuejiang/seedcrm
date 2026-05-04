package com.seedcrm.crm.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.order.dto.OrderActionDTO;
import com.seedcrm.crm.order.dto.OrderAppointmentDTO;
import com.seedcrm.crm.order.dto.OrderCreateDTO;
import com.seedcrm.crm.order.dto.OrderPayDTO;
import com.seedcrm.crm.order.dto.OrderResponse;
import com.seedcrm.crm.order.dto.OrderServiceDetailDTO;
import com.seedcrm.crm.order.dto.OrderVoucherVerifyDTO;
import com.seedcrm.crm.order.service.OrderService;
import com.seedcrm.crm.order.support.OrderAmountMaskingSupport;
import com.seedcrm.crm.permission.support.OrderPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SensitiveDataProjectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
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
    private final ObjectMapper objectMapper;
    private final SensitiveDataProjectionService sensitiveDataProjectionService;

    public OrderController(OrderService orderService,
                           PermissionRequestContextResolver permissionRequestContextResolver,
                           OrderPermissionGuard orderPermissionGuard,
                           ObjectMapper objectMapper,
                           SensitiveDataProjectionService sensitiveDataProjectionService) {
        this.orderService = orderService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.orderPermissionGuard = orderPermissionGuard;
        this.objectMapper = objectMapper;
        this.sensitiveDataProjectionService = sensitiveDataProjectionService;
    }

    @PostMapping("/create")
    public ApiResponse<OrderResponse> create(@RequestBody OrderCreateDTO orderCreateDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkCreate(context, orderCreateDTO);
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.createOrder(orderCreateDTO)), context));
    }

    @PostMapping("/pay")
    public ApiResponse<OrderResponse> pay(@RequestBody OrderPayDTO orderPayDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderPayDTO == null ? null : orderPayDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.payDeposit(orderPayDTO)), context));
    }

    @PostMapping("/appointment")
    public ApiResponse<OrderResponse> appointment(@RequestBody OrderAppointmentDTO orderAppointmentDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderAppointmentDTO == null ? null : orderAppointmentDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.appointment(
                orderAppointmentDTO,
                context.getCurrentUserId(),
                context.getRoleCode())), context));
    }

    @PostMapping("/appointment/cancel")
    public ApiResponse<OrderResponse> cancelAppointment(@RequestBody OrderActionDTO orderActionDTO,
                                                        HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.cancelAppointment(
                orderActionDTO,
                context.getCurrentUserId(),
                context.getRoleCode())), context));
    }

    @PostMapping("/arrive")
    public ApiResponse<OrderResponse> arrive(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.arrive(orderActionDTO)), context));
    }

    @PostMapping("/serving")
    public ApiResponse<OrderResponse> serving(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.serving(orderActionDTO)), context));
    }

    @PostMapping("/complete")
    public ApiResponse<OrderResponse> complete(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkFinish(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.complete(orderActionDTO, context.getCurrentUserId())), context));
    }

    @PostMapping("/cancel")
    public ApiResponse<OrderResponse> cancel(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderActionDTO == null ? null : orderActionDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.cancel(orderActionDTO)), context));
    }

    @PostMapping("/refund")
    public ApiResponse<OrderResponse> refund(@RequestBody OrderActionDTO orderActionDTO, HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkRefund(context,
                orderActionDTO == null ? null : orderActionDTO.getOrderId(),
                orderActionDTO == null ? null : orderActionDTO.getRefundScene());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.refund(orderActionDTO, context.getCurrentUserId())), context));
    }

    @PostMapping("/verify")
    public ApiResponse<OrderResponse> verify(@RequestBody OrderVoucherVerifyDTO orderVoucherVerifyDTO,
                                             HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderVoucherVerifyDTO == null ? null : orderVoucherVerifyDTO.getOrderId());
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.verifyVoucher(
                orderVoucherVerifyDTO,
                context.getCurrentUserId(),
                context.getRoleCode())), context));
    }

    @PostMapping("/service-detail")
    public ApiResponse<OrderResponse> updateServiceDetail(@RequestBody OrderServiceDetailDTO orderServiceDetailDTO,
                                                          HttpServletRequest request) {
        PermissionRequestContext context = permissionRequestContextResolver.resolve(request);
        orderPermissionGuard.checkUpdate(context, orderServiceDetailDTO == null ? null : orderServiceDetailDTO.getOrderId());
        preserveServiceAmountsIfNotEditable(orderServiceDetailDTO, context);
        return ApiResponse.success(maskAmountsIfNeeded(OrderResponse.from(orderService.updateServiceDetail(
                orderServiceDetailDTO,
                context == null ? null : context.getRoleCode())), context));
    }

    private OrderResponse maskAmountsIfNeeded(OrderResponse response, PermissionRequestContext context) {
        return sensitiveDataProjectionService.projectOrderResponse(response, context);
    }

    private void preserveServiceAmountsIfNotEditable(OrderServiceDetailDTO dto, PermissionRequestContext context) {
        if (dto == null || !StringUtils.hasText(dto.getServiceDetailJson())
                || sensitiveDataProjectionService.canEditServiceAmounts(context)) {
            return;
        }
        try {
            var parsed = objectMapper.readTree(dto.getServiceDetailJson());
            if (!parsed.isObject()) {
                return;
            }
            ObjectNode root = (ObjectNode) parsed;
            root.put(OrderAmountMaskingSupport.MASK_MARKER, true);
            dto.setServiceDetailJson(objectMapper.writeValueAsString(root));
        } catch (Exception ignored) {
            // The service layer will return the existing validation error for invalid JSON.
        }
    }
}
