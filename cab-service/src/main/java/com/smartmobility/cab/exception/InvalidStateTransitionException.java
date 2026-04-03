package com.smartmobility.cab.exception;

public class InvalidStateTransitionException extends RuntimeException{
    public InvalidStateTransitionException(String message) {
        super(message);
    }

}
