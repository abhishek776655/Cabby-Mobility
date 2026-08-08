package com.smartmobility.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Falls back to the caller's IP, not a shared "anonymous" bucket. Without this,
     * every unauthenticated request (login/register — exactly the endpoints most in
     * need of per-client limiting) shared one global bucket, so one flooding client
     * could exhaust it for everyone else hitting auth.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {

            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            if (userId != null) {
                return Mono.just(userId);
            }

            String remoteAddress = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            return Mono.just("ip:" + remoteAddress);
        };
    }
}