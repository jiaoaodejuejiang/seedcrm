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
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    private static final String CONFIG_STORE_AMOUNT_HIDDEN = "amount.visibility.store_staff_hidden";
    private static final String CONFIG_STORE_AMOUNT_HIDDEN_ROLES = "amount.visibility.store_staff_hidden_roles";
    private static final String CONFIG_SERVICE_AMOUNT_HIDDEN_ROLES = "amount.visibility.service_confirm_hidden_roles";
    private static final String CONFIG_SERVICE_AMOUNT_EDIT_ROLES = "amount.visibility.service_confirm_edit_roles";
    private static final String DEFAULT_STORE_AMOUNT_HIDDEN_ROLES =
            "STORE_SERVICE,STORE_MANAGER,PHOTOGRAPHER,MAKEUP_ARTIST,PHOTO_SELECTOR";
    private static final String DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES =
            "STORE_SERVICE,PHOTOGRAPHER,MAKEUP_ARTIST";
    private static final String DEFAULT_SERVICE_AMOUNT_EDIT_ROLES =
            "ADMIN,FINANCE,PHOTO_SELECTOR";
    private static final Set<String> STORE_AMOUNT_RESTRICTED_ROLES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR");

    private final OrderService orderService;
    private final PermissionRequestContextResolver permissionRequestContextResolver;
    private final OrderPermissionGuard orderPermissionGuard;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    public OrderController(OrderService orderService,
                           PermissionRequestContextResolver permissionRequestContextResolver,
                           OrderPermissionGuard orderPermissionGuard,
                           ObjectMapper objectMapper,
                           SystemConfigService systemConfigService) {
        this.orderService = orderService;
        this.permissionRequestContextResolver = permissionRequestContextResolver;
        this.orderPermissionGuard = orderPermissionGuard;
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
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
        if (response == null || context == null || context.getRoleCode() == null) {
            return response;
        }
        if (!systemConfigService.getBoolean(CONFIG_STORE_AMOUNT_HIDDEN, true)) {
            return response;
        }
        String roleCode = context.getRoleCode().trim().toUpperCase(Locale.ROOT);
        if (configuredRoles(CONFIG_STORE_AMOUNT_HIDDEN_ROLES, DEFAULT_STORE_AMOUNT_HIDDEN_ROLES, STORE_AMOUNT_RESTRICTED_ROLES)
                .contains(roleCode)) {
            response.maskBusinessAmounts();
        }
        if (configuredRoles(CONFIG_SERVICE_AMOUNT_HIDDEN_ROLES, DEFAULT_SERVICE_AMOUNT_HIDDEN_ROLES, Set.of("STORE_SERVICE", "PHOTOGRAPHER", "MAKEUP_ARTIST"))
                .contains(roleCode)) {
            response.maskServiceAmounts(objectMapper);
        }
        return response;
    }

    private void preserveServiceAmountsIfNotEditable(OrderServiceDetailDTO dto, PermissionRequestContext context) {
        if (dto == null || !StringUtils.hasText(dto.getServiceDetailJson()) || canEditServiceAmounts(context)) {
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

    private boolean canEditServiceAmounts(PermissionRequestContext context) {
        if (context == null || context.getRoleCode() == null) {
            return false;
        }
        String roleCode = context.getRoleCode().trim().toUpperCase(Locale.ROOT);
        return configuredRoles(CONFIG_SERVICE_AMOUNT_EDIT_ROLES, DEFAULT_SERVICE_AMOUNT_EDIT_ROLES,
                Set.of("ADMIN", "FINANCE", "PHOTO_SELECTOR")).contains(roleCode);
    }

    private Set<String> configuredRoles(String key, String defaultRoles, Set<String> fallbackRoles) {
        String configuredRoles = systemConfigService.getString(key, defaultRoles);
        Set<String> roles = Arrays.stream(configuredRoles.split("[,，\\s]+"))
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
        return roles.isEmpty() ? fallbackRoles : roles;
    }
}
