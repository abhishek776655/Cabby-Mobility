package com.smartmobility.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RateLimitConfigTest {

    private final KeyResolver resolver = new RateLimitConfig().userKeyResolver();

    @Test
    void usesXUserIdHeaderWhenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/rides").header("X-User-Id", "42"));

        assertEquals("42", resolver.resolve(exchange).block());
    }

    @Test
    void fallsBackToPerClientIpNotASharedAnonymousBucket() {
        MockServerWebExchange exchangeA = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .remoteAddress(new InetSocketAddress("10.0.0.1", 12345)));
        MockServerWebExchange exchangeB = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .remoteAddress(new InetSocketAddress("10.0.0.2", 54321)));

        String keyA = resolver.resolve(exchangeA).block();
        String keyB = resolver.resolve(exchangeB).block();

        assertNotEquals("anonymous", keyA);
        assertNotEquals(keyA, keyB, "different clients must not share one rate-limit bucket");
    }
}
