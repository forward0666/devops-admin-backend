package com.backend.login.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应数据传输对象
 * 用于标准化所有REST API的响应格式
 *
 * 设计特点：
 * 1. 使用泛型 <T> 支持任意类型的响应数据
 * 2. 使用 Lombok @Data 注解自动生成 getter/setter
 * 3. 提供便捷的静态工厂方法创建成功/失败响应
 * 4. 标准化的响应格式：{ code, message, data }
 *
 * 响应格式示例：
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": { ... }
 * }
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto<T> {

    /**
     * 响应状态码
     *
     * 标准状态码：
     * - 200: 操作成功
     * - 400: 请求参数错误
     * - 401: 未授权
     * - 403: 禁止访问
     * - 404: 资源不存在
     * - 500: 服务器内部错误
     */
    private int code;

    /**
     * 响应消息
     *
     * 描述操作结果或错误信息
     */
    private String message;

    /**
     * 响应数据
     *
     * 实际的业务数据，可能为 null
     */
    private T data;

    /**
     * 创建成功响应（200状态码）
     *
     * @param <T> 数据类型
     * @param message 成功消息
     * @param data 响应数据
     * @return ApiResponseDto<T> 成功响应对象
     */
    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(200, message, data);
    }

    /**
     * 创建成功响应（带默认消息）
     *
     * @param <T> 数据类型
     * @param data 响应数据
     * @return ApiResponseDto<T> 成功响应对象
     */
    public static <T> ApiResponseDto<T> success(T data) {
        return new ApiResponseDto<>(200, "操作成功", data);
    }

    /**
     * 创建错误响应（500状态码）
     *
     * @param <T> 数据类型
     * @param message 错误消息
     * @return ApiResponseDto<T> 错误响应对象
     */
    public static <T> ApiResponseDto<T> error(String message) {
        return new ApiResponseDto<>(500, message, null);
    }

    /**
     * 创建自定义错误响应
     *
     * @param <T> 数据类型
     * @param code 自定义错误码
     * @param message 错误消息
     * @return ApiResponseDto<T> 错误响应对象
     */
    public static <T> ApiResponseDto<T> error(int code, String message) {
        return new ApiResponseDto<>(code, message, null);
    }

    /**
     * 创建错误响应（带额外数据）
     *
     * @param <T> 数据类型
     * @param message 错误消息
     * @param data 额外的错误数据
     * @return ApiResponseDto<T> 错误响应对象
     */
    public static <T> ApiResponseDto<T> error(String message, T data) {
        return new ApiResponseDto<>(500, message, data);
    }
}
