package com.backend.user.mapper.system;

import com.backend.user.entity.system.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis mapper for User operations using XML configuration
 */
@Mapper
public interface UserMapper {

    /**
     * Find user by username
     */
    UserEntity findByUsername(@Param("username") String username);

    /**
     * Find user by ID
     */
    UserEntity findById(@Param("id") Long id);

    /**
     * Find user by ID including inactive users (for update operations)
     */
    UserEntity findByIdIncludeInactive(@Param("id") Long id);

    /**
     * Find all users
     */
    List<UserEntity> findAll();

    /**
     * Authenticate user with username and password
     */
    UserEntity authenticate(@Param("username") String username, @Param("password") String password);

    /**
     * Find users by department ID
     */
    List<UserEntity> findByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * Insert new user
     */
    int insert(UserEntity user);

    /**
     * Update existing user
     */
    int update(UserEntity user);

    /**
     * Update user password
     */
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * Update user login information
     */
    int updateLoginInfo(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt, @Param("lastLoginIp") String lastLoginIp);

    /**
     * Delete user by ID
     */
    int deleteById(@Param("id") Long id);

    /**
     * Check if user exists by ID
     */
    int existsById(@Param("id") Long id);

    /**
     * Check if username exists
     */
    int existsByUsername(@Param("username") String username);

    /**
     * Check if email exists
     */
    int existsByEmail(@Param("email") String email);

    /**
     * Check if phone exists
     */
    int existsByPhone(@Param("phone") String phone);

    /**
     * Check if Telegram username exists
     */
    int existsByTgUsername(@Param("tgUsername") String tgUsername);

    /**
     * Check if employee ID exists
     */
    int existsByEmployeeId(@Param("employeeId") String employeeId);

    /**
     * Count users by department ID
     */
    int countByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * Search users by username, email, phone, or Telegram username
     */
    List<UserEntity> searchByUsernameOrEmail(@Param("query") String query);
}
