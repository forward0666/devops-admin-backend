package com.backend.manage.service;

import com.backend.manage.dto.PermissionRequestDto;
import com.backend.manage.dto.PermissionResponseDto;
import com.backend.manage.entity.MenuEntity;
import com.backend.manage.entity.PermissionMappingEntity;
import com.backend.manage.entity.RoleEntity;
import com.backend.manage.mapper.MenuMapper;
import com.backend.manage.mapper.PermissionMapper;
import com.backend.manage.mapper.RoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限管理服务
 * 处理角色与菜单的权限映射关系
 * 使用 Java 21 风格
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Slf4j
@Service
public class PermissionService {

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private MenuMapper menuMapper;

    /**
     * 获取所有权限映射
     * @return 权限映射响应列表
     */
    public List<PermissionResponseDto> getAllPermissionMappings() {
        log.info("Fetching all permission mappings");
        try {
            List<PermissionMappingEntity> mappings = permissionMapper.findAllWithDetails();

            // Java 21: 使用 Collectors.groupingBy 和 record pattern
            Map<Long, List<PermissionMappingEntity>> groupedByRole = mappings.stream()
                    .collect(Collectors.groupingBy(PermissionMappingEntity::getRoleId));

            var responses = groupedByRole.entrySet().stream()
                    .filter(entry -> !entry.getValue().isEmpty())
                    .map(entry -> {
                        var roleMappings = entry.getValue();
                        var firstMapping = roleMappings.get(0);

                        // 使用 record 构造器创建不可变对象
                        return new PermissionResponseDto(
                                firstMapping.getRoleId(),
                                firstMapping.getRoleName(),
                                firstMapping.getRoleCode(),
                                roleMappings.stream()
                                        .map(PermissionMappingEntity::getMenuId)
                                        .toList(),
                                roleMappings.stream()
                                        .map(PermissionMappingEntity::getMenuName)
                                        .toList(),
                                roleMappings.stream()
                                        .map(mapping -> new PermissionResponseDto.MenuPermissionType(
                                                mapping.getMenuId(),
                                                mapping.getPermissionType() != null ? mapping.getPermissionType() : "view"
                                        ))
                                        .toList(),
                                firstMapping.getCreatedAt(),
                                firstMapping.getUpdatedAt()
                        );
                    })
                    .toList();

            log.info("Successfully retrieved {} permission mappings", responses.size());
            return responses;
        } catch (Exception e) {
            log.error("Error retrieving permission mappings", e);
            throw new RuntimeException("Failed to retrieve permission mappings: " + e.getMessage(), e);
        }
    }

    /**
     * 根据角色ID获取权限映射
     * @param roleId 角色ID
     * @return 权限映射响应
     */
    public PermissionResponseDto getPermissionMappingByRoleId(Long roleId) {
        log.info("Fetching permission mapping for role ID: {}", roleId);
        try {
            // 验证角色存在
            var role = roleMapper.findById(roleId);
            if (role == null) {
                log.warn("Role not found with ID: {}", roleId);
                return new PermissionResponseDto(roleId, null, null, List.of(), List.of(), List.of(), null, null);
            }

            List<PermissionMappingEntity> mappings = permissionMapper.findByRoleIdWithMenus(roleId);
            List<Long> menuIds = mappings.stream()
                    .map(PermissionMappingEntity::getMenuId)
                    .toList();
            List<String> menuNames = mappings.stream()
                    .map(PermissionMappingEntity::getMenuName)
                    .toList();
            List<PermissionResponseDto.MenuPermissionType> menuPermissionTypes = mappings.stream()
                    .map(mapping -> new PermissionResponseDto.MenuPermissionType(
                            mapping.getMenuId(),
                            mapping.getPermissionType() != null ? mapping.getPermissionType() : "view"
                    ))
                    .toList();

            // 使用 record 构造器创建不可变对象
            var response = new PermissionResponseDto(
                    roleId,
                    role.getName(),
                    role.getCode(),
                    menuIds,
                    menuNames,
                    menuPermissionTypes,
                    mappings.isEmpty() ? null : mappings.get(0).getCreatedAt(),
                    mappings.isEmpty() ? null : mappings.get(0).getUpdatedAt()
            );

            log.info("Successfully retrieved permission mapping for role: {}, menuIds: {}", role.getName(), menuIds);
            return response;
        } catch (Exception e) {
            log.error("Error retrieving permission mapping for role ID: {}", roleId, e);
            throw new RuntimeException("Failed to retrieve permission mapping: " + e.getMessage(), e);
        }
    }

    /**
     * 更新角色的权限映射
     * @param request 权限请求
     * @return 是否成功
     */
    @Transactional
    public boolean updatePermissionMapping(PermissionRequestDto request) {
        log.info("Updating permission mapping for role ID: {}", request.getRoleId());
        try {
            // 验证角色存在
            RoleEntity role = roleMapper.findById(request.getRoleId());
            if (role == null) {
                log.warn("Role not found with ID: {}", request.getRoleId());
                throw new IllegalArgumentException("Role not found");
            }

            // 验证菜单存在 - Java 21: 使用 stream 简化
            if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
                var invalidMenus = request.getMenuIds().stream()
                        .filter(menuId -> menuMapper.findById(menuId) == null)
                        .toList();
                if (!invalidMenus.isEmpty()) {
                    log.warn("Menus not found with IDs: {}", invalidMenus);
                    throw new IllegalArgumentException("Menu not found with ID: " + invalidMenus.get(0));
                }
            }

            // 删除旧的权限映射
            permissionMapper.deleteByRoleId(request.getRoleId());

            // 插入新的权限映射 - Java 21: 使用 stream 创建列表
            LocalDateTime now = LocalDateTime.now();

            if (request.getPermissions() != null && !request.getPermissions().isEmpty()) {
                // 使用 permissions 列表（包含 menuId 和 permissionType）
                List<PermissionMappingEntity> mappings = request.getPermissions().stream()
                        .map(permissionItem -> {
                            PermissionMappingEntity mapping = new PermissionMappingEntity();
                            mapping.setRoleId(request.getRoleId());
                            mapping.setMenuId(permissionItem.getMenuId());
                            mapping.setPermissionType(permissionItem.getPermissionType() != null ? permissionItem.getPermissionType() : "view");
                            mapping.setCreatedAt(now);
                            mapping.setUpdatedAt(now);
                            return mapping;
                        })
                        .toList();
                permissionMapper.batchInsert(mappings);
            } else if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
                // 使用 menuIds 列表（默认权限为 view）
                List<PermissionMappingEntity> mappings = request.getMenuIds().stream()
                        .map(menuId -> {
                            PermissionMappingEntity mapping = new PermissionMappingEntity();
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

    /**
     * 删除角色的权限映射
     * @param roleId 角色ID
     * @return 是否成功
     */
    @Transactional
    public boolean deletePermissionMapping(Long roleId) {
        log.info("Deleting permission mapping for role ID: {}", roleId);
        try {
            // 验证角色存在
            RoleEntity role = roleMapper.findById(roleId);
            if (role == null) {
                log.warn("Role not found with ID: {}", roleId);
                throw new IllegalArgumentException("Role not found");
            }

            int deleted = permissionMapper.deleteByRoleId(roleId);
            log.info("Successfully deleted {} permission mappings for role: {}", deleted, role.getName());
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role ID for deletion: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting permission mapping for role ID: {}", roleId, e);
            throw new RuntimeException("Failed to delete permission mapping: " + e.getMessage(), e);
        }
    }
}
