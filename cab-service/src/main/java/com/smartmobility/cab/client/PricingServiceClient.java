package com.smartmobility.cab.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class PricingServiceClient {

    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final String pricingServiceUrl;
    private final String internalApiSecret;
    
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 250;

    public PricingServiceClient(
            RestClient.Builder restClientBuilder,
            MeterRegistry meterRegistry,
            @Value("${services.pricing.url:http://pricing-service:8092}") String pricingServiceUrl,
            @Value("${internal.api.secret:secret}") String internalApiSecret) {
        this.restClient = restClientBuilder.build();
        this.meterRegistry = meterRegistry;
        this.pricingServiceUrl = pricingServiceUrl;
        this.internalApiSecret = internalApiSecret;
    }

    public record FareQuoteRequest(double pickupLat, double pickupLng, double dropLat, double dropLng, String vehicleType) {}
    public record FareQuoteResponse(boolean success, QuoteData data) {}
    public record QuoteData(UUID estimateId, FareBreakdown breakdown, String currency, String estimateSource) {}

    public record FareFinalizeRequest(String rideId, UUID estimateId) {}
    public record FareFinalizeResponse(boolean success, FinalizeData data) {}
    public record FinalizeData(String rideId, FareBreakdown finalBreakdown, String currency, String calculationSource) {}

    public record FareBreakdown(long baseFare, long distanceFare, long timeFare, long surgeAmount, long total, double surgeMultiplier) {}

    public Optional<QuoteData> quote(double pickupLat, double pickupLng, double dropLat, double dropLng, String vehicleType) {
        Timer.Sample sample = Timer.start(meterRegistry);
        FareQuoteRequest requestBody = new FareQuoteRequest(pickupLat, pickupLng, dropLat, dropLng, vehicleType);
        
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                FareQuoteResponse response = restClient.post()
                        .uri(pricingServiceUrl + "/internal/fares/quote")
                        .header("X-Internal-Secret", internalApiSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(FareQuoteResponse.class);
                        
                if (response != null && response.success() && response.data() != null) {
                    sample.stop(meterRegistry.timer("client.pricing.quote", "status", "success"));
                    return Optional.of(response.data());
                }
            } catch (RestClientException e) {
                log.warn("Attempt {}/{} failed to get quote from pricing-service: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        sample.stop(meterRegistry.timer("client.pricing.quote", "status", "failure"));
        log.error("All {} attempts to get quote failed", MAX_ATTEMPTS);
        return Optional.empty();
    }

    public Optional<FinalizeData> finalizeFare(String rideId, UUID estimateId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        FareFinalizeRequest requestBody = new FareFinalizeRequest(rideId, estimateId);
        
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                FareFinalizeResponse response = restClient.post()
                        .uri(pricingServiceUrl + "/internal/fares/finalize")
                        .header("X-Internal-Secret", internalApiSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(FareFinalizeResponse.class);
                        
                if (response != null && response.success() && response.data() != null) {
                    sample.stop(meterRegistry.timer("client.pricing.finalize", "status", "success"));
                    return Optional.of(response.data());
                }
            } catch (RestClientException e) {
                log.warn("Attempt {}/{} failed to finalize fare from pricing-service: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        sample.stop(meterRegistry.timer("client.pricing.finalize", "status", "failure"));
        log.error("All {} attempts to finalize fare failed", MAX_ATTEMPTS);
        return Optional.empty();
    }
}
