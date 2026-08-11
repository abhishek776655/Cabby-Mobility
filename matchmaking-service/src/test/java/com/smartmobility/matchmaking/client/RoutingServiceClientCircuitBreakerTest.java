package com.smartmobility.matchmaking.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingServiceClientCircuitBreakerTest {

    @Test
    void fallbackReturnsEmptyOptionalOnAnyThrowable() {
        RoutingServiceClient client = new RoutingServiceClient(
            "http://unused", "unused-secret",
            org.springframework.web.client.RestClient.create(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        Optional<List<Double>> result = client.getDurationsSecondsFallback(
            12.0, 77.0, List.of(), new RuntimeException("simulated open circuit"));

        assertTrue(result.isEmpty());
    }
}
