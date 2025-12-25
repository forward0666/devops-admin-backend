package com.admin.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating an existing department
 */
public class DepartmentUpdateRequest {
    
    @NotBlank(message = "Department name is required")
    @Size(max = 100, message = "Department name cannot exceed 100 characters")
    private String name;
    
    @Size(max = 500, message = "Department description cannot exceed 500 characters")
    private String description;
    
    private Long managerId;
    
    private Integer activeProjects;
    
    private Integer completedProjects;
    
    // Constructors
    public DepartmentUpdateRequest() {}
    
    public DepartmentUpdateRequest(String name, String description, Long managerId) {
        this.name = name;
        this.description = description;
        this.managerId = managerId;
    }
    
    // Getters and Setters
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
}