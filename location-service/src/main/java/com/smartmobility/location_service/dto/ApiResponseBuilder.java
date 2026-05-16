package com.smartmobility.location_service.dto;

import java.time.LocalDateTime;

public class ApiResponseBuilder {

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .timestamp(LocalDateTime.now())
                .status(200)
                .success(true)
                .data(data)
                .message(message)
                .error(null)
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, String error) {
        return ApiResponse.<T>builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .success(false)
                .data(null)
                .message(message)
                .error(error)
                .build();
    }
}
