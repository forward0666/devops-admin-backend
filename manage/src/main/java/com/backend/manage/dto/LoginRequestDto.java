package com.backend.manage.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求数据传输对象
 * 用于接收前端传递的登录信息
 *
 * 设计特点：
 * 1. 使用 Lombok @Data 注解自动生成 getter/setter
 * 2. 使用验证注解确保输入数据有效性
 * 3. 支持用户名/密码登录和验证码验证
 *
 * @author Backend Team
 * @version 2.0.0
 */
public record LoginRequestDto(
        /**
         * 用户名
         *
         * 验证规则：不能为空
         */
        @NotBlank(message = "用户名不能为空")
        String username,

        /**
         * 密码
         *
         * 验证规则：不能为空
         */
        @NotBlank(message = "密码不能为空")
        String password,

        /**
         * 验证码
         *
         * 用于图形验证码验证，防止自动化攻击
         */
        String verificationCode,

        /**
         * 验证码密钥
         *
         * 用于验证码服务端验证
         */
        String verificationCodeKey
) {}
