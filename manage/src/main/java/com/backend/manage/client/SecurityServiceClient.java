package com.backend.manage.client;

import com.backend.manage.dto.login.JwtGenerateRequestDto;
import com.backend.manage.dto.login.JwtResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 安全服务Feign客户端接口 - 用于调用安全微服务的HTTP客户端
 *
 * 中文注释：这个接口使用Spring Cloud OpenFeign声明式HTTP客户端调用安全服务
 * 通过@FeignClient注解配置服务名称、URL和降级处理类
 * 提供了验证码验证、JWT令牌生成和验证等安全相关功能的远程调用
 */
@FeignClient(name = "${security.service.name:security}",
             url = "${security.service.url:}",
             fallback = SecurityServiceClientFallback.class,
             configuration = SecurityServiceClientConfig.class)
public interface SecurityServiceClient {

    /**
     * 验证验证码 - 调用安全服务验证用户输入的验证码是否正确
     *
     * @param request 请求参数，包含codeId（验证码ID）和code（用户输入的验证码）
     * @return 响应结果，包含验证状态和相关信息
     */
    @PostMapping("/verifyCode")
    Map<String, Object> verifyCode(@RequestBody Map<String, String> request);

    /**
     * 生成JWT令牌 - 调用安全服务生成新的JWT访问令牌
     *
     * @param request 请求参数，包含subject（主题）和claims（声明信息）
     * @return 响应结果，包含生成的令牌和过期时间等信息
     */
    @PostMapping("/generate")
    JwtResponseDto generateToken(@RequestBody JwtGenerateRequestDto request);

    /**
     * 验证JWT令牌 - 调用安全服务验证JWT令牌的有效性和完整性
     *
     * @param request 请求参数，包含要验证的令牌字符串
     * @return 响应结果，包含验证结果和解析出的声明信息
     */
    @PostMapping("/validate")
    Map<String, Object> validateToken(@RequestBody Map<String, String> request);
}
