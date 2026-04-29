package com.backend.user.controller;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.UserEntity;
import com.backend.user.mapper.UserMapper;
import com.backend.user.service.CacheService;
import com.backend.user.util.JwtUtil;
import com.backend.user.vo.UserVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        return null;
    }

    @GetMapping("/profile")
    public ApiResponseDto<UserVo> getProfile(HttpServletRequest request) {
        try {
            Long userId = getCurrentUserId(request);
            UserEntity user = userMapper.findById(userId);
            if (user != null) {
                return ApiResponseDto.success("Profile retrieved successfully", UserVo.fromEntity(user));
            } else {
                return ApiResponseDto.error("User not found");
            }
        } catch (Exception e) {
            log.error("Failed to retrieve profile", e);
            return ApiResponseDto.error("Failed to retrieve profile");
        }
    }

    @PutMapping("/profile")
    public ApiResponseDto<UserVo> updateProfile(HttpServletRequest request, @RequestBody Map<String, Object> updates) {
        try {
            Long userId = getCurrentUserId(request);
            UserEntity existing = userMapper.findById(userId);
            if (existing == null) {
                return ApiResponseDto.error("User not found");
            }

            if (updates.containsKey("fullName")) existing.setFullName((String) updates.get("fullName"));
            if (updates.containsKey("email")) existing.setEmail((String) updates.get("email"));
            if (updates.containsKey("phone")) existing.setPhone((String) updates.get("phone"));
            if (updates.containsKey("tgUsername")) existing.setTgUsername((String) updates.get("tgUsername"));
            if (updates.containsKey("position")) existing.setPosition((String) updates.get("position"));
            if (updates.containsKey("avatarUrl")) existing.setAvatarUrl((String) updates.get("avatarUrl"));

            userMapper.update(existing);
            return ApiResponseDto.success("Profile updated successfully", UserVo.fromEntity(existing));
        } catch (Exception e) {
            log.error("Failed to update profile", e);
            return ApiResponseDto.error("Failed to update profile");
        }
    }

    @PutMapping("/password")
    public ApiResponseDto<Void> changePassword(HttpServletRequest request, @RequestBody Map<String, String> passwordRequest) {
        try {
            Long userId = getCurrentUserId(request);
            String oldPassword = passwordRequest.get("oldPassword");
            String newPassword = passwordRequest.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return ApiResponseDto.error("Both old and new passwords are required");
            }

            UserEntity user = userMapper.findById(userId);
            if (user == null) {
                return ApiResponseDto.error("User not found");
            }

            // Verify old password
            boolean passwordMatch = passwordEncoder.matches(oldPassword, user.getPassword());
            log.info("Change password attempt for user {}: match={}, dbPassword starts with $2a={}",
                user.getUsername(), passwordMatch, user.getPassword().startsWith("$2a"));
            if (!passwordMatch) {
                return ApiResponseDto.error("Current password is incorrect");
            }

            // Update password using dedicated method
            userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword));

            // Clear all Redis tokens for this user to force re-login
            if (cacheService != null) {
                cacheService.invalidateAllUserTokens(user.getUsername());
            }

            return ApiResponseDto.success("Password changed successfully, please login again", null);
        } catch (Exception e) {
            log.error("Failed to change password", e);
            return ApiResponseDto.error("Failed to change password");
        }
    }
}
