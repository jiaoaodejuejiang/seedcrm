package com.seedcrm.crm.auth.controller;

import com.seedcrm.crm.auth.dto.AuthLoginRequest;
import com.seedcrm.crm.auth.dto.AuthLoginResponse;
import com.seedcrm.crm.auth.dto.AuthStoreOptionResponse;
import com.seedcrm.crm.auth.model.AuthenticatedUser;
import com.seedcrm.crm.auth.service.AuthService;
import com.seedcrm.crm.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@RequestBody AuthLoginRequest request) {
        String token = authService.login(
                request == null ? null : request.getUsername(),
                request == null ? null : request.getPassword(),
                request == null ? null : request.getStoreId(),
                request == null ? null : request.getStoreName());
        return ApiResponse.success(new AuthLoginResponse(token, authService.getUserOrThrow(token)));
    }

    @GetMapping("/me")
    public ApiResponse<AuthenticatedUser> me(HttpServletRequest request) {
        return ApiResponse.success(authService.getUserOrThrow(resolveToken(request)));
    }

    @GetMapping("/store-options")
    public ApiResponse<List<AuthStoreOptionResponse>> listStoreOptions() {
        return ApiResponse.success(authService.listStoreOptions());
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest request) {
        authService.logout(resolveToken(request));
        return ApiResponse.success(Boolean.TRUE);
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("X-Auth-Token");
        if (token == null || token.isBlank()) {
            token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        return token;
    }
}
