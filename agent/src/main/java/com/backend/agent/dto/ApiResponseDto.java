package com.backend.agent.dto;

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
}