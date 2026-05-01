package com.seedcrm.crm.openapi;

import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OpenApiDocsAccessFilter extends OncePerRequestFilter {

    private static final Set<String> DOC_ROLES = Set.of("ADMIN", "INTEGRATION_ADMIN", "INTEGRATION_OPERATOR");

    private final AuthService authService;
    private final Environment environment;

    public OpenApiDocsAccessFilter(AuthService authService, Environment environment) {
        this.authService = authService;
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isOpenApiDocsRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!environment.getProperty("seedcrm.openapi.enabled", Boolean.class, true)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (environment.getProperty("seedcrm.openapi.allow-local-without-token", Boolean.class, true)
                && isLocalRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = resolveToken(request);
        AuthenticatedUser user = authService.resolve(token).orElse(null);
        String roleCode = normalize(user == null ? null : user.getRoleCode());
        if (DOC_ROLES.contains(roleCode)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Swagger/OpenAPI 仅允许管理员、集成管理员或集成操作员访问\"}");
    }

    private boolean isOpenApiDocsRequest(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return path.equals("/swagger-ui.html")
                || path.equals("/swagger-ui")
                || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/");
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String remoteAddress = request == null ? "" : request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress)
                || "localhost".equalsIgnoreCase(remoteAddress);
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request == null ? null : request.getHeader("X-Auth-Token");
        if (!StringUtils.hasText(token) && request != null) {
            token = request.getHeader("Authorization");
            if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        return token;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
