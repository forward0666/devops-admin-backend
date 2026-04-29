package com.backend.manage.service;

import com.backend.manage.entity.RoleEntity;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 角色缓存服务
 * 负责角色相关的Redis缓存操作
 */
@Slf4j
@Service
public class RoleCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String ROLE_PREFIX = "role:";
    private static final String ROLE_LIST = "roles:list";
    private static final long CACHE_MIN = 30;

    public void cacheRole(RoleEntity role) {
        if (!cacheService.isRedisAvailable() || role == null || role.getId() == null) return;
        redisTemplate.opsForValue().set(ROLE_PREFIX + role.getId(), role, CACHE_MIN, TimeUnit.MINUTES);
    }

    public RoleEntity getCachedRole(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(ROLE_PREFIX + id);
        return (v instanceof RoleEntity r) ? r : null;
    }

    public void cacheRolesList(List<RoleEntity> list) {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.opsForValue().set(ROLE_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<RoleEntity> getCachedRolesList() {
        if (!cacheService.isRedisAvailable()) return null;
        Object v = redisTemplate.opsForValue().get(ROLE_LIST);
        return (v instanceof List<?>) ? (List<RoleEntity>) v : null;
    }

    public void clearRoleCache(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return;
        redisTemplate.delete(ROLE_PREFIX + id);
    }

    public void clearAllRoleCache() {
        cacheService.clearByPrefix(ROLE_PREFIX);
        redisTemplate.delete(ROLE_LIST);
    }
}
