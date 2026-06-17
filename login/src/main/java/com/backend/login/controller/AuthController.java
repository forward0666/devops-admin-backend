package com.backend.login.controller;

import lombok.extern.slf4j.Slf4j;
import com.backend.login.dto.ApiResponseDto;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private com.backend.login.service.OperationLogService operationLogService;

    @Autowired
    private com.backend.login.service.SettingService settingService;

    @Autowired
    private SsoService ssoService;

    @Autowired
    private JwtUtil jwtUtil;

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

            // IP 访问控制
            if (!settingService.isIpAllowed(clientIP)) {
                return ResponseEntity.status(403).body(ApiResponseDto.error(403, "IP not allowed: " + clientIP));
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
            return ResponseEntity.status(code).body(ApiResponseDto.error(code, "Login failed: " + msg));
        }
    }

    /**
     * SSO 登录
     * 前端传 Keycloak token，后端验证后返回 devops-admin JWT
     */
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

            // 验证 Keycloak token 并获取/创建用户
            UserEntity user = ssoService.verifyAndGetUser(ssoRequest.token());

            // 生成 devops-admin JWT
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
            return ResponseEntity.status(401).body(ApiResponseDto.error(401, "SSO login failed: " + e.getMessage()));
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
