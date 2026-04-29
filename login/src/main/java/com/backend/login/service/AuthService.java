package com.backend.login.service;

import com.backend.login.service.CacheService;
import com.backend.login.service.SecurityService;
import com.backend.login.service.SettingService;
import com.backend.login.client.SecurityServiceClient;
import com.backend.login.dto.JwtGenerateRequestDto;
import com.backend.login.dto.LoginRequestDto;
import com.backend.login.dto.LoginResponseDto;
import com.backend.login.entity.UserEntity;
import com.backend.login.mapper.UserMapper;
import com.backend.login.service.CacheService;
import com.backend.login.service.SecurityService;
import com.backend.login.service.SettingService;
import com.backend.login.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务类
 * 负责用户登录认证、令牌验证和安全管理
 * 使用 Java 21 风格
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Slf4j
@Service
public class AuthService {

    @Autowired
    private SecurityServiceClient securityServiceClient; // 安全服务客户端，用于调用安全微服务

    @Autowired
    private UserMapper userMapper; // 用户数据访问层

    @Autowired
    private SecurityService ipWhitelistService; // IP白名单服务

    @Autowired
    private SettingService settingService;

    @Autowired
    private CacheService cacheService; // 缓存服务，用于Redis缓存操作

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil; // JWT工具类

    /**
     * 用户登录认证
     * 完整的登录流程，包括IP验证、用户认证、验证码验证和令牌生成
     *
     * @param loginRequest 登录请求对象，包含用户名、密码、验证码等信息
     * @param clientIP 客户端IP地址，用于IP白名单验证
     * @return LoginResponseDto 登录响应对象，包含JWT令牌和用户信息
     * @throws RuntimeException 当认证失败时抛出异常
     */
    public LoginResponseDto login(LoginRequestDto loginRequest, String clientIP) {
        log.info("处理用户登录请求，用户名: {}，来源IP: {}", loginRequest.username(), clientIP);

        // 步骤0: 验证客户端IP白名单/黑名单
        if (!validateClientIP(clientIP)) {
            log.warn("登录被拒绝: IP {} 不被允许或已被阻止", clientIP);
            throw new RuntimeException("访问被拒绝: 您的IP地址未获得授权");
        }

        // 步骤0.1: 检查登录锁定
        if (settingService.isLoginLocked(loginRequest.username())) {
            int lockoutMin = settingService.getLoginLockoutMinutes();
            throw new RuntimeException("Account is locked due to too many failed attempts. Try again in " + lockoutMin + " minutes.");
        }

        // 步骤0.2: 如果验证码未启用，跳过验证码检查
        boolean captchaEnabled = settingService.isLoginCaptchaEnabled();

        // 步骤1: 验证用户凭据（用户名和密码）
        var user = authenticateUser(loginRequest.username(), loginRequest.password());

        if (user == null) {
            log.warn("认证失败: 用户名或密码无效 {}", loginRequest.username());
            boolean locked = settingService.recordFailedLogin(loginRequest.username());
            if (locked) {
                throw new RuntimeException("Account is locked due to too many failed attempts. Try again in " + settingService.getLoginLockoutMinutes() + " minutes.");
            }
            int remaining = settingService.getRemainingAttempts(loginRequest.username());
            throw new RuntimeException("Invalid username or password. " + remaining + " attempts remaining.");
        }

        log.info("用户凭据验证成功: {}", loginRequest.username());

        // 清除失败登录计数
        settingService.clearFailedLogin(loginRequest.username());

        // 步骤2: 验证验证码（通过安全服务）- 仅在启用时检查
        if (captchaEnabled) {
            boolean isValidCode = validateVerificationCode(
                    loginRequest.verificationCodeKey(),
                    loginRequest.verificationCode()
            );

            if (!isValidCode) {
                log.warn("认证失败: 验证码无效 {}", loginRequest.username());
                throw new RuntimeException("Invalid verification code");
            }
            log.info("验证码验证成功: {}", loginRequest.username());
        } else {
            log.info("验证码已禁用, 跳过验证: {}", loginRequest.username());
        }

        // 步骤3: 生成JWT承载令牌
        var token = generateBearerToken(user);

        log.info("承载令牌生成成功: {}", loginRequest.username());

        // 步骤4: 返回包含令牌和用户详情的响应
        return new LoginResponseDto(token, user);
    }

    /**
     * 验证验证码
     * 通过Feign客户端调用安全服务验证验证码的有效性
     *
     * @param codeKey 验证码密钥，用于标识验证码会话
     * @param code 用户输入的验证码
     * @return boolean 验证码是否有效
     */
    private boolean validateVerificationCode(String codeKey, String code) {
        try {
            log.info("通过安全服务验证验证码，使用Feign客户端");

            // Java 21: 使用 Map.of() 创建不可变映射
            var requestBody = Map.<String, String>of(
                    "codeId", codeKey,
                    "code", code
            );

            // 调用安全服务验证验证码
            var response = securityServiceClient.verifyCode(requestBody);

            if (response != null) {
                var success = (Boolean) response.get("success");
                if (Boolean.TRUE.equals(success)) {
                    log.info("验证码验证成功");
                    return true;
                } else {
                    log.warn("验证码验证失败: {}", response.get("message"));
                    return false;
                }
            }

            log.warn("安全服务返回空响应");
            return false;
        } catch (Exception e) {
            log.warn("使用Feign验证验证码时出错: {}", e.getMessage());
            return false; // 生产环境直接失败，开发环境使用fallback机制
        }
    }

    /**
     * 生成JWT承载令牌
     * 通过安全服务生成包含用户声明的JWT令牌
     *
     * @param user 用户对象，包含用户信息和权限
     * @return String 生成的JWT令牌
     * @throws RuntimeException 当令牌生成失败时抛出异常
     */
    private String generateBearerToken(UserEntity user) {
        try {
            log.info("通过安全服务为用户生成JWT令牌: {}", user.getUsername());

            // 构建JWT声明（claims），包含用户信息
            // 使用HashMap而不是Map.of，因为可能存在null字段
            var claims = new HashMap<String, Object>();
            claims.put("userId", user.getId());
            claims.put("role", user.getRole() != null ? user.getRole() : "");
            claims.put("email", user.getEmail() != null ? user.getEmail() : "");
            claims.put("username", user.getUsername());

            var request = JwtGenerateRequestDto.of(user.getUsername(), claims);

            log.info("JWT生成请求体: subject={}, claims={}", user.getUsername(), claims);

            // 调用安全服务生成令牌
            var response = securityServiceClient.generateToken(request);

            log.info("JWT生成响应: success={}, message={}", response.success(), response.message());

            if (response != null && response.success() && response.token() != null) {
                var token = response.token();
                log.info("通过安全服务成功生成JWT令牌");

                // 如果Redis可用，将令牌验证结果缓存
                if (cacheService.isRedisAvailable()) {
                    cacheService.cacheTokenValidation(user.getUsername(), token, true, settingService.getTokenExpireSeconds());
                    log.info("令牌已存储在Redis缓存中供验证使用");
                }

                return token;
            }

            throw new RuntimeException("从安全服务生成令牌失败: " + (response != null ? response.message() : "No response"));

        } catch (Exception e) {
            log.error("通过安全服务生成JWT令牌时出错", e);
            throw new RuntimeException("令牌生成服务不可用: " + e.getMessage());
        }
    }

    /**
     * 用户认证
     * 验证用户名和密码是否匹配数据库中的记录
     *
     * @param username 用户名
     * @param password 密码
     * @return User 认证成功的用户对象，如果认证失败返回null
     */
    private UserEntity authenticateUser(String username, String password) {
        log.info("对用户进行数据库认证: {}", username);

        var authenticatedUser = userMapper.authenticate(username);
        if (authenticatedUser == null) {
            return null;
        }
        if (!passwordEncoder.matches(password, authenticatedUser.getPassword())) {
            log.warn("密码不匹配: {}", username);
            return null;
        }
        return authenticatedUser;
    }

    /**
     * 验证客户端IP
     * 检查客户端IP是否在黑名单中或不在白名单中
     *
     * @param clientIP 客户端IP地址
     * @return boolean IP是否通过验证
     */
    private boolean validateClientIP(String clientIP) {
        try {
            log.info("验证客户端IP: {}", clientIP);

            // 检查IP是否在黑名单中
            if (ipWhitelistService.isIPBlocked(clientIP)) {
                log.warn("客户端IP {} 在黑名单中", clientIP);
                return false;
            }

            // 检查IP是否在白名单中
            if (!ipWhitelistService.isIPAllowed(clientIP)) {
                log.warn("客户端IP {} 不在白名单中", clientIP);
                return false;
            }

            log.info("客户端IP {} 通过验证", clientIP);
            return true;

        } catch (Exception e) {
            log.error("验证客户端IP {} 时出错: {}", clientIP, e.getMessage());
            return true; // fail-open策略，防止误锁
        }
    }

    /**
     * 重载的登录方法（向后兼容）
     * 不进行IP验证的登录方法
     *
     * @param loginRequest 登录请求对象
     * @return LoginResponseDto 登录响应对象
     */
    public LoginResponseDto login(LoginRequestDto loginRequest) {
        return login(loginRequest, null);
    }

    /**
     * 验证JWT令牌
     * 通过安全服务验证JWT令牌的有效性，支持缓存优化
     *
}
