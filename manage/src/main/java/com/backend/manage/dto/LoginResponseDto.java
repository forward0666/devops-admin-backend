package com.backend.manage.dto;

import com.backend.manage.entity.UserEntity;

/**
 * 登录响应数据传输对象
 * 包含登录成功后返回给前端的信息
 *
 * 设计特点：
 * 1. 使用 Java 21 record 实现不可变数据结构
 * 2. 包含 JWT 令牌和用户完整信息
 * 3. 自动生成 getter 方法（如 token(), user()）
 * 4. 自动实现 equals(), hashCode(), toString()
 *
 * 响应内容：
 * - token: JWT 令牌，用于后续请求的身份验证
 * - user: 用户完整信息，包含基本数据和权限信息
 *
 * @author Backend Team
 * @version 2.0.0
 */
public record LoginResponseDto(
        /**
         * JWT 令牌
         *
         * 用于后续 API 请求的身份验证
         * 在请求头中格式为: Authorization: Bearer {token}
         */
        String token,

        /**
         * 用户信息
         *
         * 包含用户的基本数据、角色、权限等完整信息
         */
        UserEntity user
) {}