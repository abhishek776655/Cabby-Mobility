package com.smartmobility.gateway.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/cab")
    public Mono<Map<String, Object>> cabFallback() {
        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Cab service temporarily unavailable"
                )
        );    }

    @RequestMapping("/fallback/auth")
    public Mono<Map<String, Object>> authFallback() {

        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Auth service temporarily unavailable"
                )
        );
    }

    @RequestMapping("/fallback/user")
    public Mono<Map<String, Object>> userFallback() {
        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "User service temporarily unavailable"
                )
        );
    }

    @RequestMapping("/fallback/driver")
    public Mono<Map<String, Object>> driverFallback() {
        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Driver service temporarily unavailable"
                )
        );
    }

    @RequestMapping("/fallback/location")
    public Mono<Map<String, Object>> locationFallback() {
        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Location service temporarily unavailable"
                )
        );
    }

    @RequestMapping("/fallback/matchmaking")
    public Mono<Map<String, Object>> matchmakingFallback() {
        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Matchmaking service temporarily unavailable"
                )
        );
    }

    @RequestMapping("/fallback/rider")
    public Mono<Map<String, Object>> riderFallback() {
        return Mono.just(
                Map.of(
                        "status", 503,
                        "error", "SERVICE_UNAVAILABLE",
                        "message", "Rider service temporarily unavailable"
                )
        );
    }
}
