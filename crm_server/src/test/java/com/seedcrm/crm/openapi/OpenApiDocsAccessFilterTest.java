package com.seedcrm.crm.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OpenApiDocsAccessFilterTest {

    @Test
    void shouldRequireIntegrationRoleForRemoteSwaggerDocs() throws Exception {
        AuthService authService = mock(AuthService.class);
        OpenApiDocsAccessFilter filter = filter(authService, false);
        MockHttpServletRequest request = request("/swagger-ui.html", "203.0.113.10");
        request.addHeader("X-Auth-Token", "partner-token");
        when(authService.resolve("partner-token")).thenReturn(Optional.of(user("PARTNER_APP")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Swagger/OpenAPI");
    }

    @Test
    void shouldAllowIntegrationOperatorForRemoteSwaggerDocs() throws Exception {
        AuthService authService = mock(AuthService.class);
        OpenApiDocsAccessFilter filter = filter(authService, false);
        MockHttpServletRequest request = request("/v3/api-docs/distribution-open-api", "203.0.113.10");
        request.addHeader("X-Auth-Token", "operator-token");
        when(authService.resolve("operator-token")).thenReturn(Optional.of(user("INTEGRATION_OPERATOR")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowIntegrationAdminForRemoteSwaggerDocs() throws Exception {
        AuthService authService = mock(AuthService.class);
        OpenApiDocsAccessFilter filter = filter(authService, false);
        MockHttpServletRequest request = request("/v3/api-docs/distribution-open-api", "203.0.113.10");
        request.addHeader("X-Auth-Token", "admin-token");
        when(authService.resolve("admin-token")).thenReturn(Optional.of(user("INTEGRATION_ADMIN")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowLocalSwaggerDocsWhenLocalAccessIsEnabled() throws Exception {
        OpenApiDocsAccessFilter filter = filter(mock(AuthService.class), true);
        MockHttpServletRequest request = request("/swagger-ui/index.html", "127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldHideSwaggerDocsWhenDisabled() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("seedcrm.openapi.enabled", "false")
                .withProperty("seedcrm.openapi.allow-local-without-token", "true");
        OpenApiDocsAccessFilter filter = new OpenApiDocsAccessFilter(mock(AuthService.class), environment);
        MockHttpServletRequest request = request("/swagger-ui.html", "127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldIgnoreNonOpenApiRequests() throws Exception {
        OpenApiDocsAccessFilter filter = filter(mock(AuthService.class), false);
        MockHttpServletRequest request = request("/scheduler/providers", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private OpenApiDocsAccessFilter filter(AuthService authService, boolean allowLocal) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("seedcrm.openapi.enabled", "true")
                .withProperty("seedcrm.openapi.allow-local-without-token", String.valueOf(allowLocal));
        return new OpenApiDocsAccessFilter(authService, environment);
    }

    private MockHttpServletRequest request(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private AuthenticatedUser user(String roleCode) {
        return new AuthenticatedUser("tester", "测试用户", roleCode, roleCode, "ALL", 1L, 10L, "总部",
                List.of(), null, List.of("SETTING", "SCHEDULER"));
    }
}
