package com.smartmobility.gateway.filter;

import com.smartmobility.gateway.utils.JwtUtils;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    // Role-based route permissions
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", Set.of(
                    "/auth/**",
                    "/users/**",
                    "/cab/**",
                    "/rides/**",
                    "/dispatch/**",
                    "/driver/**",
                    "/drivers/**",
                    "/location/**",
                    "/matchmaking/**",
                    "/riders/**"
            ),
            "DRIVER", Set.of(
                    "/driver/**",
                    "/drivers/**",
                    "/location/**",
                    "/matchmaking/**",
                    "/dispatch/**",
                    "/rides/*/start",
                    "/rides/*/complete"
            ),
            "RIDER", Set.of(
                    "/users/**",
                    "/cab/**",
                    "/rides",
                    "/rides/*",
                    "/rides/*/cancel",
                    "/rides/*/retry",
                    "/dispatch/**",
                    "/matchmaking/**",
                    "/riders/**",
                    "/fares/**"
            )
    );

    public JwtGatewayFilter(JwtUtils jwtUtil, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
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

        return redisTemplate.hasKey("blacklist:" + token)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        return unauthorized(exchange);
                    }

                    // Extract data from JWT
                    Long userId = jwtUtil.extractUserId(token);
                    Set<String> roles = jwtUtil.extractRoles(token);

                    // Check role-based permissions
                    if (!hasPermission(path, roles)) {
                        return forbidden(exchange);
                    }

                    // Add headers with user info
                    String rolesHeader = String.join(",", roles);
                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            .header("X-User-Id", String.valueOf(userId))
                            .header("X-User-Role", rolesHeader)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    private boolean hasPermission(String path, Set<String> roles) {
        // ADMIN has access to everything
        if (roles.contains("ADMIN")) {
            return true;
        }

        // Check if any of user's roles have permission for this path
        for (String role : roles) {
            Set<String> allowedPaths = ROLE_PERMISSIONS.get(role);
            if (allowedPaths != null) {
                for (String allowedPath : allowedPaths) {
                    if (pathMatches(path, allowedPath)) {
                        return true;
                    }
                }
            }
        }

        // No matching permission found
        return false;
    }

    private boolean pathMatches(String requestPath, String allowedPath) {
        // Support wildcards: /driver/** matches /driver/dashboard
        if (allowedPath.endsWith("/**")) {
            String prefix = allowedPath.substring(0, allowedPath.length() - 3);
            return requestPath.startsWith(prefix);
        }
        if (allowedPath.contains("*")) {
            String regex = allowedPath
                    .replace(".", "\\.")
                    .replace("**", ".*")
                    .replace("*", "[^/]+");
            return requestPath.matches("^" + regex + "$");
        }
        // Exact match for paths without wildcards.
        return requestPath.equals(allowedPath);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
