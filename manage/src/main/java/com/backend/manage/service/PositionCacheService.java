package com.backend.manage.service;

import com.backend.manage.entity.PositionEntity;
import com.backend.utils.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 职位缓存服务
 * 负责职位相关的Redis缓存操作
 */
@Slf4j
@Service
public class PositionCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String POSITION_PREFIX = "position:";
    private static final String POSITION_LIST = "positions:list";
    private static final long CACHE_MIN = 30;

    public void cachePosition(PositionEntity position) {
        if (!cacheService.isRedisAvailable() || position == null || position.getId() == null) return;
        redisTemplate.opsForValue().set(POSITION_PREFIX + position.getId(), position, CACHE_MIN, TimeUnit.MINUTES);
    }

    public PositionEntity getCachedPosition(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(POSITION_PREFIX + id);
        return (v instanceof PositionEntity p) ? p : null;
    }

    public void cachePositionsList(List<PositionEntity> list) {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.opsForValue().set(POSITION_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<PositionEntity> getCachedPositionsList() {
        if (!cacheService.isRedisAvailable()) return null;
        Object v = redisTemplate.opsForValue().get(POSITION_LIST);
        return (v instanceof List<?>) ? (List<PositionEntity>) v : null;
    }

    public void clearPositionCache(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return;
        redisTemplate.delete(POSITION_PREFIX + id);
    }

    public void clearAllPositionCache() {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.delete(POSITION_LIST);
        redisTemplate.delete(POSITION_LIST);
    }
}
