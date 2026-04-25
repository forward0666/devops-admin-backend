package com.backend.user.dto.login;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record JwtGenerateRequestDto(
    @NotBlank
    String subject,
    Map<String, Object> claims
) {
    public static JwtGenerateRequestDto of(String subject, Map<String, Object> claims) {
        return new JwtGenerateRequestDto(subject, claims);
    }
}
