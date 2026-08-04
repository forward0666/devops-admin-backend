package com.backend.cloudflare.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.cloudflare.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cloudflare/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @PostMapping("/verify")
    public ApiResponseDto<Map<String, Object>> verify(
            @RequestHeader("X-Cf-Token") String cfToken) {
        try {
            Map<String, Object> result = tokenService.verifyToken(cfToken);
            return ApiResponseDto.success("Token verified", result);
        } catch (Exception e) {
            log.error("Token verify failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("Verify failed: " + e.getMessage());
        }
    }

    @GetMapping("/account")
    public ApiResponseDto<Map<String, Object>> listAccounts(
            @RequestHeader("X-Cf-Token") String cfToken) {
        try {
            Map<String, Object> result = tokenService.listAccounts(cfToken);
            return ApiResponseDto.success("ok", result);
        } catch (Exception e) {
            log.error("List accounts failed: {}", e.getMessage(), e);
            return ApiResponseDto.error("List accounts failed: " + e.getMessage());
        }
    }
}