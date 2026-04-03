package com.smartmobility.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {

            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            return Mono.just(Objects.requireNonNullElse(userId, "anonymous"));

        };
    }
}