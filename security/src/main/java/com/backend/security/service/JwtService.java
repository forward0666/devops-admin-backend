package com.backend.security.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.rsa.private-key}")
    private String privateKeyPem;

    @Value("${jwt.rsa.public-key}")
    private String publicKeyPem;

    @Value("${jwt.expiration:86400}")
    private int jwtExpirationInSec;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            if (privateKeyPem != null && !privateKeyPem.isBlank()) {
                var privateKeyBytes = Base64.getDecoder().decode(
                    privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                                .replace("-----END PRIVATE KEY-----", "")
                                .replaceAll("\\s", "")
                );
                privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            }

            if (publicKeyPem != null && !publicKeyPem.isBlank()) {
                var publicKeyBytes = Base64.getDecoder().decode(
                    publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
                               .replace("-----END PUBLIC KEY-----", "")
                               .replaceAll("\\s", "")
                );
                publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            } else {
                // 自动生成密钥对
                KeyPair pair = Keys.keyPairFor(SignatureAlgorithm.RS256);
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();
                log.warn("RSA keys not configured, generated temporary keys");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RSA keys", e);
        }
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        var now = Instant.now();
        var expiryDate = Date.from(now.plusSeconds(jwtExpirationInSec));

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(expiryDate)
                .signWith(privateKey, SignatureAlgorithm.RS256);

        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }

        return builder.compact();
    }

    public String generateToken(String subject) {
        return generateToken(subject, null);
    }

    public String getSubjectFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException |
                 UnsupportedJwtException | IllegalArgumentException ex) {
            log.error("JWT validation failed: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            var claims = getClaimsFromToken(token);
            return claims.getExpiration().before(Date.from(Instant.now()));
        } catch (Exception e) {
            log.debug("Token validation error: {}", e.getMessage());
            return true;
        }
    }

    /** 获取公钥 PEM（给 Gateway 用） */
    public String getPublicKeyPem() {
        if (publicKeyPem != null && !publicKeyPem.isBlank()) {
            return publicKeyPem;
        }
        String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}