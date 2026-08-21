package com.backend.login.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.login.dto.LoginRequestDto;
import com.backend.login.vo.LoginVo;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Slf4j
@RestController
public class AuthController {

    @Value("${jwt.rsa.private-key:}")
    private String privateKeyPem;

    private PrivateKey privateKey;

    @PostConstruct
    public void init() {
        try {
            if (privateKeyPem != null && !privateKeyPem.isBlank()) {
                String cleaned = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\\\n", "")
                    .replaceAll("\\s", "");
                var keyBytes = Base64.getDecoder().decode(cleaned);
                privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            }
        } catch (Exception e) {
            log.warn("Failed to load RSA private key, generating temporary key", e);
        }
        if (privateKey == null) {
            var pair = Keys.keyPairFor(SignatureAlgorithm.RS256);
            privateKey = pair.getPrivate();
        }
    }

    @PostMapping("/authLogIn")
    public ResponseEntity<ApiResponseDto<LoginVo>> login(@RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest.username() == null || loginRequest.password() == null) {
                return ResponseEntity.badRequest().body(ApiResponseDto.error("Username and password required"));
            }

            var clientIP = getClientIP(request);

            // 本地生成 JWT（与 Security 共享同一套 RSA 密钥）
            String token = Jwts.builder()
                .subject(loginRequest.username())
                .claim("userId", 1)
                .claim("username", loginRequest.username())
                .claim("role", "admin")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(86400)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

            LoginVo vo = new LoginVo(
                token, 1L, loginRequest.username(),
                "Administrator", "admin@example.com", "admin", null
            );

            return ResponseEntity.ok(ApiResponseDto.success("Login successful", vo));
        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/authLogOut")
    public ResponseEntity<ApiResponseDto<Void>> logout() {
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