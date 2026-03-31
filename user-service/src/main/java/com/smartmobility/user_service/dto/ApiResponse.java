package com.smartmobility.user_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApiResponse<T> {

    private LocalDateTime timestamp;
    private int status;
    private T data;
}