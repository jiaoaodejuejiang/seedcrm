package com.seedcrm.crm.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI seedCrmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SeedCRM API")
                        .version("distribution-scheme-b-v1")
                        .description("SeedCRM 后台与外部分销方案 B 联调接口文档。Swagger 用于查看接口定义，正式联调仍以联调工作台 dry-run 和异常队列追踪为准。"))
                .components(new Components()
                        .addSecuritySchemes("BackendToken", apiKey("X-Auth-Token", "后台登录 token"))
                        .addSecuritySchemes("PartnerCode", apiKey("X-Partner-Code", "外部合作方编码"))
                        .addSecuritySchemes("IdempotencyKey", apiKey("X-Idempotency-Key", "幂等键 / 防重复编号"))
                        .addSecuritySchemes("Timestamp", apiKey("X-Timestamp", "签名时间戳"))
                        .addSecuritySchemes("Nonce", apiKey("X-Nonce", "防重放随机串"))
                        .addSecuritySchemes("TraceId", apiKey("X-Trace-Id", "联调追踪编号"))
                        .addSecuritySchemes("Signature", apiKey("X-Signature", "HMAC-SHA256 签名")));
    }

    @Bean
    public GroupedOpenApi distributionOpenApi() {
        return GroupedOpenApi.builder()
                .group("distribution-open-api")
                .displayName("分销 Open API")
                .pathsToMatch("/open/distribution/**")
                .build();
    }

    @Bean
    public GroupedOpenApi schedulerAdminApi() {
        return GroupedOpenApi.builder()
                .group("scheduler-admin")
                .displayName("调度中心后台 API")
                .pathsToMatch("/scheduler/**")
                .build();
    }

    @Bean
    public GroupedOpenApi systemConfigApi() {
        return GroupedOpenApi.builder()
                .group("system-config")
                .displayName("系统配置 API")
                .pathsToMatch("/system-config/**")
                .build();
    }

    private SecurityScheme apiKey(String name, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(name)
                .description(description);
    }
}
