package com.admin.manage.repository.impl;

import com.admin.manage.mapper.UserMapper;
import com.admin.manage.model.User;
import com.admin.manage.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis implementation of UserRepository
 */
@Slf4j
@Repository
public class UserRepositoryImpl implements UserRepository {
    
    @Autowired
    private UserMapper userMapper;
    
    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("Finding user by username: {}", username);
        User user = userMapper.findByUsername(username);
        return Optional.ofNullable(user);
    }
    
    @Override
    public Optional<User> authenticate(String username, String password) {
        log.debug("Authenticating user: {}", username);
        
        // Note: In production, password should be hashed and compared properly
        // This is a simplified implementation for demonstration
        User user = userMapper.authenticate(username, password);
        
        if (user != null) {
            log.debug("Authentication successful for user: {}", username);
        } else {
            log.debug("Authentication failed for user: {}", username);
        }
        
        return Optional.ofNullable(user);
    }
    
    @Override
    public List<User> findAll() {
        log.debug("Finding all users");
        return userMapper.findAll();
    }
    
    @Override
    public Optional<User> findById(Long id) {
        log.debug("Finding user by ID: {}", id);
        User user = userMapper.findById(id);
        return Optional.ofNullable(user);
    }
    
    @Override
    public Optional<User> findByIdIncludeInactive(Long id) {
        log.debug("Finding user by ID including inactive: {}", id);
        User user = userMapper.findByIdIncludeInactive(id);
        return Optional.ofNullable(user);
    }
    
    @Override
    public User save(User user) {
        if (user.getId() == null) {
            log.debug("Creating new user: {}", user.getUsername());
            userMapper.insert(user);
        } else {
            log.debug("Updating user: {}", user.getId());
            userMapper.update(user);
        }
        return user;
    }
    
    @Override
    public void deleteById(Long id) {
        log.debug("Deleting user by ID: {}", id);
        userMapper.deleteById(id);
    }
    
    @Override
    public List<User> findByDepartmentId(Long departmentId) {
        log.debug("Finding users by department ID: {}", departmentId);
        return userMapper.findByDepartmentId(departmentId);
    }
    
    @Override
    public List<User> searchByUsernameOrEmail(String query) {
        log.debug("Searching users by username, email, phone, or Telegram username: {}", query);
        return userMapper.searchByUsernameOrEmail(query);
    }
    
    @Override
    public boolean existsByEmail(String email) {
        log.debug("Checking if email exists: {}", email);
        return userMapper.existsByEmail(email) > 0;
    }
    
    @Override
    public boolean existsByPhone(String phone) {
        log.debug("Checking if phone exists: {}", phone);
        return userMapper.existsByPhone(phone) > 0;
    }
    
    @Override
    public boolean existsByTgUsername(String tgUsername) {
        log.debug("Checking if Telegram username exists: {}", tgUsername);
        return userMapper.existsByTgUsername(tgUsername) > 0;
    }
    
    @Override
    public boolean existsByEmployeeId(String employeeId) {
        log.debug("Checking if employee ID exists: {}", employeeId);
        return userMapper.existsByEmployeeId(employeeId) > 0;
    }
}
