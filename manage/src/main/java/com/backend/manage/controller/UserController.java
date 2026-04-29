package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.UserEntity;
import com.backend.manage.service.UserService;
import com.backend.manage.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.backend.manage.dto.UserRequestDto;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ApiResponseDto<List<UserVo>> getAllUsers() {
        try {
            List<UserEntity> users = userService.getAllUsers();
            List<UserVo> result = users.stream().map(UserVo::fromEntity).toList();
            return ApiResponseDto.success("Users retrieved successfully", result);
        } catch (Exception e) {
            log.error("Failed to retrieve users", e);
            return ApiResponseDto.error("Failed to retrieve users");
        }
    }

    @GetMapping("/{id}")
    public ApiResponseDto<UserVo> getUserById(@PathVariable Long id) {
        try {
            UserEntity user = userService.getUserById(id);
            if (user != null) {
                return ApiResponseDto.success("User retrieved successfully", UserVo.fromEntity(user));
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            log.error("Failed to retrieve user: " + id, e);
            return ApiResponseDto.error("Failed to retrieve user");
        }
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建用户",
        resourceType = "USER",
        description = "创建新用户"
    )
    public ApiResponseDto<UserVo> createUser(@RequestBody UserRequestDto userRequest) {
        try {
            if (userRequest.getUsername() == null) {
                return ApiResponseDto.error("Username is required");
            }
            if (userRequest.getPassword() == null) {
                return ApiResponseDto.error("Password is required");
            }
            if (userRequest.getRole() == null) {
                return ApiResponseDto.error("Role is required");
            }

            UserEntity createdUser = userService.createUser(
                    userRequest.getUsername(), userRequest.getPassword(),
                    userRequest.getEmail(), userRequest.getPhone(), userRequest.getTgUsername(),
                    userRequest.getFullName(), userRequest.getAvatarUrl(), userRequest.getPosition(),
                    userRequest.getEmployeeId(), userRequest.getRole(),
                    userRequest.getDepartmentId(), userRequest.getCreatedBy());
            return ApiResponseDto.success("User created successfully", UserVo.fromEntity(createdUser));
        } catch (Exception e) {
            log.error("Failed to create user", e);
            return ApiResponseDto.error("Failed to create user");
        }
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新用户",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "更新用户信息"
    )
    public ApiResponseDto<UserVo> updateUser(@PathVariable Long id, @RequestBody UserRequestDto userRequest) {
        try {
            UserEntity updatedUser = userService.updateUser(id, userRequest.getEmail(), userRequest.getPhone(),
                    userRequest.getTgUsername(), userRequest.getFullName(), userRequest.getAvatarUrl(),
                    userRequest.getPosition(), userRequest.getEmployeeId(), userRequest.getRole(),
                    userRequest.getDepartmentId(), userRequest.getActive(), userRequest.getUpdatedBy());
            if (updatedUser != null) {
                return ApiResponseDto.success("User updated successfully", UserVo.fromEntity(updatedUser));
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            log.error("Failed to update user: " + id, e);
            return ApiResponseDto.error("Failed to update user");
        }
    }

    @PostMapping("/{id}/login")
    public ApiResponseDto<Void> updateLoginInfo(@PathVariable Long id, @RequestBody Map<String, String> loginRequest) {
        try {
            String ipAddress = loginRequest.get("ipAddress");

            boolean updated = userService.updateLoginInfo(id, ipAddress);
            if (updated) {
                return ApiResponseDto.success("Login info updated successfully", null);
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to update login info: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/verify-email")
    public ApiResponseDto<Void> verifyEmail(@PathVariable Long id) {
        try {
            boolean verified = userService.verifyEmail(id);
            if (verified) {
                return ApiResponseDto.success("Email verified successfully", null);
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to verify email: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/verify-phone")
    public ApiResponseDto<Void> verifyPhone(@PathVariable Long id) {
        try {
            boolean verified = userService.verifyPhone(id);
            if (verified) {
                return ApiResponseDto.success("Phone verified successfully", null);
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to verify phone: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除用户",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "删除用户"
    )
    public ApiResponseDto<Void> deleteUser(@PathVariable Long id) {
        try {
            boolean deleted = userService.deleteUser(id);
            if (deleted) {
                return ApiResponseDto.success("User deleted successfully", null);
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to delete user: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/password")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "修改密码",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "用户修改密码",
        logRequest = false
    )
    public ApiResponseDto<Void> changePassword(@PathVariable Long id, @RequestBody Map<String, String> passwordRequest) {
        try {
            String oldPassword = passwordRequest.get("oldPassword");
            String newPassword = passwordRequest.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return ApiResponseDto.error("Both old and new passwords are required");
            }

            boolean changed = userService.changePassword(id, oldPassword, newPassword);
            if (changed) {
                return ApiResponseDto.success("Password changed successfully", null);
            } else {
                return ApiResponseDto.error("Failed to change password");
            }
        } catch (Exception e) {
            log.error("Failed to change password for user: " + id, e);
            return ApiResponseDto.error("Failed to change password");
        }
    }

    @PostMapping("/{id}/reset-password")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "重置密码",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "管理员重置用户密码",
        logRequest = false
    )
    public ApiResponseDto<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordRequest) {
        try {
            String newPassword = passwordRequest.get("newPassword");

            if (newPassword == null) {
                return ApiResponseDto.error("New password is required");
            }

            boolean reset = userService.resetPassword(id, newPassword);
            if (reset) {
                return ApiResponseDto.success("Password reset successfully", null);
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to reset password");
        }
    }

    @GetMapping("/department/{departmentId}")
    public ApiResponseDto<List<UserVo>> getUsersByDepartment(@PathVariable Long departmentId) {
        try {
            List<UserEntity> users = userService.getUsersByDepartment(departmentId);
            List<UserVo> result = users.stream().map(UserVo::fromEntity).toList();
            return ApiResponseDto.success("Users retrieved successfully", result);
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to retrieve users: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ApiResponseDto<List<UserVo>> searchUsers(@RequestParam String query) {
        try {
            List<UserEntity> users = userService.searchUsers(query);
            List<UserVo> result = users.stream().map(UserVo::fromEntity).toList();
            return ApiResponseDto.success("Users retrieved successfully", result);
        } catch (Exception e) {
            return ApiResponseDto.error("Failed to search users");
        }
    }
}
