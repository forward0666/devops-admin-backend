package com.backend.manage.service.system;

import com.backend.manage.dto.system.PermissionRequestDto;
import com.backend.manage.dto.system.PermissionResponseDto;
import com.backend.manage.entity.system.PermissionEntity;
import com.backend.manage.entity.system.RoleEntity;
import com.backend.manage.mapper.system.MenuMapper;
import com.backend.manage.mapper.system.PermissionMapper;
import com.backend.manage.mapper.system.RoleMapper;
import com.backend.manage.service.system.cache.PermissionCacheService;
import com.backend.manage.service.system.cache.MenuCacheService;
import com.backend.manage.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PermissionService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private PermissionCacheService permissionCacheService;

    @Autowired
    private MenuCacheService menuCacheService;

    public List<PermissionResponseDto> getAllPermissions() {
        log.info("Fetching all permissions");
        try {
            if (cacheService.isRedisAvailable()) {
            }

            List<PermissionEntity> mappings = permissionMapper.findAllWithDetails();

            Map<Long, List<PermissionEntity>> groupedByRole = mappings.stream()
                    .collect(Collectors.groupingBy(PermissionEntity::getRoleId));

            var responses = groupedByRole.entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .map(entry -> {
                        var roleMappings = entry.getValue();
                        var firstMapping = roleMappings.get(0);

                        return new PermissionResponseDto(
                                firstMapping.getRoleId(),
                                firstMapping.getRoleName(),
                                firstMapping.getRoleCode(),
                                roleMappings.stream()
                                        .map(PermissionEntity::getMenuId)
                                        .toList(),
                                roleMappings.stream()
                                        .map(PermissionEntity::getMenuName)
                                        .toList(),
                                roleMappings.stream()
                                        .map(mapping -> new PermissionResponseDto.MenuPermissionType(
                                                mapping.getMenuId(),
                                                normalizePermissionType(mapping.getPermissionType())
                                        ))
                                        .toList(),
                                firstMapping.getCreatedAt(),
                                firstMapping.getUpdatedAt()
                        );
                    })
                    .toList();

            log.info("Successfully retrieved {} permissions", responses.size());
            return responses;
        } catch (Exception e) {
            log.error("Error retrieving permissions", e);
            throw new RuntimeException("Failed to retrieve permissions: " + e.getMessage(), e);
        }
    }

    public PermissionResponseDto getPermissionsByRoleId(Long roleId) {
        log.info("Fetching permissions by role ID: {}", roleId);
        try {
            var role = roleMapper.findById(roleId);
            if (role == null) {
                log.warn("Role not found by ID: {}", roleId);
                return new PermissionResponseDto(roleId, null, null, List.of(), List.of(), List.of(), null, null);
            }

            List<PermissionEntity> permission = null;
            if (cacheService.isRedisAvailable()) {
                permission = permissionCacheService.getPermissionsCacheByRole(roleId);
                if (permission != null) {
                    log.info("Retrieved permissions from cache for role: {}", role.getName());
                }
            }

            if (permission == null) {
                permission = permissionMapper.findByRoleIdWithMenus(roleId);
                if (cacheService.isRedisAvailable()) {
                    permissionCacheService.permissionCacheByRole(roleId, permission);
                    log.info("Cached permissions by role: {}", role.getName());
                }
            }

            List<Long> menuIds = permission.stream()
                    .map(PermissionEntity::getMenuId)
                    .toList();
            List<String> menuNames = permission.stream()
                    .map(PermissionEntity::getMenuName)
                    .toList();
            List<PermissionResponseDto.MenuPermissionType> menuPermissionTypes = permission.stream()
                    .map(mapping -> new PermissionResponseDto.MenuPermissionType(
                            mapping.getMenuId(),
                            normalizePermissionType(mapping.getPermissionType())
                    ))
                    .toList();

            var response = new PermissionResponseDto(
                    roleId,
                    role.getName(),
                    role.getCode(),
                    menuIds,
                    menuNames,
                    menuPermissionTypes,
                    permission.isEmpty() ? null : permission.get(0).getCreatedAt(),
                    permission.isEmpty() ? null : permission.get(0).getUpdatedAt()
            );

            log.info("Successfully retrieved permissions by role: {}, menuIds: {}", role.getName(), menuIds);
            return response;
        } catch (Exception e) {
            log.error("Error retrieving permissions by role ID: {}", roleId, e);
            throw new RuntimeException("Failed to retrieve permissions: " + e.getMessage(), e);
        }
    }

    @Transactional
    public boolean updatePermissions(PermissionRequestDto request) {
        log.info("Updating permissions by role ID: {}", request.getRoleId());
        try {
            RoleEntity role = roleMapper.findById(request.getRoleId());
            if (role == null) {
                log.warn("Role not found by ID: {}", request.getRoleId());
                throw new IllegalArgumentException("Role not found");
            }

            if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
                var invalidMenus = request.getMenuIds().stream()
                        .filter(menuId -> menuMapper.findById(menuId) == null)
                        .toList();
                if (!invalidMenus.isEmpty()) {
                    log.warn("Menus not found by IDs: {}", invalidMenus);
                    throw new IllegalArgumentException("Menu not found with ID: " + invalidMenus.get(0));
                }
            }

            permissionMapper.deleteByRoleId(request.getRoleId());

            LocalDateTime now = LocalDateTime.now();

            if (request.getPermissions() != null && !request.getPermissions().isEmpty()) {
                List<PermissionEntity> mappings = request.getPermissions().stream()
                        .map(permissionItem -> {
                            PermissionEntity mapping = new PermissionEntity();
                            mapping.setRoleId(request.getRoleId());
                            mapping.setMenuId(permissionItem.getMenuId());
                            mapping.setPermissionType(permissionItem.getPermissionType());
                            mapping.setCreatedAt(now);
                            mapping.setUpdatedAt(now);
                            return mapping;
                        })
                        .toList();
                permissionMapper.batchInsert(mappings);
            } else if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
                List<PermissionEntity> mappings = request.getMenuIds().stream()
                        .map(menuId -> {
                            PermissionEntity mapping = new PermissionEntity();
                            mapping.setRoleId(request.getRoleId());
                            mapping.setMenuId(menuId);
                            mapping.setPermissionType("view");
                            mapping.setCreatedAt(now);
                            mapping.setUpdatedAt(now);
                            return mapping;
                        })
                        .toList();
                permissionMapper.batchInsert(mappings);
            }

            clearPermissionCaches(request.getRoleId());

            log.info("Successfully updated permission mapping for role: {}", role.getName());
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid permission mapping data: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating permission mapping for role ID: {}", request.getRoleId(), e);
            throw new RuntimeException("Failed to update permission mapping: " + e.getMessage(), e);
        }
    }

    private void clearPermissionCaches(Long roleId) {
        if (cacheService.isRedisAvailable()) {
            permissionCacheService.clearPermissionsCacheByRole(roleId);
            menuCacheService.clearAllMenuCache();
            log.info("Cleared permission and menu caches for role: {}", roleId);
        }
    }

    @Transactional
    public boolean deletePermissionMapping(Long roleId) {
        log.info("Deleting permission mapping for role ID: {}", roleId);
        try {
            RoleEntity role = roleMapper.findById(roleId);
            if (role == null) {
                log.warn("Role not found with ID: {}", roleId);
                throw new IllegalArgumentException("Role not found");
            }

            int deleted = permissionMapper.deleteByRoleId(roleId);
            log.info("Successfully deleted {} permission mappings for role: {}", deleted, role.getName());

            clearPermissionCaches(roleId);

            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role ID for deletion: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting permission mapping for role ID: {}", roleId, e);
            throw new RuntimeException("Failed to delete permission mapping: " + e.getMessage(), e);
        }
    }

    private String normalizePermissionType(String permissionType) {
        if (permissionType == null || permissionType.trim().isEmpty() || "null".equalsIgnoreCase(permissionType)) {
            return "view";
        }
        return switch (permissionType.toLowerCase()) {
            case "view", "edit", "all" -> permissionType.toLowerCase();
            default -> {
                log.warn("Invalid permission type: {}, defaulting to 'view'", permissionType);
                yield "view";
            }
        };
    }
}
