package com.seedcrm.crm.order.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OrderAmountMaskingSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMaskChineseAndCommonAmountKeysWithoutMaskingServiceText() throws Exception {
        String masked = OrderAmountMaskingSupport.maskServiceDetailJson("""
                {
                  "\\u91d1\\u989d": 1288.00,
                  "\\u603b\\u4ef7": 1999.00,
                  "\\u670d\\u52a1\\u9879\\u76ee": "\\u4eb2\\u5b50\\u5199\\u771f",
                  "serviceCost": 99,
                  "costume": "\\u767d\\u8272\\u793c\\u670d",
                  "serviceTemplate": {
                    "config": {
                      "\\u5355\\u4ef7": 199,
                      "totalDuration": 120
                    }
                  }
                }
                """, objectMapper);

        JsonNode root = objectMapper.readTree(masked);

        assertThat(root.path("\u91d1\u989d").isNull()).isTrue();
        assertThat(root.path("\u603b\u4ef7").isNull()).isTrue();
        assertThat(root.path("serviceCost").isNull()).isTrue();
        assertThat(root.path("serviceTemplate").path("config").path("\u5355\u4ef7").isNull()).isTrue();
        assertThat(root.path("\u670d\u52a1\u9879\u76ee").asText()).isEqualTo("\u4eb2\u5b50\u5199\u771f");
        assertThat(root.path("costume").asText()).isEqualTo("\u767d\u8272\u793c\u670d");
        assertThat(root.path("serviceTemplate").path("config").path("totalDuration").asInt()).isEqualTo(120);
        assertThat(root.path(OrderAmountMaskingSupport.MASK_MARKER).asBoolean()).isTrue();
    }
}
