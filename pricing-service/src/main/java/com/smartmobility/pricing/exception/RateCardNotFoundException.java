package com.smartmobility.pricing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RateCardNotFoundException extends RuntimeException {
    public RateCardNotFoundException(String message) {
        super(message);
    }
}
