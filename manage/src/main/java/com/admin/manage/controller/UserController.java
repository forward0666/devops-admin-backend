package com.admin.manage.controller;

import com.admin.manage.annotation.OperationLog;
import com.admin.manage.dto.ApiResponse;
import com.admin.manage.model.User;
import com.admin.manage.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 * 
 * 功能说明：
 * - 提供完整的用户管理REST API接口
 * - 包括用户CRUD操作、密码管理、验证状态更新等功能
 * - 集成操作日志记录，支持审计追踪
 * - 使用统一的ApiResponse格式返回结果
 * - 基础路径为"/users"，遵循RESTful API设计原则
 * 
 * 注解说明：
 * @RestController - 标识为REST控制器，自动处理HTTP请求和JSON响应
 * @RequestMapping("/users") - 设置控制器的基础请求路径为"/users"
 * @Autowired - 自动注入UserService依赖，处理业务逻辑
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取所有用户列表接口
     * 
     * 功能说明：
     * - 查询系统中所有用户信息
     * - 使用GET方法，无请求参数
     * - 返回用户列表，适用于用户数量较少的情况
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @return ApiResponse包含用户列表，成功时返回用户数据，失败时返回错误信息
     */
    @GetMapping
    public ApiResponse<List<User>> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            return ApiResponse.success("Users retrieved successfully", users);
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve users: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取用户详情接口
     * 
     * 功能说明：
     * - 根据用户ID查询特定用户的详细信息
     * - 使用GET方法，路径参数传递用户ID
     * - 支持用户不存在时的错误处理
     * - 返回完整的用户对象信息
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @return ApiResponse包含用户对象，成功时返回用户数据，用户不存在时返回错误信息
     */
    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);
            if (user != null) {
                return ApiResponse.success("User retrieved successfully", user);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve user: " + e.getMessage());
        }
    }

    /**
     * 创建新用户接口
     * 
     * 功能说明：
     * - 创建新的系统用户
     * - 使用POST方法，请求体为Map格式的用户信息
     * - 集成操作日志记录，记录用户创建操作
     * - 验证必填字段：用户名、密码、角色
     * - 支持丰富的用户信息字段，包括联系方式、个人信息等
     * 
     * 操作日志配置：
     * @OperationLog - 记录创建用户操作
     *   operationType: "CREATE" - 操作类型为创建
     *   operationName: "创建用户" - 操作名称为中文
     *   resourceType: "USER" - 资源类型为用户
     *   description: "创建新用户" - 操作描述
     * 
     * @param userRequest 用户创建请求，包含用户名、密码、邮箱、电话等信息
     * @return ApiResponse包含创建的用户对象，成功时返回用户数据，验证失败时返回错误信息
     */
    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建用户",
        resourceType = "USER",
        description = "创建新用户"
    )
    public ApiResponse<User> createUser(@RequestBody Map<String, Object> userRequest) {
        try {
            // 验证必填字段
            if (!userRequest.containsKey("username") || userRequest.get("username") == null) {
                return ApiResponse.error("Username is required");
            }
            if (!userRequest.containsKey("password") || userRequest.get("password") == null) {
                return ApiResponse.error("Password is required");
            }
            if (!userRequest.containsKey("role") || userRequest.get("role") == null) {
                return ApiResponse.error("Role is required");
            }

            // 提取请求参数
            String username = (String) userRequest.get("username");
            String password = (String) userRequest.get("password");
            String email = (String) userRequest.get("email");
            String phone = (String) userRequest.get("phone");
            String tgUsername = (String) userRequest.get("tgUsername");
            String fullName = (String) userRequest.get("fullName");
            String avatarUrl = (String) userRequest.get("avatarUrl");
            String position = (String) userRequest.get("position");
            String employeeId = (String) userRequest.get("employeeId");
            String role = (String) userRequest.get("role");
            Long departmentId = userRequest.get("departmentId") != null ? 
                Long.valueOf(userRequest.get("departmentId").toString()) : null;
            Long createdBy = userRequest.get("createdBy") != null ? 
                Long.valueOf(userRequest.get("createdBy").toString()) : null;

            // 调用服务层创建用户
            User createdUser = userService.createUser(username, password, email, phone, tgUsername, 
                                                    fullName, avatarUrl, position, employeeId, 
                                                    role, departmentId, createdBy);
            return ApiResponse.success("User created successfully", createdUser);
        } catch (Exception e) {
            return ApiResponse.error("Failed to create user: " + e.getMessage());
        }
    }

    /**
     * 更新用户信息接口
     * 
     * 功能说明：
     * - 更新现有用户的详细信息
     * - 使用PUT方法，路径参数传递用户ID，请求体为更新信息
     * - 集成操作日志记录，记录用户更新操作
     * - 支持更新用户基本信息、联系方式、状态等
     * - 返回更新后的用户对象
     * 
     * 操作日志配置：
     * @OperationLog - 记录更新用户操作
     *   operationType: "UPDATE" - 操作类型为更新
     *   operationName: "更新用户" - 操作名称为中文
     *   resourceType: "USER" - 资源类型为用户
     *   resourceIdIndex: 0 - 资源ID从路径参数索引0获取
     *   description: "更新用户信息" - 操作描述
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @param userRequest 用户更新请求，包含要更新的字段信息
     * @return ApiResponse包含更新后的用户对象，成功时返回用户数据，用户不存在时返回错误信息
     */
    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新用户",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "更新用户信息"
    )
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> userRequest) {
        try {
            // 提取更新参数
            String email = (String) userRequest.get("email");
            String phone = (String) userRequest.get("phone");
            String tgUsername = (String) userRequest.get("tgUsername");
            String fullName = (String) userRequest.get("fullName");
            String avatarUrl = (String) userRequest.get("avatarUrl");
            String position = (String) userRequest.get("position");
            String employeeId = (String) userRequest.get("employeeId");
            String role = (String) userRequest.get("role");
            Long departmentId = userRequest.get("departmentId") != null ? 
                Long.valueOf(userRequest.get("departmentId").toString()) : null;
            Boolean active = userRequest.get("active") != null ? 
                Boolean.valueOf(userRequest.get("active").toString()) : null;
            Long updatedBy = userRequest.get("updatedBy") != null ? 
                Long.valueOf(userRequest.get("updatedBy").toString()) : null;

            // 调用服务层更新用户
            User updatedUser = userService.updateUser(id, email, phone, tgUsername, fullName, 
                                                    avatarUrl, position, employeeId, role, 
                                                    departmentId, active, updatedBy);
            if (updatedUser != null) {
                return ApiResponse.success("User updated successfully", updatedUser);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to update user: " + e.getMessage());
        }
    }

    /**
     * 更新用户登录信息接口
     * 
     * 功能说明：
     * - 更新用户的登录相关信息，如最后登录IP地址
     * - 使用POST方法，路径参数传递用户ID，请求体为登录信息
     * - 通常用于用户登录成功后记录登录信息
     * - 不记录操作日志，因为登录操作频率较高
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @param loginRequest 登录信息请求，包含IP地址等信息
     * @return ApiResponse指示操作结果，成功时返回成功信息，用户不存在时返回错误信息
     */
    @PostMapping("/{id}/login")
    public ApiResponse<Void> updateLoginInfo(@PathVariable Long id, @RequestBody Map<String, String> loginRequest) {
        try {
            String ipAddress = loginRequest.get("ipAddress");

            boolean updated = userService.updateLoginInfo(id, ipAddress);
            if (updated) {
                return ApiResponse.success("Login info updated successfully", null);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to update login info: " + e.getMessage());
        }
    }

    /**
     * 验证用户邮箱接口
     * 
     * 功能说明：
     * - 验证用户的邮箱地址，标记为已验证状态
     * - 使用POST方法，路径参数传递用户ID
     * - 通常用于邮箱验证流程完成后调用
     * - 更新用户的邮箱验证状态字段
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @return ApiResponse指示操作结果，成功时返回成功信息，用户不存在时返回错误信息
     */
    @PostMapping("/{id}/verify-email")
    public ApiResponse<Void> verifyEmail(@PathVariable Long id) {
        try {
            boolean verified = userService.verifyEmail(id);
            if (verified) {
                return ApiResponse.success("Email verified successfully", null);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to verify email: " + e.getMessage());
        }
    }

    /**
     * 验证用户手机号接口
     * 
     * 功能说明：
     * - 验证用户的手机号码，标记为已验证状态
     * - 使用POST方法，路径参数传递用户ID
     * - 通常用于手机验证流程完成后调用
     * - 更新用户的手机验证状态字段
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @return ApiResponse指示操作结果，成功时返回成功信息，用户不存在时返回错误信息
     */
    @PostMapping("/{id}/verify-phone")
    public ApiResponse<Void> verifyPhone(@PathVariable Long id) {
        try {
            boolean verified = userService.verifyPhone(id);
            if (verified) {
                return ApiResponse.success("Phone verified successfully", null);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to verify phone: " + e.getMessage());
        }
    }

    /**
     * 删除用户接口
     * 
     * 功能说明：
     * - 删除指定ID的用户
     * - 使用DELETE方法，路径参数传递用户ID
     * - 集成操作日志记录，记录用户删除操作
     * - 执行软删除或物理删除（取决于服务层实现）
     * - 返回操作结果状态
     * 
     * 操作日志配置：
     * @OperationLog - 记录删除用户操作
     *   operationType: "DELETE" - 操作类型为删除
     *   operationName: "删除用户" - 操作名称为中文
     *   resourceType: "USER" - 资源类型为用户
     *   resourceIdIndex: 0 - 资源ID从路径参数索引0获取
     *   description: "删除用户" - 操作描述
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @return ApiResponse指示操作结果，成功时返回成功信息，用户不存在时返回错误信息
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除用户",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "删除用户"
    )
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        try {
            boolean deleted = userService.deleteUser(id);
            if (deleted) {
                return ApiResponse.success("User deleted successfully", null);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * 用户修改密码接口
     * 
     * 功能说明：
     * - 允许用户修改自己的登录密码
     * - 使用PUT方法，路径参数传递用户ID，请求体包含新旧密码
     * - 需要验证当前密码的正确性以确保安全性
     * - 集成操作日志记录，但设置logRequest=false不记录密码内容
     * - 新密码会进行加密处理后再存储
     * 
     * 操作日志配置：
     * @OperationLog - 记录密码修改操作
     *   operationType: "UPDATE" - 操作类型为更新
     *   operationName: "修改密码" - 操作名称为中文
     *   resourceType: "USER" - 资源类型为用户
     *   resourceIdIndex: 0 - 资源ID从路径参数索引0获取
     *   description: "用户修改密码" - 操作描述
     *   logRequest: false - 不记录请求体内容（保护密码安全）
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @param passwordRequest 密码修改请求，包含当前密码和新密码
     * @return ApiResponse指示操作结果，成功时返回成功信息，密码验证失败时返回错误信息
     */
    @PutMapping("/{id}/password")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "修改密码",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "用户修改密码",
        logRequest = false
    )
    public ApiResponse<Void> changePassword(@PathVariable Long id, @RequestBody Map<String, String> passwordRequest) {
        try {
            String oldPassword = passwordRequest.get("oldPassword");
            String newPassword = passwordRequest.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return ApiResponse.error("Both old and new passwords are required");
            }

            boolean changed = userService.changePassword(id, oldPassword, newPassword);
            if (changed) {
                return ApiResponse.success("Password changed successfully", null);
            } else {
                return ApiResponse.error("Failed to change password. Please check your old password.");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to change password: " + e.getMessage());
        }
    }

    /**
     * 管理员重置用户密码接口
     * 
     * 功能说明：
     * - 允许管理员重置其他用户的密码（管理员专用功能）
     * - 使用POST方法，路径参数传递用户ID，请求体包含新密码
     * - 不需要验证当前密码，直接设置新密码
     * - 集成操作日志记录，但设置logRequest=false不记录密码内容
     * - 新密码会进行加密处理后再存储
     * 
     * 操作日志配置：
     * @OperationLog - 记录密码重置操作
     *   operationType: "UPDATE" - 操作类型为更新
     *   operationName: "重置密码" - 操作名称为中文
     *   resourceType: "USER" - 资源类型为用户
     *   resourceIdIndex: 0 - 资源ID从路径参数索引0获取
     *   description: "管理员重置用户密码" - 操作描述
     *   logRequest: false - 不记录请求体内容（保护密码安全）
     * 
     * @param id 用户ID，通过路径变量@PathVariable传递
     * @param passwordRequest 密码重置请求，包含新密码
     * @return ApiResponse指示操作结果，成功时返回成功信息，用户不存在时返回错误信息
     */
    @PostMapping("/{id}/reset-password")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "重置密码",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "管理员重置用户密码",
        logRequest = false
    )
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordRequest) {
        try {
            String newPassword = passwordRequest.get("newPassword");

            if (newPassword == null) {
                return ApiResponse.error("New password is required");
            }

            boolean reset = userService.resetPassword(id, newPassword);
            if (reset) {
                return ApiResponse.success("Password reset successfully", null);
            } else {
                return ApiResponse.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponse.error("Failed to reset password: " + e.getMessage());
        }
    }

    /**
     * 按部门获取用户列表接口
     * 
     * 功能说明：
     * - 根据部门ID查询该部门下的所有用户
     * - 使用GET方法，路径参数传递部门ID
     * - 适用于组织架构管理，按部门查看用户分布
     * - 返回用户列表，包含用户基本信息
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @param departmentId 部门ID，通过路径变量@PathVariable传递
     * @return ApiResponse包含用户列表，成功时返回用户数据，失败时返回错误信息
     */
    @GetMapping("/department/{departmentId}")
    public ApiResponse<List<User>> getUsersByDepartment(@PathVariable Long departmentId) {
        try {
            List<User> users = userService.getUsersByDepartment(departmentId);
            return ApiResponse.success("Users retrieved successfully", users);
        } catch (Exception e) {
            return ApiResponse.error("Failed to retrieve users: " + e.getMessage());
        }
    }

    /**
     * 搜索用户接口
     * 
     * 功能说明：
     * - 根据用户名或邮箱进行模糊搜索
     * - 使用GET方法，查询参数传递搜索关键词
     * - 支持用户管理中的快速查找功能
     * - 返回匹配的用户列表
     * - 使用try-catch处理异常，返回统一的错误信息
     * 
     * @param query 搜索关键词，通过查询参数@RequestParam传递
     * @return ApiResponse包含用户列表，成功时返回匹配的用户数据，失败时返回错误信息
     */
    @GetMapping("/search")
    public ApiResponse<List<User>> searchUsers(@RequestParam String query) {
        try {
            List<User> users = userService.searchUsers(query);
            return ApiResponse.success("Users retrieved successfully", users);
        } catch (Exception e) {
            return ApiResponse.error("Failed to search users: " + e.getMessage());
        }
    }
}