package com.backend.gateway.filter;

import com.backend.gateway.config.BaseAuthConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import network.HttpResponseUtils;
import network.TraceIdUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Callable;

@Slf4j
public abstract class AuthFilter<T extends BaseAuthConfig> extends AbstractGatewayFilterFactory<T> {

    private final Scheduler scheduler;
    private final CacheManager cacheManager;

    // JWT 公钥（从 Security 服务获取）
    private static PublicKey jwtPublicKey;

    // 签名密钥（用于 API 签名验证）
    @Value("${secure.api.signing.secret:default-signing-secret}")
    private String apiSigningSecret;

    public AuthFilter(Class<T> configClass, Scheduler scheduler, CacheManager cacheManager) {
        super(configClass);
        this.scheduler = scheduler;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void initJwtKey() {
        // 从 Nacos 或环境变量读取公钥
        String pubKeyPem = getJwtPublicKeyPem();
        if (pubKeyPem != null && !pubKeyPem.isBlank()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(
                    pubKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "")
                );
                jwtPublicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            } catch (Exception e) {
                log.error("Failed to load JWT public key", e);
            }
        }
    }

    /** 子类可覆盖，从 Nacos 或 direct call 获取公钥 */
    protected String getJwtPublicKeyPem() {
        return null;
    }

    protected abstract boolean isWhitelistedPath(String path);

    @Override
    public GatewayFilter apply(T config) {
        if (!config.isEnabled()) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return createAuthGatewayFilter(config);
    }

    private GatewayFilter createAuthGatewayFilter(T config) {
        return (exchange, chain) -> Mono.deferContextual(contextView -> {
            String traceId = contextView.getOrEmpty("traceId")
                    .map(Object::toString).orElse("NO_TRACE_ID");

            if (!"NO_TRACE_ID".equals(traceId)) {
                TraceIdUtils.setTraceId(traceId);
            }

            String path = exchange.getRequest().getURI().getPath();

            if (isWhitelistedPath(path)) {
                log.info("[traceId={}] ✅ Whitelisted path | Path={}", traceId, path);
                return chain.filter(exchange);
            }

            Callable<Boolean> validationCallable = () -> {
                TraceIdUtils.setTraceId(traceId);
                try {
                    // Step 1: JWT 验证（优先）
                    String authHeader = exchange.getRequest().getHeaders()
                            .getFirst(HttpHeaders.AUTHORIZATION);

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (jwtPublicKey != null) {
                            try {
                                Claims claims = Jwts.parser()
                                        .verifyWith(jwtPublicKey)
                                        .build()
                                        .parseSignedClaims(token)
                                        .getPayload();

                                log.info("[traceId={}] ✅ JWT valid | subject={} | exp={}",
                                        traceId, claims.getSubject(), claims.getExpiration());
                                return true;
                            } catch (Exception e) {
                                log.warn("[traceId={}] ❌ JWT invalid: {}", traceId, e.getMessage());
                            }
                        }
                    }

                    // Step 2: API 签名验证（用于服务间调用/Webhook）
                    String signature = exchange.getRequest().getHeaders().getFirst("X-Signature");
                    String timestamp = exchange.getRequest().getHeaders().getFirst("X-Timestamp");
                    String nonce = exchange.getRequest().getHeaders().getFirst("X-Nonce");

                    if (signature != null && timestamp != null && nonce != null) {
                        // 检查时间戳（5 分钟内有效）
                        long ts = Long.parseLong(timestamp);
                        if (Math.abs(Instant.now().getEpochSecond() - ts) > 300) {
                            log.warn("[traceId={}] ❌ Signature expired | ts={}", traceId, timestamp);
                            return false;
                        }

                        String method = exchange.getRequest().getMethod().name();
                        String payload = method + "\n" + path + "\n" + timestamp + "\n" + nonce;
                        String expectedSign = hmacSha256(apiSigningSecret, payload);

                        if (expectedSign.equals(signature)) {
                            log.info("[traceId={}] ✅ Signature valid", traceId);
                            return true;
                        }

                        log.warn("[traceId={}] ❌ Signature mismatch", traceId);
                        return false;
                    }

                    log.warn("[traceId={}] ❌ No auth provided | Path={}", traceId, path);
                    return false;
                } finally {
                    TraceIdUtils.clearMdc();
                }
            };

            Mono<Boolean> validationMono = Mono.fromCallable(validationCallable)
                    .subscribeOn(scheduler);

            return validationMono
                    .flatMap(authorized -> {
                        if (!authorized) {
                            return HttpResponseUtils.write(exchange.getResponse(),
                                    HttpResponseUtils.unauthorized("Unauthorized"));
                        }
                        return chain.filter(exchange);
                    })
                    .onErrorResume(ex -> {
                        log.error("[traceId={}] ❌ Auth error: {}", traceId, ex.getMessage());
                        return HttpResponseUtils.write(exchange.getResponse(),
                                HttpResponseUtils.internalError("Auth service unavailable"));
                    })
                    .doFinally(signal -> TraceIdUtils.clearMdc());
        });
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }
}