package com.mobility.realtime.security;

/**
 * Thrown from the STOMP inbound channel interceptor to reject an unauthenticated CONNECT
 * or an unauthorized SUBSCRIBE. Spring's STOMP support converts this into a STOMP ERROR
 * frame and closes the session.
 */
public class StompAuthorizationException extends RuntimeException {
    public StompAuthorizationException(String message) {
        super(message);
    }
}
