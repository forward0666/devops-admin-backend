package com.admin.manage.controller;

import com.admin.manage.dto.ApiResponse;
import com.admin.manage.model.Department;
import com.admin.manage.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @GetMapping
    public ApiResponse<List<Department>> getAllDepartments() {
        try {
            List<Department> departments = departmentService.getAllDepartments();
            return ApiResponse.success("Departments retrieved successfully", departments);
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve departments: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Department> getDepartmentById(@PathVariable Long id) {
        try {
            Department department = departmentService.getDepartmentById(id);
            if (department != null) {
                return ApiResponse.success("Department retrieved successfully", department);
            } else {
                return ApiResponse.error("Department not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve department: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Department> createDepartment(@RequestBody Department department) {
        try {
            Department createdDepartment = departmentService.createDepartment(department);
            return ApiResponse.success("Department created successfully", createdDepartment);
        } catch (Exception e) {
            return ApiResponse.error("Failed to create department: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> updateDepartment(@PathVariable Long id, @RequestBody Department department) {
        try {
            department.setId(id);
            Department updatedDepartment = departmentService.updateDepartment(department);
            if (updatedDepartment != null) {
                return ApiResponse.success("Department updated successfully", updatedDepartment);
            } else {
                return ApiResponse.error("Department not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to update department: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable Long id) {
        try {
            boolean deleted = departmentService.deleteDepartment(id);
            if (deleted) {
                return ApiResponse.success("Department deleted successfully", null);
            } else {
                return ApiResponse.error("Department not found or cannot be deleted");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete department: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/users")
    public ApiResponse<List<Object>> getDepartmentUsers(@PathVariable Long id) {
        try {
            List<Object> users = departmentService.getDepartmentUsers(id);
            return ApiResponse.success("Department users retrieved successfully", users);
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve department users: " + e.getMessage());
        }
    }
}