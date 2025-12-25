package com.admin.manage.service;

import com.admin.manage.model.Department;
import com.admin.manage.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务
 * 管理用户、部门、系统设置和Token的Redis缓存
 */
@Service
@Slf4j
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String USER_CACHE_PREFIX = "user:";
    private static final String USERS_LIST_CACHE_KEY = "users:list";
    private static final String DEPARTMENT_CACHE_PREFIX = "department:";
    private static final String DEPARTMENTS_LIST_CACHE_KEY = "departments:list";
    private static final String USER_DEPARTMENT_CACHE_PREFIX = "user:department:";
    private static final String TOKEN_VALIDATION_CACHE_PREFIX = "token:validation:";
    private static final String SYSTEM_SETTINGS_CACHE_PREFIX = "settings:";

    // 缓存过期时间（分钟）
    private static final long CACHE_EXPIRE_MINUTES = 30;
    private static final long TOKEN_CACHE_EXPIRE_MINUTES = 1440; // 1天
    private static final long SETTINGS_CACHE_EXPIRE_MINUTES = 60; // 1小时

    // ================= 用户缓存 =================

    public void cacheUser(User user) {
        if (user != null && user.getId() != null) {
            String key = USER_CACHE_PREFIX + user.getId();
            redisTemplate.opsForValue().set(key, user, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached user: {}", user.getId());
        }
    }

    public User getCachedUser(Long userId) {
        if (userId == null) return null;
        String key = USER_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        return (cached instanceof User) ? (User) cached : null;
    }

    public void cacheUsersList(List<User> users) {
        redisTemplate.opsForValue().set(USERS_LIST_CACHE_KEY, users, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.debug("Cached users list with {} users", users.size());
    }

    @SuppressWarnings("unchecked")
    public List<User> getCachedUsersList() {
        Object cached = redisTemplate.opsForValue().get(USERS_LIST_CACHE_KEY);
        return (cached instanceof List) ? (List<User>) cached : null;
    }

    public void clearUserCache(Long userId) {
        if (userId != null) {
            redisTemplate.delete(USER_CACHE_PREFIX + userId);
            log.debug("Cleared cache for user: {}", userId);
        }
        redisTemplate.delete(USERS_LIST_CACHE_KEY);
        clearByPrefix(USER_DEPARTMENT_CACHE_PREFIX);
    }

    public void clearAllUserCache() {
        clearByPrefix(USER_CACHE_PREFIX);
        redisTemplate.delete(USERS_LIST_CACHE_KEY);
        clearByPrefix(USER_DEPARTMENT_CACHE_PREFIX);
        log.debug("Cleared all user caches");
    }

    // ================= 部门缓存 =================

    public void cacheDepartment(Department department) {
        if (department != null && department.getId() != null) {
            String key = DEPARTMENT_CACHE_PREFIX + department.getId();
            redisTemplate.opsForValue().set(key, department, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached department: {}", department.getId());
        }
    }

    public Department getCachedDepartment(Long departmentId) {
        if (departmentId == null) return null;
        String key = DEPARTMENT_CACHE_PREFIX + departmentId;
        Object cached = redisTemplate.opsForValue().get(key);
        return (cached instanceof Department) ? (Department) cached : null;
    }

    public void cacheDepartmentsList(List<Department> departments) {
        redisTemplate.opsForValue().set(DEPARTMENTS_LIST_CACHE_KEY, departments, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        log.debug("Cached departments list with {} departments", departments.size());
    }

    @SuppressWarnings("unchecked")
    public List<Department> getCachedDepartmentsList() {
        Object cached = redisTemplate.opsForValue().get(DEPARTMENTS_LIST_CACHE_KEY);
        return (cached instanceof List) ? (List<Department>) cached : null;
    }

    public void cacheDepartmentUsers(Long departmentId, List<User> users) {
        if (departmentId != null) {
            String key = USER_DEPARTMENT_CACHE_PREFIX + departmentId;
            redisTemplate.opsForValue().set(key, users, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached users for department: {}", departmentId);
        }
    }

    @SuppressWarnings("unchecked")
    public List<User> getCachedDepartmentUsers(Long departmentId) {
        if (departmentId == null) return null;
        String key = USER_DEPARTMENT_CACHE_PREFIX + departmentId;
        Object cached = redisTemplate.opsForValue().get(key);
        return (cached instanceof List) ? (List<User>) cached : null;
    }

    public void clearDepartmentCache(Long departmentId) {
        if (departmentId != null) {
            redisTemplate.delete(DEPARTMENT_CACHE_PREFIX + departmentId);
            redisTemplate.delete(USER_DEPARTMENT_CACHE_PREFIX + departmentId);
            log.debug("Cleared cache for department: {}", departmentId);
        }
        redisTemplate.delete(DEPARTMENTS_LIST_CACHE_KEY);
    }

    public void clearAllDepartmentCache() {
        clearByPrefix(DEPARTMENT_CACHE_PREFIX);
        redisTemplate.delete(DEPARTMENTS_LIST_CACHE_KEY);
        clearByPrefix(USER_DEPARTMENT_CACHE_PREFIX);
        log.debug("Cleared all department caches");
    }

    // ================= Token 缓存 =================

    public void cacheTokenValidation(String token, boolean isValid) {
        if (token == null || token.isEmpty()) return;
        try {
            String username = extractUsernameFromToken(token);
            String key = TOKEN_VALIDATION_CACHE_PREFIX + username + ":" + token.hashCode();
            redisTemplate.opsForValue().set(key, isValid, TOKEN_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);

            if (isValid) {
                String tokenKey = "token:" + username + ":" + token.hashCode();
                redisTemplate.opsForValue().set(tokenKey, token, TOKEN_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            redisTemplate.opsForValue().set(TOKEN_VALIDATION_CACHE_PREFIX + token.hashCode(),
                    isValid, TOKEN_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
    }

    private String extractUsernameFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return "unknown";
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            if (claims.containsKey("username")) return (String) claims.get("username");
            if (claims.containsKey("sub")) return (String) claims.get("sub");
            if (claims.containsKey("subject")) return (String) claims.get("subject");
        } catch (Exception e) {
            log.error("Error extracting username from token: {}", e.getMessage());
        }
        return "unknown";
    }

    public Boolean getCachedTokenValidation(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            String username = extractUsernameFromToken(token);
            String key = TOKEN_VALIDATION_CACHE_PREFIX + username + ":" + token.hashCode();
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof Boolean) return (Boolean) cached;

            String fallbackKey = TOKEN_VALIDATION_CACHE_PREFIX + token.hashCode();
            Object fallbackCached = redisTemplate.opsForValue().get(fallbackKey);
            return (fallbackCached instanceof Boolean) ? (Boolean) fallbackCached : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void clearTokenValidationCache(String token) {
        if (token == null || token.isEmpty()) return;
        try {
            String username = extractUsernameFromToken(token);
            redisTemplate.delete(TOKEN_VALIDATION_CACHE_PREFIX + username + ":" + token.hashCode());
            redisTemplate.delete("token:" + username + ":" + token.hashCode());
            redisTemplate.delete(TOKEN_VALIDATION_CACHE_PREFIX + token.hashCode());
        } catch (Exception e) {
            redisTemplate.delete(TOKEN_VALIDATION_CACHE_PREFIX + token.hashCode());
        }
    }

    public void clearAllTokenValidationCache() {
        clearByPrefix(TOKEN_VALIDATION_CACHE_PREFIX);
        clearByPrefix("token:");
        log.debug("Cleared all token validation caches");
    }

    // ================= 系统设置缓存 =================

    public void cacheSystemSettings(String settingsType, Object settings) {
        if (settingsType != null && settings != null) {
            String key = SYSTEM_SETTINGS_CACHE_PREFIX + settingsType;
            redisTemplate.opsForValue().set(key, settings, SETTINGS_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("Cached system settings: {}", settingsType);
        }
    }

    public Object getCachedSystemSettings(String settingsType) {
        if (settingsType == null) return null;
        return redisTemplate.opsForValue().get(SYSTEM_SETTINGS_CACHE_PREFIX + settingsType);
    }

    public void clearSystemSettingsCache(String settingsType) {
        if (settingsType != null) {
            redisTemplate.delete(SYSTEM_SETTINGS_CACHE_PREFIX + settingsType);
        }
    }

    public void clearAllSystemSettingsCache() {
        clearByPrefix(SYSTEM_SETTINGS_CACHE_PREFIX);
        log.debug("Cleared all system settings caches");
    }

    // ================= 全部缓存 =================

    public void clearAllCache() {
        clearAllUserCache();
        clearAllDepartmentCache();
        clearAllTokenValidationCache();
        clearAllSystemSettingsCache();
        log.info("Cleared ALL caches (user, department, token, system settings)");
    }

    // ================= 工具方法 =================

    private void clearByPrefix(String prefix) {
        redisTemplate.delete(redisTemplate.keys(prefix + "*"));
    }

    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("test");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
