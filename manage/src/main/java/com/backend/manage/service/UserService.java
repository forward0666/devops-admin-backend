package com.backend.manage.service;

import com.backend.manage.entity.UserEntity;
import com.backend.manage.mapper.UserMapper;
import com.backend.manage.service.DepartmentCacheService;
import com.backend.manage.service.UserCacheService;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户服务类 - 处理所有用户相关的业务逻辑
 * 包括用户CRUD操作、密码管理、验证、缓存管理等
 * 
 * @author System
 * @version 1.0
 */
@Slf4j
@Service
@Transactional
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCacheService userCacheService;

    @Autowired
    private DepartmentCacheService departmentCacheService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== 查询方法 ====================

    /**
     * 获取所有用户列表
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     *
     * @return 所有用户的列表
     */
    @Transactional(readOnly = true)
    public List<UserEntity> getAllUsers() {
        log.info("正在获取所有用户列表");

        var cachedUsers = getCachedList(userCacheService::getCachedUsersList);
        if (cachedUsers != null) {
            log.debug("从缓存中获取用户列表成功");
            return cachedUsers;
        }

        var users = userMapper.findAll();
        cacheList(userCacheService::cacheUsersList, users);
        return users;
    }

    /**
     * 根据用户ID获取用户信息
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     *
     * @param id 用户ID
     * @return 用户对象，如果不存在则返回null
     */
    @Transactional(readOnly = true)
    public UserEntity getUserById(Long id) {
        log.info("正在根据ID获取用户: " + id);

        var cachedUser = getCachedUser(id);
        if (cachedUser != null) {
            log.debug("从缓存中获取用户成功: " + id);
            return cachedUser;
        }

        var user = Optional.ofNullable(userMapper.findById(id)).orElse(null);

        if (user != null && cacheService.isRedisAvailable()) {
            userCacheService.cacheUser(user);
        }

        return user;
    }

    /**
     * 根据用户ID获取用户信息（包括非活跃用户）
     *
     * @param id 用户ID
     * @return 用户对象，如果不存在则返回null
     */
    @Transactional(readOnly = true)
    public UserEntity getUserByIdIncludeInactive(Long id) {
        log.info("正在获取用户信息（包括非活跃用户）: " + id);
        return Optional.ofNullable(userMapper.findByIdIncludeInactive(id)).orElse(null);
    }

    /**
     * 获取部门下的用户列表（带缓存）
     *
     * @param departmentId 部门ID
     * @return 部门用户列表
     */
    public List<UserEntity> getUsersByDepartment(Long departmentId) {
        log.info("Retrieving users for department: " + departmentId);

        if (cacheService.isRedisAvailable()) {
            List<UserEntity> cachedUsers = departmentCacheService.getDepartmentUsersCache(departmentId);
            if (cachedUsers != null) {
                log.debug("Retrieved department users from cache: " + departmentId);
                return cachedUsers;
            }
        }

        List<UserEntity> users = userMapper.findByDepartmentId(departmentId);
        if (cacheService.isRedisAvailable()) {
            departmentCacheService.departmentUsersCache(departmentId, users);
        }

        return users;
    }

    /**
     * 搜索用户（按用户名或邮箱）
     *
     * @param query 搜索关键词
     * @return 匹配的用户列表
     */
    @Transactional(readOnly = true)
    public List<UserEntity> searchUsers(String query) {
        log.info("Searching users with query: " + query);
        return userMapper.searchByUsernameOrEmail(query);
    }

    // ==================== 创建方法 ====================

    /**
     * 创建新用户（完整版本）
     * 检查用户名、邮箱、手机号、Telegram用户名、员工ID的唯一性
     * 对密码进行BCrypt加密处理，创建成功后清除相关缓存
     *
     * @param username 用户名（必填，唯一）
     * @param password 密码（必填，BCrypt加密）
     * @param email 邮箱地址（可选，唯一）
     * @param phone 手机号码（可选，唯一）
     * @param tgUsername Telegram用户名（可选，唯一）
     * @param fullName 用户全名（可选）
     * @param avatarUrl 头像URL（可选）
     * @param position 职位（可选）
     * @param employeeId 员工ID（可选，唯一）
     * @param role 用户角色（必填）
     * @param departmentId 部门ID（可选）
     * @param createdBy 创建者用户ID（可选）
     * @return 创建成功的用户对象
     * @throws RuntimeException 当唯一字段已存在时抛出异常
     */
    public UserEntity createUser(String username, String password, String email, String phone, String tgUsername,
                          String fullName, String avatarUrl, String position, String employeeId,
                          String role, Long departmentId, Long createdBy) {
        log.info("正在创建新用户: " + username);

        // 唯一性校验
        validateCreateUniqueness(username, email, phone, tgUsername, employeeId);

        // 构建并保存用户
        UserEntity user = buildNewUser(username, password, email, phone, tgUsername,
                fullName, avatarUrl, position, employeeId, role, departmentId, createdBy);
        userMapper.insert(user);

        // 清除相关缓存
        clearCreateUserCaches(user.getId(), departmentId);

        log.info("用户创建成功: " + username);
        return user;
    }

    /**
     * 创建新用户（简化版本，用于向后兼容）
     *
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱地址
     * @param fullName 用户全名
     * @param role 用户角色
     * @param departmentId 部门ID
     * @return 创建成功的用户对象
     */
    public UserEntity createUser(String username, String password, String email, String fullName, String role, Long departmentId) {
        return createUser(username, password, email, null, null, fullName, null, null, null, role, departmentId, null);
    }

    // ==================== 更新方法 ====================

    /**
     * Update an existing user
     *
     * @param id the user ID
     * @param email the new email
     * @param phone the new phone
     * @param tgUsername the new Telegram username
     * @param fullName the new full name
     * @param avatarUrl the new avatar URL
     * @param position the new position
     * @param employeeId the new employee ID
     * @param role the new role
     * @param departmentId the new department ID
     * @param active the active status
     * @param updatedBy the ID of user updating this user
     * @return the updated User object or null if not found
     */
    public UserEntity updateUser(Long id, String email, String phone, String tgUsername, String fullName,
                          String avatarUrl, String position, String employeeId, String role,
                          Long departmentId, Boolean active, Long updatedBy) {
        log.info("Updating user: " + id);

        Optional<UserEntity> optionalUser = Optional.ofNullable(userMapper.findByIdIncludeInactive(id));
        if (!optionalUser.isPresent()) {
            log.warn("User not found for update: " + id);
            return null;
        }

        UserEntity user = optionalUser.get();
        Long oldDepartmentId = user.getDepartmentId();

        // 唯一性校验（排除自身）
        validateUpdateUniqueness(user, email, phone, tgUsername, employeeId);

        // 更新字段
        applyUserUpdates(user, email, phone, tgUsername, fullName, avatarUrl,
                position, employeeId, role, departmentId, active, updatedBy);
        userMapper.update(user);

        // 清除缓存（处理部门变更）
        clearUpdateUserCaches(id, oldDepartmentId, departmentId);

        log.info("User updated successfully: " + id);
        return user;
    }

    /**
     * Update an existing user (simplified version for backward compatibility)
     */
    public UserEntity updateUser(Long id, String email, String fullName, String role, Long departmentId, Boolean active) {
        return updateUser(id, email, null, null, fullName, null, null, null, role, departmentId, active, null);
    }

    // ==================== 删除方法 ====================

    /**
     * Delete a user
     *
     * @param id the user ID
     * @return true if deleted, false if not found
     */
    public boolean deleteUser(Long id) {
        log.info("Deleting user: " + id);

        Optional<UserEntity> optionalUser = Optional.ofNullable(userMapper.findByIdIncludeInactive(id));
        if (!optionalUser.isPresent()) {
            log.warn("User not found for deletion: " + id);
            return false;
        }

        Long departmentId = optionalUser.get().getDepartmentId();
        userMapper.deleteById(id);

        clearDeleteUserCaches(departmentId);

        log.info("User deleted successfully: " + id);
        return true;
    }

    // ==================== 密码管理 ====================

    /**
     * Change user password
     *
     * @param id the user ID
     * @param oldPassword the old password
     * @param newPassword the new password
     * @return true if changed, false if old password is incorrect or user not found
     */
    public boolean changePassword(Long id, String oldPassword, String newPassword) {
        log.info("Changing password for user: " + id);

        validatePasswordPolicy(newPassword);

        Optional<UserEntity> optionalUser = Optional.ofNullable(userMapper.findById(id));
        if (!optionalUser.isPresent()) {
            log.warn("User not found for password change: " + id);
            return false;
        }

        UserEntity user = optionalUser.get();
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Old password verification failed for user: " + id);
            return false;
        }

        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
        clearSingleUserCache(id);

        log.info("Password changed successfully for user: " + id);
        return true;
    }

    /**
     * Reset user password (admin function)
     *
     * @param id the user ID
     * @param newPassword the new password
     * @return true if reset, false if user not found
     */
    public boolean resetPassword(Long id, String newPassword) {
        log.info("Resetting password for user: " + id);

        validatePasswordPolicy(newPassword);

        Optional<UserEntity> optionalUser = Optional.ofNullable(userMapper.findById(id));
        if (!optionalUser.isPresent()) {
            log.warn("User not found for password reset: " + id);
            return false;
        }

        userMapper.updatePassword(optionalUser.get().getId(), passwordEncoder.encode(newPassword));
        clearSingleUserCache(id);

        log.info("Password reset successfully for user: " + id);
        return true;
    }

    // ==================== 登录与验证 ====================

    /**
     * Update user login information
     *
     * @param id the user ID
     * @param ipAddress the login IP address
     * @return true if updated, false if user not found
     */
    public boolean updateLoginInfo(Long id, String ipAddress) {
        log.info("Updating login info for user: " + id);

        return executeOnActiveUser(id, user -> {
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ipAddress);
            user.setLoginCount(user.getLoginCount() + 1);
            user.setUpdatedAt(LocalDateTime.now());
        }, "login info update");
    }

    /**
     * Verify user email
     *
     * @param id the user ID
     * @return true if verified, false if user not found
     */
    public boolean verifyEmail(Long id) {
        log.info("Verifying email for user: " + id);

        return executeOnActiveUser(id, user -> {
            user.setEmailVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
        }, "email verification");
    }

    /**
     * Verify user phone
     *
     * @param id the user ID
     * @return true if verified, false if user not found
     */
    public boolean verifyPhone(Long id) {
        log.info("Verifying phone for user: " + id);

        return executeOnActiveUser(id, user -> {
            user.setPhoneVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
        }, "phone verification");
    }

    // ==================== 私有辅助方法：缓存 ====================

    /**
     * 从缓存获取用户列表
     */
    private List<UserEntity> getCachedList(java.util.function.Supplier<List<UserEntity>> cacheLoader) {
        return cacheService.isRedisAvailable() ? cacheLoader.get() : null;
    }

    /**
     * 将用户列表写入缓存
     */
    private void cacheList(java.util.function.Consumer<List<UserEntity>> cacheWriter, List<UserEntity> users) {
        if (cacheService.isRedisAvailable()) {
            cacheWriter.accept(users);
        }
    }

    /**
     * 从缓存获取单个用户
     */
    private UserEntity getCachedUser(Long id) {
        return cacheService.isRedisAvailable() ? userCacheService.getCachedUser(id) : null;
    }

    /**
     * 清除单个用户的缓存
     */
    private void clearSingleUserCache(Long id) {
        if (cacheService.isRedisAvailable()) {
            userCacheService.clearUserCache(id);
        }
    }

    /**
     * 清除用户列表缓存及部门缓存
     */
    private void clearUserListAndDepartmentCache(Long departmentId) {
        if (!cacheService.isRedisAvailable()) {
            return;
        }
        userCacheService.clearAllUserListCache();
        if (departmentId != null) {
            departmentCacheService.clearDepartmentUsersCache(departmentId);
        }
    }

    /**
     * 创建用户后清除缓存
     */
    private void clearCreateUserCaches(Long userId, Long departmentId) {
        if (!cacheService.isRedisAvailable()) {
            return;
        }
        userCacheService.clearUserCache(userId);
        clearUserListAndDepartmentCache(departmentId);
    }

    /**
     * 更新用户后清除缓存（处理部门变更）
     */
    private void clearUpdateUserCaches(Long id, Long oldDeptId, Long newDeptId) {
        if (!cacheService.isRedisAvailable()) {
            return;
        }
        userCacheService.clearUserCache(id);
        userCacheService.clearAllUserListCache();

        // 清除登录 token，强制下线
        UserEntity user = userMapper.findByIdIncludeInactive(id);
        if (user != null && user.getUsername() != null) {
            userCacheService.clearUserTokens(user.getUsername());
        }

        if (newDeptId != null && !newDeptId.equals(oldDeptId)) {
            if (oldDeptId != null) {
                departmentCacheService.clearDepartmentUsersCache(oldDeptId);
            }
            departmentCacheService.clearDepartmentUsersCache(newDeptId);
        }
    }

    /**
     * 删除用户后清除缓存
     */
    private void clearDeleteUserCaches(Long departmentId) {
        if (!cacheService.isRedisAvailable()) {
            return;
        }
        userCacheService.clearAllUserListCache();
        if (departmentId != null) {
            departmentCacheService.clearDepartmentUsersCache(departmentId);
        }
    }

    // ==================== 私有辅助方法：唯一性校验 ====================

    /**
     * 创建用户时的唯一性校验
     */
    private void validateCreateUniqueness(String username, String email, String phone,
                                          String tgUsername, String employeeId) {
        if (userMapper.existsByUsername(username) > 0) {
            throw new RuntimeException("用户名已存在: " + username);
        }
        if (isNotBlank(email) && userMapper.existsByEmail(email) > 0) {
            throw new RuntimeException("邮箱地址已存在: " + email);
        }
        if (isNotBlank(phone) && userMapper.existsByPhone(phone) > 0) {
            throw new RuntimeException("手机号码已存在: " + phone);
        }
        if (isNotBlank(tgUsername) && userMapper.existsByTgUsername(tgUsername) > 0) {
            throw new RuntimeException("Telegram用户名已存在: " + tgUsername);
        }
        if (isNotBlank(employeeId) && userMapper.existsByEmployeeId(employeeId) > 0) {
            throw new RuntimeException("员工ID已存在: " + employeeId);
        }
    }

    /**
     * 更新用户时的唯一性校验（排除自身已有值）
     */
    private void validateUpdateUniqueness(UserEntity user, String email, String phone,
                                          String tgUsername, String employeeId) {
        if (email != null && !email.equals(user.getEmail()) && userMapper.existsByEmail(email) > 0) {
            throw new RuntimeException("Email already exists: " + email);
        }
        if (phone != null && !phone.equals(user.getPhone()) && userMapper.existsByPhone(phone) > 0) {
            throw new RuntimeException("Phone already exists: " + phone);
        }
        if (tgUsername != null && !tgUsername.equals(user.getTgUsername()) && userMapper.existsByTgUsername(tgUsername) > 0) {
            throw new RuntimeException("Telegram username already exists: " + tgUsername);
        }
        if (employeeId != null && !employeeId.equals(user.getEmployeeId()) && userMapper.existsByEmployeeId(employeeId) > 0) {
            throw new RuntimeException("Employee ID already exists: " + employeeId);
        }
    }

    // ==================== 私有辅助方法：实体构建与更新 ====================

    /**
     * 构建新用户实体
     */
    private UserEntity buildNewUser(String username, String password, String email, String phone,
                                    String tgUsername, String fullName, String avatarUrl, String position,
                                    String employeeId, String role, Long departmentId, Long createdBy) {
        var now = LocalDateTime.now();
        var user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhone(phone);
        user.setTgUsername(tgUsername);
        user.setFullName(fullName);
        user.setAvatarUrl(avatarUrl);
        user.setPosition(position);
        user.setEmployeeId(employeeId);
        user.setRole(role);
        user.setDepartmentId(departmentId);
        user.setCreatedBy(createdBy);
        user.setUpdatedBy(createdBy);
        user.setActive(true);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setPasswordChangedAt(now);
        return user;
    }

    /**
     * 将非null字段应用到用户实体
     */
    private void applyUserUpdates(UserEntity user, String email, String phone, String tgUsername,
                                  String fullName, String avatarUrl, String position,
                                  String employeeId, String role, Long departmentId,
                                  Boolean active, Long updatedBy) {
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (tgUsername != null) user.setTgUsername(tgUsername);
        if (fullName != null) user.setFullName(fullName);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
        if (position != null) user.setPosition(position);
        if (employeeId != null) user.setEmployeeId(employeeId);
        if (role != null) user.setRole(role);
        if (departmentId != null) user.setDepartmentId(departmentId);
        if (active != null) user.setActive(active);
        if (updatedBy != null) user.setUpdatedBy(updatedBy);
        user.setUpdatedAt(LocalDateTime.now());
    }

    // ==================== 私有辅助方法：通用操作 ====================

    /**
     * 在活跃用户上执行操作：查找 -> 修改 -> 保存 -> 清缓存
     *
     * @param id 用户ID
     * @param modifier 修改用户实体的函数
     * @param operationName 操作名称（用于日志）
     * @return true if successful, false if user not found
     */
    private boolean executeOnActiveUser(Long id, java.util.function.Consumer<UserEntity> modifier, String operationName) {
        Optional<UserEntity> optionalUser = Optional.ofNullable(userMapper.findById(id));
        if (!optionalUser.isPresent()) {
            log.warn("User not found for {}: " + id, operationName);
            return false;
        }

        UserEntity user = optionalUser.get();
        modifier.accept(user);
        userMapper.update(user);
        clearSingleUserCache(id);

        log.info("{} successfully for user: {}", operationName, id);
        return true;
    }

    /**
     * 校验密码策略
     */
    private void validatePasswordPolicy(String password) {
        String passwordError = settingService.validatePassword(password);
        if (passwordError != null) {
            throw new RuntimeException(passwordError);
        }
    }

    /**
     * 判断字符串非空且非空白
     */
    private boolean isNotBlank(String value) {
        return value != null && !value.isEmpty();
    }
}
