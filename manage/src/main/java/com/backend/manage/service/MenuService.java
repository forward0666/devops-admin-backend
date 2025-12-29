package com.backend.manage.service;

import com.backend.manage.mapper.MenuMapper;
import com.backend.manage.entity.MenuEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单服务类
 * 提供菜单相关的业务逻辑处理
 * 支持Redis缓存，提高查询性能
 * 使用 Java 21 风格
 */
@Slf4j
@Service
public class MenuService {

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 获取所有菜单列表
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * @return 菜单列表
     */
    public List<MenuEntity> getAllMenus() {
        log.info("Fetching all menus");

        // 优先从Redis缓存获取菜单列表
        if (cacheService.isRedisAvailable()) {
            var cachedMenus = cacheService.getCachedMenusList();
            if (cachedMenus != null) {
                log.debug("从缓存中获取菜单列表成功");
                return cachedMenus;
            }
        }

        try {
            // 从数据库查询所有菜单
            var menus = menuMapper.findAll();

            // 为每个菜单设置父级菜单名称
            setParentNames(menus);

            // 将查询结果缓存到Redis中
            if (cacheService.isRedisAvailable()) {
                cacheService.cacheMenusList(menus);
            }

            log.info("成功获取 {} 个菜单", menus.size());
            return menus;
        } catch (Exception e) {
            log.error("获取所有菜单时发生错误", e);
            throw new RuntimeException("获取菜单列表失败", e);
        }
    }

    /**
     * 根据ID获取菜单
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * @param id 菜单ID
     * @return 菜单对象
     */
    public MenuEntity getMenuById(Long id) {
        log.info("Fetching menu by ID: {}", id);

        if (id == null) {
            log.warn("菜单ID不能为空");
            return null;
        }

        // 优先从Redis缓存获取菜单信息
        if (cacheService.isRedisAvailable()) {
            var cachedMenu = cacheService.getCachedMenu(id);
            if (cachedMenu != null) {
                log.debug("从缓存中获取菜单成功: {}", id);
                return cachedMenu;
            }
        }

        try {
            // 从数据库查询菜单信息
            var menu = menuMapper.findById(id);

            // 缓存到Redis
            if (menu != null && cacheService.isRedisAvailable()) {
                cacheService.cacheMenu(menu);
            }

            return menu;
        } catch (Exception e) {
            log.error("获取菜单时发生错误", e);
            throw new RuntimeException("获取菜单失败", e);
        }
    }

    /**
     * 创建新菜单
     * 创建后清除菜单列表缓存
     * @param menu 菜单对象
     * @return 创建的菜单对象
     * @throws IllegalArgumentException 如果父菜单不存在
     */
    public MenuEntity createMenu(MenuEntity menu) {
        log.info("Creating new menu: {}", menu.getName());

        // 验证父菜单是否存在
        if (menu.getParentId() != null && menuMapper.existsById(menu.getParentId()) == 0) {
            throw new IllegalArgumentException("Parent menu not found with ID: " + menu.getParentId());
        }

        // 设置默认值 - Java 21: 使用 switch 表达式
        menu.setSort(menu.getSort() != null ? menu.getSort() : 0);
        menu.setStatus(menu.getStatus() != null && !menu.getStatus().isEmpty() ? menu.getStatus() : "active");
        menu.setType(menu.getType() != null && !menu.getType().isEmpty() ? menu.getType() : "menu");

        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());

        menuMapper.insert(menu);

        // 清除菜单列表缓存
        clearMenuCache();

        log.info("Successfully created menu with ID: {}", menu.getId());
        return menu;
    }

    /**
     * 更新菜单
     * 更新后清除菜单列表和该菜单的缓存
     * @param menu 菜单对象
     * @return 更新后的菜单对象
     * @throws IllegalArgumentException 如果菜单不存在或父菜单无效
     */
    public MenuEntity updateMenu(MenuEntity menu) {
        log.info("Updating menu with ID: {}", menu.getId());

        // 验证菜单是否存在
        if (menuMapper.existsById(menu.getId()) == 0) {
            throw new IllegalArgumentException("Menu not found with ID: " + menu.getId());
        }

        // 验证父菜单是否存在（如果设置了父菜单）
        if (menu.getParentId() != null) {
            if (menu.getParentId().equals(menu.getId())) {
                throw new IllegalArgumentException("Menu cannot be its own parent");
            }
            if (menuMapper.existsById(menu.getParentId()) == 0) {
                throw new IllegalArgumentException("Parent menu not found with ID: " + menu.getParentId());
            }
        }

        menu.setUpdatedAt(LocalDateTime.now());

        int result = menuMapper.update(menu);
        if (result > 0) {
            // 清除菜单相关缓存
            clearMenuCache();

            log.info("Successfully updated menu with ID: {}", menu.getId());
            return menu;
        } else {
            log.error("Failed to update menu with ID: {}", menu.getId());
            return null;
        }
    }

    /**
     * 删除菜单
     * 删除后清除菜单列表缓存
     * @param id 菜单ID
     * @return 是否删除成功
     * @throws IllegalStateException 如果菜单有子菜单
     */
    public boolean deleteMenu(Long id) {
        log.info("Deleting menu with ID: {}", id);

        // 检查菜单是否存在
        if (menuMapper.existsById(id) == 0) {
            return false;
        }

        // 检查是否有子菜单
        int childCount = menuMapper.countByParentId(id);
        if (childCount > 0) {
            throw new IllegalStateException("Cannot delete menu because it has " + childCount + " child menu(s)");
        }

        int result = menuMapper.deleteById(id);
        if (result > 0) {
            // 清除菜单相关缓存
            clearMenuCache();

            log.info("Successfully deleted menu with ID: {}", id);
            return true;
        } else {
            log.error("Failed to delete menu with ID: {}", id);
            return false;
        }
    }

    /**
     * 获取根菜单列表（顶级菜单）
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * @return 根菜单列表
     */
    public List<MenuEntity> getRootMenus() {
        log.info("Fetching root menus");

        // 优先从Redis缓存获取根菜单
        if (cacheService.isRedisAvailable()) {
            var cachedMenus = cacheService.getCachedRootMenusList();
            if (cachedMenus != null) {
                log.debug("从缓存中获取根菜单成功");
                return cachedMenus;
            }
        }

        try {
            // 从数据库查询根菜单
            var menus = menuMapper.findRootMenus();

            // 缓存到Redis
            if (cacheService.isRedisAvailable()) {
                cacheService.cacheRootMenusList(menus);
            }

            log.info("成功获取 {} 个根菜单", menus.size());
            return menus;
        } catch (Exception e) {
            log.error("获取根菜单时发生错误", e);
            throw new RuntimeException("获取根菜单失败", e);
        }
    }

    /**
     * 根据父菜单ID获取子菜单
     * @param parentId 父菜单ID
     * @return 子菜单列表
     */
    public List<MenuEntity> getChildMenus(Long parentId) {
        log.info("Fetching child menus for parent ID: {}", parentId);
        return menuMapper.findByParentId(parentId);
    }

    /**
     * 为菜单列表设置父级菜单名称
     * @param menus 菜单列表
     */
    private void setParentNames(List<MenuEntity> menus) {
        if (menus == null || menus.isEmpty()) {
            return;
        }

        // 创建ID到菜单的映射 - Java 21: 使用 stream 简化
        Map<Long, MenuEntity> menuMap = menus.stream()
                .collect(Collectors.toMap(MenuEntity::getId, menu -> menu));

        // 为每个菜单设置父级菜单名称
        menus.forEach(menu -> {
            if (menu.getParentId() != null) {
                var parent = menuMap.get(menu.getParentId());
                if (parent != null) {
                    menu.setParentName(parent.getName());
                }
            }
        });
    }

    /**
     * 清除菜单相关缓存
     */
    private void clearMenuCache() {
        if (cacheService.isRedisAvailable()) {
            cacheService.clearAllMenuCache();
            log.debug("已清除菜单缓存");
        }
    }
}
