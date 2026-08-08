package com.smartmobility.location_service.exception;

import com.smartmobility.location_service.dto.ApiResponse;
import com.smartmobility.location_service.dto.ApiResponseBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidLocationException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidLocation(InvalidLocationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseBuilder.error(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        ErrorCodes.INVALID_LOCATION
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseBuilder.error(
                        HttpStatus.BAD_REQUEST.value(),
                        message,
                        ErrorCodes.VALIDATION_FAILED
                ));
    }

    @ExceptionHandler(LocationServiceException.class)
    public ResponseEntity<ApiResponse<?>> handleLocationService(LocationServiceException ex) {
        log.error("Location service error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseBuilder.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ex.getMessage(),
                        ErrorCodes.LOCATION_SERVICE_ERROR
                ));
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    public ResponseEntity<ApiResponse<?>> handleForbidden(ForbiddenAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseBuilder.error(
                        HttpStatus.FORBIDDEN.value(),
                        ex.getMessage(),
                        ErrorCodes.FORBIDDEN
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unexpected location service error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseBuilder.error(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error",
                        ErrorCodes.INTERNAL_ERROR
                ));
    }
}
