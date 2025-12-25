package com.admin.manage.dto;

import com.admin.manage.model.User;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录响应数据传输对象
 * 包含登录成功后返回给前端的信息
 * 
 * @author Admin
 * @version 1.0
 * @since 2024
 */
@Setter
@Getter
public class LoginResponse {
    private String token; // JWT令牌，用于后续请求的身份验证
    private User user;    // 用户信息，包含用户基本数据和权限信息

    /**
     * 构造函数
     * 
     * @param token JWT令牌
     * @param user 用户信息对象
     */
    public LoginResponse(String token, User user) {
        this.token = token;
        this.user = user;
    }
}