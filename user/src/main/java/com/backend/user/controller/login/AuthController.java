package com.backend.user.controller.login;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.dto.login.LoginRequestDto;
import com.backend.user.dto.login.LoginResponseDto;
import com.backend.user.service.login.AuthService;
import com.backend.user.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ApiResponseDto<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest.username() == null || loginRequest.username().isEmpty()) {
                return ApiResponseDto.error("Username is required");
            }

            if (loginRequest.password() == null || loginRequest.password().isEmpty()) {
                return ApiResponseDto.error("Password is required");
            }

            var clientIP = request.getHeader("X-Forwarded-For");
            if (clientIP == null || clientIP.isEmpty()) {
                clientIP = request.getHeader("X-Real-IP");
            }
            if (clientIP == null || clientIP.isEmpty()) {
                clientIP = request.getRemoteAddr();
            }

            var loginResponse = authService.login(loginRequest, clientIP);
            return ApiResponseDto.success("Login successful", loginResponse);
        } catch (Exception e) {
            return ApiResponseDto.error("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponseDto<Void> logout() {
        try {
            return ApiResponseDto.success("Logout successful", null);
        } catch (Exception e) {
            return ApiResponseDto.error("Logout failed: " + e.getMessage());
        }
    }

    @PostMapping("/validate-token")
    public ApiResponseDto<Map<String, Object>> validateToken(HttpServletRequest request) {
        try {
            var authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                var result = new HashMap<String, Object>();
                result.put("valid", false);
                return ApiResponseDto.success("Invalid token format", result);
            }

            var token = authHeader.substring(7);
            boolean isValid = authService.validateToken(token);

            var result = isValid
                ? Map.<String, Object>of("valid", true, "message", "Token is valid")
                : Map.<String, Object>of("valid", false, "message", "Token is invalid or expired");

            return ApiResponseDto.success("Token validation completed", result);
        } catch (Exception e) {
            var result = Map.<String, Object>of(
                "valid", false,
                "message", "Token validation failed: " + e.getMessage()
            );
            return ApiResponseDto.success("Token validation completed", result);
        }
    }
}
