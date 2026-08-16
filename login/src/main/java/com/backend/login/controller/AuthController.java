package com.backend.login.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.login.dto.LoginRequestDto;
import com.backend.login.vo.LoginVo;
import com.backend.login.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/authLogIn")
    public ResponseEntity<ApiResponseDto<LoginVo>> login(@RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest.username() == null || loginRequest.username().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("Username is required"));
            }
            if (loginRequest.password() == null || loginRequest.password().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("Password is required"));
            }

            var clientIP = getClientIP(request);
            var loginResponse = authService.login(loginRequest, clientIP);

            return ResponseEntity.ok(ApiResponseDto.success("Login successful", loginResponse));
        } catch (Exception e) {
            String msg = e.getMessage();
            int code = (msg != null && (msg.contains("locked") || msg.contains("password"))) ? 401 : 500;
            return ResponseEntity.status(code).body(ApiResponseDto.error("Login failed: " + msg));
        }
    }

    @PostMapping("/authLogOut")
    public ResponseEntity<ApiResponseDto<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(ApiResponseDto.success("Logout successful", null));
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip;
        return request.getRemoteAddr();
    }
}