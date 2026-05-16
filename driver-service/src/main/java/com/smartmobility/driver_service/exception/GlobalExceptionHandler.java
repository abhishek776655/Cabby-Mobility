package com.smartmobility.driver_service.exception;


import com.smartmobility.driver_service.dto.ApiResponse;
import com.smartmobility.driver_service.dto.ApiResponseBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleDriverNotFound(DriverNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponseBuilder.error(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        ErrorCodes.DRIVER_NOT_FOUND
                ));
    }

    @ExceptionHandler(DriverAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<?>> handleDriverAlreadyExists(DriverAlreadyExistsException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseBuilder.error(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        ErrorCodes.DRIVER_ALREADY_EXISTS
                ));
    }

    // 🔴 Validation errors
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseBuilder.error(
                        HttpStatus.BAD_REQUEST.value(),
                        message,
                        ErrorCodes.VALIDATION_FAILED
                ));
    }

    // 🔴 Fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseBuilder.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error",
                        ErrorCodes.INTERNAL_ERROR
                ));
    }
}