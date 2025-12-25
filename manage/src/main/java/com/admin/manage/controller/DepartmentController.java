package com.admin.manage.controller;

import com.admin.manage.dto.ApiResponse;
import com.admin.manage.model.Department;
import com.admin.manage.service.DepartmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/departments")
public class DepartmentController {
    
    // 使用构造器注入替代@Autowired，这是Java 21的推荐实践
    private final DepartmentService departmentService;
    
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ApiResponse<List<Department>> getAllDepartments() {
        return tryGetAllDepartments();
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ApiResponse<List<Department>> tryGetAllDepartments() {
        try {
            var departments = departmentService.getAllDepartments();
            return ApiResponse.success("Departments retrieved successfully", departments);
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve departments: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Department> getDepartmentById(@PathVariable Long id) {
        return tryGetDepartmentById(id);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ApiResponse<Department> tryGetDepartmentById(Long id) {
        try {
            // 使用Optional.ofNullable进行更安全的空值处理
            return Optional.ofNullable(departmentService.getDepartmentById(id))
                    .map(department -> ApiResponse.success("Department retrieved successfully", department))
                    .orElseGet(() -> ApiResponse.notFound("Department not found"));
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve department: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<Department> createDepartment(@RequestBody Department department) {
        return tryCreateDepartment(department);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ApiResponse<Department> tryCreateDepartment(Department department) {
        try {
            var createdDepartment = departmentService.createDepartment(department);
            return ApiResponse.success("Department created successfully", createdDepartment);
        } catch (Exception e) {
            return ApiResponse.error("Failed to create department: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> updateDepartment(@PathVariable Long id, @RequestBody Department department) {
        return tryUpdateDepartment(id, department);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ApiResponse<Department> tryUpdateDepartment(Long id, Department department) {
        try {
            // 使用Java 21的with方法创建新对象，避免修改原始对象
            var departmentWithId = department.withId(id);
            var updatedDepartment = departmentService.updateDepartment(departmentWithId);
            
            // 使用if-else语句替代switch表达式
            if (updatedDepartment != null) {
                return ApiResponse.success("Department updated successfully", updatedDepartment);
            } else {
                return ApiResponse.notFound("Department not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to update department: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable Long id) {
        return tryDeleteDepartment(id);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ApiResponse<Void> tryDeleteDepartment(Long id) {
        try {
            // 使用if-else语句替代switch表达式
            if (departmentService.deleteDepartment(id)) {
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
        return tryGetDepartmentUsers(id);
    }
    
    // 将异常处理提取为私有方法，提高代码可读性
    private ApiResponse<List<Object>> tryGetDepartmentUsers(Long id) {
        try {
            var users = departmentService.getDepartmentUsers(id);
            return ApiResponse.success("Department users retrieved successfully", users);
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve department users: " + e.getMessage());
        }
    }
}