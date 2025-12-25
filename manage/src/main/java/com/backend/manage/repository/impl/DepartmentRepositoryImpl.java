package com.backend.manage.repository.impl;

import com.backend.manage.mapper.DepartmentMapper;
import com.backend.manage.model.Department;
import com.backend.manage.model.User;
import com.backend.manage.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis implementation of DepartmentRepository
 */
@Repository
public class DepartmentRepositoryImpl implements DepartmentRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(DepartmentRepositoryImpl.class);
    
    @Autowired
    private DepartmentMapper departmentMapper;
    
    @Override
    public List<Department> findAll() {
        logger.debug("Finding all departments");
        return departmentMapper.findAll();
    }
    
    @Override
    public Optional<Department> findById(Long id) {
        logger.debug("Finding department by ID: {}", id);
        Department department = departmentMapper.findById(id);
        return Optional.ofNullable(department);
    }
    
    @Override
    public Optional<Department> findByName(String name) {
        logger.debug("Finding department by name: {}", name);
        Department department = departmentMapper.findByName(name);
        return Optional.ofNullable(department);
    }
    
    @Override
    public Department save(Department department) {
        logger.debug("Saving new department: {}", department.getName());
        
        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        
        // Set default values if not provided
        if (department.getUserCount() == null) {
            department.setUserCount(0);
        }
        if (department.getActiveProjects() == null) {
            department.setActiveProjects(0);
        }
        if (department.getCompletedProjects() == null) {
            department.setCompletedProjects(0);
        }
        
        int result = departmentMapper.insert(department);
        if (result > 0) {
            logger.debug("Successfully saved department with ID: {}", department.getId());
            return department;
        } else {
            throw new RuntimeException("Failed to save department");
        }
    }
    
    @Override
    public Department update(Department department) {
        logger.debug("Updating department with ID: {}", department.getId());
        
        // Set update timestamp
        department.setUpdatedAt(LocalDateTime.now());
        
        int result = departmentMapper.update(department);
        if (result > 0) {
            logger.debug("Successfully updated department with ID: {}", department.getId());
            return department;
        } else {
            throw new RuntimeException("Failed to update department or department not found");
        }
    }
    
    @Override
    public boolean deleteById(Long id) {
        logger.debug("Deleting department with ID: {}", id);
        
        // Check if department has users first
        List<User> users = findUsersByDepartmentId(id);
        if (!users.isEmpty()) {
            throw new IllegalStateException("Cannot delete department with existing users");
        }
        
        int result = departmentMapper.deleteById(id);
        boolean deleted = result > 0;
        
        if (deleted) {
            logger.debug("Successfully deleted department with ID: {}", id);
        } else {
            logger.warn("Failed to delete department with ID: {} - department may not exist", id);
        }
        
        return deleted;
    }
    
    @Override
    public boolean existsById(Long id) {
        logger.debug("Checking if department exists with ID: {}", id);
        return departmentMapper.existsById(id) > 0;
    }
    
    @Override
    public boolean existsByName(String name) {
        logger.debug("Checking if department exists with name: {}", name);
        return departmentMapper.existsByName(name) > 0;
    }
    
    @Override
    public List<User> findUsersByDepartmentId(Long departmentId) {
        logger.debug("Finding users for department ID: {}", departmentId);
        return departmentMapper.findUsersByDepartmentId(departmentId);
    }
    
    @Override
    public List<User> findRecentUsersByDepartmentId(Long departmentId) {
        logger.debug("Finding recent users for department ID: {}", departmentId);
        return departmentMapper.findRecentUsersByDepartmentId(departmentId);
    }
    
    @Override
    public void updateUserCount(Long departmentId) {
        logger.debug("Updating user count for department ID: {}", departmentId);
        int result = departmentMapper.updateUserCount(departmentId);
        if (result > 0) {
            logger.debug("Successfully updated user count for department ID: {}", departmentId);
        } else {
            logger.warn("Failed to update user count for department ID: {}", departmentId);
        }
    }
    
    @Override
    public void updateProjectCounts(Long departmentId, int activeProjects, int completedProjects) {
        logger.debug("Updating project counts for department ID: {} - Active: {}, Completed: {}", 
                   departmentId, activeProjects, completedProjects);
        
        int result = departmentMapper.updateProjectCounts(departmentId, activeProjects, completedProjects);
        if (result > 0) {
            logger.debug("Successfully updated project counts for department ID: {}", departmentId);
        } else {
            logger.warn("Failed to update project counts for department ID: {}", departmentId);
        }
    }
    
    /**
     * Check if department name exists excluding a specific ID (for updates)
     */
    public boolean existsByNameExcludingId(String name, Long id) {
        logger.debug("Checking if department name '{}' exists excluding ID: {}", name, id);
        return departmentMapper.existsByNameExcludingId(name, id) > 0;
    }
}
