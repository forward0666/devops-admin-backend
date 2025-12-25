package com.admin.security.controller;

import com.admin.security.dto.JwtGenerateRequest;
import com.admin.security.dto.JwtValidateRequest;
import com.admin.security.dto.JwtResponse;
import com.admin.security.dto.VerificationCodeResponse;
import com.admin.security.service.JwtService;
import com.admin.security.service.VerificationCodeService;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class JwtController {
    
    // 使用构造器注入替代@Autowired，这是Java 21的推荐实践
    private final JwtService jwtService;
    private final VerificationCodeService verificationCodeService;
    
    public JwtController(JwtService jwtService, VerificationCodeService verificationCodeService) {
        this.jwtService = jwtService;
        this.verificationCodeService = verificationCodeService;
    }

    /**
     * 生成JWT token
     */
    @PostMapping("/generate")
    public ResponseEntity<JwtResponse> generateToken(@Valid @RequestBody JwtGenerateRequest request) {
        // 使用try-catch块捕获异常，并使用Java 21的switch表达式
        return tryGenerateToken(request);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ResponseEntity<JwtResponse> tryGenerateToken(JwtGenerateRequest request) {
        try {
            String token = jwtService.generateToken(request.subject(), request.claims());
            return ResponseEntity.ok(JwtResponse.success("Token generated successfully", token));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(JwtResponse.error("Failed to generate token: " + e.getMessage()));
        }
    }

    /**
     * 验证JWT token并返回所有信息
     */
    @PostMapping("/validate")
    public ResponseEntity<JwtResponse> validateToken(@Valid @RequestBody JwtValidateRequest request) {
        // 使用try-catch块捕获异常，并使用Java 21的switch表达式
        return tryValidateToken(request.token());
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ResponseEntity<JwtResponse> tryValidateToken(String token) {
        try {
            if (jwtService.validateToken(token)) {
                Claims claims = jwtService.getClaimsFromToken(token);
                // 使用Map.of创建不可变映射，这是Java 9+的特性
                Map<String, Object> data = new HashMap<>();
                data.put("subject", claims.getSubject());
                data.put("issuedAt", claims.getIssuedAt());
                data.put("expiration", claims.getExpiration());
                data.put("expired", claims.getExpiration().before(Date.from(Instant.now())));
                data.put("claims", claims);
                
                return ResponseEntity.ok(JwtResponse.success("Token is valid", token, data));
            } else {
                return ResponseEntity.badRequest()
                        .body(JwtResponse.error("Token is invalid or expired"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(JwtResponse.error("Failed to validate token: " + e.getMessage()));
        }
    }

    /**
     * 生成数字图形验证码
     */
    @PostMapping("/verificationCode")
    public ResponseEntity<VerificationCodeResponse> generateVerificationCode() {
        // 使用try-catch块捕获异常
        return tryGenerateVerificationCode();
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ResponseEntity<VerificationCodeResponse> tryGenerateVerificationCode() {
        try {
            var result = verificationCodeService.generateVerificationCode();
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
