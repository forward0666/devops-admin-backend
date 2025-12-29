package com.backend.manage.vo;

import com.backend.manage.entity.UserEntity;

/**
 * 登录视图对象
 * 用于返回登录成功的响应信息
 *
 * 设计特点：
 * 1. 使用 Java 21 record 实现不可变数据结构
 * 2. 包含 JWT 令牌和用户信息
 * 3. 提供静态工厂方法 fromEntity() 用于转换
 * 4. 自动生成 getter 方法（如 token(), user()）
 * 5. 自动实现 equals(), hashCode(), toString()
 *
 * @author Backend Team
 * @version 2.0.0
 */
public record LoginVo(
        /**
         * JWT 令牌
         *
         * 用于后续 API 请求的身份验证
         */
        String token,

        /**
         * 用户视图对象
         *
         * 包含用户的基本信息和权限
         */
        UserVo user
) {
    /**
     * 从用户实体创建登录视图对象
     *
     * @param token JWT 令牌
     * @param userEntity 用户实体
     * @return 登录视图对象
     */
    public static LoginVo fromEntity(String token, UserEntity userEntity) {
        return new LoginVo(token, UserVo.fromUser(userEntity));
    }
}
