package com.smartmobility.payment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiSecurityFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${internal.api.secret}")
    private String internalApiSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX) && !isAuthorized(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing or invalid internal service credentials");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String provided = request.getHeader(SECRET_HEADER);
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                internalApiSecret.getBytes(StandardCharsets.UTF_8));
    }
}
