package com.admin.manage.controller;

import com.admin.manage.annotation.OperationLog;
import com.admin.manage.dto.ApiResponse;
import com.admin.manage.model.Department;
import com.admin.manage.service.DepartmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

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
@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentController.class);

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
    public ResponseEntity<ApiResponse<List<Department>>> getAllDepartments() {
        logger.info("GET /departments - Fetching all departments");
        try {
            List<Department> departments = departmentService.getAllDepartments();
            logger.info("Successfully retrieved {} departments", departments.size());
            return ResponseEntity.ok(ApiResponse.success("Departments retrieved successfully", departments));
        } catch (Exception e) {
            logger.error("Error retrieving departments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve departments: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Department>> getDepartmentById(@PathVariable Long id) {
        logger.info("GET /departments/{} - Fetching department by ID", id);
        try {
            Department department = departmentService.getDepartmentById(id);
            if (department != null) {
                logger.info("Successfully retrieved department: {}", department.getName());
                return ResponseEntity.ok(ApiResponse.success("Department retrieved successfully", department));
            } else {
                logger.warn("Department not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Department not found"));
            }
        } catch (Exception e) {
            logger.error("Error retrieving department with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve department: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Department>> createDepartment(@Valid @RequestBody Department department) {
        logger.info("POST /departments - Creating new department: {}", department.getName());
        try {
            Department createdDepartment = departmentService.createDepartment(department);
            logger.info("Successfully created department with ID: {}", createdDepartment.getId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Department created successfully", createdDepartment));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid department data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid department data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating department: {}", department.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create department: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Department>> updateDepartment(@PathVariable Long id, @Valid @RequestBody Department department) {
        logger.info("PUT /departments/{} - Updating department", id);
        try {
            department.setId(id);
            Department updatedDepartment = departmentService.updateDepartment(department);
            if (updatedDepartment != null) {
                logger.info("Successfully updated department: {}", updatedDepartment.getName());
                return ResponseEntity.ok(ApiResponse.success("Department updated successfully", updatedDepartment));
            } else {
                logger.warn("Department not found for update with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Department not found"));
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid department data for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Invalid department data: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating department with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update department: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        logger.info("DELETE /departments/{} - Deleting department", id);
        try {
            boolean deleted = departmentService.deleteDepartment(id);
            if (deleted) {
                logger.info("Successfully deleted department with ID: {}", id);
                return ResponseEntity.ok(ApiResponse.success("Department deleted successfully", null));
            } else {
                logger.warn("Department not found for deletion with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Department not found"));
            }
        } catch (IllegalStateException e) {
            logger.warn("Cannot delete department with ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting department with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete department: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<List<Object>>> getDepartmentUsers(@PathVariable Long id) {
        logger.info("GET /departments/{}/users - Fetching users for department", id);
        try {
            List<Object> users = departmentService.getDepartmentUsers(id);
            logger.info("Successfully retrieved {} users for department ID: {}", users.size(), id);
            return ResponseEntity.ok(ApiResponse.success("Department users retrieved successfully", users));
        } catch (Exception e) {
            logger.error("Error retrieving users for department ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve department users: " + e.getMessage()));
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
    public ResponseEntity<ApiResponse<List<Department>>> searchDepartments(@RequestParam String name) {
        logger.info("GET /departments/search?name={} - Searching departments", name);
        try {
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Search name cannot be empty"));
            }
            
            List<Department> allDepartments = departmentService.getAllDepartments();
            List<Department> filteredDepartments = allDepartments.stream()
                    .filter(dept -> dept.getName().toLowerCase().contains(name.toLowerCase()))
                    .toList();
            
            logger.info("Found {} departments matching search term: {}", filteredDepartments.size(), name);
            return ResponseEntity.ok(ApiResponse.success("Departments found", filteredDepartments));
        } catch (Exception e) {
            logger.error("Error searching departments with name: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to search departments: " + e.getMessage()));
        }
    }


}
