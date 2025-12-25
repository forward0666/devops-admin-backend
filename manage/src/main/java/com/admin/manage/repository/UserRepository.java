package com.admin.manage.repository;

import com.admin.manage.model.User;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层接口 - User Repository
 * 
 * 提供用户相关的数据库操作接口，包括用户认证、查询、保存、删除等功能
 * 使用Optional包装返回结果，避免空指针异常
 * 
 * Repository interface for User entity
 */
public interface UserRepository {
    
    /**
     * 根据用户名查找用户
     * 用于用户登录、用户信息查询等场景
     * 
     * @param username 要搜索的用户名
     * @return 包含用户的Optional对象，如果未找到则为空
     * 
     * Find a user by username
     * 
     * @param username the username to search for
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 用户认证 - 验证用户名和密码
     * 用于登录验证，返回认证成功的用户信息
     * 
     * @param username 用户名
     * @param password 密码（已加密）
     * @return 认证成功的用户Optional对象，认证失败返回空
     * 
     * Authenticate a user with username and password
     * 
     * @param username the username
     * @param password the password
     * @return an Optional containing the authenticated user if successful, or empty if authentication fails
     */
    Optional<User> authenticate(String username, String password);
    
    /**
     * 查找所有用户
     * 用于用户管理列表展示，返回系统中所有用户
     * 
     * @return 所有用户的列表
     * 
     * Find all users
     * 
     * @return List of all users
     */
    List<User> findAll();
    
    /**
     * 根据用户ID查找用户（仅查找活跃用户）
     * 用于用户信息查看、编辑等操作，不包含已删除或禁用的用户
     * 
     * @param id 用户ID
     * @return 包含用户的Optional对象，如果未找到则为空
     * 
     * Find a user by ID (active users only)
     * 
     * @param id the user ID
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findById(Long id);
    
    /**
     * 根据用户ID查找用户（包含非活跃用户）
     * 用于用户更新操作，可以查找已删除或禁用的用户
     * 
     * @param id 用户ID
     * @return 包含用户的Optional对象，如果未找到则为空
     * 
     * Find a user by ID including inactive users (for update operations)
     * 
     * @param id the user ID
     * @return an Optional containing the user if found, or empty if not found
     */
    Optional<User> findByIdIncludeInactive(Long id);
    
    /**
     * 保存用户（创建或更新）
     * 根据用户ID判断是新增还是更新操作，返回保存后的用户对象
     * 
     * @param user 要保存的用户对象
     * @return 保存后的用户对象
     * 
     * Save a user (create or update)
     * 
     * @param user the user to save
     * @return the saved user
     */
    User save(User user);
    
    /**
     * 根据用户ID删除用户
     * 执行用户删除操作，通常为逻辑删除而非物理删除
     * 
     * @param id 要删除的用户ID
     * 
     * Delete a user by ID
     * 
     * @param id the user ID to delete
     */
    void deleteById(Long id);
    
    /**
     * 根据部门ID查找用户
     * 用于部门管理，查看某个部门下的所有用户
     * 
     * @param departmentId 部门ID
     * @return 该部门下的用户列表
     * 
     * Find users by department ID
     * 
     * @param departmentId the department ID
     * @return List of users in the department
     */
    List<User> findByDepartmentId(Long departmentId);
    
    /**
     * 搜索用户 - 根据用户名、邮箱、手机号或Telegram用户名
     * 用于用户管理中的搜索功能，支持多字段模糊搜索
     * 
     * @param query 搜索关键词
     * @return 匹配的用户列表
     * 
     * Search users by username, email, phone, or Telegram username
     * 
     * @param query the search query
     * @return List of matching users
     */
    List<User> searchByUsernameOrEmail(String query);
    
    /**
     * 检查邮箱是否已存在
     * 用于用户注册或修改邮箱时的重复性验证
     * 
     * @param email 要检查的邮箱地址
     * @return true表示邮箱已存在，false表示不存在
     * 
     * Check if email exists
     * 
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    boolean existsByEmail(String email);
    
    /**
     * 检查手机号是否已存在
     * 用于用户注册或修改手机号时的重复性验证
     * 
     * @param phone 要检查的手机号码
     * @return true表示手机号已存在，false表示不存在
     * 
     * Check if phone exists
     * 
     * @param phone the phone to check
     * @return true if phone exists, false otherwise
     */
    boolean existsByPhone(String phone);
    
    /**
     * 检查Telegram用户名是否已存在
     * 用于用户绑定Telegram账号时的重复性验证
     * 
     * @param tgUsername 要检查的Telegram用户名
     * @return true表示用户名已存在，false表示不存在
     * 
     * Check if Telegram username exists
     * 
     * @param tgUsername the Telegram username to check
     * @return true if Telegram username exists, false otherwise
     */
    boolean existsByTgUsername(String tgUsername);
    
    /**
     * 检查员工ID是否已存在
     * 用于员工信息管理，确保员工ID的唯一性
     * 
     * @param employeeId 要检查的员工ID
     * @return true表示员工ID已存在，false表示不存在
     * 
     * Check if employee ID exists
     * 
     * @param employeeId the employee ID to check
     * @return true if employee ID exists, false otherwise
     */
    boolean existsByEmployeeId(String employeeId);
}
