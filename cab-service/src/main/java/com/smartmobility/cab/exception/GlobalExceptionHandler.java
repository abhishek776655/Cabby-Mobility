package com.smartmobility.cab.exception;


import com.smartmobility.cab.dto.ApiResponse;
import com.smartmobility.cab.dto.ApiResponseBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RideNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleRideNotFound(RideNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseBuilder.error(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                "RIDE_NOT_FOUND"
        ));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidState(InvalidStateTransitionException ex) {
        return ResponseEntity.badRequest().body(ApiResponseBuilder.error(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                "INVALID_STATE_TRANSITION"
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiResponseBuilder.error(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request",
                "VALIDATION_ERROR"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponseBuilder.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Something went wrong",
                "INTERNAL_SERVER_ERROR"
        ));
    }
}
