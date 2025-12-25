package com.admin.manage.dto;

// 使用Java 21的record特性，简化不可变数据载体
// 泛型record，支持任意类型的数据
public record ApiResponse<T>(
    int code,
    String message,
    T data
) {
    
    // 定义标准状态码常量
    public static final int SUCCESS_CODE = 200;
    public static final int ERROR_CODE = 500;
    public static final int NOT_FOUND_CODE = 404;
    public static final int BAD_REQUEST_CODE = 400;
    
    // 提供便利的静态工厂方法
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data);
    }
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "操作成功", data);
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ERROR_CODE, message, null);
    }
    
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
    
    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(NOT_FOUND_CODE, message, null);
    }
    
    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(BAD_REQUEST_CODE, message, null);
    }
    
    // 便利方法，快速判断响应是否成功
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}