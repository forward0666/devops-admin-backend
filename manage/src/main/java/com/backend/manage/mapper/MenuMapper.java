package com.backend.manage.mapper;

import com.backend.manage.entity.MenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单数据访问接口 - 使用MyBatis XML配置进行菜单相关数据库操作
 * 提供菜单的CRUD操作、树形结构查询等功能
 */
@Mapper
public interface MenuMapper {

    /**
     * 根据ID查找菜单
     * @param id 菜单ID
     * @return 菜单对象
     */
    MenuEntity findById(@Param("id") Long id);

    /**
     * 查找所有菜单
     * @return 菜单列表
     */
    List<MenuEntity> findAll();

    /**
     * 根据父菜单ID查找子菜单
     * @param parentId 父菜单ID
     * @return 子菜单列表
     */
    List<MenuEntity> findByParentId(@Param("parentId") Long parentId);

    /**
     * 插入新菜单
     * @param MenuEntity 菜单对象
     * @return 插入记录数
     */
    int insert(MenuEntity MenuEntity);

    /**
     * 更新现有菜单
     * @param MenuEntity 菜单对象
     * @return 更新记录数
     */
    int update(MenuEntity MenuEntity);

    /**
     * 根据ID删除菜单
     * @param id 菜单ID
     * @return 删除记录数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 检查菜单是否存在
     * @param id 菜单ID
     * @return 存在返回1，不存在返回0
     */
    int existsById(@Param("id") Long id);

    /**
     * 统计子菜单数量
     * @param parentId 父菜单ID
     * @return 子菜单数量
     */
    int countByParentId(@Param("parentId") Long parentId);

    /**
     * 查找所有根菜单（顶级菜单）
     * @return 根菜单列表
     */
    List<MenuEntity> findRootMenus();
}
