package com.backend.manage.controller;

import com.backend.manage.dto.ApiResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 验证码控制器
 * 负责生成和管理登录验证码
 * 
 * @author Admin
 * @version 1.0
 * @since 2024
 */
@RestController
public class VerificationCodeController {

    /**
     * 获取登录验证码
     * 生成一个验证码和对应的密钥，用于登录时的验证
     * 
     * 在实际实现中，验证码会与密钥一起存储到缓存中，用于后续验证
     * 当前为模拟实现，使用固定的验证码和随机生成的密钥
     * 
     * @return ApiResponseDto<Map<String, String>> 包含验证码和密钥的响应对象
     *         - verificationCode: 验证码字符串
     *         - verificationCodeKey: 验证码密钥，用于后续验证
     */
    @GetMapping("/verification/code")
    public ApiResponseDto<Map<String, String>> getVerificationCode() {
        try {
            // 在实际实现中，这里会生成一个真实的验证码（如图形验证码）
            // 并将验证码和密钥存储到缓存中（如Redis），设置过期时间
            // 后续登录验证时会使用密钥来验证用户输入的验证码
            
            // 当前为模拟实现，使用固定验证码和随机密钥
            String verificationCode = "1234"; // 模拟验证码
            String codeKey = UUID.randomUUID().toString(); // 生成随机密钥
            
            // 构建响应数据
            Map<String, String> response = new HashMap<>();
            response.put("verificationCode", verificationCode);
            response.put("verificationCodeKey", codeKey);
            
            // 返回成功响应
            return ApiResponseDto.success("验证码生成成功", response);
        } catch (Exception e) {
            // 处理异常情况
            return ApiResponseDto.error("生成验证码失败: " + e.getMessage());
        }
    }
}