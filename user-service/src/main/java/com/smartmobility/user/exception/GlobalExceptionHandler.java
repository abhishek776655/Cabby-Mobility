package com.smartmobility.user.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // ✅ User not found → 404
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<?> handleUserNotFound(UserNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                        "timestamp", LocalDateTime.now(),
                        "status", 404,
                        "error", "NOT_FOUND",
                        "message", ex.getMessage()
                )
        );
    }

    // ✅ Duplicate user → 409
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<?> handleDuplicateUser(DuplicateUserException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of(
                        "timestamp", LocalDateTime.now(),
                        "status", 409,
                        "error", "CONFLICT",
                        "message", ex.getMessage()
                )
        );
    }

    // ✅ Validation errors → 400
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(Exception ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of(
                        "timestamp", LocalDateTime.now(),
                        "status", 400,
                        "error", "VALIDATION_ERROR",
                        "message", "Invalid request"
                )
        );
    }

    // ✅ Fallback → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of(
                        "timestamp", LocalDateTime.now(),
                        "status", 500,
                        "error", "INTERNAL_SERVER_ERROR",
                        "message", ex.getMessage()
                )
        );
    }
}
