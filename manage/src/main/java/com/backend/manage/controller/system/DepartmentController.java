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

/**
 * 部门管理控制器
 * 处理部门相关的CRUD操作和业务逻辑
 * 
 * 功能说明：
 * - 提供完整的部门管理API接口
 * - 支持部门的创建、查询、更新、删除操作
 * - 集成操作日志记录注解@OperationLog
 * - 使用统一的ApiResponse响应格式
 * - 支持RESTful API设计风格
 * - 包含部门用户管理和搜索功能
 * - 集成详细的日志记录和异常处理
 * - 支持数据验证和业务逻辑校验
 */
@Slf4j
@RestController
@RequestMapping("/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    /**
     * 获取所有部门列表接口
     * 
     * 功能说明：
     * - 查询系统中所有的部门信息
     * - 返回完整的部门列表数据
     * - 使用GET方法，无请求参数
     * - 记录详细的请求日志和结果统计
     * - 使用统一的ApiResponse响应格式
     * - 异常处理机制，返回友好的错误信息
     * 
     * @return ResponseEntity包含操作结果，成功时返回部门列表数据，失败时返回错误信息
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<DepartmentEntity>>> getAllDepartments() {
        log.info("GET /departments - Fetching all departments");
        try {
            List<DepartmentEntity> departments = departmentService.getAllDepartments();
            log.info("Successfully retrieved {} departments", departments.size());
            return ResponseEntity.ok(ApiResponseDto.success("Departments retrieved successfully", departments));
        } catch (Exception e) {
            log.error("Error retrieving departments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve departments: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取部门详情接口
     * 
     * 功能说明：
     * - 根据部门ID查询具体的部门信息
     * - 支持路径参数传递部门ID
     * - 返回指定部门的详细信息
     * - 使用GET方法，支持路径参数
     * - 记录详细的请求日志，包括部门ID
     * - 使用统一的ApiResponse响应格式
     * - 支持404 Not Found状态码处理部门不存在的情况
     * - 异常处理机制，返回友好的错误信息
     * 
     * @param id 部门ID，通过路径参数传递
     * @return ResponseEntity包含操作结果，成功时返回部门详情数据，失败时返回错误信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<DepartmentEntity>> getDepartmentById(@PathVariable Long id) {
        log.info("GET /departments/{} - Fetching department by ID", id);
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

    /**
     * 创建新部门接口
     * 
     * 功能说明：
     * - 创建新的部门信息
     * - 接收JSON格式的部门数据，支持数据验证
     * - 使用POST方法，请求体包含部门信息
     * - 返回创建成功的部门数据
     * - 记录详细的请求日志，包括部门名称
     * - 使用统一的ApiResponse响应格式
     * - 集成操作日志记录@OperationLog注解
     * - 支持数据验证异常处理(IllegalArgumentException)
     * - 返回201 Created状态码表示资源创建成功
     * - 异常处理机制，返回友好的错误信息
     * 
     * @param department 部门对象，通过请求体传递JSON数据，支持数据验证
     * @return ResponseEntity包含操作结果，成功时返回创建的部门数据，失败时返回错误信息
     */
    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建部门",
        resourceType = "DEPARTMENT",
        description = "创建新部门"
    )
    public ResponseEntity<ApiResponseDto<DepartmentEntity>> createDepartment(@Valid @RequestBody DepartmentEntity department) {
        log.info("POST /departments - Creating new department: {}", department.getName());
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

    /**
     * 更新部门信息接口
     * 
     * 功能说明：
     * - 更新指定部门的详细信息
     * - 根据部门ID和更新的部门数据进行修改
     * - 使用PUT方法，支持路径参数和请求体
     * - 返回更新后的部门数据
     * - 记录详细的请求日志，包括部门ID
     * - 使用统一的ApiResponse响应格式
     * - 集成操作日志记录@OperationLog注解
     * - 支持数据验证异常处理(IllegalArgumentException)
     * - 支持404 Not Found状态码处理部门不存在的情况
     * - 异常处理机制，返回友好的错误信息
     * 
     * @param id 部门ID，通过路径参数传递
     * @param department 部门对象，通过请求体传递更新的JSON数据，支持数据验证
     * @return ResponseEntity包含操作结果，成功时返回更新后的部门数据，失败时返回错误信息
     */
    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新部门",
        resourceType = "DEPARTMENT",
        resourceIdIndex = 0,
        description = "更新部门信息"
    )
    public ResponseEntity<ApiResponseDto<DepartmentEntity>> updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentEntity department) {
        log.info("PUT /departments/{} - Updating department", id);
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

    /**
     * 删除部门接口
     * 
     * 功能说明：
     * - 删除指定的部门信息
     * - 根据部门ID进行删除操作
     * - 使用DELETE方法，支持路径参数
     * - 返回删除操作的成功状态
     * - 记录详细的请求日志，包括部门ID
     * - 使用统一的ApiResponse响应格式
     * - 集成操作日志记录@OperationLog注解
     * - 支持404 Not Found状态码处理部门不存在的情况
     * - 支持409 Conflict状态码处理无法删除的情况(IllegalStateException)
     * - 异常处理机制，返回友好的错误信息
     * 
     * @param id 部门ID，通过路径参数传递
     * @return ResponseEntity包含操作结果，成功时返回成功消息，失败时返回错误信息
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除部门",
        resourceType = "DEPARTMENT",
        resourceIdIndex = 0,
        description = "删除部门"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteDepartment(@PathVariable Long id) {
        log.info("DELETE /departments/{} - Deleting department", id);
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

    /**
     * 获取部门下所有用户接口
     * 
     * 功能说明：
     * - 查询指定部门下的所有用户信息
     * - 根据部门ID获取该部门的所有用户列表
     * - 使用GET方法，支持路径参数
     * - 返回用户列表数据
     * - 记录详细的请求日志，包括部门ID和用户数量
     * - 使用统一的ApiResponse响应格式
     * - 异常处理机制，返回友好的错误信息
     * 
     * @param id 部门ID，通过路径参数传递
     * @return ResponseEntity包含操作结果，成功时返回用户列表数据，失败时返回错误信息
     */
    @GetMapping("/{id}/users")
    public ResponseEntity<ApiResponseDto<List<Object>>> getDepartmentUsers(@PathVariable Long id) {
        log.info("GET /departments/{}/users - Fetching users for department", id);
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

    /**
     * 按名称搜索部门接口
     * 
     * 功能说明：
     * - 根据部门名称进行模糊搜索
     * - 支持查询参数传递搜索关键词
     * - 使用GET方法，支持查询参数
     * - 返回匹配的部门列表
     * - 记录详细的请求日志，包括搜索关键词和匹配数量
     * - 使用统一的ApiResponse响应格式
     * - 支持400 Bad Request状态码处理空搜索关键词的情况
     * - 异常处理机制，返回友好的错误信息
     * - 使用流式处理进行内存中的模糊匹配
     * 
     * @param name 部门名称搜索关键词，通过查询参数传递
     * @return ResponseEntity包含操作结果，成功时返回匹配的部门列表，失败时返回错误信息
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponseDto<List<DepartmentEntity>>> searchDepartments(@RequestParam String name) {
        log.info("GET /departments/search?name={} - Searching departments", name);
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
            log.error("Error searching departments with name: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to search departments: " + e.getMessage()));
        }
    }


}
