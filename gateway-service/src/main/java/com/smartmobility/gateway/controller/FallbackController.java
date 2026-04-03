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
}
