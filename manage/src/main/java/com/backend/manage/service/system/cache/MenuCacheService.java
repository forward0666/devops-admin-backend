package com.backend.manage.service.system.cache;

import com.backend.manage.entity.system.MenuEntity;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 菜单缓存服务
 * 负责菜单相关的Redis缓存操作
 */
@Slf4j
@Service
public class MenuCacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheService cacheService;

    private static final String MENU_PREFIX = "menu:";
    private static final String MENU_LIST = "menus:list";
    private static final String MENU_ROOT = "menus:root";
    private static final long CACHE_MIN = 30;

    public void cacheMenu(MenuEntity menu) {
        if (!cacheService.isRedisAvailable() || menu == null || menu.getMenuId() == null) return;
        redisTemplate.opsForValue().set(MENU_PREFIX + menu.getMenuId(), menu, CACHE_MIN, TimeUnit.MINUTES);
    }

    public MenuEntity getCachedMenu(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return null;
        Object v = redisTemplate.opsForValue().get(MENU_PREFIX + id);
        return (v instanceof MenuEntity m) ? m : null;
    }

    public void cacheMenusList(List<MenuEntity> list) {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.opsForValue().set(MENU_LIST, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<MenuEntity> getCachedMenusList() {
        if (!cacheService.isRedisAvailable()) return null;
        Object v = redisTemplate.opsForValue().get(MENU_LIST);
        return (v instanceof List<?>) ? (List<MenuEntity>) v : null;
    }

    public void cacheRootMenusList(List<MenuEntity> list) {
        if (!cacheService.isRedisAvailable()) return;
        redisTemplate.opsForValue().set(MENU_ROOT, list, CACHE_MIN, TimeUnit.MINUTES);
    }

    @SuppressWarnings("unchecked")
    public List<MenuEntity> getCachedRootMenusList() {
        if (!cacheService.isRedisAvailable()) return null;
        Object v = redisTemplate.opsForValue().get(MENU_ROOT);
        return (v instanceof List<?>) ? (List<MenuEntity>) v : null;
    }

    public void clearMenuCache(Long id) {
        if (!cacheService.isRedisAvailable() || id == null) return;
        redisTemplate.delete(MENU_PREFIX + id);
    }

    public void clearAllMenuCache() {
        cacheService.clearByPrefix(MENU_PREFIX);
        redisTemplate.delete(MENU_LIST);
        redisTemplate.delete(MENU_ROOT);
    }
}
