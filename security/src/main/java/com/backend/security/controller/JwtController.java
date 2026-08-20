package com.backend.security.controller;

import com.backend.security.dto.JwtGenerateRequestDto;
import com.backend.security.dto.JwtValidateRequestDto;
import com.backend.security.dto.JwtResponseDto;
import com.backend.security.dto.VerificationCodeRequestDto;
import com.backend.security.dto.VerificationCodeResponseDto;
import com.backend.security.service.JwtService;
import com.backend.security.service.VerificationCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.Instant;
import java.util.*;

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
    public ResponseEntity<JwtResponseDto> generateToken(@Valid @RequestBody JwtGenerateRequestDto request) {
        // 使用try-catch块捕获异常，并使用Java 21的switch表达式
        return tryGenerateToken(request);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ResponseEntity<JwtResponseDto> tryGenerateToken(JwtGenerateRequestDto request) {
        try {
            String token = jwtService.generateToken(request.subject(), request.claims());
            return ResponseEntity.ok(JwtResponseDto.success("Token generated successfully", token));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(JwtResponseDto.error("Failed to generate token: " + e.getMessage()));
        }
    }

    /**
     * 验证JWT token并返回所有信息
     */
    @PostMapping("/validate")
    public ResponseEntity<JwtResponseDto> validateToken(@Valid @RequestBody JwtValidateRequestDto request) {
        // 使用try-catch块捕获异常，并使用Java 21的switch表达式
        return tryValidateToken(request.token());
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ResponseEntity<JwtResponseDto> tryValidateToken(String token) {
        try {
            if (jwtService.validateToken(token)) {
                Map<String, Object> claims = jwtService.getClaims(token);
                String subject = (String) claims.get("sub");
                Map<String, Object> data = new HashMap<>();
                data.put("subject", subject != null ? subject : "");
                data.put("issuedAt", claims.get("iat"));
                data.put("expiration", claims.get("exp"));
                Object exp = claims.get("exp");
                data.put("expired", exp != null && ((Number) exp).longValue() * 1000 < System.currentTimeMillis());
                data.put("claims", claims);
                
                return ResponseEntity.ok(JwtResponseDto.success("Token is valid", token, data));
            } else {
                return ResponseEntity.badRequest()
                        .body(JwtResponseDto.error("Token is invalid or expired"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(JwtResponseDto.error("Failed to validate token: " + e.getMessage()));
        }
    }

    /**
     * 获取公钥（给 Gateway 验证 JWT 用）
     */
    @GetMapping("/security/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", jwtService.getPublicKeyPem()));
    }

    /**
     * 生成数字图形验证码
     */
    @PostMapping("/verificationCode")
    public ResponseEntity<VerificationCodeResponseDto> generateVerificationCode() {
        // 使用try-catch块捕获异常
        return tryGenerateVerificationCode();
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ResponseEntity<VerificationCodeResponseDto> tryGenerateVerificationCode() {
        try {
            var result = verificationCodeService.generateVerificationCode();
            return ResponseEntity.ok(new VerificationCodeResponseDto(
                    true, 
                    "Verification code generated successfully", 
                    result.get("codeId"), 
                    result.get("imageBase64")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new VerificationCodeResponseDto(false, "Failed to generate verification code: " + e.getMessage(), null, null));
        }
    }

    /**
     * 验证验证码
     * 
     * 功能说明：
     * 验证用户输入的验证码是否正确
     * 
     * 请求参数：
     * @param request VerificationCodeRequest对象，包含：
     *   - codeId: 验证码的唯一标识符（从生成接口获取）
     *   - code: 用户输入的验证码文本
     * 
     * 返回结果：
     * @return ResponseEntity<Map<String, Object>> 包含：
     *   - success: 验证是否成功
     *   - message: 验证结果消息（中文）
     * 
     * 验证流程：
     * 1. 参数验证：检查codeId和code是否为空
     * 2. 检查验证码是否存在和是否过期
     * 3. 比较用户输入与存储的验证码（不区分大小写）
     * 4. 验证成功时删除验证码，失败时保留以便重试
     * 
     * 错误处理：
     * - 参数为空：返回400错误
     * - 验证码不存在或过期：返回验证失败
     * - 服务器错误：返回500错误
     * 
     * 安全特性：
     * - 支持多次重试（防止误操作）
     * - 验证成功后立即删除验证码
     * - 详细的错误日志记录
     * 
     * 使用场景：
     * - 用户登录时的验证码验证
     * - 表单提交前的防机器人验证
     * - 敏感操作的安全验证
     */
    @PostMapping("/verifyCode")
    public ResponseEntity<Map<String, Object>> verifyCode(@RequestBody VerificationCodeRequestDto request) {
        // 使用Java 21的record模式和switch表达式优化代码
        try {
            // 使用模式匹配检查请求有效性
            if (!request.isValid()) {
                var errorMessage = request.codeId() == null || request.codeId().trim().isEmpty() 
                    ? "验证码ID不能为空" : "验证码不能为空";
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", errorMessage));
            }
            
            // 使用if-else处理验证结果
            var isValid = verificationCodeService.validateVerificationCode(request.codeId(), request.code());
            Map<String, Object> response;
            if (isValid) {
                response = Map.of("success", true, "message", "验证码验证成功");
            } else {
                response = Map.of("success", false, "message", "验证码错误或已过期");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "验证码验证失败: " + e.getMessage()));
        }
    }
}
