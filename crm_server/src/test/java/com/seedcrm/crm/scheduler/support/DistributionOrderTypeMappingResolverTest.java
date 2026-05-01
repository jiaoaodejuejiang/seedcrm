package com.seedcrm.crm.scheduler.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionOrderPayload;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistributionOrderTypeMappingResolverTest {

    @Test
    void shouldResolveOrderTypeByConfiguredSkuRule() {
        DistributionOrderTypeMappingResolver resolver = resolver("""
                {
                  "default": "coupon",
                  "aliases": {
                    "coupon": "coupon",
                    "deposit": "deposit"
                  },
                  "rules": [
                    {
                      "ruleId": "sku-deposit",
                      "providerCode": "DISTRIBUTION",
                      "externalSkuId": "sku_001",
                      "internalOrderType": "deposit",
                      "verificationPolicy": "DIRECT",
                      "priority": 10
                    }
                  ]
                }
                """);
        DistributionOrderPayload payload = new DistributionOrderPayload();
        payload.setType("coupon");
        payload.setExternalSkuId("sku_001");

        DistributionOrderTypeMappingResolver.ResolvedOrderType result =
                resolver.resolve("DISTRIBUTION", "DISTRIBUTOR", payload);

        assertThat(result.code()).isEqualTo(OrderType.DEPOSIT.getCode());
        assertThat(result.apiValue()).isEqualTo("deposit");
        assertThat(result.resolution()).isEqualTo("RULE");
        assertThat(result.ruleId()).isEqualTo("sku-deposit");
        assertThat(result.verificationPolicy()).isEqualTo("DIRECT");
    }

    @Test
    void shouldResolveOrderTypeByAliasWhenNoRuleMatches() {
        DistributionOrderTypeMappingResolver resolver = resolver(DistributionOrderTypeMappingResolver.DEFAULT_MAPPING_JSON);
        DistributionOrderPayload payload = new DistributionOrderPayload();
        payload.setType("prepay");

        DistributionOrderTypeMappingResolver.ResolvedOrderType result =
                resolver.resolve("DISTRIBUTION", "DISTRIBUTOR", payload);

        assertThat(result.code()).isEqualTo(OrderType.DEPOSIT.getCode());
        assertThat(result.apiValue()).isEqualTo("deposit");
        assertThat(result.resolution()).isEqualTo("ALIAS");
    }

    @Test
    void shouldResolveDistributionProductAsFirstClassOrderType() {
        DistributionOrderTypeMappingResolver resolver = resolver(DistributionOrderTypeMappingResolver.DEFAULT_MAPPING_JSON);
        DistributionOrderPayload payload = new DistributionOrderPayload();
        payload.setType("distribution_product");

        DistributionOrderTypeMappingResolver.ResolvedOrderType result =
                resolver.resolve("DISTRIBUTION", "DISTRIBUTOR", payload);

        assertThat(result.code()).isEqualTo(OrderType.DISTRIBUTION_PRODUCT.getCode());
        assertThat(result.apiValue()).isEqualTo("distribution_product");
        assertThat(result.resolution()).isEqualTo("ALIAS");
    }

    @Test
    void shouldRejectInvalidConfiguredJsonInsteadOfSilentlyFallingBack() {
        DistributionOrderTypeMappingResolver resolver = resolver("{not-json");
        DistributionOrderPayload payload = new DistributionOrderPayload();
        payload.setType("coupon");

        assertThatThrownBy(() -> resolver.resolve("DISTRIBUTION", "DISTRIBUTOR", payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mapping config invalid");
    }

    @Test
    void shouldRejectInvalidInternalOrderType() {
        DistributionOrderTypeMappingResolver resolver = resolver("""
                {
                  "default": "coupon",
                  "aliases": {
                    "bad": "normal"
                  },
                  "rules": []
                }
                """);
        DistributionOrderPayload payload = new DistributionOrderPayload();
        payload.setType("bad");

        assertThatThrownBy(() -> resolver.resolve("DISTRIBUTION", "DISTRIBUTOR", payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mapping config invalid");
    }

    @Test
    void shouldMarkMissingProductRuleWhenStrictMappingIsEnabled() {
        DistributionOrderTypeMappingResolver resolver = resolver("""
                {
                  "default": "coupon",
                  "strictProductMapping": true,
                  "aliases": {
                    "coupon": "coupon"
                  },
                  "rules": []
                }
                """);
        DistributionOrderPayload payload = new DistributionOrderPayload();
        payload.setType("coupon");
        payload.setExternalProductId("prod_missing");

        DistributionOrderTypeMappingResolver.ResolvedOrderType result =
                resolver.resolve("DISTRIBUTION", "DISTRIBUTOR", payload);

        assertThat(result.missingProductRule()).isTrue();
        assertThat(result.resolution()).isEqualTo("MISSING_PRODUCT_RULE");
    }

    private DistributionOrderTypeMappingResolver resolver(String mappingJson) {
        return new DistributionOrderTypeMappingResolver(new ObjectMapper(), new StaticSystemConfigService(mappingJson));
    }

    private record StaticSystemConfigService(String value) implements SystemConfigService {

        @Override
        public List<SystemConfigDtos.ConfigResponse> listConfigs(String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SystemConfigDtos.ConfigResponse saveConfig(SystemConfigDtos.SaveConfigRequest request,
                                                          PermissionRequestContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SystemConfigDtos.DomainSettingsResponse getDomainSettings() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SystemConfigDtos.DomainSettingsResponse saveDomainSettings(SystemConfigDtos.SaveDomainSettingsRequest request,
                                                                          PermissionRequestContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean(String configKey, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public String getString(String configKey, String defaultValue) {
            return value;
        }
    }
}
