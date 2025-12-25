package com.admin.manage.model;

import java.time.LocalDateTime;
import java.util.List;

// 使用Java 21的record特性，简化不可变数据载体
// 对于可变实体类，保留原有的类结构但添加Java 21的特性
public class Department {
    private Long id;
    private String name;
    private String description;
    private Long managerId;
    private Integer userCount;
    private Integer activeProjects;
    private Integer completedProjects;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> users;  // 简化类型，避免依赖Spring Security
    private List<String> recentUsers; // 简化类型，避免依赖Spring Security

    // 使用Java 21的简洁构造器
    public Department() {
        this.userCount = 0;
        this.activeProjects = 0;
        this.completedProjects = 0;
        var now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Department(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }
    
    // 全参数构造器，用于创建不可变副本
    public Department(Long id, String name, String description, Long managerId, 
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.userCount = 0;
        this.activeProjects = 0;
        this.completedProjects = 0;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // 使用Java 21的便利方法创建副本
    public Department withId(Long id) {
        var copy = new Department(this.name, this.description);
        copy.id = id;
        copy.managerId = this.managerId;
        copy.userCount = this.userCount;
        copy.activeProjects = this.activeProjects;
        copy.completedProjects = this.completedProjects;
        copy.createdAt = this.createdAt;
        copy.updatedAt = this.updatedAt;
        copy.users = this.users;
        copy.recentUsers = this.recentUsers;
        return copy;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public Integer getUserCount() {
        return userCount;
    }

    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }

    public Integer getActiveProjects() {
        return activeProjects;
    }

    public void setActiveProjects(Integer activeProjects) {
        this.activeProjects = activeProjects;
    }

    public Integer getCompletedProjects() {
        return completedProjects;
    }

    public void setCompletedProjects(Integer completedProjects) {
        this.completedProjects = completedProjects;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

    public List<String> getRecentUsers() {
        return recentUsers;
    }

    public void setRecentUsers(List<String> recentUsers) {
        this.recentUsers = recentUsers;
    }
}