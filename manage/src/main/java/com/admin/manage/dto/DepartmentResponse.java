package com.admin.manage.dto;

import com.admin.manage.model.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for department response
 */
public class DepartmentResponse {
    
    private Long id;
    private String name;
    private String description;
    private Long managerId;
    private String managerName;
    private Integer userCount;
    private Integer activeProjects;
    private Integer completedProjects;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<UserSummary> recentUsers;
    
    // Constructors
    public DepartmentResponse() {}
    
    // Static factory method
    public static DepartmentResponse fromDepartment(com.admin.manage.model.Department department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setId(department.getId());
        response.setName(department.getName());
        response.setDescription(department.getDescription());
        response.setManagerId(department.getManagerId());
        response.setUserCount(department.getUserCount());
        response.setActiveProjects(department.getActiveProjects());
        response.setCompletedProjects(department.getCompletedProjects());
        response.setCreatedAt(department.getCreatedAt());
        response.setUpdatedAt(department.getUpdatedAt());
        
        // Convert recent users to summary format
        if (department.getRecentUsers() != null) {
            response.setRecentUsers(
                department.getRecentUsers().stream()
                    .map(UserSummary::fromUser)
                    .toList()
            );
        }
        
        return response;
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
    
    public String getManagerName() {
        return managerName;
    }
    
    public void setManagerName(String managerName) {
        this.managerName = managerName;
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
    
    public List<UserSummary> getRecentUsers() {
        return recentUsers;
    }
    
    public void setRecentUsers(List<UserSummary> recentUsers) {
        this.recentUsers = recentUsers;
    }
    
    /**
     * Inner class for user summary
     */
    public static class UserSummary {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private String role;
        private boolean active;
        
        public static UserSummary fromUser(User user) {
            UserSummary summary = new UserSummary();
            summary.setId(user.getId());
            summary.setUsername(user.getUsername());
            summary.setFullName(user.getFullName());
            summary.setEmail(user.getEmail());
            summary.setRole(user.getRole());
            summary.setActive(user.isActive());
            return summary;
        }
        
        // Getters and Setters
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getFullName() {
            return fullName;
        }
        
        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public boolean isActive() {
            return active;
        }
        
        public void setActive(boolean active) {
            this.active = active;
        }
    }
}