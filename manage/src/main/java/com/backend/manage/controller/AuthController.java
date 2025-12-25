package com.backend.manage.controller;

import com.backend.manage.dto.ApiResponse;
import com.backend.manage.dto.LoginRequest;
import com.backend.manage.dto.LoginResponse;
import com.backend.manage.service.AuthService;
import com.backend.manage.service.IPWhitelistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理用户认证相关操作，包括登录、登出和令牌验证
 * 
 * 功能说明：
 * - 提供完整的用户认证生命周期管理
 * - 支持用户名密码验证和验证码验证
 * - 集成IP白名单验证，增强安全性
 * - 使用JWT令牌进行身份验证
 * - 统一的异常处理和响应格式
 */
@RestController
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private IPWhitelistService ipWhitelistService;

    /**
     * 用户登录接口
     * 
     * 功能说明：
     * - 验证用户名、密码、验证码和IP白名单
     * - 使用POST方法，请求体包含登录信息和验证码
     * - 从HTTP请求头中提取真实客户端IP地址
     * - 调用认证服务处理登录逻辑
     * - 返回包含令牌和用户详细信息的响应
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @param loginRequest 登录请求，包含用户名、密码和验证码
     * @param request HTTP请求对象，用于提取客户端IP地址
     * @return ApiResponse包含登录响应，成功时返回用户数据和令牌，失败时返回错误信息
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            // 验证请求参数
            if (loginRequest.getUsername() == null || loginRequest.getUsername().isEmpty()) {
                return ApiResponse.error("Username is required");
            }
            
            if (loginRequest.getPassword() == null || loginRequest.getPassword().isEmpty()) {
                return ApiResponse.error("Password is required");
            }
            
            if (loginRequest.getVerificationCode() == null || loginRequest.getVerificationCode().isEmpty()) {
                return ApiResponse.error("Verification code is required");
            }
            
            // 提取客户端真实IP地址
            String clientIP = ipWhitelistService.getRealClientIP(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );
            
            // 处理登录逻辑，包含IP验证
            LoginResponse loginResponse = authService.login(loginRequest, clientIP);
            
            return ApiResponse.success("Login successful", loginResponse);
        } catch (Exception e) {
            return ApiResponse.error("Login failed: " + e.getMessage());
        }
    }
    
    /**
     * 用户登出接口
     * 
     * 功能说明：
     * - 处理用户登出操作
     * - 使用POST方法，无请求参数
     * - 在实际实现中会使令牌失效
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @return ApiResponse指示操作结果，成功时返回成功信息，失败时返回错误信息
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        try {
            // 在实际实现中，这里会使令牌失效
            return ApiResponse.success("Logout successful", null);
        } catch (Exception e) {
            return ApiResponse.error("Logout failed: " + e.getMessage());
        }
    }

    /**
     * 令牌验证接口
     * 
     * 功能说明：
     * - 验证JWT令牌的有效性
     * - 使用POST方法，从HTTP请求头中提取Authorization令牌
     * - 支持Bearer令牌格式处理
     * - 返回令牌验证结果和详细信息
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @param request HTTP请求对象，用于提取Authorization请求头
     * @return ApiResponse包含令牌验证结果，成功时返回验证状态和消息，失败时返回错误信息
     */
    @PostMapping("/validate-token")
    public ApiResponse<Map<String, Object>> validateToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Map<String, Object> result = new HashMap<>();
                result.put("valid", false);
                return ApiResponse.success("Invalid token format", result);
            }

            String token = authHeader.substring(7);
            boolean isValid = authService.validateToken(token);
            
            Map<String, Object> result = new HashMap<>();
            result.put("valid", isValid);
            
            if (isValid) {
                result.put("message", "Token is valid");
            } else {
                result.put("message", "Token is invalid or expired");
            }
            
            return ApiResponse.success("Token validation completed", result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("valid", false);
            result.put("message", "Token validation failed: " + e.getMessage());
            return ApiResponse.success("Token validation completed", result);
        }
    }
}
