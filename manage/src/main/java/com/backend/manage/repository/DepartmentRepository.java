package com.backend.manage.repository;

import com.backend.manage.entity.DepartmentEntity;
import com.backend.manage.entity.UserEntity;
import java.util.List;
import java.util.Optional;

/**
 * 部门数据访问层接口 - Department Repository
 *
 * 提供部门相关的数据库操作接口，包括部门管理、用户统计、项目统计等功能
 * 支持部门的增删改查以及相关的统计信息更新
 *
 * Repository interface for Department entity
 */
public interface DepartmentRepository {

    /**
     * 查找所有部门
     * 用于部门管理列表展示，返回系统中所有部门信息
     *
     * @return 所有部门的列表
     *
     * Find all departments
     *
     * @return list of all departments
     */
    List<DepartmentEntity> findAll();

    /**
     * 根据部门ID查找部门
     * 用于部门信息查看、编辑等操作
     *
     * @param id 部门ID
     * @return 包含部门的Optional对象，如果未找到则为空
     *
     * Find a department by ID
     *
     * @param id
     * @return an Optional containing the department if found, or empty if not found
     */
    Optional<DepartmentEntity> findById(Long id);

    /**
     * 根据部门名称查找部门
     * 用于部门名称重复性验证和部门搜索
     *
     * @param name 部门名称
     * @return 包含部门的Optional对象，如果未找到则为空
     *
     * Find a department by name
     *
     * @param name
     * @return an Optional containing the department if found, or empty if not found
     */
    Optional<DepartmentEntity> findByName(String name);

    /**
     * 保存新部门（创建）
     * 用于新增部门，生成部门ID并保存到数据库
     *
     * @param department 要保存的部门对象
     * @return 保存后的部门对象（包含生成的ID）
     *
     * Save a new department
     *
     * @param department
     * @return
     */
    DepartmentEntity save(DepartmentEntity department);

    /**
     * 更新现有部门
     * 用于修改部门信息，更新部门名称、描述等信息
     *
     * @param department 要更新的部门对象
     * @return 更新后的部门对象
     *
     * Update an existing department
     *
     * @param department
     * @return
     */
    DepartmentEntity update(DepartmentEntity department);

    /**
     * 根据部门ID删除部门
     * 执行部门删除操作，通常需要先检查部门下是否有用户
     *
     * @param id 部门ID
     * @return true表示删除成功，false表示删除失败
     *
     * Delete a department by ID
     *
     * @param id
     * @return true if deleted successfully, false otherwise
     */
    boolean deleteById(Long id);

    /**
     * 检查部门是否存在（根据ID）
     * 用于验证部门ID的有效性
     *
     * @param id 部门ID
     * @return true表示部门存在，false表示不存在
     *
     * Check if a department exists by ID
     *
     * @param id
     * @return true if exists, false otherwise
     */
    boolean existsById(Long id);

    /**
     * 检查部门名称是否已存在
     * 用于部门创建或重命名时的重复性验证
     *
     * @param name 部门名称
     * @return true表示名称已存在，false表示不存在
     *
     * Check if a department name already exists
     *
     * @param name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);

    /**
     * 获取部门下的所有用户
     * 用于部门管理，查看某个部门的所有成员
     *
     * @param departmentId 部门ID
     * @return 该部门下的用户列表
     *
     * Get all users in a department
     *
     * @param departmentId
     * @return list of users in the department
     */
    List<UserEntity> findUsersByDepartmentId(Long departmentId);

    /**
     * 获取部门下的最近用户（最近10个）
     * 用于部门概览页面，显示最近加入部门的用户
     *
     * @param departmentId 部门ID
     * @return 该部门下的最近用户列表
     *
     * Get recent users in a department (last 10)
     *
     * @param departmentId
     * @return list of recent users in the department
     */
    List<UserEntity> findRecentUsersByDepartmentId(Long departmentId);

    /**
     * 更新部门的用户数量统计
     * 当部门用户变动时，自动更新部门的用户计数
     *
     * @param departmentId 部门ID
     *
     * Update user count for a department
     *
     * @param departmentId
     */
    void updateUserCount(Long departmentId);

    /**
     * 更新部门的项目数量统计
     * 更新部门的活跃项目和已完成项目数量
     *
     * @param departmentId 部门ID
     * @param activeProjects 活跃项目数量
     * @param completedProjects 已完成项目数量
     *
     * Update project counts for a department
     *
     * @param departmentId
     * @param activeProjects
     * @param completedProjects
     */
}
