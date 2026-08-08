package com.mobility.realtime.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JwtUtils {

    private final SecretKey signingKey;

    public JwtUtils(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns the authenticated principal, or null if the token is missing/invalid/expired.
     */
    public RealtimePrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = extractClaims(token);
            Long userId = Long.parseLong(claims.getSubject());
            return new RealtimePrincipal(userId, extractRoles(claims));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        return Set.of();
    }
}
