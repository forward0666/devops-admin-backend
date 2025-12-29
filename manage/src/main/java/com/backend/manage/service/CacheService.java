package com.backend.manage.service;

import com.backend.manage.model.Department;
import com.backend.manage.model.Menu;
import com.backend.manage.model.Position;
import com.backend.manage.model.Role;
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

    private static final String MENU_PREFIX = "menu:";
    private static final String MENU_LIST = "menus:list";
    private static final String MENU_ROOT = "menus:root";

    private static final String USER_DEPT_PREFIX = "user:department:";
    private static final String TOKEN_PREFIX = "token:validation:";
    private static final String SETTINGS_PREFIX = "settings:";

    private static final String ROLE_PREFIX = "role:";
    private static final String ROLE_LIST = "roles:list";

    private static final String POSITION_PREFIX = "position:";
    private static final String POSITION_LIST = "positions:list";

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

    /* ================= 菜单缓存 ================= */
    public void cacheMenu(Menu menu) {
        if (!redisOk() || menu == null || menu.getId() == null) return;
        redisTemplate.opsForValue().set(MENU_PREFIX + menu.getId(), menu, CACHE_MIN, TimeUnit.MINUTES);
    }

    public Menu getCachedMenu(Long id) {
        if (!redisOk() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(MENU_PREFIX + id);
        return (v instanceof Menu m) ? m : null;
    }

    public void cacheMenusList(List<Menu> list) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(MENU_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<Menu> getCachedMenusList() {
        if (!redisOk()) return null;
        Object v = redisTemplate.opsForValue().get(MENU_LIST);
        return (v instanceof List<?>) ? (List<Menu>) v : null;
    }

    public void cacheRootMenusList(List<Menu> list) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(MENU_ROOT, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<Menu> getCachedRootMenusList() {
        if (!redisOk()) return null;
        Object v = redisTemplate.opsForValue().get(MENU_ROOT);
        return (v instanceof List<?>) ? (List<Menu>) v : null;
    }

    public void clearMenuCache(Long id) {
        if (!redisOk() || id == null) return;
        redisTemplate.delete(MENU_PREFIX + id);
    }

    public void clearAllMenuCache() {
        clearByPrefix(MENU_PREFIX);
        redisTemplate.delete(MENU_LIST);
        redisTemplate.delete(MENU_ROOT);
    }

    /* ================= 角色缓存 ================= */
    public void cacheRole(Role role) {
        if (!redisOk() || role == null || role.getId() == null) return;
        redisTemplate.opsForValue().set(ROLE_PREFIX + role.getId(), role, CACHE_MIN, TimeUnit.MINUTES);
    }

    public Role getCachedRole(Long id) {
        if (!redisOk() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(ROLE_PREFIX + id);
        return (v instanceof Role r) ? r : null;
    }

    public void cacheRolesList(List<Role> list) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(ROLE_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<Role> getCachedRolesList() {
        if (!redisOk()) return null;
        Object v = redisTemplate.opsForValue().get(ROLE_LIST);
        return (v instanceof List<?>) ? (List<Role>) v : null;
    }

    public void clearRoleCache(Long id) {
        if (!redisOk() || id == null) return;
        redisTemplate.delete(ROLE_PREFIX + id);
    }

    public void clearAllRoleCache() {
        clearByPrefix(ROLE_PREFIX);
        redisTemplate.delete(ROLE_LIST);
    }

    /* ================= 职位缓存 ================= */
    public void cachePosition(Position position) {
        if (!redisOk() || position == null || position.getId() == null) return;
        redisTemplate.opsForValue().set(POSITION_PREFIX + position.getId(), position, CACHE_MIN, TimeUnit.MINUTES);
    }

    public Position getCachedPosition(Long id) {
        if (!redisOk() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(POSITION_PREFIX + id);
        return (v instanceof Position p) ? p : null;
    }

    public void cachePositionsList(List<Position> list) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(POSITION_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<Position> getCachedPositionsList() {
        if (!redisOk()) return null;
        Object v = redisTemplate.opsForValue().get(POSITION_LIST);
        return (v instanceof List<?>) ? (List<Position>) v : null;
    }

    public void clearPositionCache(Long id) {
        if (!redisOk() || id == null) return;
        redisTemplate.delete(POSITION_PREFIX + id);
    }

    public void clearAllPositionCache() {
        clearByPrefix(POSITION_PREFIX);
        redisTemplate.delete(POSITION_LIST);
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
        clearByPrefix(MENU_PREFIX);
        clearByPrefix(USER_DEPT_PREFIX);
        clearByPrefix(DEPT_USERS);
        clearByPrefix(TOKEN_PREFIX);
        clearByPrefix(SETTINGS_PREFIX);
        clearByPrefix(ROLE_PREFIX);
        clearByPrefix(POSITION_PREFIX);
        redisTemplate.delete(USERS_LIST);
        redisTemplate.delete(DEPT_LIST);
        redisTemplate.delete(MENU_LIST);
        redisTemplate.delete(MENU_ROOT);
        redisTemplate.delete(ROLE_LIST);
        redisTemplate.delete(POSITION_LIST);
    }
}
