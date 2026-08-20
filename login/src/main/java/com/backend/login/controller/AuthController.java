package com.backend.login.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.login.dto.LoginRequestDto;
import com.backend.login.vo.LoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    @Value("${security.service.url:http://security:8080}")
    private String securityServiceUrl;

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
            log.info("Login attempt: username={}, ip={}", loginRequest.username(), clientIP);

            // Dev mode: call security service to generate real JWT
            String token = callSecurityService(loginRequest.username(), clientIP);
            LoginVo mockVo = new LoginVo(
                token,
                1L,
                loginRequest.username(),
                "Administrator",
                "admin@example.com",
                "admin",
                null
            );

            return ResponseEntity.ok(ApiResponseDto.success("Login successful", mockVo));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponseDto.error("Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/authLogOut")
    public ResponseEntity<ApiResponseDto<Void>> logout(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponseDto.success("Logout successful", null));
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip;
        return request.getRemoteAddr();
    }

    /**
     * 调用 Security 服务生成 JWT token
     * 用 K8s service DNS 直连
     */
    private String callSecurityService(String username, String ip) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String body = "{\"userId\":1,\"username\":\"" + username + "\"}";
            var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(securityServiceUrl + "/security/generate"))
                .header("Content-Type", "application/json")
                .header("X-Real-IP", ip)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var root = mapper.readTree(response.body());
                // /generate 返回格式: {"success":true,"token":"***"}
                var tokenNode = root.get("token");
                if (tokenNode != null && !tokenNode.asText().isBlank()) {
                    return tokenNode.asText();
                }
                // 备用格式: {"data":{"token":"***"}}
                var data = root.get("data");
                if (data != null && data.get("token") != null) {
                    return data.get("token").asText();
                }
            }
            log.warn("Security service returned {}: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Failed to call security service", e);
        }
        // fallback
        return "mock-token-" + System.currentTimeMillis();
    }
}