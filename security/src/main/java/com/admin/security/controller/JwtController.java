package com.admin.security.controller;

import com.admin.security.dto.JwtGenerateRequest;
import com.admin.security.dto.JwtValidateRequest;
import com.admin.security.dto.JwtResponse;
import com.admin.security.dto.VerificationCodeRequest;
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
    public ResponseEntity<Map<String, Object>> verifyCode(@RequestBody VerificationCodeRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (request.getCodeId() == null || request.getCodeId().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "验证码ID不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "验证码不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean isValid = verificationCodeService.validateVerificationCode(
                request.getCodeId(), 
                request.getCode()
            );
            
            if (isValid) {
                response.put("success", true);
                response.put("message", "验证码验证成功");
            } else {
                response.put("success", false);
                response.put("message", "验证码错误或已过期");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "验证码验证失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
