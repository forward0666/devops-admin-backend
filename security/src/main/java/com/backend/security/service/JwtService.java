package com.backend.security.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpirationInMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 生成JWT token
     * @param subject 主题（通常是用户ID或用户名）
     * @param claims 额外的声明
     * @return JWT token
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        // 使用Java 21的特性，更简洁的代码
        var now = Instant.now();
        var expiryDate = Date.from(now.plusSeconds(jwtExpirationInMs));
        
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(expiryDate)
                .signWith(getSigningKey());

        // 使用Java 21的特性处理可选参数
        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 生成简单的JWT token（只包含subject）
     * @param subject 主题
     * @return JWT token
     */
    public String generateToken(String subject) {
        return generateToken(subject, null);
    }

    /**
     * 从JWT token中获取subject
     * @param token JWT token
     * @return subject
     */
    public String getSubjectFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * 从JWT token中获取所有claims
     * @param token JWT token
     * @return claims
     */
    public Claims getClaimsFromToken(String token) {
        // 使用Java 21的特性，更简洁的链式调用
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证JWT token是否有效
     * @param token JWT token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            // 使用Java 21的特性，更简洁的代码
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException |
                 UnsupportedJwtException | IllegalArgumentException ex) {
            // 使用Java 21的多catch特性，简化异常处理
            log.error("JWT validation failed: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            return false;
        }
    }

    /**
     * 检查token是否过期
     * @param token JWT token
     * @return 是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            // 使用Java 21的特性，更简洁的时间处理
            var claims = getClaimsFromToken(token);
            return claims.getExpiration().before(Date.from(Instant.now()));
        } catch (Exception e) {
            log.debug("Token validation error: {}", e.getMessage());
            return true;
        }
    }
}