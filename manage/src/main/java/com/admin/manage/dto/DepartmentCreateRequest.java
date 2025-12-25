package com.admin.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new department
 */
public class DepartmentCreateRequest {
    
    @NotBlank(message = "Department name is required")
    @Size(max = 100, message = "Department name cannot exceed 100 characters")
    private String name;
    
    @Size(max = 500, message = "Department description cannot exceed 500 characters")
    private String description;
    
    private Long managerId;
    
    // Constructors
    public DepartmentCreateRequest() {}
    
    public DepartmentCreateRequest(String name, String description, Long managerId) {
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
}