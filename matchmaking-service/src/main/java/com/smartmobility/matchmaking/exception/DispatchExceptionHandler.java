package com.smartmobility.matchmaking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class DispatchExceptionHandler {

    @ExceptionHandler(DispatchNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDispatchNotFound(DispatchNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("success", false, "error", ex.getErrorCode(), "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(ReservationExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleReservationExpired(ReservationExpiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("success", false, "error", ex.getErrorCode(), "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(InvalidDispatchStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidDispatchStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("success", false, "error", ex.getErrorCode(), "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(DispatchException.class)
    public ResponseEntity<Map<String, Object>> handleDispatchError(DispatchException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("success", false, "error", ex.getErrorCode(), "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }
}