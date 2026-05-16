package com.smartmobility.gateway.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static final String SECRET = "test-secret-key-for-auth-and-gateway-123456";

    @Test
    void validatesAndExtractsClaimsFromAuthCompatibleToken() {
        JwtUtils jwtUtils = new JwtUtils(SECRET);

        String token = Jwts.builder()
                .subject("42")
                .claim("email", "rider@example.com")
                .claim("roles", Set.of("RIDER", "USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey())
                .compact();

        assertTrue(jwtUtils.validateToken(token));
        assertEquals(42L, jwtUtils.extractUserId(token));
        assertEquals("rider@example.com", jwtUtils.extractEmail(token));
        assertEquals(Set.of("RIDER", "USER"), jwtUtils.extractRoles(token));
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }
}
