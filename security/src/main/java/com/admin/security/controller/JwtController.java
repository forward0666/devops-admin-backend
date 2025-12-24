package com.admin.security.controller;

import com.admin.security.dto.JwtGenerateRequest;
import com.admin.security.dto.JwtValidateRequest;
import com.admin.security.dto.JwtResponse;
import com.admin.security.dto.VerificationCodeResponse;
import com.admin.security.service.JwtService;
import com.admin.security.service.VerificationCodeService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class JwtController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private VerificationCodeService verificationCodeService;

    /**
     * 生成JWT token
     */
    @PostMapping("/generate")
    public ResponseEntity<JwtResponse> generateToken(@Valid @RequestBody JwtGenerateRequest request) {
        try {
            String token = jwtService.generateToken(request.getSubject(), request.getClaims());
            return ResponseEntity.ok(new JwtResponse(true, "Token generated successfully", token, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new JwtResponse(false, "Failed to generate token: " + e.getMessage(), null, null));
        }
    }

    /**
     * 验证JWT token并返回所有信息
     */
    @PostMapping("/validate")
    public ResponseEntity<JwtResponse> validateToken(@Valid @RequestBody JwtValidateRequest request) {
        try {
            boolean isValid = jwtService.validateToken(request.getToken());
            if (isValid) {
                Claims claims = jwtService.getClaimsFromToken(request.getToken());
                Map<String, Object> data = new HashMap<>();
                data.put("subject", claims.getSubject());
                data.put("issuedAt", claims.getIssuedAt());
                data.put("expiration", claims.getExpiration());
                data.put("expired", claims.getExpiration().before(new Date()));
                data.put("claims", claims);
                
                return ResponseEntity.ok(new JwtResponse(true, "Token is valid", null, data));
            } else {
                return ResponseEntity.badRequest()
                        .body(new JwtResponse(false, "Token is invalid or expired", null, null));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new JwtResponse(false, "Failed to validate token: " + e.getMessage(), null, null));
        }
    }

    /**
     * 生成数字图形验证码
     */
    @PostMapping("/verificationCode")
    public ResponseEntity<VerificationCodeResponse> generateVerificationCode() {
        try {
            Map<String, String> result = verificationCodeService.generateVerificationCode();
            return ResponseEntity.ok(new VerificationCodeResponse(
                    true, 
                    "Verification code generated successfully", 
                    result.get("codeId"), 
                    result.get("imageBase64")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new VerificationCodeResponse(false, "Failed to generate verification code: " + e.getMessage(), null, null));
        }
    }
}
