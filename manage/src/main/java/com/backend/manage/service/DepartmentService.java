package com.backend.manage.service;

import com.backend.manage.model.Department;
import com.backend.manage.model.User;
import com.backend.manage.repository.DepartmentRepository;
import com.backend.manage.repository.impl.DepartmentRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 部门服务类 - 处理所有部门相关的业务逻辑
 * 包括部门CRUD操作、用户管理、统计信息、缓存管理等
 * 支持部门层级结构管理和项目统计功能
 * 
 * @author System
 * @version 1.0
 */
@Slf4j
@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepository;  // 部门数据访问层
    
    @Autowired
    private CacheService cacheService;                   // 缓存服务，用于Redis缓存管理
    
    /**
     * 获取所有部门列表及其统计信息
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * 自动填充每个部门的用户信息和最近用户列表
     * 
     * @return 所有部门的列表，包含完整的统计信息和用户数据
     */
    public List<Department> getAllDepartments() {
        log.info("正在获取所有部门列表");
        
        // 优先从Redis缓存获取部门列表，提高性能
        if (cacheService.isRedisAvailable()) {
            List<Department> cachedDepartments = cacheService.getCachedDepartmentsList();
            if (cachedDepartments != null) {
                log.debug("从缓存中获取部门列表成功");
                return cachedDepartments;
            }
        }
        
        try {
            // 从数据库查询所有部门
            List<Department> departments = departmentRepository.findAll();
            
            // 为每个部门填充用户信息和最近用户列表
            for (Department department : departments) {
                populateDepartmentUsers(department);
            }
            
            // 将查询结果缓存到Redis中，提高后续查询性能
            if (cacheService.isRedisAvailable()) {
                cacheService.cacheDepartmentsList(departments);
            }
            
            log.info("成功获取 {} 个部门", departments.size());
            return departments;
        } catch (Exception e) {
            log.error("获取所有部门时发生错误", e);
            throw new RuntimeException("获取部门列表失败", e);
        }
    }

    /**
     * 根据部门ID获取部门详细信息
     * 优先从Redis缓存获取，缓存不存在时从数据库查询并缓存结果
     * 包含完整的用户信息和统计数据
     * 
     * @param id 部门ID
     * @return 包含完整详细信息的部门对象，如果不存在则返回null
     */
    public Department getDepartmentById(Long id) {
        log.info("正在根据ID获取部门: {}", id);
        
        if (id == null) {
            log.warn("部门ID不能为空");
            return null;
        }
        
        // 优先从Redis缓存获取部门信息
        if (cacheService.isRedisAvailable()) {
            Department cachedDepartment = cacheService.getCachedDepartment(id);
            if (cachedDepartment != null) {
                log.debug("从缓存中获取部门成功: {}", id);
                return cachedDepartment;
            }
        }
        
        try {
            // 从数据库查询部门信息
            Optional<Department> departmentOpt = departmentRepository.findById(id);
            
            if (departmentOpt.isPresent()) {
                Department department = departmentOpt.get();
                populateDepartmentUsers(department);  // 填充用户信息
                
                // 将查询结果缓存到Redis中
                if (cacheService.isRedisAvailable()) {
                    cacheService.cacheDepartment(department);
                }
                
                log.info("成功获取部门: {}", department.getName());
                return department;
            } else {
                log.warn("找不到ID为 {} 的部门", id);
                return null;
            }
        } catch (Exception e) {
            log.error("根据ID获取部门时发生错误: {}", id, e);
            throw new RuntimeException("获取部门信息失败", e);
        }
    }

    /**
     * 创建新部门
     * 执行部门名称唯一性验证，设置默认值，并清除相关缓存
     * 
     * @param department 要创建的部门对象
     * @return 创建成功的部门对象
     * @throws IllegalArgumentException 当部门名称已存在或验证失败时抛出
     */
    public Department createDepartment(Department department) {
        log.info("正在创建新部门: {}", department.getName());
        
        // 验证部门数据合法性
        validateDepartmentForCreation(department);
        
        try {
            // 检查部门名称是否已存在，确保名称唯一性
            if (departmentRepository.existsByName(department.getName())) {
                throw new IllegalArgumentException("部门名称 '" + department.getName() + "' 已存在");
            }
            
            // 设置默认值，确保数据完整性
            if (department.getUserCount() == null) {
                department.setUserCount(0);  // 初始化用户数量为0
            }
            if (department.getActiveProjects() == null) {
                department.setActiveProjects(0);  // 初始化活跃项目数为0
            }
            if (department.getCompletedProjects() == null) {
                department.setCompletedProjects(0);  // 初始化完成项目数为0
            }
            
            // 保存部门到数据库
            Department createdDepartment = departmentRepository.save(department);
            
            // 创建新部门后清除相关缓存，确保数据一致性
            if (cacheService.isRedisAvailable()) {
                cacheService.clearAllDepartmentCache();
            }
            
            log.info("成功创建部门，ID: {}", createdDepartment.getId());
            return createdDepartment;
            
        } catch (Exception e) {
            log.error("创建部门时发生错误: {}", department.getName(), e);
            throw new RuntimeException("创建部门失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新现有部门信息
     * 执行部门名称唯一性验证（排除当前部门），更新后清除相关缓存
     * 
     * @param department 要更新的部门对象
     * @return 更新成功的部门对象，如果部门不存在则返回null
     * @throws IllegalArgumentException 当新部门名称与其他部门冲突时抛出
     */
    public Department updateDepartment(Department department) {
        log.info("正在更新部门，ID: {}", department.getId());
        
        // 验证部门数据合法性
        validateDepartmentForUpdate(department);
        
        try {
            // 检查部门是否存在
            if (!departmentRepository.existsById(department.getId())) {
                log.warn("找不到要更新的部门，ID: {}", department.getId());
                return null;
            }
            
            // 检查新部门名称是否与其他部门冲突（排除当前部门）
            DepartmentRepositoryImpl repoImpl = (DepartmentRepositoryImpl) departmentRepository;
            if (repoImpl.existsByNameExcludingId(department.getName(), department.getId())) {
                throw new IllegalArgumentException("部门名称 '" + department.getName() + "' 已存在");
            }
            
            // 更新部门信息
            Department updatedDepartment = departmentRepository.update(department);
            populateDepartmentUsers(updatedDepartment);  // 填充更新后的用户信息
            
            // 更新部门后清除相关缓存，确保数据一致性
            if (cacheService.isRedisAvailable()) {
                cacheService.clearDepartmentCache(department.getId());
            }
            
            log.info("成功更新部门: {}", updatedDepartment.getName());
            return updatedDepartment;
            
        } catch (Exception e) {
            log.error("更新部门时发生错误，ID: {}", department.getId(), e);
            throw new RuntimeException("更新部门失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据部门ID删除部门
     * 执行存在性检查，确保部门没有用户才能删除
     * 删除成功后清除相关缓存
     * 
     * @param id 部门ID
     * @return 如果删除成功返回true，如果部门不存在返回false
     * @throws IllegalStateException 当部门中存在用户时抛出异常
     */
    public boolean deleteDepartment(Long id) {
        log.info("正在删除部门，ID: {}", id);
        
        if (id == null) {
            log.warn("删除部门时部门ID不能为空");
            return false;
        }
        
        try {
            // 检查部门是否存在
            if (!departmentRepository.existsById(id)) {
                log.warn("找不到要删除的部门，ID: {}", id);
                return false;
            }
            
            // 检查部门中是否有用户，有用户的部门不能删除
            List<User> users = departmentRepository.findUsersByDepartmentId(id);
            if (!users.isEmpty()) {
                throw new IllegalStateException("无法删除包含 " + users.size() + " 个用户的部门。请先重新分配用户。");
            }
            
            // 执行删除操作
            boolean deleted = departmentRepository.deleteById(id);
            
            if (deleted) {
                // 删除部门后清除相关缓存，确保数据一致性
                if (cacheService.isRedisAvailable()) {
                    cacheService.clearDepartmentCache(id);
                }
                
                log.info("成功删除部门，ID: {}", id);
            } else {
                log.warn("删除部门失败，ID: {}", id);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("删除部门时发生错误，ID: {}", id, e);
            throw new RuntimeException("删除部门失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取部门中的所有用户列表
     * 返回格式化的用户信息，用于API响应
     * 
     * @param departmentId 部门ID
     * @return 部门中用户的列表，包含基本用户信息
     */
    public List<Object> getDepartmentUsers(Long departmentId) {
        log.info("Fetching users for department ID: {}", departmentId);
        
        if (departmentId == null) {
            log.warn("Department ID cannot be null");
            return new ArrayList<>();
        }
        
        try {
            // Check if department exists
            if (!departmentRepository.existsById(departmentId)) {
                log.warn("Department not found with ID: {}", departmentId);
                return new ArrayList<>();
            }
            
            List<User> users = departmentRepository.findUsersByDepartmentId(departmentId);
            
            // Convert to list of objects for API response
            List<Object> result = new ArrayList<>();
            for (User user : users) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("username", user.getUsername());
                userMap.put("email", user.getEmail());
                userMap.put("fullName", user.getFullName());
                userMap.put("role", user.getRole());
                userMap.put("active", user.isActive());
                userMap.put("createdAt", user.getCreatedAt());
                result.add(userMap);
            }
            
            log.info("Successfully fetched {} users for department ID: {}", result.size(), departmentId);
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching users for department ID: {}", departmentId, e);
            throw new RuntimeException("Failed to fetch department users", e);
        }
    }
    
    /**
     * Get department statistics
     * 
     * @param departmentId department ID
     * @return department statistics
     */
    public Map<String, Object> getDepartmentStatistics(Long departmentId) {
        log.info("Fetching statistics for department ID: {}", departmentId);
        
        try {
            Department department = getDepartmentById(departmentId);
            if (department == null) {
                return new HashMap<>();
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", department.getUserCount());
            stats.put("activeProjects", department.getActiveProjects());
            stats.put("completedProjects", department.getCompletedProjects());
            stats.put("totalProjects", department.getActiveProjects() + department.getCompletedProjects());
            
            // Calculate additional statistics
            List<User> users = departmentRepository.findUsersByDepartmentId(departmentId);
            long activeUsers = users.stream().filter(User::isActive).count();
            long adminUsers = users.stream().filter(u -> "admin".equals(u.getRole())).count();
            long editorUsers = users.stream().filter(u -> "editor".equals(u.getRole())).count();
            long viewerUsers = users.stream().filter(u -> "viewer".equals(u.getRole())).count();
            
            stats.put("activeUsers", activeUsers);
            stats.put("inactiveUsers", users.size() - activeUsers);
            stats.put("adminUsers", adminUsers);
            stats.put("editorUsers", editorUsers);
            stats.put("viewerUsers", viewerUsers);
            
            return stats;
            
        } catch (Exception e) {
            log.error("Error fetching statistics for department ID: {}", departmentId, e);
            throw new RuntimeException("Failed to fetch department statistics", e);
        }
    }
    
    /**
     * Update department project counts
     * 
     * @param departmentId department ID
     * @param activeProjects number of active projects
     * @param completedProjects number of completed projects
     */
    public void updateProjectCounts(Long departmentId, int activeProjects, int completedProjects) {
        log.info("Updating project counts for department ID: {} - Active: {}, Completed: {}", 
                   departmentId, activeProjects, completedProjects);
        
        try {
            departmentRepository.updateProjectCounts(departmentId, activeProjects, completedProjects);
            log.info("Successfully updated project counts for department ID: {}", departmentId);
        } catch (Exception e) {
            log.error("Error updating project counts for department ID: {}", departmentId, e);
            throw new RuntimeException("Failed to update project counts", e);
        }
    }
    
    // Private helper methods
    
    private void populateDepartmentUsers(Department department) {
        try {
            List<User> allUsers = departmentRepository.findUsersByDepartmentId(department.getId());
            List<User> recentUsers = departmentRepository.findRecentUsersByDepartmentId(department.getId());
            
            department.setUsers(allUsers);
            department.setRecentUsers(recentUsers);
            
            // Update user count if it doesn't match
            Integer currentUserCount = department.getUserCount();
            if (currentUserCount == null || currentUserCount != allUsers.size()) {
                department.setUserCount(allUsers.size());
                departmentRepository.updateUserCount(department.getId());
            }
        } catch (Exception e) {
            log.warn("Error populating users for department {}: {}", department.getId(), e.getMessage());
            department.setUsers(new ArrayList<>());
            department.setRecentUsers(new ArrayList<>());
        }
    }
    
    private void validateDepartmentForCreation(Department department) {
        if (department == null) {
            throw new IllegalArgumentException("Department cannot be null");
        }
        
        if (department.getName() == null || department.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Department name is required");
        }
        
        if (department.getName().length() > 100) {
            throw new IllegalArgumentException("Department name cannot exceed 100 characters");
        }
        
        if (department.getDescription() != null && department.getDescription().length() > 500) {
            throw new IllegalArgumentException("Department description cannot exceed 500 characters");
        }
    }
    
    private void validateDepartmentForUpdate(Department department) {
        if (department == null) {
            throw new IllegalArgumentException("Department cannot be null");
        }
        
        if (department.getId() == null) {
            throw new IllegalArgumentException("Department ID is required for update");
        }
        
        validateDepartmentForCreation(department);
    }
}
