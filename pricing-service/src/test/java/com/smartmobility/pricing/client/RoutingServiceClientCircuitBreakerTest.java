package com.smartmobility.pricing.client;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingServiceClientCircuitBreakerTest {

    @Test
    void fallbackReturnsEmptyOptionalOnAnyThrowable() {
        RoutingServiceClient client = new RoutingServiceClient(
            org.springframework.web.client.RestClient.create(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            "http://unused", "unused-secret");

        Optional<RoutingServiceClient.RouteData> result = client.getRouteFallback(
            12.0, 77.0, 12.1, 77.1, new RuntimeException("simulated open circuit"));

        assertTrue(result.isEmpty());
    }
}
