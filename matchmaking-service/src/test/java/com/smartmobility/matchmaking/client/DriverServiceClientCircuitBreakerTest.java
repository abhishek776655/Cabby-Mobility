package com.smartmobility.matchmaking.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverServiceClientCircuitBreakerTest {

    @Test
    void getDriverFallbackReturnsNull() {
        DriverServiceClient client = new DriverServiceClient(
            "http://unused",
            org.springframework.web.client.RestClient.create(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        var result = client.getDriverFallback(1L, new RuntimeException("simulated"));

        assertNull(result);
    }

    @Test
    void getDriversBatchFallbackReturnsEmptyList() {
        DriverServiceClient client = new DriverServiceClient(
            "http://unused",
            org.springframework.web.client.RestClient.create(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        var result = client.getDriversBatchFallback(List.of(1L, 2L), new RuntimeException("simulated"));

        assertTrue(result.isEmpty());
    }
}
