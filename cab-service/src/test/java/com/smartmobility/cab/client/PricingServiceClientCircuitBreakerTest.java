package com.smartmobility.cab.client;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingServiceClientCircuitBreakerTest {

    @Test
    void quoteFallbackReturnsEmptyOptional() {
        PricingServiceClient client = new PricingServiceClient(
            org.springframework.web.client.RestClient.builder(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            "http://unused", "unused-secret");

        Optional<PricingServiceClient.QuoteData> result = client.quoteFallback(
            12.0, 77.0, 12.1, 77.1, "STANDARD", new RuntimeException("simulated"));

        assertTrue(result.isEmpty());
    }

    @Test
    void finalizeFareFallbackReturnsEmptyOptional() {
        PricingServiceClient client = new PricingServiceClient(
            org.springframework.web.client.RestClient.builder(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            "http://unused", "unused-secret");

        Optional<PricingServiceClient.FinalizeData> result = client.finalizeFareFallback(
            "ride-1", UUID.randomUUID(), new RuntimeException("simulated"));

        assertTrue(result.isEmpty());
    }
}
