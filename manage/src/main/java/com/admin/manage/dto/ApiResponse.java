package com.admin.manage.dto;

/**
 * 统一API响应对象
 * 用于标准化所有REST API的响应格式
 * 
 * @param <T> 响应数据的类型
 * 
 * @author Admin
 * @version 1.0
 * @since 2024
 */
public class ApiResponse<T> {
    private int code;        // 响应状态码
    private String message;  // 响应消息
    private T data;          // 响应数据

    /**
     * 默认构造函数
     */
    public ApiResponse() {}

    /**
     * 全参数构造函数
     * 
     * @param code 状态码
     * @param message 消息
     * @param data 数据
     */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应
     * 
     * @param <T> 数据类型
     * @param message 成功消息
     * @param data 响应数据
     * @return ApiResponse<T> 成功响应对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /**
     * 创建错误响应（默认500错误码）
     * 
     * @param <T> 数据类型
     * @param message 错误消息
     * @return ApiResponse<T> 错误响应对象
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }

    /**
     * 创建自定义错误响应
     * 
     * @param <T> 数据类型
     * @param code 自定义错误码
     * @param message 错误消息
     * @return ApiResponse<T> 错误响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ==================== Getter和Setter方法 ====================

    /**
     * 获取状态码
     * 
     * @return int 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置状态码
     * 
     * @param code 状态码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取响应消息
     * 
     * @return String 消息内容
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置响应消息
     * 
     * @param message 消息内容
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取响应数据
     * 
     * @return T 响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据
     * 
     * @param data 响应数据
     */
    public void setData(T data) {
        this.data = data;
    }
}