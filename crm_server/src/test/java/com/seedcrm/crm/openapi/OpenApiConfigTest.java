package com.seedcrm.crm.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void shouldDeclareDistributionSecurityHeaders() {
        OpenAPI openAPI = config.seedCrmOpenApi();

        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKeys(
                        "BackendToken",
                        "PartnerCode",
                        "IdempotencyKey",
                        "Timestamp",
                        "Nonce",
                        "TraceId",
                        "Signature");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("distribution-scheme-b-v1");
    }

    @Test
    void shouldKeepOpenApiGroupsInsideAllowedBoundaries() {
        assertThat(config.distributionOpenApi().getGroup()).isEqualTo("distribution-open-api");
        assertThat(config.distributionOpenApi().getPathsToMatch()).containsExactly("/open/distribution/**");
        assertThat(config.schedulerAdminApi().getPathsToMatch()).containsExactly("/scheduler/**");
        assertThat(config.systemConfigApi().getPathsToMatch()).containsExactly("/system-config/**");
    }
}
