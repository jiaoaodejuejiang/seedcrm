package com.seedcrm.crm.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.api.ApiResponse;
import com.seedcrm.crm.order.dto.OrderResponse;
import com.seedcrm.crm.order.dto.OrderServiceDetailDTO;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.order.service.OrderService;
import com.seedcrm.crm.permission.support.OrderPermissionGuard;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.permission.support.PermissionRequestContextResolver;
import com.seedcrm.crm.permission.support.SensitiveDataProjectionService;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderControllerAmountMaskingTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PermissionRequestContextResolver permissionRequestContextResolver;

    @Mock
    private OrderPermissionGuard orderPermissionGuard;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(
                orderService,
                permissionRequestContextResolver,
                orderPermissionGuard,
                objectMapper,
                new SensitiveDataProjectionService(objectMapper, systemConfigService));
        when(systemConfigService.getBoolean("amount.visibility.store_staff_hidden", true)).thenReturn(true);
        when(systemConfigService.getString(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(orderService.updateServiceDetail(nullable(OrderServiceDetailDTO.class), nullable(String.class)))
                .thenAnswer(invocation -> sampleOrder());
    }

    @Test
    void storeManagerShouldHideBusinessAmountsButKeepServiceConfirmAmount() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("STORE_MANAGER"));

        ApiResponse<OrderResponse> response = controller.updateServiceDetail(null, request);

        OrderResponse data = response.getData();
        assertThat(data.getAmount()).isNull();
        assertThat(data.getDeposit()).isNull();
        assertThat(data.getVerificationCode()).isNull();
        JsonNode serviceDetail = objectMapper.readTree(data.getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
        assertThat(serviceDetail.path("serviceTemplate").path("config").path("price").asInt()).isEqualTo(99);
    }

    @Test
    void storeServiceShouldHideBothBusinessAndServiceConfirmAmounts() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("STORE_SERVICE"));

        ApiResponse<OrderResponse> response = controller.updateServiceDetail(null, request);

        OrderResponse data = response.getData();
        assertThat(data.getAmount()).isNull();
        assertThat(data.getDeposit()).isNull();
        assertThat(data.getVerificationCode()).isNull();
        JsonNode serviceDetail = objectMapper.readTree(data.getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").isNull()).isTrue();
        assertThat(serviceDetail.path("serviceTemplate").path("config").path("price").isNull()).isTrue();
    }

    @Test
    void adminShouldKeepAllAmounts() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("ADMIN"));

        ApiResponse<OrderResponse> response = controller.updateServiceDetail(null, request);

        OrderResponse data = response.getData();
        assertThat(data.getAmount()).isEqualByComparingTo("1999.00");
        assertThat(data.getDeposit()).isEqualByComparingTo("299.00");
        assertThat(data.getVerificationCode()).isEqualTo("VC-8888");
        JsonNode serviceDetail = objectMapper.readTree(data.getServiceDetailJson());
        assertThat(serviceDetail.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
        assertThat(serviceDetail.path("serviceTemplate").path("config").path("price").asInt()).isEqualTo(99);
    }

    @Test
    void storeManagerSaveShouldMarkServiceAmountsAsMaskedToPreserveOriginalValues() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("STORE_MANAGER"));
        OrderServiceDetailDTO dto = serviceDetailRequest("2888.00");

        controller.updateServiceDetail(dto, request);

        ArgumentCaptor<OrderServiceDetailDTO> dtoCaptor = ArgumentCaptor.forClass(OrderServiceDetailDTO.class);
        verify(orderService).updateServiceDetail(dtoCaptor.capture(), any());
        JsonNode serviceDetail = objectMapper.readTree(dtoCaptor.getValue().getServiceDetailJson());
        assertThat(serviceDetail.path("_amountsMasked").asBoolean()).isTrue();
        assertThat(serviceDetail.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("2888.00");
    }

    @Test
    void photoSelectorSaveShouldKeepServiceAmountEditable() throws Exception {
        when(permissionRequestContextResolver.resolve(request)).thenReturn(context("PHOTO_SELECTOR"));
        OrderServiceDetailDTO dto = serviceDetailRequest("2888.00");

        ApiResponse<OrderResponse> response = controller.updateServiceDetail(dto, request);

        ArgumentCaptor<OrderServiceDetailDTO> dtoCaptor = ArgumentCaptor.forClass(OrderServiceDetailDTO.class);
        verify(orderService).updateServiceDetail(dtoCaptor.capture(), any());
        JsonNode submitted = objectMapper.readTree(dtoCaptor.getValue().getServiceDetailJson());
        assertThat(submitted.has("_amountsMasked")).isFalse();
        assertThat(response.getData().getAmount()).isNull();
        assertThat(response.getData().getDeposit()).isNull();
        assertThat(response.getData().getVerificationCode()).isNull();
        JsonNode returned = objectMapper.readTree(response.getData().getServiceDetailJson());
        assertThat(returned.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
    }

    private PermissionRequestContext context(String roleCode) {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode(roleCode);
        context.setCurrentUserId(7L);
        return context;
    }

    private OrderServiceDetailDTO serviceDetailRequest(String amount) {
        OrderServiceDetailDTO dto = new OrderServiceDetailDTO();
        dto.setOrderId(11L);
        dto.setServiceRequirement("updated");
        dto.setServiceDetailJson("""
                {
                  "serviceRequirement": "updated",
                  "serviceConfirmAmount": %s,
                  "serviceTemplate": {
                    "config": {
                      "price": 199
                    }
                  }
                }
                """.formatted(amount));
        return dto;
    }

    private Order sampleOrder() {
        Order order = new Order();
        order.setId(11L);
        order.setOrderNo("ORD-11");
        order.setType(OrderType.COUPON.getCode());
        order.setStatus(OrderStatus.ARRIVED.name());
        order.setAmount(new BigDecimal("1999.00"));
        order.setDeposit(new BigDecimal("299.00"));
        order.setVerificationCode("VC-8888");
        order.setServiceDetailJson("""
                {
                  "serviceRequirement": "portrait",
                  "serviceConfirmAmount": 1288.00,
                  "serviceTemplate": {
                    "config": {
                      "price": 99,
                      "title": "A"
                    }
                  }
                }
                """);
        return order;
    }
}
