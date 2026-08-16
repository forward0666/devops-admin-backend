package com.backend.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@RefreshScope
@Component
public class AuthFilter {

    // ==================== 配置注入 ====================

    @Value("${gateway.type:client}")
    private String gatewayType; // client | admin

    @Value("${auth.public-key.url:http://security:8080/security/public-key}")
    private String publicKeyUrl;

    @Value("${auth.whitelist.paths:/security/public-key,/security/generate,/security/verificationCode,/login/api/login,/login/api/register,/actuator/health}")
    private String whitelistPaths;

    @Value("${auth.cf.header:X-Forwarded-For}")
    private String cfHeaderName;

    @Value("${auth.admin.header:X-Manage-Auth}")
    private String adminHeaderName;

    @Value("${auth.admin.header-value:admin-token}")
    private String adminHeaderValue;

    private final StringRedisTemplate redisTemplate;

    // ==================== 内部状态 ====================

    private volatile PublicKey publicKey;
    private volatile long lastKeyRefresh = 0;
    private static final long KEY_REFRESH_INTERVAL_MS = 3600_000;
    private static final String REDIS_IP_WHITELIST_KEY = "gateway:ip-whitelist:admin";
    private static final long REDIS_CACHE_SECONDS = 300;

    public AuthFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        refreshPublicKey();
    }

    // ==================== 公钥刷新 ====================

    private void refreshPublicKey() {
        try {
            java.net.URL url = new java.net.URL(publicKeyUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            String response = new String(conn.getInputStream().readAllBytes());
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
            log.warn("⚠️ Failed to load JWT public key: {}", e.getMessage());
        }
    }

    // ==================== 核心过滤逻辑 ====================

    public GatewayFilter createAuthFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // 1. 路径白名单（从配置注入，支持 Nacos 动态刷新）
            List<String> whitelist = parseWhitelist(whitelistPaths);
            if (matchWhitelist(path, whitelist)) {
                log.debug("✅ Whitelist path: {}", path);
                return chain.filter(exchange);
            }

            // 2. CF Header 验证（所有请求都必须携带）
            String cfValue = exchange.getRequest().getHeaders().getFirst(cfHeaderName);
            if (cfValue == null || cfValue.isBlank()) {
                log.warn("❌ Missing required header: {}", cfHeaderName);
                return unauthorized(exchange, "Missing required header: " + cfHeaderName);
            }

            // 3. JWT 验证（本地公钥验证）
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, "Authorization header required");
            }

            String token = authHeader.substring(7);
            try {
                if (publicKey == null) {
                    refreshPublicKey();
                    if (publicKey == null) {
                        return unauthorized(exchange, "Public key not loaded");
                    }
                }

                // 定时刷新公钥
                if (System.currentTimeMillis() - lastKeyRefresh > KEY_REFRESH_INTERVAL_MS) {
                    refreshPublicKey();
                }

                Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

                // JWT 验证通过，检查 IP 白名单
                String requestIp = extractClientIp(exchange);
                String role = claims.get("role", String.class);

                // 如果是 admin 角色或 admin gateway，检查 IP 白名单
                if ("admin".equalsIgnoreCase(gatewayType) || "admin".equalsIgnoreCase(role)) {
                    if (!isIpWhitelisted(requestIp)) {
                        log.warn("❌ IP not whitelisted for admin: {}", requestIp);
                        return unauthorized(exchange, "IP not authorized for admin access");
                    }
                }

                // 构造下游请求头
                ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(r -> r
                        .header("X-User-Id", claims.getSubject())
                        .header("X-User-Role", role)
                        .header("X-Trace-Id", claims.getId())
                        .header("X-Real-IP", requestIp)
                        .header(cfHeaderName, cfValue)
                    )
                    .build();

                log.debug("✅ Access granted: user={}, role={}, ip={}", claims.getSubject(), role, requestIp);
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

    // ==================== IP 白名单 ====================

    private String extractClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst(cfHeaderName);
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim(); // X-Forwarded-For 可能有多级
        }
        ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        return exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    }

    private boolean isIpWhitelisted(String ip) {
        if (ip == null || ip.isBlank()) return false;

        try {
            // 从 Redis 获取白名单列表，缓存 5 分钟
            Set<String> whitelist = redisTemplate.opsForSet().members(REDIS_IP_WHITELIST_KEY);
            if (whitelist == null || whitelist.isEmpty()) {
                log.warn("IP whitelist is empty in Redis, allowing none");
                return false;
            }

            for (String entry : whitelist) {
                if (entry.contains("/")) {
                    // CIDR 格式：192.168.1.0/24 或 10.0.0.1/32
                    if (matchCidr(ip, entry.trim())) {
                        return true;
                    }
                } else {
                    // 单 IP
                    if (ip.equals(entry.trim())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Redis IP whitelist check failed: {}", e.getMessage());
            return false; // Redis 不可用时拒绝，保安全
        }
    }

    private boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            int prefixLen = Integer.parseInt(parts[1]);
            java.net.InetAddress inetAddr = java.net.InetAddress.getByName(ip);
            java.net.InetAddress cidrAddr = java.net.InetAddress.getByName(parts[0]);

            byte[] addrBytes = inetAddr.getAddress();
            byte[] cidrBytes = cidrAddr.getAddress();

            if (addrBytes.length != cidrBytes.length) return false;

            int fullBytes = prefixLen / 8;
            int remainderBits = prefixLen % 8;

            for (int i = 0; i < fullBytes && i < addrBytes.length; i++) {
                if (addrBytes[i] != cidrBytes[i]) return false;
            }

            if (remainderBits > 0 && fullBytes < addrBytes.length) {
                int mask = (0xFF << (8 - remainderBits)) & 0xFF;
                if ((addrBytes[fullBytes] & mask) != (cidrBytes[fullBytes] & mask)) return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 路径白名单 ====================

    private List<String> parseWhitelist(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        return Arrays.asList(config.split(","));
    }

    private boolean matchWhitelist(String path, List<String> whitelist) {
        for (String wl : whitelist) {
            if (path.startsWith(wl.trim())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 公共 ====================

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