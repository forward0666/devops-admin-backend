package com.backend.manage.service;

import com.backend.manage.dto.PermissionRequestDto;
import com.backend.manage.dto.PermissionResponseDto;
import com.backend.manage.entity.MenuEntity;
import com.backend.manage.entity.PermissionMappingEntity;
import com.backend.manage.entity.RoleEntity;
import com.backend.manage.mapper.MenuMapper;
import com.backend.manage.mapper.PermissionMapper;
import com.backend.manage.mapper.RoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Service
public class PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionService.class);

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
        logger.info("Fetching all permission mappings");
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
                                firstMapping.getCreatedAt(),
                                firstMapping.getUpdatedAt()
                        );
                    })
                    .toList();

            logger.info("Successfully retrieved {} permission mappings", responses.size());
            return responses;
        } catch (Exception e) {
            logger.error("Error retrieving permission mappings", e);
            throw new RuntimeException("Failed to retrieve permission mappings: " + e.getMessage(), e);
        }
    }

    /**
     * 根据角色ID获取权限映射
     * @param roleId 角色ID
     * @return 权限映射响应
     */
    public PermissionResponseDto getPermissionMappingByRoleId(Long roleId) {
        logger.info("Fetching permission mapping for role ID: {}", roleId);
        try {
            // 验证角色存在
            var role = roleMapper.findById(roleId);
            if (role == null) {
                logger.warn("Role not found with ID: {}", roleId);
                return new PermissionResponseDto(roleId, null, null, List.of(), List.of(), null, null);
            }

            List<PermissionMappingEntity> mappings = permissionMapper.findByRoleIdWithMenus(roleId);
            List<Long> menuIds = mappings.stream()
                    .map(PermissionMappingEntity::getMenuId)
                    .toList();
            List<String> menuNames = mappings.stream()
                    .map(PermissionMappingEntity::getMenuName)
                    .toList();

            // 使用 record 构造器创建不可变对象
            var response = new PermissionResponseDto(
                    roleId,
                    role.getName(),
                    role.getCode(),
                    menuIds,
                    menuNames,
                    mappings.isEmpty() ? null : mappings.get(0).getCreatedAt(),
                    mappings.isEmpty() ? null : mappings.get(0).getUpdatedAt()
            );

            logger.info("Successfully retrieved permission mapping for role: {}", role.getName());
            return response;
        } catch (Exception e) {
            logger.error("Error retrieving permission mapping for role ID: {}", roleId, e);
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
        logger.info("Updating permission mapping for role ID: {}", request.getRoleId());
        try {
            // 验证角色存在
            RoleEntity role = roleMapper.findById(request.getRoleId());
            if (role == null) {
                logger.warn("Role not found with ID: {}", request.getRoleId());
                throw new IllegalArgumentException("Role not found");
            }

            // 验证菜单存在 - Java 21: 使用 stream 简化
            if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
                var invalidMenus = request.getMenuIds().stream()
                        .filter(menuId -> menuMapper.findById(menuId) == null)
                        .toList();
                if (!invalidMenus.isEmpty()) {
                    logger.warn("Menus not found with IDs: {}", invalidMenus);
                    throw new IllegalArgumentException("Menu not found with ID: " + invalidMenus.get(0));
                }
            }

            // 删除旧的权限映射
            permissionMapper.deleteByRoleId(request.getRoleId());

            // 插入新的权限映射 - Java 21: 使用 stream 创建列表
            if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                List<PermissionMappingEntity> mappings = request.getMenuIds().stream()
                        .map(menuId -> {
                            PermissionMappingEntity mapping = new PermissionMappingEntity();
                            mapping.setRoleId(request.getRoleId());
                            mapping.setMenuId(menuId);
                            mapping.setCreatedAt(now);
                            mapping.setUpdatedAt(now);
                            return mapping;
                        })
                        .toList();
                permissionMapper.batchInsert(mappings);
            }

            logger.info("Successfully updated permission mapping for role: {}", role.getName());
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid permission mapping data: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error updating permission mapping for role ID: {}", request.getRoleId(), e);
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
        logger.info("Deleting permission mapping for role ID: {}", roleId);
        try {
            // 验证角色存在
            RoleEntity role = roleMapper.findById(roleId);
            if (role == null) {
                logger.warn("Role not found with ID: {}", roleId);
                throw new IllegalArgumentException("Role not found");
            }

            int deleted = permissionMapper.deleteByRoleId(roleId);
            logger.info("Successfully deleted {} permission mappings for role: {}", deleted, role.getName());
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid role ID for deletion: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error deleting permission mapping for role ID: {}", roleId, e);
            throw new RuntimeException("Failed to delete permission mapping: " + e.getMessage(), e);
        }
    }
}
