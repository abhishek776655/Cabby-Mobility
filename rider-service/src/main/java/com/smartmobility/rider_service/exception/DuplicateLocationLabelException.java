package com.smartmobility.rider_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateLocationLabelException extends RuntimeException {
    public DuplicateLocationLabelException(String message) {
        super(message);
    }
}
