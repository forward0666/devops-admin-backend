package com.backend.login.service;

import com.backend.login.entity.UserEntity;
import com.backend.login.mapper.UserMapper;
import com.backend.utils.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Keycloak SSO 服务
 * 负责验证 Keycloak JWT token 并完成用户登录
 */
@Slf4j
@Service
public class SsoService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${keycloak.url:https://keycloak.sdjk35.com}")
    private String keycloakUrl;

    @Value("${keycloak.realm:master}")
    private String keycloakRealm;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    /**
     * 验证 Keycloak JWT token 并返回用户
     *
     * @param keycloakToken Keycloak 的 access_token
     * @return 用户实体
     */
    public UserEntity verifyAndGetUser(String keycloakToken) {
        try {
            // 1. 解析 JWT header 获取 kid
            String[] parts = keycloakToken.split("\\.");
            if (parts.length < 2) {
                throw new RuntimeException("Invalid JWT token format");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode headerNode = objectMapper.readTree(headerJson);
            String kid = headerNode.get("kid").asText();

            // 2. 解析 JWT payload 获取用户信息
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payloadNode = objectMapper.readTree(payloadJson);

            // 3. 从 Keycloak JWKS 获取公钥并验证签名
            RSAPublicKey publicKey = getPublicKey(kid);
            if (!verifySignature(keycloakToken, publicKey)) {
                throw new RuntimeException("JWT signature verification failed");
            }

            // 4. 验证 issuer
            String issuer = payloadNode.has("iss") ? payloadNode.get("iss").asText() : null;
            String expectedIssuer = keycloakUrl + "/realms/" + keycloakRealm;
            if (issuer != null && !issuer.equals(expectedIssuer)) {
                throw new RuntimeException("Invalid issuer: " + issuer);
            }

            // 5. 提取用户信息
            String username = payloadNode.has("preferred_username") ? payloadNode.get("preferred_username").asText() : null;
            String email = payloadNode.has("email") ? payloadNode.get("email").asText() : null;
            String name = payloadNode.has("name") ? payloadNode.get("name").asText() : null;

            if (username == null || username.isEmpty()) {
                throw new RuntimeException("No username found in token");
            }

            // 6. 查找或创建用户
            return findOrCreateUser(username, email, name);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("SSO token verification failed: {}", e.getMessage(), e);
            throw new RuntimeException("SSO authentication failed: " + e.getMessage());
        }
    }

    /**
     * 查找或创建 SSO 用户
     */
    private UserEntity findOrCreateUser(String username, String email, String name) {
        // 先按 username 查找
        UserEntity user = userMapper.findByUsername(username);
        if (user != null) {
            // 更新最后登录信息
            userMapper.updateLoginInfo(user.getId(), LocalDateTime.now(), null);
            // 如果本地用户没有 source，补充为 keycloak
            if (user.getSource() == null || user.getSource().isEmpty()) {
                user.setSource("keycloak");
                userMapper.update(user);
            }
            log.info("SSO user found: {}", username);
            return user;
        }

        // 按 email 查找
        if (email != null && !email.isEmpty()) {
            var users = userMapper.searchByUsernameOrEmail(email);
            if (!users.isEmpty()) {
                user = users.get(0);
                userMapper.updateLoginInfo(user.getId(), LocalDateTime.now(), null);
                log.info("SSO user found by email: {} -> {}", email, username);
                return user;
            }
        }

        // 创建新用户
        user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(name != null ? name : username);
        user.setRole("user");
        user.setActive(true);
        user.setEmailVerified(true);
        // SSO 用户设置随机密码（不会用于登录，SSO 登录不走密码验证）
        user.setPassword(passwordEncoder.encode("sso-" + System.currentTimeMillis()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLoginCount(0);
        user.setSource("keycloak");

        userMapper.insert(user);
        log.info("SSO user created: {} (email={})", username, email);

        return user;
    }

    /**
     * 从 Keycloak JWKS 获取公钥
     */
    private RSAPublicKey getPublicKey(String kid) throws Exception {
        // 先查缓存
        if (keyCache.containsKey(kid)) {
            return keyCache.get(kid);
        }

        String jwksUrl = keycloakUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/certs";
        log.info("Fetching JWKS from: {}", jwksUrl);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUrl))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("JWKS response status: {}, body length: {}", response.statusCode(), response.body().length());
        if (response.statusCode() != 200) {
            throw new RuntimeException("JWKS endpoint returned status " + response.statusCode() + ": " + response.body().substring(0, Math.min(200, response.body().length())));
        }
        JsonNode jwks = objectMapper.readTree(response.body());
        JsonNode keys = jwks.get("keys");

        if (keys == null) {
            throw new RuntimeException("No keys found in JWKS");
        }

        for (JsonNode key : keys) {
            if (kid.equals(key.get("kid").asText())) {
                String n = key.get("n").asText();
                String e = key.get("e").asText();

                // 构建 RSA 公钥
                java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(
                        new java.math.BigInteger(1, Base64.getUrlDecoder().decode(n)),
                        new java.math.BigInteger(1, Base64.getUrlDecoder().decode(e))
                );
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(spec);

                // 缓存公钥
                keyCache.put(kid, publicKey);
                return publicKey;
            }
        }

        throw new RuntimeException("Key not found for kid: " + kid);
    }

    /**
     * 验证 JWT 签名
     */
    private boolean verifySignature(String token, RSAPublicKey publicKey) {
        try {
            String[] parts = token.split("\\.");
            String headerPayload = parts[0] + "." + parts[1];
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);

            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(headerPayload.getBytes());
            return sig.verify(signature);
        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
