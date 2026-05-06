package com.backend.login.controller;

import com.backend.login.dto.ApiResponseDto;
import com.backend.login.dto.LoginRequestDto;
import com.backend.login.vo.LoginVo;
import com.backend.login.service.AuthService;
import com.backend.login.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private com.backend.login.service.OperationLogService operationLogService;

    @PostMapping("/authLogIn")
    public ApiResponseDto<LoginVo> login(@RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest.username() == null || loginRequest.username().isEmpty()) {
                return ApiResponseDto.error("Username is required");
            }
            if (loginRequest.password() == null || loginRequest.password().isEmpty()) {
                return ApiResponseDto.error("Password is required");
            }
            if (loginRequest.verificationCode() == null || loginRequest.verificationCode().isEmpty()) {
                return ApiResponseDto.error("Verification code is required");
            }

            var clientIP = securityService.getRealClientIP(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );

            var loginResponse = authService.login(loginRequest, clientIP);

            operationLogService.logUserOperation(
                null, loginRequest.username(), "LOGIN", "User Login",
                "AUTH", null, "POST", "/authLogIn",
                clientIP, request.getHeader("User-Agent"),
                null, null, true, null, "LOGIN");

            return ApiResponseDto.success("Login successful", loginResponse);
        } catch (Exception e) {
            try {
                var clientIP = securityService.getRealClientIP(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                operationLogService.logUserOperation(
                    null, loginRequest.username(), "LOGIN", "User Login",
                    "AUTH", null, "POST", "/authLogIn",
                    clientIP, request.getHeader("User-Agent"),
                    null, null, false, e.getMessage(), "LOGIN");
            } catch (Exception ignored) {}
            String msg = e.getMessage();
            int code = (msg != null && (msg.contains("locked") || msg.contains("password") || msg.contains("verification"))) ? 401 : 500;
            return ApiResponseDto.error(code, "Login failed: " + msg);
        }
    }

    @PostMapping("/authLogOut")
    public ApiResponseDto<Void> logout(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                authService.logout(authHeader.substring(7));
            }
            return ApiResponseDto.success("Logout successful", null);
        } catch (Exception e) {
            return ApiResponseDto.error("Logout failed: " + e.getMessage());
        }
    }
}
