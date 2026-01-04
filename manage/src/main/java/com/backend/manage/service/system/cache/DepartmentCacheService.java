package com.backend.manage.service.system.cache;

import com.backend.manage.entity.system.DepartmentEntity;
import com.backend.manage.entity.system.UserEntity;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 部门缓存服务
 * 负责部门相关的Redis缓存操作
 */
@Slf4j
@Service
public class DepartmentCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String DEPT_PREFIX = "department:";
    private static final String DEPT_LIST = "departments:list";
    private static final String DEPT_USERS = "department:users:";
    private static final long CACHE_MIN = 30;

    public void departmentCache(DepartmentEntity dept) {
        if (!cacheService.isRedisAvailable() || dept == null || dept.getId() == null) return;
        redisTemplate.opsForValue().set(DEPT_PREFIX + dept.getId(), dept, CACHE_MIN, TimeUnit.MINUTES);
    }

    public DepartmentEntity getDepartmentCache(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(DEPT_PREFIX + id);
        return (v instanceof DepartmentEntity d) ? d : null;
    }

    public void departmentsCache(List<DepartmentEntity> list) {
        if (!cacheService.isRedisAvailable()) {
            log.warn("⚠️ Redis 不可用，无法缓存部门列表");
            return;
        }
        if (list == null || list.isEmpty()) {
            log.warn("⚠️ 部门列表为空，无法缓存");
            return;
        }
        try {
            redisTemplate.opsForValue().set(DEPT_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
            log.info("✅ 部门列表已缓存到 Redis: {}", DEPT_LIST);
        } catch (Exception e) {
            log.error("❌ 缓存部门列表失败: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<DepartmentEntity> getDepartmentCache() {
        if (!cacheService.isRedisAvailable()) {
            log.debug("⚠️ Redis 不可用，无法读取部门列表缓存");
            return null;
        }
        try {
            Object v = redisTemplate.opsForValue().get(DEPT_LIST);
            if (v != null && v instanceof List<?>) {
                log.info("✅ 从 Redis 读取部门列表缓存成功: {}", DEPT_LIST);
                return (List<DepartmentEntity>) v;
            }
            log.debug("🔍 Redis 中找不到部门列表缓存: {}", DEPT_LIST);
            return null;
        } catch (Exception e) {
            log.error("❌ 读取部门列表缓存失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public void clearDepartmentCache(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return;
        redisTemplate.delete(DEPT_PREFIX + id);
    }

    public void clearDepartmentsCache() {
        cacheService.clearByPrefix(DEPT_PREFIX);
        redisTemplate.delete(DEPT_LIST);
    }

    public void departmentUsersCache(Long deptId, List<UserEntity> users) {
        if (!cacheService.isRedisAvailable() || deptId == null) return;
        redisTemplate.opsForValue().set(DEPT_USERS + deptId, users, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<UserEntity> getDepartmentUsersCache(Long deptId) {
        if (!cacheService.isRedisAvailable() || deptId == null) return null;
        Object v = redisTemplate.opsForValue().get(DEPT_USERS + deptId);
        return (v instanceof List<?>) ? (List<UserEntity>) v : null;
    }

    public void clearDepartmentUsersCache(Long deptId) {
        if (!cacheService.isRedisAvailable() || deptId == null) return;
        redisTemplate.delete(DEPT_USERS + deptId);
    }

    public List<DepartmentEntity> getDepartmentsCache() {
        return getDepartmentCache();
    }
}
