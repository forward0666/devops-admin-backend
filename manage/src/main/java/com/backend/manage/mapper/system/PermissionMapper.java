package com.backend.manage.mapper.system;

import com.backend.manage.entity.system.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限映射数据访问接口
 * 提供角色菜单权限的CRUD操作
 */
@Mapper
public interface PermissionMapper {

    /**
     * 根据ID查找权限映射
     * @param id 映射ID
     * @return 权限映射对象
     */
    PermissionEntity findById(@Param("id") Long id);

    /**
     * 根据角色ID查找所有权限映射
     * @param roleId 角色ID
     * @return 权限映射列表
     */
    List<PermissionEntity> findByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据菜单ID查找所有权限映射
     * @param menuId 菜单ID
     * @return 权限映射列表
     */
    List<PermissionEntity> findByMenuId(@Param("menuId") Long menuId);

    /**
     * 查找所有权限映射（包含角色和菜单信息）
     * @return 权限映射列表
     */
    List<PermissionEntity> findAllWithDetails();

    /**
     * 根据角色ID查找所有权限映射（包含菜单信息）
     * @param roleId 角色ID
     * @return 权限映射列表
     */
    List<PermissionEntity> findByRoleIdWithMenus(@Param("roleId") Long roleId);

    /**
     * 插入权限映射
     * @param mapping 权限映射对象
     * @return 插入记录数
     */
    int insert(PermissionEntity mapping);

    /**
     * 批量插入权限映射
     * @param mappings 权限映射列表
     * @return 插入记录数
     */
    int batchInsert(@Param("mappings") List<PermissionEntity> mappings);

    /**
     * 根据ID删除权限映射
     * @param id 映射ID
     * @return 删除记录数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据角色ID删除所有权限映射
     * @param roleId 角色ID
     * @return 删除记录数
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据菜单ID删除所有权限映射
     * @param menuId 菜单ID
     * @return 删除记录数
     */
    int deleteByMenuId(@Param("menuId") Long menuId);

    /**
     * 统计角色的权限数量
     * @param roleId 角色ID
     * @return 权限数量
     */
    int countByRoleId(@Param("roleId") Long roleId);
}
