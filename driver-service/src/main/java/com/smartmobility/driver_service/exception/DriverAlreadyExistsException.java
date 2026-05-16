package com.smartmobility.driver_service.exception;

public class DriverAlreadyExistsException extends RuntimeException {
    public DriverAlreadyExistsException(String message) {
        super(message);
    }
}