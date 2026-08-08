package com.mobility.realtime.security;

import java.security.Principal;
import java.util.Set;

/**
 * Authenticated WebSocket session identity, derived from the JWT presented at STOMP CONNECT.
 */
public record RealtimePrincipal(Long userId, Set<String> roles) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
