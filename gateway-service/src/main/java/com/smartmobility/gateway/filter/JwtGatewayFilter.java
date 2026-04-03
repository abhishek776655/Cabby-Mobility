package com.smartmobility.gateway.filter;

import com.smartmobility.gateway.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {
    private final JwtUtils jwtUtil;

    public JwtGatewayFilter(JwtUtils jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().toString();

        // 1. Skip public routes
        if (path.startsWith("/auth")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            return unauthorized(exchange);
        }

        // 🔥 Extract data from JWT
        Long userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);

        // 🔥 Add headers
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
                .build();

        // 🔥 Continue with modified request
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}