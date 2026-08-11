package com.smartmobility.matchmaking.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationServiceClientCircuitBreakerTest {

    @Test
    void findNearbyDriversFallbackReturnsEmptyList() {
        LocationServiceClient client = new LocationServiceClient(
            "http://unused", "unused-secret",
            org.springframework.web.client.RestClient.create(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        List<Long> result = client.findNearbyDriversFallback(12.0, 77.0, 5.0, 10, new RuntimeException("simulated"));

        assertTrue(result.isEmpty());
    }

    @Test
    void getDriverLocationsBatchFallbackReturnsEmptyList() {
        LocationServiceClient client = new LocationServiceClient(
            "http://unused", "unused-secret",
            org.springframework.web.client.RestClient.create(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        var result = client.getDriverLocationsBatchFallback(List.of(1L, 2L), new RuntimeException("simulated"));

        assertTrue(result.isEmpty());
    }
}
