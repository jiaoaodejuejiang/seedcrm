package com.seedcrm.crm.order.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class OrderAmountMaskingSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void maskServiceDetailJsonShouldScrubNestedAmountsAndMarkPayload() throws Exception {
        String masked = OrderAmountMaskingSupport.maskServiceDetailJson("""
                {
                  "serviceRequirement": "portrait",
                  "serviceConfirmAmount": 1288.00,
                  "确认金额": 3888,
                  "serviceTemplate": {
                    "config": {
                      "price": 99,
                      "sections": [
                        { "deposit": 200, "title": "A", "定金": 50 }
                      ]
                    }
                  }
                }
                """, objectMapper);

        JsonNode root = objectMapper.readTree(masked);

        assertThat(root.path("serviceRequirement").asText()).isEqualTo("portrait");
        assertThat(root.path("serviceConfirmAmount").isNull()).isTrue();
        assertThat(root.path("确认金额").isNull()).isTrue();
        assertThat(root.path("serviceTemplate").path("config").path("price").isNull()).isTrue();
        assertThat(root.path("serviceTemplate").path("config").path("sections").get(0).path("deposit").isNull()).isTrue();
        assertThat(root.path("serviceTemplate").path("config").path("sections").get(0).path("定金").isNull()).isTrue();
        assertThat(root.path(OrderAmountMaskingSupport.MASK_MARKER).asBoolean()).isTrue();
    }

    @Test
    void restoreMaskedAmountFieldsShouldKeepHistoricalAmountsOnMaskedSave() throws Exception {
        ObjectNode incoming = (ObjectNode) objectMapper.readTree("""
                {
                  "_amountsMasked": true,
                  "serviceRequirement": "updated",
                  "serviceConfirmAmount": null,
                  "serviceTemplate": {
                    "config": { "price": null, "title": "A" }
                  }
                }
                """);

        OrderAmountMaskingSupport.restoreMaskedAmountFields(incoming, """
                {
                  "serviceRequirement": "old",
                  "serviceConfirmAmount": 1288.00,
                  "serviceTemplate": {
                    "config": { "price": 99, "title": "A" }
                  }
                }
                """, objectMapper);

        assertThat(incoming.has(OrderAmountMaskingSupport.MASK_MARKER)).isFalse();
        assertThat(incoming.path("serviceRequirement").asText()).isEqualTo("updated");
        assertThat(incoming.path("serviceConfirmAmount").decimalValue()).isEqualByComparingTo("1288.00");
        assertThat(incoming.path("serviceTemplate").path("config").path("price").asInt()).isEqualTo(99);
    }
}
