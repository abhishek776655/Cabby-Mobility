package com.smartmobility.user_service.config;

import com.smartmobility.user_service.dto.ApiResponse;

import java.time.LocalDateTime;

public class ApiResponseBuilder {

    public static <T> ApiResponse<T> success(T data, int status) {
        return ApiResponse.<T>builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .data(data)
                .build();
    }
}