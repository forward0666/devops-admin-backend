package com.backend.manage.service;

import com.backend.manage.entity.UserEntity;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户缓存服务
 * 负责用户相关的Redis缓存操作
 */
@Slf4j
@Service
public class UserCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String USER_PREFIX = "user:";
    private static final String USERS_LIST = "users:list";
    private static final long CACHE_MIN = 30;
    private static final long USER_LIST_CACHE_MIN = 10;

    public void cacheUser(UserEntity user) {
        if (!cacheService.isRedisAvailable() || user == null || user.getId() == null) return;
        redisTemplate.opsForValue().set(USER_PREFIX + user.getId(), user, CACHE_MIN, TimeUnit.MINUTES);
    }

    public UserEntity getCachedUser(Long userId) {
        if (!cacheService.isRedisAvailable() || userId == null) return null;
        Object v = redisTemplate.opsForValue().get(USER_PREFIX + userId);
        return (v instanceof UserEntity u) ? u : null;
    }

    public void cacheUsersList(List<UserEntity> users) {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.opsForValue().set(USERS_LIST, users, USER_LIST_CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<UserEntity> getCachedUsersList() {
        if (!cacheService.isRedisAvailable()) return null;
        Object v = redisTemplate.opsForValue().get(USERS_LIST);
        return (v instanceof List<?>) ? (List<UserEntity>) v : null;
    }

    public void clearAllUserListCache() {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.delete(USERS_LIST);
    }

    public void clearUserCache(Long userId) {
        if (!cacheService.isRedisAvailable() || userId == null) return;
        redisTemplate.delete(USER_PREFIX + userId);
    }

    public void clearAllUserCache() {
        cacheService.clearByPrefix(USER_PREFIX);
        redisTemplate.delete(USERS_LIST);
    }

    /**
     * 清除用户的登录 token，强制下线
n     */
    public void clearUserTokens(String username) {
        if (!cacheService.isRedisAvailable() || username == null || username.isEmpty()) return;
        cacheService.clearByPrefix("token:validation:" + username + ":");
        log.info("✅ Cleared login tokens for user: {}", username);
    }
}
