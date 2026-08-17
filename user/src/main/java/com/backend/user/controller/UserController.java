package com.backend.user.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.user.entity.UserEntity;
import com.backend.user.mapper.UserMapper;
import com.backend.utils.CacheService;
import com.backend.utils.JwtUtil;
import com.backend.utils.exception.BizException;
import com.backend.user.vo.UserVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;
    private final JwtUtil jwtUtil;

    private Long getCurrentUserId(HttpServletRequest request) {
        // 信任 Gateway 上游认证
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                return Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id: {}", userIdStr);
            }
        }
        // Fallback: 从 Attribute 获取（Filter 设置）
        Object attr = request.getAttribute("userId");
        if (attr != null) {
            try {
                return Long.parseLong(attr.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid userId attribute: {}", attr);
            }
        }
        // 最后手段：解析 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 如有需要可以从 Security Feign 调 validate
        }
        return null;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponseDto<UserVo>> getProfile(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        UserEntity user = userMapper.findById(userId);
        if (user == null) throw new BizException(404, "User not found");
        return ResponseEntity.ok(ApiResponseDto.success("Profile retrieved successfully", UserVo.fromEntity(user)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponseDto<UserVo>> updateProfile(HttpServletRequest request, @RequestBody Map<String, Object> updates) {
        Long userId = getCurrentUserId(request);
        UserEntity existing = userMapper.findById(userId);
        if (existing == null) throw new BizException(404, "User not found");

        if (updates.containsKey("fullName")) existing.setFullName((String) updates.get("fullName"));
        if (updates.containsKey("email")) existing.setEmail((String) updates.get("email"));
        if (updates.containsKey("phone")) existing.setPhone((String) updates.get("phone"));
        if (updates.containsKey("tgUsername")) existing.setTgUsername((String) updates.get("tgUsername"));
        if (updates.containsKey("position")) existing.setPosition((String) updates.get("position"));
        if (updates.containsKey("departmentId")) {
            Object deptVal = updates.get("departmentId");
            existing.setDepartmentId(deptVal != null ? Long.valueOf(deptVal.toString()) : null);
        }
        if (updates.containsKey("avatarUrl")) existing.setAvatarUrl((String) updates.get("avatarUrl"));

        userMapper.update(existing);
        cacheService.clearByPrefix("bot:projectMembers:");
        try {
            cacheService.delete("user:" + userId);
            cacheService.delete("users:list");
        } catch (Exception e) {
            cacheService.clearByPrefix("user:");
            cacheService.clearByPrefix("users:");
        }
        return ResponseEntity.ok(ApiResponseDto.success("Profile updated successfully", UserVo.fromEntity(existing)));
    }

    @PutMapping("/resetVerification")
    public ResponseEntity<ApiResponseDto<Void>> resetVerification(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId(request);
        UserEntity existing = userMapper.findById(userId);
        if (existing == null) throw new BizException(404, "User not found");
        String type = body.get("type");
        if ("email".equals(type)) {
            existing.setEmailVerified(false);
        } else if ("telegram".equals(type)) {
            existing.setTgVerified(false);
        }
        userMapper.update(existing);
        cacheService.delete("user:" + userId);
        cacheService.delete("users:list");
        return ResponseEntity.ok(ApiResponseDto.success("Verification reset successfully", null));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponseDto<Void>> changePassword(HttpServletRequest request, @RequestBody Map<String, String> passwordRequest) {
        Long userId = getCurrentUserId(request);
        String oldPassword = passwordRequest.get("oldPassword");
        String newPassword = passwordRequest.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            throw new BizException(400, "Both old and new passwords are required");
        }

        UserEntity user = userMapper.findById(userId);
        if (user == null) throw new BizException(404, "User not found");

        boolean passwordMatch = passwordEncoder.matches(oldPassword, user.getPassword());
        log.info("Change password attempt for user {}: match={}, dbPassword starts with $2a={}",
            user.getUsername(), passwordMatch, user.getPassword().startsWith("$2a"));
        if (!passwordMatch) {
            throw new BizException(400, "Current password is incorrect");
        }

        userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
        cacheService.invalidateAllUserTokens(user.getUsername());

        return ResponseEntity.ok(ApiResponseDto.success("Password changed successfully, please login again", null));
    }
}