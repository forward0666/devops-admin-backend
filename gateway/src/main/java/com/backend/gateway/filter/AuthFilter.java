package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Slf4j
public class AuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    private static final List<String> DEFAULT_WHITELIST = List.of(
        "/security/public-key",
        "/security/generate",
        "/security/verificationCode",
        "/login/api/login",
        "/login/api/register",
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness"
    );

    @Value("${auth.public-key.url:http://security:8080/security/public-key}")
    private String publicKeyUrl;

    @Value("${auth.whitelist.paths:}")
    private String whitelistConfig;

    private volatile List<String> whitelist;
    private volatile PublicKey publicKey;
    private volatile long lastKeyRefresh = 0;
    private static final long KEY_REFRESH_INTERVAL_MS = 3600_000;

    public AuthFilter(Class<T> configClass) {
        super(configClass);
    }

    @PostConstruct
    public void init() {
        refreshPublicKey();
        initWhitelist();
    }

    private void initWhitelist() {
        if (whitelistConfig != null && !whitelistConfig.isBlank()) {
            whitelist = List.of(whitelistConfig.split(","));
            log.info("✅ Whitelist loaded from config: {}", whitelist);
        } else {
            whitelist = DEFAULT_WHITELIST;
            log.info("✅ Whitelist using defaults: {}", whitelist);
        }
    }

    private void refreshPublicKey() {
        try {
            java.net.URL url = new java.net.URL(publicKeyUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            String response = new String(conn.getInputStream().readAllBytes());
            
            // 解析 JSON: {"publicKey":"-----BEGIN PUBLIC KEY-----..."}
            String keyStr = response;
            if (response.contains("\"publicKey\"")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                keyStr = mapper.readTree(response).get("publicKey").asText();
            }

            String pem = keyStr
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.publicKey = kf.generatePublic(spec);
            this.lastKeyRefresh = System.currentTimeMillis();
            log.info("✅ JWT public key loaded ({} bytes)", keyBytes.length);
        } catch (Exception e) {
            log.warn("⚠️ Failed to load JWT public key from {}: {}", publicKeyUrl, e.getMessage());
        }
    }

    @Override
    public GatewayFilter apply(T config) {
        if (!config.isEnabled()) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return createAuthGatewayFilter(config);
    }

    private GatewayFilter createAuthGatewayFilter(T config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // 白名单放行
            if (isWhitelisted(path)) {
                log.debug("✅ Whitelisted path: {}", path);
                return chain.filter(exchange);
            }

            // 定时刷新公钥
            if (System.currentTimeMillis() - lastKeyRefresh > KEY_REFRESH_INTERVAL_MS) {
                refreshPublicKey();
            }

            // 获取 Authorization header
            String authHeader = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, "Authorization header required");
            }

            String token = authHeader.substring(7);

            // 本地验证 JWT
            try {
                if (publicKey == null) {
                    return unauthorized(exchange, "Public key not loaded");
                }

                Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

                // 验证通过，向下游传递用户信息
                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(r -> r
                        .header("X-User-Id", claims.getSubject())
                        .header("X-User-Role", claims.get("role", String.class))
                        .header("X-Trace-Id", claims.getId())
                    )
                    .build();

                log.debug("✅ JWT validated: subject={}, role={}", 
                    claims.getSubject(), claims.get("role"));

                return chain.filter(mutatedExchange);

            } catch (SignatureException e) {
                return unauthorized(exchange, "Invalid token signature");
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                return unauthorized(exchange, "Token expired");
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
                return unauthorized(exchange, "Invalid token");
            }
        };
    }

    private boolean isWhitelisted(String path) {
        for (String wl : whitelist) {
            if (path.startsWith(wl.trim())) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        byte[] body = String.format(
            "{\"code\":401,\"success\":false,\"message\":\"%s\"}", message
        ).getBytes();
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}