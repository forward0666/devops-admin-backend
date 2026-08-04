package com.backend.utils.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(200, message, data);
    }

    public static <T> ApiResponseDto<T> error(String message) {
        return new ApiResponseDto<>(500, message, null);
    }

    public static <T> ApiResponseDto<T> error(int code, String message) {
        return new ApiResponseDto<>(code, message, null);
    }

    public static <T> ApiResponseDto<T> error(int code, String message, T data) {
        return new ApiResponseDto<>(code, message, data);
    }
}