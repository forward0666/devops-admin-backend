package com.backend.manage.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import com.backend.manage.service.system.SettingService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

/**
 * Redis缓存辅助类
 * 提供公共的Redis操作方法
 */
@Slf4j
@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SettingService settingService;

    public CacheService(RedisTemplate<String, Object> redisTemplate, @Lazy SettingService settingService) {
        this.redisTemplate = redisTemplate;
        this.settingService = settingService;
    }

    /* ================= Redis 健康检查 ================= */
    private volatile boolean redisAvailable = true;
    private volatile long lastCheck = 0;
    private static final String TOKEN_PREFIX = "token:validation:";
    private static final long TOKEN_MIN = 60;

    private boolean redisOk() {
        long now = System.currentTimeMillis();
        if (redisAvailable && now - lastCheck < 30_000) return true;

        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisAvailable = true;
            log.debug("✅ Redis 连接正常");
        } catch (Exception e) {
            redisAvailable = false;
            log.error("❌ Redis 不可用: {}", e.getMessage(), e);
        }
        lastCheck = now;
        return redisAvailable;
    }

    public boolean isRedisAvailable() {
        return redisOk();
    }

    /* ================= prefix 清理（SCAN） ================= */
    public void clearByPrefix(String prefix) {
        if (!redisOk()) return;

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(1000)
                    .build();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    connection.keyCommands().del(key); // Redis 3.2.5 只能用 DEL
                }
            }
            return null;
        });
    }

    /* ================= 清理所有缓存 ================= */
    public void clearAllCache() {
        if (!redisOk()) return;

        // 清理所有业务相关的缓存
        clearByPrefix("user:");
        clearByPrefix("users:");
        clearByPrefix("role:");
        clearByPrefix("roles:");
        clearByPrefix("department:");
        clearByPrefix("departments:");
        clearByPrefix("menu:");
        clearByPrefix("menus:");
        clearByPrefix("position:");
        clearByPrefix("positions:");
        clearByPrefix("permission:");
        clearByPrefix("token:");
        clearByPrefix("settings:");
    }

    public void cacheTokenValidation(String username, String token, boolean valid) {
        if (!isRedisAvailable() || token == null) return;
        String key = TOKEN_PREFIX + username + ":" + DigestUtils.sha256Hex(token);
        long expireMin = settingService.getTokenExpireSeconds() / 60;
        redisTemplate.opsForValue().set(key, valid, expireMin, java.util.concurrent.TimeUnit.MINUTES);
    }

    public Boolean getCachedTokenValidation(String username, String token) {
        if (!isRedisAvailable() || token == null) return null;
        String key = TOKEN_PREFIX + username + ":" + DigestUtils.sha256Hex(token);
        Object v = redisTemplate.opsForValue().get(key);
        return (v instanceof Boolean b) ? b : null;
    }

    /* ================= Generic Cache Operations ================= */

    public Object get(String key) {
        if (!redisOk()) return null;
        return redisTemplate.opsForValue().get(key);
    }

    public void set(String key, Object value, long timeout, java.util.concurrent.TimeUnit unit) {
        if (!redisOk()) return;
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Long increment(String key) {
        if (!redisOk()) return null;
        return redisTemplate.opsForValue().increment(key);
    }

    public Boolean delete(String key) {
        if (!redisOk()) return false;
        return redisTemplate.delete(key);
    }
}
