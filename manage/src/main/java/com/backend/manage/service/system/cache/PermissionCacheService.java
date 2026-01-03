package com.backend.manage.service.system.cache;

import com.backend.manage.entity.system.PermissionMappingEntity;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 权限缓存服务
 * 负责权限相关的Redis缓存操作
 */
@Slf4j
@Service
public class PermissionCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String PERMISSION_PREFIX = "permission:";
    private static final String PERMISSION_ROLE_PREFIX = "permission:role:";
    private static final long CACHE_MIN = 30;

    public void cachePermissionMapping(PermissionMappingEntity mapping) {
        if (!cacheService.isRedisAvailable() || mapping == null) return;
        String key = PERMISSION_PREFIX + mapping.getRoleId() + ":" + mapping.getMenuId();
        redisTemplate.opsForValue().set(key, mapping, CACHE_MIN, TimeUnit.MINUTES);
    }

    public void cachePermissionMappingsByRole(Long roleId, List<PermissionMappingEntity> mappings) {
        if (!cacheService.isRedisAvailable() || roleId == null) return;
        redisTemplate.opsForValue().set(PERMISSION_ROLE_PREFIX + roleId, mappings, CACHE_MIN, TimeUnit.MINUTES);
    }

    public PermissionMappingEntity getCachedPermissionMapping(Long roleId, Long menuId) {
        if (!cacheService.isRedisAvailable() || roleId == null || menuId == null) return null;
        String key = PERMISSION_PREFIX + roleId + ":" + menuId;
        Object v = redisTemplate.opsForValue().get(key);
        return (v instanceof PermissionMappingEntity m) ? m : null;
    }

    @SuppressWarnings("unchecked")
    public List<PermissionMappingEntity> getCachedPermissionMappingsByRole(Long roleId) {
        if (!cacheService.isRedisAvailable() || roleId == null) return null;
        Object v = redisTemplate.opsForValue().get(PERMISSION_ROLE_PREFIX + roleId);
        return (v instanceof List<?>) ? (List<PermissionMappingEntity>) v : null;
    }

    public void clearPermissionMappingCache(Long roleId, Long menuId) {
        if (!cacheService.isRedisAvailable() || roleId == null || menuId == null) return;
        redisTemplate.delete(PERMISSION_PREFIX + roleId + ":" + menuId);
    }

    public void clearPermissionMappingsByRoleCache(Long roleId) {
        if (!cacheService.isRedisAvailable() || roleId == null) return;
        redisTemplate.delete(PERMISSION_ROLE_PREFIX + roleId);
    }

    public void clearAllPermissionMappingCache() {
        cacheService.clearByPrefix(PERMISSION_PREFIX);
        cacheService.clearByPrefix(PERMISSION_ROLE_PREFIX);
    }
}
