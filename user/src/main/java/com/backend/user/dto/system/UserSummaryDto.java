package com.backend.user.dto.system;

import com.backend.user.entity.system.UserEntity;

/**
 * 用户摘要数据传输对象
 * 用于展示简化的用户信息
 * 使用 Java 21 record 实现不可变数据结构
 *
 * 设计特点：
 * 1. 使用 Record 实现不可变对象，确保线程安全和数据一致性
 * 2. 自动生成 getter 方法（如 id(), username()）
 * 3. 自动实现 equals(), hashCode(), toString()
 * 4. 提供静态工厂方法 fromUser() 用于转换
 *
 * @author Backend Team
 * @version 2.0.0
 */
public record UserSummaryDto(
        /**
         * 用户唯一标识符
         */
        Long id,

        /**
         * 用户名
         */
        String username,

        /**
         * 用户全名
         */
        String fullName,

        /**
         * 用户邮箱
         */
        String email,

        /**
         * 用户角色
         */
        String role,

        /**
         * 用户状态
         */
        boolean active
) {
    /**
     * 从用户实体转换为用户摘要
     *
     * @param user 用户实体
     * @return 用户摘要
     */
    public static UserSummaryDto fromUser(UserEntity user) {
        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isActive()
        );
    }
}
