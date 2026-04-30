package com.seedcrm.crm.permission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class PermissionRequestContextResolverTest {

    @Mock
    private AuthService authService;

    @Test
    void shouldResolvePartnerCodeFromAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(
                "partner_app",
                "外部伙伴应用",
                "partner_app",
                "外部伙伴应用",
                "partner",
                93001L,
                null,
                null,
                List.of(),
                null,
                List.of("SCHEDULER"));
        user.setPartnerCode("distribution");
        when(authService.resolve("token-001")).thenReturn(Optional.of(user));
        PermissionRequestContextResolver resolver = new PermissionRequestContextResolver(authService, new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Token", "token-001");

        PermissionRequestContext context = resolver.resolve(request);

        assertThat(context.getRoleCode()).isEqualTo("PARTNER_APP");
        assertThat(context.getDataScope()).isEqualTo("PARTNER");
        assertThat(context.getCurrentPartnerCode()).isEqualTo("DISTRIBUTION");
        assertThat(context.getResourcePartnerCode()).isEqualTo("DISTRIBUTION");
    }

    @Test
    void shouldResolvePartnerCodeFromLocalHeaderFallback() {
        when(authService.resolve(null)).thenReturn(Optional.empty());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        PermissionRequestContextResolver resolver = new PermissionRequestContextResolver(authService, environment);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Role-Code", "partner_app");
        request.addHeader("X-Data-Scope", "partner");
        request.addHeader("X-Partner-Code", "distribution_a");
        request.addHeader("X-Resource-Partner-Code", "distribution_b");

        PermissionRequestContext context = resolver.resolve(request);

        assertThat(context.getRoleCode()).isEqualTo("PARTNER_APP");
        assertThat(context.getDataScope()).isEqualTo("PARTNER");
        assertThat(context.getCurrentPartnerCode()).isEqualTo("DISTRIBUTION_A");
        assertThat(context.getResourcePartnerCode()).isEqualTo("DISTRIBUTION_B");
    }
}
