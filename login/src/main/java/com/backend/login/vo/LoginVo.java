package com.backend.login.vo;

import com.backend.login.entity.UserEntity;

public record LoginVo(
    String token,
    Long id,
    String username,
    String fullName,
    String email,
    String role,
    String avatar
) {
    public static LoginVo from(String token, UserEntity user) {
        return new LoginVo(
            token,
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.getAvatar()
        );
    }
}
