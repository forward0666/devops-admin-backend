package com.backend.login.controller.login;

import com.backend.login.service.audits.OperationLogService;
import com.backend.login.dto.ApiResponseDto;
import com.backend.login.dto.login.LoginRequestDto;
import com.backend.login.dto.login.LoginResponseDto;
import com.backend.login.service.login.AuthService;
import com.backend.login.service.system.SecurityService;
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
 * 使用 Java 21 风格
 *
 * 功能说明：
 * - 提供完整的用户认证生命周期管理
 * - 支持用户名密码验证和验证码验证
 * - 集成IP白名单验证，增强安全性
 * - 使用JWT令牌进行身份验证
 * - 统一的异常处理和响应格式
 *
 * @author Backend Team
 * @version 2.0.0
 */
@RestController
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SecurityService ipWhitelistService;

    @Autowired
    private OperationLogService operationLogService;

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
     * @return ApiResponseDto包含登录响应，成功时返回用户数据和令牌，失败时返回错误信息
     */
    @PostMapping("/authLogin")
    public ApiResponseDto<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequest, HttpServletRequest request) {
        try {
            // 验证请求参数 - Java 21: 使用 switch 表达式
            if (loginRequest.username() == null || loginRequest.username().isEmpty()) {
                return ApiResponseDto.error("Username is required");
            }

            if (loginRequest.password() == null || loginRequest.password().isEmpty()) {
                return ApiResponseDto.error("Password is required");
            }

            if (loginRequest.verificationCode() == null || loginRequest.verificationCode().isEmpty()) {
                return ApiResponseDto.error("Verification code is required");
            }

            // 提取客户端真实IP地址
            var clientIP = ipWhitelistService.getRealClientIP(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );

            // 处理登录逻辑，包含IP验证
            var loginResponse = authService.login(loginRequest, clientIP);

            operationLogService.logUserOperation(
                    loginResponse.user().getId(),
                    loginRequest.username(),
                    "LOGIN", "用户登录", "AUTH", null,
                    request.getMethod(), request.getRequestURI(),
                    clientIP, request.getHeader("User-Agent"),
                    null, null, true, null, null, "AUTH"
            );

            return ApiResponseDto.success("Login successful", loginResponse);
        } catch (Exception e) {
            var clientIP = ipWhitelistService.getRealClientIP(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
            );
            operationLogService.logUserOperation(
                    null, loginRequest.username(),
                    "LOGIN", "用户登录", "AUTH", null,
                    request.getMethod(), request.getRequestURI(),
                    clientIP, request.getHeader("User-Agent"),
                    null, null, false, e.getMessage(), null, "AUTH"
            );
            return ApiResponseDto.error("Login failed: " + e.getMessage());
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
     * @return ApiResponseDto指示操作结果，成功时返回成功信息，失败时返回错误信息
     */
    @PostMapping("/authOut")
    public ApiResponseDto<Void> logout() {
        try {
            // 在实际实现中，这里会使令牌失效
            return ApiResponseDto.success("Logout successful", null);
        } catch (Exception e) {
            return ApiResponseDto.error("Logout failed: " + e.getMessage());
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
     * @return ApiResponseDto包含令牌验证结果，成功时返回验证状态和消息，失败时返回错误信息
     */
    @PostMapping("/validate-token")
    public ApiResponseDto<Map<String, Object>> validateToken(HttpServletRequest request) {
        try {
            var authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                var result = new HashMap<String, Object>();
                result.put("valid", false);
                return ApiResponseDto.success("Invalid token format", result);
            }

            var token = authHeader.substring(7);
            boolean isValid = authService.validateToken(token);

            // Java 21: 使用 Map.of() 创建不可变映射
            var result = isValid
                ? Map.<String, Object>of("valid", true, "message", "Token is valid")
                : Map.<String, Object>of("valid", false, "message", "Token is invalid or expired");

            return ApiResponseDto.success("Token validation completed", result);
        } catch (Exception e) {
            // Java 21: 使用 Map.of() 创建不可变映射
            var result = Map.<String, Object>of(
                "valid", false,
                "message", "Token validation failed: " + e.getMessage()
            );
            return ApiResponseDto.success("Token validation completed", result);
        }
    }
}
