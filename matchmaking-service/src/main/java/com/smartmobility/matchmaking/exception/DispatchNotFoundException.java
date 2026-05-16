package com.smartmobility.matchmaking.exception;

public class DispatchNotFoundException extends DispatchException {
    public DispatchNotFoundException(String message) {
        super(message, "DISPATCH_NOT_FOUND");
    }
}