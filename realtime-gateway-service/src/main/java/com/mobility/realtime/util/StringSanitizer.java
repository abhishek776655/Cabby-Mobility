package com.mobility.realtime.util;

public final class StringSanitizer {

    private StringSanitizer() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
