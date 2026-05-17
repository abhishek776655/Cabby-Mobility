package com.smartmobility.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    private LocalDateTime timestamp;
    private int status;
    private String error;
}