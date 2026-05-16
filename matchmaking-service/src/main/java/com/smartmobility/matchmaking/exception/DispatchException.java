package com.smartmobility.matchmaking.exception;

public class DispatchException extends RuntimeException {
    private final String errorCode;

    public DispatchException(String message) {
        super(message);
        this.errorCode = "DISPATCH_ERROR";
    }

    public DispatchException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}