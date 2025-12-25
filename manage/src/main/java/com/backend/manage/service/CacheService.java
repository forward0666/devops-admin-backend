package com.backend.manage.service;

import com.backend.manage.model.Department;
import com.backend.manage.model.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /* ================= key 前缀 ================= */
    private static final String USER_PREFIX = "user:";
    private static final String USERS_LIST = "users:list";

    private static final String DEPT_PREFIX = "department:";
    private static final String DEPT_LIST = "departments:list";
    private static final String DEPT_USERS = "department:users:";

    private static final String USER_DEPT_PREFIX = "user:department:";
    private static final String TOKEN_PREFIX = "token:validation:";
    private static final String SETTINGS_PREFIX = "settings:";

    private static final long CACHE_MIN = 30;      // 通用缓存分钟
    private static final long TOKEN_MIN = 1440;    // Token缓存分钟
    private static final long SETTINGS_MIN = 60;   // 系统设置缓存分钟

    /* ================= Redis 健康检查 ================= */
    private volatile boolean redisAvailable = true;
    private volatile long lastCheck = 0;

    private boolean redisOk() {
        long now = System.currentTimeMillis();
        if (redisAvailable && now - lastCheck < 30_000) return true;

        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisAvailable = true;
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis不可用: {}", e.getMessage());
        }
        lastCheck = now;
        return redisAvailable;
    }

    public boolean isRedisAvailable() {
        return redisOk();
    }

    /* ================= 用户缓存 ================= */
    public void cacheUser(User user) {
        if (!redisOk() || user == null || user.getId() == null) return;
        redisTemplate.opsForValue().set(USER_PREFIX + user.getId(), user, CACHE_MIN, TimeUnit.MINUTES);
    }

    public User getCachedUser(Long userId) {
        if (!redisOk() || userId == null) return null;
        Object v = redisTemplate.opsForValue().get(USER_PREFIX + userId);
        return (v instanceof User u) ? u : null;
    }

    public void cacheUsersList(List<User> users) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(USERS_LIST, users, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<User> getCachedUsersList() {
        if (!redisOk()) return null;
        Object v = redisTemplate.opsForValue().get(USERS_LIST);
        return (v instanceof List<?>) ? (List<User>) v : null;
    }

    public void clearUserCache(Long userId) {
        if (!redisOk() || userId == null) return;
        redisTemplate.delete(USER_PREFIX + userId);
    }

    public void clearAllUserCache() {
        clearByPrefix(USER_PREFIX);
        redisTemplate.delete(USERS_LIST);
    }

    /* ================= 部门缓存 ================= */
    public void cacheDepartment(Department dept) {
        if (!redisOk() || dept == null || dept.getId() == null) return;
        redisTemplate.opsForValue().set(DEPT_PREFIX + dept.getId(), dept, CACHE_MIN, TimeUnit.MINUTES);
    }

    public Department getCachedDepartment(Long id) {
        if (!redisOk() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(DEPT_PREFIX + id);
        return (v instanceof Department d) ? d : null;
    }

    public void cacheDepartmentsList(List<Department> list) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(DEPT_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<Department> getCachedDepartmentsList() {
        if (!redisOk()) return null;
        Object v = redisTemplate.opsForValue().get(DEPT_LIST);
        return (v instanceof List<?>) ? (List<Department>) v : null;
    }

    public void clearDepartmentCache(Long id) {
        if (!redisOk() || id == null) return;
        redisTemplate.delete(DEPT_PREFIX + id);
    }

    public void clearAllDepartmentCache() {
        clearByPrefix(DEPT_PREFIX);
        redisTemplate.delete(DEPT_LIST);
    }

    /* ================= 部门用户缓存 ================= */
    public void cacheDepartmentUsers(Long deptId, List<User> users) {
        if (!redisOk() || deptId == null) return;
        redisTemplate.opsForValue().set(DEPT_USERS + deptId, users, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<User> getCachedDepartmentUsers(Long deptId) {
        if (!redisOk() || deptId == null) return null;
        Object v = redisTemplate.opsForValue().get(DEPT_USERS + deptId);
        return (v instanceof List<?>) ? (List<User>) v : null;
    }

    /* ================= Token 缓存 ================= */
    public void cacheTokenValidation(String token, boolean valid) {
        if (!redisOk() || token == null) return;
        redisTemplate.opsForValue().set(TOKEN_PREFIX + DigestUtils.sha256Hex(token), valid, TOKEN_MIN, TimeUnit.MINUTES);
    }

    public Boolean getCachedTokenValidation(String token) {
        if (!redisOk() || token == null) return null;
        Object v = redisTemplate.opsForValue().get(TOKEN_PREFIX + DigestUtils.sha256Hex(token));
        return (v instanceof Boolean b) ? b : null;
    }

    /* ================= 系统设置 ================= */
    public void cacheSystemSettings(String key, Object value) {
        if (!redisOk() || key == null) return;
        redisTemplate.opsForValue().set(SETTINGS_PREFIX + key, value, SETTINGS_MIN, TimeUnit.MINUTES);
    }

    public Object getCachedSystemSettings(String key) {
        if (!redisOk() || key == null) return null;
        return redisTemplate.opsForValue().get(SETTINGS_PREFIX + key);
    }

    public void clearSystemSettingsCache(String key) {
        if (!redisOk() || key == null || key.isBlank()) return;
        redisTemplate.delete(SETTINGS_PREFIX + key);
    }

    /* ================= prefix 清理（SCAN） ================= */
    public void clearByPrefix(String prefix) {
        if (!redisOk()) return;

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(prefix + "*").count(1000).build()
            )) {
                while (cursor.hasNext()) {
                    connection.del(cursor.next());
                }
            }
            return null;
        });
    }

    public void clearAllCache() {
        clearByPrefix(USER_PREFIX);
        clearByPrefix(DEPT_PREFIX);
        clearByPrefix(USER_DEPT_PREFIX);
        clearByPrefix(DEPT_USERS);
        clearByPrefix(TOKEN_PREFIX);
        clearByPrefix(SETTINGS_PREFIX);
    }
}
