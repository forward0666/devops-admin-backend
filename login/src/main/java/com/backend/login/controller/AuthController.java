package com.backend.login.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.utils.exception.BizException;
import com.backend.login.dto.LoginRequestDto;
import com.backend.login.dto.SsoLoginRequestDto;
import com.backend.login.dto.JwtGenerateRequestDto;
import com.backend.login.vo.LoginVo;
import com.backend.login.service.AuthService;
import com.backend.login.service.SecurityService;
import com.backend.login.service.SsoService;
import com.backend.login.entity.UserEntity;
import com.backend.utils.JwtUtil;
import java.util.HashMap;
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
    private final SecurityService securityService;
    private final com.backend.login.service.OperationLogService operationLogService;
    private final com.backend.login.service.SettingService settingService;
    private final SsoService ssoService;
    private final JwtUtil jwtUtil;

    @PostMapping("/authLogIn")
    public ResponseEntity<ApiResponseDto<LoginVo>> login(@RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest.username() == null || loginRequest.username().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("Username is required"));
            }
            if (loginRequest.password() == null || loginRequest.password().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("Password is required"));
            }
            if (loginRequest.verificationCode() == null || loginRequest.verificationCode().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("Verification code is required"));
            }

            var clientIP = securityService.getRealClientIP(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );

            if (!settingService.isIpAllowed(clientIP)) {
                return ResponseEntity.status(403).body(ApiResponseDto.error("IP not allowed: " + clientIP));
            }

            var loginResponse = authService.login(loginRequest, clientIP);

            operationLogService.logUserOperation(
                null, loginRequest.username(), "LOGIN", "User Login",
                "AUTH", null, "POST", "/authLogIn",
                clientIP, request.getHeader("User-Agent"),
                null, null, true, null, "LOGIN");

            return ResponseEntity.ok(ApiResponseDto.success("Login successful", loginResponse));
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
            return ResponseEntity.status(code).body(ApiResponseDto.error("Login failed: " + msg));
        }
    }

    @PostMapping("/authSSO")
    public ResponseEntity<ApiResponseDto<LoginVo>> authSSO(@RequestBody SsoLoginRequestDto ssoRequest, HttpServletRequest request) {
        try {
            if (ssoRequest.token() == null || ssoRequest.token().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("SSO token is required"));
            }

            var clientIP = securityService.getRealClientIP(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );

            UserEntity user = ssoService.verifyAndGetUser(ssoRequest.token());

            var claims = new HashMap<String, Object>();
            claims.put("userId", user.getId());
            claims.put("role", user.getRole() != null ? user.getRole() : "");
            claims.put("email", user.getEmail() != null ? user.getEmail() : "");
            claims.put("username", user.getUsername());

            var jwtRequest = JwtGenerateRequestDto.of(user.getUsername(), claims);
            var jwtResponse = authService.generateToken(jwtRequest);

            if (jwtResponse == null || !jwtResponse.success() || jwtResponse.token() == null) {
                return ResponseEntity.status(500).body(ApiResponseDto.error("Failed to generate token"));
            }

            LoginVo loginVo = LoginVo.from(jwtResponse.token(), user);

            operationLogService.logUserOperation(
                user.getId(), user.getUsername(), "SSO_LOGIN", "SSO Login",
                "AUTH", null, "POST", "/authSSO",
                clientIP, request.getHeader("User-Agent"),
                null, null, true, null, "LOGIN");

            return ResponseEntity.ok(ApiResponseDto.success("SSO login successful", loginVo));
        } catch (Exception e) {
            try {
                var clientIP = securityService.getRealClientIP(
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Real-IP"),
                    request.getRemoteAddr()
                );
                operationLogService.logUserOperation(
                    null, "sso-user", "SSO_LOGIN", "SSO Login",
                    "AUTH", null, "POST", "/authSSO",
                    clientIP, request.getHeader("User-Agent"),
                    null, null, false, e.getMessage(), "LOGIN");
            } catch (Exception ignored) {}
            log.error("SSO login failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(ApiResponseDto.error("SSO login failed: " + e.getMessage()));
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
}