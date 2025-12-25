package com.backend.manage.dto;

/**
 * 登录请求数据传输对象
 * 用于接收前端传递的登录信息
 * 
 * @author Admin
 * @version 1.0
 * @since 2024
 */
public class LoginRequest {
    private String username;            // 用户名
    private String password;            // 密码
    private String verificationCode;    // 验证码
    private String verificationCodeKey; // 验证码密钥

    /**
     * 获取用户名
     * 
     * @return String 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名
     * 
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取密码
     * 
     * @return String 密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码
     * 
     * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取验证码
     * 
     * @return String 验证码
     */
    public String getVerificationCode() {
        return verificationCode;
    }

    /**
     * 设置验证码
     * 
     * @param verificationCode 验证码
     */
    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    /**
     * 获取验证码密钥
     * 
     * @return String 验证码密钥
     */
    public String getVerificationCodeKey() {
        return verificationCodeKey;
    }

    /**
     * 设置验证码密钥
     * 
     * @param verificationCodeKey 验证码密钥
     */
    public void setVerificationCodeKey(String verificationCodeKey) {
        this.verificationCodeKey = verificationCodeKey;
    }
}