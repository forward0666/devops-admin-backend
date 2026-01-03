package com.backend.manage.mapper.system;

import com.backend.manage.entity.system.DepartmentEntity;
import com.backend.manage.entity.system.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门数据访问接口 - 使用MyBatis XML配置进行部门相关数据库操作
 *
 * 中文注释：这个Mapper接口定义了所有与部门相关的数据库操作方法
 * 使用@Mapper注解标记为MyBatis的Mapper接口，方法对应的SQL在XML文件中配置
 * 提供了部门的CRUD操作、查询统计、关联用户查询等功能
 */
@Mapper
public interface DepartmentMapper {

    /**
     * 根据ID查找部门 - 通过部门ID查询部门详细信息
     * @param id 部门ID
     * @return 部门对象，包含所有部门信息
     */
    DepartmentEntity findById(@Param("id") Long id);

    /**
     * 查找所有部门 - 查询系统中所有的部门列表
     * @return 部门列表，按创建时间排序
     */
    List<DepartmentEntity> findAll();

    /**
     * 根据名称查找部门 - 通过部门名称精确查询部门
     * @param name 部门名称
     * @return 部门对象，如果存在则返回，否则返回null
     */
    DepartmentEntity findByName(@Param("name") String name);

    /**
     * 根据父部门ID查找子部门 - 查询指定父部门下的所有子部门
     * @param parentId 父部门ID
     * @return 子部门列表，用于构建部门树形结构
     */
    List<DepartmentEntity> findByParentId(@Param("parentId") Long parentId);

    /**
     * 根据管理员ID查找部门 - 查询指定管理员管理的部门
     * @param managerId 管理员用户ID
     * @return 部门对象，一个管理员只能管理一个部门
     */
    DepartmentEntity findByManagerId(@Param("managerId") Long managerId);

    /**
     * 插入新部门 - 创建新的部门记录
     * @param department 部门对象，包含部门所有信息
     * @return 插入记录数，成功返回1
     */
    int insert(DepartmentEntity department);

    /**
     * 更新现有部门 - 修改部门信息
     * @param department 部门对象，包含要更新的字段
     * @return 更新记录数，成功返回1
     */
    int update(DepartmentEntity department);

    /**
     * 根据ID删除部门（软删除） - 将部门标记为删除状态，不实际删除数据
     * @param id 部门ID
     * @return 删除记录数，成功返回1
     */
    int deleteById(@Param("id") Long id);

    /**
     * 检查部门是否存在 - 根据ID检查部门记录是否存在
     * @param id 部门ID
     * @return 存在返回1，不存在返回0
     */
    int existsById(@Param("id") Long id);

    /**
     * 检查部门名称是否存在 - 检查指定名称的部门是否已存在
     * @param name 部门名称
     * @return 存在返回1，不存在返回0
     */
    int existsByName(@Param("name") String name);

    /**
     * 检查部门名称是否存在（排除指定ID） - 用于更新时检查名称冲突
     * @param name 部门名称
     * @param id 要排除的部门ID
     * @return 存在返回1，不存在返回0
     */
    int existsByNameExcludingId(@Param("name") String name, @Param("id") Long id);

    /**
     * 统计子部门数量 - 统计指定父部门下的子部门数量
     * @param parentId 父部门ID
     * @return 子部门数量
     */
    int countByParentId(@Param("parentId") Long parentId);

    /**
     * 根据部门ID查找用户 - 查询属于指定部门的所有用户
     * @param departmentId 部门ID
     * @return 用户列表，包含用户基本信息
     */
    List<UserEntity> findUsersByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * 查找部门最近用户（最近10个） - 查询部门中最近加入或活动的用户
     * @param departmentId 部门ID
     * @return 最近用户列表，最多10个
     */
    List<UserEntity> findRecentUsersByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * 更新部门用户数量 - 根据实际用户数量更新部门的统计信息
     * @param departmentId 部门ID
     * @return 更新记录数
     */
    int updateUserCount(@Param("departmentId") Long departmentId);
}
