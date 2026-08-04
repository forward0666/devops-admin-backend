package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.UserEntity;
import com.backend.manage.service.UserService;
import com.backend.manage.vo.UserVo;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.backend.manage.dto.UserRequestDto;
import com.backend.utils.CacheService;
import com.backend.manage.util.AccessValidator;
import com.backend.utils.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CacheService cacheService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<UserVo>>> getAllUsers() {
        List<UserEntity> users = userService.getAllUsers();
        List<UserVo> result = users.stream()
                .map(u -> UserVo.fromEntity(u, isLocked(u.getUsername())))
                .toList();
        return ResponseEntity.ok(ApiResponseDto.success("Users retrieved successfully", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserVo>> getUserById(@PathVariable Long id) {
        UserEntity user = userService.getUserById(id);
        if (user == null) throw new BizException(404, "User not found");
        return ResponseEntity.ok(ApiResponseDto.success("User retrieved successfully",
            UserVo.fromEntity(user, isLocked(user.getUsername()))));
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建用户",
        resourceType = "USER",
        description = "创建新用户"
    )
    public ResponseEntity<ApiResponseDto<UserVo>> createUser(@RequestBody UserRequestDto userRequest) {
        if (userRequest.getUsername() == null) throw new BizException(400, "Username is required");
        if (userRequest.getPassword() == null) throw new BizException(400, "Password is required");
        if (userRequest.getRole() == null) throw new BizException(400, "Role is required");

        UserEntity createdUser = userService.createUser(
                userRequest.getUsername(), userRequest.getPassword(),
                userRequest.getEmail(), userRequest.getPhone(), userRequest.getTgUsername(),
                userRequest.getFullName(), userRequest.getAvatarUrl(), userRequest.getPosition(),
                userRequest.getEmployeeId(), userRequest.getRole(),
                userRequest.getDepartmentId(), userRequest.getCreatedBy());
        return ResponseEntity.ok(ApiResponseDto.success("User created successfully", UserVo.fromEntity(createdUser)));
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新用户",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "更新用户信息"
    )
    public ResponseEntity<ApiResponseDto<UserVo>> updateUser(@PathVariable Long id, @RequestBody UserRequestDto userRequest) {
        UserEntity updatedUser = userService.updateUser(id, userRequest.getEmail(), userRequest.getPhone(),
                userRequest.getTgUsername(), userRequest.getFullName(), userRequest.getAvatarUrl(),
                userRequest.getPosition(), userRequest.getEmployeeId(), userRequest.getRole(),
                userRequest.getDepartmentId(), userRequest.getActive(), userRequest.getUpdatedBy());
        if (updatedUser == null) throw new BizException(404, "User not found");
        cacheService.clearByPrefix("bot:projectMembers:");
        cacheService.delete("user:" + id);
        cacheService.delete("users:list");
        return ResponseEntity.ok(ApiResponseDto.success("User updated successfully", UserVo.fromEntity(updatedUser)));
    }

    @PostMapping("/{id}/login")
    public ResponseEntity<ApiResponseDto<Void>> updateLoginInfo(@PathVariable Long id, @RequestBody Map<String, String> loginRequest) {
        String ipAddress = loginRequest.get("ipAddress");
        boolean updated = userService.updateLoginInfo(id, ipAddress);
        if (!updated) throw new BizException(404, "User not found");
        return ResponseEntity.ok(ApiResponseDto.success("Login info updated successfully", null));
    }

    @PostMapping("/{id}/verify-email")
    public ResponseEntity<ApiResponseDto<Void>> verifyEmail(@PathVariable Long id) {
        boolean verified = userService.verifyEmail(id);
        if (!verified) throw new BizException(404, "User not found");
        return ResponseEntity.ok(ApiResponseDto.success("Email verified successfully", null));
    }

    @PostMapping("/{id}/verify-phone")
    public ResponseEntity<ApiResponseDto<Void>> verifyPhone(@PathVariable Long id) {
        boolean verified = userService.verifyPhone(id);
        if (!verified) throw new BizException(404, "User not found");
        return ResponseEntity.ok(ApiResponseDto.success("Phone verified successfully", null));
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除用户",
        resourceType = "USER",
        resourceIdIndex = 0,
        description = "删除用户"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        AccessValidator.validate(request, jwtUtil, "sys_admin", "admin");
        boolean deleted = userService.deleteUser(id);
        if (!deleted) throw new BizException(404, "User not found");
        cacheService.delete("user:" + id);
        cacheService.delete("users:list");
        return ResponseEntity.ok(ApiResponseDto.success("User deleted successfully", null));
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
    public ResponseEntity<ApiResponseDto<Void>> changePassword(@PathVariable Long id, @RequestBody Map<String, String> passwordRequest) {
        String oldPassword = passwordRequest.get("oldPassword");
        String newPassword = passwordRequest.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            throw new BizException(400, "Both old and new passwords are required");
        }
        boolean changed = userService.changePassword(id, oldPassword, newPassword);
        if (!changed) throw new BizException(400, "Failed to change password");
        return ResponseEntity.ok(ApiResponseDto.success("Password changed successfully", null));
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
    public ResponseEntity<ApiResponseDto<Void>> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordRequest, HttpServletRequest request) {
        AccessValidator.validate(request, jwtUtil, "sys_admin", "admin");
        String newPassword = passwordRequest.get("newPassword");
        if (newPassword == null) throw new BizException(400, "New password is required");
        boolean reset = userService.resetPassword(id, newPassword);
        if (!reset) throw new BizException(404, "User not found");
        return ResponseEntity.ok(ApiResponseDto.success("Password reset successfully", null));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<ApiResponseDto<List<UserVo>>> getUsersByDepartment(@PathVariable Long departmentId) {
        List<UserEntity> users = userService.getUsersByDepartment(departmentId);
        List<UserVo> result = users.stream().map(UserVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Users retrieved successfully", result));
    }

    @PostMapping("/unlock/{id}")
    public ResponseEntity<ApiResponseDto<Void>> unlockUser(@PathVariable Long id, HttpServletRequest request) {
        AccessValidator.validate(request, jwtUtil, "sys_admin", "admin");
        UserEntity user = userService.getUserById(id);
        if (user == null) throw new BizException(404, "User not found");
        cacheService.delete("login:lock:" + user.getUsername());
        cacheService.delete("login:fail:" + user.getUsername());
        log.info("🔓 Unlocked user: {}", user.getUsername());
        return ResponseEntity.ok(ApiResponseDto.success("User unlocked", null));
    }

    private boolean isLocked(String username) {
        if (username == null) return false;
        Object locked = cacheService.get("login:lock:" + username);
        return locked != null && Boolean.TRUE.equals(locked);
    }
}