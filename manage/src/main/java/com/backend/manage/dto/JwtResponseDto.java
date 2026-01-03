package com.backend.manage.dto;

import java.util.Map;

public record JwtResponseDto(
    Boolean success,
    String message,
    String token,
    Map<String, Object> data
) {
    public static JwtResponseDto success(String message, String token, Map<String, Object> data) {
        return new JwtResponseDto(true, message, token, data);
    }

    public static JwtResponseDto success(String message, String token) {
        return new JwtResponseDto(true, message, token, null);
    }

    public static JwtResponseDto error(String message) {
        return new JwtResponseDto(false, message, null, null);
    }
}
