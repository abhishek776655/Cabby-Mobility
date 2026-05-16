package com.smartmobility.gateway.utils;

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

    private SecretKey getSigningKey() {
        return signingKey;
    }

    // 🔹 Validate Token
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 🔹 Extract Claims
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(extractClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        Object roles = extractClaims(token).get("roles");
        if (roles instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        throw new IllegalArgumentException("Invalid roles claim in token");
    }
}
