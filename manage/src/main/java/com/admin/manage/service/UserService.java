package com.admin.manage.service;

import com.admin.manage.model.User;
import com.admin.manage.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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
public class UserService {

    // 使用Java 21的构造器注入，避免@Autowired
    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CacheService cacheService) {
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 获取所有用户列表
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * 
     * @return 所有用户的列表
     */
    public List<User> getAllUsers() {
        log.info("正在获取所有用户列表");
        
        // 使用Java 21的模式匹配，优化缓存检查逻辑
        var cachedUsers = cacheService.isRedisAvailable() ? cacheService.getCachedUsersList() : null;
        
        if (cachedUsers != null) {
            log.debug("从缓存中获取用户列表成功");
            return cachedUsers;
        }
        
        // 缓存不存在，从数据库查询所有用户
        var users = userRepository.findAll();
        
        // 将查询结果缓存到Redis中
        if (cacheService.isRedisAvailable()) {
            cacheService.cacheUsersList(users);
        }
        
        return users;
    }

    /**
     * 根据用户ID获取用户信息
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * 
     * @param id 用户ID
     * @return 用户对象，如果不存在则返回null
     */
    public User getUserById(Long id) {
        log.info("正在根据ID获取用户: " + id);
        
        // 使用Java 21的模式匹配，优化缓存检查逻辑
        var cachedUser = cacheService.isRedisAvailable() ? cacheService.getCachedUser(id) : null;
        
        if (cachedUser != null) {
            log.debug("从缓存中获取用户成功: " + id);
            return cachedUser;
        }
        
        // 缓存不存在，从数据库查询用户信息
        var user = userRepository.findById(id).orElse(null);
        
        // 将查询结果缓存到Redis中
        if (user != null && cacheService.isRedisAvailable()) {
            cacheService.cacheUser(user);
        }
        
        return user;
    }

    /**
     * 根据用户ID获取用户信息（包括非活跃用户）
     * 主要用于操作日志记录等需要访问所有用户的场景
     * 
     * @param id 用户ID
     * @return 用户对象，如果不存在则返回null
     */
    public User getUserByIdIncludeInactive(Long id) {
        log.info("正在获取用户信息（包括非活跃用户）: " + id);
        
        // 使用Java 21的模式匹配简化Optional处理
        return userRepository.findByIdIncludeInactive(id).orElse(null);
    }

    /**
     * 创建新用户
     * 检查用户名、邮箱、手机号、Telegram用户名、员工ID的唯一性
     * 对密码进行BCrypt加密处理
     * 创建成功后清除相关缓存
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
     * @throws RuntimeException 当用户名、邮箱、手机号、Telegram用户名或员工ID已存在时抛出异常
     */
    public User createUser(String username, String password, String email, String phone, String tgUsername, 
                          String fullName, String avatarUrl, String position, String employeeId, 
                          String role, Long departmentId, Long createdBy) {
        log.info("正在创建新用户: " + username);

        // 使用Java 21的Optional模式匹配，检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("用户名已存在: " + username);
        }

        // 检查邮箱是否已存在
        if (email != null && !email.isEmpty() && userRepository.existsByEmail(email)) {
            throw new RuntimeException("邮箱地址已存在: " + email);
        }

        // 检查手机号是否已存在
        if (phone != null && !phone.isEmpty() && userRepository.existsByPhone(phone)) {
            throw new RuntimeException("手机号码已存在: " + phone);
        }

        // 检查Telegram用户名是否已存在
        if (tgUsername != null && !tgUsername.isEmpty() && userRepository.existsByTgUsername(tgUsername)) {
            throw new RuntimeException("Telegram用户名已存在: " + tgUsername);
        }

        // 检查员工ID是否已存在
        if (employeeId != null && !employeeId.isEmpty() && userRepository.existsByEmployeeId(employeeId)) {
            throw new RuntimeException("员工ID已存在: " + employeeId);
        }

        // 创建新用户对象并设置属性
        // 使用Java 21的文本块和record特性，优化对象创建
        var now = LocalDateTime.now();
        var user = new User();
        user.setUsername(username);                                  // 设置用户名
        user.setPassword(passwordEncoder.encode(password));          // 使用BCrypt加密密码
        user.setEmail(email);                                        // 设置邮箱地址
        user.setPhone(phone);                                        // 设置手机号码
        user.setTgUsername(tgUsername);                              // 设置Telegram用户名
        user.setFullName(fullName);                                  // 设置用户全名
        user.setAvatarUrl(avatarUrl);                                // 设置头像URL
        user.setPosition(position);                                  // 设置职位
        user.setEmployeeId(employeeId);                              // 设置员工ID
        user.setRole(role);                                          // 设置用户角色
        user.setDepartmentId(departmentId);                          // 设置部门ID
        user.setCreatedBy(createdBy);                                // 设置创建者用户ID
        user.setUpdatedBy(createdBy);                                // 设置更新者用户ID
        user.setActive(true);                                        // 设置用户为活跃状态
        user.setEmailVerified(false);                                // 邮箱未验证状态
        user.setPhoneVerified(false);                                // 手机号未验证状态
        user.setLoginCount(0);                                       // 初始化登录次数为0
        user.setCreatedAt(now);                                      // 设置创建时间
        user.setUpdatedAt(now);                                      // 设置更新时间
        user.setPasswordChangedAt(now);                               // 设置密码修改时间

        // 保存用户到数据库
        User savedUser = userRepository.save(user);
        
        // 创建用户后清除相关缓存，确保数据一致性
        if (cacheService.isRedisAvailable()) {
            cacheService.clearAllUserCache();
        }
        
        log.info("用户创建成功: " + username);
        return savedUser;
    }

    /**
     * 创建新用户（简化版本，用于向后兼容）
     * 只提供基本必填字段，其他字段使用默认值
     * 
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱地址
     * @param fullName 用户全名
     * @param role 用户角色
     * @param departmentId 部门ID
     * @return 创建成功的用户对象
     */
    public User createUser(String username, String password, String email, String fullName, String role, Long departmentId) {
        return createUser(username, password, email, null, null, fullName, null, null, null, role, departmentId, null);
    }

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
    public User updateUser(Long id, String email, String phone, String tgUsername, String fullName, 
                          String avatarUrl, String position, String employeeId, String role, 
                          Long departmentId, Boolean active, Long updatedBy) {
        log.info("Updating user: " + id);

        Optional<User> optionalUser = userRepository.findByIdIncludeInactive(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            
            // Check for duplicate email
            if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new RuntimeException("Email already exists: " + email);
            }
            
            // Check for duplicate phone
            if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
                throw new RuntimeException("Phone already exists: " + phone);
            }
            
            // Check for duplicate Telegram username
            if (tgUsername != null && !tgUsername.equals(user.getTgUsername()) && userRepository.existsByTgUsername(tgUsername)) {
                throw new RuntimeException("Telegram username already exists: " + tgUsername);
            }
            
            // Check for duplicate employee ID
            if (employeeId != null && !employeeId.equals(user.getEmployeeId()) && userRepository.existsByEmployeeId(employeeId)) {
                throw new RuntimeException("Employee ID already exists: " + employeeId);
            }
            
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

            User updatedUser = userRepository.save(user);
            
            // Clear cache after updating user
            if (cacheService.isRedisAvailable()) {
                cacheService.clearUserCache(id);
            }
            
            log.info("User updated successfully: " + id);
            return updatedUser;
        }
        
        log.warn("User not found for update: " + id);
        return null;
    }

    /**
     * Update an existing user (simplified version for backward compatibility)
     */
    public User updateUser(Long id, String email, String fullName, String role, Long departmentId, Boolean active) {
        return updateUser(id, email, null, null, fullName, null, null, null, role, departmentId, active, null);
    }

    /**
     * Delete a user
     * 
     * @param id the user ID
     * @return true if deleted, false if not found
     */
    public boolean deleteUser(Long id) {
        log.info("Deleting user: " + id);

        Optional<User> optionalUser = userRepository.findByIdIncludeInactive(id);
        if (optionalUser.isPresent()) {
            userRepository.deleteById(id);
            
            // Clear cache after deleting user
            if (cacheService.isRedisAvailable()) {
                cacheService.clearUserCache(id);
            }
            
            log.info("User deleted successfully: " + id);
            return true;
        }
        
        log.warn("User not found for deletion: " + id);
        return false;
    }

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

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            
            // Verify old password
            if (passwordEncoder.matches(oldPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);
                log.info("Password changed successfully for user: " + id);
                return true;
            } else {
                log.warn("Old password verification failed for user: " + id);
                return false;
            }
        }
        
        log.warn("User not found for password change: " + id);
        return false;
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

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChangedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Password reset successfully for user: " + id);
            return true;
        }
        
        log.warn("User not found for password reset: " + id);
        return false;
    }

    /**
     * Update user login information
     * 
     * @param id the user ID
     * @param ipAddress the login IP address
     * @return true if updated, false if user not found
     */
    public boolean updateLoginInfo(Long id, String ipAddress) {
        log.info("Updating login info for user: " + id);

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ipAddress);
            user.setLoginCount(user.getLoginCount() + 1);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Login info updated successfully for user: " + id);
            return true;
        }
        
        log.warn("User not found for login info update: " + id);
        return false;
    }

    /**
     * Verify user email
     * 
     * @param id the user ID
     * @return true if verified, false if user not found
     */
    public boolean verifyEmail(Long id) {
        log.info("Verifying email for user: " + id);

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setEmailVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Email verified successfully for user: " + id);
            return true;
        }
        
        log.warn("User not found for email verification: " + id);
        return false;
    }

    /**
     * Verify user phone
     * 
     * @param id the user ID
     * @return true if verified, false if user not found
     */
    public boolean verifyPhone(Long id) {
        log.info("Verifying phone for user: " + id);

        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setPhoneVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Phone verified successfully for user: " + id);
            return true;
        }
        
        log.warn("User not found for phone verification: " + id);
        return false;
    }

    /**
     * Get users by department
     * 
     * @param departmentId the department ID
     * @return List of users in the department
     */
    public List<User> getUsersByDepartment(Long departmentId) {
        log.info("Retrieving users for department: " + departmentId);
        
        // Try to get from cache first
        if (cacheService.isRedisAvailable()) {
            List<User> cachedUsers = cacheService.getCachedDepartmentUsers(departmentId);
            if (cachedUsers != null) {
                log.debug("Retrieved department users from cache: " + departmentId);
                return cachedUsers;
            }
        }
        
        // Get from database and cache the result
        List<User> users = userRepository.findByDepartmentId(departmentId);
        if (cacheService.isRedisAvailable()) {
            cacheService.cacheDepartmentUsers(departmentId, users);
        }
        
        return users;
    }

    /**
     * Search users by username or email
     * 
     * @param query the search query
     * @return List of matching users
     */
    public List<User> searchUsers(String query) {
        log.info("Searching users with query: " + query);
        return userRepository.searchByUsernameOrEmail(query);
    }
}