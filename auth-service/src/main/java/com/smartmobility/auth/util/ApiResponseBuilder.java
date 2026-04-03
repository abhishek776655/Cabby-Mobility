package com.smartmobility.auth.util;

import com.smartmobility.auth.dto.ApiResponse;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public class ApiResponseBuilder {

    public static <T> ApiResponse<T> success(T data, String message, HttpStatus status) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(null)
                .build();
    }

    public static ApiResponse<Void> error(String message, HttpStatus status) {
        return ApiResponse.<Void>builder()
                .success(false)
                .data(null)
                .message(message)
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .build();
    }
    public static <T> ApiResponse<T> error(String message, HttpStatus status,T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .data(data)
                .message(message)
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .build();
    }
}