package com.backend.manage.service.settings.cache;

import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 系统配置缓存服务
 * 负责系统配置相关的Redis缓存操作
 */
@Slf4j
@Service
public class SystemConfigCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String SETTINGS_PREFIX = "settings:";
    private static final long SETTINGS_MIN = 60;

    public void cacheSystemSettings(String key, Object value) {
        if (!cacheService.isRedisAvailable() || key == null) return;
        redisTemplate.opsForValue().set(SETTINGS_PREFIX + key, value, SETTINGS_MIN, java.util.concurrent.TimeUnit.MINUTES);
    }

    public Object getCachedSystemSettings(String key) {
        if (!cacheService.isRedisAvailable() || key == null) return null;
        return redisTemplate.opsForValue().get(SETTINGS_PREFIX + key);
    }

    public void clearSystemSettingsCache(String key) {
        if (!cacheService.isRedisAvailable() || key == null || key.isBlank()) return;
        redisTemplate.delete(SETTINGS_PREFIX + key);
    }
}
