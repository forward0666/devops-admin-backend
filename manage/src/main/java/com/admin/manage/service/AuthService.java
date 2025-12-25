package com.admin.manage.service;

import com.admin.manage.client.SecurityServiceClient;
import com.admin.manage.dto.LoginRequest;
import com.admin.manage.dto.LoginResponse;
import com.admin.manage.model.User;
import com.admin.manage.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证服务类
 * 负责用户登录认证、令牌验证和安全管理
 * 
 * @author Admin
 * @version 1.0
 * @since 2024
 */
@Slf4j
@Service
public class AuthService {

    @Autowired
    private SecurityServiceClient securityServiceClient; // 安全服务客户端，用于调用安全微服务

    @Autowired
    private UserRepository userRepository; // 用户数据访问层

    @Autowired
    private IPWhitelistService ipWhitelistService; // IP白名单服务

    @Autowired
    private CacheService cacheService; // 缓存服务，用于Redis缓存操作

    /**
     * 用户登录认证
     * 完整的登录流程，包括IP验证、用户认证、验证码验证和令牌生成
     * 
     * @param loginRequest 登录请求对象，包含用户名、密码、验证码等信息
     * @param clientIP 客户端IP地址，用于IP白名单验证
     * @return LoginResponse 登录响应对象，包含JWT令牌和用户信息
     * @throws RuntimeException 当认证失败时抛出异常
     */
    public LoginResponse login(LoginRequest loginRequest, String clientIP) {
        log.info("处理用户登录请求，用户名: {}，来源IP: {}", loginRequest.getUsername(), clientIP);

        // 步骤0: 验证客户端IP白名单/黑名单
        if (!validateClientIP(clientIP)) {
            log.warn("登录被拒绝: IP {} 不被允许或已被阻止", clientIP);
            throw new RuntimeException("访问被拒绝: 您的IP地址未获得授权");
        }

        // 步骤1: 验证用户凭据（用户名和密码）
        User user = authenticateUser(loginRequest.getUsername(), loginRequest.getPassword());

        if (user == null) {
            log.warn("认证失败: 用户名或密码无效 {}", loginRequest.getUsername());
            throw new RuntimeException("无效的用户名或密码");
        }

        log.info("用户凭据验证成功: {}", loginRequest.getUsername());

        // 步骤2: 验证验证码（通过安全服务）
        boolean isValidCode = validateVerificationCode(
                loginRequest.getVerificationCodeKey(),
                loginRequest.getVerificationCode()
        );

        if (!isValidCode) {
            log.warn("认证失败: 验证码无效 {}", loginRequest.getUsername());
            throw new RuntimeException("无效的验证码");
        }

        log.info("验证码验证成功: {}", loginRequest.getUsername());

        // 步骤3: 生成JWT承载令牌
        String token = generateBearerToken(user);

        log.info("承载令牌生成成功: {}", loginRequest.getUsername());

        // 步骤4: 返回包含令牌和用户详情的响应
        return new LoginResponse(token, user);
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

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("codeId", codeKey); // 验证码会话ID
            requestBody.put("code", code);      // 用户输入的验证码

            // 调用安全服务验证验证码
            Map<String, Object> response = securityServiceClient.verifyCode(requestBody);

            if (response != null) {
                Boolean success = (Boolean) response.get("success");
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
    private String generateBearerToken(User user) {
        try {
            log.info("通过安全服务为用户生成JWT令牌: {}", user.getUsername());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("subject", user.getUsername()); // JWT主题，通常为用户名

            // 构建JWT声明（claims），包含用户信息
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());          // 用户ID
            claims.put("role", user.getRole());          // 用户角色
            claims.put("email", user.getEmail());        // 用户邮箱
            claims.put("username", user.getUsername());  // 用户名
            requestBody.put("claims", claims);

            // 调用安全服务生成令牌
            Map<String, Object> response = securityServiceClient.generateToken(requestBody);

            if (response != null && response.get("token") != null) {
                String token = (String) response.get("token");
                log.info("通过安全服务成功生成JWT令牌");

                // 如果Redis可用，将令牌验证结果缓存
                if (cacheService.isRedisAvailable()) {
                    cacheService.cacheTokenValidation(token, true);
                    log.info("令牌已存储在Redis缓存中供验证使用");
                }

                return token;
            }

            throw new RuntimeException("从安全服务生成令牌失败");

        } catch (Exception e) {
            log.warn("通过安全服务生成JWT令牌时出错: {}", e.getMessage());
            throw new RuntimeException("令牌生成服务不可用");
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
    private User authenticateUser(String username, String password) {
        log.info("对用户进行数据库认证: {}", username);

        Optional<User> authenticatedUser = userRepository.authenticate(username, password);
        return authenticatedUser.orElse(null);
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
     * @return LoginResponse 登录响应对象
     */
    public LoginResponse login(LoginRequest loginRequest) {
        return login(loginRequest, null);
    }

    /**
     * 验证JWT令牌
     * 通过安全服务验证JWT令牌的有效性，支持缓存优化
     * 
     * @param token JWT令牌（可包含Bearer前缀）
     * @return boolean 令牌是否有效
     */
    public boolean validateToken(String token) {
        try {
            log.info("通过安全服务验证JWT令牌");

            // 清理令牌（移除Bearer前缀）
            String cleanToken = token.replace("Bearer ", "");

            // 如果Redis可用，首先检查缓存
            if (cacheService.isRedisAvailable()) {
                Boolean cachedResult = cacheService.getCachedTokenValidation(cleanToken);
                if (cachedResult != null) {
                    log.debug("从缓存中获取令牌验证结果: {}", cachedResult);
                    return cachedResult;
                }
            }

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("token", cleanToken);

            // 调用安全服务验证令牌
            Map<String, Object> response = securityServiceClient.validateToken(requestBody);

            boolean isValid = false;
            if (response != null) {
                Boolean valid = (Boolean) response.get("valid");
                if (Boolean.TRUE.equals(valid)) {
                    log.info("通过安全服务JWT令牌验证成功");
                    isValid = true;
                } else {
                    log.warn("JWT令牌验证失败: {}", response.get("message"));
                }
            } else {
                log.warn("安全服务返回空响应（令牌验证）");
            }

            // 缓存验证结果
            if (cacheService.isRedisAvailable()) {
                cacheService.cacheTokenValidation(cleanToken, isValid);
            }

            return isValid;

        } catch (Exception e) {
            log.error("令牌验证过程中出错: {}", e.getMessage());
            return false; // 生产环境直接失败，开发环境使用fallback机制
        }
    }
}
