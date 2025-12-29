package com.backend.manage.repository.impl;

import com.backend.manage.mapper.DepartmentMapper;
import com.backend.manage.entity.DepartmentEntity;
import com.backend.manage.entity.UserEntity;
import com.backend.manage.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis implementation of DepartmentRepository
 */
@Slf4j
@Repository
public class DepartmentRepositoryImpl implements DepartmentRepository {

    @Autowired
    private DepartmentMapper departmentMapper;
    
    @Override
    public List<DepartmentEntity> findAll() {
        log.debug("Finding all departments");
        return departmentMapper.findAll();
    }

    @Override
    public Optional<DepartmentEntity> findById(Long id) {
        log.debug("Finding department by ID: {}", id);
        DepartmentEntity department = departmentMapper.findById(id);
        return Optional.ofNullable(department);
    }

    @Override
    public Optional<DepartmentEntity> findByName(String name) {
        log.debug("Finding department by name: {}", name);
        DepartmentEntity department = departmentMapper.findByName(name);
        return Optional.ofNullable(department);
    }
    
    @Override
    public DepartmentEntity save(DepartmentEntity department) {
        log.debug("Saving new department: {}", department.getName());
        
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
            log.debug("Successfully saved department with ID: {}", department.getId());
            return department;
        } else {
            throw new RuntimeException("Failed to save department");
        }
    }
    
    @Override
    public DepartmentEntity update(DepartmentEntity department) {
        log.debug("Updating department with ID: {}", department.getId());
        
        // Set update timestamp
        department.setUpdatedAt(LocalDateTime.now());
        
        int result = departmentMapper.update(department);
        if (result > 0) {
            log.debug("Successfully updated department with ID: {}", department.getId());
            return department;
        } else {
            throw new RuntimeException("Failed to update department or department not found");
        }
    }
    
    @Override
    public boolean deleteById(Long id) {
        log.debug("Deleting department with ID: {}", id);
        
        // Check if department has users first
        List<UserEntity> users = findUsersByDepartmentId(id);
        if (!users.isEmpty()) {
            throw new IllegalStateException("Cannot delete department with existing users");
        }
        
        int result = departmentMapper.deleteById(id);
        boolean deleted = result > 0;
        
        if (deleted) {
            log.debug("Successfully deleted department with ID: {}", id);
        } else {
            log.warn("Failed to delete department with ID: {} - department may not exist", id);
        }
        
        return deleted;
    }
    
    @Override
    public boolean existsById(Long id) {
        log.debug("Checking if department exists with ID: {}", id);
        return departmentMapper.existsById(id) > 0;
    }
    
    @Override
    public boolean existsByName(String name) {
        log.debug("Checking if department exists with name: {}", name);
        return departmentMapper.existsByName(name) > 0;
    }
    
    @Override
    public List<UserEntity> findUsersByDepartmentId(Long departmentId) {
        log.debug("Finding users for department ID: {}", departmentId);
        return departmentMapper.findUsersByDepartmentId(departmentId);
    }

    @Override
    public List<UserEntity> findRecentUsersByDepartmentId(Long departmentId) {
        log.debug("Finding recent users for department ID: {}", departmentId);
        return departmentMapper.findRecentUsersByDepartmentId(departmentId);
    }
    
    @Override
    public void updateUserCount(Long departmentId) {
        log.debug("Updating user count for department ID: {}", departmentId);
        int result = departmentMapper.updateUserCount(departmentId);
        if (result > 0) {
            log.debug("Successfully updated user count for department ID: {}", departmentId);
        } else {
            log.warn("Failed to update user count for department ID: {}", departmentId);
        }
    }
    
    @Override
    public void updateProjectCounts(Long departmentId, int activeProjects, int completedProjects) {
        log.debug("Updating project counts for department ID: {} - Active: {}, Completed: {}", 
                   departmentId, activeProjects, completedProjects);
        
        int result = departmentMapper.updateProjectCounts(departmentId, activeProjects, completedProjects);
        if (result > 0) {
            log.debug("Successfully updated project counts for department ID: {}", departmentId);
        } else {
            log.warn("Failed to update project counts for department ID: {}", departmentId);
        }
    }
    
    /**
     * Check if department name exists excluding a specific ID (for updates)
     */
    public boolean existsByNameExcludingId(String name, Long id) {
        log.debug("Checking if department name '{}' exists excluding ID: {}", name, id);
        return departmentMapper.existsByNameExcludingId(name, id) > 0;
    }
}
