package com.smartmobility.matchmaking.exception;

public class InvalidDispatchStateException extends DispatchException {
    public InvalidDispatchStateException(String message) {
        super(message, "INVALID_STATE");
    }
}