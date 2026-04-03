package com.smartmobility.auth.exception;

public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException(String message) {
        super(message);
    }
}
