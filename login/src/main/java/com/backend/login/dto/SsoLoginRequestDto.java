package com.backend.login.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * SSO 登录请求
 * 前端传 Keycloak 的 access_token
 */
public record SsoLoginRequestDto(
        @NotBlank(message = "SSO token is required")
        String token
) {}
