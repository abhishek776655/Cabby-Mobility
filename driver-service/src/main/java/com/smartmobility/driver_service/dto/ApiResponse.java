package com.smartmobility.driver_service.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    private LocalDateTime timestamp;
    private int status;
    private String error;
}
