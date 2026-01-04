package com.backend.manage.controller.system;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.system.DepartmentEntity;
import com.backend.manage.service.system.DepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<DepartmentEntity>>> getAllDepartments() {
        log.info("GET /department - Fetching all departments");
        try {
            List<DepartmentEntity> department = departmentService.getAllDepartments();
            log.info("Successfully retrieved {} departments", department.size());
            return ResponseEntity.ok(ApiResponseDto.success("Departments retrieved successfully", department));
        } catch (Exception e) {
            log.error("Error retrieving departments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve departments: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<DepartmentEntity>> getDepartmentById(@PathVariable Long id) {
        log.info("GET /department/{} - Fetching department by ID", id);
        try {
            DepartmentEntity department = departmentService.getDepartmentById(id);
            if (department != null) {
                log.info("Successfully retrieved department: {}", department.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Department retrieved successfully", department));
            } else {
                log.warn("Department not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Department not found"));
            }
        } catch (Exception e) {
            log.error("Error retrieving department with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve department: " + e.getMessage()));
        }
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建部门",
        resourceType = "DEPARTMENT",
        description = "创建新部门"
    )
    public ResponseEntity<ApiResponseDto<DepartmentEntity>> createDepartment(@Valid @RequestBody DepartmentEntity department) {
        log.info("POST /department - Creating new department: {}", department.getName());
        try {
            DepartmentEntity createdDepartment = departmentService.createDepartment(department);
            log.info("Successfully created department with ID: {}", createdDepartment.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseDto.success("Department created successfully", createdDepartment));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid department data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid department data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating department: {}", department.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to create department: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新部门",
        resourceType = "DEPARTMENT",
        resourceIdIndex = 0,
        description = "更新部门信息"
    )
    public ResponseEntity<ApiResponseDto<DepartmentEntity>> updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentEntity department) {
        log.info("PUT /department/{} - Updating department", id);
        try {
            department.setId(id);
            DepartmentEntity updatedDepartment = departmentService.updateDepartment(department);
            if (updatedDepartment != null) {
                log.info("Successfully updated department: {}", updatedDepartment.getName());
                return ResponseEntity.ok(ApiResponseDto.success("Department updated successfully", updatedDepartment));
            } else {
                log.warn("Department not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Department not found"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid department data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.error("Invalid department data: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating department with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to update department: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除部门",
        resourceType = "DEPARTMENT",
        resourceIdIndex = 0,
        description = "删除部门"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteDepartment(@PathVariable Long id) {
        log.info("DELETE /department/{} - Deleting department", id);
        try {
            boolean deleted = departmentService.deleteDepartment(id);
            if (deleted) {
                log.info("Successfully deleted department with ID: {}", id);
                return ResponseEntity.ok(ApiResponseDto.success("Department deleted successfully", null));
            } else {
                log.warn("Department not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponseDto.error("Department not found"));
            }
        } catch (IllegalStateException e) {
            log.warn("Cannot delete department with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseDto.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting department with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to delete department: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/users")
    public ResponseEntity<ApiResponseDto<List<Object>>> getDepartmentUsers(@PathVariable Long id) {
        log.info("GET /department/{}/users - Fetching users for department", id);
        try {
            List<Object> users = departmentService.getDepartmentUsers(id);
            log.info("Successfully retrieved {} users for department ID: {}", users.size(), id);
            return ResponseEntity.ok(ApiResponseDto.success("Department users retrieved successfully", users));
        } catch (Exception e) {
            log.error("Error retrieving users for department ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve department users: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDto<List<DepartmentEntity>>> searchDepartments(@RequestParam String name) {
        log.info("GET /department/search?name={} - Searching department by name", name);
        try {
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponseDto.error("Search name cannot be empty"));
            }

            List<DepartmentEntity> allDepartments = departmentService.getAllDepartments();
            List<DepartmentEntity> filteredDepartments = allDepartments.stream()
                    .filter(dept -> dept.getName().toLowerCase().contains(name.toLowerCase()))
                    .toList();
            
            log.info("Found {} departments matching search term: {}", filteredDepartments.size(), name);
            return ResponseEntity.ok(ApiResponseDto.success("Departments found", filteredDepartments));
        } catch (Exception e) {
            log.error("Error searching departments by name: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to search department by name: " + e.getMessage()));
        }
    }
}
