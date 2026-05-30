package com.backend.manage.service;

import com.backend.manage.entity.RoleEntity;
import com.backend.manage.mapper.RoleMapper;
import com.backend.manage.service.RoleCacheService;
import com.backend.utils.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色服务类
 * 处理角色相关的业务逻辑
 * 使用 Java 21 风格
 */
@Slf4j
@Service
public class RoleService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RoleCacheService roleCacheService;

    /**
     * 获取所有角色列表
     */
    public List<RoleEntity> getAllRoles() {
        log.info("正在获取所有角色列表");

        if (cacheService.isRedisAvailable()) {
            var cached = roleCacheService.getCachedRolesList();
            if (cached != null) {
                log.debug("从缓存中获取角色列表成功");
                return cached;
            }
        }

        try {
            var roles = roleMapper.findAll();
            cacheRoleList(roles);
            log.info("成功获取 {} 个角色", roles.size());
            return roles;
        } catch (Exception e) {
            log.error("获取所有角色时发生错误", e);
            throw new RuntimeException("获取角色列表失败", e);
        }
    }

    /**
     * 根据ID获取角色
     */
    public RoleEntity getRoleById(Long id) {
        log.info("正在根据ID获取角色: {}", id);

        if (id == null) {
            log.warn("角色ID不能为空");
            return null;
        }

        if (cacheService.isRedisAvailable()) {
            var cached = roleCacheService.getCachedRole(id);
            if (cached != null) {
                log.debug("从缓存中获取角色成功: {}", id);
                return cached;
            }
        }

        try {
            var role = roleMapper.findById(id);
            if (role != null) {
                cacheSingleRole(role);
                log.info("成功获取角色: {}", role.getName());
                return role;
            }
            log.warn("找不到ID为 {} 的角色", id);
            return null;
        } catch (Exception e) {
            log.error("根据ID获取角色时发生错误: {}", id, e);
            throw new RuntimeException("获取角色信息失败", e);
        }
    }

    /**
     * 创建新角色
     */
    public RoleEntity createRole(RoleEntity role) {
        log.info("正在创建新角色: {}", role.getName());

        validateRoleForCreation(role);

        try {
            validateCodeUnique(role.getCode(), null);

            if (role.getUserCount() == null) {
                role.setUserCount(0);
            }

            setTimestamps(role);
            roleMapper.insert(role);
            clearRoleCache();

            log.info("成功创建角色，ID: {}", role.getId());
            return role;
        } catch (Exception e) {
            log.error("创建角色时发生错误: {}", role.getName(), e);
            throw new RuntimeException("创建角色失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新角色
     */
    public RoleEntity updateRole(RoleEntity role) {
        log.info("正在更新角色，ID: {}", role.getId());

        validateRoleForUpdate(role);

        try {
            if (!roleMapper.existsById(role.getId())) {
                log.warn("找不到要更新的角色，ID: {}", role.getId());
                return null;
            }

            validateCodeUnique(role.getCode(), role.getId());

            role.setUpdatedAt(LocalDateTime.now());
            roleMapper.update(role);
            clearRoleCache();

            log.info("成功更新角色: {}", role.getName());
            return getRoleById(role.getId());
        } catch (Exception e) {
            log.error("更新角色时发生错误，ID: {}", role.getId(), e);
            throw new RuntimeException("更新角色失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除角色
     */
    public boolean deleteRole(Long id) {
        log.info("正在删除角色，ID: {}", id);

        if (id == null) {
            log.warn("删除角色时角色ID不能为空");
            return false;
        }

        try {
            if (!roleMapper.existsById(id)) {
                log.warn("找不到要删除的角色，ID: {}", id);
                return false;
            }

            validateNoUsersAssigned(id);
            boolean deleted = roleMapper.deleteById(id) > 0;

            if (deleted) {
                clearRoleCache();
                log.info("成功删除角色，ID: {}", id);
            } else {
                log.warn("删除角色失败，ID: {}", id);
            }
            return deleted;
        } catch (Exception e) {
            log.error("删除角色时发生错误，ID: {}", id, e);
            throw new RuntimeException("删除角色失败: " + e.getMessage(), e);
        }
    }

    // --- Cache helpers ---

    private void cacheRoleList(List<RoleEntity> roles) {
        if (cacheService.isRedisAvailable()) {
            roleCacheService.cacheRolesList(roles);
        }
    }

    private void cacheSingleRole(RoleEntity role) {
        if (cacheService.isRedisAvailable()) {
            roleCacheService.cacheRole(role);
        }
    }

    private void clearRoleCache() {
        if (cacheService.isRedisAvailable()) {
            roleCacheService.clearAllRoleCache();
        }
    }

    // --- Validation helpers ---

    private void validateCodeUnique(String code, Long excludeId) {
        boolean exists = excludeId != null
                ? roleMapper.existsByCodeExcludingId(code, excludeId)
                : roleMapper.existsByCode(code);
        if (exists) {
            throw new IllegalArgumentException("角色代码 '" + code + "' 已存在");
        }
    }

    private void validateNoUsersAssigned(Long roleId) {
        RoleEntity role = roleMapper.findById(roleId);
        if (role.getUserCount() != null && role.getUserCount() > 0) {
            throw new IllegalStateException("无法删除包含 " + role.getUserCount() + " 个用户的角色。请先重新分配用户。");
        }
    }

    private void setTimestamps(RoleEntity role) {
        LocalDateTime now = LocalDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
    }

    // --- Field validation ---

    private void validateRoleForCreation(RoleEntity role) {
        if (role == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        validateRoleFields(role);
    }

    private void validateRoleForUpdate(RoleEntity role) {
        if (role == null) {
            throw new IllegalArgumentException("角色不能为空");
        }
        if (role.getId() == null) {
            throw new IllegalArgumentException("更新角色需要提供角色ID");
        }
        validateRoleFields(role);
    }

    private void validateRoleFields(RoleEntity role) {
        if (role.getName() == null || role.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        if (role.getName().length() > 100) {
            throw new IllegalArgumentException("角色名称不能超过100个字符");
        }
        if (role.getCode() == null || role.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("角色代码不能为空");
        }
        if (role.getCode().length() > 50) {
            throw new IllegalArgumentException("角色代码不能超过50个字符");
        }
        if (role.getDescription() != null && role.getDescription().length() > 500) {
            throw new IllegalArgumentException("角色描述不能超过500个字符");
        }
        if (role.getStatus() == null) {
            role.setStatus("active");
        }
    }
}
