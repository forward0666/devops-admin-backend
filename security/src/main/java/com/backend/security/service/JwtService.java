package com.backend.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
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

    @Value("${jwt.rsa.private-key:}")
    private String privateKeyPem;

    @Value("${jwt.rsa.public-key:}")
    private String publicKeyPem;

    @Value("${jwt.expiration:86400}")
    private int jwtExpirationInSec;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            if (privateKeyPem != null && !privateKeyPem.isBlank()) {
                String cleaned = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("[REDACTED PRIVATE KEY]", "")
                    .replaceAll("\\\\n", "")
                    .replaceAll("\\s", "");
                var keyBytes = Base64.getDecoder().decode(cleaned);
                privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            }

            if (publicKeyPem != null && !publicKeyPem.isBlank()) {
                String cleaned = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\\\n", "")
                    .replaceAll("\\s", "");
                var keyBytes = Base64.getDecoder().decode(cleaned);
                publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            }

            if (privateKey == null || publicKey == null) {
                KeyPair pair = Keys.keyPairFor(SignatureAlgorithm.RS256);
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();
                log.warn("RSA keys not configured, generated temporary keys");
            }

            log.info("✅ JWT RSA keys initialized");
        } catch (Exception e) {
            log.error("Failed to initialize RSA keys, generating temporary keys", e);
            try {
                KeyPair pair = Keys.keyPairFor(SignatureAlgorithm.RS256);
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();
            } catch (Exception e2) {
                throw new RuntimeException("Failed to initialize RSA keys", e2);
            }
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
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> getClaims(String token) {
        try {
            return Jwts.parser().verifyWith(publicKey).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            throw new RuntimeException("Invalid token", e);
        }
    }

    public String getPublicKeyPem() {
        if (publicKeyPem != null && !publicKeyPem.isBlank()) {
            return publicKeyPem.replace("\\n", "\n");
        }
        String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}