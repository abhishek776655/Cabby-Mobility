package com.smartmobility.cab.exception;


import com.smartmobility.cab.dto.ApiResponse;
import com.smartmobility.cab.dto.ApiResponseBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RideNotFoundException.class)
    public ApiResponse<?> handleRideNotFound(RideNotFoundException ex) {
        return ApiResponseBuilder.error(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                "RIDE_NOT_FOUND"
        );
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ApiResponse<?> handleInvalidState(InvalidStateTransitionException ex) {
        return ApiResponseBuilder.error(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                "INVALID_STATE_TRANSITION"
        );
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleGeneric(Exception ex) {
        return ApiResponseBuilder.error(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Something went wrong",
                "INTERNAL_SERVER_ERROR"
        );
    }
}